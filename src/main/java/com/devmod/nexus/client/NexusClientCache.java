package com.devmod.nexus.client;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.devmod.nexus.data.SlotType;
import com.devmod.nexus.network.HubStatusPayload;
import com.devmod.nexus.network.NexusBuildProgressPayload;
import com.devmod.nexus.network.SlotListPayload;

/**
 * Client-side cache for Nexus hub data.
 * Stores slot summaries and hub status received from the server.
 * Used by UI components to display hub information.
 */
public final class NexusClientCache {
    public static final NexusClientCache INSTANCE = new NexusClientCache();

    /** Cached slots by slotId */
    private final Map<String, SlotListPayload.SlotSummary> slots = new ConcurrentHashMap<>();

    /** Last received hub status */
    @Nullable
    private volatile HubStatus hubStatus = null;

    /** Current build progress (null when no build active) */
    @Nullable
    private volatile BuildProgress buildProgress = null;

    /** Timestamp of last slot update for staleness checking */
    private volatile long lastSlotUpdate = 0;

    /** Timestamp of last status update for staleness checking */
    private volatile long lastStatusUpdate = 0;

    private NexusClientCache() {}

    // ========================================================================
    // Slot Cache
    // ========================================================================

    /**
     * Updates the slot cache with new data from the server.
     *
     * @param slotSummaries the list of slot summaries
     */
    public void updateSlots(@Nonnull List<SlotListPayload.SlotSummary> slotSummaries) {
        Objects.requireNonNull(slotSummaries);
        slots.clear();
        for (SlotListPayload.SlotSummary slot : slotSummaries) {
            slots.put(Objects.requireNonNull(slot.slotId()), slot);
        }
        lastSlotUpdate = System.currentTimeMillis();
    }

    /**
     * Gets all cached slots.
     */
    @Nonnull
    public Collection<SlotListPayload.SlotSummary> getAllSlots() {
        return Objects.requireNonNull(Collections.unmodifiableCollection(slots.values()));
    }

    /**
     * Gets a slot by ID.
     */
    @Nonnull
    public Optional<SlotListPayload.SlotSummary> getSlot(@Nonnull String slotId) {
        return Objects.requireNonNull(Optional.ofNullable(slots.get(Objects.requireNonNull(slotId))));
    }

    /**
     * Gets a slot by linked area UUID.
     */
    @Nonnull
    public Optional<SlotListPayload.SlotSummary> getSlotByArea(@Nonnull UUID areaId) {
        Objects.requireNonNull(areaId);
        return Objects.requireNonNull(slots.values().stream()
            .filter(s -> areaId.equals(s.linkedAreaId()))
            .findFirst());
    }

    /**
     * Gets all slots of a specific type.
     */
    @Nonnull
    public List<SlotListPayload.SlotSummary> getSlotsByType(@Nonnull SlotType type) {
        Objects.requireNonNull(type);
        return Objects.requireNonNull(slots.values().stream()
            .filter(s -> s.type() == type)
            .toList());
    }

    /**
     * Gets all empty (unlinked) slots.
     */
    @Nonnull
    public List<SlotListPayload.SlotSummary> getEmptySlots() {
        return Objects.requireNonNull(slots.values().stream()
            .filter(s -> !s.hasLinkedArea())
            .toList());
    }

    /**
     * Gets all linked slots.
     */
    @Nonnull
    public List<SlotListPayload.SlotSummary> getLinkedSlots() {
        return Objects.requireNonNull(slots.values().stream()
            .filter(SlotListPayload.SlotSummary::hasLinkedArea)
            .toList());
    }

    /**
     * Gets the number of cached slots.
     */
    public int getSlotCount() {
        return slots.size();
    }

    /**
     * Checks if the slot cache has data.
     */
    public boolean hasSlotsData() {
        return !slots.isEmpty();
    }

    /**
     * Gets the timestamp of the last slot update.
     */
    public long getLastSlotUpdate() {
        return lastSlotUpdate;
    }

    // ========================================================================
    // Hub Status Cache
    // ========================================================================

    /**
     * Updates the hub status cache with new data from the server.
     *
     * @param payload the hub status payload
     */
    public void updateHubStatus(@Nonnull HubStatusPayload payload) {
        Objects.requireNonNull(payload);
        this.hubStatus = new HubStatus(
            payload.initialized(),
            payload.totalSlots(),
            payload.linkedSlots(),
            payload.emptySlots()
        );
        lastStatusUpdate = System.currentTimeMillis();
    }

