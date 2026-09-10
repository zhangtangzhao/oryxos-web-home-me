package com.oryxos.kb;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.oryxos.core.OryxTool;
import com.oryxos.core.Profile;
import com.oryxos.core.ToolResult;
import com.oryxos.core.tool.ToolContext;

import java.util.List;

/**
 * kb_search 工具（contracts/agent-tools.md，模式同 MemoryTools）。工具为全局
 * 单例，绑定信息经 {@link ToolContext} 取当前 Agent 的 Profile；绑定过滤在
 * {@link KbSearchService} 强制（越权防护不依赖模型自觉）。无沙箱动作——检索
 * 只读库内数据，但执行仍经 ToolExecutor 落审计（宪法 V）。
 */
public final class KbTools {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private KbTools() {
    }

    public static OryxTool kbSearchTool(KbSearchService service) {
        return new KbSearchTool(service);
    }

    public static OryxTool kbOverviewTool(KbStore store, KbSearchService searchService) {
        return new KbOverviewTool(store, searchService);
    }

    /** kb_overview（US5）：绑定解析复用 kb_search 规则；只读，不触嵌入服务。 */
    static final class KbOverviewTool implements OryxTool {

        private final KbStore store;
        private final KbSearchService searchService;

        KbOverviewTool(KbStore store, KbSearchService searchService) {
            this.store = store;
            this.searchService = searchService;
        }

        @Override
        public String getName() {
            return "kb_overview";
        }

        @Override
        public String getDescription() {
            return "列出知识库的文档清单与标题大纲，用于了解库里有什么、选择检索方向。";
        }

        @Override
        public JsonNode getInputSchema() {
            ObjectNode schema = MAPPER.createObjectNode();
            schema.put("type", "object");
            ObjectNode properties = schema.putObject("properties");
            ObjectNode kb = properties.putObject("kb");
            kb.put("type", "string");
            kb.put("description", "知识库名；省略时若仅绑定一个库则使用该库");
            return schema;
        }

        @Override
        public ToolResult execute(JsonNode input) {
            KbSearchService.Resolved resolved = searchService.resolveKb(
                    boundKbs(), input.path("kb").asText(null));
            if (!resolved.ok()) {
                return ToolResult.failure(resolved.errorText(), false);
            }
            String kb = resolved.name();
            List<KbOverviewEntry> entries;
            try {
                entries = store.overview(kb);
            } catch (KbNotFoundException e) {
                return ToolResult.failure("知识库不可用: " + kb, false);
            }

            StringBuilder text = new StringBuilder();
            if (entries.isEmpty()) {
                text.append("kb=").append(kb).append(" 暂无文档");
            } else {
                text.append("kb=").append(kb).append(" 文档 ").append(entries.size()).append(" 篇:");
                for (KbOverviewEntry e : entries) {
                    text.append("\n- ").append(e.docPath());
                    if (e.headings() != null) {
                        // headings 为完整标题路径（REST 同形），逐行 "# path" 呈现大纲
                        for (String h : e.headings()) {
                            text.append("\n  # ").append(h);
                        }
                    }
                }
            }

            ObjectNode audit = MAPPER.createObjectNode();
            audit.put("kb", kb);
            audit.put("document_count", entries.size());
            ArrayNode documents = audit.putArray("documents");
            for (KbOverviewEntry e : entries) {
                ObjectNode d = documents.addObject();
                d.put("doc_path", e.docPath());
                ArrayNode headings = d.putArray("headings");
                if (e.headings() != null) {
                    e.headings().forEach(headings::add);
                }
            }
            return ToolResult.successWithAudit(text.toString(), audit.toString());
        }
    }

    static final class KbSearchTool implements OryxTool {

        private final KbSearchService service;

        KbSearchTool(KbSearchService service) {
            this.service = service;
        }

        @Override
        public String getName() {
            return "kb_search";
        }

        @Override
        public String getDescription() {
            return "在已绑定的知识库中检索相关内容片段。返回带来源引用"
                    + "（知识库名、文档路径、标题路径、序号）的候选列表；零结果时明确返回未找到。";
        }

        @Override
        public JsonNode getInputSchema() {
            ObjectNode schema = MAPPER.createObjectNode();
            schema.put("type", "object");
            ObjectNode properties = schema.putObject("properties");
            ObjectNode query = properties.putObject("query");
            query.put("type", "string");
            query.put("description", "检索问题或关键词");
            ObjectNode kb = properties.putObject("kb");
            kb.put("type", "string");
            kb.put("description", "知识库名；省略时若仅绑定一个库则使用该库");
            ObjectNode topK = properties.putObject("top_k");
            topK.put("type", "integer");
            topK.put("description", "返回条数，默认 5，上限 20");
            schema.putArray("required").add("query");
            return schema;
        }

        @Override
        public ToolResult execute(JsonNode input) {
            String query = input.path("query").asText(null);
            if (query == null || query.isBlank()) {
                return ToolResult.failure("缺少必填参数 query", false);
            }
            Integer topK = input.hasNonNull("top_k") && input.path("top_k").isInt()
                    ? input.path("top_k").asInt() : null;
            KbSearchService.SearchResult result = service.search(
                    boundKbs(), input.path("kb").asText(null), query.trim(), topK);
            if (result.error()) {
                return ToolResult.failure(result.errorMessage(), false);
            }
            return ToolResult.successWithAudit(result.text(), result.auditJson());
        }
    }

    /** 当前 Agent 的知识库绑定；无上下文（如离线测试直调）视为未绑定。 */
    static List<String> boundKbs() {
        Profile profile = ToolContext.current();
        return profile == null ? List.of() : profile.getKnowledgeBases();
    }
}
