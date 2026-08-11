package com.oryxos.tool;

import com.oryxos.core.OryxTool;
import com.oryxos.core.ToolRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * In-memory implementation of ToolRegistry.
 * Thread-safe for virtual-thread concurrent access.
 */
public class InMemoryToolRegistry implements ToolRegistry {

    private final Map<String, OryxTool> tools = new ConcurrentHashMap<>();

    @Override
    public void register(OryxTool tool) {
        tools.put(tool.getName(), tool);
    }

    @Override
    public OryxTool get(String name) {
        return tools.get(name);
    }

    @Override
    public List<OryxTool> listAll() {
        return new ArrayList<>(tools.values());
    }

    @Override
    public List<OryxTool> listForAgent(List<String> toolNames) {
        if (toolNames == null || toolNames.isEmpty()) {
            return new ArrayList<>();
        }
        return toolNames.stream()
                .map(tools::get)
                .filter(t -> t != null)
                .collect(Collectors.toList());
    }
}
