package com.devmod.arena.builder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;

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

    private final UUID transactionId;
    private final String templateId;
    private final Instant startTime;

    // DD7: Track all changes for rollback
    private final CompactBlockTracker blockTracker;
    private final List<UUID> spawnedEntities = new ArrayList<>();
    private final Set<Long> forcedChunks = new HashSet<>(); // ChunkPos packed as long

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
        this(templateId, CompactBlockTracker.MAX_TRACKED_BLOCKS);
    }

    public BuildTransaction(String templateId, int maxBlocks) {
        this.transactionId = UUID.randomUUID();
        this.templateId = templateId;
        this.startTime = Instant.now();
        this.blockTracker = new CompactBlockTracker(maxBlocks);
    }

    /**
     * Tracks a block change.
     */
    public void trackBlock(long packedPos, int previousStateId) {
        checkActive();
        blockTracker.track(packedPos, previousStateId);
    }

    /**
     * Tracks a spawned entity.
     */
    public void trackEntity(UUID entityId) {
        checkActive();
        spawnedEntities.add(entityId);
    }

    /**
     * Tracks a forced chunk.
     */
    public void trackChunk(long packedChunkPos) {
        checkActive();
        forcedChunks.add(packedChunkPos);
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
        for (UUID entityId : spawnedEntities) {
            try {
                if (entityRemover.remove(entityId)) {
                    entitiesRolledBack++;
                }
            } catch (Exception e) {
                errors.add("Entity " + entityId + ": " + e.getMessage());
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
