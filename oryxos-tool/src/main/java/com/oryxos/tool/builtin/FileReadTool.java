package com.oryxos.tool.builtin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.oryxos.core.OryxTool;
import com.oryxos.core.Sandbox;
import com.oryxos.core.ToolResult;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * read_file built-in tool: path must pass the file whitelist BEFORE any read
 * (FR-012/013). Content truncated for conversation-history economy.
 */
public class FileReadTool implements OryxTool {

    public static final int MAX_CONTENT_LENGTH = 20000;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Sandbox sandbox;

    public FileReadTool(Sandbox sandbox) {
        this.sandbox = sandbox;
    }

    @Override
    public String getName() {
        return "read_file";
    }

    @Override
    public String getDescription() {
        return "读取指定路径的文本文件内容（受路径白名单限制）";
    }

    @Override
    public JsonNode getInputSchema() {
        ObjectNode schema = MAPPER.createObjectNode();
        schema.put("type", "object");
        ObjectNode properties = schema.putObject("properties");
        ObjectNode path = properties.putObject("path");
        path.put("type", "string");
        path.put("description", "要读取的文件路径");
        schema.putArray("required").add("path");
        return schema;
    }

    @Override
    public ToolResult execute(JsonNode input) {
        String path = input.path("path").asText(null);
        if (path == null || path.isBlank()) {
            return ToolResult.failure("缺少必填参数 path", false);
        }
        // 白名单校验必须是第一道闸（FR-013）
        sandbox.enforce(Sandbox.ActionType.FILE_READ, path);
        try {
            Path file = Path.of(path);
            if (!Files.isRegularFile(file)) {
                return ToolResult.failure("文件不存在或不是常规文件: " + path, false);
            }
            String content = Files.readString(file);
            if (content.length() > MAX_CONTENT_LENGTH) {
                content = content.substring(0, MAX_CONTENT_LENGTH) + "\n…(truncated)";
            }
            return ToolResult.success(content);
        } catch (Exception e) {
            return ToolResult.failure("读取失败: " + e.getMessage(), true);
        }
    }
}
