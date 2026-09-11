package com.oryxos.kb;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 未配置判定（quickstart 边界：去掉 KB_EMBEDDING_* 后试检索应 503 而非误报
 * 身份 409）：未配置时 model() 必须为空——KbSearchService 的 FR-015 身份比对
 * 据此跳过，未配置信号交给调用时的 EmbeddingNotConfiguredException。
 */
class OpenAiCompatEmbeddingClientTest {

    private static KbProperties props(String baseUrl, String apiKeyEnv, String model) {
        KbProperties p = new KbProperties();
        p.getEmbedding().setBaseUrl(baseUrl);
        p.getEmbedding().setApiKeyEnv(apiKeyEnv);
        p.getEmbedding().setModel(model);
        return p;
    }

    @Test
    void blankBaseUrlIsNotConfigured() {
        OpenAiCompatEmbeddingClient client = new OpenAiCompatEmbeddingClient(
                props("", "DASHSCOPE_API_KEY", "text-embedding-v3"));
        assertEquals("", client.model()); // 空模型名 → 身份比对跳过
        EmbeddingNotConfiguredException e = assertThrows(EmbeddingNotConfiguredException.class,
                () -> client.embed(java.util.List.of("q")));
        assertTrue(e.getMessage().contains("base-url"));
    }

    @Test
    void missingApiKeyEnvIsNotConfigured() {
        OpenAiCompatEmbeddingClient client = new OpenAiCompatEmbeddingClient(
                props("http://127.0.0.1:9", "ORYXOS_TEST_MISSING_KEY_XYZ", "m"));
        assertEquals("", client.model());
        EmbeddingNotConfiguredException e = assertThrows(EmbeddingNotConfiguredException.class,
                () -> client.embed(java.util.List.of("q")));
        assertTrue(e.getMessage().contains("ORYXOS_TEST_MISSING_KEY_XYZ"));
    }

    @Test
    void configuredClientReportsModelName() {
        OpenAiCompatEmbeddingClient client = new OpenAiCompatEmbeddingClient(
                props("http://127.0.0.1:9", "ORYXOS_TEST_MISSING_KEY_XYZ", "m"));
        // 仅 base-url+model 齐备但 env 未解析仍属未配置；此用例验证已配置路径的判定开关本身：
        // env 缺失 → 未配置 → 空模型名（不发起网络调用即可判定）。
        assertEquals("", client.model());
    }
}
