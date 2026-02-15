package com.devmod.arena.builder;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.devmod.arena.registry.ArenaTemplate;

class ArenaShapeHelperTest {

    private ArenaTemplate templateOfSize(int size) {
        return ArenaTemplate.builder("test")
            .version(1).schemaVersion(1)
            .size(size)
            .build();
    }

    private ArenaTemplate templateOfSizeXZ(int sizeX, int sizeZ) {
        return ArenaTemplate.builder("test")
            .version(1).schemaVersion(1)
            .size(sizeX)
            .sizeX(sizeX).sizeZ(sizeZ)
            .build();
    }

    private ArenaTemplate circularTemplate(int size) {
        return ArenaTemplate.builder("test")
            .version(1).schemaVersion(1)
            .size(size)
            .arenaShape(ArenaTemplate.ArenaShape.CIRCULAR)
            .build();
    }

    private ArenaTemplate ringTemplate(int size, int innerRadius) {
        return ArenaTemplate.builder("test")
            .version(1).schemaVersion(1)
            .size(size)
            .arenaShape(ArenaTemplate.ArenaShape.RING)
            .ringInnerRadius(innerRadius)
            .build();
    }

    // === getSizeX / getSizeZ ===

    @Test
    void getSizeX_fallsBackToSize() {
        ArenaTemplate t = templateOfSize(32);
        assertEquals(32, ArenaShapeHelper.getSizeX(t));
    }

    @Test
    void getSizeX_usesExplicitSizeX() {
        ArenaTemplate t = templateOfSizeXZ(20, 30);
        assertEquals(20, ArenaShapeHelper.getSizeX(t));
        assertEquals(30, ArenaShapeHelper.getSizeZ(t));
    }

    // === Offsets ===

    @Test
    void offsetsAreSymmetric_evenSize() {
        ArenaTemplate t = templateOfSize(10);
        assertEquals(-5, ArenaShapeHelper.minOffsetX(t));
        assertEquals(4, ArenaShapeHelper.maxOffsetX(t));
        assertEquals(-5, ArenaShapeHelper.minOffsetZ(t));
        assertEquals(4, ArenaShapeHelper.maxOffsetZ(t));
    }

    @Test
    void offsetsAreSymmetric_oddSize() {
        ArenaTemplate t = templateOfSize(11);
        assertEquals(-5, ArenaShapeHelper.minOffsetX(t));
        assertEquals(5, ArenaShapeHelper.maxOffsetX(t));
    }

    // === isInArenaShape: RECTANGULAR ===

    @Test
    void rectangular_centerIsInside() {
        ArenaTemplate t = templateOfSize(10);
        assertTrue(ArenaShapeHelper.isInArenaShape(0, 0, t));
    }

    @Test
    void rectangular_cornersAreInside() {
        ArenaTemplate t = templateOfSize(10);
        assertTrue(ArenaShapeHelper.isInArenaShape(-5, -5, t));
        assertTrue(ArenaShapeHelper.isInArenaShape(4, 4, t));
    }

    @Test
    void rectangular_outsideIsOutside() {
        ArenaTemplate t = templateOfSize(10);
        assertFalse(ArenaShapeHelper.isInArenaShape(5, 0, t));
        assertFalse(ArenaShapeHelper.isInArenaShape(0, -6, t));
    }

    // === isInArenaShape: CIRCULAR ===

    @Test
    void circular_centerIsInside() {
        ArenaTemplate t = circularTemplate(20);
        assertTrue(ArenaShapeHelper.isInArenaShape(0, 0, t));
    }

    @Test
    void circular_onRadiusIsInside() {
        ArenaTemplate t = circularTemplate(20); // radius = 10
        assertTrue(ArenaShapeHelper.isInArenaShape(10, 0, t));
        assertTrue(ArenaShapeHelper.isInArenaShape(0, 10, t));
    }

    @Test
    void circular_outsideRadiusIsOutside() {
        ArenaTemplate t = circularTemplate(20); // radius = 10
        assertFalse(ArenaShapeHelper.isInArenaShape(10, 10, t)); // dist = ~14.14
    }

    // === isInArenaShape: RING ===

    @Test
    void ring_insideInnerRadiusIsOutside() {
        ArenaTemplate t = ringTemplate(20, 5); // outer=10, inner=5
        assertFalse(ArenaShapeHelper.isInArenaShape(0, 0, t)); // center is hollow
        assertFalse(ArenaShapeHelper.isInArenaShape(3, 0, t)); // inside inner radius
    }

    @Test
    void ring_betweenRadiiIsInside() {
        ArenaTemplate t = ringTemplate(20, 5);
        assertTrue(ArenaShapeHelper.isInArenaShape(7, 0, t)); // between 5 and 10
    }

    // === isOnCircularBorder ===

    @Test
    void circularBorder_rectangularAlwaysFalse() {
        ArenaTemplate t = templateOfSize(20);
        assertFalse(ArenaShapeHelper.isOnCircularBorder(0, 0, t, 2));
    }

    @Test
    void circularBorder_onEdgeIsTrue() {
        ArenaTemplate t = circularTemplate(20); // radius = 10
        // thickness=2 means border from radius to radius+2
        assertTrue(ArenaShapeHelper.isOnCircularBorder(10, 0, t, 2));
        assertTrue(ArenaShapeHelper.isOnCircularBorder(11, 0, t, 2));
    }

    @Test
    void circularBorder_insideIsNotBorder() {
        ArenaTemplate t = circularTemplate(20);
        assertFalse(ArenaShapeHelper.isOnCircularBorder(5, 0, t, 2));
    }

    // === isOnFloorBorder ===

    @Test
    void floorBorder_rectangular_edgesAreBorder() {
        ArenaTemplate t = templateOfSize(10); // min=-5, max=4
        assertTrue(ArenaShapeHelper.isOnFloorBorder(-5, 0, t, 1));
        assertTrue(ArenaShapeHelper.isOnFloorBorder(4, 0, t, 1));
        assertTrue(ArenaShapeHelper.isOnFloorBorder(0, -5, t, 1));
    }

    @Test
    void floorBorder_rectangular_centerIsNotBorder() {
        ArenaTemplate t = templateOfSize(10);
        assertFalse(ArenaShapeHelper.isOnFloorBorder(0, 0, t, 1));
    }

    @Test
    void floorBorder_circular_outerEdge() {
        ArenaTemplate t = circularTemplate(20); // radius=10
        // Inner border starts at radius - borderWidth = 10-2=8
        assertTrue(ArenaShapeHelper.isOnFloorBorder(9, 0, t, 2));
        assertFalse(ArenaShapeHelper.isOnFloorBorder(5, 0, t, 2));
    }
}
