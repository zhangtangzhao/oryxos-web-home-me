package com.oryxos.core;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Unified tool abstraction interface for OryxOS.
 * All tools — built-in, @Tool-annotated plugin, and MCP tools — implement this interface.
 */
public interface OryxTool {

    /** Unique tool name, used as the function name in LLM function calling. */
    String getName();

    /** Human-readable description, injected into the LLM prompt as tool documentation. */
    String getDescription();

    /** JSON Schema describing the tool's input parameters. */
    JsonNode getInputSchema();

    /** Execute the tool with the given JSON input and return a result. */
    ToolResult execute(JsonNode input);
}
