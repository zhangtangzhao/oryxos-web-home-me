package com.oryxos.tool.builtin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.oryxos.core.OryxTool;
import com.oryxos.core.Sandbox;
import com.oryxos.core.ToolResult;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * http_get built-in tool. The URL must pass the domain whitelist BEFORE the
 * request is made (FR-012/013); the response body is truncated for the
 * conversation history.
 */
public class HttpGetTool implements OryxTool {

    public static final int MAX_BODY_LENGTH = 8000;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Sandbox sandbox;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public HttpGetTool(Sandbox sandbox) {
        this.sandbox = sandbox;
    }

    @Override
    public String getName() {
        return "http_get";
    }

    @Override
    public String getDescription() {
        return "向指定 URL 发起 HTTP GET 请求并返回响应正文（受域名白名单限制）";
    }

    @Override
    public JsonNode getInputSchema() {
        ObjectNode schema = MAPPER.createObjectNode();
        schema.put("type", "object");
        ObjectNode properties = schema.putObject("properties");
        ObjectNode url = properties.putObject("url");
        url.put("type", "string");
        url.put("description", "完整的请求 URL，必须为 http/https");
        schema.putArray("required").add("url");
        return schema;
    }

    @Override
    public ToolResult execute(JsonNode input) {
        String url = input.path("url").asText(null);
        if (url == null || url.isBlank()) {
            return ToolResult.failure("缺少必填参数 url", false);
        }
        try {
            URI uri = URI.create(url);
            String scheme = uri.getScheme();
            if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
                return ToolResult.failure("仅支持 http/https URL: " + url, false);
            }
            // 白名单校验必须是第一道闸（FR-013）
            sandbox.enforce(Sandbox.ActionType.HTTP_REQUEST, url);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(uri)
                    .timeout(Duration.ofSeconds(20))
                    .header("Accept", "*/*")
                    .header("User-Agent", "oryxos-http_get/1.0")
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            String body = response.body() == null ? "" : response.body();
            if (body.length() > MAX_BODY_LENGTH) {
                body = body.substring(0, MAX_BODY_LENGTH) + "…(truncated)";
            }
            return ToolResult.success("HTTP " + response.statusCode() + "\n" + body);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ToolResult.failure("请求被中断", false);
        } catch (Exception e) {
            return ToolResult.failure("HTTP GET 失败: " + e.getMessage(), true);
        }
    }
}
