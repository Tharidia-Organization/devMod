package com.devmod.area.builder;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.ResourceLocation;

/**
 * Serializable state of a build task for persistence across server restarts.
 * Stores enough information to reconstruct and resume a build from where it left off.
 *
 * <p>Resume strategy:
 * <ul>
 *   <li>Look up AreaDefinition from AreaRegistry using areaId</li>
 *   <li>Regenerate block map using the same deterministic generators</li>
 *   <li>Skip first {@code blocksPlaced} positions to resume from correct point</li>
 * </ul>
 *
 * <p>Limitations:
 * <ul>
 *   <li>Biome generation may not be perfectly reproducible if it uses non-seeded randomness</li>
 *   <li>If area definition was modified after pause, build may produce unexpected results</li>
 * </ul>
 *
 * @param areaId           UUID of the area being built (references AreaRegistry)
 * @param dimensionId      Dimension where build is taking place
 * @param playerId         Player who initiated the build (null for server-initiated)
 * @param blocksPlaced     Number of blocks already placed (progress)
 * @param totalBlocks      Total blocks in the build (for progress %)
 * @param currentStepName  Name of current build step (floor, walls, ceiling, biome_terrain)
 * @param clearFirst       Whether area should be cleared before building
 * @param forceMultiTick   Whether multi-tick was explicitly requested
 * @param pausedAt         Timestamp when build was paused/saved
 * @param isPaused         True if explicitly paused, false if saved during shutdown
 * @param areaRevision     Area definition revision when paused (for consistency check on resume)
 */
public record BuildTaskState(
    UUID areaId,
    ResourceLocation dimensionId,
    @Nullable UUID playerId,
    int blocksPlaced,
    int totalBlocks,
    String currentStepName,
    boolean clearFirst,
    boolean forceMultiTick,
    long pausedAt,
    boolean isPaused,
    long areaRevision,
    int clearStepBlocks
) {
    /** Maximum step name length for validation */
    public static final int MAX_STEP_NAME_LENGTH = 64;

    public static final Codec<BuildTaskState> CODEC = RecordCodecBuilder.create(instance ->
        instance.group(
            UUIDUtil.CODEC.fieldOf("areaId").forGetter(BuildTaskState::areaId),
            ResourceLocation.CODEC.fieldOf("dimensionId").forGetter(BuildTaskState::dimensionId),
            UUIDUtil.CODEC.optionalFieldOf("playerId")
                .forGetter(s -> Optional.ofNullable(s.playerId())),
            Codec.INT.fieldOf("blocksPlaced").forGetter(BuildTaskState::blocksPlaced),
            Codec.INT.fieldOf("totalBlocks").forGetter(BuildTaskState::totalBlocks),
            Codec.STRING.fieldOf("currentStepName").forGetter(BuildTaskState::currentStepName),
            Codec.BOOL.fieldOf("clearFirst").forGetter(BuildTaskState::clearFirst),
            Codec.BOOL.fieldOf("forceMultiTick").forGetter(BuildTaskState::forceMultiTick),
            Codec.LONG.fieldOf("pausedAt").forGetter(BuildTaskState::pausedAt),
            Codec.BOOL.fieldOf("isPaused").forGetter(BuildTaskState::isPaused),
            // H-02 fix: Store area revision for consistency check on resume
            Codec.LONG.optionalFieldOf("areaRevision", 0L).forGetter(BuildTaskState::areaRevision),
            // Exact clear-step size; 0 means "not recorded" (states saved by older builds)
            Codec.INT.optionalFieldOf("clearStepBlocks", 0).forGetter(BuildTaskState::clearStepBlocks)
        ).apply(instance, (areaId, dimensionId, playerIdOpt, blocksPlaced, totalBlocks,
                           currentStepName, clearFirst, forceMultiTick, pausedAt, isPaused, areaRevision,
                           clearStepBlocks) ->
            new BuildTaskState(
                areaId, dimensionId, playerIdOpt.orElse(null),
                blocksPlaced, totalBlocks, currentStepName,
                clearFirst, forceMultiTick, pausedAt, isPaused, areaRevision,
                clearStepBlocks
            )
        )
    );

    /**
     * Validates and normalizes the state on construction.
     */
    public BuildTaskState {
        Objects.requireNonNull(areaId, "areaId cannot be null");
        Objects.requireNonNull(dimensionId, "dimensionId cannot be null");
        Objects.requireNonNull(currentStepName, "currentStepName cannot be null");

        // Normalize step name
        if (currentStepName.length() > MAX_STEP_NAME_LENGTH) {
            currentStepName = currentStepName.substring(0, MAX_STEP_NAME_LENGTH);
        }

        // Ensure non-negative counts
        if (blocksPlaced < 0) blocksPlaced = 0;
        if (totalBlocks < 0) totalBlocks = 0;
        if (blocksPlaced > totalBlocks) blocksPlaced = totalBlocks;
        if (clearStepBlocks < 0) clearStepBlocks = 0;
    }

    /**
     * Gets the progress as a percentage (0-100).
     */
    public int getProgressPercent() {
        return totalBlocks > 0 ? (int) ((blocksPlaced * 100L) / totalBlocks) : 0;
    }

    /**
     * Gets the progress as a float (0.0-1.0).
     */
    public float getProgress() {
        return totalBlocks > 0 ? (float) blocksPlaced / totalBlocks : 0.0f;
    }

    /**
     * Checks if this build was explicitly paused by user action.
     */
    public boolean wasExplicitlyPaused() {
        return isPaused;
    }

    /**
     * Checks if this build was saved during server shutdown (not user-initiated pause).
     */
    public boolean wasSavedOnShutdown() {
        return !isPaused;
    }

    /**
     * Gets time since this state was saved, in milliseconds.
     */
    public long getTimeSincePaused() {
        return System.currentTimeMillis() - pausedAt;
    }

    /**
     * Creates a state from an active build.
     *
     * @param task        The build task
     * @param dimensionId The dimension
     * @param playerId    The player ID (nullable)
     * @param clearFirst  Whether clear-first was requested
     * @param forceMultiTick Whether multi-tick was forced
     * @param isPaused    Whether this is an explicit pause (vs shutdown save)
     */
    @Nonnull
    public static BuildTaskState fromActiveBuild(
        @Nonnull AreaBuildTask task,
        @Nonnull ResourceLocation dimensionId,
        @Nullable UUID playerId,
        boolean clearFirst,
        boolean forceMultiTick,
        boolean isPaused
    ) {
        Objects.requireNonNull(task, "task");
        Objects.requireNonNull(dimensionId, "dimensionId");

        return new BuildTaskState(
            task.getDefinition().id(),
            dimensionId,
            playerId,
            task.getTotalBlocksPlaced(),
            task.getTotalBlockCount(),
            task.getCurrentStepName(),
            clearFirst,
            forceMultiTick,
            System.currentTimeMillis(),
            isPaused,
            // H-02 fix: Store area revision for consistency check on resume
            task.getDefinition().revision(),
            task.getClearStepBlockCount()
        );
    }

    @Override
    public String toString() {
        return String.format("BuildTaskState[area=%s, dim=%s, progress=%d/%d (%d%%), step=%s, paused=%s]",
            areaId, dimensionId, blocksPlaced, totalBlocks, getProgressPercent(), currentStepName, isPaused);
    }
}
