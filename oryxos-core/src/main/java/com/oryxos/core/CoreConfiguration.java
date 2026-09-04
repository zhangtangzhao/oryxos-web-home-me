package com.oryxos.core;

import com.oryxos.core.agent.AgentLoader;
import com.oryxos.core.agent.ContextLoader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Core beans wired from the oryxos.root property (same property the datasource
 * URL and sandbox whitelists use, so runtime views stay consistent).
 */
@Configuration
public class CoreConfiguration {

    @Bean
    public Path workspaceRoot(@Value("${oryxos.root:.oryxos}") String root) {
        return Paths.get(root).toAbsolutePath().normalize();
    }

    @Bean
    public AgentLoader agentLoader(Path workspaceRoot) {
        return new AgentLoader(workspaceRoot);
    }

    @Bean
    public ContextLoader contextLoader(Path workspaceRoot) {
        return new ContextLoader(workspaceRoot);
    }
}
