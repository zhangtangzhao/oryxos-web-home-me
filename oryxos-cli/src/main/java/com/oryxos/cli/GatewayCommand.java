package com.oryxos.cli;

import picocli.CommandLine.Command;

import java.util.concurrent.Callable;

/**
 * oryxos gateway — start as a daemon serving multiple channels.
 */
@Command(name = "gateway", description = "Start daemon process (multi-channel)")
public class GatewayCommand implements Callable<Integer> {

    @Override
    public Integer call() {
        System.out.println("Starting OryxOS gateway daemon...");
        System.out.println("(Full multi-channel support planned for extension phase)");
        return 0;
    }
}
