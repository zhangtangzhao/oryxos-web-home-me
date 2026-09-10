package com.oryxos.kb;

/** 摄取产出的分段（embeddingJson = JSON float 数组，null 表示嵌入失败待重试）。 */
public record KbChunkData(int ordinal, String headingPath, String content, String embeddingJson) {
}
