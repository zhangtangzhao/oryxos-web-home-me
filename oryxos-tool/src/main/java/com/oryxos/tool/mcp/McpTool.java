package com.oryxos.tool.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oryxos.core.OryxTool;
import com.oryxos.core.ToolResult;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;

import java.util.Map;

/**
 * Remote MCP tool exposed as a local OryxTool. Execution forwards over MCP
 * (research R-5); audit happens in ToolExecutor because the wrapper lives in
 * the ToolRegistry like any built-in tool — no bypass path exists.
 */
public class McpTool implements OryxTool {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int MAX_RESULT_LENGTH = 8000;

    private final McpSyncClient client;
    private final String name;
    private final String description;
    private final String inputSchemaJson;

    public McpTool(McpSyncClient client, String name, String description, String inputSchemaJson) {
        this.client = client;
        this.name = name;
        this.description = description;
        this.inputSchemaJson = inputSchemaJson;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public JsonNode getInputSchema() {
        try {
            JsonNode schema = MAPPER.readTree(inputSchemaJson);
            if (schema != null && !schema.isMissingNode()) {
                return schema;
            }
        } catch (Exception ignored) {
            // fall through to empty schema
        }
        return MAPPER.createObjectNode();
    }

    @Override
    public ToolResult execute(JsonNode input) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> args = MAPPER.convertValue(input, Map.class);
            McpSchema.CallToolResult result = client.callTool(new McpSchema.CallToolRequest(remoteName(), args));
            String text = renderContent(result);
            if (Boolean.TRUE.equals(result.isError())) {
                return ToolResult.failure("MCP 工具错误: " + text, true);
            }
            return ToolResult.success(text);
        } catch (Exception e) {
            return ToolResult.failure("MCP 调用失败: " + e.getMessage(), true);
        }
    }

    /** The name registered on the remote server (local name carries the server prefix). */
    public String remoteName() {
        int idx = name.indexOf("__");
        return idx < 0 ? name : name.substring(idx + 2);
    }

    private static String renderContent(McpSchema.CallToolResult result) {
        StringBuilder sb = new StringBuilder();
        for (McpSchema.Content content : result.content()) {
            if (!sb.isEmpty()) {
                sb.append('\n');
            }
            if (content instanceof McpSchema.TextContent textContent) {
                sb.append(textContent.text());
            } else {
                sb.append('[').append(content.type()).append(']');
            }
        }
        String text = sb.toString();
        if (text.length() > MAX_RESULT_LENGTH) {
            text = text.substring(0, MAX_RESULT_LENGTH) + "…(truncated)";
        }
        return text;
    }
}
