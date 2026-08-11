package com.oryxos.tool;

import com.oryxos.core.Sandbox;
import com.oryxos.core.SandboxViolationException;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;

/**
 * Core-phase sandbox implementation using application-layer whitelist checks.
 * Checks file paths, shell commands, and HTTP URLs against configured whitelists.
 */
public class WhitelistSandbox implements Sandbox {

    private final Set<String> allowedPaths;
    private final Set<String> allowedCommands;
    private final Set<String> allowedDomains;

    public WhitelistSandbox(Set<String> allowedPaths, Set<String> allowedCommands, Set<String> allowedDomains) {
        this.allowedPaths = allowedPaths;
        this.allowedCommands = allowedCommands;
        this.allowedDomains = allowedDomains;
    }

    @Override
    public void enforce(ActionType type, String target) {
        switch (type) {
            case FILE_READ:
            case FILE_WRITE:
                checkFilePath(target);
                break;
            case SHELL_COMMAND:
                checkShellCommand(target);
                break;
            case HTTP_REQUEST:
                checkHttpUrl(target);
                break;
        }
    }

    private void checkFilePath(String filePath) {
        Path normalized = Paths.get(filePath).normalize().toAbsolutePath();
        String pathStr = normalized.toString();
        boolean allowed = allowedPaths.stream().anyMatch(p -> {
            Path allowedPath = Paths.get(p).normalize().toAbsolutePath();
            return pathStr.startsWith(allowedPath.toString());
        });
        if (!allowed) {
            throw new SandboxViolationException("File path not in allowed paths: " + filePath);
        }
    }

    private void checkShellCommand(String command) {
        String firstToken = command.trim().split("\\s+")[0];
        if (!allowedCommands.contains(firstToken)) {
            throw new SandboxViolationException("Shell command not allowed: " + firstToken);
        }
    }

    private void checkHttpUrl(String url) {
        try {
            String host = new java.net.URI(url).getHost();
            if (host == null) {
                throw new SandboxViolationException("Cannot parse host from URL: " + url);
            }
            boolean allowed = allowedDomains.stream().anyMatch(d ->
                host.equals(d) || host.endsWith("." + d) || d.equals("*"));
            if (!allowed) {
                throw new SandboxViolationException("HTTP domain not in whitelist: " + host);
            }
        } catch (java.net.URISyntaxException e) {
            throw new SandboxViolationException("Invalid URL: " + url);
        }
    }
}
