package com.oryxos.web;

import com.oryxos.core.OryxTool;
import com.oryxos.core.ToolRegistry;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Available tools (contract §8): name / type / description. */
@RestController
@RequestMapping("/api/v1/tools")
public class ToolApiController {

    private final ToolRegistry toolRegistry;

    public ToolApiController(ToolRegistry toolRegistry) {
        this.toolRegistry = toolRegistry;
    }

    @GetMapping
    public ApiResponse<Map<String, Object>> listTools() {
        List<Map<String, Object>> tools = new ArrayList<>();
        for (OryxTool tool : toolRegistry.listAll()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("name", tool.getName());
            entry.put("type", classify(tool.getName()));
            entry.put("description", tool.getDescription() == null ? "" : tool.getDescription());
            tools.add(entry);
        }
        return ApiResponse.ok(Map.of("tools", tools));
    }

    /** builtin|memory|http|shell|mcp (contract §8); MCP tools carry the server__tool name. */
    private static String classify(String name) {
        if (name.contains("__")) {
            return "mcp";
        }
        return switch (name) {
            case "save_memory", "recall_memory" -> "memory";
            case "http_get", "http_post" -> "http";
            case "shell" -> "shell";
            default -> "builtin";
        };
    }
}
