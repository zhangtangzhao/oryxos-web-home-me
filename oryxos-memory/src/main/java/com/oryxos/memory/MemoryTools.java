package com.oryxos.memory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.oryxos.core.OryxTool;
import com.oryxos.core.Sandbox;
import com.oryxos.core.ToolResult;

import java.nio.file.Path;

/**
 * save_memory / recall_memory built-in tools (FR-012/018). They target the
 * fixed workspace MEMORY.md — the path is static, but each execution still
 * passes the sandbox whitelist on the resolved file (first gate, FR-013) and
 * is audited by ToolExecutor like every other registry tool.
 */
public final class MemoryTools {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private MemoryTools() {
    }

    public static OryxTool saveMemoryTool(LongTermMemoryStore store, Sandbox sandbox, Path memoryFile) {
        return new SaveMemoryTool(store, sandbox, memoryFile);
    }

    public static OryxTool recallMemoryTool(LongTermMemoryStore store, Sandbox sandbox, Path memoryFile) {
        return new RecallMemoryTool(store, sandbox, memoryFile);
    }

    static final class SaveMemoryTool implements OryxTool {

        private final LongTermMemoryStore store;
        private final Sandbox sandbox;
        private final Path memoryFile;

        SaveMemoryTool(LongTermMemoryStore store, Sandbox sandbox, Path memoryFile) {
            this.store = store;
            this.sandbox = sandbox;
            this.memoryFile = memoryFile;
        }

        @Override
        public String getName() {
            return "save_memory";
        }

        @Override
        public String getDescription() {
            return "把一条事实/偏好保存到长期记忆（跨会话可用）";
        }

        @Override
        public JsonNode getInputSchema() {
            ObjectNode schema = MAPPER.createObjectNode();
            schema.put("type", "object");
            ObjectNode properties = schema.putObject("properties");
            ObjectNode content = properties.putObject("content");
            content.put("type", "string");
            content.put("description", "要记住的内容，一句话表述");
            schema.putArray("required").add("content");
            return schema;
        }

        @Override
        public ToolResult execute(JsonNode input) {
            String content = input.path("content").asText(null);
            if (content == null || content.isBlank()) {
                return ToolResult.failure("缺少必填参数 content", false);
            }
            sandbox.enforce(Sandbox.ActionType.FILE_WRITE, memoryFile.toString());
            store.append(content, MemoryScope.CORE);
            return ToolResult.success("已保存到长期记忆: " + content.trim());
        }
    }

    static final class RecallMemoryTool implements OryxTool {

        private final LongTermMemoryStore store;
        private final Sandbox sandbox;
        private final Path memoryFile;

        RecallMemoryTool(LongTermMemoryStore store, Sandbox sandbox, Path memoryFile) {
            this.store = store;
            this.sandbox = sandbox;
            this.memoryFile = memoryFile;
        }

        @Override
        public String getName() {
            return "recall_memory";
        }

        @Override
        public String getDescription() {
            return "按关键词检索长期记忆，返回命中的记忆条目";
        }

        @Override
        public JsonNode getInputSchema() {
            ObjectNode schema = MAPPER.createObjectNode();
            schema.put("type", "object");
            ObjectNode properties = schema.putObject("properties");
            ObjectNode query = properties.putObject("query");
            query.put("type", "string");
            query.put("description", "检索关键词（大小写不敏感包含匹配）");
            schema.putArray("required").add("query");
            return schema;
        }

        @Override
        public ToolResult execute(JsonNode input) {
            String query = input.path("query").asText(null);
            if (query == null || query.isBlank()) {
                return ToolResult.failure("缺少必填参数 query", false);
            }
            sandbox.enforce(Sandbox.ActionType.FILE_READ, memoryFile.toString());
            String hits = store.recallByKeyword(query);
            if (hits.isBlank()) {
                return ToolResult.success("（长期记忆中没有匹配 \"" + query.trim() + "\" 的条目）");
            }
            return ToolResult.success(hits);
        }
    }
}
