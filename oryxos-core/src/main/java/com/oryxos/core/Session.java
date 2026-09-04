package com.oryxos.core;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * A conversation session between a user and an agent.
 */
public class Session {

    private String sessionId;
    private String profileName;
    private String channel;
    private String userId;
    private List<Message> messages = new ArrayList<>();
    private SessionStatus status = SessionStatus.ACTIVE;
    private Instant createdAt;
    private Instant lastActiveAt;
    private Instant archivedAt;
    /** Ephemeral sessions (REST stateless invoke) are processed but never persisted. */
    private boolean ephemeral;

    public enum SessionStatus { ACTIVE, ARCHIVED }

    public Session() {
        this.createdAt = Instant.now();
        this.lastActiveAt = Instant.now();
    }

    public Session(String sessionId, String profileName, String channel, String userId) {
        this();
        this.sessionId = sessionId;
        this.profileName = profileName;
        this.channel = channel;
        this.userId = userId;
    }

    public void addMessage(Message message) {
        this.messages.add(message);
        this.lastActiveAt = Instant.now();
    }

    public void archive() {
        this.status = SessionStatus.ARCHIVED;
        this.archivedAt = Instant.now();
    }

    // Getters and setters
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    public String getProfileName() { return profileName; }
    public void setProfileName(String profileName) { this.profileName = profileName; }
    public String getChannel() { return channel; }
    public void setChannel(String channel) { this.channel = channel; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public List<Message> getMessages() { return messages; }
    public void setMessages(List<Message> messages) { this.messages = messages; }
    public SessionStatus getStatus() { return status; }
    public void setStatus(SessionStatus status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getLastActiveAt() { return lastActiveAt; }
    public void setLastActiveAt(Instant lastActiveAt) { this.lastActiveAt = lastActiveAt; }
    public Instant getArchivedAt() { return archivedAt; }
    public void setArchivedAt(Instant archivedAt) { this.archivedAt = archivedAt; }
    public boolean isEphemeral() { return ephemeral; }
    public void setEphemeral(boolean ephemeral) { this.ephemeral = ephemeral; }
}
