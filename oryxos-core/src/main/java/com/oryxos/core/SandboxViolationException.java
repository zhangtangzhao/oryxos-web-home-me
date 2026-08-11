package com.oryxos.core;

/**
 * Thrown when a sandbox check fails.
 */
public class SandboxViolationException extends RuntimeException {

    public SandboxViolationException(String message) {
        super(message);
    }

    public SandboxViolationException(Sandbox.ActionType type, String target) {
        super("Sandbox violation: " + type + " action denied for target '" + target + "'");
    }
}
