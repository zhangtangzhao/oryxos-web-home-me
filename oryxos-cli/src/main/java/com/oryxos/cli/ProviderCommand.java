package com.oryxos.cli;

import picocli.CommandLine.Command;

import java.util.concurrent.Callable;

/**
 * oryxos provider list — list configured LLM providers.
 */
@Command(name = "provider", description = "Manage LLM providers",
         subcommands = ProviderCommand.ListCommand.class)
public class ProviderCommand {

    @Command(name = "list", description = "List configured providers")
    public static class ListCommand implements Callable<Integer> {
        @Override
        public Integer call() {
            System.out.println("Registered Providers:");
            System.out.println("  - deepseek (https://api.deepseek.com)");
            System.out.println("  - qwen (https://dashscope.aliyuncs.com)");
            System.out.println("  - kimi (https://api.moonshot.cn)");
            System.out.println("(Configured via application.yml — API keys from environment variables)");
            return 0;
        }
    }
}
