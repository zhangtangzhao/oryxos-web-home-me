package com.oryxos.core.audit;

import java.util.List;

/**
 * Port from core services to the audit store. Implementations persist every
 * LLM call and tool invocation — day-one audit data foundation (constitution V).
 */
public interface AuditLog {

    void recordLlmCall(String sessionId, String provider, String model,
                       int promptTokens, int completionTokens, int totalTokens, long durationMs);

    void recordToolInvocation(String sessionId, String toolName, String inputJson,
                              String resultJson, boolean success, String errorMessage, long durationMs);

    /** Tool invocation records for a session, newest first (CLI /tools view). */
    List<ToolCallRecord> toolInvocations(String sessionId);

    /** Number of llm_calls rows recorded for the session (turn-scoped diffing, REST iterations). */
    long countLlmCalls(String sessionId);
}
