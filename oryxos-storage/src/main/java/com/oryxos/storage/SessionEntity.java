package com.oryxos.storage;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * JPA entity for persisted sessions.
 */
@Entity
@Table(name = "sessions")
public class SessionEntity {

    @Id
    @Column(name = "session_id")
    private String sessionId;

    @Column(name = "profile_name", nullable = false)
    private String profileName;

    @Column(nullable = false)
    private String channel;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "messages_json", columnDefinition = "TEXT")
    private String messagesJson;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private SessionStatus status = SessionStatus.ACTIVE;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "last_active_at", nullable = false)
    private Instant lastActiveAt;

    @Column(name = "archived_at")
    private Instant archivedAt;

    public enum SessionStatus { ACTIVE, ARCHIVED }

    // Getters and setters
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    public String getProfileName() { return profileName; }
    public void setProfileName(String profileName) { this.profileName = profileName; }
    public String getChannel() { return channel; }
    public void setChannel(String channel) { this.channel = channel; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getMessagesJson() { return messagesJson; }
    public void setMessagesJson(String messagesJson) { this.messagesJson = messagesJson; }
    public SessionStatus getStatus() { return status; }
    public void setStatus(SessionStatus status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getLastActiveAt() { return lastActiveAt; }
    public void setLastActiveAt(Instant lastActiveAt) { this.lastActiveAt = lastActiveAt; }
    public Instant getArchivedAt() { return archivedAt; }
    public void setArchivedAt(Instant archivedAt) { this.archivedAt = archivedAt; }
}
