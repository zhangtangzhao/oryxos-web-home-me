package com.oryxos.core.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oryxos.core.OryxTool;
import com.oryxos.core.SandboxViolationException;
import com.oryxos.core.Session;
import com.oryxos.core.ToolRegistry;
import com.oryxos.core.ToolResult;
import com.oryxos.core.audit.AuditLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * The single orchestration point for tool execution: lookup -> execute ->
 * audit -> retry if retryable -> result. Every attempt (including sandbox
 * violations and each retry) lands in tool_invocations — audit happens before
 * the result is handed back to the model (constitution V, FR-014).
 * <p>
 * Whitelist enforcement itself happens inside each tool via Sandbox.enforce
 * as the first statement of execute(); this executor catches violations and
 * records them.
 */
@Service
public class ToolExecutor {

    private static final Logger log = LoggerFactory.getLogger(ToolExecutor.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ToolRegistry registry;
    private final AuditLog auditLog;

    public ToolExecutor(ToolRegistry registry, AuditLog auditLog) {
        this.registry = registry;
        this.auditLog = auditLog;
    }

    public ToolResult execute(Session session, String toolName, Map<String, Object> arguments) {
        OryxTool tool = registry.get(toolName);
        if (tool == null) {
            ToolResult unknown = ToolResult.failure("未知工具: " + toolName, false);
            auditLog.recordToolInvocation(session.getSessionId(), toolName,
                    toJson(arguments), null, false, unknown.getErrorMessage(), 0);
            return unknown;
        }

        String inputJson = toJson(arguments);
        for (int attempt = 1; attempt <= RetryPolicy.MAX_ATTEMPTS; attempt++) {
            long start = System.currentTimeMillis();
            ToolResult result;
            try {
                result = tool.execute(MAPPER.valueToTree(arguments));
            } catch (SandboxViolationException e) {
                log.warn("沙箱拦截: tool={} session={} 原因={}", toolName, session.getSessionId(), e.getMessage());
                result = ToolResult.failure(e.getMessage(), false);
            } catch (Exception e) {
                log.error("工具执行异常: tool={} attempt={}", toolName, attempt, e);
                result = ToolResult.failure(e.getClass().getSimpleName() + ": " + e.getMessage(), true);
            }
            long durationMs = System.currentTimeMillis() - start;

            // 每次尝试各落一条审计；先审计，后回传（宪法 V）
            auditLog.recordToolInvocation(session.getSessionId(), toolName, inputJson,
                    result.isSuccess() ? result.getContent() : null,
                    result.isSuccess(),
                    result.isSuccess() ? null : result.getErrorMessage(),
                    durationMs);

            if (result.isSuccess() || !RetryPolicy.shouldRetry(attempt, result.isRetryable())) {
                return result;
            }
            try {
                Thread.sleep(RetryPolicy.backoffDelayMs(attempt));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return result;
            }
        }
        // unreachable: loop always returns on the final attempt
        throw new IllegalStateException("retry loop exited");
    }

    private static String toJson(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (Exception e) {
            return String.valueOf(value);
        }
    }
}
