package com.devmod.arena.builder;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Transactional build with full rollback capability (DD7).
 *
 * <p>Tracks:
 * <ul>
 *   <li>Block changes (using CompactBlockTracker)</li>
 *   <li>Spawned entities (UUID list)</li>
 *   <li>Forced chunks (for ticket release)</li>
 * </ul>
 *
 * <p>On failure or abort:
 * <ol>
 *   <li>Remove spawned entities</li>
 *   <li>Revert blocks in reverse order</li>
 *   <li>Release chunk tickets</li>
 * </ol>
 */
public class BuildTransaction {

    private static final Logger LOGGER = LoggerFactory.getLogger(BuildTransaction.class);

    // Default max transaction duration: 5 minutes
    private static final long DEFAULT_MAX_DURATION_MS = 5 * 60 * 1000;

    private final UUID transactionId;
    private final String templateId;
    private final Instant startTime;
    private final long maxDurationMs;
    private long totalBlockPlacements = 0;

    // DD7: Track all changes for rollback
    private final CompactBlockTracker blockTracker;
    private final List<EntitySpawn> spawnedEntities = new ArrayList<>();
    private final Set<Long> forcedChunks = new HashSet<>(); // ChunkPos packed as long
    private final List<StructurePlacement> structurePlacements = new ArrayList<>();

    /**
     * DD7: Entity spawn tracking with full state for rollback.
     */
    public record EntitySpawn(
        UUID entityId,
        String entityType,
        double x, double y, double z,
        float yaw, float pitch
    ) {}

    /**
     * DD7: Block state object for precise rollback.
     */
    public record BlockState(
        long packedPos,
        int stateId,
        String nbtData // nullable, for block entities
    ) {
        public BlockState(long packedPos, int stateId) {
            this(packedPos, stateId, null);
        }
    }

    /**
     * DD7: Structure placement tracking for rollback.
     */
    public record StructurePlacement(
        String path,
        int originX,
        int originY,
        int originZ
    ) {}

    // State tracking
    private TransactionState state = TransactionState.ACTIVE;
    private int entitiesRolledBack = 0;
    private int blocksRolledBack = 0;
    private long rollbackDurationMs = 0;

    public enum TransactionState {
        ACTIVE,
        COMMITTED,
        ROLLED_BACK,
        FAILED
    }

    public BuildTransaction(String templateId) {
        this(templateId, CompactBlockTracker.MAX_TRACKED_BLOCKS, DEFAULT_MAX_DURATION_MS);
    }

    public BuildTransaction(String templateId, int maxBlocks) {
        this(templateId, maxBlocks, DEFAULT_MAX_DURATION_MS);
    }

    public BuildTransaction(String templateId, int maxBlocks, long maxDurationMs) {
        this.transactionId = UUID.randomUUID();
        this.templateId = templateId;
        this.startTime = Instant.now();
        this.blockTracker = new CompactBlockTracker(maxBlocks);
        this.maxDurationMs = maxDurationMs;
    }

    /**
     * Tracks a block change.
     */
    public void trackBlock(long packedPos, int previousStateId) {
        checkActive();
        blockTracker.track(packedPos, previousStateId);
        totalBlockPlacements++;
    }

    /**
     * Tracks a spawned entity (legacy - uses default position).
     */
    public void trackEntity(UUID entityId) {
        checkActive();
        spawnedEntities.add(new EntitySpawn(entityId, "unknown", 0, 0, 0, 0, 0));
    }

    /**
     * DD7: Tracks a spawned entity with full state for rollback.
     */
    public void trackEntity(EntitySpawn entity) {
        checkActive();
        spawnedEntities.add(entity);
    }

    /**
     * Tracks a forced chunk.
     */
    public void trackChunk(long packedChunkPos) {
        checkActive();
        forcedChunks.add(packedChunkPos);
    }

    /**
     * DD7: Tracks a structure placement for potential rollback.
     */
    public void trackStructurePlacement(String path, int originX, int originY, int originZ) {
        checkActive();
        structurePlacements.add(new StructurePlacement(path, originX, originY, originZ));
    }

    /**
     * Returns tracked structure placements (for rollback purposes).
     */
    public List<StructurePlacement> getStructurePlacements() {
        return List.copyOf(structurePlacements);
    }

    /**
     * Commits the transaction (no rollback possible after this).
     */
    public void commit() {
        checkActive();
        state = TransactionState.COMMITTED;
        LOGGER.debug("Transaction {} committed: {} blocks, {} entities, {} chunks",
            transactionId, blockTracker.size(), spawnedEntities.size(), forcedChunks.size());
    }

