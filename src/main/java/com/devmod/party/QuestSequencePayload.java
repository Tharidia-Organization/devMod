package com.devmod.party;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import javax.annotation.Nonnull;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import com.devmod.network.PayloadValidation;

public record QuestSequencePayload(
    UUID partyId,
    Phase phase,
    int secondsRemaining,
    int totalMembers,
    List<UUID> arrivedMembers,
    String title,
    String subtitle,
    List<String> infoLines
) implements CustomPacketPayload, PayloadValidation.SizedPayload {

    public static final Type<QuestSequencePayload> TYPE = new Type<>(
        Objects.requireNonNull(ResourceLocation.fromNamespaceAndPath("devmod", "quest_sequence"))
    );

    // Security limits
    private static final int MAX_MEMBERS = 20;
    private static final int MAX_TEXT_LENGTH = 128;
    private static final int MAX_INFO_LINES = 6;

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

            String title = buf.readUtf(MAX_TEXT_LENGTH);
            String subtitle = buf.readUtf(MAX_TEXT_LENGTH);

            int infoCount = Math.min(buf.readVarInt(), MAX_INFO_LINES);
            List<String> infoLines = new ArrayList<>(infoCount);
            for (int i = 0; i < infoCount; i++) {
                infoLines.add(buf.readUtf(MAX_TEXT_LENGTH));
            }

            return new QuestSequencePayload(partyId, phase, secondsRemaining, totalMembers, arrivedMembers,
                title, subtitle, infoLines);
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

            buf.writeUtf(payload.title != null ? payload.title : "", MAX_TEXT_LENGTH);
            buf.writeUtf(payload.subtitle != null ? payload.subtitle : "", MAX_TEXT_LENGTH);
            List<String> lines = payload.infoLines != null ? payload.infoLines : List.of();
            buf.writeVarInt(Math.min(lines.size(), MAX_INFO_LINES));
            int lineWritten = 0;
            for (String line : lines) {
                if (lineWritten >= MAX_INFO_LINES) break;
                buf.writeUtf(line != null ? line : "", MAX_TEXT_LENGTH);
                lineWritten++;
            }
        }
    };

    /**
     * Convenience constructor for simple updates (backward compatible).
     */
    public QuestSequencePayload(UUID partyId, Phase phase, int secondsRemaining) {
        this(partyId, phase, secondsRemaining, 0, List.of(), "", "", List.of());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    @Override
    public int estimatedSize() {
        int size = 16; // partyId
        size += varIntSize(phase.ordinal());
        size += varIntSize(secondsRemaining);
        size += varIntSize(totalMembers);
        size += varIntSize(arrivedMembers.size());
        size += arrivedMembers.size() * 16;
        size += estimatedUtfSize(title);
        size += estimatedUtfSize(subtitle);
        size += varIntSize(infoLines.size());
        for (String line : infoLines) {
            size += estimatedUtfSize(line);
        }
        return size;
    }

    private static int estimatedUtfSize(String value) {
        if (value == null || value.isEmpty()) {
            return varIntSize(0);
        }
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        return varIntSize(bytes.length) + bytes.length;
    }

    private static int varIntSize(int value) {
        int v = value;
        int size = 1;
        while ((v & ~0x7F) != 0) {
            v >>>= 7;
            size++;
        }
        return size;
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
        CANCELLED,
        /** Pre-quest briefing */
        BRIEFING,
        /** Safe window after teleport */
        SAFE_WINDOW,
        /** Wave countdown */
        WAVE_INCOMING,
        /** Boss intro pause */
        BOSS_INTRO
    }
}
