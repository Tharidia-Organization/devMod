package com.devmod.party;

import java.util.Objects;
import java.util.UUID;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import com.devmod.endurance.QuestType;

/**
 * Payload sent from server to client for party notifications.
 * Handles: invite received, member joined/left, kicked, disbanded, quest started, etc.
 */
public record PartyNotificationPayload(
    NotificationType notificationType,
    UUID relatedId,           // Invite ID, Player ID, or Party ID depending on type
    String playerName,        // Name of the player involved (sender, joiner, etc.)
    int questTypeOrdinal,     // Quest type ordinal for invite notifications
    long expiresAt            // Expiration timestamp for invites (0 if N/A)
) implements CustomPacketPayload {

    // Security limits
    private static final int MAX_NAME_LENGTH = 64;

    public static final Type<PartyNotificationPayload> TYPE = new Type<>(
        Objects.requireNonNull(ResourceLocation.fromNamespaceAndPath("devmod", "party_notification"))
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, PartyNotificationPayload> STREAM_CODEC = StreamCodec.of(
        PartyNotificationPayload::encode,
        PartyNotificationPayload::decode
    );

    private static void encode(RegistryFriendlyByteBuf buf, PartyNotificationPayload payload) {
        buf.writeVarInt(payload.notificationType.ordinal());
        buf.writeUUID(Objects.requireNonNull(payload.relatedId));
        buf.writeUtf(Objects.requireNonNull(payload.playerName));
        buf.writeVarInt(payload.questTypeOrdinal);
        buf.writeLong(payload.expiresAt);
    }

    private static PartyNotificationPayload decode(RegistryFriendlyByteBuf buf) {
        int typeOrdinal = buf.readVarInt();
        NotificationType type = NotificationType.fromOrdinal(typeOrdinal);
        UUID relatedId = Objects.requireNonNull(buf.readUUID());
        String playerName = Objects.requireNonNull(buf.readUtf(MAX_NAME_LENGTH));
        int questTypeOrdinal = buf.readVarInt();
        long expiresAt = buf.readLong();
        return new PartyNotificationPayload(type, relatedId, playerName, questTypeOrdinal, expiresAt);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * Get the quest type from ordinal.
     */
    public QuestType getQuestType() {
        QuestType[] values = QuestType.values();
        if (questTypeOrdinal >= 0 && questTypeOrdinal < values.length) {
            return values[questTypeOrdinal];
        }
        return QuestType.PVE_COOP;
    }

    /**
     * Types of party notifications.
     */
    public enum NotificationType {
        // Invite-related
        INVITE_RECEIVED,        // You received an invite (relatedId = inviteId)
        INVITE_ACCEPTED,        // Someone accepted your invite (relatedId = playerId)
        INVITE_DECLINED,        // Someone declined your invite (relatedId = playerId)
        INVITE_EXPIRED,         // An invite you sent expired (relatedId = inviteId)
        INVITE_CANCELLED,       // An invite was cancelled (relatedId = inviteId)

        // Member-related
        MEMBER_JOINED,          // A player joined the party (relatedId = playerId)
        MEMBER_LEFT,            // A player left the party (relatedId = playerId)
        MEMBER_KICKED,          // A player was kicked (relatedId = playerId)
        YOU_WERE_KICKED,        // You were kicked from party (relatedId = partyId)

        // Party state
        PARTY_DISBANDED,        // The party was disbanded (relatedId = partyId)
        LEADER_CHANGED,         // Leadership was transferred (relatedId = new leader playerId)
        QUEST_STARTING,         // Quest is about to start (relatedId = partyId)
        QUEST_TYPE_CHANGED,     // Party quest type changed (relatedId = partyId)

        // Ready status
        MEMBER_READY,           // A member marked ready (relatedId = playerId)
        MEMBER_NOT_READY,       // A member unmarked ready (relatedId = playerId)
        ALL_READY,              // All members are ready (relatedId = partyId)

        // Errors
        ERROR;                  // Generic error (playerName contains error message)

        public static NotificationType fromOrdinal(int ordinal) {
            NotificationType[] values = values();
            if (ordinal >= 0 && ordinal < values.length) {
                return values[ordinal];
            }
            return ERROR;
        }
    }

    // === Factory methods ===

    /**
     * Create an invite received notification.
     */
    public static PartyNotificationPayload inviteReceived(UUID inviteId, String senderName,
            QuestType questType, long expiresAt) {
        return new PartyNotificationPayload(
            NotificationType.INVITE_RECEIVED, inviteId, senderName, questType.ordinal(), expiresAt
        );
    }

    /**
     * Create an invite response notification (accepted/declined).
     */
    public static PartyNotificationPayload inviteResponse(boolean accepted, UUID playerId, String playerName) {
        return new PartyNotificationPayload(
            accepted ? NotificationType.INVITE_ACCEPTED : NotificationType.INVITE_DECLINED,
            playerId, playerName, 0, 0
        );
    }

    /**
     * Create a member joined notification.
     */
    public static PartyNotificationPayload memberJoined(UUID playerId, String playerName) {
        return new PartyNotificationPayload(
            NotificationType.MEMBER_JOINED, playerId, playerName, 0, 0
        );
    }

    /**
     * Create a member left notification.
     */
    public static PartyNotificationPayload memberLeft(UUID playerId, String playerName) {
        return new PartyNotificationPayload(
            NotificationType.MEMBER_LEFT, playerId, playerName, 0, 0
        );
    }

    /**
     * Create a member kicked notification.
     */
    public static PartyNotificationPayload memberKicked(UUID playerId, String playerName) {
        return new PartyNotificationPayload(
            NotificationType.MEMBER_KICKED, playerId, playerName, 0, 0
        );
    }

    /**
     * Create a "you were kicked" notification.
     */
    public static PartyNotificationPayload youWereKicked(UUID partyId, String leaderName) {
        return new PartyNotificationPayload(
            NotificationType.YOU_WERE_KICKED, partyId, leaderName, 0, 0
        );
    }

    /**
     * Create a party disbanded notification.
     */
    public static PartyNotificationPayload partyDisbanded(UUID partyId, String leaderName) {
        return new PartyNotificationPayload(
            NotificationType.PARTY_DISBANDED, partyId, leaderName, 0, 0
        );
    }

    /**
     * Create a leader changed notification.
     */
    public static PartyNotificationPayload leaderChanged(UUID newLeaderId, String newLeaderName) {
        return new PartyNotificationPayload(
            NotificationType.LEADER_CHANGED, newLeaderId, newLeaderName, 0, 0
        );
    }

    /**
     * Create a quest starting notification.
     */
    public static PartyNotificationPayload questStarting(UUID partyId, QuestType questType) {
        return new PartyNotificationPayload(
            NotificationType.QUEST_STARTING, partyId, "", questType.ordinal(), 0
        );
    }

    /**
     * Create an error notification.
     */
    public static PartyNotificationPayload error(String errorMessage) {
        return new PartyNotificationPayload(
            NotificationType.ERROR, new UUID(0, 0), errorMessage, 0, 0
        );
    }

    /**
     * Create a ready status notification.
     */
    public static PartyNotificationPayload readyStatus(UUID playerId, String playerName, boolean ready) {
        return new PartyNotificationPayload(
            ready ? NotificationType.MEMBER_READY : NotificationType.MEMBER_NOT_READY,
            playerId, playerName, 0, 0
        );
    }

    /**
     * Create an all ready notification.
     */
    public static PartyNotificationPayload allReady(UUID partyId) {
        return new PartyNotificationPayload(
            NotificationType.ALL_READY, partyId, "", 0, 0
        );
    }
}
