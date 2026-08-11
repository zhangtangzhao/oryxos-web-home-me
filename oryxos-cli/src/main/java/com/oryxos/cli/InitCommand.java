package com.oryxos.cli;

import picocli.CommandLine.Command;

import java.io.File;
import java.util.concurrent.Callable;

/**
 * oryxos init — initialize the .oryxos workspace directory.
 */
@Command(name = "init", description = "Initialize .oryxos/ workspace directory")
public class InitCommand implements Callable<Integer> {

    @Override
    public Integer call() {
        String root = System.getenv().getOrDefault("ORYXOS_ROOT", ".oryxos");
        File workspace = new File(root);

        if (!workspace.exists()) {
            workspace.mkdirs();
        }

        String[] subdirs = {"agents", "skills", "memory", "output", "sessions", "logs"};
        for (String sub : subdirs) {
            File dir = new File(workspace, sub);
            if (!dir.exists()) {
                dir.mkdirs();
                System.out.println("  Created: " + dir.getPath());
            }
        }

        // Create bootstrap files if they don't exist (idempotent)
        String[] bootFiles = {"AGENTS.md", "SOUL.md", "USER.md"};
        for (String f : bootFiles) {
            File file = new File(workspace, f);
            if (!file.exists()) {
                try {
                    file.createNewFile();
                    System.out.println("  Created: " + file.getPath());
                } catch (Exception e) {
                    System.err.println("  Failed to create: " + file.getPath() + " — " + e.getMessage());
                }
            }
        }

        System.out.println();
        System.out.println("OryxOS workspace initialized at: " + workspace.getAbsolutePath());
        System.out.println("Next: oryxos profile create <name>");
        return 0;
    }
}
