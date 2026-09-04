package com.oryxos.core.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oryxos.core.OryxTool;
import com.oryxos.core.SandboxViolationException;
import com.oryxos.core.Session;
import com.oryxos.core.ToolRegistry;
import com.oryxos.core.ToolResult;
import com.oryxos.core.audit.AuditLog;
import com.oryxos.core.audit.ToolCallRecord;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * FR-014 retry semantics: retryable failures back off and retry at most
 * {@link RetryPolicy#MAX_ATTEMPTS} times, EVERY attempt is audited, and the
 * final failure is returned so the model can keep reasoning. Non-retryable
 * failures (e.g. sandbox violations) return immediately.
 */
class ToolExecutorRetryTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static Session session() {
        Session session = new Session();
        session.setSessionId("s-retry");
        return session;
    }

    /** AuditLog test double recording calls in memory. */
    static class RecordingAuditLog implements AuditLog {
        final List<String> events = new ArrayList<>();

        @Override
        public void recordLlmCall(String sessionId, String provider, String model,
                                  int promptTokens, int completionTokens,
                                  int totalTokens, long durationMs) {
            events.add("llm");
        }

        @Override
        public void recordToolInvocation(String sessionId, String toolName, String inputJson,
                                         String resultJson, boolean success, String errorMessage,
                                         long durationMs) {
            events.add((success ? "ok" : "fail") + ":" + toolName);
        }

        @Override
        public List<ToolCallRecord> toolInvocations(String sessionId) {
            return List.of();
        }

        @Override
        public long countLlmCalls(String sessionId) {
            return events.stream().filter("llm"::equals).count();
        }
    }

    @Test
    void retryableFailureRetriesUpToThreeAttemptsWithAuditEachTime() {
        AtomicInteger executions = new AtomicInteger();
        OryxTool flaky = new OryxTool() {
            @Override
            public String getName() { return "flaky"; }

            @Override
            public String getDescription() { return "always fails retryably"; }

            @Override
            public com.fasterxml.jackson.databind.JsonNode getInputSchema() {
                return MAPPER.createObjectNode();
            }

            @Override
            public ToolResult execute(com.fasterxml.jackson.databind.JsonNode input) {
                executions.incrementAndGet();
                return ToolResult.failure("网络超时", true);
            }
        };
        ToolRegistry registry = new ToolRegistry() {
            @Override
            public void register(OryxTool tool) { }

            @Override
            public OryxTool get(String name) { return flaky; }

            @Override
            public List<OryxTool> listAll() { return List.of(flaky); }

            @Override
            public List<OryxTool> listForAgent(List<String> toolNames) { return List.of(flaky); }
        };
        RecordingAuditLog auditLog = new RecordingAuditLog();
        ToolExecutor executor = new ToolExecutor(registry, auditLog);

        ToolResult result = executor.execute(session(), "flaky", Map.of());

        assertFalse(result.isSuccess());
        assertEquals(RetryPolicy.MAX_ATTEMPTS, executions.get(), "恰好尝试 3 次");
        assertEquals(RetryPolicy.MAX_ATTEMPTS, auditLog.events.size(), "每次尝试各落一条审计");
        assertTrue(auditLog.events.stream().allMatch(e -> e.equals("fail:flaky")));
    }

    @Test
    void nonRetryableFailureReturnsImmediatelyWithSingleAudit() {
        AtomicInteger executions = new AtomicInteger();
        OryxTool hardFail = new OryxTool() {
            @Override
            public String getName() { return "hard_fail"; }

            @Override
            public String getDescription() { return "always fails hard"; }

            @Override
            public com.fasterxml.jackson.databind.JsonNode getInputSchema() {
                return MAPPER.createObjectNode();
            }

            @Override
            public ToolResult execute(com.fasterxml.jackson.databind.JsonNode input) {
                executions.incrementAndGet();
                return ToolResult.failure("参数非法", false);
            }
        };
        ToolRegistry registry = new ToolRegistry() {
            @Override
            public void register(OryxTool tool) { }

            @Override
            public OryxTool get(String name) { return hardFail; }

            @Override
            public List<OryxTool> listAll() { return List.of(); }

            @Override
            public List<OryxTool> listForAgent(List<String> toolNames) { return List.of(); }
        };
        RecordingAuditLog auditLog = new RecordingAuditLog();
        ToolExecutor executor = new ToolExecutor(registry, auditLog);

        ToolResult result = executor.execute(session(), "hard_fail", Map.of());

        assertFalse(result.isSuccess());
        assertEquals(1, executions.get());
        assertEquals(1, auditLog.events.size());
    }

    @Test
    void sandboxViolationIsAuditedAsFailureAndNotRetried() {
        OryxTool guarded = new OryxTool() {
            @Override
            public String getName() { return "guarded"; }

            @Override
            public String getDescription() { return "throws sandbox violation"; }

            @Override
            public com.fasterxml.jackson.databind.JsonNode getInputSchema() {
                return MAPPER.createObjectNode();
            }

            @Override
            public ToolResult execute(com.fasterxml.jackson.databind.JsonNode input) {
                throw new SandboxViolationException("文件路径不在白名单内: /etc/passwd");
            }
        };
        ToolRegistry registry = new ToolRegistry() {
            @Override
            public void register(OryxTool tool) { }

            @Override
            public OryxTool get(String name) { return guarded; }

            @Override
            public List<OryxTool> listAll() { return List.of(); }

            @Override
            public List<OryxTool> listForAgent(List<String> toolNames) { return List.of(); }
        };
        RecordingAuditLog auditLog = new RecordingAuditLog();
        ToolExecutor executor = new ToolExecutor(registry, auditLog);

        ToolResult result = executor.execute(session(), "guarded", Map.of());

        assertFalse(result.isSuccess());
        assertEquals(1, auditLog.events.size(), "拦截只落一条审计且不重试");
        assertTrue(result.getErrorMessage().contains("/etc/passwd"));
    }

    @Test
    void successOnLaterAttemptStopsRetrying() {
        AtomicInteger executions = new AtomicInteger();
        OryxTool flakyThenOk = new OryxTool() {
            @Override
            public String getName() { return "flaky_ok"; }

            @Override
            public String getDescription() { return "fails once then succeeds"; }

            @Override
            public com.fasterxml.jackson.databind.JsonNode getInputSchema() {
                return MAPPER.createObjectNode();
            }

            @Override
            public ToolResult execute(com.fasterxml.jackson.databind.JsonNode input) {
                if (executions.incrementAndGet() < 2) {
                    return ToolResult.failure("瞬时抖动", true);
                }
                return ToolResult.success("done");
            }
        };
        ToolRegistry registry = new ToolRegistry() {
            @Override
            public void register(OryxTool tool) { }

            @Override
            public OryxTool get(String name) { return flakyThenOk; }

            @Override
            public List<OryxTool> listAll() { return List.of(); }

            @Override
            public List<OryxTool> listForAgent(List<String> toolNames) { return List.of(); }
        };
        RecordingAuditLog auditLog = new RecordingAuditLog();
        ToolExecutor executor = new ToolExecutor(registry, auditLog);

        ToolResult result = executor.execute(session(), "flaky_ok", Map.of());

        assertTrue(result.isSuccess());
        assertEquals("done", result.getContent());
        assertEquals(2, auditLog.events.size(), "两次尝试各一条审计");
    }

    @Test
    void unknownToolAuditsOnceWithoutExecuting() {
        ToolRegistry registry = new ToolRegistry() {
            @Override
            public void register(OryxTool tool) { }

            @Override
            public OryxTool get(String name) { return null; }

            @Override
            public List<OryxTool> listAll() { return List.of(); }

            @Override
            public List<OryxTool> listForAgent(List<String> toolNames) { return List.of(); }
        };
        RecordingAuditLog auditLog = new RecordingAuditLog();
        ToolExecutor executor = new ToolExecutor(registry, auditLog);

        ToolResult result = executor.execute(session(), "nope", Map.of());

        assertFalse(result.isSuccess());
        assertEquals(1, auditLog.events.size());
        assertTrue(result.getErrorMessage().contains("nope"));
    }
}
