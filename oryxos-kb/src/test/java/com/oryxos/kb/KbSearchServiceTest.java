package com.oryxos.kb;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import com.oryxos.kb.KbStore.ChunkRef;
import com.oryxos.kb.KbStore.EmbeddedChunk;
import com.oryxos.kb.KbStore.KeywordHit;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * KbSearchService 分支与合并序（contracts/agent-tools.md）：假 EmbeddingClient
 * 2 维向量 + 假 KbStore 固定关键词命中，RRF 序手工可算（k=60, w=0.7/0.3）。
 */
class KbSearchServiceTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    /** 2 维假嵌入：query=(1,0)；块向量 (1,0)/(0,1)/(1,1) → 余弦 1.0/0/0.707。 */
    static class FakeEmbedding implements EmbeddingClient {
        boolean unavailable;
        boolean notConfigured;
        float[] queryVector = {1f, 0f};

        @Override
        public List<float[]> embed(List<String> inputs) {
            if (notConfigured) {
                throw new EmbeddingNotConfiguredException("未配置嵌入服务：base-url 为空");
            }
            if (unavailable) {
                throw new EmbeddingUnavailableException("嵌入服务调用失败: conn refused");
            }
            return inputs.stream().map(s -> queryVector).toList();
        }

        @Override
        public String model() {
            return "fake-embedding";
        }

        @Override
        public int dimensions() {
            return 2;
        }
    }

    /** 只实现检索侧读取；其余操作本测试不触及。 */
    static class FakeStore implements KbStore {
        List<EmbeddedChunk> vectors = new ArrayList<>();
        List<KeywordHit> keyword = new ArrayList<>();
        Map<Long, ChunkRef> refs = new LinkedHashMap<>();
        /** 库已记录的嵌入身份；null = 未摄取过（FR-015 比对跳过）。 */
        KbIdentity identity;

        @Override
        public List<KeywordHit> searchKeywords(String kb, String query, int limit) {
            return keyword;
        }

        @Override
        public List<EmbeddedChunk> chunkVectors(String kb) {
            return vectors;
        }

        @Override
        public Optional<ChunkRef> chunkById(long chunkId) {
            return Optional.ofNullable(refs.get(chunkId));
        }

        @Override
        public List<KbDocumentRecord> listDocuments(String kb) {
            return List.of();
        }

        private UnsupportedOperationException na() {
            return new UnsupportedOperationException("not used in search tests");
        }

        @Override
        public KbRecord createKb(String name, String description) { throw na(); }

        @Override
        public Optional<KbRecord> findKb(String name) { throw na(); }

        @Override
        public List<KbRecord> listKbs() { throw na(); }

        @Override
        public void deleteKb(String name) { throw na(); }

        @Override
        public KbDocumentRecord upsertDocument(String kb, String docPath, long sizeBytes, String contentHash) { throw na(); }

        @Override
        public Optional<KbDocumentRecord> findDocument(String kb, String docPath) { throw na(); }

        @Override
        public void markDocument(long documentId, KbDocumentStatus status, String errorMessage, Integer chunkCount) { throw na(); }

        @Override
        public void removeDocument(String kb, String docPath) { throw na(); }

        @Override
        public void replaceChunks(long documentId, String kb, List<KbChunkData> chunks) { throw na(); }

        @Override
        public List<KbChunkData> chunksOf(long documentId) { throw na(); }

        @Override
        public Optional<KbIdentity> embeddingIdentity(String kb) { return Optional.ofNullable(identity); }

        @Override
        public void saveEmbeddingIdentity(String kb, String model, int dimensions) { throw na(); }

        @Override
        public List<KbOverviewEntry> overview(String kb) { throw na(); }
    }

    private static FakeStore store() {
        FakeStore store = new FakeStore();
        // id, 向量, 文档/标题
        store.vectors.add(new EmbeddedChunk(101L, "docs/deploy.md", 0, "部署指南 > Windows", "内容A", new float[]{1f, 0f}));
        store.vectors.add(new EmbeddedChunk(102L, "docs/deploy.md", 1, "部署指南 > Windows", "内容B", new float[]{0f, 1f}));
        store.vectors.add(new EmbeddedChunk(103L, "docs/faq.md", 0, "常见问题", "内容C", new float[]{1f, 1f}));
        store.refs.put(101L, new ChunkRef(101L, "docs", "docs/deploy.md", 0, "部署指南 > Windows", "内容A"));
        store.refs.put(102L, new ChunkRef(102L, "docs", "docs/deploy.md", 1, "部署指南 > Windows", "内容B"));
        store.refs.put(103L, new ChunkRef(103L, "docs", "docs/faq.md", 0, "常见问题", "内容C"));
        return store;
    }

    /** 关键词路命中序：103 第一、102 第二（与语义路错开以验证合并）。 */
    private static FakeStore storeWithKeyword() {
        FakeStore store = store();
        store.keyword.add(new KeywordHit(103L, 8.1, false));
        store.keyword.add(new KeywordHit(102L, 5.5, false));
        return store;
    }

    private static KbSearchService service(FakeStore store, FakeEmbedding embedding) {
        return new KbSearchService(store, embedding, new NoopReranker(), new KbProperties());
    }

    // ---- RRF 合并序 ----

    @Test
    void weightedRrfMergeOrderAndScores() throws Exception {
        FakeStore store = storeWithKeyword();
        KbSearchService.SearchResult r = service(store, new FakeEmbedding())
                .search(List.of("docs"), "docs", "部署", 5);
        assertFalse(r.error());
        // 语义路（floor=0.35，查询向量 (1,0)）：101(1.0)、103(0.707) 入池；102(0.0) 被弃
        // 关键词 ranks: 103(1) 102(2)
        // 103 = (0.7/62 + 0.3/61) / (1/61) = 0.98870（双路第一）
        // 101 = 0.7（仅语义路 rank1）
        // 102 = (0.3/62) / (1/61) = 0.29516（仅关键词路 rank2）
        assertEquals(List.of("docs/faq.md", "docs/deploy.md", "docs/deploy.md"), docOrder(r));
        assertEquals(3, r.resultsCount());
        assertEquals(0.9887, r.topScores().get(0), 1e-3);
        assertEquals(0.7, r.topScores().get(1), 1e-3);
        assertEquals(0.2952, r.topScores().get(2), 1e-3);
        assertTrue(r.text().contains("[1] score=0.99 kb=docs docs/faq.md"));
        assertFalse(r.degraded());
        assertFalse(r.zeroResult());
    }

    @Test
    void belowFloorSemanticCandidatesDropped() {
        FakeStore store = store(); // 无关键词命中
        FakeEmbedding embedding = new FakeEmbedding();
        embedding.queryVector = new float[]{1f, 0f}; // 102=(0,1) 余弦 0.0 → 低于 floor
        store.vectors.removeIf(c -> c.chunkId() != 102L); // 只留正交候选
        KbSearchService.SearchResult r = service(store, embedding)
                .search(List.of("docs"), "docs", "q", 5);
        assertTrue(r.zeroResult()); // 语义候选全部低于 floor 且关键词无命中 → 零结果
        assertFalse(r.degraded());
    }

    @Test
    void topKClampAndTruncate() {
        FakeStore store = storeWithKeyword();
        KbSearchService svc = service(store, new FakeEmbedding());
        assertEquals(2, svc.search(List.of("docs"), "docs", "q", 2).resultsCount());
        assertEquals(3, svc.search(List.of("docs"), "docs", "q", 999).resultsCount()); // 钳到 maxTopK=20
        assertEquals(1, svc.search(List.of("docs"), "docs", "q", 0).resultsCount());   // 钳到 1
    }

    // ---- 绑定过滤 ----

    @Test
    void unboundKbRejected() {
        KbSearchService.SearchResult r = service(storeWithKeyword(), new FakeEmbedding())
                .search(List.of("docs"), "other", "q", null);
        assertTrue(r.error());
        assertEquals("知识库不可用: other", r.errorMessage());
    }

    @Test
    void multiKbOmittedKbPromptsChoice() {
        KbSearchService.SearchResult r = service(storeWithKeyword(), new FakeEmbedding())
                .search(List.of("alpha", "beta"), null, "q", null);
        assertTrue(r.error());
        assertEquals("请指定 kb 参数，可选值: alpha, beta", r.errorMessage());
    }

    @Test
    void singleKbOmittedKbAutoSelected() {
        KbSearchService.SearchResult r = service(storeWithKeyword(), new FakeEmbedding())
                .search(List.of("docs"), null, "q", null);
        assertFalse(r.error());
        assertEquals("docs", r.kb());
    }

    // ---- 零结果 ----

    @Test
    void zeroResultFlagWhenNoHits() {
        FakeStore store = store(); // 无关键词命中；块向量与查询正交的块也会参与，但候选非空
        store.vectors.clear();     // 库为空 → 零结果
        KbSearchService.SearchResult r = service(store, new FakeEmbedding())
                .search(List.of("docs"), "docs", "不存在的问题", 5);
        assertFalse(r.error());
        assertTrue(r.zeroResult());
        assertEquals(0, r.resultsCount());
        assertTrue(r.text().contains("未找到与 \"不存在的问题\" 相关的内容（kb=docs）"));
        assertFalse(r.degraded());
    }

    // ---- 降级与未配置 ----

    @Test
    void embeddingUnavailableFallsBackToKeywordOnly() {
        FakeEmbedding embedding = new FakeEmbedding();
        embedding.unavailable = true;
        KbSearchService.SearchResult r = service(storeWithKeyword(), embedding)
                .search(List.of("docs"), "docs", "q", 5);
        assertFalse(r.error());
        assertTrue(r.degraded());
        assertEquals(2, r.resultsCount()); // 仅关键词路：103、102 两条命中
        assertTrue(r.text().startsWith("[降级：仅关键词检索]"));
        assertTrue(r.auditJson().contains("\"degraded\":true"));
    }

    @Test
    void embeddingUnavailableWithNoKeywordHitsIsZeroResult() {
        FakeEmbedding embedding = new FakeEmbedding();
        embedding.unavailable = true;
        KbSearchService.SearchResult r = service(store(), embedding)
                .search(List.of("docs"), "docs", "q", 5);
        assertTrue(r.zeroResult());
        assertTrue(r.degraded()); // 搜索真实执行过（关键词路），仅无命中
    }

    @Test
    void embeddingNotConfiguredIsUnavailableBranch() {
        FakeEmbedding embedding = new FakeEmbedding();
        embedding.notConfigured = true;
        KbSearchService.SearchResult r = service(storeWithKeyword(), embedding)
                .search(List.of("docs"), "docs", "q", 5);
        assertFalse(r.error());
        assertEquals("知识库检索不可用：未配置嵌入服务（oryxos.kb.embedding）", r.text());
        assertFalse(r.degraded());
        assertFalse(r.zeroResult());
        assertTrue(r.auditJson().contains("embedding_not_configured"));
    }

    @Test
    void embeddingModelMismatchIsExplicitError() throws Exception {
        FakeStore store = storeWithKeyword();
        store.identity = new KbIdentity("stored-model", 8); // 与 FakeEmbedding("fake-embedding") 不一致
        KbSearchService.SearchResult r = service(store, new FakeEmbedding())
                .search(List.of("docs"), "docs", "q", 5);
        assertFalse(r.error());
        assertTrue(r.text().contains("嵌入模型已变更（stored-model → fake-embedding）"));
        assertTrue(r.text().contains("请恢复配置或重建知识库"));
        assertFalse(r.degraded());
        assertFalse(r.zeroResult());
        JsonNode audit = JSON.readTree(r.auditJson());
        assertEquals("embedding_model_mismatch", audit.get("degraded_reason").asText());
        assertEquals("stored-model", audit.get("stored_model").asText());
    }

    @Test
    void embeddingIdentityMatchDoesNotBlockSearch() throws Exception {
        FakeStore store = storeWithKeyword();
        store.identity = new KbIdentity("fake-embedding", 2); // 与假客户端一致
        KbSearchService.SearchResult r = service(store, new FakeEmbedding())
                .search(List.of("docs"), "docs", "q", 5);
        assertFalse(r.text().contains("嵌入模型已变更"));
        assertTrue(r.resultsCount() > 0);
    }

    @Test
    void ftsLikeFallbackMarksDegradedReason() throws Exception {
        FakeStore store = store(); // 无关键词命中；仅语义路命中
        store.keyword.add(new KbStore.KeywordHit(103L, 3.2, true)); // LIKE 降级标注
        KbSearchService.SearchResult r = service(store, new FakeEmbedding())
                .search(List.of("docs"), "docs", "q", 5);
        assertFalse(r.error());
        assertTrue(r.degraded());
        JsonNode audit = JSON.readTree(r.auditJson());
        assertTrue(audit.get("degraded_reason").asText().contains("fts_unavailable_like_fallback"));
        assertTrue(r.degradedReason().contains("fts_unavailable_like_fallback"));
    }

    // ---- 审计 JSON 固定键 ----

    @Test
    void auditJsonHasFixedKeys() throws Exception {
        KbSearchService.SearchResult r = service(storeWithKeyword(), new FakeEmbedding())
                .search(List.of("docs"), "docs", "q", 5);
        JsonNode audit = JSON.readTree(r.auditJson());
        assertTrue(audit.has("kb"));
        assertTrue(audit.has("results_count"));
        assertTrue(audit.has("zero_result"));
        assertTrue(audit.has("degraded"));
        assertTrue(audit.has("degraded_reason"));
        assertTrue(audit.has("top_scores"));
        assertTrue(audit.has("duration_ms"));
        assertTrue(audit.has("results"));
        assertEquals("docs", audit.get("kb").asText());
        assertEquals(3, audit.get("results_count").asInt());
        assertEquals(3, audit.get("top_scores").size());
        assertEquals(3, audit.get("results").size());
        assertEquals("docs/faq.md", audit.get("results").get(0).get("doc_path").asText());
        assertEquals("常见问题", audit.get("results").get(0).get("heading_path").asText());
    }

    // ---- helpers ----

    private static List<String> docOrder(KbSearchService.SearchResult r) throws Exception {
        List<String> out = new ArrayList<>();
        for (JsonNode n : JSON.readTree(r.auditJson()).get("results")) {
            out.add(n.get("doc_path").asText());
        }
        return out;
    }
}
