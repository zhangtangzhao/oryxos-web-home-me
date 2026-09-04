package com.oryxos.core.session;

import com.oryxos.core.Session;

import java.util.List;
import java.util.Optional;

/**
 * Port from core services to session persistence. Implemented by the storage
 * module over SQLite (FR-017).
 */
public interface SessionStore {

    /** Insert or update the session (messages + status + timestamps). */
    void save(Session session);

    Optional<Session> findById(String sessionId);

    /** Only sessions whose status is ACTIVE. */
    Optional<Session> findActiveById(String sessionId);

    List<Session> findByStatus(Session.SessionStatus status);
}
