package com.oryxos.core;

/**
 * Sandbox abstraction — enforces safety constraints on tool execution.
 * Core phase implements WhitelistSandbox (path/command/domain whitelist).
 * Extension phases add container and microVM isolation.
 */
public interface Sandbox {

    enum ActionType {
        FILE_READ,
        FILE_WRITE,
        SHELL_COMMAND,
        HTTP_REQUEST
    }

    /**
     * Check whether the given action is allowed.
     *
     * @param type the action category
     * @param target the specific target (file path, shell command, URL)
     * @throws SandboxViolationException if the action is not permitted
     */
    void enforce(ActionType type, String target);
}
