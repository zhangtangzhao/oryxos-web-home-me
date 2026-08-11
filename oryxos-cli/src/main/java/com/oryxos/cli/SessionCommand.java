package com.oryxos.cli;

import picocli.CommandLine.Command;

import java.util.concurrent.Callable;

/**
 * oryxos session list — list conversation sessions.
 */
@Command(name = "session", description = "Manage sessions",
         subcommands = SessionCommand.ListCommand.class)
public class SessionCommand {

    @Command(name = "list", description = "List conversation sessions")
    public static class ListCommand implements Callable<Integer> {
        @Override
        public Integer call() {
            System.out.println("Sessions:");
            System.out.println("  - (No sessions yet — SQLite persistence initializes on first chat)");
            return 0;
        }
    }
}
