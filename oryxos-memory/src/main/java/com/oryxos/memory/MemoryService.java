package com.oryxos.memory;

import com.oryxos.core.Session;

/**
 * Unified memory facade for the ReAct loop.
 * Internally delegates session memory to SessionManager and long-term memory to LongTermMemoryStore.
 */
public interface MemoryService {

    /**
     * Get the full memory context to inject into the system prompt.
     * Includes session history and long-term memory.
     */
    String getMemoryContext(Session session);

    /**
     * Save a piece of content to long-term memory.
     */
    void saveMemory(String content, MemoryScope scope);

    /**
     * Recall long-term memory entries matching the given keyword query.
     */
    String recallMemory(String query);
}
