package com.oryxos.core.session;

import com.oryxos.core.Profile;
import com.oryxos.core.Session;
import com.oryxos.core.agent.AgentLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T035/T038: idle archiving — default 30-minute threshold, per-agent
 * {@code settings.session_timeout_minutes} override; archived data is kept and
 * the caller transparently gets a fresh session (clarification #2).
 */
class SessionArchiveTest {

    @TempDir
    Path workspace;

    @Test
    void defaultThresholdIs30Minutes() {
        assertEquals(Duration.ofMinutes(30), SessionService.idleTimeout(null));
        assertEquals(Duration.ofMinutes(30), SessionService.idleTimeout(new Profile()));
    }

    @Test
    void profileOverrideChangesThreshold() {
        Profile profile = new Profile();
        Profile.Settings settings = new Profile.Settings();
        settings.setSessionTimeoutMinutes(5);
        profile.setSettings(settings);
        assertEquals(Duration.ofMinutes(5), SessionService.idleTimeout(profile));
    }

    @Test
    void freshActiveSessionIsReused() {
        InMemoryStore store = new InMemoryStore();
        SessionService service = new SessionService(store, new AgentLoader(workspace));

        Session first = service.getOrCreate("cli", "alice", "demo");
        Session second = service.getOrCreate("cli", "alice", "demo");

        assertEquals(first.getSessionId(), second.getSessionId());
    }

    @Test
    void idleExpiredSessionIsArchivedAndNewOneCreated() {
        InMemoryStore store = new InMemoryStore();
        SessionService service = new SessionService(store, new AgentLoader(workspace));

        Session first = service.getOrCreate("cli", "alice", "demo");
        first.setLastActiveAt(Instant.now().minus(Duration.ofMinutes(31)));
        store.save(first);

        Session second = service.getOrCreate("cli", "alice", "demo");

        assertNotEquals(first.getSessionId(), second.getSessionId());
        assertTrue(second.getSessionId().startsWith(first.getSessionId()));
        assertEquals(Session.SessionStatus.ARCHIVED, store.byId.get(first.getSessionId()).getStatus());
        assertNotNull(store.byId.get(first.getSessionId()).getArchivedAt());
        assertEquals(Session.SessionStatus.ACTIVE, second.getStatus());
        assertEquals(Optional.of(second), store.findActiveById(second.getSessionId()));
    }

    @Test
    void boundaryOfDefaultThreshold() throws Exception {
        writeAgent("demo", null);
        InMemoryStore store = new InMemoryStore();
        SessionService service = new SessionService(store, new AgentLoader(workspace));

        Session a = service.getOrCreate("cli", "bob", "demo");
        a.setLastActiveAt(Instant.now().minus(Duration.ofMinutes(29)));
        store.save(a);
        assertEquals(a.getSessionId(), service.getOrCreate("cli", "bob", "demo").getSessionId());

        a.setLastActiveAt(Instant.now().minus(Duration.ofMinutes(30)).minusSeconds(1));
        store.save(a);
        assertNotEquals(a.getSessionId(), service.getOrCreate("cli", "bob", "demo").getSessionId());
    }

    @Test
    void perAgentShortTimeoutExpiresEarlier() throws Exception {
        writeAgent("quickbot", 1);
        InMemoryStore store = new InMemoryStore();
        AgentLoader loader = new AgentLoader(workspace);
        SessionService service = new SessionService(store, loader);

        Session session = service.getOrCreate("cli", "carol", "quickbot");
        session.setLastActiveAt(Instant.now().minus(Duration.ofMinutes(2)));
        store.save(session);

        assertTrue(service.isIdleExpired(session, "quickbot"));
        Session next = service.getOrCreate("cli", "carol", "quickbot");
        assertNotEquals(session.getSessionId(), next.getSessionId());
    }

    /** AGENT.md with optional settings.session_timeout_minutes. */
    private void writeAgent(String name, Integer timeoutMinutes) throws Exception {
        Path dir = workspace.resolve("agents").resolve(name);
        Files.createDirectories(dir);
        StringBuilder front = new StringBuilder();
        front.append("---\nname: ").append(name).append("\n")
                .append("provider:\n  name: deepseek\n  model: deepseek-chat\n")
                .append("tools: []\nbootstrap: []\n");
        if (timeoutMinutes != null) {
            front.append("settings:\n  session_timeout_minutes: ").append(timeoutMinutes).append('\n');
        }
        front.append("---\n\n正文。\n");
        Files.writeString(dir.resolve("AGENT.md"), front.toString());
    }

    static class InMemoryStore implements com.oryxos.core.session.SessionStore {
        final Map<String, Session> byId = new HashMap<>();

        @Override
        public void save(Session session) {
            byId.put(session.getSessionId(), session);
        }

        @Override
        public Optional<Session> findById(String sessionId) {
            return Optional.ofNullable(byId.get(sessionId));
        }

        @Override
        public Optional<Session> findActiveById(String sessionId) {
            return Optional.ofNullable(byId.get(sessionId))
                    .filter(s -> s.getStatus() == Session.SessionStatus.ACTIVE);
        }

        @Override
        public List<Session> findByStatus(Session.SessionStatus status) {
            return new ArrayList<>(byId.values().stream()
                    .filter(s -> s.getStatus() == status).toList());
        }
    }
}
