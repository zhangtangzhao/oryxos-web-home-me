package com.oryxos.tool.builtin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.oryxos.core.OryxTool;
import com.oryxos.core.Sandbox;
import com.oryxos.core.ToolResult;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

/**
 * list_dir built-in tool: directory read counts as FILE_READ against the path
 * whitelist (FR-012/013).
 */
public class ListDirTool implements OryxTool {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final int MAX_ENTRIES = 500;

    private final Sandbox sandbox;

    public ListDirTool(Sandbox sandbox) {
        this.sandbox = sandbox;
    }

    @Override
    public String getName() {
        return "list_dir";
    }

    @Override
    public String getDescription() {
        return "列出指定目录的条目（受路径白名单限制）";
    }

    @Override
    public JsonNode getInputSchema() {
        ObjectNode schema = MAPPER.createObjectNode();
        schema.put("type", "object");
        ObjectNode properties = schema.putObject("properties");
        properties.putObject("path").put("type", "string").put("description", "目标目录路径，默认当前目录");
        return schema;
    }

    @Override
    public ToolResult execute(JsonNode input) {
        String path = input.path("path").asText(".");
        // 白名单校验必须是第一道闸（FR-013）
        sandbox.enforce(Sandbox.ActionType.FILE_READ, path);
        try (Stream<Path> entries = Files.list(Path.of(path))) {
            StringBuilder sb = new StringBuilder();
            long count = entries
                    .sorted()
                    .limit(MAX_ENTRIES)
                    .mapToLong(entry -> {
                        String type = Files.isDirectory(entry) ? "d" : "f";
                        if (!sb.isEmpty()) {
                            sb.append('\n');
                        }
                        sb.append(type).append(' ').append(entry.getFileName());
                        return 1;
                    })
                    .sum();
            return ToolResult.success(sb.isEmpty() ? "（空目录）" : sb.toString());
        } catch (Exception e) {
            return ToolResult.failure("列目录失败: " + e.getMessage(), true);
        }
    }
}
