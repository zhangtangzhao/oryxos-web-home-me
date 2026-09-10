package com.oryxos.kb;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * kb eval 评测服务（US6，contracts/cli.md §5）：读 {@code <root>/kb/<name>/evalset.yaml}
 * （格式 {@code [ {query, expected_path} ]}），逐条经 {@link KbSearchService} 真实
 * 检索（含嵌入），命中判定 = top_k 结果里出现 expected_path。expected_path 不在
 * 库中的样例标记无效、不计分；检索出错（如嵌入未配置）整体失败——没有嵌入的
 * hit@k 无意义。
 */
public class KbEvalService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final KbStore store;
    private final KbSearchService searchService;
    private final Path workspaceRoot;

    public KbEvalService(KbStore store, KbSearchService searchService, Path workspaceRoot) {
        this.store = store;
        this.searchService = searchService;
        this.workspaceRoot = workspaceRoot;
    }

    public record EvalCase(String query, String expectedPath) {
    }

    /** invalid=true 的样例不计分（hit/zeroResult/duration 均无意义）。 */
    public record CaseResult(String query, String expectedPath, boolean invalid,
                             boolean hit, int hitRank, boolean zeroResult, long durationMs) {
    }

    public record EvalReport(String kb, int topK, int total, int scored, int hits,
                             double hitRate, int zeroResults, long avgDurationMs,
                             List<CaseResult> cases) {
    }

    public EvalReport eval(String kb, Integer topK) {
        if (store.findKb(kb).isEmpty()) {
            throw new KbNotFoundException(kb);
        }
        Path evalset = workspaceRoot.resolve("kb").resolve(kb).resolve("evalset.yaml");
        if (!Files.exists(evalset)) {
            throw new IllegalArgumentException(
                    "评测集不存在: " + evalset + "（格式: - {query: 问题, expected_path: docs/xxx.md}）");
        }
        List<EvalCase> cases = parse(evalset);
        if (cases.isEmpty()) {
            throw new IllegalArgumentException("评测集为空: " + evalset);
        }

        Set<String> docPaths = store.listDocuments(kb).stream()
                .map(KbDocumentRecord::docPath).collect(Collectors.toSet());

        List<CaseResult> results = new ArrayList<>();
        int invalid = 0;
        int hits = 0;
        int zeroResults = 0;
        long totalMs = 0;
        for (EvalCase c : cases) {
            if (c.expectedPath() == null || !docPaths.contains(c.expectedPath())) {
                results.add(new CaseResult(c.query(), c.expectedPath(), true, false, 0, false, 0));
                invalid++;
                continue;
            }
            KbSearchService.SearchResult r = searchService.search(List.of(kb), kb, c.query(), topK);
            if (r.error()) {
                throw new IllegalStateException("样例检索失败（" + c.query() + "）: " + r.errorMessage());
            }
            int rank = hitRank(r.auditJson(), c.expectedPath());
            boolean hit = rank > 0;
            if (hit) {
                hits++;
            }
            if (r.zeroResult()) {
                zeroResults++;
            }
            totalMs += r.durationMs();
            results.add(new CaseResult(c.query(), c.expectedPath(), false, hit, rank,
                    r.zeroResult(), r.durationMs()));
        }

        int scored = cases.size() - invalid;
        double hitRate = scored == 0 ? 0.0 : hits * 100.0 / scored;
        long avg = scored == 0 ? 0 : Math.round((double) totalMs / scored);
        return new EvalReport(kb, topK == null ? 5 : topK, cases.size(), scored, hits,
                hitRate, zeroResults, avg, List.copyOf(results));
    }

    private List<EvalCase> parse(Path evalset) {
        Object yaml;
        try {
            yaml = new Yaml().load(Files.readString(evalset));
        } catch (IOException e) {
            throw new UncheckedIOException("读取评测集失败: " + evalset, e);
        }
        if (!(yaml instanceof List<?> rows)) {
            throw new IllegalArgumentException("评测集格式错误（需为列表）: " + evalset);
        }
        List<EvalCase> cases = new ArrayList<>();
        for (Object row : rows) {
            if (!(row instanceof java.util.Map<?, ?> m)) {
                throw new IllegalArgumentException("评测集条目格式错误（需为 query/expected_path 映射）: " + row);
            }
            Object query = m.get("query");
            Object expected = m.get("expected_path");
            cases.add(new EvalCase(query == null ? null : query.toString(),
                    expected == null ? null : expected.toString()));
        }
        return cases;
    }

    /** 命中名次（1 起）；未命中 0。解析审计 JSON 的 results[].doc_path。 */
    private int hitRank(String auditJson, String expectedPath) {
        try {
            JsonNode results = MAPPER.readTree(auditJson).path("results");
            for (int i = 0; i < results.size(); i++) {
                if (expectedPath.equals(results.get(i).path("doc_path").asText(null))) {
                    return i + 1;
                }
            }
            return 0;
        } catch (IOException e) {
            throw new IllegalStateException("解析检索审计结果失败", e);
        }
    }

    /** CLI 报告渲染（contracts/cli.md §5 输出形状）。 */
    public static String render(EvalReport r) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format(Locale.ROOT,
                "kb=%s 样例 %d 条（有效 %d）: 命中 %d → hit@%d = %.1f%%, zero_result %d, 平均耗时 %dms",
                r.kb(), r.total(), r.scored(), r.hits(), r.topK(), r.hitRate(),
                r.zeroResults(), r.avgDurationMs()));
        for (CaseResult c : r.cases()) {
            sb.append("\n- ");
            if (c.invalid()) {
                sb.append("[无效] ").append(c.query()).append(" → ").append(c.expectedPath())
                        .append("（库中不存在，不计分）");
            } else if (c.hit()) {
                sb.append("[命中 rank=").append(c.hitRank()).append("] ")
                        .append(c.query()).append(" → ").append(c.expectedPath());
            } else {
                sb.append("[未命中] ").append(c.query()).append(" → ").append(c.expectedPath());
            }
        }
        return sb.toString();
    }
}
