package com.devmod.party;

import com.devmod.endurance.QuestType;

import java.util.UUID;

/**
 * Represents an invitation to join a party for an Endurance Quest.
 * Invites have a timeout and can be accepted, declined, or expire.
 */
public class PartyInvite {

    /** Timeout for invites in milliseconds (30 seconds) */
    public static final long TIMEOUT_MS = 30_000;

    /** Unique identifier for this invite */
    private final UUID inviteId;

    /** UUID of the player who sent the invite (party leader) */
    private final UUID senderId;

    /** Display name of the sender for UI purposes */
    private final String senderName;

    /** UUID of the player receiving the invite */
    private final UUID receiverId;

    /** The party this invite is for */
    private final UUID partyId;

    /** Quest type the party is configured for */
    private final QuestType questType;

    /** Timestamp when the invite was created */
    private final long createdAt;

    /** Current status of the invite */
    private InviteStatus status;

    /**
     * Status of a party invite.
     */
    public enum InviteStatus {
        /** Invite is waiting for response */
        PENDING,
        /** Invite was accepted by the receiver */
        ACCEPTED,
        /** Invite was declined by the receiver */
        DECLINED,
        /** Invite timed out without response */
        EXPIRED,
        /** Invite was cancelled by the sender */
        CANCELLED
    }

    /**
     * Create a new party invite.
     *
     * @param senderId UUID of the sender (party leader)
     * @param senderName Display name of the sender
     * @param receiverId UUID of the receiver
     * @param partyId UUID of the party
     * @param questType Quest type for the party
     */
    public PartyInvite(UUID senderId, String senderName, UUID receiverId,
                       UUID partyId, QuestType questType) {
        this.inviteId = UUID.randomUUID();
        this.senderId = senderId;
        this.senderName = senderName;
        this.receiverId = receiverId;
        this.partyId = partyId;
        this.questType = questType;
        this.createdAt = System.currentTimeMillis();
        this.status = InviteStatus.PENDING;
    }

    /**
     * Check if this invite has expired.
     *
     * @return true if expired, false otherwise
     */
    public boolean isExpired() {
        return System.currentTimeMillis() - createdAt > TIMEOUT_MS;
    }

    /**
     * Get remaining time before expiration in milliseconds.
     *
     * @return Remaining time in ms, or 0 if expired
     */
    public long getRemainingTimeMs() {
        long elapsed = System.currentTimeMillis() - createdAt;
        return Math.max(0, TIMEOUT_MS - elapsed);
    }

    /**
     * Get remaining time before expiration in seconds.
     *
     * @return Remaining time in seconds, or 0 if expired
     */
    public int getRemainingTimeSeconds() {
        return (int) (getRemainingTimeMs() / 1000);
    }

    /**
     * Check if this invite is still pending and not expired.
     *
     * @return true if can still be responded to
     */
    public boolean canRespond() {
        return status == InviteStatus.PENDING && !isExpired();
    }

    /**
     * Accept this invite.
     *
     * @return true if successfully accepted, false if cannot respond
     */
    public boolean accept() {
        if (!canRespond()) {
            return false;
        }
        this.status = InviteStatus.ACCEPTED;
        return true;
    }

    /**
     * Decline this invite.
     *
     * @return true if successfully declined, false if cannot respond
     */
    public boolean decline() {
        if (!canRespond()) {
            return false;
        }
        this.status = InviteStatus.DECLINED;
        return true;
    }

    /**
     * Mark this invite as expired.
     */
    public void markExpired() {
        if (status == InviteStatus.PENDING) {
            this.status = InviteStatus.EXPIRED;
        }
    }

    /**
     * Cancel this invite (called by sender).
     */
    public void cancel() {
        if (status == InviteStatus.PENDING) {
            this.status = InviteStatus.CANCELLED;
        }
    }

    // === Getters ===

    public UUID getInviteId() {
        return inviteId;
    }

    public UUID getSenderId() {
        return senderId;
    }

    public String getSenderName() {
        return senderName;
    }

    public UUID getReceiverId() {
        return receiverId;
    }

    public UUID getPartyId() {
        return partyId;
    }

    public QuestType getQuestType() {
        return questType;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    /**
     * Get the timestamp when this invite will expire.
     *
     * @return Expiration timestamp in milliseconds
     */
    public long getExpiresAt() {
        return createdAt + TIMEOUT_MS;
    }

    public InviteStatus getStatus() {
        return status;
    }

    @Override
    public String toString() {
        return "PartyInvite{" +
                "inviteId=" + inviteId +
                ", sender=" + senderName +
                ", receiver=" + receiverId +
                ", questType=" + questType +
                ", status=" + status +
                ", remainingTime=" + getRemainingTimeSeconds() + "s" +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PartyInvite that = (PartyInvite) o;
        return inviteId.equals(that.inviteId);
    }

    @Override
    public int hashCode() {
        return inviteId.hashCode();
    }
}
