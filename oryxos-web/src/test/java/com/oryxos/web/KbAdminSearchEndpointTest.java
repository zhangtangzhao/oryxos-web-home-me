package com.oryxos.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oryxos.core.audit.AuditLog;
import com.oryxos.core.audit.ToolCallRecord;
import com.oryxos.kb.DefaultKbService;
import com.oryxos.kb.KbNotFoundException;
import com.oryxos.kb.KbRecord;
import com.oryxos.kb.KbSearchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * 试检索端点契约（contracts/admin-rest-api.md US4）：分支映射 200/400/404/409/
 * 503/502，且每次服务端检索落审计行（session_id=admin-ui、tool_name=kb_search、
 * result_json=auditJson），与 Agent 工具路径同 schema、同 sink（宪法 V）。
 */
class KbAdminSearchEndpointTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private DefaultKbService kbService;
    private KbSearchService searchService;
    private RecordingAuditLog auditLog;
    private MockMvc mvc;

    /** 单行录音审计桩：只关心 recordToolInvocation 的参数。 */
    static class RecordingAuditLog implements AuditLog {
        int calls;
        String sessionId;
        String toolName;
        String inputJson;
        String resultJson;
        boolean success;
        String errorMessage;

        @Override
        public void recordToolInvocation(String sessionId, String toolName, String inputJson,
                                         String resultJson, boolean success, String errorMessage,
                                         long durationMs) {
            this.calls++;
            this.sessionId = sessionId;
            this.toolName = toolName;
            this.inputJson = inputJson;
            this.resultJson = resultJson;
            this.success = success;
            this.errorMessage = errorMessage;
        }

        @Override
        public void recordLlmCall(String sessionId, String provider, String model, int promptTokens,
                                  int completionTokens, int totalTokens, long durationMs) {
        }

        @Override
        public List<ToolCallRecord> toolInvocations(String sessionId) {
            return List.of();
        }

        @Override
        public long countLlmCalls(String sessionId) {
            return 0;
        }
    }

    @BeforeEach
    void setUp() {
        kbService = mock(DefaultKbService.class);
        searchService = mock(KbSearchService.class);
        auditLog = new RecordingAuditLog();
        mvc = MockMvcBuilders.standaloneSetup(new KbApiController(kbService, searchService, auditLog))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        when(kbService.require("docs")).thenReturn(new KbRecord(
                "docs", "测试库", "fake-embedding", 2, Instant.now(), Instant.now()));
    }

    private static KbSearchService.SearchHit hit(int rank, double score, String docPath,
                                                 String heading, int ordinal, String content) {
        return new KbSearchService.SearchHit(rank, score, "docs", docPath, heading, ordinal, content);
    }

    private static KbSearchService.SearchResult result(List<KbSearchService.SearchHit> hits,
                                                       boolean zeroResult, String degradedReason,
                                                       String auditJson) {
        return new KbSearchService.SearchResult(false, null, "text", "docs", hits.size(),
                zeroResult, degradedReason != null, degradedReason,
                hits.stream().map(KbSearchService.SearchHit::score).toList(), 41L, auditJson, hits);
    }

    private JsonNode postExpect(int status, String body) throws Exception {
        MvcResult res = mvc.perform(post("/api/v1/kbs/docs/search")
                        .contentType("application/json").content(body))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .status().is(status))
                .andReturn();
        return JSON.readTree(res.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    // ---- 200 命中 ----

    @Test
    void searchReturnsHitsAndAuditRow() throws Exception {
        String auditJson = "{\"kb\":\"docs\",\"results_count\":1}";
        when(searchService.search(List.of("docs"), "docs", "部署", 3))
                .thenReturn(result(List.of(hit(1, 0.83, "docs/faq.md", "常见问题", 0, "内容C")),
                        false, null, auditJson));
        JsonNode resp = postExpect(200, "{\"query\":\"部署\",\"top_k\":3}");
        assertTrue(resp.get("success").asBoolean());
        JsonNode data = resp.get("data");
        assertEquals("docs", data.get("kb").asText());
        assertEquals("部署", data.get("query").asText());
        assertEquals(1, data.get("hits").size());
        JsonNode h = data.get("hits").get(0);
        assertEquals("docs/faq.md", h.get("doc_path").asText());
        assertEquals("常见问题", h.get("heading_path").asText());
        assertEquals(0, h.get("chunk_ordinal").asInt());
        assertEquals(0.83, h.get("score").asDouble(), 1e-9);
        assertEquals("内容C", h.get("content").asText());
        // 审计一致性（SC-002 / 宪法 V）
        assertEquals(1, auditLog.calls);
        assertEquals("admin-ui", auditLog.sessionId);
        assertEquals("kb_search", auditLog.toolName);
        assertTrue(auditLog.success);
        JsonNode input = JSON.readTree(auditLog.inputJson);
        assertEquals("docs", input.get("kb").asText());
        assertEquals("部署", input.get("query").asText());
        assertEquals(3, input.get("top_k").asInt());
        assertEquals(auditJson, auditLog.resultJson);
    }

    // ---- 200 零命中 ----

    @Test
    void zeroHitReturnsEmptyHitsWithZeroResultFlag() throws Exception {
        when(searchService.search(List.of("docs"), "docs", "不存在", null))
                .thenReturn(result(List.of(), true, null, "{\"kb\":\"docs\",\"results_count\":0}"));
        JsonNode resp = postExpect(200, "{\"query\":\"不存在\"}");
        assertTrue(resp.get("data").get("zero_result").asBoolean());
        assertEquals(0, resp.get("data").get("hits").size());
        assertTrue(auditLog.success);
        assertTrue(JSON.readTree(auditLog.inputJson).get("top_k").isNull());
    }

    // ---- 400 空 query ----

    @Test
    void blankQueryRejectedBeforeServiceCall() throws Exception {
        JsonNode resp = postExpect(400, "{\"query\":\"   \"}");
        assertEquals(400, resp.get("error").get("code").asInt());
        verifyNoInteractions(searchService);
        assertEquals(0, auditLog.calls);
    }

    // ---- 404 库不存在 ----

    @Test
    void missingKbMapsTo404() throws Exception {
        doThrow(new KbNotFoundException("知识库不存在: docs")).when(kbService).require("docs");
        JsonNode resp = postExpect(404, "{\"query\":\"q\"}");
        assertEquals(404, resp.get("error").get("code").asInt());
        assertTrue(resp.get("error").get("message").asText().contains("KB_NOT_FOUND"));
        verifyNoInteractions(searchService);
        assertEquals(0, auditLog.calls);
    }

    // ---- 409 身份变更（degraded_reason 判别） ----

    @Test
    void modelMismatchMapsTo409WithAudit() throws Exception {
        String auditJson = "{\"degraded_reason\":\"embedding_model_mismatch\"}";
        when(searchService.search(List.of("docs"), "docs", "q", null))
                .thenReturn(result(List.of(), false, "embedding_model_mismatch", auditJson));
        JsonNode resp = postExpect(409, "{\"query\":\"q\"}");
        assertEquals(409, resp.get("error").get("code").asInt());
        assertTrue(resp.get("error").get("message").asText().contains("EMBEDDING_MISMATCH"));
        assertEquals(1, auditLog.calls);
        assertEquals(false, auditLog.success);
        assertEquals(auditJson, auditLog.resultJson);
    }

    // ---- 503 未配置 ----

    @Test
    void notConfiguredMapsTo503() throws Exception {
        when(searchService.search(List.of("docs"), "docs", "q", null))
                .thenReturn(result(List.of(), false, "embedding_not_configured",
                        "{\"degraded_reason\":\"embedding_not_configured\"}"));
        JsonNode resp = postExpect(503, "{\"query\":\"q\"}");
        assertEquals(503, resp.get("error").get("code").asInt());
        assertTrue(resp.get("error").get("message").asText().contains("EMBEDDING_NOT_CONFIGURED"));
        assertEquals(1, auditLog.calls);
        assertEquals(false, auditLog.success);
    }

    // ---- 502 兜底 ----

    @Test
    void serviceFailureMapsTo502WithFailureAudit() throws Exception {
        when(searchService.search(List.of("docs"), "docs", "q", null))
                .thenThrow(new IllegalStateException("boom"));
        JsonNode resp = postExpect(502, "{\"query\":\"q\"}");
        assertEquals(502, resp.get("error").get("code").asInt());
        assertTrue(resp.get("error").get("message").asText().contains("SEARCH_FAILED"));
        assertEquals(1, auditLog.calls);
        assertEquals(false, auditLog.success);
        assertTrue(auditLog.errorMessage.contains("boom"));
    }
}
