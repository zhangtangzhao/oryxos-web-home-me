package com.oryxos.cli;

import picocli.CommandLine;
import picocli.CommandLine.Command;

import java.util.concurrent.Callable;

/**
 * Picocli main entry point for OryxOS CLI.
 * Provides 12 subcommands for agent management, interaction, and system operations.
 */
@Command(
    name = "oryxos",
    description = "OryxOS — Enterprise Agent OS CLI",
    mixinStandardHelpOptions = true,
    versionProvider = OryxOsVersionProvider.class,
    subcommands = {
        InitCommand.class,
        StatusCommand.class,
        ChatCommand.class,
        ServeCommand.class,
        GatewayCommand.class,
        ProfileCommand.class,
        ProviderCommand.class,
        ToolCommand.class,
        SessionCommand.class,
        KbCommand.class,
        CommandLine.HelpCommand.class
    }
)
public class OryxOsCli implements Callable<Integer> {

    @Override
    public Integer call() {
        System.out.println("OryxOS v1.0.0-SNAPSHOT — Enterprise Agent OS");
        System.out.println("Java 21 + Spring Boot 3.x");
        System.out.println();
        System.out.println("Usage: oryxos <command> [args]");
        System.out.println("Run 'oryxos --help' for available commands.");
        return 0;
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new OryxOsCli()).execute(args);
        System.exit(exitCode);
    }
}
