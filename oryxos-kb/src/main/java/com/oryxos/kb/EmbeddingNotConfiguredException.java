package com.oryxos.kb;

/** 嵌入服务未配置 → REST 503 EMBEDDING_NOT_CONFIGURED，message 点名缺失的配置项。 */
public class EmbeddingNotConfiguredException extends RuntimeException {

    public EmbeddingNotConfiguredException(String message) {
        super(message);
    }
}
