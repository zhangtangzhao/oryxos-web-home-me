package com.oryxos.storage;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.oryxos.core.Message;
import com.oryxos.core.Session;
import com.oryxos.core.session.SessionStore;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * JPA/SQLite implementation of the core SessionStore port.
 */
@Component
public class JpaSessionStore implements SessionStore {

    private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

    private final SessionRepository repository;

    public JpaSessionStore(SessionRepository repository) {
        this.repository = repository;
    }

    @Override
    public void save(Session session) {
        SessionEntity entity = repository.findById(session.getSessionId()).orElseGet(SessionEntity::new);
        entity.setSessionId(session.getSessionId());
        entity.setProfileName(session.getProfileName());
        entity.setChannel(session.getChannel());
        entity.setUserId(session.getUserId());
        entity.setMessagesJson(toJson(session.getMessages()));
        entity.setStatus(session.getStatus() == Session.SessionStatus.ARCHIVED
                ? SessionEntity.SessionStatus.ARCHIVED : SessionEntity.SessionStatus.ACTIVE);
        entity.setCreatedAt(session.getCreatedAt());
        entity.setLastActiveAt(session.getLastActiveAt());
        entity.setArchivedAt(session.getArchivedAt());
        repository.save(entity);
    }

    @Override
    public Optional<Session> findById(String sessionId) {
        return repository.findById(sessionId).map(JpaSessionStore::toDomain);
    }

    @Override
    public Optional<Session> findActiveById(String sessionId) {
        return repository.findBySessionIdAndStatus(sessionId, SessionEntity.SessionStatus.ACTIVE)
                .map(JpaSessionStore::toDomain);
    }

    @Override
    public List<Session> findByStatus(Session.SessionStatus status) {
        SessionEntity.SessionStatus s = status == Session.SessionStatus.ARCHIVED
                ? SessionEntity.SessionStatus.ARCHIVED : SessionEntity.SessionStatus.ACTIVE;
        return repository.findByStatus(s).stream().map(JpaSessionStore::toDomain).toList();
    }

    private static Session toDomain(SessionEntity e) {
        Session s = new Session(e.getSessionId(), e.getProfileName(), e.getChannel(), e.getUserId());
        List<Message> messages = fromJson(e.getMessagesJson());
        if (messages != null) {
            s.setMessages(messages);
        }
        s.setStatus(e.getStatus() == SessionEntity.SessionStatus.ARCHIVED
                ? Session.SessionStatus.ARCHIVED : Session.SessionStatus.ACTIVE);
        s.setCreatedAt(e.getCreatedAt());
        s.setLastActiveAt(e.getLastActiveAt());
        s.setArchivedAt(e.getArchivedAt());
        return s;
    }

    private static String toJson(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("会话消息序列化失败", e);
        }
    }

    @SuppressWarnings("unchecked")
    private static List<Message> fromJson(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return (List<Message>) MAPPER.readValue(json, MAPPER.getTypeFactory()
                    .constructCollectionType(List.class, Message.class));
        } catch (Exception e) {
            throw new IllegalStateException("会话消息反序列化失败", e);
        }
    }
}
