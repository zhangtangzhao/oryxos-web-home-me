package com.oryxos.core.audit;

import java.time.Instant;

/** Read model of one tool_invocations row. */
public record ToolCallRecord(String toolName, boolean success, String errorMessage,
                             long durationMs, Instant createdAt) {
}
