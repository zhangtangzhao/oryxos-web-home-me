package com.oryxos.core.llm;

/**
 * Tool schema exposed to the LLM for function calling. Schema-only — execution
 * stays in OryxOS's own ToolExecutor, never in the provider library (constitution II).
 */
public class ToolSpec {

    private String name;
    private String description;
    private String inputSchemaJson;

    public ToolSpec() {}

    public ToolSpec(String name, String description, String inputSchemaJson) {
        this.name = name;
        this.description = description;
        this.inputSchemaJson = inputSchemaJson;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getInputSchemaJson() { return inputSchemaJson; }
    public void setInputSchemaJson(String inputSchemaJson) { this.inputSchemaJson = inputSchemaJson; }
}
