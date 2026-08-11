package com.oryxos.core;

import java.time.Instant;

/**
 * A single message in a conversation (user, assistant, or tool result).
 */
public class Message {

    public enum Role {
        USER, ASSISTANT, TOOL, SYSTEM
    }

    private Role role;
    private String content;
    private String toolName;
    private String toolCallId;
    private Instant timestamp;

    public Message() {}

    public Message(Role role, String content) {
        this.role = role;
        this.content = content;
        this.timestamp = Instant.now();
    }

    public static Message user(String content) {
        return new Message(Role.USER, content);
    }

    public static Message assistant(String content) {
        return new Message(Role.ASSISTANT, content);
    }

    public static Message tool(String toolName, String toolCallId, String content) {
        Message msg = new Message(Role.TOOL, content);
        msg.toolName = toolName;
        msg.toolCallId = toolCallId;
        return msg;
    }

    // Getters and setters
    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getToolName() { return toolName; }
    public void setToolName(String toolName) { this.toolName = toolName; }
    public String getToolCallId() { return toolCallId; }
    public void setToolCallId(String toolCallId) { this.toolCallId = toolCallId; }
    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
}
