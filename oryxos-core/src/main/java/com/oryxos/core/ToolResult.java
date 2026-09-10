package com.oryxos.core;

/**
 * Result of a tool execution.
 */
public class ToolResult {

    private final boolean success;
    private final String content;
    private final String errorMessage;
    private final boolean retryable;
    private final String auditJson;

    public ToolResult(boolean success, String content, String errorMessage, boolean retryable) {
        this(success, content, errorMessage, retryable, null);
    }

    private ToolResult(boolean success, String content, String errorMessage,
                       boolean retryable, String auditJson) {
        this.success = success;
        this.content = content;
        this.errorMessage = errorMessage;
        this.retryable = retryable;
        this.auditJson = auditJson;
    }

    public static ToolResult success(String content) {
        return new ToolResult(true, content, null, false, null);
    }

    /** 成功且附带结构化审计 JSON（落 tool_invocations.result_json，模型只见 content）。 */
    public static ToolResult successWithAudit(String content, String auditJson) {
        return new ToolResult(true, content, null, false, auditJson);
    }

    public static ToolResult failure(String errorMessage, boolean retryable) {
        return new ToolResult(false, null, errorMessage, retryable, null);
    }

    public boolean isSuccess() { return success; }
    public String getContent() { return content; }
    public String getErrorMessage() { return errorMessage; }
    public boolean isRetryable() { return retryable; }
    public String getAuditJson() { return auditJson; }
}
