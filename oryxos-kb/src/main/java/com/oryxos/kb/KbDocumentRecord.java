package com.oryxos.kb;

import java.time.Instant;

/** 知识库文档（data-model.md: kb_documents，存储无关视图）。 */
public record KbDocumentRecord(Long id, String kbName, String docPath, String contentHash,
                               Long sizeBytes, KbDocumentStatus status, String errorMessage,
                               Integer chunkCount, Instant ingestedAt) {
}
