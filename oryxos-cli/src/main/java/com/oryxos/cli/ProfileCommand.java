package com.oryxos.cli;

import com.oryxos.core.Profile;
import com.oryxos.core.agent.AgentLoader;
import com.oryxos.core.workspace.WorkspaceInitializer;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.util.concurrent.Callable;

/**
 * oryxos profile create|list|show|delete — Agent profile management over
 * .oryxos/agents/ directories (one directory = one Agent, constitution VII).
 */
@Command(name = "profile",
         description = "Manage agent profiles",
         subcommands = {
             ProfileCommand.CreateCommand.class,
             ProfileCommand.ListCommand.class,
             ProfileCommand.ShowCommand.class,
             ProfileCommand.DeleteCommand.class
         })
public class ProfileCommand {

    private static final String NAME_PATTERN = "^[A-Za-z0-9][A-Za-z0-9_-]*$";

    @Command(name = "create", description = "Create a new agent profile")
    public static class CreateCommand implements Callable<Integer> {
        @Parameters(description = "Profile name")
        private String name;

        @Override
        public Integer call() {
            if (name == null || !name.matches(NAME_PATTERN)) {
                System.err.println("非法名称: " + name + "（仅允许字母数字、中划线、下划线，且以字母数字开头）");
                return 1;
            }
            if (!WorkspaceInitializer.isInitialized(WorkspaceInitializer.resolveRoot())) {
                System.err.println("未找到 OryxOS 工作区。请先执行: oryxos init");
                return 1;
            }
            File agentDir = new File(WorkspaceInitializer.resolveRoot().toFile(), "agents/" + name);

            if (agentDir.exists()) {
                System.err.println("Agent 已存在: " + name + "（一个目录 = 一个 Agent，不可覆盖）");
                return 1;
            }

            agentDir.mkdirs();
            File agentMd = new File(agentDir, "AGENT.md");
            try (FileWriter w = new FileWriter(agentMd)) {
                w.write("---\n");
                w.write("name: " + name + "\n");
                w.write("description: Agent description\n");
                w.write("identity:\n");
                w.write("  agent_name: " + name + "\n");
                w.write("  prompt: \"You are a helpful assistant.\"\n");
                w.write("provider:\n");
                w.write("  name: deepseek\n");
                w.write("  model: deepseek-chat\n");
                w.write("tools: []\n");
                w.write("mcp_servers: []\n");
                w.write("channels:\n");
                w.write("  - name: cli\n");
                w.write("bootstrap: []\n");
                w.write("settings:\n");
                w.write("  max_iterations: 10\n");
                w.write("  max_history_turns: 20\n");
                w.write("schedules: []\n");
                w.write("---\n\n");
                w.write("# " + name + " — Agent 任务指令\n\n");
                w.write("在此编写该 Agent 的任务指令。正文会注入 system prompt 首位。\n");
            } catch (IOException e) {
                System.err.println("AGENT.md 创建失败: " + e.getMessage());
                return 1;
            }

            System.out.println("Agent created: " + name);
            System.out.println("  Edit: " + agentMd.getAbsolutePath());
            return 0;
        }
    }

    @Command(name = "list", description = "List all agent profiles")
    public static class ListCommand implements Callable<Integer> {
        @Override
        public Integer call() {
            AgentLoader loader = new AgentLoader(WorkspaceInitializer.resolveRoot());
            var profiles = loader.list();
            if (profiles.isEmpty()) {
                System.out.println("No agents found.");
                return 0;
            }
            System.out.println("Agents:");
            for (Profile p : profiles) {
                System.out.printf("  - %-20s provider=%s model=%s tools=%d%n",
                        p.getName(),
                        p.getProvider() == null ? "-" : p.getProvider().getName(),
                        p.getProvider() == null ? "-" : p.getProvider().getModel(),
                        p.getTools() == null ? 0 : p.getTools().size());
            }
            return 0;
        }
    }

    @Command(name = "show", description = "Show agent profile details")
    public static class ShowCommand implements Callable<Integer> {
        @Parameters(description = "Profile name")
        private String name;

        @Override
        public Integer call() {
            File agentMd = new File(WorkspaceInitializer.resolveRoot().toFile(), "agents/" + name + "/AGENT.md");

            if (!agentMd.exists()) {
                System.err.println("Agent not found: " + name);
                return 1;
            }

            try {
                System.out.println(Files.readString(agentMd.toPath()));
            } catch (IOException e) {
                System.err.println("Failed to read AGENT.md: " + e.getMessage());
                return 1;
            }
            return 0;
        }
    }

    @Command(name = "delete", description = "Delete an agent profile")
    public static class DeleteCommand implements Callable<Integer> {
        @Parameters(description = "Profile name")
        private String name;

        @Override
        public Integer call() {
            File agentDir = new File(WorkspaceInitializer.resolveRoot().toFile(), "agents/" + name);

            if (!agentDir.exists()) {
                System.err.println("Agent not found: " + name);
                return 1;
            }

            deleteRecursive(agentDir);
            System.out.println("Agent deleted: " + name);
            return 0;
        }

        private void deleteRecursive(File file) {
            if (file.isDirectory()) {
                File[] children = file.listFiles();
                if (children != null) {
                    for (File child : children) {
                        deleteRecursive(child);
                    }
                }
            }
            file.delete();
        }
    }
}
