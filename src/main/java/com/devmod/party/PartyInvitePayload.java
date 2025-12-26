package com.devmod.party;

import java.util.Objects;
import java.util.UUID;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import com.devmod.endurance.QuestType;

/**
 * Payload sent from client to server to invite a player to a party.
 * The server will validate the invite and forward a notification to the target player.
 */
public record PartyInvitePayload(
    UUID targetPlayerId,
    int questTypeOrdinal
) implements CustomPacketPayload {

    public static final Type<PartyInvitePayload> TYPE = new Type<>(
        Objects.requireNonNull(ResourceLocation.fromNamespaceAndPath("devmod", "party_invite"))
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, PartyInvitePayload> STREAM_CODEC = Objects.requireNonNull(StreamCodec.composite(
        Objects.requireNonNull(ByteBufCodecs.STRING_UTF8.map(s -> UUID.fromString(Objects.requireNonNull(s)), uuid -> Objects.requireNonNull(uuid.toString()))), PartyInvitePayload::targetPlayerId,
        Objects.requireNonNull(ByteBufCodecs.VAR_INT), PartyInvitePayload::questTypeOrdinal,
        (a, b) -> new PartyInvitePayload(a, b)
    ));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * Get the quest type from the ordinal.
     * Returns PVE_COOP as default if ordinal is invalid.
     */
    public QuestType getQuestType() {
        QuestType[] values = QuestType.values();
        if (questTypeOrdinal >= 0 && questTypeOrdinal < values.length) {
            return values[questTypeOrdinal];
        }
        return QuestType.PVE_COOP;
    }

    /**
     * Create a payload with the quest type enum.
     */
    public static PartyInvitePayload create(UUID targetPlayerId, QuestType questType) {
        return new PartyInvitePayload(targetPlayerId, questType.ordinal());
    }
}
