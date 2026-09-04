package com.oryxos.core.session;

import com.oryxos.core.Profile;
import com.oryxos.core.Session;
import com.oryxos.core.agent.AgentLoader;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Session lifecycle: find-or-create by the channel:user:profile triple and
 * persist through the SessionStore port (FR-017).
 * <p>
 * Idle archiving (clarification #2): an active session idle longer than
 * {@code settings.session_timeout_minutes} (default 30) is archived on the
 * next getOrCreate — data is kept, and the caller transparently receives a
 * fresh session, so the CLI channel "auto-opens a new session" (FR-029).
 */
@Service
public class SessionService {

    /** Default idle threshold when the profile carries no override. */
    public static final int DEFAULT_TIMEOUT_MINUTES = 30;

    private final SessionStore store;
    private final AgentLoader agentLoader;

    public SessionService(SessionStore store, AgentLoader agentLoader) {
        this.store = store;
        this.agentLoader = agentLoader;
    }

    /**
     * Returns the active session for the triple; creates one if none exists.
     * An idle-expired active session is archived first, so a fresh session id
     * (with a numeric suffix) is generated and archived history stays intact.
     */
    public Session getOrCreate(String channel, String userId, String profileName) {
        String baseId = channel + "-" + userId + "-" + profileName;
        Optional<Session> active = store.findActiveById(baseId);
        if (active.isPresent()) {
            Session session = active.get();
            if (isIdleExpired(session, profileName)) {
                session.archive();
                store.save(session);
            } else {
                return session;
            }
        }
        String sessionId = store.findById(baseId).isPresent() ? baseId + "-" + System.currentTimeMillis() : baseId;
        Session session = new Session(sessionId, profileName, channel, userId);
        store.save(session);
        return session;
    }

    /** Threshold test extracted for unit coverage (T038). */
    boolean isIdleExpired(Session session, String profileName) {
        return session.getLastActiveAt().isBefore(Instant.now().minus(idleTimeout(profileName)));
    }

    static Duration idleTimeout(Profile profile) {
        int minutes = DEFAULT_TIMEOUT_MINUTES;
        if (profile != null && profile.getSettings() != null) {
            minutes = profile.getSettings().getSessionTimeoutMinutes();
        }
        return Duration.ofMinutes(minutes);
    }

    private Duration idleTimeout(String profileName) {
        return idleTimeout(agentLoader.get(profileName));
    }

    public Optional<Session> findActive(String sessionId) {
        return store.findActiveById(sessionId);
    }

    public Session requireActive(String sessionId) {
        return store.findById(sessionId)
                .map(s -> {
                    if (s.getStatus() == Session.SessionStatus.ARCHIVED) {
                        throw new SessionArchivedException(sessionId);
                    }
                    return s;
                })
                .orElseThrow(() -> new IllegalArgumentException("会话不存在: " + sessionId));
    }

    public void save(Session session) {
        store.save(session);
    }

    public void archive(Session session) {
        session.archive();
        store.save(session);
    }
}
