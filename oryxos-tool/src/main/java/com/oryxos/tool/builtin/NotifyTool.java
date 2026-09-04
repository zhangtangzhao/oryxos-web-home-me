package com.oryxos.tool.builtin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.oryxos.core.OryxTool;
import com.oryxos.core.ToolResult;
import com.oryxos.tool.notify.NotifyChannelAdapter;

/**
 * notify built-in tool: pushes a message through a registered notification
 * channel. The channel is "webhook" (MVP, clarification #1); the target URL
 * comes from the explicit url argument or the configured default
 * (oryxos.notify.webhook-url). Delivery is audited via ToolExecutor.
 */
public class NotifyTool implements OryxTool {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final NotifyChannelAdapter channel;
    private final String defaultTarget;

    public NotifyTool(NotifyChannelAdapter channel, String defaultTarget) {
        this.channel = channel;
        this.defaultTarget = defaultTarget;
    }

    @Override
    public String getName() {
        return "notify";
    }

    @Override
    public String getDescription() {
        return "向配置的通知渠道（Webhook）推送一条文本消息";
    }

    @Override
    public JsonNode getInputSchema() {
        ObjectNode schema = MAPPER.createObjectNode();
        schema.put("type", "object");
        ObjectNode properties = schema.putObject("properties");
        properties.putObject("message").put("type", "string").put("description", "要推送的消息文本");
        properties.putObject("url").put("type", "string")
                .put("description", "Webhook URL；缺省时使用系统配置的默认 Webhook");
        schema.putArray("required").add("message");
        return schema;
    }

    @Override
    public ToolResult execute(JsonNode input) {
        String message = input.path("message").asText(null);
        if (message == null || message.isBlank()) {
            return ToolResult.failure("缺少必填参数 message", false);
        }
        String url = input.path("url").asText(defaultTarget);
        if (url == null || url.isBlank()) {
            return ToolResult.failure("未提供 url 且未配置默认 Webhook（oryxos.notify.webhook-url）", false);
        }
        try {
            return ToolResult.success(channel.send(url, message));
        } catch (IllegalArgumentException e) {
            return ToolResult.failure(e.getMessage(), false);
        } catch (Exception e) {
            return ToolResult.failure("通知推送失败: " + e.getMessage(), true);
        }
    }
}
