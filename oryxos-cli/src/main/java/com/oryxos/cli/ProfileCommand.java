package com.oryxos.cli;

import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.io.File;
import java.io.FileWriter;
import java.util.concurrent.Callable;

/**
 * oryxos profile create|list|show|delete — Agent profile management.
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

    @Command(name = "create", description = "Create a new agent profile")
    public static class CreateCommand implements Callable<Integer> {
        @Parameters(description = "Profile name")
        private String name;

        @Override
        public Integer call() {
            String root = System.getenv().getOrDefault("ORYXOS_ROOT", ".oryxos");
            File agentDir = new File(root + "/agents/" + name);

            if (agentDir.exists()) {
                System.out.println("Agent already exists: " + name);
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
                w.write("bootstrap: []\n");
                w.write("settings:\n");
                w.write("  max_iterations: 10\n");
                w.write("  max_history_turns: 20\n");
                w.write("---\n\n");
                w.write("# " + name + " — Agent Task Instructions\n\n");
                w.write("Write your agent's task instructions here.\n");
            } catch (Exception e) {
                System.err.println("Failed to create AGENT.md: " + e.getMessage());
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
            String root = System.getenv().getOrDefault("ORYXOS_ROOT", ".oryxos");
            File agentsDir = new File(root + "/agents");

            if (!agentsDir.exists() || agentsDir.listFiles() == null || agentsDir.listFiles().length == 0) {
                System.out.println("No agents found.");
                return 0;
            }

            System.out.println("Agents:");
            File[] dirs = agentsDir.listFiles(File::isDirectory);
            if (dirs != null) {
                for (File d : dirs) {
                    System.out.println("  - " + d.getName());
                }
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
            String root = System.getenv().getOrDefault("ORYXOS_ROOT", ".oryxos");
            File agentMd = new File(root + "/agents/" + name + "/AGENT.md");

            if (!agentMd.exists()) {
                System.out.println("Agent not found: " + name);
                return 1;
            }

            try {
                String content = new String(java.nio.file.Files.readAllBytes(agentMd.toPath()));
                System.out.println(content);
            } catch (Exception e) {
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
            String root = System.getenv().getOrDefault("ORYXOS_ROOT", ".oryxos");
            File agentDir = new File(root + "/agents/" + name);

            if (!agentDir.exists()) {
                System.out.println("Agent not found: " + name);
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
