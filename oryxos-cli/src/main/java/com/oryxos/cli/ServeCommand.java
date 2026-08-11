package com.oryxos.cli;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.util.concurrent.Callable;

/**
 * oryxos serve — start the HTTP API server.
 */
@Command(name = "serve", description = "Start HTTP API server (Spring MVC)")
public class ServeCommand implements Callable<Integer> {

    @Option(names = {"--port"}, description = "Server port (default: 8080)")
    private int port = 8080;

    @Override
    public Integer call() {
        System.out.println("Starting OryxOS API server on port " + port + "...");
        System.out.println("REST API: http://localhost:" + port + "/api/v1/");
        System.out.println("Web Admin: http://localhost:" + port + "/admin/");
        System.out.println("(Bootstrapped by oryxos-boot Spring context)");
        return 0;
    }
}
