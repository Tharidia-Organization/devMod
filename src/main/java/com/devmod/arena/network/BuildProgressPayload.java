package com.devmod.arena.network;

import java.util.Objects;
import java.util.UUID;

import javax.annotation.Nonnull;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import com.devmod.arena.BuildPhase;
import com.devmod.arena.ProgressFlags;

public record BuildProgressPayload(
    UUID arenaId,
    BuildPhase phase,
    int progressBps,     // Basis points: 0-10000 (0.00% - 100.00%)
    int blocksPlaced,
    int totalBlocks,
    int flags
) implements CustomPacketPayload {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** Maximum valid progress in basis points (100.00%) */
    public static final int MAX_PROGRESS_BPS = 10000;

    /** Maximum allowed total blocks (prevents overflow attacks) */
    public static final int MAX_BLOCKS = 10_000_000;

    /** Maximum valid flags value (8-bit) */
    public static final int MAX_FLAGS = 0xFF;

    /**
     * Compact constructor with bounds validation and clamping.
     * Ensures all values are within safe ranges to prevent overflow attacks.
     */
    public BuildProgressPayload {
        Objects.requireNonNull(arenaId, "arenaId cannot be null");
        Objects.requireNonNull(phase, "phase cannot be null");

        // Clamp progressBps to valid range [0, 10000]
        progressBps = Math.max(0, Math.min(progressBps, MAX_PROGRESS_BPS));

        // Clamp blocksPlaced to valid range [0, MAX_BLOCKS]
        blocksPlaced = Math.max(0, Math.min(blocksPlaced, MAX_BLOCKS));

        // Clamp totalBlocks to valid range [0, MAX_BLOCKS]
        totalBlocks = Math.max(0, Math.min(totalBlocks, MAX_BLOCKS));

        // Ensure blocksPlaced doesn't exceed totalBlocks
        if (blocksPlaced > totalBlocks) {
            blocksPlaced = totalBlocks;
        }

        // Clamp flags to valid range [0, 255]
        flags = flags & MAX_FLAGS;
    }

    /** Packet type identifier */
    public static final Type<BuildProgressPayload> TYPE = new Type<>(
        Objects.requireNonNull(ResourceLocation.fromNamespaceAndPath("devmod", "build_progress"))
    );

    /** Stream codec for network serialization */
    public static final StreamCodec<RegistryFriendlyByteBuf, BuildProgressPayload> STREAM_CODEC = StreamCodec.of(
        BuildProgressPayload::encode,
        BuildProgressPayload::decode
    );

    /**
     * Creates a progress payload from a percentage (0.0 - 1.0).
     */
    public static BuildProgressPayload of(UUID arenaId, BuildPhase phase, double progress,
                                           int blocksPlaced, int totalBlocks, int flags) {
        int progressBps = (int) (progress * 10000);
        return new BuildProgressPayload(arenaId, phase, progressBps, blocksPlaced, totalBlocks, flags);
    }

    /**
     * Creates a completion payload.
     */
    public static BuildProgressPayload complete(UUID arenaId, int totalBlocks) {
        return new BuildProgressPayload(
            arenaId,
            BuildPhase.COMPLETE,
            10000, // 100%
            totalBlocks,
            totalBlocks,
            ProgressFlags.COMPLETE
        );
    }

    /**
     * Creates a failure payload.
     */
    public static BuildProgressPayload failed(UUID arenaId, int blocksPlaced, int totalBlocks) {
        return new BuildProgressPayload(
            arenaId,
            BuildPhase.FAILED,
            (totalBlocks > 0) ? (blocksPlaced * 10000 / totalBlocks) : 0,
            blocksPlaced,
            totalBlocks,
            ProgressFlags.FAILED
        );
    }

    /**
     * Returns progress as percentage (0.0 - 1.0).
     */
    public double progressPercent() {
        return progressBps / 10000.0;
    }

    /**
     * Returns true if the build is complete.
     */
    public boolean isComplete() {
        return (flags & ProgressFlags.COMPLETE) != 0;
    }

    /**
     * Returns true if the build failed.
     */
    public boolean isFailed() {
        return (flags & ProgressFlags.FAILED) != 0;
    }

    /**
     * Returns true if the build phase changed.
     */
    public boolean isPhaseChanged() {
        return (flags & ProgressFlags.PHASE_CHANGED) != 0;
    }

    /**
     * Returns true if the build is paused.
     */
    public boolean isPaused() {
        return (flags & ProgressFlags.PAUSED) != 0;
    }

    @Override
    @Nonnull
    public Type<BuildProgressPayload> type() {
        return Objects.requireNonNull(TYPE, "payload type");
    }

    // ========== Serialization ==========

    private static void encode(RegistryFriendlyByteBuf buffer, BuildProgressPayload payload) {
        // UUID (16 bytes)
        buffer.writeLong(payload.arenaId.getMostSignificantBits());
        buffer.writeLong(payload.arenaId.getLeastSignificantBits());

        // Phase (1 byte)
        buffer.writeByte(payload.phase.ordinal());

        // Progress (2 bytes)
        buffer.writeShort(payload.progressBps);

        // Blocks placed (4 bytes)
        buffer.writeInt(payload.blocksPlaced);

        // Total blocks (4 bytes)
        buffer.writeInt(payload.totalBlocks);

        // Flags (1 byte)
        buffer.writeByte(payload.flags);
    }

    private static BuildProgressPayload decode(RegistryFriendlyByteBuf buffer) {
        // UUID (16 bytes)
        long msb = buffer.readLong();
        long lsb = buffer.readLong();
        UUID arenaId = new UUID(msb, lsb);

        // Phase (1 byte)
        int phaseOrdinal = buffer.readByte() & 0xFF;
        BuildPhase[] phases = BuildPhase.values();
        BuildPhase phase = (phaseOrdinal < phases.length) ? phases[phaseOrdinal] : BuildPhase.INITIALIZING;

        // Progress (2 bytes)
        int progressBps = buffer.readShort() & 0xFFFF;

        // Blocks placed (4 bytes)
        int blocksPlaced = buffer.readInt();

        // Total blocks (4 bytes)
        int totalBlocks = buffer.readInt();

        // Flags (1 byte)
        int flags = buffer.readByte() & 0xFF;

        if (!isWithinBounds(progressBps, blocksPlaced, totalBlocks, flags)) {
            LOGGER.warn("[BuildProgressPayload] Out-of-range payload received: progress={}, placed={}, total={}, flags={}",
                progressBps, blocksPlaced, totalBlocks, flags);
        }

        return new BuildProgressPayload(arenaId, phase, progressBps, blocksPlaced, totalBlocks, flags);
    }

    private static boolean isWithinBounds(int progressBps, int blocksPlaced, int totalBlocks, int flags) {
        if (progressBps < 0 || progressBps > MAX_PROGRESS_BPS) return false;
        if (blocksPlaced < 0 || blocksPlaced > MAX_BLOCKS) return false;
        if (totalBlocks < 0 || totalBlocks > MAX_BLOCKS) return false;
        if (blocksPlaced > totalBlocks) return false;
        return (flags & ~MAX_FLAGS) == 0;
    }

    /**
     * Serializes to raw 28-byte array (for legacy compatibility).
     */
    public byte[] toBytes() {
        byte[] bytes = new byte[28];

        long msb = arenaId.getMostSignificantBits();
        long lsb = arenaId.getLeastSignificantBits();

        // UUID (16 bytes)
        for (int i = 0; i < 8; i++) {
            bytes[i] = (byte) (msb >> (56 - i * 8));
            bytes[i + 8] = (byte) (lsb >> (56 - i * 8));
        }

        // Phase (1 byte)
        bytes[16] = (byte) phase.ordinal();

        // Progress (2 bytes)
        bytes[17] = (byte) (progressBps >> 8);
        bytes[18] = (byte) progressBps;

        // Blocks placed (4 bytes)
        bytes[19] = (byte) (blocksPlaced >> 24);
        bytes[20] = (byte) (blocksPlaced >> 16);
        bytes[21] = (byte) (blocksPlaced >> 8);
        bytes[22] = (byte) blocksPlaced;

        // Total blocks (4 bytes)
        bytes[23] = (byte) (totalBlocks >> 24);
        bytes[24] = (byte) (totalBlocks >> 16);
        bytes[25] = (byte) (totalBlocks >> 8);
        bytes[26] = (byte) totalBlocks;

        // Flags (1 byte)
        bytes[27] = (byte) flags;

        return bytes;
    }

    /**
     * Deserializes from raw 28-byte array (for legacy compatibility).
     */
    public static BuildProgressPayload fromBytes(byte[] bytes) {
        if (bytes.length < 28) {
            throw new IllegalArgumentException("Invalid packet size: " + bytes.length + " (expected 28)");
        }

        // UUID (16 bytes)
        long msb = 0;
        long lsb = 0;
        for (int i = 0; i < 8; i++) {
            msb = (msb << 8) | (bytes[i] & 0xFF);
            lsb = (lsb << 8) | (bytes[i + 8] & 0xFF);
        }
        UUID arenaId = new UUID(msb, lsb);

        // Phase (1 byte)
        int phaseOrdinal = bytes[16] & 0xFF;
        BuildPhase[] phases = BuildPhase.values();
        BuildPhase phase = (phaseOrdinal < phases.length) ? phases[phaseOrdinal] : BuildPhase.INITIALIZING;

        // Progress (2 bytes)
        int progressBps = ((bytes[17] & 0xFF) << 8) | (bytes[18] & 0xFF);

        // Blocks placed (4 bytes)
        int blocksPlaced = ((bytes[19] & 0xFF) << 24) | ((bytes[20] & 0xFF) << 16) |
                          ((bytes[21] & 0xFF) << 8) | (bytes[22] & 0xFF);

        // Total blocks (4 bytes)
        int totalBlocks = ((bytes[23] & 0xFF) << 24) | ((bytes[24] & 0xFF) << 16) |
                         ((bytes[25] & 0xFF) << 8) | (bytes[26] & 0xFF);

        // Flags (1 byte)
        int flags = bytes[27] & 0xFF;

        return new BuildProgressPayload(arenaId, phase, progressBps, blocksPlaced, totalBlocks, flags);
    }
}