    /**
     * Rolls back all changes.
     *
     * @param blockReverter Function to revert a block (packedPos, stateId) -> success
     * @param entityRemover Function to remove an entity by UUID -> success
     * @param chunkReleaser Function to release a chunk ticket (packedChunkPos) -> void
     * @return RollbackResult with details
     */
    public RollbackResult rollback(
            BlockReverter blockReverter,
            EntityRemover entityRemover,
            ChunkReleaser chunkReleaser) {

        if (state != TransactionState.ACTIVE && state != TransactionState.FAILED) {
            LOGGER.warn("Cannot rollback transaction {} in state {}", transactionId, state);
            return new RollbackResult(false, 0, 0, 0, 0);
        }

        long rollbackStart = System.currentTimeMillis();
        List<String> errors = new ArrayList<>();

        // 1. Remove spawned entities first
        for (EntitySpawn entity : spawnedEntities) {
            try {
                if (entityRemover.remove(entity.entityId())) {
                    entitiesRolledBack++;
                }
            } catch (Exception e) {
                errors.add("Entity " + entity.entityId() + ": " + e.getMessage());
            }
        }

        // 2. Revert blocks in reverse order (DD7)
        for (CompactBlockTracker.BlockChange change : blockTracker.getChangesReversed()) {
            try {
                if (blockReverter.revert(change.packedPos(), change.previousStateId())) {
                    blocksRolledBack++;
                }
            } catch (Exception e) {
                errors.add("Block at " + change.packedPos() + ": " + e.getMessage());
            }
        }

        // 3. Release chunk tickets
        int chunksReleased = 0;
        for (Long chunkPos : forcedChunks) {
            try {
                chunkReleaser.release(chunkPos);
                chunksReleased++;
            } catch (Exception e) {
                errors.add("Chunk " + chunkPos + ": " + e.getMessage());
            }
        }

        rollbackDurationMs = System.currentTimeMillis() - rollbackStart;
        state = TransactionState.ROLLED_BACK;

        LOGGER.info("Transaction {} rolled back in {}ms: {} blocks, {} entities, {} chunks. Errors: {}",
            transactionId, rollbackDurationMs, blocksRolledBack, entitiesRolledBack, chunksReleased, errors.size());

        return new RollbackResult(
            errors.isEmpty(),
            blocksRolledBack,
            entitiesRolledBack,
            chunksReleased,
            rollbackDurationMs
        );
    }

    /**
     * Marks transaction as failed (can still rollback).
     */
    public void markFailed() {
        if (state == TransactionState.ACTIVE) {
            state = TransactionState.FAILED;
        }
    }

    private void checkActive() {
        if (state != TransactionState.ACTIVE) {
            throw new IllegalStateException("Transaction not active: " + state);
        }
        if (isExpired()) {
            state = TransactionState.FAILED;
            throw new TransactionExpiredException(
                "Transaction " + transactionId + " expired after " + getElapsedMs() + "ms (limit: " + maxDurationMs + "ms)");
        }
    }

    /**
     * Checks if this transaction has exceeded its maximum duration.
     */
    public boolean isExpired() {
        return getElapsedMs() > maxDurationMs;
    }

    /**
     * Returns the maximum duration for this transaction.
     */
    public long getMaxDurationMs() {
        return maxDurationMs;
    }

    /**
     * Returns remaining time before expiration in milliseconds.
     */
    public long getRemainingMs() {
        return Math.max(0, maxDurationMs - getElapsedMs());
    }

    /**
     * Exception thrown when a transaction exceeds its time limit.
     */
    public static class TransactionExpiredException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public TransactionExpiredException(String message) {
            super(message);
        }
    }

    // === Getters ===

    public UUID getTransactionId() {
        return transactionId;
    }

    public String getTemplateId() {
        return templateId;
    }

    public Instant getStartTime() {
        return startTime;
    }

    public int getBlockCount() {
        return blockTracker.size();
    }

    /**
     * Total block placements attempted (including overwrites).
     */
    public long getTotalBlockPlacements() {
        return totalBlockPlacements;
    }

    public int getEntityCount() {
        return spawnedEntities.size();
    }

    public int getChunkCount() {
        return forcedChunks.size();
    }

    public TransactionState getState() {
        return state;
    }

    public long getElapsedMs() {
        return System.currentTimeMillis() - startTime.toEpochMilli();
    }

    // === Functional Interfaces ===

    @FunctionalInterface
    public interface BlockReverter {
        boolean revert(long packedPos, int stateId);
    }

    @FunctionalInterface
    public interface EntityRemover {
        boolean remove(UUID entityId);
    }

    @FunctionalInterface
    public interface ChunkReleaser {
        void release(long packedChunkPos);
    }

    // === Result Record ===

    public record RollbackResult(
        boolean success,
        int blocksReverted,
        int entitiesRemoved,
        int chunksReleased,
        long durationMs
    ) {}
}
