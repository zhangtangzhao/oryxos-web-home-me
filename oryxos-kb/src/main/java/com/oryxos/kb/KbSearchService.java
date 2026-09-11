package com.oryxos.kb;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 混合检索（research D4/D6）：查询嵌入 → 语义路余弦 top-candidate-pool（每 KB
 * 向量懒加载缓存，按文档版本失效）→ 关键词路 FTS BM25 top-candidate-pool →
 * 加权 RRF（k=60, w=0.7/0.3）→ Reranker 槽位 → top_k。
 * <p>
 * 分支契约 = contracts/agent-tools.md：绑定过滤在此强制（不依赖模型自觉）；
 * 嵌入未配置 → 不可用输出；已配置但故障 → 仅关键词路 + degraded 标志。
 * RRF 得分归一化到 [0,1]：除以 (w_sem+w_kw)/(k+1)，双路第一名 = 1.0。
 */
public class KbSearchService {

    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(KbSearchService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int CONTENT_MAX_CHARS = 8000;

    private final KbStore store;
    private final EmbeddingClient embeddingClient;
    private final Reranker reranker;
    private final int defaultTopK;
    private final int maxTopK;
    private final int candidatePool;
    private final int rrfK;
    private final double semanticWeight;
    private final double keywordWeight;
    private final double semanticFloor;

    private final Map<String, CachedVectors> vectorCache = new ConcurrentHashMap<>();

    private record CachedVectors(String version, List<KbStore.EmbeddedChunk> chunks) {
    }

    public KbSearchService(KbStore store, EmbeddingClient embeddingClient, Reranker reranker,
                           KbProperties properties) {
        this.store = store;
        this.embeddingClient = embeddingClient;
        this.reranker = reranker;
        KbProperties.Search s = properties.getSearch();
        this.defaultTopK = s.getTopK();
        this.maxTopK = s.getMaxTopK();
        this.candidatePool = s.getCandidatePool();
        this.rrfK = s.getRrfK();
        this.semanticWeight = s.getSemanticWeight();
        this.keywordWeight = s.getKeywordWeight();
        this.semanticFloor = s.getSemanticFloor();
    }

    // ---- 结果形状 ----

    public record SearchHit(int rank, double score, String kb, String docPath,
                            String headingPath, int chunkOrdinal, String content) {
    }

    /**
     * error=true 时 errorMessage/text 给出模型可读分支文本；成功时 text 为
     * 模型可见正文，auditJson 为落 tool_invocations.result_json 的固定结构。
     * hits 为结构化命中（auditJson.results 无 content，试检索展示片段由此取）；
     * 非成功分支恒为空列表。
     */
    public record SearchResult(boolean error, String errorMessage, String text,
                               String kb, int resultsCount, boolean zeroResult,
                               boolean degraded, String degradedReason,
                               List<Double> topScores, long durationMs, String auditJson,
                               List<SearchHit> hits) {
    }

    // ---- 入口 ----

    public SearchResult search(List<String> boundKbs, String kbParam, String query, Integer topKParam) {
        long start = System.currentTimeMillis();
        Resolved resolved = resolveKb(boundKbs, kbParam);
        if (!resolved.ok()) {
            return error(resolved.errorText(), start);
        }
        String kb = resolved.name();
        int topK = clampTopK(topKParam);

        // ---- 嵌入模型身份防护（FR-015）：库已摄取过才比对，防静默错配检索 ----
        KbIdentity stored = store.embeddingIdentity(kb).orElse(null);
        if (stored != null && !embeddingClient.model().isBlank()
                && (!stored.model().equals(embeddingClient.model())
                        || stored.dimensions() != embeddingClient.dimensions())) {
            return modelMismatch(kb, stored, start);
        }

        boolean degraded = false;
        String degradedReason = null;
        List<KbStore.EmbeddedChunk> semanticRanked = List.of();

        // ---- 语义路（嵌入是弱依赖，故障降级不失败）----
        try {
            List<float[]> vectors = embeddingClient.embed(List.of(query));
            semanticRanked = semanticTop(kb, vectors.get(0));
        } catch (EmbeddingNotConfiguredException e) {
            log.warn("kb_search 嵌入未配置: kb={}, 原因: {}", kb, e.getMessage());
            return unavailable(kb, start);
        } catch (EmbeddingUnavailableException e) {
            degraded = true;
            degradedReason = "embedding_unavailable: " + e.getMessage();
        }

        // ---- 关键词路 ----
        List<KbStore.KeywordHit> keywordHits = store.searchKeywords(kb, query, candidatePool);
        if (!keywordHits.isEmpty() && keywordHits.get(0).degraded()) {
            degraded = true;
            degradedReason = degradedReason == null
                    ? "fts_unavailable_like_fallback"
                    : degradedReason + "; fts_unavailable_like_fallback";
        }

        // ---- 加权 RRF 合并 ----
        List<SearchHit> hits = merge(kb, semanticRanked, keywordHits, topK, query);
        boolean zeroResult = hits.isEmpty();
        long durationMs = System.currentTimeMillis() - start;
        List<Double> topScores = hits.stream().map(SearchHit::score).toList();

        StringBuilder text = new StringBuilder();
        if (degraded) {
            text.append("[降级：仅关键词检索]\n");
        }
        if (zeroResult) {
            text.append("未找到与 \"").append(query).append("\" 相关的内容（kb=").append(kb).append("）");
        } else {
            for (SearchHit h : hits) {
                text.append(renderHit(h));
            }
        }
        String auditJson = audit(kb, hits, zeroResult, degraded, degradedReason, durationMs);
        return new SearchResult(false, null, text.toString(), kb, hits.size(),
                zeroResult, degraded, degradedReason, topScores, durationMs, auditJson, hits);
    }

    // ---- kb 解析（绑定过滤，越权防护）----

    public record Resolved(boolean ok, String name, String errorText) {
    }

    public Resolved resolveKb(List<String> boundKbs, String kbParam) {
        List<String> bound = boundKbs == null ? List.of()
                : boundKbs.stream().filter(k -> k != null && !k.isBlank()).distinct().toList();
        if (bound.isEmpty()) {
            return new Resolved(false, null, "当前 Agent 未绑定知识库（frontmatter knowledge_bases）");
        }
        if (kbParam == null || kbParam.isBlank()) {
            if (bound.size() == 1) {
                return new Resolved(true, bound.get(0), null);
            }
            return new Resolved(false, null, "请指定 kb 参数，可选值: " + String.join(", ", bound));
        }
        if (!bound.contains(kbParam)) {
            return new Resolved(false, null, "知识库不可用: " + kbParam);
        }
        return new Resolved(true, kbParam, null);
    }

    private int clampTopK(Integer topKParam) {
        int topK = topKParam == null ? defaultTopK : topKParam;
        return Math.max(1, Math.min(topK, maxTopK));
    }

    // ---- 语义路 ----

    private List<KbStore.EmbeddedChunk> semanticTop(String kb, float[] queryVector) {
        List<KbStore.EmbeddedChunk> chunks = cachedVectors(kb);
        record Scored(KbStore.EmbeddedChunk chunk, double score) {
        }
        List<Scored> scored = new ArrayList<>(chunks.size());
        for (KbStore.EmbeddedChunk c : chunks) {
            double cos = cosine(queryVector, c.embedding());
            if (cos >= semanticFloor) {
                scored.add(new Scored(c, cos));
            }
        }
        scored.sort(Comparator.comparingDouble(Scored::score).reversed());
        return scored.stream().limit(candidatePool).map(Scored::chunk).toList();
    }

    /** 每 KB 向量懒加载缓存：版本 = 文档数 + 最大 ingested_at，摄取后自动失效。 */
    private List<KbStore.EmbeddedChunk> cachedVectors(String kb) {
        String version = documentVersion(kb);
        CachedVectors cached = vectorCache.get(kb);
        if (cached == null || !cached.version().equals(version)) {
            cached = new CachedVectors(version, store.chunkVectors(kb));
            vectorCache.put(kb, cached);
        }
        return cached.chunks();
    }

    private String documentVersion(String kb) {
        List<KbDocumentRecord> docs = store.listDocuments(kb);
        long maxIngested = docs.stream()
                .filter(d -> d.ingestedAt() != null)
                .mapToLong(d -> d.ingestedAt().toEpochMilli())
                .max().orElse(0);
        return docs.size() + ":" + maxIngested;
    }

    /** 余弦相似度；零向量返回 NEGATIVE_INFINITY（不可比）。 */
    private static double cosine(float[] a, float[] b) {
        if (a == null || b == null || a.length != b.length || a.length == 0) {
            return Double.NEGATIVE_INFINITY;
        }
        double dot = 0;
        double na = 0;
        double nb = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            na += (double) a[i] * a[i];
            nb += (double) b[i] * b[i];
        }
        if (na == 0 || nb == 0) {
            return Double.NEGATIVE_INFINITY;
        }
        return dot / (Math.sqrt(na) * Math.sqrt(nb));
    }

