package com.oryxos.core.llm;

import java.util.List;

/** LLM completion result: text content and/or requested tool calls plus usage. */
public class LlmResult {

    private String content;
    private List<ToolCallSpec> toolCalls;
    private int promptTokens;
    private int completionTokens;
    private int totalTokens;
    private long durationMs;

    public boolean hasToolCalls() {
        return toolCalls != null && !toolCalls.isEmpty();
    }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public List<ToolCallSpec> getToolCalls() { return toolCalls; }
    public void setToolCalls(List<ToolCallSpec> toolCalls) { this.toolCalls = toolCalls; }
    public int getPromptTokens() { return promptTokens; }
    public void setPromptTokens(int promptTokens) { this.promptTokens = promptTokens; }
    public int getCompletionTokens() { return completionTokens; }
    public void setCompletionTokens(int completionTokens) { this.completionTokens = completionTokens; }
    public int getTotalTokens() { return totalTokens; }
    public void setTotalTokens(int totalTokens) { this.totalTokens = totalTokens; }
    public long getDurationMs() { return durationMs; }
    public void setDurationMs(long durationMs) { this.durationMs = durationMs; }
}
