package com.devmod.arena.zone;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ZoneTransitionTest {

    @Test
    void hardTransition() {
        assertEquals(ZoneTransition.TransitionType.HARD, ZoneTransition.HARD.type());
        assertEquals(0, ZoneTransition.HARD.width());
        assertFalse(ZoneTransition.HARD.blendMaterials());
        assertFalse(ZoneTransition.HARD.blendLighting());
    }

    @Test
    void gradientPresets() {
        assertEquals(3, ZoneTransition.GRADIENT_SMALL.width());
        assertEquals(5, ZoneTransition.GRADIENT_MEDIUM.width());
        assertEquals(8, ZoneTransition.GRADIENT_LARGE.width());

        assertTrue(ZoneTransition.GRADIENT_SMALL.blendMaterials());
        assertTrue(ZoneTransition.GRADIENT_SMALL.blendLighting());
    }

    @Test
    void wallTransition() {
        assertEquals(ZoneTransition.TransitionType.WALL, ZoneTransition.WALL.type());
        assertEquals(1, ZoneTransition.WALL.width());
    }

    @Test
    void customGradient() {
        ZoneTransition custom = ZoneTransition.gradient(10);
        assertEquals(ZoneTransition.TransitionType.GRADIENT, custom.type());
        assertEquals(10, custom.width());
        assertTrue(custom.blendMaterials());
    }

    @Test
    void customWall() {
        ZoneTransition custom = ZoneTransition.wall(3);
        assertEquals(ZoneTransition.TransitionType.WALL, custom.type());
        assertEquals(3, custom.width());
        assertFalse(custom.blendMaterials());
    }

    @Test
    void requiresSpace() {
        assertFalse(ZoneTransition.HARD.requiresSpace());
        assertTrue(ZoneTransition.GRADIENT_SMALL.requiresSpace());
        assertTrue(ZoneTransition.WALL.requiresSpace());
    }

    @Test
    void requiredSpace() {
        assertEquals(0, ZoneTransition.HARD.getRequiredSpace());
        assertEquals(3, ZoneTransition.GRADIENT_SMALL.getRequiredSpace());
        assertEquals(5, ZoneTransition.GRADIENT_MEDIUM.getRequiredSpace());
        assertEquals(1, ZoneTransition.WALL.getRequiredSpace());
    }

    @Test
    void transitionTypeValues() {
        assertEquals(4, ZoneTransition.TransitionType.values().length);
    }

    @Test
    void gapRequiresSpace() {
        ZoneTransition gap = new ZoneTransition(ZoneTransition.TransitionType.GAP, 4, false, false);
        assertTrue(gap.requiresSpace());
        assertEquals(4, gap.getRequiredSpace());
    }
}
