package com.oryxos.memory;

import com.oryxos.core.Session;
import com.oryxos.core.agent.ContextLoader;

/**
 * Default MemoryService facade: session memory stays in the ReAct conversation
 * history (SessionService), long-term memory delegates to the store. The
 * injection view follows FR-018 — full MEMORY.md truncated to
 * {@link ContextLoader#MEMORY_VIEW_LIMIT}; ContextLoader/PromptBuilder read the
 * same file so both share this one view rule.
 */
public class DefaultMemoryService implements MemoryService {

    private final LongTermMemoryStore store;

    public DefaultMemoryService(LongTermMemoryStore store) {
        this.store = store;
    }

    @Override
    public String getMemoryContext(Session session) {
        String full = store.load();
        if (full.isBlank()) {
            return "";
        }
        return ContextLoader.truncate(full, ContextLoader.MEMORY_VIEW_LIMIT);
    }

    @Override
    public void saveMemory(String content, MemoryScope scope) {
        store.append(content, scope);
    }

    @Override
    public String recallMemory(String query) {
        return store.recallByKeyword(query);
    }
}