    // ---- RRF 合并 ----

    private List<SearchHit> merge(String kb, List<KbStore.EmbeddedChunk> semanticRanked,
                                  List<KbStore.KeywordHit> keywordHits, int topK, String query) {
        Map<Long, Integer> semRank = new LinkedHashMap<>();
        for (int i = 0; i < semanticRanked.size(); i++) {
            semRank.put(semanticRanked.get(i).chunkId(), i + 1);
        }
        Map<Long, Integer> kwRank = new LinkedHashMap<>();
        for (int i = 0; i < keywordHits.size(); i++) {
            kwRank.put(keywordHits.get(i).chunkId(), i + 1);
        }

        record Merged(long chunkId, double score) {
        }
        double normalizer = (semanticWeight + keywordWeight) / (rrfK + 1);
        List<Merged> merged = new ArrayList<>();
        for (Long chunkId : union(semRank.keySet(), kwRank.keySet())) {
            Integer sr = semRank.get(chunkId);
            Integer kr = kwRank.get(chunkId);
            double rrf = (sr == null ? 0 : semanticWeight / (rrfK + sr))
                    + (kr == null ? 0 : keywordWeight / (rrfK + kr));
            merged.add(new Merged(chunkId, rrf / normalizer));
        }
        merged.sort(Comparator.comparingDouble(Merged::score).reversed());

        // Reranker 槽位（本期 Noop 直通截断）
        List<Object> candidates = new ArrayList<>(merged);
        List<?> reranked = reranker.rerank(query, candidates, topK);

        List<SearchHit> hits = new ArrayList<>(reranked.size());
        int rank = 1;
        for (Object o : reranked) {
            Merged m = (Merged) o;
            KbStore.ChunkRef ref = resolveChunk(m.chunkId(), semanticRanked);
            if (ref == null) {
                continue;
            }
            hits.add(new SearchHit(rank++, m.score(), kb, ref.docPath(), ref.headingPath(),
                    ref.ordinal(), ref.content()));
        }
        return hits;
    }

