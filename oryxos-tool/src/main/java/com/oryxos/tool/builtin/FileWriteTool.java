package com.oryxos.tool.builtin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.oryxos.core.OryxTool;
import com.oryxos.core.Sandbox;
import com.oryxos.core.ToolResult;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * write_file built-in tool: target path must pass the file whitelist BEFORE
 * any write (FR-012/013). Parent directories are created as needed.
 */
public class FileWriteTool implements OryxTool {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Sandbox sandbox;

    public FileWriteTool(Sandbox sandbox) {
        this.sandbox = sandbox;
    }

    @Override
    public String getName() {
        return "write_file";
    }

    @Override
    public String getDescription() {
        return "将文本内容写入指定路径的文件（受路径白名单限制，默认覆盖，append=true 时追加）";
    }

    @Override
    public JsonNode getInputSchema() {
        ObjectNode schema = MAPPER.createObjectNode();
        schema.put("type", "object");
        ObjectNode properties = schema.putObject("properties");
        properties.putObject("path").put("type", "string").put("description", "目标文件路径");
        properties.putObject("content").put("type", "string").put("description", "要写入的文本内容");
        properties.putObject("append").put("type", "boolean").put("description", "是否追加而非覆盖，默认 false");
        schema.putArray("required").add("path").add("content");
        return schema;
    }

    @Override
    public ToolResult execute(JsonNode input) {
        String path = input.path("path").asText(null);
        String content = input.path("content").asText("");
        boolean append = input.path("append").asBoolean(false);
        if (path == null || path.isBlank()) {
            return ToolResult.failure("缺少必填参数 path", false);
        }
        // 白名单校验必须是第一道闸（FR-013）
        sandbox.enforce(Sandbox.ActionType.FILE_WRITE, path);
        try {
            Path file = Path.of(path);
            if (file.getParent() != null) {
                Files.createDirectories(file.getParent());
            }
            if (append) {
                Files.writeString(file, content, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } else {
                Files.writeString(file, content, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            }
            return ToolResult.success("已写入 " + file.toAbsolutePath() + "（" + content.length() + " 字符"
                    + (append ? "，追加" : "") + "）");
        } catch (Exception e) {
            return ToolResult.failure("写入失败: " + e.getMessage(), true);
        }
    }
}
