package com.devmod.mailbox;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MessageTypeTest {

    @Test
    @DisplayName("fromId returns correct type for known ids")
    void fromIdKnown() {
        assertEquals(MessageType.PLAYER, MessageType.fromId("player"));
        assertEquals(MessageType.SYSTEM, MessageType.fromId("system"));
        assertEquals(MessageType.ADMIN, MessageType.fromId("admin"));
        assertEquals(MessageType.REWARD, MessageType.fromId("reward"));
    }

    @Test
    @DisplayName("fromId is case insensitive")
    void fromIdCaseInsensitive() {
        assertEquals(MessageType.PLAYER, MessageType.fromId("PLAYER"));
        assertEquals(MessageType.ADMIN, MessageType.fromId("Admin"));
    }

    @Test
    @DisplayName("fromId returns SYSTEM for null")
    void fromIdNull() {
        assertEquals(MessageType.SYSTEM, MessageType.fromId(null));
    }

    @Test
    @DisplayName("fromId returns SYSTEM for unknown")
    void fromIdUnknown() {
        assertEquals(MessageType.SYSTEM, MessageType.fromId("unknown"));
    }

    @Test
    @DisplayName("getId returns correct id strings")
    void getId() {
        assertEquals("player", MessageType.PLAYER.getId());
        assertEquals("system", MessageType.SYSTEM.getId());
        assertEquals("admin", MessageType.ADMIN.getId());
        assertEquals("reward", MessageType.REWARD.getId());
    }
}
