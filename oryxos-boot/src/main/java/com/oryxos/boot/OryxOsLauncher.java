package com.oryxos.boot;

import com.oryxos.cli.OryxOsCli;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import picocli.CommandLine;
import picocli.spring.PicocliSpringFactory;

import java.util.Set;

/**
 * Hybrid launcher: lightweight commands go straight to Picocli (no Spring,
 * sub-second); heavy commands boot the Spring runtime first, then dispatch
 * the same Picocli tree with a SpringFactory so commands get beans injected.
 */
public class OryxOsLauncher {

    /** Commands that need no Spring context at all. */
    private static final Set<String> LIGHT_COMMANDS = Set.of(
        "init", "status", "profile", "provider", "tool", "session",
        "--version", "--help", "-V", "-h"
    );

    public static void main(String[] args) {
        boolean light = (args.length == 1 && LIGHT_COMMANDS.contains(args[0]))
                || (args.length >= 2 && LIGHT_COMMANDS.contains(args[0]));

        if (light) {
            System.exit(new CommandLine(new OryxOsCli()).execute(args));
            return;
        }

        // chat needs the runtime but no web server; serve/gateway will host REST (US4)
        WebApplicationType webType = args.length > 0
                && ("serve".equals(args[0]) || "gateway".equals(args[0]))
                ? WebApplicationType.SERVLET
                : WebApplicationType.NONE;

        SpringApplicationBuilder builder = new SpringApplicationBuilder(OryxOsApplication.class)
                .web(webType)
                .logStartupInfo(false);
        // `--port` must outrank config/application.yml → pass as a command-line
        // arg (highest precedence); defaultProperties would lose to the file.
        String port = portOption(args);
        String[] springArgs = args;
        if (port != null) {
            springArgs = new String[args.length + 1];
            System.arraycopy(args, 0, springArgs, 0, args.length);
            springArgs[args.length] = "--server.port=" + port;
        }
        ConfigurableApplicationContext context = builder.run(springArgs);
        try {
            int exitCode = new CommandLine(new OryxOsCli(), new PicocliSpringFactory(context)).execute(args);
            System.exit(exitCode);
        } finally {
            context.close();
        }
    }

    /** `serve --port 9000` / `--port=9000` must be applied before the web server starts. */
    private static String portOption(String[] args) {
        for (int i = 0; i < args.length; i++) {
            if (args[i].startsWith("--port=")) {
                return args[i].substring("--port=".length());
            }
            if ("--port".equals(args[i]) && i + 1 < args.length) {
                return args[i + 1];
            }
        }
        return null;
    }
}