    private static List<Long> union(Iterable<Long> a, Iterable<Long> b) {
        Map<Long, Boolean> seen = new LinkedHashMap<>();
        for (Long id : a) {
            seen.put(id, Boolean.TRUE);
        }
        for (Long id : b) {
            seen.putIfAbsent(id, Boolean.FALSE);
        }
        return new ArrayList<>(seen.keySet());
    }

    private KbStore.ChunkRef resolveChunk(long chunkId, List<KbStore.EmbeddedChunk> semanticRanked) {
        for (KbStore.EmbeddedChunk c : semanticRanked) {
            if (c.chunkId() == chunkId) {
                return new KbStore.ChunkRef(c.chunkId(), null, c.docPath(), c.ordinal(),
                        c.headingPath(), c.content());
            }
        }
        return store.chunkById(chunkId).orElse(null);
    }

    // ---- 输出与审计 ----

    private static String renderHit(SearchHit h) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("[%d] score=%.2f kb=%s %s", h.rank(), h.score(), h.kb(), h.docPath()));
        if (h.headingPath() != null && !h.headingPath().isBlank()) {
            sb.append(" # ").append(h.headingPath());
        }
        sb.append(" (chunk ").append(h.chunkOrdinal()).append(")\n");
        String content = h.content();
        if (content != null && content.length() > CONTENT_MAX_CHARS) {
            content = content.substring(0, CONTENT_MAX_CHARS) + "…";
        }
        sb.append("    ").append(content == null ? "" : content.replace("\n", "\n    "));
        return sb.append('\n').toString();
    }

    private String audit(String kb, List<SearchHit> hits, boolean zeroResult, boolean degraded,
                         String degradedReason, long durationMs) {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("kb", kb);
        root.put("results_count", hits.size());
        root.put("zero_result", zeroResult);
        root.put("degraded", degraded);
        root.put("degraded_reason", degradedReason);
        ArrayNode scores = root.putArray("top_scores");
        hits.forEach(h -> scores.add(h.score()));
        root.put("duration_ms", durationMs);
        ArrayNode results = root.putArray("results");
        for (SearchHit h : hits) {
            ObjectNode r = results.addObject();
            r.put("kb", h.kb());
            r.put("doc_path", h.docPath());
            r.put("heading_path", h.headingPath());
            r.put("chunk_ordinal", h.chunkOrdinal());
            r.put("score", h.score());
        }
        try {
            return MAPPER.writeValueAsString(root);
        } catch (Exception e) {
            return "{\"error\":\"audit_serialize_failed\"}";
        }
    }

    private SearchResult unavailable(String kb, long start) {
        String text = "知识库检索不可用：未配置嵌入服务（oryxos.kb.embedding）";
        return unavailableLike(kb, text, "embedding_not_configured", start, null, null);
    }

    /** FR-015 / SC-007：嵌入模型身份与库记录不一致 → 明确报错而非静默错配检索。 */
    private SearchResult modelMismatch(String kb, KbIdentity stored, long start) {
        String text = "知识库检索不可用：嵌入模型已变更（" + stored.model() + " → "
                + embeddingClient.model() + "），请恢复配置或重建知识库";
        return unavailableLike(kb, text, "embedding_model_mismatch", start,
                stored.model(), embeddingClient.model());
    }

    private SearchResult unavailableLike(String kb, String text, String reason, long start,
                                         String storedModel, String currentModel) {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("kb", kb);
        root.put("results_count", 0);
        root.put("zero_result", false);
        root.put("degraded", false);
        root.put("degraded_reason", reason);
        if (storedModel != null) {
            root.put("stored_model", storedModel);
            root.put("current_model", currentModel);
        }
        root.putArray("top_scores");
        root.put("duration_ms", System.currentTimeMillis() - start);
        root.putArray("results");
        String auditJson;
        try {
            auditJson = MAPPER.writeValueAsString(root);
        } catch (Exception e) {
            auditJson = "{\"error\":\"audit_serialize_failed\"}";
        }
        return new SearchResult(false, null, text, kb, 0, false, false,
                reason, List.of(), System.currentTimeMillis() - start, auditJson, List.of());
    }

    private static SearchResult error(String message, long start) {
        return new SearchResult(true, message, message, null, 0, false, false,
                null, List.of(), System.currentTimeMillis() - start, null, List.of());
    }
}
