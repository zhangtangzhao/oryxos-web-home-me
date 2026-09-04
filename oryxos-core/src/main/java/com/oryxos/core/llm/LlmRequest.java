package com.oryxos.core.llm;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A single LLM completion request. Messages are OpenAI-compatible role/content maps
 * (including assistant tool_calls and tool role results) so any provider adapter
 * can translate them.
 */
public class LlmRequest {

    private String providerName;
    private String model;
    private Float temperature;
    private List<Map<String, Object>> messages = new ArrayList<>();
    private List<ToolSpec> tools = new ArrayList<>();

    public String getProviderName() { return providerName; }
    public void setProviderName(String providerName) { this.providerName = providerName; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public Float getTemperature() { return temperature; }
    public void setTemperature(Float temperature) { this.temperature = temperature; }
    public List<Map<String, Object>> getMessages() { return messages; }
    public void setMessages(List<Map<String, Object>> messages) { this.messages = messages; }
    public List<ToolSpec> getTools() { return tools; }
    public void setTools(List<ToolSpec> tools) { this.tools = tools; }
}
