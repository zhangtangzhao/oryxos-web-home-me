package com.oryxos.tool;

import com.oryxos.core.Sandbox;
import com.oryxos.core.SandboxViolationException;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;

/**
 * Core-phase sandbox implementation using application-layer whitelist checks.
 * Checks file paths, shell commands, and HTTP URLs against configured
 * whitelists. Empty whitelist for a category = deny all for that category
 * (minimal privilege, FR-013/SC-006).
 */
public class WhitelistSandbox implements Sandbox {

    /** Shell metacharacters that can smuggle a second command past a first-token check. */
    private static final String COMMAND_SEPARATORS = "[;|&\n`]";

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
        // Resolve symlinks when the target exists so a link cannot escape the whitelist
        if (normalized.toFile().exists()) {
            try {
                normalized = normalized.toRealPath();
            } catch (IOException ignored) {
                // fall back to the normalized absolute path
            }
        }
        String pathStr = normalized.toString();
        for (String p : allowedPaths) {
            Path allowedPath = Paths.get(p).normalize().toAbsolutePath();
            if (allowedPath.toFile().exists()) {
                try {
                    allowedPath = allowedPath.toRealPath();
                } catch (IOException ignored) {
                    // fall back to the normalized absolute path
                }
            }
            if (pathStr.startsWith(allowedPath.toString())) {
                return;
            }
        }
        throw new SandboxViolationException("文件路径不在白名单内: " + filePath);
    }

    private void checkShellCommand(String command) {
        if (allowedCommands.isEmpty()) {
            throw new SandboxViolationException("Shell 命令白名单为空，所有命令均被拒绝");
        }
        // Every segment split by shell separators must itself start with a whitelisted
        // command — blocks `git status && rm -rf /` style smuggling (SC-006).
        String[] segments = command.split(COMMAND_SEPARATORS);
        for (String segment : segments) {
            String trimmed = segment.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            String firstToken = trimmed.split("\\s+")[0];
            if (!allowedCommands.contains(firstToken)) {
                throw new SandboxViolationException("Shell 命令不在白名单内: " + firstToken);
            }
        }
    }

    private void checkHttpUrl(String url) {
        try {
            java.net.URI uri = new java.net.URI(url);
            String scheme = uri.getScheme();
            String host = uri.getHost();
            if (host == null) {
                throw new SandboxViolationException("无法从 URL 解析主机名: " + url);
            }
            if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
                throw new SandboxViolationException("仅允许 http/https 请求: " + url);
            }
            for (String d : allowedDomains) {
                if ("*".equals(d) || host.equalsIgnoreCase(d) || host.toLowerCase().endsWith("." + d.toLowerCase())) {
                    return;
                }
            }
            throw new SandboxViolationException("HTTP 域名不在白名单内: " + host);
        } catch (java.net.URISyntaxException e) {
            throw new SandboxViolationException("URL 非法: " + url);
        }
    }

    /** Whitelist summary for CLI display (contracts/cli.md: 白名单摘要). */
    public String summary() {
        return "paths=" + allowedPaths.size() + ", commands=" + allowedCommands.size()
                + ", domains=" + (allowedDomains.contains("*") ? "*（全部）" : allowedDomains.size());
    }

    public Set<String> getAllowedPaths() { return allowedPaths; }
    public Set<String> getAllowedCommands() { return allowedCommands; }
    public Set<String> getAllowedDomains() { return allowedDomains; }
}
