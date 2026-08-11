package com.oryxos.cli;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.util.Scanner;
import java.util.concurrent.Callable;

/**
 * oryxos chat — interactive agent conversation.
 */
@Command(name = "chat", description = "Start interactive agent conversation")
public class ChatCommand implements Callable<Integer> {

    @Option(names = {"--profile"}, description = "Agent profile name (default: default)")
    private String profile = "default";

    @Override
    public Integer call() {
        System.out.println("OryxOS Chat — Agent [" + profile + "]");
        System.out.println("Type /quit to exit.");
        System.out.println();

        try (Scanner scanner = new Scanner(System.in)) {
            while (true) {
                System.out.print("> ");
                if (!scanner.hasNextLine()) {
                    break;
                }
                String input = scanner.nextLine().trim();

                if (input.isEmpty()) {
                    continue;
                }
                if ("/quit".equals(input)) {
                    System.out.println("Goodbye.");
                    break;
                }

                // Placeholder: actual ReAct loop would process here
                System.out.println("[Agent response would appear here — ReAct loop processes: \"" + input + "\"]");
            }
        }

        return 0;
    }
}
