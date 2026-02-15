package com.devmod.party;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.devmod.endurance.QuestType;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("PartyInvite")
class PartyInviteTest {

    private UUID senderId;
    private UUID receiverId;
    private UUID partyId;
    private PartyInvite invite;

    @BeforeEach
    void setUp() {
        senderId = UUID.randomUUID();
        receiverId = UUID.randomUUID();
        partyId = UUID.randomUUID();
        invite = new PartyInvite(senderId, "Sender", receiverId, partyId, QuestType.PVE_COOP);
    }

    @Test
    @DisplayName("Newly created invite is PENDING")
    void newInviteIsPending() {
        assertEquals(PartyInvite.InviteStatus.PENDING, invite.getStatus());
        assertTrue(invite.canRespond());
        assertNotNull(invite.getInviteId());
    }

    @Test
    @DisplayName("Invite has correct sender/receiver info")
    void inviteHasCorrectInfo() {
        assertEquals(senderId, invite.getSenderId());
        assertEquals("Sender", invite.getSenderName());
        assertEquals(receiverId, invite.getReceiverId());
        assertEquals(partyId, invite.getPartyId());
        assertEquals(QuestType.PVE_COOP, invite.getQuestType());
    }

    @Test
    @DisplayName("TIMEOUT_MS is 30 seconds")
    void timeoutIs30Seconds() {
        assertEquals(30_000, PartyInvite.TIMEOUT_MS);
    }

    @Test
    @DisplayName("getExpiresAt is createdAt + TIMEOUT_MS")
    void expiresAtCalculation() {
        assertEquals(invite.getCreatedAt() + PartyInvite.TIMEOUT_MS, invite.getExpiresAt());
    }

    @Nested
    @DisplayName("Expiry")
    class Expiry {

        @Test
        @DisplayName("Not expired immediately after creation")
        void notExpiredImmediately() {
            assertFalse(invite.isExpiredAt(invite.getCreatedAt()));
            assertFalse(invite.isExpiredAt(invite.getCreatedAt() + 29_000));
        }

        @Test
        @DisplayName("Expired after timeout")
        void expiredAfterTimeout() {
            assertTrue(invite.isExpiredAt(invite.getCreatedAt() + PartyInvite.TIMEOUT_MS + 1));
        }

        @Test
        @DisplayName("markExpired changes status to EXPIRED from PENDING")
        void markExpired() {
            invite.markExpired();
            assertEquals(PartyInvite.InviteStatus.EXPIRED, invite.getStatus());
        }

        @Test
        @DisplayName("markExpired no-op when already accepted")
        void markExpiredNoOpWhenAccepted() {
            invite.accept();
            invite.markExpired();
            assertEquals(PartyInvite.InviteStatus.ACCEPTED, invite.getStatus());
        }
    }

    @Nested
    @DisplayName("Accept/Decline")
    class AcceptDecline {

        @Test
        @DisplayName("Accept succeeds when pending and not expired")
        void acceptSuccess() {
            assertTrue(invite.accept());
            assertEquals(PartyInvite.InviteStatus.ACCEPTED, invite.getStatus());
        }

        @Test
        @DisplayName("Decline succeeds when pending and not expired")
        void declineSuccess() {
            assertTrue(invite.decline());
            assertEquals(PartyInvite.InviteStatus.DECLINED, invite.getStatus());
        }

        @Test
        @DisplayName("Cannot accept already accepted invite")
        void cannotDoubleAccept() {
            invite.accept();
            assertFalse(invite.accept());
        }

        @Test
        @DisplayName("Cannot decline already declined invite")
        void cannotDoubleDecline() {
            invite.decline();
            assertFalse(invite.decline());
        }

        @Test
        @DisplayName("Cannot accept after decline")
        void cannotAcceptAfterDecline() {
            invite.decline();
            assertFalse(invite.accept());
        }

        @Test
        @DisplayName("canRespond false when cancelled")
        void canRespondFalseWhenCancelled() {
            invite.cancel();
            assertFalse(invite.canRespond());
        }
    }

    @Nested
    @DisplayName("Cancel")
    class Cancel {

        @Test
        @DisplayName("Cancel changes status from PENDING to CANCELLED")
        void cancelSuccess() {
            invite.cancel();
            assertEquals(PartyInvite.InviteStatus.CANCELLED, invite.getStatus());
        }

        @Test
        @DisplayName("Cancel no-op when not PENDING")
        void cancelNoOpWhenAccepted() {
            invite.accept();
            invite.cancel();
            assertEquals(PartyInvite.InviteStatus.ACCEPTED, invite.getStatus());
        }
    }

    @Nested
    @DisplayName("Equality")
    class Equality {

        @Test
        @DisplayName("Equals based on inviteId")
        void equalsByInviteId() {
            assertEquals(invite, invite);
            assertNotEquals(invite, new PartyInvite(senderId, "Sender", receiverId, partyId, QuestType.PVE_COOP));
        }

        @Test
        @DisplayName("hashCode based on inviteId")
        void hashCodeConsistent() {
            assertEquals(invite.hashCode(), invite.hashCode());
        }

        @Test
        @DisplayName("Not equal to null or different type")
        void notEqualToNullOrDifferentType() {
            assertNotEquals(invite, null);
            assertNotEquals(invite, "not an invite");
        }
    }

    @Test
    @DisplayName("toString contains useful info")
    void toStringContainsInfo() {
        String str = invite.toString();
        assertTrue(str.contains("PartyInvite"));
        assertTrue(str.contains("Sender"));
        assertTrue(str.contains("PENDING"));
    }
}
