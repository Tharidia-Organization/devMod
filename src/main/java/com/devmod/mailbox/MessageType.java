package com.devmod.mailbox;

/**
 * Enum representing the different types of mailbox messages.
 */
public enum MessageType {
    /** Message sent between players */
    PLAYER("player"),

    /** System-generated message (automated notifications) */
    SYSTEM("system"),

    /** Message sent by an administrator */
    ADMIN("admin"),

    /** Reward message with claimable attachments */
    REWARD("reward");

    private final String id;

    MessageType(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    /**
     * Get MessageType from string ID.
     *
     * @param id the string identifier
     * @return the matching MessageType, or SYSTEM if not found
     */
    public static MessageType fromId(String id) {
        if (id == null) {
            return SYSTEM;
        }
        for (MessageType type : values()) {
            if (type.id.equalsIgnoreCase(id)) {
                return type;
            }
        }
        return SYSTEM;
    }
}
