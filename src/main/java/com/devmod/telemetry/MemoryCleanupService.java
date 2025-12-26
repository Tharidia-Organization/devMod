package com.devmod.telemetry;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.devmod.telemetry.boss.BossPhaseService;
import com.devmod.telemetry.dungeon.DungeonSessionService;
import com.devmod.telemetry.room.RoomAnalysisService;
import com.devmod.telemetry.skills.SkillTrackingService;
public class MemoryCleanupService {
    private static final Logger LOGGER = LoggerFactory.getLogger(MemoryCleanupService.class);
    public static final MemoryCleanupService INSTANCE = new MemoryCleanupService();

    // Cleanup configuration
    private static final long CLEANUP_INTERVAL_MS = 5 * 60 * 1000; // 5 minutes
    private static final long STALE_ENTITY_THRESHOLD_MS = 30 * 60 * 1000; // 30 minutes
    private static final int MAX_ENTRIES_PER_MAP = 10_000; // Safety limit
    private static final int MAX_ROOM_ENTRIES = 1_000;

    /**
     * Gets the maximum room entries allowed before cleanup is triggered.
     * Used for debugging and monitoring memory usage.
     */
    public int getMaxRoomEntries() {
        return MAX_ROOM_ENTRIES;
    }

    // Tracking last cleanup
    private final AtomicLong lastCleanupTime = new AtomicLong(0);
    private final AtomicLong totalCleanedEntries = new AtomicLong(0);

    // Entity tracking for cleanup (entity UUID -> last seen timestamp)
    private final Map<UUID, Long> entityLastSeen = new ConcurrentHashMap<>();

    private MemoryCleanupService() {}

    /**
     * Mark an entity as seen (call this when processing entity events).
     *
     * @param entityId Entity UUID
     */
    public void markEntitySeen(UUID entityId) {
        if (entityId != null) {
            entityLastSeen.put(entityId, System.currentTimeMillis());
        }
    }

    /**
     * Remove an entity from tracking (call when entity is removed from world).
     *
     * @param entityId Entity UUID
     */
    public void markEntityRemoved(UUID entityId) {
        if (entityId != null) {
            entityLastSeen.remove(entityId);
            // Also clean up from all sub-services
            cleanupEntity(entityId);
        }
    }

    /**
     * Called periodically to check if cleanup is needed.
     * Returns immediately if not enough time has passed.
     */
    public void tick() {
        long now = System.currentTimeMillis();
        if (now - lastCleanupTime.get() < CLEANUP_INTERVAL_MS) {
            return;
        }

        // Perform cleanup
        performCleanup(now);
    }

    /**
     * Force a cleanup regardless of timing (e.g., on world unload).
     */
    public void forceCleanup() {
        performCleanup(System.currentTimeMillis());
    }

    /**
     * Perform the actual cleanup.
     */
    private void performCleanup(long now) {
        lastCleanupTime.set(now);
        long cleanedCount = 0;

        try {
            // 1. Clean up stale entity tracking
            cleanedCount += cleanupStaleEntities(now);

            // 2. Clean up TelemetryService maps
            cleanedCount += cleanupTelemetryService();

            // 3. Clean up sub-services
            cleanedCount += cleanupSubServices();

            // 4. Clean up entity last seen map itself
            cleanedCount += trimEntityLastSeenMap(now);

            totalCleanedEntries.addAndGet(cleanedCount);

            if (cleanedCount > 0) {
                LOGGER.info("Memory cleanup: removed {} stale entries (total lifetime: {})",
                    cleanedCount, totalCleanedEntries.get());
            }

        } catch (Exception e) {
            LOGGER.error("Error during memory cleanup: {}", e.getMessage());
        }
    }

    /**
     * Clean up stale entities from all tracking systems.
     */
    private long cleanupStaleEntities(long now) {
        long threshold = now - STALE_ENTITY_THRESHOLD_MS;
        long count = 0;

        for (Map.Entry<UUID, Long> entry : entityLastSeen.entrySet()) {
            if (entry.getValue() < threshold) {
                cleanupEntity(entry.getKey());
                count++;
            }
        }

        // Remove stale entries from entityLastSeen
        entityLastSeen.entrySet().removeIf(e -> e.getValue() < threshold);

        return count;
    }

    /**
     * Clean up a single entity from all tracking systems.
     */
    private void cleanupEntity(UUID entityId) {
        TelemetryService.INSTANCE.cleanupEntity(entityId);
        SkillTrackingService.INSTANCE.cleanupEntity(entityId);
        BossPhaseService.INSTANCE.cleanupEntity(entityId);
        DungeonSessionService.INSTANCE.cleanupEntity(entityId);
    }

    /**
     * Clean up TelemetryService maps that might grow unbounded.
     */
    private long cleanupTelemetryService() {
        long count = 0;

        // The TelemetryService now delegates to sub-services
        // This method handles any remaining direct cleanup needed

        return count;
    }

    /**
     * Clean up sub-services that might accumulate data.
     */
    private long cleanupSubServices() {
        long count = 0;

        // Room analysis - clear old room data if it exceeds limits
        // Note: RoomAnalysisService handles its own cleanup via clear() method
        // We only trigger if we detect excessive growth (would need getSize method)

        return count;
    }

    /**
     * Trim the entity last seen map to prevent unbounded growth.
     */
    private long trimEntityLastSeenMap(long now) {
        if (entityLastSeen.size() <= MAX_ENTRIES_PER_MAP) {
            return 0;
        }

        // Remove oldest entries until we're under the limit
        long threshold = now - (STALE_ENTITY_THRESHOLD_MS / 2); // More aggressive threshold
        long initialSize = entityLastSeen.size();

        entityLastSeen.entrySet().removeIf(e -> e.getValue() < threshold);

        return initialSize - entityLastSeen.size();
    }

    /**
     * Get statistics about memory usage.
     */
    public String getMemoryStats() {
        return String.format(
            "Tracked entities: %d, Total cleaned: %d, Last cleanup: %d seconds ago",
            entityLastSeen.size(),
            totalCleanedEntries.get(),
            (System.currentTimeMillis() - lastCleanupTime.get()) / 1000
        );
    }

    /**
     * Clear all tracking data (e.g., on world unload).
     */
    public void clearAll() {
        entityLastSeen.clear();
        RoomAnalysisService.INSTANCE.clear();
        SkillTrackingService.INSTANCE.clear();
        BossPhaseService.INSTANCE.clear();
        DungeonSessionService.INSTANCE.clear();
        LOGGER.info("All telemetry tracking data cleared");
    }

    /**
     * Get the count of currently tracked entities.
     */
    public int getTrackedEntityCount() {
        return entityLastSeen.size();
    }

    /**
     * Get total entries cleaned since startup.
     */
    public long getTotalCleanedEntries() {
        return totalCleanedEntries.get();
    }
}
