package com.devmod.arena.api;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class ArenaHandleTest {

    // ---- AABB ----

    @Test
    void aabbSize() {
        var aabb = new ArenaHandle.AABB(0, 0, 0, 9, 4, 9);
        assertEquals(10, aabb.sizeX());
        assertEquals(5, aabb.sizeY());
        assertEquals(10, aabb.sizeZ());
    }

    @Test
    void aabbVolume() {
        var aabb = new ArenaHandle.AABB(0, 0, 0, 9, 4, 9);
        assertEquals(500, aabb.volume());
    }

    @Test
    void aabbContains() {
        var aabb = new ArenaHandle.AABB(0, 0, 0, 10, 10, 10);
        assertTrue(aabb.contains(5, 5, 5));
        assertTrue(aabb.contains(0, 0, 0));
        assertTrue(aabb.contains(10, 10, 10));
        assertFalse(aabb.contains(11, 5, 5));
        assertFalse(aabb.contains(-1, 5, 5));
    }

    @Test
    void aabbExpand() {
        var aabb = new ArenaHandle.AABB(5, 5, 5, 10, 10, 10);
        var expanded = aabb.expand(2);
        assertEquals(3, expanded.minX());
        assertEquals(3, expanded.minY());
        assertEquals(12, expanded.maxX());
        assertEquals(12, expanded.maxZ());
    }

    // ---- BlockPos ----

    @Test
    void blockPosZero() {
        assertEquals(0, ArenaHandle.BlockPos.ZERO.x());
        assertEquals(0, ArenaHandle.BlockPos.ZERO.y());
        assertEquals(0, ArenaHandle.BlockPos.ZERO.z());
    }

    @Test
    void blockPosOffset() {
        var pos = new ArenaHandle.BlockPos(10, 20, 30);
        var offset = pos.offset(1, 2, 3);
        assertEquals(11, offset.x());
        assertEquals(22, offset.y());
        assertEquals(33, offset.z());
    }

    @Test
    void blockPosAboveBelow() {
        var pos = new ArenaHandle.BlockPos(0, 64, 0);
        assertEquals(65, pos.above().y());
        assertEquals(63, pos.below().y());
    }

    @Test
    void blockPosDistance() {
        var a = new ArenaHandle.BlockPos(0, 0, 0);
        var b = new ArenaHandle.BlockPos(3, 4, 0);
        assertEquals(25.0, a.distanceSquared(b));
        assertEquals(5.0, a.distance(b), 0.001);
    }

    // ---- ArenaHandle ----

    @Test
    void primaryPlayerSpawnFallsBackToZero() {
        ArenaHandle handle = createHandle(List.of(), List.of());
        assertEquals(ArenaHandle.BlockPos.ZERO, handle.primaryPlayerSpawn());
    }

    @Test
    void primaryPlayerSpawn() {
        var spawn = new ArenaHandle.BlockPos(10, 64, 20);
        ArenaHandle handle = createHandle(List.of(spawn), List.of());
        assertEquals(spawn, handle.primaryPlayerSpawn());
    }

    @Test
    void primaryMobSpawnNull() {
        ArenaHandle handle = createHandle(List.of(), List.of());
        assertNull(handle.primaryMobSpawn());
    }

    @Test
    void primaryMobSpawn() {
        var spawn = new ArenaHandle.BlockPos(5, 64, 5);
        ArenaHandle handle = createHandle(List.of(), List.of(spawn));
        assertEquals(spawn, handle.primaryMobSpawn());
    }

    @Test
    void contains() {
        ArenaHandle handle = createHandle(List.of(), List.of());
        assertTrue(handle.contains(5, 5, 5));
        assertFalse(handle.contains(100, 100, 100));
    }

    @Test
    void center() {
        ArenaHandle handle = createHandle(List.of(), List.of());
        var center = handle.center();
        assertEquals(5, center.x());
        assertEquals(5, center.y());
        assertEquals(5, center.z());
    }

    @Test
    void volume() {
        ArenaHandle handle = createHandle(List.of(), List.of());
        assertEquals(new ArenaHandle.AABB(0, 0, 0, 10, 10, 10).volume(), handle.volume());
    }

    @Test
    void builderValidation() {
        assertThrows(IllegalStateException.class, () -> ArenaHandle.builder().build());
        assertThrows(IllegalStateException.class, () ->
            ArenaHandle.builder().arenaId(UUID.randomUUID()).build());
    }

    @Test
    void builderFullyConfigured() {
        UUID arenaId = UUID.randomUUID();
        UUID instanceId = UUID.randomUUID();
        ArenaHandle handle = ArenaHandle.builder()
            .arenaId(arenaId)
            .instanceId(instanceId)
            .templateId("template1")
            .templateVersion(1)
            .policyId("policy1")
            .policyVersion(2)
            .bounds(0, 0, 0, 10, 10, 10)
            .origin(5, 5, 5)
            .playerSpawnPositions(List.of(new ArenaHandle.BlockPos(5, 5, 5)))
            .mobSpawnPositions(List.of(new ArenaHandle.BlockPos(7, 5, 7)))
            .build();

        assertEquals(arenaId, handle.arenaId());
        assertEquals(instanceId, handle.instanceId());
        assertEquals("template1", handle.templateId());
        assertEquals(1, handle.templateVersion());
        assertEquals("policy1", handle.policyId());
        assertEquals(5, handle.originX());
    }

    private ArenaHandle createHandle(List<ArenaHandle.BlockPos> playerSpawns, List<ArenaHandle.BlockPos> mobSpawns) {
        return ArenaHandle.builder()
            .arenaId(UUID.randomUUID())
            .instanceId(UUID.randomUUID())
            .templateId("test")
            .policyId("default")
            .bounds(0, 0, 0, 10, 10, 10)
            .playerSpawnPositions(playerSpawns)
            .mobSpawnPositions(mobSpawns)
            .build();
    }
}
