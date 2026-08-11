package com.oryxos.cli;

import picocli.CommandLine.Command;

import java.util.concurrent.Callable;

/**
 * oryxos tool list — list available tools.
 */
@Command(name = "tool", description = "Manage tools",
         subcommands = ToolCommand.ListCommand.class)
public class ToolCommand {

    @Command(name = "list", description = "List available tools")
    public static class ListCommand implements Callable<Integer> {
        @Override
        public Integer call() {
            System.out.println("Available Tools:");
            System.out.println("  - read_file       : Read file content");
            System.out.println("  - write_file      : Write file content");
            System.out.println("  - list_dir        : List directory contents");
            System.out.println("  - shell           : Execute shell command");
            System.out.println("  - http_get        : HTTP GET request");
            System.out.println("  - http_post       : HTTP POST request");
            System.out.println("  - save_memory     : Save to long-term memory");
            System.out.println("  - recall_memory   : Recall from long-term memory");
            System.out.println("  - notify          : Send notification via webhook");
            return 0;
        }
    }
}
