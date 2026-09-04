package com.oryxos.tool;

import com.oryxos.core.Sandbox;
import com.oryxos.core.ToolRegistry;
import com.oryxos.tool.builtin.FileReadTool;
import com.oryxos.tool.builtin.FileWriteTool;
import com.oryxos.tool.builtin.HttpGetTool;
import com.oryxos.tool.builtin.HttpPostTool;
import com.oryxos.tool.builtin.ListDirTool;
import com.oryxos.tool.builtin.NotifyTool;
import com.oryxos.tool.builtin.ShellTool;
import com.oryxos.tool.notify.NotifyChannelAdapter;
import com.oryxos.tool.notify.WebhookNotifyChannelAdapter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashSet;
import java.util.Set;

/**
 * Tool module wiring: registry, whitelist sandbox, built-in tools, and the
 * webhook notify channel. Default file whitelist = the workspace root
 * (minimal privilege, FR-013).
 */
@Configuration
@EnableConfigurationProperties(SandboxProperties.class)
public class ToolModuleConfiguration {

    @Bean
    public Sandbox sandbox(SandboxProperties properties,
                           @Value("${oryxos.root:.oryxos}") String workspaceRoot) {
        Set<String> paths = new HashSet<>(properties.getFile().getAllowedPaths());
        Set<String> commands = new HashSet<>(properties.getShell().getAllowedCommands());
        Set<String> domains = new HashSet<>(properties.getHttp().getAllowedDomains());
        if (paths.isEmpty()) {
            paths.add(workspaceRoot);
        }
        return new WhitelistSandbox(paths, commands, domains);
    }

    @Bean
    public NotifyChannelAdapter webhookNotifyChannelAdapter(Sandbox sandbox) {
        return new WebhookNotifyChannelAdapter(sandbox);
    }

    @Bean
    public ToolRegistry toolRegistry(Sandbox sandbox, NotifyChannelAdapter webhookNotifyChannelAdapter,
                                     @Value("${oryxos.notify.webhook-url:}") String defaultWebhookUrl) {
        InMemoryToolRegistry registry = new InMemoryToolRegistry();
        registry.register(new FileReadTool(sandbox));
        registry.register(new FileWriteTool(sandbox));
        registry.register(new ListDirTool(sandbox));
        registry.register(new ShellTool(sandbox));
        registry.register(new HttpGetTool(sandbox));
        registry.register(new HttpPostTool(sandbox));
        registry.register(new NotifyTool(webhookNotifyChannelAdapter, defaultWebhookUrl));
        return registry;
    }
}
