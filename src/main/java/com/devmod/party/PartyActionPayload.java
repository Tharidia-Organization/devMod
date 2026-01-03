package com.devmod.party;

import java.util.Objects;
import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import com.devmod.network.PayloadValidation;

public record PartyActionPayload(
    Action action,
    @Nullable UUID targetPlayerId,  // For KICK action
    int questTypeOrdinal,           // For SET_QUEST_TYPE action
    @Nullable String mobId          // For SET_MOB_TYPE action (ResourceLocation as string)
) implements CustomPacketPayload, PayloadValidation.SizedPayload {

    public static final Type<PartyActionPayload> TYPE = new Type<>(
        Objects.requireNonNull(ResourceLocation.fromNamespaceAndPath("devmod", "party_action"))
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, PartyActionPayload> STREAM_CODEC = StreamCodec.of(
        PartyActionPayload::encode,
        PartyActionPayload::decode
    );

    private static void encode(RegistryFriendlyByteBuf buf, PartyActionPayload payload) {
        buf.writeVarInt(payload.action.ordinal());

        buf.writeBoolean(payload.targetPlayerId != null);
        if (payload.targetPlayerId != null) {
            buf.writeUUID(payload.targetPlayerId);
        }

        buf.writeVarInt(payload.questTypeOrdinal);

        buf.writeBoolean(payload.mobId != null);
        if (payload.mobId != null) {
            buf.writeUtf(payload.mobId);
        }
    }

    private static PartyActionPayload decode(RegistryFriendlyByteBuf buf) {
        int actionOrdinal = buf.readVarInt();
        Action action = Action.values()[Math.min(actionOrdinal, Action.values().length - 1)];

        UUID targetPlayerId = null;
        if (buf.readBoolean()) {
            targetPlayerId = buf.readUUID();
        }

        int questTypeOrdinal = buf.readVarInt();

        String mobId = null;
        if (buf.readBoolean()) {
            mobId = buf.readUtf(128);
        }

        return new PartyActionPayload(action, targetPlayerId, questTypeOrdinal, mobId);
    }

    /**
     * Party action types.
     */
    public enum Action {
        /** Toggle ready status */
        TOGGLE_READY,
        /** Leave the party */
        LEAVE_PARTY,
        /** Kick a member (leader only) */
        KICK_MEMBER,
        /** Change quest type (leader only) */
        SET_QUEST_TYPE,
        /** Change mob type for the quest (leader only) */
        SET_MOB_TYPE,
        /** Disband the party (leader only) */
        DISBAND_PARTY,
        /** Start the quest (leader only, all ready) */
        START_QUEST,
        /** Create a new party */
        CREATE_PARTY
    }

    // Factory methods for common actions

    public static PartyActionPayload toggleReady() {
        return new PartyActionPayload(Action.TOGGLE_READY, null, 0, null);
    }

    public static PartyActionPayload leaveParty() {
        return new PartyActionPayload(Action.LEAVE_PARTY, null, 0, null);
    }

    public static PartyActionPayload kickMember(UUID playerId) {
        return new PartyActionPayload(Action.KICK_MEMBER, playerId, 0, null);
    }

    public static PartyActionPayload setQuestType(com.devmod.endurance.QuestType questType) {
        return new PartyActionPayload(Action.SET_QUEST_TYPE, null, questType.ordinal(), null);
    }

    public static PartyActionPayload setMobType(ResourceLocation mobId) {
        return new PartyActionPayload(Action.SET_MOB_TYPE, null, 0, mobId.toString());
    }

    public static PartyActionPayload disbandParty() {
        return new PartyActionPayload(Action.DISBAND_PARTY, null, 0, null);
    }

    public static PartyActionPayload startQuest() {
        return new PartyActionPayload(Action.START_QUEST, null, 0, null);
    }

    public static PartyActionPayload createParty(com.devmod.endurance.QuestType questType) {
        return new PartyActionPayload(Action.CREATE_PARTY, null, questType.ordinal(), null);
    }

    /**
     * Get the QuestType from ordinal (for SET_QUEST_TYPE action).
     */
    public com.devmod.endurance.QuestType getQuestType() {
        return com.devmod.endurance.QuestType.fromOrdinal(questTypeOrdinal);
    }

    /**
     * Get the mob ID as ResourceLocation (for SET_MOB_TYPE action).
     */
    @Nullable
    public ResourceLocation getMobResourceLocation() {
        String localMobId = mobId;
        if (localMobId == null || localMobId.isEmpty()) {
            return null;
        }
        return ResourceLocation.tryParse(localMobId);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    @Override
    public int estimatedSize() {
        int size = 1; // action ordinal (varint, typically 1 byte)
        size += 1; // boolean for targetPlayerId presence
        if (targetPlayerId != null) {
            size += 16; // UUID
        }
        size += 1; // questTypeOrdinal (varint, typically 1 byte)
        size += 1; // boolean for mobId presence
        String mob = mobId;
        if (mob != null) {
            size += varIntSize(mob.length()) + mob.length();
        }
        return size;
    }

    private static int varIntSize(int value) {
        int size = 1;
        while ((value & ~0x7F) != 0) {
            value >>>= 7;
            size++;
        }
        return size;
    }
}
