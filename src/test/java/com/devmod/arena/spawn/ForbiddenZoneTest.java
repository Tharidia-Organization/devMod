package com.devmod.arena.spawn;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ForbiddenZoneTest {

    @Test
    void boxFactory() {
        ForbiddenZone zone = ForbiddenZone.box(0, 0, 0, 10, 10, 10);
        assertEquals("forbidden zone", zone.description());
        assertNotNull(zone.id());
    }

    @Test
    void cylinderFactory() {
        ForbiddenZone zone = ForbiddenZone.cylinder(50, 50, 10, 0, 64, "spawn protection");
        assertEquals(40.0, zone.minX());
        assertEquals(60.0, zone.maxX());
        assertEquals(40.0, zone.minZ());
        assertEquals(60.0, zone.maxZ());
        assertEquals(0.0, zone.minY());
        assertEquals(64.0, zone.maxY());
    }

    @Test
    void sphereFactory() {
        ForbiddenZone zone = ForbiddenZone.sphere(0, 32, 0, 5, "boss area");
        assertEquals(-5.0, zone.minX());
        assertEquals(5.0, zone.maxX());
        assertEquals(27.0, zone.minY());
        assertEquals(37.0, zone.maxY());
    }

    @Test
    void boundsNormalization() {
        // Pass inverted bounds; constructor should swap them
        ForbiddenZone zone = new ForbiddenZone(10, 20, 30, 0, 0, 0, "test");
        assertEquals(0.0, zone.minX());
        assertEquals(10.0, zone.maxX());
        assertEquals(0.0, zone.minY());
        assertEquals(20.0, zone.maxY());
        assertEquals(0.0, zone.minZ());
        assertEquals(30.0, zone.maxZ());
    }

    @Test
    void containsPoint() {
        ForbiddenZone zone = ForbiddenZone.box(0, 0, 0, 10, 10, 10);
        assertTrue(zone.contains(5, 5, 5));
        assertTrue(zone.contains(0, 0, 0));   // min edge
        assertTrue(zone.contains(10, 10, 10)); // max edge
        assertFalse(zone.contains(11, 5, 5));
        assertFalse(zone.contains(-1, 5, 5));
    }

    @Test
    void containsSpawnSlot() {
        ForbiddenZone zone = ForbiddenZone.box(0, 0, 0, 10, 10, 10);
        SpawnSlot inside = SpawnSlot.at(5, 5, 5);
        SpawnSlot outside = SpawnSlot.at(15, 5, 5);
        assertTrue(zone.contains(inside));
        assertFalse(zone.contains(outside));
    }

    @Test
    void overlaps() {
        ForbiddenZone a = ForbiddenZone.box(0, 0, 0, 10, 10, 10);
        ForbiddenZone b = ForbiddenZone.box(5, 5, 5, 15, 15, 15);
        ForbiddenZone c = ForbiddenZone.box(20, 20, 20, 30, 30, 30);
        assertTrue(a.overlaps(b));
        assertTrue(b.overlaps(a));
        assertFalse(a.overlaps(c));
    }

    @Test
    void center() {
        ForbiddenZone zone = ForbiddenZone.box(0, 0, 0, 10, 10, 10);
        double[] center = zone.getCenter();
        assertEquals(5.0, center[0]);
        assertEquals(5.0, center[1]);
        assertEquals(5.0, center[2]);
    }

    @Test
    void volume() {
        ForbiddenZone zone = ForbiddenZone.box(0, 0, 0, 10, 20, 5);
        assertEquals(1000.0, zone.getVolume());
    }

    @Test
    void expand() {
        ForbiddenZone zone = ForbiddenZone.box(5, 5, 5, 10, 10, 10);
        ForbiddenZone expanded = zone.expand(2);
        assertEquals(3.0, expanded.minX());
        assertEquals(12.0, expanded.maxX());
        assertEquals(3.0, expanded.minY());
        assertEquals(12.0, expanded.maxY());
    }
}
