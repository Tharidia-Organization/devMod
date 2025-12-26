package com.devmod.arena.cleanup;

import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
public class ArenaCleanupExecutor {

    private static final Logger LOGGER = LoggerFactory.getLogger(ArenaCleanupExecutor.class);

    /**
     * Configuration for cleanup behavior.
     */
    public record CleanupConfig(
        boolean preservePlayers,
        boolean clearContainersBeforeRemove,
        int maxBlocksPerTick,
        boolean verifyAfterCleanup
    ) {
        public static CleanupConfig defaults() {
            return new CleanupConfig(true, true, 10000, true);
        }
    }

    /**
     * Bounding box for arena region.
     */
    public record ArenaBounds(
        int minX, int minY, int minZ,
        int maxX, int maxY, int maxZ
    ) {
        public int volume() {
            return (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
        }
    }

    private final CleanupConfig config;
    private final Set<UUID> excludedEntities;
    private final LevelAccess levelAccess;

    public ArenaCleanupExecutor(LevelAccess levelAccess) {
        this(CleanupConfig.defaults(), Set.of(), levelAccess);
    }

    public ArenaCleanupExecutor(CleanupConfig config, Set<UUID> excludedEntities, LevelAccess levelAccess) {
        this.config = config;
        this.excludedEntities = excludedEntities;
        this.levelAccess = levelAccess;
    }

    /**
     * Executes full 4-phase cleanup on the specified region.
     *
     * @param bounds The arena bounds to clean
     * @return CleanupResult with statistics and any warnings
     */
    public CleanupResult execute(ArenaBounds bounds) {
        LOGGER.info("Starting arena cleanup for bounds {}", bounds);

        CleanupResult.Builder result = CleanupResult.builder();
        long startNs = System.nanoTime();

        try {
            // Phase 1: Remove entities
            int entities = executePhase1Entities(bounds, result);
            result.entitiesRemoved(entities);
            LOGGER.debug("Phase 1 complete: {} entities removed", entities);

            // Phase 2: Remove block entities
            int blockEntities = executePhase2BlockEntities(bounds, result);
            result.blockEntitiesRemoved(blockEntities);
            LOGGER.debug("Phase 2 complete: {} block entities removed", blockEntities);

            // Phase 3: Cancel scheduled ticks
            int ticks = executePhase3ScheduledTicks(bounds, result);
            result.scheduledTicksCancelled(ticks);
            LOGGER.debug("Phase 3 complete: {} scheduled ticks cancelled", ticks);

            // Phase 4: Remove blocks
            int blocks = executePhase4Blocks(bounds, result);
            result.blocksRemoved(blocks);
            LOGGER.debug("Phase 4 complete: {} blocks removed", blocks);

            // Optional verification
            if (config.verifyAfterCleanup()) {
                CleanupVerification verification = verify(bounds);
                if (!verification.isFullyClean()) {
                    result.addWarnings(verification.violations());
                }
            }

        } catch (Exception e) {
            LOGGER.error("Cleanup failed", e);
            result.addWarning("Cleanup failed: " + e.getMessage());
        }

        long durationMs = (System.nanoTime() - startNs) / 1_000_000;
        result.durationMs(durationMs);

        // Residual metrics for alerts/baseline
        int entitiesResidual = levelAccess.countEntitiesInBounds(
            bounds.minX(), bounds.minY(), bounds.minZ(),
            bounds.maxX(), bounds.maxY(), bounds.maxZ(),
            config.preservePlayers(),
            excludedEntities
        );
        int blocksResidual = levelAccess.countNonAirBlocksInBounds(
            bounds.minX(), bounds.minY(), bounds.minZ(),
            bounds.maxX(), bounds.maxY(), bounds.maxZ()
        );
        result.entitiesResidual(entitiesResidual);
        result.blocksResidual(blocksResidual);

        CleanupResult finalResult = result.build();
        LOGGER.info("Arena cleanup complete: {}", finalResult);
        return finalResult;
    }

    private int executePhase1Entities(ArenaBounds bounds, CleanupResult.Builder result) {
        return levelAccess.removeEntitiesInBounds(
            bounds.minX(), bounds.minY(), bounds.minZ(),
            bounds.maxX(), bounds.maxY(), bounds.maxZ(),
            config.preservePlayers(),
            excludedEntities
        );
    }

    private int executePhase2BlockEntities(ArenaBounds bounds, CleanupResult.Builder result) {
        return levelAccess.removeBlockEntitiesInBounds(
            bounds.minX(), bounds.minY(), bounds.minZ(),
            bounds.maxX(), bounds.maxY(), bounds.maxZ(),
            config.clearContainersBeforeRemove()
        );
    }

    private int executePhase3ScheduledTicks(ArenaBounds bounds, CleanupResult.Builder result) {
        return levelAccess.cancelScheduledTicksInBounds(
            bounds.minX(), bounds.minY(), bounds.minZ(),
            bounds.maxX(), bounds.maxY(), bounds.maxZ()
        );
    }

    private int executePhase4Blocks(ArenaBounds bounds, CleanupResult.Builder result) {
        return levelAccess.setBlocksToAirInBounds(
            bounds.minX(), bounds.minY(), bounds.minZ(),
            bounds.maxX(), bounds.maxY(), bounds.maxZ(),
            config.maxBlocksPerTick()
        );
    }

    /**
     * Verifies that the arena region is fully clean after cleanup.
     */
    public CleanupVerification verify(ArenaBounds bounds) {
        CleanupVerification.Builder verification = CleanupVerification.builder();

        int remainingEntities = levelAccess.countEntitiesInBounds(
            bounds.minX(), bounds.minY(), bounds.minZ(),
            bounds.maxX(), bounds.maxY(), bounds.maxZ(),
            config.preservePlayers(),
            excludedEntities
        );

        if (remainingEntities > 0) {
            verification.addViolation(CleanupPhase.ENTITIES,
                remainingEntities + " entities still present");
        }

        int remainingBlockEntities = levelAccess.countBlockEntitiesInBounds(
            bounds.minX(), bounds.minY(), bounds.minZ(),
            bounds.maxX(), bounds.maxY(), bounds.maxZ()
        );

        if (remainingBlockEntities > 0) {
            verification.addViolation(CleanupPhase.BLOCK_ENTITIES,
                remainingBlockEntities + " block entities still present");
        }

        int remainingBlocks = levelAccess.countNonAirBlocksInBounds(
            bounds.minX(), bounds.minY(), bounds.minZ(),
            bounds.maxX(), bounds.maxY(), bounds.maxZ()
        );

        if (remainingBlocks > 0) {
            verification.addViolation(CleanupPhase.BLOCKS,
                remainingBlocks + " non-air blocks still present");
        }

        return verification.build();
    }

    /**
     * Executes cleanup asynchronously across multiple ticks.
     */
    public void executeAsync(ArenaBounds bounds, Consumer<CleanupResult> callback) {
        // Async execution would use a scheduler
        CleanupResult result = execute(bounds);
        callback.accept(result);
    }

    /**
     * Interface for level access operations.
     * Implementations should delegate to actual Minecraft level operations.
     */
    public interface LevelAccess {
        int removeEntitiesInBounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ,
                                   boolean preservePlayers, Set<UUID> excludedEntities);

        int removeBlockEntitiesInBounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ,
                                        boolean clearContainers);

        int cancelScheduledTicksInBounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ);

        int setBlocksToAirInBounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ,
                                   int maxBlocksPerTick);

        int countEntitiesInBounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ,
                                  boolean excludePlayers, Set<UUID> excludedEntities);

        int countBlockEntitiesInBounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ);

        int countNonAirBlocksInBounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ);
    }
}
