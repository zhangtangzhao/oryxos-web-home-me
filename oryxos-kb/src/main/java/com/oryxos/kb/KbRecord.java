package com.oryxos.kb;

import java.time.Instant;

/** 知识库元数据（存储无关的端口侧视图，data-model.md: kb_knowledge_bases）。 */
public record KbRecord(String name, String description, String embeddingModel,
                       Integer embeddingDimensions, Instant createdAt, Instant updatedAt) {
}
