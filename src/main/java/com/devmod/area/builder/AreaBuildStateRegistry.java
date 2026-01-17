package com.devmod.area.builder;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nonnull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Persistent storage for paused/interrupted build task states.
 * Allows builds to be resumed after server restart or explicit pause.
 *
 * <p>This registry stores {@link BuildTaskState} records that capture
 * enough information to reconstruct and resume builds from where they
 * left off.
 *
 * <p>States are stored when:
 * <ul>
 *   <li>A player explicitly pauses a build</li>
 *   <li>Server is shutting down with active builds</li>
 *   <li>A build is interrupted by error (for debugging/retry)</li>
 * </ul>
 *
 * <p>States are removed when:
 * <ul>
 *   <li>A build is successfully resumed and completed</li>
 *   <li>A player explicitly cancels a paused build</li>
 *   <li>The associated area is deleted</li>
 * </ul>
 */
public class AreaBuildStateRegistry extends SavedData {
    private static final Logger LOGGER = LoggerFactory.getLogger(AreaBuildStateRegistry.class);
    private static final String DATA_NAME = "devmod_area_build_states";
    private static final int DATA_VERSION = 1;

    private static final String TAG_VERSION = "version";
    private static final String TAG_STATES = "states";

    /** Maximum paused builds to prevent unbounded growth */
    public static final int MAX_PAUSED_BUILDS = 50;

    /** Maximum age for auto-cleanup (7 days in milliseconds) */
    public static final long MAX_STATE_AGE_MS = 7L * 24 * 60 * 60 * 1000;

    /** Paused/interrupted build states by area ID */
    private final Map<UUID, BuildTaskState> states = new ConcurrentHashMap<>();

    /**
     * Gets the AreaBuildStateRegistry instance for the server.
     */
    @Nonnull
    public static AreaBuildStateRegistry get(@Nonnull MinecraftServer server) {
        Objects.requireNonNull(server);
        return Objects.requireNonNull(
            server.overworld().getDataStorage()
                .computeIfAbsent(Objects.requireNonNull(factory()), DATA_NAME)
        );
    }

    private static Factory<AreaBuildStateRegistry> factory() {
        return new Factory<>(
            AreaBuildStateRegistry::new,
            AreaBuildStateRegistry::load,
            net.minecraft.util.datafix.DataFixTypes.LEVEL
        );
    }

    // ========================================================================
    // State Management
    // ========================================================================

    /**
     * Saves a build state for later resumption.
     * Overwrites any existing state for the same area.
     *
     * @param state The build state to save
     * @return true if saved, false if registry is full
     */
    public boolean saveState(@Nonnull BuildTaskState state) {
        Objects.requireNonNull(state);

        // Check capacity (but allow overwrite of existing)
        if (states.size() >= MAX_PAUSED_BUILDS && !states.containsKey(state.areaId())) {
            // Try to make room by cleaning old states
            cleanupOldStates();

            if (states.size() >= MAX_PAUSED_BUILDS) {
                LOGGER.warn("[BuildState] Cannot save state for area {}: registry full ({}/{})",
                    state.areaId(), states.size(), MAX_PAUSED_BUILDS);
                return false;
            }
        }

        states.put(state.areaId(), state);
        setDirty();

        LOGGER.info("[BuildState] Saved build state for area {} ({}% complete, {})",
            state.areaId(), state.getProgressPercent(),
            state.isPaused() ? "paused" : "shutdown-saved");
        return true;
    }

    /**
     * Saves multiple build states (typically during shutdown).
     *
     * @param stateList States to save
     * @return Number of states successfully saved
     */
    public int saveStates(@Nonnull Collection<BuildTaskState> stateList) {
        Objects.requireNonNull(stateList);
        int saved = 0;
        for (BuildTaskState state : stateList) {
            if (saveState(Objects.requireNonNull(state))) {
                saved++;
            }
        }
        return saved;
    }

    /**
     * Gets a saved build state for an area.
     */
    @Nonnull
    public Optional<BuildTaskState> getState(@Nonnull UUID areaId) {
        return Objects.requireNonNull(Optional.ofNullable(states.get(Objects.requireNonNull(areaId))));
    }

    /**
     * Removes a build state (e.g., after successful resume or cancel).
     *
     * @param areaId The area ID
     * @return The removed state, or null if not found
     */
    public BuildTaskState removeState(@Nonnull UUID areaId) {
        BuildTaskState removed = states.remove(Objects.requireNonNull(areaId));
        if (removed != null) {
            setDirty();
            LOGGER.info("[BuildState] Removed build state for area {}", areaId);
        }
        return removed;
    }

    /**
     * Checks if there's a saved state for an area.
     */
    public boolean hasState(@Nonnull UUID areaId) {
        return states.containsKey(Objects.requireNonNull(areaId));
    }

