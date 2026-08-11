package com.oryxos.cli;

import picocli.CommandLine.Command;

import java.io.File;
import java.util.concurrent.Callable;

/**
 * oryxos status — show workspace and configuration status.
 */
@Command(name = "status", description = "Show workspace and configuration status")
public class StatusCommand implements Callable<Integer> {

    @Override
    public Integer call() {
        String root = System.getenv().getOrDefault("ORYXOS_ROOT", ".oryxos");
        File workspace = new File(root);

        System.out.println("OryxOS Status");
        System.out.println("=============");
        System.out.println("Workspace: " + workspace.getAbsolutePath());
        System.out.println("Initialized: " + workspace.exists());

        if (workspace.exists()) {
            File agents = new File(workspace, "agents");
            int agentCount = (agents.exists() && agents.listFiles() != null) ? agents.listFiles().length : 0;
            System.out.println("Agents: " + agentCount);

            File db = new File(workspace.getParent(), "oryxos.db");
            System.out.println("Database: " + (db.exists() ? "created" : "not yet created"));

            File memory = new File(workspace, "memory/MEMORY.md");
            System.out.println("Memory file: " + (memory.exists() ? "exists" : "not yet created"));
        }

        return 0;
    }
}
