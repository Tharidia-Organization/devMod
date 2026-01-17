package com.devmod.area.builder;

import java.io.Closeable;
import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.function.Consumer;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import com.devmod.area.data.AreaDefinition;
import com.devmod.area.data.AreaOptions;

/**
 * Multi-tick build task for large areas.
 * Spreads block placement over multiple ticks to avoid lag.
 * Uses AreaBlockMapGenerator for consistent block placement logic.
 *
 * <p>Thread-safety: The {@link #tick()} method is synchronized to prevent
 * concurrent access to internal iterators and state.
 *
 * <p>Implements {@link Closeable} to ensure proper cleanup of large block maps
 * when builds are cancelled or abandoned.
 */
public class AreaBuildTask implements Closeable {
    private static final Logger LOGGER = LoggerFactory.getLogger(AreaBuildTask.class);

    /** Default blocks to place per tick */
    private static final int DEFAULT_BLOCKS_PER_TICK = 500;

    /**
     * Block update flag for area building.
     * Uses UPDATE_CLIENTS (2) to sync to clients without triggering expensive neighbor updates.
     * This provides ~90% performance improvement over UPDATE_ALL for large areas.
     */
    private static final int AREA_BUILD_FLAGS = Block.UPDATE_CLIENTS;

    private final ServerLevel level;
    private final AreaDefinition definition;
    private final int blocksPerTick;
    private final Consumer<AreaBuildTask> onComplete;

    /** Lock object for thread-safe tick() execution */
    private final Object tickLock = new Object();

    private final Queue<BuildStep> steps = new ArrayDeque<>();
    @Nullable private BuildStep currentStep = null;
    @Nullable private Iterator<BlockPos> currentIterator = null;
    private volatile boolean completed = false;
    private volatile boolean closed = false;
    private int totalBlocksPlaced = 0;

    /** Number of blocks to skip (for resume functionality) */
    private int blocksToSkip = 0;
    private int blocksSkipped = 0;

    /** Cached total block count for O(1) progress calculation */
    private int totalBlockCount = 0;

    /** Count of failed block placements */
    private int failedBlockCount = 0;

    /**
     * Creates a new build task.
     *
     * @param level          The server level
     * @param definition     The area definition
     * @param blocksPerTick  Blocks to place per tick
     * @param onComplete     Callback when complete
     */
    public AreaBuildTask(@Nonnull ServerLevel level, @Nonnull AreaDefinition definition,
            int blocksPerTick, @Nonnull Consumer<AreaBuildTask> onComplete) {
        this(level, definition, blocksPerTick, onComplete, 0);
    }

    /**
     * Creates a new build task with skip capability for resume.
     *
     * @param level          The server level
     * @param definition     The area definition
     * @param blocksPerTick  Blocks to place per tick
     * @param onComplete     Callback when complete
     * @param skipBlocks     Number of blocks to skip (for resume from saved state)
     */
    public AreaBuildTask(@Nonnull ServerLevel level, @Nonnull AreaDefinition definition,
            int blocksPerTick, @Nonnull Consumer<AreaBuildTask> onComplete, int skipBlocks) {
        this.level = Objects.requireNonNull(level);
        this.definition = Objects.requireNonNull(definition);
        this.blocksPerTick = blocksPerTick > 0 ? blocksPerTick : DEFAULT_BLOCKS_PER_TICK;
        this.onComplete = Objects.requireNonNull(onComplete);
        this.blocksToSkip = Math.max(0, skipBlocks);

        initializeSteps();

        if (this.blocksToSkip > 0) {
            LOGGER.info("[Area] Build task will skip first {} blocks (resume mode)", this.blocksToSkip);
        }
    }

    /**
     * Creates a build task with default blocks per tick.
     */
    public AreaBuildTask(@Nonnull ServerLevel level, @Nonnull AreaDefinition definition,
            @Nonnull Consumer<AreaBuildTask> onComplete) {
        this(level, definition, DEFAULT_BLOCKS_PER_TICK, onComplete, 0);
    }

    /**
     * Creates a build task from a pre-computed block map.
     * Use this for biome generation where blocks are pre-calculated.
     *
     * @param level          The server level
     * @param definition     The area definition
     * @param precomputedBlocks Pre-computed blocks to place
     * @param blocksPerTick  Blocks to place per tick
     * @param onComplete     Callback when complete
     */
    public AreaBuildTask(@Nonnull ServerLevel level, @Nonnull AreaDefinition definition,
            @Nonnull Map<BlockPos, BlockState> precomputedBlocks,
            int blocksPerTick, @Nonnull Consumer<AreaBuildTask> onComplete) {
        this(level, definition, precomputedBlocks, blocksPerTick, onComplete, 0);
    }

