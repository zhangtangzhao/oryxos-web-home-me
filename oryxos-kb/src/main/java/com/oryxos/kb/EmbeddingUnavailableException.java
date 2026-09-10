package com.oryxos.kb;

/** 已配置但嵌入服务调用失败 → REST 502 EMBEDDING_UNAVAILABLE；检索侧降级为关键词路。 */
public class EmbeddingUnavailableException extends RuntimeException {

    public EmbeddingUnavailableException(String message) {
        super(message);
    }

    public EmbeddingUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
