package com.devmod.telemetry.duckdb.aggregation;

import java.time.Instant;
import java.util.UUID;

import javax.annotation.Nullable;

/**
 * Rate-limiting sampler for player snapshot events.
 *
 * <p>Player snapshots are high-volume, low-priority events. This sampler
 * ensures only 1 snapshot per configured interval (default 10s) is recorded,
 * reducing database writes while preserving useful data.
 *
 * <p>Memory: ~100B (timestamps + reference)
 */
public class SnapshotSampler {

    private final long sampleIntervalMs;

    /** Timestamp of last accepted snapshot */
    private long lastSnapshotTimeMs;

    /** Last accepted snapshot data (for queries) */
    @Nullable
    private SnapshotData lastSnapshot;

    /** Count of snapshots dropped due to rate limiting */
    private int droppedCount;

    /** Count of snapshots accepted */
    private int acceptedCount;

    /**
     * Create a snapshot sampler with default interval.
     */
    public SnapshotSampler() {
        this(AggregationConfig.SNAPSHOT_SAMPLE_INTERVAL_MS);
    }

    /**
     * Create a snapshot sampler with custom interval.
     *
     * @param sampleIntervalMs minimum milliseconds between accepted snapshots
     */
    public SnapshotSampler(long sampleIntervalMs) {
        this.sampleIntervalMs = sampleIntervalMs;
        this.lastSnapshotTimeMs = 0;
    }

    /**
     * Try to record a snapshot. Returns true if accepted, false if rate-limited.
     *
     * @param snapshot the snapshot data
     * @return true if snapshot was accepted and should be written
     */
    public boolean tryRecord(SnapshotData snapshot) {
        long now = System.currentTimeMillis();

        if (now - lastSnapshotTimeMs < sampleIntervalMs) {
            droppedCount++;
            return false;
        }

        lastSnapshotTimeMs = now;
        lastSnapshot = snapshot;
        acceptedCount++;
        return true;
    }

    /**
     * Get the last accepted snapshot (for real-time queries).
     */
    @Nullable
    public SnapshotData getLastSnapshot() {
        return lastSnapshot;
    }

    /**
     * Check if a new snapshot would be accepted.
     */
    public boolean wouldAccept() {
        return System.currentTimeMillis() - lastSnapshotTimeMs >= sampleIntervalMs;
    }

    /**
     * Get time until next snapshot would be accepted (milliseconds).
     */
    public long getTimeUntilNextAccept() {
        long elapsed = System.currentTimeMillis() - lastSnapshotTimeMs;
        return Math.max(0, sampleIntervalMs - elapsed);
    }

    /**
     * Reset sampler state.
     */
    public void reset() {
        lastSnapshotTimeMs = 0;
        lastSnapshot = null;
        droppedCount = 0;
        acceptedCount = 0;
    }

    // ============================================
    // STATISTICS
    // ============================================

    public int getDroppedCount() {
        return droppedCount;
    }

    public int getAcceptedCount() {
        return acceptedCount;
    }

    public double getAcceptRate() {
        int total = acceptedCount + droppedCount;
        return total > 0 ? (double) acceptedCount / total : 0.0;
    }

    // ============================================
    // SNAPSHOT DATA
    // ============================================

    /**
     * Player snapshot data structure.
     */
    public record SnapshotData(
        Instant timestamp,
        UUID playerId,
        String playerName,

        // Position
        double x,
        double y,
        double z,
        String dimension,

        // Health/Resources
        float health,
        float maxHealth,
        float armor,
        float stamina,
        float maxStamina,

        // Combat state
        @Nullable String heldItem,
        boolean inCombat,
        int comboCount,

        // Quest state
        @Nullable UUID questId,
        int currentWave,

        // Performance hint
        float clientFps
    ) {
        /**
         * Create a minimal snapshot with just essential data.
         */
        public static SnapshotData minimal(UUID playerId, String playerName,
                                           double x, double y, double z, String dimension) {
            return new SnapshotData(
                Instant.now(),
                playerId,
                playerName,
                x, y, z,
                dimension,
                20f, 20f, 0f, 100f, 100f,
                null, false, 0,
                null, 0,
                60f
            );
        }

        /**
         * Check if snapshot has quest context.
         */
        public boolean hasQuestContext() {
            return questId != null;
        }

        /**
         * Get health percentage (0.0 - 1.0).
         */
        public float healthPercent() {
            return maxHealth > 0 ? health / maxHealth : 0f;
        }

        /**
         * Get stamina percentage (0.0 - 1.0).
         */
        public float staminaPercent() {
            return maxStamina > 0 ? stamina / maxStamina : 0f;
        }
    }
}