    /**
     * Creates a build task from a pre-computed block map with skip capability.
     * Use this for biome generation where blocks are pre-calculated.
     *
     * @param level             The server level
     * @param definition        The area definition
     * @param precomputedBlocks Pre-computed blocks to place
     * @param blocksPerTick     Blocks to place per tick
     * @param onComplete        Callback when complete
     * @param skipBlocks        Number of blocks to skip (for resume from saved state)
     */
    public AreaBuildTask(@Nonnull ServerLevel level, @Nonnull AreaDefinition definition,
            @Nonnull Map<BlockPos, BlockState> precomputedBlocks,
            int blocksPerTick, @Nonnull Consumer<AreaBuildTask> onComplete, int skipBlocks) {
        this.level = Objects.requireNonNull(level);
        this.definition = Objects.requireNonNull(definition);
        this.blocksPerTick = blocksPerTick > 0 ? blocksPerTick : DEFAULT_BLOCKS_PER_TICK;
        this.onComplete = Objects.requireNonNull(onComplete);
        this.blocksToSkip = Math.max(0, skipBlocks);

        // Add precomputed blocks as a single step
        steps.add(new BuildStep("biome_terrain", Objects.requireNonNull(precomputedBlocks)));
        totalBlockCount = precomputedBlocks.size();

        LOGGER.info("[Area] Build task (precomputed) initialized with {} blocks for '{}'{}",
            totalBlockCount, definition.name(),
            blocksToSkip > 0 ? " (skipping " + blocksToSkip + " blocks)" : "");
    }

    /**
     * Initializes build steps using shared AreaBlockMapGenerator.
     * This ensures consistent building logic with AreaBuilder (synchronous).
     */
    private void initializeSteps() {
        AreaOptions options = definition.options();

        // Step 1: Floor (with checkerboard pattern)
        if (options.hasFloor()) {
            Map<BlockPos, BlockState> floorBlocks = AreaBlockMapGenerator.generateFloorBlocks(Objects.requireNonNull(definition));
            steps.add(new BuildStep("floor", floorBlocks));
        }

        // Step 2: Walls (with full WallStyle support)
        if (options.hasWalls()) {
            Map<BlockPos, BlockState> wallBlocks = AreaBlockMapGenerator.generateWallBlocks(Objects.requireNonNull(definition));
            steps.add(new BuildStep("walls", wallBlocks));
        }

        // Step 3: Ceiling (with light grid pattern)
        if (options.hasCeiling()) {
            Map<BlockPos, BlockState> ceilingBlocks = AreaBlockMapGenerator.generateCeilingBlocks(Objects.requireNonNull(definition));
            steps.add(new BuildStep("ceiling", ceilingBlocks));
        }

        // Cache total block count for O(1) progress calculation
        totalBlockCount = steps.stream().mapToInt(s -> s.blocks().size()).sum();

        LOGGER.info("[Area] Build task initialized with {} steps, {} total blocks for '{}'",
            steps.size(), totalBlockCount, definition.name());
    }

    /**
     * MED-02 fix: Prepends a clear step to clear the area before building.
     * This allows clearing to happen asynchronously as part of the multi-tick build,
     * instead of synchronously before the build starts.
     *
     * <p>Must be called immediately after construction, before the first tick().
     *
     * @return this task for fluent chaining
     */
    @Nonnull
    public AreaBuildTask withClearStep() {
        if (completed || closed) {
            LOGGER.warn("[Area] Cannot add clear step to completed/closed task");
            return this;
        }

        Map<BlockPos, BlockState> clearBlocks = AreaBlockMapGenerator.generateClearBlocks(
            Objects.requireNonNull(definition));

        // Create a new queue with clear step first
        Queue<BuildStep> newSteps = new ArrayDeque<>(steps.size() + 1);
        newSteps.add(new BuildStep("clear", clearBlocks));
        newSteps.addAll(steps);
        steps.clear();
        steps.addAll(newSteps);

        // Update total block count
        totalBlockCount += clearBlocks.size();

        LOGGER.info("[Area] Added clear step ({} blocks) to build task for '{}'",
            clearBlocks.size(), definition.name());

        return this;
    }

