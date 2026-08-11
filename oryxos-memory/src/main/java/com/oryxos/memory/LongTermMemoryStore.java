package com.oryxos.memory;

/**
 * Pluggable long-term memory backend.
 * Core phase provides MarkdownMemoryStore (default, file-based).
 * Upgrade paths: SqliteMemoryStore, Mem0MemoryStore.
 */
public interface LongTermMemoryStore {

    /** Append content to the specified memory partition. */
    void append(String content, MemoryScope scope);

    /** Load memory: core partition in full + truncated archival partition. */
    String load();

    /** Keyword-search within the archival partition only. */
    String recallByKeyword(String query);
}
