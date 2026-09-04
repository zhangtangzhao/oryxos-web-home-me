package com.oryxos.core.prompt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oryxos.core.Message;
import com.oryxos.core.OryxTool;
import com.oryxos.core.Profile;
import com.oryxos.core.Session;
import com.oryxos.core.agent.ContextLoader;
import com.oryxos.core.llm.ToolSpec;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds the LLM payload. System prompt order follows research R-7:
 * AGENT.md body -> AGENTS.md -> SOUL.md -> USER.md -> Skill metadata ->
 * MEMORY.md view -> current datetime -> tool list; then the (truncated)
 * conversation history.
 */
@Component
public class PromptBuilder {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ContextLoader contextLoader;

    public PromptBuilder(ContextLoader contextLoader) {
        this.contextLoader = contextLoader;
    }

    public List<Map<String, Object>> build(Session session, Profile profile, List<OryxTool> tools) {
        List<Map<String, Object>> payload = new ArrayList<>();
        payload.add(map("system", systemPrompt(profile, tools)));
        payload.addAll(historyMessages(session, profile));
        return payload;
    }

    public String systemPrompt(Profile profile, List<OryxTool> tools) {
        StringBuilder sb = new StringBuilder();

        Profile.Identity identity = profile.getIdentity();
        if (identity != null && identity.getPrompt() != null && !identity.getPrompt().isBlank()) {
            sb.append(identity.getPrompt().trim()).append("\n\n");
        }

        String body = contextLoader.agentBody(profile);
        if (!body.isBlank()) {
            sb.append(body).append("\n\n");
        }

        String bootstrap = contextLoader.bootstrapText(profile);
        if (!bootstrap.isBlank()) {
            sb.append(bootstrap).append("\n\n");
        }

        String skills = contextLoader.skillMetadata(profile);
        if (!skills.isBlank()) {
            sb.append("## 可用 Skills\n\n").append(skills).append("\n\n");
        }

        String memory = contextLoader.memoryView();
        if (!memory.isBlank()) {
            sb.append("## 长期记忆（MEMORY.md）\n\n").append(memory).append("\n\n");
        }

        sb.append("当前日期时间：")
                .append(LocalDateTime.now(ZoneId.systemDefault())
                        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")))
                .append("\n\n");

        if (!tools.isEmpty()) {
            sb.append("## 可用工具\n\n");
            for (OryxTool tool : tools) {
                sb.append("- ").append(tool.getName()).append(": ")
                        .append(tool.getDescription() == null ? "" : tool.getDescription())
                        .append('\n');
            }
        }
        return sb.toString().trim();
    }

    public List<ToolSpec> toolSpecs(List<OryxTool> tools) {
        List<ToolSpec> specs = new ArrayList<>();
        for (OryxTool tool : tools) {
            try {
                specs.add(new ToolSpec(tool.getName(), tool.getDescription(),
                        MAPPER.writeValueAsString(tool.getInputSchema())));
            } catch (Exception e) {
                throw new IllegalStateException("工具 schema 序列化失败: " + tool.getName(), e);
            }
        }
        return specs;
    }

    /** History as OpenAI-compatible role maps, truncated to max_history_turns user turns. */
    List<Map<String, Object>> historyMessages(Session session, Profile profile) {
        List<Message> messages = session.getMessages();
        int maxTurns = profile.getSettings() != null
                ? profile.getSettings().getMaxHistoryTurns() : 20;

        int start = 0;
        int userTurns = 0;
        for (int i = messages.size() - 1; i >= 0; i--) {
            if (messages.get(i).getRole() == Message.Role.USER) {
                userTurns++;
                if (userTurns > maxTurns) {
                    start = i + 1;
                    break;
                }
            }
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (int i = start; i < messages.size(); i++) {
            Message m = messages.get(i);
            switch (m.getRole()) {
                case USER -> result.add(map("user", m.getContent()));
                case ASSISTANT -> {
                    Map<String, Object> map = map("assistant", m.getContent());
                    if (m.getToolCallsJson() != null && !m.getToolCallsJson().isBlank()) {
                        map.put("tool_calls_json", m.getToolCallsJson());
                    }
                    result.add(map);
                }
                case TOOL -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("role", "tool");
                    map.put("content", m.getContent());
                    map.put("name", m.getToolName());
                    map.put("tool_call_id", m.getToolCallId());
                    result.add(map);
                }
                case SYSTEM -> result.add(map("system", m.getContent()));
            }
        }
        return result;
    }

    private static Map<String, Object> map(String role, String content) {
        Map<String, Object> map = new HashMap<>();
        map.put("role", role);
        map.put("content", content == null ? "" : content);
        return map;
    }
}