    /**
     * Gets all saved build states.
     */
    @Nonnull
    public Collection<BuildTaskState> getAllStates() {
        return Objects.requireNonNull(Collections.unmodifiableCollection(states.values()));
    }

    /**
     * Gets the number of saved states.
     */
    public int getStateCount() {
        return states.size();
    }

    /**
     * Gets states that were saved during shutdown (not explicitly paused).
     * These are candidates for automatic resume on server start.
     */
    @Nonnull
    public List<BuildTaskState> getShutdownSavedStates() {
        return Objects.requireNonNull(states.values().stream()
            .filter(BuildTaskState::wasSavedOnShutdown)
            .toList());
    }

    /**
     * Gets states that were explicitly paused by users.
     */
    @Nonnull
    public List<BuildTaskState> getPausedStates() {
        return Objects.requireNonNull(states.values().stream()
            .filter(BuildTaskState::wasExplicitlyPaused)
            .toList());
    }

    /**
     * Gets states for a specific player.
     */
    @Nonnull
    public List<BuildTaskState> getStatesForPlayer(@Nonnull UUID playerId) {
        Objects.requireNonNull(playerId);
        return Objects.requireNonNull(states.values().stream()
            .filter(s -> playerId.equals(s.playerId()))
            .toList());
    }

    // ========================================================================
    // Cleanup
    // ========================================================================

    /**
     * Removes states older than MAX_STATE_AGE_MS.
     *
     * @return Number of states removed
     */
    public int cleanupOldStates() {
        long now = System.currentTimeMillis();
        List<UUID> toRemove = new ArrayList<>();

        for (Map.Entry<UUID, BuildTaskState> entry : states.entrySet()) {
            if (now - entry.getValue().pausedAt() > MAX_STATE_AGE_MS) {
                toRemove.add(entry.getKey());
            }
        }

        for (UUID id : toRemove) {
            states.remove(id);
            LOGGER.info("[BuildState] Removed stale build state for area {} (older than 7 days)", id);
        }

        if (!toRemove.isEmpty()) {
            setDirty();
        }

        return toRemove.size();
    }

    /**
     * Clears all saved states.
     */
    public void clearAll() {
        int count = states.size();
        states.clear();
        if (count > 0) {
            setDirty();
            LOGGER.info("[BuildState] Cleared {} build states", count);
        }
    }

    // ========================================================================
    // Serialization
    // ========================================================================

    @Override
    @Nonnull
    public CompoundTag save(@Nonnull CompoundTag tag, @Nonnull HolderLookup.Provider provider) {
        tag.putInt(TAG_VERSION, DATA_VERSION);

        ListTag statesList = new ListTag();
        for (BuildTaskState state : states.values()) {
            BuildTaskState.CODEC.encodeStart(NbtOps.INSTANCE, state)
                .result()
                .ifPresent(statesList::add);
        }
        tag.put(TAG_STATES, statesList);

        return tag;
    }

    // ========================================================================
    // Data Migration
    // ========================================================================

    /**
     * Migrates data from an older version to the current version.
     */
    @Nonnull
    private static CompoundTag migrateData(@Nonnull CompoundTag tag, int fromVersion) {
        LOGGER.info("[BuildState] Migrating data from version {} to {}", fromVersion, DATA_VERSION);

        int currentVersion = fromVersion;

        if (currentVersion < 1) {
            currentVersion = 1;
        }

        // Future migrations here

        tag.putInt(TAG_VERSION, DATA_VERSION);
        LOGGER.info("[BuildState] Migration complete, now at version {}", DATA_VERSION);
        return tag;
    }

    @Nonnull
    public static AreaBuildStateRegistry load(@Nonnull CompoundTag tag, @Nonnull HolderLookup.Provider provider) {
        AreaBuildStateRegistry registry = new AreaBuildStateRegistry();

        int version = tag.getInt(TAG_VERSION);
        if (version > DATA_VERSION) {
            LOGGER.warn("[BuildState] Loading data from newer version {} (current: {})", version, DATA_VERSION);
        }

        if (version < DATA_VERSION) {
            tag = migrateData(tag, version);
        }

        ListTag statesList = tag.getList(TAG_STATES, Tag.TAG_COMPOUND);
        for (int i = 0; i < statesList.size(); i++) {
            CompoundTag stateTag = statesList.getCompound(i);
            BuildTaskState.CODEC.parse(NbtOps.INSTANCE, stateTag)
                .result()
                .ifPresent(state -> registry.states.put(state.areaId(), state));
        }

        // Auto-cleanup old states on load
        int cleaned = registry.cleanupOldStates();
        if (cleaned > 0) {
            LOGGER.info("[BuildState] Cleaned up {} stale states on load", cleaned);
        }

        LOGGER.info("[BuildState] Loaded {} build states", registry.states.size());
        return registry;
    }
}
