package com.oryxos.core.session;

/** Thrown when an operation targets an archived session (REST returns 409, FR-029). */
public class SessionArchivedException extends RuntimeException {

    private final String sessionId;

    public SessionArchivedException(String sessionId) {
        super("会话已归档，不能继续发送消息: " + sessionId);
        this.sessionId = sessionId;
    }

    public String getSessionId() { return sessionId; }
}
