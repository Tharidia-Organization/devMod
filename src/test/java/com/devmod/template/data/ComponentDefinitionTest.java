package com.devmod.template.data;

import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.core.Vec3i;

import com.devmod.TestBootstrap;

import static org.junit.jupiter.api.Assertions.*;

class ComponentDefinitionTest {

    @BeforeAll
    static void bootstrap() {
        TestBootstrap.init();
    }

    private ComponentDefinition makeComponent(String id, int minRoomSize, boolean requiresWall, List<String> zones) {
        return new ComponentDefinition(id, "Test Component", ComponentCategory.DISPLAY,
                new Vec3i(4, 3, 1), minRoomSize, requiresWall, zones);
    }

    @Test
    void constructorRejectsNullId() {
        assertThrows(NullPointerException.class, () ->
                new ComponentDefinition(null, "name", ComponentCategory.DISPLAY,
                        new Vec3i(1, 1, 1), 0, false, List.of()));
    }

    @Test
    void constructorRejectsNullDisplayName() {
        assertThrows(NullPointerException.class, () ->
                new ComponentDefinition("id", null, ComponentCategory.DISPLAY,
                        new Vec3i(1, 1, 1), 0, false, List.of()));
    }

    @Test
    void constructorRejectsNullCategory() {
        assertThrows(NullPointerException.class, () ->
                new ComponentDefinition("id", "name", null,
                        new Vec3i(1, 1, 1), 0, false, List.of()));
    }

    @Test
    void constructorRejectsNullBaseSize() {
        assertThrows(NullPointerException.class, () ->
                new ComponentDefinition("id", "name", ComponentCategory.DISPLAY,
                        null, 0, false, List.of()));
    }

    @Test
    void constructorRejectsNullCompatibleZones() {
        assertThrows(NullPointerException.class, () ->
                new ComponentDefinition("id", "name", ComponentCategory.DISPLAY,
                        new Vec3i(1, 1, 1), 0, false, null));
    }

    @Test
    void isCompatibleWithReturnsTrueForEmptyZoneList() {
        ComponentDefinition comp = makeComponent("test", 0, false, List.of());
        assertTrue(comp.isCompatibleWith("any_zone"));
    }

    @Test
    void isCompatibleWithReturnsTrueForWildcard() {
        ComponentDefinition comp = makeComponent("test", 0, false, List.of("*"));
        assertTrue(comp.isCompatibleWith("any_zone"));
    }

    @Test
    void isCompatibleWithReturnsTrueForMatchingZone() {
        ComponentDefinition comp = makeComponent("test", 0, false, List.of("tutorial", "spawn"));
        assertTrue(comp.isCompatibleWith("tutorial"));
        assertTrue(comp.isCompatibleWith("spawn"));
    }

    @Test
    void isCompatibleWithReturnsFalseForNonMatchingZone() {
        ComponentDefinition comp = makeComponent("test", 0, false, List.of("tutorial", "spawn"));
        assertFalse(comp.isCompatibleWith("war_arena"));
    }

    @Test
    void fitsInRoomReturnsTrueWhenRoomLargeEnough() {
        ComponentDefinition comp = makeComponent("test", 12, false, List.of());
        assertTrue(comp.fitsInRoom(20, 20));
        assertTrue(comp.fitsInRoom(12, 15));
        assertTrue(comp.fitsInRoom(15, 12));
    }

    @Test
    void fitsInRoomReturnsFalseWhenRoomTooSmall() {
        ComponentDefinition comp = makeComponent("test", 12, false, List.of());
        assertFalse(comp.fitsInRoom(11, 20));
        assertFalse(comp.fitsInRoom(20, 11));
        assertFalse(comp.fitsInRoom(5, 5));
    }

    @Test
    void fitsInRoomUsesMinimumDimension() {
        ComponentDefinition comp = makeComponent("test", 10, false, List.of());
        // min(100, 5) = 5, which < 10
        assertFalse(comp.fitsInRoom(100, 5));
    }

    @Test
    void recordAccessorsWork() {
        ComponentDefinition comp = makeComponent("screen", 12, true, List.of("tutorial"));
        assertEquals("screen", comp.id());
        assertEquals("Test Component", comp.displayName());
        assertEquals(ComponentCategory.DISPLAY, comp.category());
        assertEquals(new Vec3i(4, 3, 1), comp.baseSize());
        assertEquals(12, comp.minRoomSize());
        assertTrue(comp.requiresWall());
        assertEquals(List.of("tutorial"), comp.compatibleZones());
    }
}
