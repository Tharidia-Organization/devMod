package com.frenkvs.devmod.party;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Payload sent from server to client to update quest start sequence status.
 * Used to show countdown, teleport progress, arrival status, and sync status on client UI.
 */
public record QuestSequencePayload(
    UUID partyId,
    Phase phase,
    int secondsRemaining,
    int totalMembers,
    List<UUID> arrivedMembers
) implements CustomPacketPayload {

    public static final Type<QuestSequencePayload> TYPE = new Type<>(
        Objects.requireNonNull(ResourceLocation.fromNamespaceAndPath("devmod", "quest_sequence"))
    );

    // Security limits
    private static final int MAX_MEMBERS = 20;

    public static final StreamCodec<RegistryFriendlyByteBuf, QuestSequencePayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        @Nonnull
        public QuestSequencePayload decode(@Nonnull RegistryFriendlyByteBuf buf) {
            UUID partyId = Objects.requireNonNull(buf.readUUID());
            int phaseOrdinal = buf.readVarInt();
            Phase phase = Phase.values()[Math.min(phaseOrdinal, Phase.values().length - 1)];
            int secondsRemaining = buf.readVarInt();
            int totalMembers = buf.readVarInt();

            // Read arrived members
            int arrivedCount = buf.readVarInt();
            arrivedCount = Math.min(arrivedCount, MAX_MEMBERS);
            List<UUID> arrivedMembers = new ArrayList<>(arrivedCount);
            for (int i = 0; i < arrivedCount; i++) {
                arrivedMembers.add(Objects.requireNonNull(buf.readUUID()));
            }

            return new QuestSequencePayload(partyId, phase, secondsRemaining, totalMembers, arrivedMembers);
        }

        @Override
        public void encode(@Nonnull RegistryFriendlyByteBuf buf, @Nonnull QuestSequencePayload payload) {
            buf.writeUUID(Objects.requireNonNull(payload.partyId));
            buf.writeVarInt(payload.phase.ordinal());
            buf.writeVarInt(payload.secondsRemaining);
            buf.writeVarInt(payload.totalMembers);

            // Write arrived members
            List<UUID> arrived = payload.arrivedMembers;
            buf.writeVarInt(Math.min(arrived.size(), MAX_MEMBERS));
            int written = 0;
            for (UUID memberId : arrived) {
                if (written >= MAX_MEMBERS) break;
                buf.writeUUID(Objects.requireNonNull(memberId));
                written++;
            }
        }
    };

    /**
     * Convenience constructor for simple updates (backward compatible).
     */
    public QuestSequencePayload(UUID partyId, Phase phase, int secondsRemaining) {
        this(partyId, phase, secondsRemaining, 0, List.of());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * Get arrived count for UI display.
     */
    public int arrivedCount() {
        return arrivedMembers.size();
    }

    /**
     * Check if a specific player has arrived.
     */
    public boolean hasArrived(UUID playerId) {
        return arrivedMembers.contains(playerId);
    }

    /**
     * Phases of the quest start sequence.
     */
    public enum Phase {
        /** Pre-teleport countdown (5-4-3-2-1) */
        COUNTDOWN_START,
        /** Teleporting players to arena */
        TELEPORTING,
        /** Waiting for all players to confirm arrival */
        WAITING_FOR_ARRIVALS,
        /** All arrived, syncing/wave countdown (3-2-1) */
        SYNCING,
        /** Quest is starting (final countdown) */
        STARTING,
        /** Quest has started */
        STARTED,
        /** Sequence was cancelled */
        CANCELLED
    }
}
