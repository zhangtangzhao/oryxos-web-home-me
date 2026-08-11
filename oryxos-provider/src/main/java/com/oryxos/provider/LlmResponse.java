package com.oryxos.provider;

import java.util.List;
import java.util.Map;

/**
 * Response from an LLM provider call.
 */
public class LlmResponse {

    private String content;
    private List<ToolCall> toolCalls;
    private int promptTokens;
    private int completionTokens;
    private int totalTokens;
    private long durationMs;

    public static class ToolCall {
        private String id;
        private String name;
        private Map<String, Object> arguments;

        public ToolCall() {}
        public ToolCall(String id, String name, Map<String, Object> arguments) {
            this.id = id;
            this.name = name;
            this.arguments = arguments;
        }
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public Map<String, Object> getArguments() { return arguments; }
        public void setArguments(Map<String, Object> arguments) { this.arguments = arguments; }
    }

    public boolean hasToolCalls() {
        return toolCalls != null && !toolCalls.isEmpty();
    }

    // Getters and setters
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public List<ToolCall> getToolCalls() { return toolCalls; }
    public void setToolCalls(List<ToolCall> toolCalls) { this.toolCalls = toolCalls; }
    public int getPromptTokens() { return promptTokens; }
    public void setPromptTokens(int promptTokens) { this.promptTokens = promptTokens; }
    public int getCompletionTokens() { return completionTokens; }
    public void setCompletionTokens(int completionTokens) { this.completionTokens = completionTokens; }
    public int getTotalTokens() { return totalTokens; }
    public void setTotalTokens(int totalTokens) { this.totalTokens = totalTokens; }
    public long getDurationMs() { return durationMs; }
    public void setDurationMs(long durationMs) { this.durationMs = durationMs; }
}
