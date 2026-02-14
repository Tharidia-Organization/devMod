package com.devmod.notification;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for NotificationCenterActionData and PartyInviteActionData serialization.
 */
class NotificationActionDataTest {

    // ========================================================================
    // NOTIFICATION CENTER ACTION DATA
    // ========================================================================

    @Nested
    @DisplayName("NotificationCenterActionData")
    class CenterActionDataTests {

        @Test
        @DisplayName("Null tab throws NullPointerException")
        void nullTabThrows() {
            assertThrows(NullPointerException.class, () ->
                new NotificationCenterActionData(null, null));
        }

        @Test
        @DisplayName("forTab factory creates correctly")
        void forTabFactoryCreates() {
            UUID entityId = UUID.randomUUID();
            NotificationCenterActionData data = NotificationCenterActionData.forTab("MAILBOX", entityId);

            assertEquals("MAILBOX", data.tab());
            assertEquals(entityId, data.entityId());
        }

        @Test
        @DisplayName("forTab with null entityId works")
        void forTabWithNullEntityId() {
            NotificationCenterActionData data = NotificationCenterActionData.forTab("NEWS", null);
            assertEquals("NEWS", data.tab());
            assertNull(data.entityId());
        }

        @Test
        @DisplayName("toJson and fromJson round-trip with entityId")
        void roundTripWithEntityId() {
            UUID entityId = UUID.randomUUID();
            NotificationCenterActionData original = NotificationCenterActionData.forTab("MAILBOX", entityId);

            String json = original.toJson();
            assertNotNull(json);
            assertFalse(json.isBlank());

            NotificationCenterActionData parsed = NotificationCenterActionData.fromJson(json);
            assertNotNull(parsed);
            assertEquals("MAILBOX", parsed.tab());
            assertEquals(entityId, parsed.entityId());
        }

        @Test
        @DisplayName("toJson and fromJson round-trip without entityId")
        void roundTripWithoutEntityId() {
            NotificationCenterActionData original = NotificationCenterActionData.forTab("NEWS", null);

            String json = original.toJson();
            NotificationCenterActionData parsed = NotificationCenterActionData.fromJson(json);

            assertNotNull(parsed);
            assertEquals("NEWS", parsed.tab());
            assertNull(parsed.entityId());
        }

        @Test
        @DisplayName("fromJson returns null for null input")
        void fromJsonNullReturnsNull() {
            assertNull(NotificationCenterActionData.fromJson(null));
        }

        @Test
        @DisplayName("fromJson returns null for blank input")
        void fromJsonBlankReturnsNull() {
            assertNull(NotificationCenterActionData.fromJson(""));
            assertNull(NotificationCenterActionData.fromJson("   "));
        }

        @Test
        @DisplayName("fromJson returns null for malformed JSON")
        void fromJsonMalformedReturnsNull() {
            assertNull(NotificationCenterActionData.fromJson("not json"));
            assertNull(NotificationCenterActionData.fromJson("{broken"));
        }
    }

    // ========================================================================
    // PARTY INVITE ACTION DATA
    // ========================================================================

    @Nested
    @DisplayName("PartyInviteActionData")
    class PartyInviteActionDataTests {

        @Test
        @DisplayName("Null inviteId throws NullPointerException")
        void nullInviteIdThrows() {
            assertThrows(NullPointerException.class, () ->
                new PartyInviteActionData(null, "Player", 0, 1000L));
        }

        @Test
        @DisplayName("Null senderName throws NullPointerException")
        void nullSenderNameThrows() {
            assertThrows(NullPointerException.class, () ->
                new PartyInviteActionData(UUID.randomUUID(), null, 0, 1000L));
        }

        @Test
        @DisplayName("Valid construction preserves all fields")
        void validConstruction() {
            UUID inviteId = UUID.randomUUID();
            PartyInviteActionData data = new PartyInviteActionData(inviteId, "TestPlayer", 2, 99999L);

            assertEquals(inviteId, data.inviteId());
            assertEquals("TestPlayer", data.senderName());
            assertEquals(2, data.questTypeOrdinal());
            assertEquals(99999L, data.expiresAt());
        }

        @Test
        @DisplayName("toJson and fromJson round-trip")
        void roundTrip() {
            UUID inviteId = UUID.randomUUID();
            PartyInviteActionData original = new PartyInviteActionData(
                inviteId, "SenderName", 1, System.currentTimeMillis() + 60000);

            String json = original.toJson();
            assertNotNull(json);
            assertFalse(json.isBlank());

            PartyInviteActionData parsed = PartyInviteActionData.fromJson(json);
            assertNotNull(parsed);
            assertEquals(inviteId, parsed.inviteId());
            assertEquals("SenderName", parsed.senderName());
            assertEquals(1, parsed.questTypeOrdinal());
            assertEquals(original.expiresAt(), parsed.expiresAt());
        }

        @Test
        @DisplayName("fromJson returns null for null input")
        void fromJsonNullReturnsNull() {
            assertNull(PartyInviteActionData.fromJson(null));
        }

        @Test
        @DisplayName("fromJson returns null for blank input")
        void fromJsonBlankReturnsNull() {
            assertNull(PartyInviteActionData.fromJson(""));
            assertNull(PartyInviteActionData.fromJson("   "));
        }

        @Test
        @DisplayName("fromJson returns null for malformed JSON")
        void fromJsonMalformedReturnsNull() {
            assertNull(PartyInviteActionData.fromJson("not json"));
            assertNull(PartyInviteActionData.fromJson("{broken"));
        }

        @Test
        @DisplayName("Round-trip with special characters in sender name")
        void roundTripWithSpecialChars() {
            UUID inviteId = UUID.randomUUID();
            PartyInviteActionData original = new PartyInviteActionData(
                inviteId, "Player \"The Best\" <3", 0, 12345L);

            String json = original.toJson();
            PartyInviteActionData parsed = PartyInviteActionData.fromJson(json);

            assertNotNull(parsed);
            assertEquals("Player \"The Best\" <3", parsed.senderName());
        }
    }
}
