package com.devmod.arena.spawn;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for SpawnSlotConstraints.
 * DD47: SpawnSlots Distance - melee 3-15, ranged 12-30.
 */
@DisplayName("SpawnSlotConstraints Tests")
class SpawnSlotConstraintsTest {

    @Test
    @DisplayName("DD47: Melee defaults are 3-15")
    void meleeDefaultsAreCorrect() {
        SpawnSlotConstraints melee = SpawnSlotConstraints.MELEE_DEFAULTS;

        assertEquals(3.0, melee.minDistance());
        assertEquals(15.0, melee.maxDistance());
        assertTrue(melee.requireLineOfSight());
        assertTrue(melee.requireGroundCheck());
        assertTrue(melee.checkForbiddenZones());
    }

    @Test
    @DisplayName("DD47: Ranged defaults are 12-30")
    void rangedDefaultsAreCorrect() {
        SpawnSlotConstraints ranged = SpawnSlotConstraints.RANGED_DEFAULTS;

        assertEquals(12.0, ranged.minDistance());
        assertEquals(30.0, ranged.maxDistance());
        assertTrue(ranged.requireLineOfSight());
        assertTrue(ranged.requireGroundCheck());
        assertTrue(ranged.checkForbiddenZones());
    }

    @Test
    @DisplayName("Distance validation works correctly")
    void distanceValidationWorks() {
        SpawnSlotConstraints constraints = SpawnSlotConstraints.MELEE_DEFAULTS;

        // Too close
        assertFalse(constraints.isDistanceValid(2.0));

        // In range
        assertTrue(constraints.isDistanceValid(3.0));
        assertTrue(constraints.isDistanceValid(10.0));
        assertTrue(constraints.isDistanceValid(15.0));

        // Too far
        assertFalse(constraints.isDistanceValid(16.0));
    }

    @Test
    @DisplayName("Invalid constraints throw exception")
    void invalidConstraintsThrow() {
        // Negative min distance
        assertThrows(IllegalArgumentException.class, () ->
            new SpawnSlotConstraints(-1, 10, true, true, true));

        // Min > Max
        assertThrows(IllegalArgumentException.class, () ->
            new SpawnSlotConstraints(15, 10, true, true, true));
    }

    @Test
    @DisplayName("Combat type factory works")
    void combatTypeFactoryWorks() {
        assertEquals(SpawnSlotConstraints.MELEE_DEFAULTS,
            SpawnSlotConstraints.forCombatType(SpawnSlotConstraints.CombatType.MELEE));

        assertEquals(SpawnSlotConstraints.RANGED_DEFAULTS,
            SpawnSlotConstraints.forCombatType(SpawnSlotConstraints.CombatType.RANGED));

        SpawnSlotConstraints mixed = SpawnSlotConstraints.forCombatType(SpawnSlotConstraints.CombatType.MIXED);
        assertTrue(mixed.minDistance() > SpawnSlotConstraints.MELEE_DEFAULTS.minDistance());
        assertTrue(mixed.maxDistance() < SpawnSlotConstraints.RANGED_DEFAULTS.maxDistance());
    }

    @Test
    @DisplayName("Builder pattern works")
    void builderPatternWorks() {
        SpawnSlotConstraints custom = SpawnSlotConstraints.builder()
            .minDistance(5.0)
            .maxDistance(20.0)
            .requireLineOfSight(false)
            .requireGroundCheck(true)
            .checkForbiddenZones(true)
            .build();

        assertEquals(5.0, custom.minDistance());
        assertEquals(20.0, custom.maxDistance());
        assertFalse(custom.requireLineOfSight());
        assertTrue(custom.requireGroundCheck());
    }

    @Test
    @DisplayName("With methods create new instances")
    void withMethodsCreateNewInstances() {
        SpawnSlotConstraints original = SpawnSlotConstraints.MELEE_DEFAULTS;
        SpawnSlotConstraints modified = original.withMinDistance(5.0);

        assertNotSame(original, modified);
        assertEquals(3.0, original.minDistance());
        assertEquals(5.0, modified.minDistance());
        assertEquals(original.maxDistance(), modified.maxDistance());
    }

    @Test
    @DisplayName("Team spawn has relaxed constraints")
    void teamSpawnHasRelaxedConstraints() {
        SpawnSlotConstraints team = SpawnSlotConstraints.TEAM_SPAWN;

        assertTrue(team.minDistance() < SpawnSlotConstraints.MELEE_DEFAULTS.minDistance());
        assertFalse(team.requireLineOfSight()); // Teams don't need LOS
    }

    @Test
    @DisplayName("Spectator has minimal constraints")
    void spectatorHasMinimalConstraints() {
        SpawnSlotConstraints spectator = SpawnSlotConstraints.SPECTATOR;

        assertFalse(spectator.requireLineOfSight());
        assertFalse(spectator.requireGroundCheck());
        assertFalse(spectator.checkForbiddenZones());
    }
}
