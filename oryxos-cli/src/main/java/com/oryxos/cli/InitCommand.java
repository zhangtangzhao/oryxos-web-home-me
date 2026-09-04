package com.oryxos.cli;

import com.oryxos.core.workspace.WorkspaceInitializer;
import picocli.CommandLine.Command;

import java.util.List;
import java.util.concurrent.Callable;

/**
 * oryxos init — initialize the .oryxos workspace directory (idempotent, FR-001).
 */
@Command(name = "init", description = "Initialize .oryxos/ workspace directory")
public class InitCommand implements Callable<Integer> {

    @Override
    public Integer call() {
        try {
            List<String> report = WorkspaceInitializer.initialize(WorkspaceInitializer.resolveRoot());
            report.forEach(line -> System.out.println("  " + line));
        } catch (Exception e) {
            System.err.println("工作区初始化失败: " + e.getMessage());
            return 1;
        }
        System.out.println();
        System.out.println("OryxOS workspace initialized at: "
                + WorkspaceInitializer.resolveRoot().toAbsolutePath());
        System.out.println("Next: oryxos profile create <name>");
        return 0;
    }
}
