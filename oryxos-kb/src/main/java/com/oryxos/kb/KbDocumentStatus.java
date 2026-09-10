package com.oryxos.kb;

/** 文档状态机（data-model.md）：add → PENDING → READY | FAILED；FAILED 可经 ingest 重试。 */
public enum KbDocumentStatus {
    PENDING, READY, FAILED
}
