package com.oryxos.core.llm;

import java.util.Map;

/** A tool call requested by the LLM. */
public class ToolCallSpec {

    private String id;
    private String name;
    private Map<String, Object> arguments;

    public ToolCallSpec() {}

    public ToolCallSpec(String id, String name, Map<String, Object> arguments) {
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
