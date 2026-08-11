package com.oryxos.core;

/**
 * Result of a tool execution.
 */
public class ToolResult {

    private final boolean success;
    private final String content;
    private final String errorMessage;
    private final boolean retryable;

    public ToolResult(boolean success, String content, String errorMessage, boolean retryable) {
        this.success = success;
        this.content = content;
        this.errorMessage = errorMessage;
        this.retryable = retryable;
    }

    public static ToolResult success(String content) {
        return new ToolResult(true, content, null, false);
    }

    public static ToolResult failure(String errorMessage, boolean retryable) {
        return new ToolResult(false, null, errorMessage, retryable);
    }

    public boolean isSuccess() { return success; }
    public String getContent() { return content; }
    public String getErrorMessage() { return errorMessage; }
    public boolean isRetryable() { return retryable; }
}
