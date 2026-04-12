package com.tetra.app;

public class ChatMessage {
    public enum Role { USER, AI }

    private String text;
    private Role role;
    private long timestamp;

    public ChatMessage(String text, Role role) {
        this.text = text;
        this.role = role;
        this.timestamp = System.currentTimeMillis();
    }

    public String getText() { return text; }
    public Role getRole() { return role; }
    public long getTimestamp() { return timestamp; }
}