    /**
     * Processes one tick of building.
     * Thread-safe: synchronized on tickLock to prevent concurrent access.
     *
     * @return True if the build is complete
     */
    public boolean tick() {
        // Early exit for completed or closed tasks (volatile reads, no lock needed)
        if (completed || closed) {
            return true;
        }

        synchronized (tickLock) {
            // Double-check after acquiring lock
            if (completed || closed) {
                return true;
            }

            int blocksThisTick = 0;

            while (blocksThisTick < blocksPerTick) {
                // Get next position to build
                if (currentIterator == null || !currentIterator.hasNext()) {
                    // Move to next step
                    if (!advanceToNextStep()) {
                        // All steps complete
                        completed = true;
                        LOGGER.info("[Area] Build complete for '{}': {} blocks placed{}",
                            definition.name(), totalBlocksPlaced,
                            failedBlockCount > 0 ? " (" + failedBlockCount + " failed)" : "");
                        onComplete.accept(this);
                        return true;
                    }
                }

                var localIterator = this.currentIterator;
                var localStep = this.currentStep;
                if (localIterator != null && localIterator.hasNext() && localStep != null) {
                    BlockPos pos = localIterator.next();

                    // Skip blocks for resume functionality (C-01 fix)
                    // MED-02 fix: Count skipped blocks against tick budget to avoid lag spike
                    // HIGH-01 fix: Also increment totalBlocksPlaced so progress shows correctly after resume
                    if (blocksSkipped < blocksToSkip) {
                        blocksSkipped++;
                        blocksThisTick++; // Count skip against budget
                        totalBlocksPlaced++; // Count as placed (already placed in previous session)
                        continue;
                    }

                    BlockState state = localStep.blocks().get(pos);

                    // CRITICAL FIX: For biome builds, we must place ALL blocks including CAVE_AIR
                    // to preserve cave carving. For clear steps, we must place AIR to remove blocks.
                    // For regular builds (floor/walls/ceiling), skip air.
                    boolean isBiomeBuild = "biome_terrain".equals(localStep.name());
                    boolean isClearStep = "clear".equals(localStep.name());
                    boolean shouldPlace = state != null && (isBiomeBuild || isClearStep || !state.isAir());

                    if (shouldPlace) {
                        // Wrap block placement in try-catch (H-01 fix)
                        try {
                            level.setBlock(Objects.requireNonNull(pos), Objects.requireNonNull(state), AREA_BUILD_FLAGS);
                            totalBlocksPlaced++;
                        } catch (Exception e) {
                            // Log and continue - don't let single block failure crash the build
                            failedBlockCount++;
                            if (failedBlockCount <= 10) {
                                LOGGER.warn("[Area] Failed to place block at {}: {}", pos, e.getMessage());
                            } else if (failedBlockCount == 11) {
                                LOGGER.warn("[Area] Suppressing further block placement errors...");
                            }
                        }
                    }

                    // HIGH-BUDGET fix: Always count against tick budget when processing a position,
                    // whether or not we actually place a block. This prevents the while loop from
                    // running indefinitely when many air blocks are skipped in non-biome/non-clear steps.
                    blocksThisTick++;
                }
            }

            return false;
        }
    }

    private boolean advanceToNextStep() {
        BuildStep step = steps.poll();
        if (step == null) {
            currentStep = null;
            currentIterator = null;
            return false;
        }

        currentStep = step;
        LOGGER.debug("[Area] Starting build step '{}' with {} blocks",
            step.name(), step.blocks().size());

        currentIterator = step.blocks().keySet().iterator();
        return true;
    }

    /**
     * Gets the current progress as a percentage.
     * O(1) - uses cached totalBlockCount instead of iterating steps.
     */
    public float getProgress() {
        if (completed) {
            return 1.0f;
        }
        return totalBlockCount > 0 ? (float) totalBlocksPlaced / totalBlockCount : 0.0f;
    }

    /**
     * Gets the total block count for this build task.
     */
    public int getTotalBlockCount() {
        return totalBlockCount;
    }

    /**
     * Gets the current step name.
     */
    public String getCurrentStepName() {
        return currentStep != null ? currentStep.name() : "complete";
    }

    /**
     * Gets total blocks placed so far.
     */
    public int getTotalBlocksPlaced() {
        return totalBlocksPlaced;
    }

    /**
     * Checks if the build is complete.
     */
    public boolean isCompleted() {
        return completed;
    }

    /**
     * Gets the area definition.
     */
    @Nonnull
    public AreaDefinition getDefinition() {
        return Objects.requireNonNull(definition);
    }

    /**
     * Gets the number of blocks skipped (for resume tracking).
     */
    public int getBlocksSkipped() {
        return blocksSkipped;
    }

    /**
     * Gets the number of failed block placements.
     */
    public int getFailedBlockCount() {
        return failedBlockCount;
    }

    /**
     * Checks if this task has been closed.
     */
    public boolean isClosed() {
        return closed;
    }

    /**
     * Cleans up resources held by this task (C-03 fix).
     * Releases references to large block maps to prevent memory leaks.
     * Safe to call multiple times.
     */
    @Override
    public void close() {
        synchronized (tickLock) {
            if (closed) {
                return;
            }
            closed = true;

            // Clear all steps to release block map references
            int stepsCleared = steps.size();
            steps.clear();
            currentStep = null;
            currentIterator = null;

            if (stepsCleared > 0) {
                LOGGER.debug("[Area] Cleaned up {} build steps for '{}'", stepsCleared, definition.name());
            }
        }
    }

    /**
     * A single build step containing blocks to place.
     */
    private record BuildStep(String name, Map<BlockPos, BlockState> blocks) {}
}
