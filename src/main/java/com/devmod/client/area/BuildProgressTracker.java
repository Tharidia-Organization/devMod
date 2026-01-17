package com.devmod.client.area;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import javax.annotation.Nonnull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import com.devmod.area.network.BuildStatusPayload;

/**
 * Client-side singleton for tracking build progress.
 * Receives updates from BuildStatusPayload and notifies listeners.
 * Used by UI components to display build progress.
 */
@OnlyIn(Dist.CLIENT)
public final class BuildProgressTracker {
    private static final Logger LOGGER = LoggerFactory.getLogger(BuildProgressTracker.class);

    /** Singleton instance */
    public static final BuildProgressTracker INSTANCE = new BuildProgressTracker();

    /** Active build states by area ID */
    private final Map<UUID, BuildState> builds = new ConcurrentHashMap<>();

    /** Listeners for build status changes */
    private final Map<UUID, Consumer<BuildState>> listeners = new ConcurrentHashMap<>();

    private BuildProgressTracker() {}

    /**
     * Represents the state of an active build.
     */
    public record BuildState(
        UUID areaId,
        BuildStatusPayload.Status status,
        int progressPercent,
        long lastUpdateTime
    ) {
        public boolean isActive() {
            return status == BuildStatusPayload.Status.STARTED
                || status == BuildStatusPayload.Status.IN_PROGRESS
                || status == BuildStatusPayload.Status.RESUMED
                || status == BuildStatusPayload.Status.QUEUED;
        }

        public boolean isPaused() {
            return status == BuildStatusPayload.Status.PAUSED;
        }

        public boolean isComplete() {
            return status == BuildStatusPayload.Status.COMPLETED;
        }

        public boolean isFailed() {
            return status == BuildStatusPayload.Status.FAILED;
        }
    }

    /**
     * Updates the build status for an area.
     * Called by AreaClientHooks when BuildStatusPayload is received.
     *
     * @param areaId         The area ID
     * @param status         The new status
     * @param progressPercent Progress percentage (0-100)
     */
    public void updateStatus(@Nonnull UUID areaId, @Nonnull BuildStatusPayload.Status status, int progressPercent) {
        Objects.requireNonNull(areaId);
        Objects.requireNonNull(status);

        BuildState newState = new BuildState(areaId, status, progressPercent, System.currentTimeMillis());

        // Store or remove based on status
        if (status == BuildStatusPayload.Status.COMPLETED || status == BuildStatusPayload.Status.FAILED) {
            builds.remove(areaId);
            LOGGER.debug("[BuildTracker] Build {} for area {}", status.getSerializedName(), areaId);
        } else {
            builds.put(areaId, newState);
            LOGGER.debug("[BuildTracker] Build {} at {}% for area {}",
                status.getSerializedName(), progressPercent, areaId);
        }

        // Notify listeners
        Consumer<BuildState> listener = listeners.get(areaId);
        if (listener != null) {
            try {
                listener.accept(newState);
            } catch (Exception e) {
                LOGGER.error("[BuildTracker] Listener error for area {}: {}", areaId, e.getMessage());
            }
        }
    }

    /**
     * Gets the current build state for an area.
     *
     * @param areaId The area ID
     * @return The build state, or empty if no active build
     */
    @Nonnull
    public Optional<BuildState> getBuildState(@Nonnull UUID areaId) {
        return Objects.requireNonNull(Optional.ofNullable(builds.get(Objects.requireNonNull(areaId))));
    }

    /**
     * Checks if there's an active build for an area.
     */
    public boolean hasActiveBuild(@Nonnull UUID areaId) {
        BuildState state = builds.get(Objects.requireNonNull(areaId));
        return state != null && (state.isActive() || state.isPaused());
    }

    /**
     * Gets all active builds.
     *
     * @return Map of area ID to build state
     */
    @Nonnull
    public Map<UUID, BuildState> getActiveBuilds() {
        return Objects.requireNonNull(Map.copyOf(builds));
    }

    /**
     * Registers a listener for build status changes on a specific area.
     *
     * @param areaId   The area ID to listen for
     * @param listener The listener callback
     */
    public void addListener(@Nonnull UUID areaId, @Nonnull Consumer<BuildState> listener) {
        listeners.put(Objects.requireNonNull(areaId), Objects.requireNonNull(listener));
    }

    /**
     * Removes a listener for an area.
     *
     * @param areaId The area ID
     */
    public void removeListener(@Nonnull UUID areaId) {
        listeners.remove(Objects.requireNonNull(areaId));
    }

    /**
     * Clears all tracked builds and listeners.
     * Called when disconnecting from server.
     */
    public void clear() {
        builds.clear();
        listeners.clear();
        LOGGER.debug("[BuildTracker] Cleared all build states");
    }
}