    /**
     * Gets the cached hub status.
     */
    @Nonnull
    public Optional<HubStatus> getHubStatus() {
        return Objects.requireNonNull(Optional.ofNullable(hubStatus));
    }

    /**
     * Checks if hub status data is available.
     */
    public boolean hasHubStatus() {
        return hubStatus != null;
    }

    /**
     * Gets the timestamp of the last status update.
     */
    public long getLastStatusUpdate() {
        return lastStatusUpdate;
    }

    // ========================================================================
    // Cache Management
    // ========================================================================

    /**
     * Clears all cached data.
     * Called on dimension change or disconnect.
     */
    public void clear() {
        slots.clear();
        hubStatus = null;
        buildProgress = null;
        lastSlotUpdate = 0;
        lastStatusUpdate = 0;
    }

    /**
     * Checks if the slot cache is stale (older than specified milliseconds).
     *
     * @param maxAgeMs maximum age in milliseconds
     * @return true if cache is stale or empty
     */
    public boolean isSlotsStale(long maxAgeMs) {
        if (slots.isEmpty()) {
            return true;
        }
        return System.currentTimeMillis() - lastSlotUpdate > maxAgeMs;
    }

    /**
     * Checks if the status cache is stale (older than specified milliseconds).
     *
     * @param maxAgeMs maximum age in milliseconds
     * @return true if cache is stale or empty
     */
    public boolean isStatusStale(long maxAgeMs) {
        if (hubStatus == null) {
            return true;
        }
        return System.currentTimeMillis() - lastStatusUpdate > maxAgeMs;
    }

    // ========================================================================
    // Build Progress Cache
    // ========================================================================

    /**
     * Updates the build progress cache with new data from the server.
     *
     * @param payload the build progress payload
     */
    public void updateBuildProgress(@Nonnull NexusBuildProgressPayload payload) {
        Objects.requireNonNull(payload);
        if (payload.complete()) {
            // Build completed - clear progress after a short delay for fade-out
            this.buildProgress = new BuildProgress(
                payload.currentStep(),
                payload.totalSteps(),
                payload.stepName(),
                true,
                System.currentTimeMillis()
            );
        } else {
            this.buildProgress = new BuildProgress(
                payload.currentStep(),
                payload.totalSteps(),
                payload.stepName(),
                false,
                System.currentTimeMillis()
            );
        }
    }

    /**
     * Gets the current build progress.
     */
    @Nonnull
    public Optional<BuildProgress> getBuildProgress() {
        return Objects.requireNonNull(Optional.ofNullable(buildProgress));
    }

    /**
     * Checks if a build is currently in progress.
     */
    public boolean isBuildInProgress() {
        BuildProgress progress = buildProgress;
        return progress != null && !progress.complete();
    }

    /**
     * Clears the build progress (call after fade-out completes).
     */
    public void clearBuildProgress() {
        this.buildProgress = null;
    }

    // ========================================================================
    // Hub Status Record
    // ========================================================================

    /**
     * Immutable hub status snapshot.
     */
    public record HubStatus(
        boolean initialized,
        int totalSlots,
        int linkedSlots,
        int emptySlots
    ) {
        /**
         * Gets the percentage of slots that are linked.
         */
        public float getLinkedPercentage() {
            if (totalSlots == 0) {
                return 0.0f;
            }
            return (float) linkedSlots / totalSlots * 100.0f;
        }

        @Override
        public String toString() {
            return String.format(
                "HubStatus[initialized=%s, total=%d, linked=%d, empty=%d]",
                initialized, totalSlots, linkedSlots, emptySlots
            );
        }
    }

    // ========================================================================
    // Build Progress Record
    // ========================================================================

    /**
     * Immutable build progress snapshot.
     */
    public record BuildProgress(
        int currentStep,
        int totalSteps,
        @Nullable String stepName,
        boolean complete,
        long timestamp
    ) {
        /**
         * Gets the progress percentage (0.0 - 1.0).
         */
        public float getProgressPercent() {
            if (totalSteps <= 0) return 0.0f;
            if (complete) return 1.0f;
            return Math.min(1.0f, (float) currentStep / totalSteps);
        }

        /**
         * Gets the progress as an integer percentage (0 - 100).
         */
        public int getProgressPercentInt() {
            return Math.round(getProgressPercent() * 100);
        }

        /**
         * Gets elapsed time since this progress update in milliseconds.
         */
        public long getElapsedMs() {
            return System.currentTimeMillis() - timestamp;
        }

        @Override
        public String toString() {
            return String.format(
                "BuildProgress[%d/%d step='%s' complete=%s]",
                currentStep, totalSteps, stepName, complete
            );
        }
    }
}
