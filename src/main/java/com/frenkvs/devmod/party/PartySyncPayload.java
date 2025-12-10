package com.frenkvs.devmod.party;

import com.frenkvs.devmod.endurance.QuestType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Payload sent from server to client to synchronize full party state.
 * Sent when: joining party, member changes, ready status changes, quest type changes.
 */
public record PartySyncPayload(
    boolean hasParty,
    UUID partyId,
    UUID leaderId,
    List<PartyMemberInfo> members,
    int questTypeOrdinal,
    int stateOrdinal,
    UUID instanceId          // null (zero UUID) if not in quest
) implements CustomPacketPayload {

    // Security limits
    private static final int MAX_MEMBERS = 20;
    private static final int MAX_NAME_LENGTH = 64;

    public static final Type<PartySyncPayload> TYPE = new Type<>(
        Objects.requireNonNull(ResourceLocation.fromNamespaceAndPath("devmod", "party_sync"))
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, PartySyncPayload> STREAM_CODEC = StreamCodec.of(
        PartySyncPayload::encode,
        PartySyncPayload::decode
    );

    private static void encode(RegistryFriendlyByteBuf buf, PartySyncPayload payload) {
        buf.writeBoolean(payload.hasParty);
        buf.writeUUID(payload.partyId);
        buf.writeUUID(payload.leaderId);

        // Write members list
        buf.writeVarInt(payload.members.size());
        for (PartyMemberInfo member : payload.members) {
            buf.writeUUID(member.playerId);
            buf.writeUtf(member.playerName);
            buf.writeBoolean(member.isReady);
            buf.writeBoolean(member.isLeader);
            buf.writeBoolean(member.isOnline);
        }

        buf.writeVarInt(payload.questTypeOrdinal);
        buf.writeVarInt(payload.stateOrdinal);
        buf.writeUUID(payload.instanceId != null ? payload.instanceId : new UUID(0, 0));
    }

    private static PartySyncPayload decode(RegistryFriendlyByteBuf buf) {
        boolean hasParty = buf.readBoolean();
        UUID partyId = buf.readUUID();
        UUID leaderId = buf.readUUID();

        // Read members list with security bounds
        int memberCount = buf.readVarInt();
        if (memberCount < 0 || memberCount > MAX_MEMBERS) {
            memberCount = 0;
        }
        List<PartyMemberInfo> members = new ArrayList<>(memberCount);
        for (int i = 0; i < memberCount; i++) {
            UUID playerId = buf.readUUID();
            String playerName = buf.readUtf(MAX_NAME_LENGTH);
            boolean isReady = buf.readBoolean();
            boolean isLeader = buf.readBoolean();
            boolean isOnline = buf.readBoolean();
            members.add(new PartyMemberInfo(playerId, playerName, isReady, isLeader, isOnline));
        }

        int questTypeOrdinal = buf.readVarInt();
        int stateOrdinal = buf.readVarInt();
        UUID instanceId = buf.readUUID();

        // Convert zero UUID back to null
        if (instanceId.getMostSignificantBits() == 0 && instanceId.getLeastSignificantBits() == 0) {
            instanceId = null;
        }

        return new PartySyncPayload(hasParty, partyId, leaderId, members, questTypeOrdinal, stateOrdinal, instanceId);
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
     * Get the party state from ordinal.
     */
    public PartyData.PartyState getState() {
        PartyData.PartyState[] values = PartyData.PartyState.values();
        if (stateOrdinal >= 0 && stateOrdinal < values.length) {
            return values[stateOrdinal];
        }
        return PartyData.PartyState.FORMING;
    }

    /**
     * Check if player is the leader.
     */
    public boolean isLeader(UUID playerId) {
        return leaderId.equals(playerId);
    }

    /**
     * Get member info by player ID.
     */
    public PartyMemberInfo getMember(UUID playerId) {
        for (PartyMemberInfo member : members) {
            if (member.playerId.equals(playerId)) {
                return member;
            }
        }
        return null;
    }

    /**
     * Create an empty payload (no party).
     */
    public static PartySyncPayload empty() {
        return new PartySyncPayload(
            false,
            new UUID(0, 0),
            new UUID(0, 0),
            List.of(),
            0,
            0,
            null
        );
    }

    /**
     * Create a sync payload from a PartyData object.
     *
     * @param party The party data to sync
     * @param onlineChecker Function to check if a player is online
     * @return The sync payload
     */
    public static PartySyncPayload fromParty(PartyData party, java.util.function.Predicate<UUID> onlineChecker) {
        List<PartyMemberInfo> memberInfos = new ArrayList<>();

        for (UUID memberId : party.getMembers()) {
            String name = party.getMemberName(memberId);
            boolean isReady = party.isReady(memberId);
            boolean isLeader = party.isLeader(memberId);
            boolean isOnline = onlineChecker != null ? onlineChecker.test(memberId) : true;

            memberInfos.add(new PartyMemberInfo(memberId, name, isReady, isLeader, isOnline));
        }

        return new PartySyncPayload(
            true,
            party.getPartyId(),
            party.getLeaderId(),
            memberInfos,
            party.getQuestType().ordinal(),
            party.getState().ordinal(),
            party.getInstanceId()
        );
    }

    /**
     * Information about a single party member.
     */
    public record PartyMemberInfo(
        UUID playerId,
        String playerName,
        boolean isReady,
        boolean isLeader,
        boolean isOnline
    ) {
        /**
         * Get display status string.
         */
        public String getStatusDisplay() {
            if (!isOnline) return "§7[OFFLINE]";
            if (isLeader) return "§6[LEADER]";
            if (isReady) return "§a[READY]";
            return "§c[NOT READY]";
        }
    }
}
