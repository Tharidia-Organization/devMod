package com.devmod.arena.builder;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

class BuildTransactionTest {

    @Test
    void initialState() {
        BuildTransaction tx = new BuildTransaction("template1");
        assertEquals("template1", tx.getTemplateId());
        assertEquals(BuildTransaction.TransactionState.ACTIVE, tx.getState());
        assertEquals(0, tx.getBlockCount());
        assertEquals(0, tx.getEntityCount());
        assertEquals(0, tx.getChunkCount());
        assertNotNull(tx.getTransactionId());
        assertNotNull(tx.getStartTime());
    }

    @Test
    void trackBlock() {
        BuildTransaction tx = new BuildTransaction("t1");
        tx.trackBlock(CompactBlockTracker.pack(0, 64, 0), 1);
        tx.trackBlock(CompactBlockTracker.pack(1, 64, 0), 2);
        assertEquals(2, tx.getBlockCount());
        assertEquals(2, tx.getTotalBlockPlacements());
    }

    @Test
    void trackEntity() {
        BuildTransaction tx = new BuildTransaction("t1");
        UUID entityId = UUID.randomUUID();
        tx.trackEntity(entityId);
        assertEquals(1, tx.getEntityCount());
    }

    @Test
    void trackEntityWithFullState() {
        BuildTransaction tx = new BuildTransaction("t1");
        var entity = new BuildTransaction.EntitySpawn(UUID.randomUUID(), "zombie", 10, 64, 20, 0, 0);
        tx.trackEntity(entity);
        assertEquals(1, tx.getEntityCount());
    }

    @Test
    void trackChunk() {
        BuildTransaction tx = new BuildTransaction("t1");
        tx.trackChunk(12345L);
        assertEquals(1, tx.getChunkCount());
    }

    @Test
    void trackStructurePlacement() {
        BuildTransaction tx = new BuildTransaction("t1");
        tx.trackStructurePlacement("devmod:arena", 0, 64, 0);
        assertEquals(1, tx.getStructurePlacements().size());
        assertEquals("devmod:arena", tx.getStructurePlacements().get(0).path());
    }

    @Test
    void commit() {
        BuildTransaction tx = new BuildTransaction("t1");
        tx.commit();
        assertEquals(BuildTransaction.TransactionState.COMMITTED, tx.getState());
    }

    @Test
    void cannotTrackAfterCommit() {
        BuildTransaction tx = new BuildTransaction("t1");
        tx.commit();
        assertThrows(IllegalStateException.class, () ->
            tx.trackBlock(CompactBlockTracker.pack(0, 0, 0), 0));
    }

    @Test
    void rollback() {
        BuildTransaction tx = new BuildTransaction("t1");
        UUID entityId = UUID.randomUUID();
        tx.trackBlock(CompactBlockTracker.pack(0, 64, 0), 1);
        tx.trackEntity(entityId);
        tx.trackChunk(100L);

        AtomicInteger blocksReverted = new AtomicInteger();
        AtomicInteger entitiesRemoved = new AtomicInteger();
        AtomicInteger chunksReleased = new AtomicInteger();

        BuildTransaction.RollbackResult result = tx.rollback(
            (pos, stateId) -> { blocksReverted.incrementAndGet(); return true; },
            id -> { entitiesRemoved.incrementAndGet(); return true; },
            pos -> chunksReleased.incrementAndGet()
        );

        assertTrue(result.success());
        assertEquals(1, result.blocksReverted());
        assertEquals(1, result.entitiesRemoved());
        assertEquals(1, result.chunksReleased());
        assertEquals(BuildTransaction.TransactionState.ROLLED_BACK, tx.getState());
    }

    @Test
    void cannotRollbackAfterCommit() {
        BuildTransaction tx = new BuildTransaction("t1");
        tx.commit();
        BuildTransaction.RollbackResult result = tx.rollback(
            (p, s) -> true, id -> true, p -> {});
        assertFalse(result.success());
    }

    @Test
    void markFailed() {
        BuildTransaction tx = new BuildTransaction("t1");
        tx.markFailed();
        assertEquals(BuildTransaction.TransactionState.FAILED, tx.getState());
    }

    @Test
    void failedCanStillRollback() {
        BuildTransaction tx = new BuildTransaction("t1");
        tx.trackBlock(CompactBlockTracker.pack(0, 0, 0), 1);
        tx.markFailed();
        BuildTransaction.RollbackResult result = tx.rollback(
            (p, s) -> true, id -> true, p -> {});
        assertTrue(result.success());
    }

    @Test
    void entitySpawnRecord() {
        var spawn = new BuildTransaction.EntitySpawn(UUID.randomUUID(), "zombie", 1, 2, 3, 90, 0);
        assertEquals("zombie", spawn.entityType());
        assertEquals(1.0, spawn.x());
        assertEquals(90.0f, spawn.yaw());
    }

    @Test
    void blockStateRecord() {
        var state = new BuildTransaction.BlockState(12345L, 42, "nbt");
        assertEquals(12345L, state.packedPos());
        assertEquals(42, state.stateId());
        assertEquals("nbt", state.nbtData());

        var stateNoNbt = new BuildTransaction.BlockState(12345L, 42);
        assertNull(stateNoNbt.nbtData());
    }

    @Test
    void structurePlacementRecord() {
        var placement = new BuildTransaction.StructurePlacement("path", 10, 20, 30);
        assertEquals("path", placement.path());
        assertEquals(10, placement.originX());
        assertEquals(20, placement.originY());
        assertEquals(30, placement.originZ());
    }

    @Test
    void transactionExpiredException() {
        var ex = new BuildTransaction.TransactionExpiredException("expired");
        assertEquals("expired", ex.getMessage());
        assertInstanceOf(RuntimeException.class, ex);
    }
}
