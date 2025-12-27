package com.devmod.mailbox;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for MailboxMessage record.
 */
class MailboxMessageTest {

    private static final UUID TEST_ID = UUID.randomUUID();
    private static final UUID SENDER_UUID = UUID.randomUUID();
    private static final UUID RECIPIENT_UUID = UUID.randomUUID();

    @Nested
    @DisplayName("Message Creation")
    class MessageCreation {

        @Test
        @DisplayName("Builder creates valid message with required fields")
        void builderCreatesValidMessage() {
            MailboxMessage msg = MailboxMessage.builder()
                .id(TEST_ID)
                .sender(SENDER_UUID, "TestSender")
                .recipient(RECIPIENT_UUID)
                .subject("Test Subject")
                .body("Test Body")
                .messageType(MessageType.PLAYER)
                .createdAt(Instant.now())
                .build();

            assertNotNull(msg);
            assertEquals(TEST_ID, msg.id());
            assertEquals(SENDER_UUID, msg.senderUuid());
            assertEquals("TestSender", msg.senderName());
            assertEquals(RECIPIENT_UUID, msg.recipientUuid());
            assertEquals("Test Subject", msg.subject());
            assertEquals("Test Body", msg.body());
            assertEquals(MessageType.PLAYER, msg.messageType());
            assertFalse(msg.isRead());
        }

        @Test
        @DisplayName("System message has null sender UUID")
        void systemMessageHasNullSenderUuid() {
            MailboxMessage msg = MailboxMessage.builder()
                .id(TEST_ID)
                .sender(null, "System")
                .recipient(RECIPIENT_UUID)
                .subject("System Notice")
                .body("Important message")
                .messageType(MessageType.SYSTEM)
                .createdAt(Instant.now())
                .build();

            assertNull(msg.senderUuid());
            assertEquals(MessageType.SYSTEM, msg.messageType());
        }
    }

    @Nested
    @DisplayName("Read Status")
    class ReadStatus {

        @Test
        @DisplayName("New message is unread by default")
        void newMessageIsUnread() {
            MailboxMessage msg = createTestMessage();
            assertFalse(msg.isRead());
        }

        @Test
        @DisplayName("withReadAt creates new message with read status")
        void withReadAtCreatesNewMessage() {
            MailboxMessage original = createTestMessage();
            MailboxMessage read = original.withReadAt(Instant.now());

            assertFalse(original.isRead());
            assertTrue(read.isRead());
            assertEquals(original.id(), read.id());
        }
    }

    @Nested
    @DisplayName("Attachments")
    class Attachments {

        @Test
        @DisplayName("Message without attachment returns false for hasAttachment")
        void messageWithoutAttachment() {
            MailboxMessage msg = createTestMessage();
            assertFalse(msg.hasAttachment());
        }

        @Test
        @DisplayName("Message with attachment data has attachment")
        void messageWithAttachment() {
            MailboxMessage msg = MailboxMessage.builder()
                .id(TEST_ID)
                .sender(SENDER_UUID, "TestSender")
                .recipient(RECIPIENT_UUID)
                .subject("Reward")
                .body("Here is your reward!")
                .messageType(MessageType.REWARD)
                .createdAt(Instant.now())
                .attachment("{\"type\":\"currency\",\"amount\":100}")
                .build();

            assertTrue(msg.hasAttachment());
            assertFalse(msg.attachmentClaimed());
        }

        @Test
        @DisplayName("canClaimAttachment returns false if no attachment")
        void cannotClaimNoAttachment() {
            MailboxMessage msg = createTestMessage();
            assertFalse(msg.canClaimAttachment());
        }

        @Test
        @DisplayName("canClaimAttachment returns false if already claimed")
        void cannotClaimAlreadyClaimed() {
            MailboxMessage msg = MailboxMessage.builder()
                .id(TEST_ID)
                .sender(SENDER_UUID, "TestSender")
                .recipient(RECIPIENT_UUID)
                .subject("Reward")
                .body("Here is your reward!")
                .messageType(MessageType.REWARD)
                .createdAt(Instant.now())
                .attachment("{\"type\":\"currency\",\"amount\":100}")
                .attachmentClaimed(true)
                .build();

            assertFalse(msg.canClaimAttachment());
        }

        @Test
        @DisplayName("canClaimAttachment returns true for unclaimed attachment")
        void canClaimUnclaimedAttachment() {
            MailboxMessage msg = MailboxMessage.builder()
                .id(TEST_ID)
                .sender(SENDER_UUID, "TestSender")
                .recipient(RECIPIENT_UUID)
                .subject("Reward")
                .body("Here is your reward!")
                .messageType(MessageType.REWARD)
                .createdAt(Instant.now())
                .attachment("{\"type\":\"currency\",\"amount\":100}")
                .attachmentClaimed(false)
                .build();

            assertTrue(msg.canClaimAttachment());
        }
    }

    @Nested
    @DisplayName("Expiration")
    class Expiration {

        @Test
        @DisplayName("Message without expiration is not expired")
        void messageWithoutExpirationNotExpired() {
            MailboxMessage msg = createTestMessage();
            assertNull(msg.expiresAt());
            assertFalse(msg.isExpired());
        }

        @Test
        @DisplayName("Message with future expiration is not expired")
        void messageWithFutureExpirationNotExpired() {
            MailboxMessage msg = MailboxMessage.builder()
                .id(TEST_ID)
                .sender(SENDER_UUID, "TestSender")
                .recipient(RECIPIENT_UUID)
                .subject("Temporary")
                .body("This expires later")
                .messageType(MessageType.SYSTEM)
                .createdAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();

            assertFalse(msg.isExpired());
        }

        @Test
        @DisplayName("Message with past expiration is expired")
        void messageWithPastExpirationIsExpired() {
            MailboxMessage msg = MailboxMessage.builder()
                .id(TEST_ID)
                .sender(SENDER_UUID, "TestSender")
                .recipient(RECIPIENT_UUID)
                .subject("Expired")
                .body("This has expired")
                .messageType(MessageType.SYSTEM)
                .createdAt(Instant.now().minusSeconds(7200))
                .expiresAt(Instant.now().minusSeconds(3600))
                .build();

            assertTrue(msg.isExpired());
        }
    }

    @Nested
    @DisplayName("Message Types")
    class MessageTypes {

        @Test
        @DisplayName("All message types are valid")
        void allMessageTypesValid() {
            for (MessageType type : MessageType.values()) {
                assertNotNull(type.name());
            }
            assertEquals(4, MessageType.values().length);
        }

        @Test
        @DisplayName("Message types include PLAYER, SYSTEM, ADMIN, REWARD")
        void messageTypesIncludeExpected() {
            assertNotNull(MessageType.PLAYER);
            assertNotNull(MessageType.SYSTEM);
            assertNotNull(MessageType.ADMIN);
            assertNotNull(MessageType.REWARD);
        }
    }

    private MailboxMessage createTestMessage() {
        return MailboxMessage.builder()
            .id(TEST_ID)
            .sender(SENDER_UUID, "TestSender")
            .recipient(RECIPIENT_UUID)
            .subject("Test Subject")
            .body("Test Body")
            .messageType(MessageType.PLAYER)
            .createdAt(Instant.now())
            .build();
    }
}
