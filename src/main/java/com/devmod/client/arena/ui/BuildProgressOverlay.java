package com.devmod.client.arena.ui;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.devmod.arena.BuildPhase;
import com.devmod.arena.ProgressFlags;

/**
 * Server-side build progress overlay with rate limiting.
 * DD39: Rate limit 4Hz (250ms), delta min 1%.
 */
public class BuildProgressOverlay {

    private static final Logger LOGGER = LoggerFactory.getLogger(BuildProgressOverlay.class);
    private static final long MIN_UPDATE_INTERVAL_MS = 250; // 4Hz
    private static final double MIN_DELTA_PERCENT = 0.01; // 1%

    private final Map<UUID, ProgressState> playerStates = new ConcurrentHashMap<>();
    private final Consumer<ProgressUpdate> updateSender;

    public BuildProgressOverlay(Consumer<ProgressUpdate> updateSender) {
        this.updateSender = updateSender;
    }

    /**
     * Updates progress for a player. Applies rate limiting and delta threshold.
     *
     * @param playerId The player to update
     * @param arenaId The arena being built
     * @param phase Current build phase
     * @param progress Progress percentage (0.0 - 1.0)
     * @param blocksPlaced Blocks placed so far
     * @param totalBlocks Total blocks to place
     * @param estimatedMs Estimated time remaining in ms
     */
    public void updateProgress(UUID playerId, UUID arenaId, BuildPhase phase,
                                double progress, int blocksPlaced, int totalBlocks,
                                long estimatedMs) {

        ProgressState state = playerStates.computeIfAbsent(playerId,
            k -> new ProgressState());

        // Check if update should be skipped
        if (shouldSkipUpdate(state, progress)) {
            return;
        }

        // Create and send update
        ProgressUpdate update = new ProgressUpdate(
            playerId, arenaId, phase, progress,
            blocksPlaced, totalBlocks, estimatedMs,
            calculateFlags(state, phase)
        );

        state.lastProgress = progress;
        state.lastUpdateTime = System.currentTimeMillis();
        state.lastPhase = phase;

        updateSender.accept(update);
    }

    /**
     * Completes progress for a player.
     */
    public void complete(UUID playerId, UUID arenaId, boolean success) {
        ProgressState state = playerStates.remove(playerId);
        if (state != null) {
            ProgressUpdate update = new ProgressUpdate(
                playerId, arenaId,
                success ? BuildPhase.COMPLETE : BuildPhase.FAILED,
                success ? 1.0 : state.lastProgress,
                0, 0, 0,
                success ? ProgressFlags.COMPLETE : ProgressFlags.FAILED
            );
            updateSender.accept(update);
        }
    }

    /**
     * Clears all progress states.
     */
    public void clear() {
        playerStates.clear();
    }

    /**
     * Returns true if update should be skipped due to rate limiting or delta threshold.
     */
    private boolean shouldSkipUpdate(ProgressState state, double newProgress) {
        long now = System.currentTimeMillis();

        // Rate limit: skip if less than 250ms since last update
        if (now - state.lastUpdateTime < MIN_UPDATE_INTERVAL_MS) {
            LOGGER.trace("Skipping update: rate limited ({} ms since last)",
                now - state.lastUpdateTime);
            return true;
        }

        // Delta threshold: skip if progress change is less than 1%
        double delta = Math.abs(newProgress - state.lastProgress);
        if (delta < MIN_DELTA_PERCENT) {
            LOGGER.trace("Skipping update: delta too small ({}%)",
                String.format("%.2f", delta * 100));
            return true;
        }

        return false;
    }

    private int calculateFlags(ProgressState state, BuildPhase phase) {
        int flags = 0;
        if (phase != state.lastPhase) {
            flags |= ProgressFlags.PHASE_CHANGED;
        }
        return flags;
    }

    // ========== Supporting Types ==========

    /**
     * Internal state tracking for a player's progress updates.
     */
    private static class ProgressState {
        double lastProgress = 0.0;
        long lastUpdateTime = 0;
        BuildPhase lastPhase = BuildPhase.INITIALIZING;

        ProgressState() {
        }
    }

    /**
     * Progress update to send to client.
     */
    public record ProgressUpdate(
        UUID playerId,
        UUID arenaId,
        BuildPhase phase,
        double progress,
        int blocksPlaced,
        int totalBlocks,
        long estimatedMs,
        int flags
    ) {
        /**
         * Serializes to 28 bytes for network transmission.
         */
        public byte[] toBytes() {
            // Layout (28 bytes):
            // 0-15: arenaId (UUID = 2 longs = 16 bytes)
            // 16: phase (1 byte)
            // 17-18: progress (2 bytes, 0-10000)
            // 19-22: blocksPlaced (4 bytes)
            // 23-26: totalBlocks (4 bytes)
            // 27: flags (1 byte)

            byte[] bytes = new byte[28];

            long msb = arenaId.getMostSignificantBits();
            long lsb = arenaId.getLeastSignificantBits();

            for (int i = 0; i < 8; i++) {
                bytes[i] = (byte) (msb >> (56 - i * 8));
                bytes[i + 8] = (byte) (lsb >> (56 - i * 8));
            }

            bytes[16] = (byte) phase.ordinal();

            int progressShort = (int) (progress * 10000);
            bytes[17] = (byte) (progressShort >> 8);
            bytes[18] = (byte) progressShort;

            bytes[19] = (byte) (blocksPlaced >> 24);
            bytes[20] = (byte) (blocksPlaced >> 16);
            bytes[21] = (byte) (blocksPlaced >> 8);
            bytes[22] = (byte) blocksPlaced;

            bytes[23] = (byte) (totalBlocks >> 24);
            bytes[24] = (byte) (totalBlocks >> 16);
            bytes[25] = (byte) (totalBlocks >> 8);
            bytes[26] = (byte) totalBlocks;

            bytes[27] = (byte) flags;

            return bytes;
        }
    }
}
