package com.oryxos.memory;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;

/**
 * Memory module wiring: MarkdownMemoryStore over the workspace and the
 * MemoryService facade; tool registration into the shared ToolRegistry is done
 * by {@link MemoryToolRegistrar} (T033).
 */
@Configuration
public class MemoryModuleConfiguration {

    @Bean
    public MarkdownMemoryStore markdownMemoryStore(@Value("${oryxos.root:.oryxos}") String workspaceRoot) {
        return new MarkdownMemoryStore(Path.of(workspaceRoot));
    }

    @Bean
    public MemoryService memoryService(MarkdownMemoryStore store) {
        return new DefaultMemoryService(store);
    }
}
