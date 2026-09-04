package com.oryxos.cli;

import com.oryxos.channel.cli.CliChannel;
import org.springframework.beans.factory.annotation.Autowired;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.util.concurrent.Callable;

/**
 * oryxos chat — interactive agent conversation (heavy command: runs inside the
 * Spring context so the full ReAct runtime is available).
 */
@Command(name = "chat", description = "Start interactive agent conversation")
public class ChatCommand implements Callable<Integer> {

    @Option(names = {"--profile"}, description = "Agent profile name (default: first available)")
    private String profile;

    @Option(names = {"--message"}, description = "Send a single message, print the reply, and exit")
    private String message;

    @Autowired
    private CliChannel cliChannel;

    @Override
    public Integer call() {
        return cliChannel.run(profile, message);
    }
}
