package com.oryxos.kb;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * OpenAI 兼容 /embeddings 客户端（research D2）：同步 JDK HttpClient + Jackson，
 * 批 16 逐批调用；未配置（base-url 留空 / api-key-env 未解析）在调用时点名
 * 缺失配置项抛 EmbeddingNotConfiguredException——启动不因缺配置崩溃。
 */
public class OpenAiCompatEmbeddingClient implements EmbeddingClient {

    static final int BATCH_SIZE = 16;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final KbProperties.Embedding cfg;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public OpenAiCompatEmbeddingClient(KbProperties properties) {
        this.cfg = properties.getEmbedding();
    }

    @Override
    public String model() {
        // 未配置时返回空：FR-015 身份比对（KbSearchService）据此跳过，
        // 未配置信号由 embed() 的 EmbeddingNotConfiguredException 点名给出。
        return isConfigured() ? cfg.getModel() : "";
    }

    @Override
    public int dimensions() {
        return cfg.getDimensions();
    }

    @Override
    public List<float[]> embed(List<String> inputs) {
        requireConfigured();
        List<float[]> out = new ArrayList<>(inputs.size());
        for (int from = 0; from < inputs.size(); from += BATCH_SIZE) {
            out.addAll(callBatch(inputs.subList(from, Math.min(from + BATCH_SIZE, inputs.size()))));
        }
        return out;
    }

    /** 未配置 = base-url 留空，或 api-key-env 指向的环境变量缺失——点名缺失项。 */
    private void requireConfigured() {
        if (cfg.getBaseUrl() == null || cfg.getBaseUrl().isBlank()) {
            throw new EmbeddingNotConfiguredException(
                    "知识库嵌入服务未配置：缺少 oryxos.kb.embedding.base-url（oryxos.kb.embedding.*）");
        }
        if (cfg.getApiKeyEnv() == null || cfg.getApiKeyEnv().isBlank()) {
            throw new EmbeddingNotConfiguredException(
                    "知识库嵌入服务未配置：缺少 oryxos.kb.embedding.api-key-env");
        }
        String key = System.getenv(cfg.getApiKeyEnv());
        if (key == null || key.isBlank()) {
            throw new EmbeddingNotConfiguredException(
                    "知识库嵌入服务未配置：环境变量 " + cfg.getApiKeyEnv() + " 未设置（api-key-env=" + cfg.getApiKeyEnv() + "）");
        }
        if (cfg.getModel() == null || cfg.getModel().isBlank()) {
            throw new EmbeddingNotConfiguredException(
                    "知识库嵌入服务未配置：缺少 oryxos.kb.embedding.model");
        }
    }

    private boolean isConfigured() {
        if (cfg.getBaseUrl() == null || cfg.getBaseUrl().isBlank()) return false;
        if (cfg.getApiKeyEnv() == null || cfg.getApiKeyEnv().isBlank()) return false;
        String key = System.getenv(cfg.getApiKeyEnv());
        if (key == null || key.isBlank()) return false;
        return cfg.getModel() != null && !cfg.getModel().isBlank();
    }

    private List<float[]> callBatch(List<String> batch) {
        ObjectNode body = MAPPER.createObjectNode();
        body.put("model", cfg.getModel());
        ArrayNode input = body.putArray("input");
        batch.forEach(input::add);

        String url = cfg.getBaseUrl().replaceAll("/+$", "") + "/embeddings";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(60))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + System.getenv(cfg.getApiKeyEnv()))
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();
        try {
            HttpResponse<String> resp = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                throw new EmbeddingUnavailableException(
                        "嵌入服务调用失败: HTTP " + resp.statusCode() + " (" + url + ")");
            }
            JsonNode data = MAPPER.readTree(resp.body()).path("data");
            float[][] byIndex = new float[batch.size()][];
            for (JsonNode item : data) {
                int idx = item.path("index").asInt(-1);
                if (idx < 0 || idx >= batch.size()) {
                    throw new EmbeddingUnavailableException("嵌入服务响应 index 越界: " + idx);
                }
                JsonNode vec = item.path("embedding");
                float[] v = new float[vec.size()];
                for (int i = 0; i < vec.size(); i++) {
                    v[i] = (float) vec.get(i).asDouble();
                }
                byIndex[idx] = v;
            }
            List<float[]> out = new ArrayList<>(batch.size());
            for (float[] v : byIndex) {
                if (v == null) {
                    throw new EmbeddingUnavailableException("嵌入服务响应缺少向量（index 断档）");
                }
                out.add(v);
            }
            return out;
        } catch (IOException e) {
            throw new EmbeddingUnavailableException("嵌入服务不可达: " + url, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new EmbeddingUnavailableException("嵌入服务调用被中断", e);
        }
    }
}
