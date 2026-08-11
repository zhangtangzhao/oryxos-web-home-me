package com.oryxos.boot;

import com.oryxos.cli.OryxOsCli;
import org.springframework.boot.SpringApplication;

import java.util.Set;

/**
 * Hybrid launcher: routes lightweight commands directly to Picocli (no Spring)
 * and heavy commands to Spring Boot context.
 * <p>
 * This is the main class configured in spring-boot-maven-plugin.
 * Run with: java -jar oryxos-boot.jar [command]
 */
public class OryxOsLauncher {

    /** Commands that DO NOT need Spring context — run them directly via Picocli for sub-second startup. */
    private static final Set<String> LIGHT_COMMANDS = Set.of(
        "init", "status", "profile", "provider", "tool", "session",
        "--version", "--help", "-V", "-h"
    );

    /** Prefixes that indicate a light command (e.g., "profile create", "tool list"). */
    private static final Set<String> LIGHT_PREFIXES = Set.of(
        "profile", "provider", "tool", "session"
    );

    public static void main(String[] args) {
        // Determine if this is a light or heavy command
        boolean isLight = false;
        if (args.length == 1 && LIGHT_COMMANDS.contains(args[0])) {
            isLight = true;
        } else if (args.length >= 2 && LIGHT_PREFIXES.contains(args[0])) {
            isLight = true;
        }

        if (isLight) {
            // Lightweight: run Picocli directly, no Spring context
            OryxOsCli.main(args);
        } else {
            // Heavy: start full Spring Boot context
            SpringApplication.run(OryxOsApplication.class, args);
        }
    }
}
