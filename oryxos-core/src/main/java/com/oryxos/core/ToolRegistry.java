package com.oryxos.core;

import java.util.List;

/**
 * Central registry for all tools available to agents.
 * Tools are registered at startup and filtered per-agent based on Profile configuration.
 */
public interface ToolRegistry {

    /** Register a tool. */
    void register(OryxTool tool);

    /** Get a tool by name. */
    OryxTool get(String name);

    /** List all registered tools. */
    List<OryxTool> listAll();

    /** List tools filtered by the given names (from Profile). */
    List<OryxTool> listForAgent(List<String> toolNames);
}
