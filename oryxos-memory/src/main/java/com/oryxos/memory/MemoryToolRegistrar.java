package com.oryxos.memory;

import com.oryxos.core.OryxTool;
import com.oryxos.core.Sandbox;
import com.oryxos.core.ToolRegistry;
import org.springframework.stereotype.Component;

/**
 * Registers save_memory / recall_memory into the shared ToolRegistry at
 * startup. Because registration happens into the registry, every execution
 * flows through ToolExecutor — sandbox + audit are mandatory (FR-011).
 */
@Component
public class MemoryToolRegistrar {

    public MemoryToolRegistrar(ToolRegistry registry, MarkdownMemoryStore store, Sandbox sandbox) {
        OryxTool save = MemoryTools.saveMemoryTool(store, sandbox, store.memoryFile());
        OryxTool recall = MemoryTools.recallMemoryTool(store, sandbox, store.memoryFile());
        registry.register(save);
        registry.register(recall);
    }
}
