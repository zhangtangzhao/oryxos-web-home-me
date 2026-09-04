package com.oryxos.tool.notify;

import com.oryxos.core.Sandbox;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Generic webhook notification channel (clarification #1): POSTs the message
 * text to a configured URL. The URL must pass the domain whitelist before the
 * request is made (FR-012/013) and every call is audited by ToolExecutor via
 * the notify tool.
 */
public class WebhookNotifyChannelAdapter implements NotifyChannelAdapter {

    public static final int MAX_BODY_LENGTH = 8000;

    private final Sandbox sandbox;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public WebhookNotifyChannelAdapter(Sandbox sandbox) {
        this.sandbox = sandbox;
    }

    @Override
    public String getName() {
        return "webhook";
    }

    @Override
    public String send(String target, String message) throws Exception {
        if (target == null || target.isBlank()) {
            throw new IllegalArgumentException("缺少 Webhook URL");
        }
        URI uri = URI.create(target);
        String scheme = uri.getScheme();
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
            throw new IllegalArgumentException("仅支持 http/https Webhook URL: " + target);
        }
        // 白名单校验必须是第一道闸（FR-013）
        sandbox.enforce(Sandbox.ActionType.HTTP_REQUEST, target);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri)
                .timeout(Duration.ofSeconds(20))
                .header("Content-Type", "text/plain; charset=utf-8")
                .header("User-Agent", "oryxos-notify/1.0")
                .POST(HttpRequest.BodyPublishers.ofString(message == null ? "" : message))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        String body = response.body() == null ? "" : response.body();
        if (body.length() > MAX_BODY_LENGTH) {
            body = body.substring(0, MAX_BODY_LENGTH) + "…(truncated)";
        }
        if (response.statusCode() >= 400) {
            throw new IllegalStateException("Webhook 返回 HTTP " + response.statusCode() + ": " + body);
        }
        return "HTTP " + response.statusCode() + (body.isBlank() ? "" : "\n" + body);
    }
}
