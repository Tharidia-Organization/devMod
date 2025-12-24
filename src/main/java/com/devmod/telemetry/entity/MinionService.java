package com.devmod.telemetry.entity;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for tracking minion wave statistics during encounters.
 * Extracted from TelemetryService for better separation of concerns.
 *
 * Tracks:
 * - Active minions per room
 * - Peak concurrent minion count
 * - Total spawned minions
 * - Total damage dealt by minions
 *
 * Thread-safe singleton for concurrent access.
 */
public class MinionService {
    public static final MinionService INSTANCE = new MinionService();

    private final Map<String, MinionWaveTracker> minionWaves = new ConcurrentHashMap<>();

    private MinionService() {}

    /**
     * Record a minion spawn in a room.
     *
     * @param room Room identifier
     * @param minionId Minion entity UUID
     */
    public void recordSpawn(String room, UUID minionId) {
        minionWaves.computeIfAbsent(room, k -> new MinionWaveTracker()).spawn(minionId);
    }

    /**
     * Record a minion death in a room.
     *
     * @param room Room identifier
     * @param minionId Minion entity UUID
     * @param damageDone Total damage the minion dealt before dying
     */
    public void recordDeath(String room, UUID minionId, double damageDone) {
        MinionWaveTracker tracker = minionWaves.get(room);
        if (tracker != null) {
            tracker.death(minionId, damageDone);
        }
    }

    /**
     * Get the wave tracker for a room.
     *
     * @param room Room identifier
     * @return Optional containing tracker if exists
     */
    public Optional<MinionWaveTracker> getTracker(String room) {
        return Optional.ofNullable(minionWaves.get(room));
    }

    /**
     * Get peak concurrent count for a room.
     *
     * @param room Room identifier
     * @return Peak concurrent minion count
     */
    public int getPeakConcurrent(String room) {
        MinionWaveTracker tracker = minionWaves.get(room);
        return tracker != null ? tracker.peakConcurrent : 0;
    }

    /**
     * Get current active minion count for a room.
     *
     * @param room Room identifier
     * @return Current active minion count
     */
    public int getCurrentCount(String room) {
        MinionWaveTracker tracker = minionWaves.get(room);
        return tracker != null ? tracker.getCurrentCount() : 0;
    }

    /**
     * Get minion wave statistics for a room.
     *
     * @param room Room identifier
     * @return Formatted statistics string
     */
    public String getStats(String room) {
        MinionWaveTracker tracker = minionWaves.get(room);
        if (tracker == null) return "No minion data for room: " + room;
        return String.format("Room: %s | Peak: %d | Total Spawned: %d | Total Damage: %.1f | Currently Active: %d",
                room, tracker.peakConcurrent, tracker.totalSpawned, tracker.totalDamage, tracker.getCurrentCount());
    }

    /**
     * Get all rooms with minion wave data.
     *
     * @return List of formatted statistics for all rooms
     */
    public List<String> getAllStats() {
        List<String> stats = new ArrayList<>();
        minionWaves.forEach((room, tracker) -> {
            stats.add(String.format("%s peak=%d spawned=%d dmg=%.1f active=%d",
                    room, tracker.peakConcurrent, tracker.totalSpawned, tracker.totalDamage, tracker.getCurrentCount()));
        });
        return stats;
    }

    /**
     * Get all room IDs with minion tracking data.
     *
     * @return Set of room identifiers
     */
    public Set<String> getTrackedRooms() {
        return new HashSet<>(minionWaves.keySet());
    }

    /**
     * Clear data for a specific room.
     *
     * @param room Room identifier
     */
    public void clearRoom(String room) {
        minionWaves.remove(room);
    }

    /**
     * Clear all minion tracking data.
     */
    public void clear() {
        minionWaves.clear();
    }

    /**
     * Internal tracker for minion wave statistics in a single room.
     */
    public static final class MinionWaveTracker {
        private final Set<UUID> activeMinions = ConcurrentHashMap.newKeySet();
        int peakConcurrent = 0;
        int totalSpawned = 0;
        double totalDamage = 0;
        final long startMs = System.currentTimeMillis();

        void spawn(UUID minionId) {
            activeMinions.add(minionId);
            totalSpawned++;
            if (activeMinions.size() > peakConcurrent) {
                peakConcurrent = activeMinions.size();
            }
        }

        void death(UUID minionId, double damageDone) {
            activeMinions.remove(minionId);
            totalDamage += damageDone;
        }

        public int getCurrentCount() {
            return activeMinions.size();
        }

        public int getPeakConcurrent() {
            return peakConcurrent;
        }

        public int getTotalSpawned() {
            return totalSpawned;
        }

        public double getTotalDamage() {
            return totalDamage;
        }

        public long getStartMs() {
            return startMs;
        }
    }
}
