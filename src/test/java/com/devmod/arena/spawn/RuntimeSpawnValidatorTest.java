package com.devmod.arena.spawn;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for RuntimeSpawnValidator.
 * DD48: O(n^2) at load, O(1) runtime.
 */
@DisplayName("RuntimeSpawnValidator Tests")
class RuntimeSpawnValidatorTest {

    private List<SpawnSlot> testSlots;
    private SpawnSlotResolver resolver;
    private RuntimeSpawnValidator validator;

    @BeforeEach
    void setUp() {
        testSlots = Arrays.asList(
            SpawnSlot.at(0, 64, 0),
            SpawnSlot.at(10, 64, 0),
            SpawnSlot.at(20, 64, 0),
            SpawnSlot.at(0, 64, 10),
            SpawnSlot.at(10, 64, 10)
        );

        resolver = new SpawnSlotResolver(testSlots, SpawnSlotConstraints.MELEE_DEFAULTS);
        resolver.setGroundChecker(slot -> true);
        validator = new RuntimeSpawnValidator(resolver);
    }

    @Test
    @DisplayName("DD48: O(1) runtime lookup after cache build")
    void o1RuntimeLookup() {
        validator.buildCache();

        // Measure lookup time - should be very fast (O(1))
        SpawnSlot slotA = testSlots.get(0);
        SpawnSlot slotB = testSlots.get(1);

        long start = System.nanoTime();
        for (int i = 0; i < 10000; i++) {
            validator.isSlotValid(slotA);
            validator.isSlotPairCompatible(slotA, slotB);
        }
        long elapsed = System.nanoTime() - start;

        // 10000 lookups should be very fast (under 10ms for O(1))
        assertTrue(elapsed < 10_000_000, "Lookups should be O(1), took " + (elapsed / 1_000_000) + "ms");
    }

    @Test
    @DisplayName("Cache is built correctly")
    void cacheIsBuiltCorrectly() {
        assertFalse(validator.isCacheBuilt());

        validator.buildCache();

        assertTrue(validator.isCacheBuilt());

        RuntimeSpawnValidator.CacheStats stats = validator.getCacheStats();
        assertTrue(stats.slotCount() > 0);
        assertTrue(stats.pairCount() > 0);
        assertTrue(stats.built());
    }

    @Test
    @DisplayName("Slot validity is cached correctly")
    void slotValidityIsCached() {
        validator.buildCache();

        SpawnSlot validSlot = testSlots.get(1); // (10, 64, 0) - not in any forbidden zone
        assertTrue(validator.isSlotValid(validSlot));
    }

    @Test
    @DisplayName("Slot pair compatibility is cached correctly")
    void slotPairCompatibilityIsCached() {
        validator.buildCache();

        SpawnSlot slotA = testSlots.get(0); // (0, 64, 0)
        SpawnSlot slotB = testSlots.get(1); // (10, 64, 0) - 10 blocks apart

        // Should be compatible (within 3-15 range for melee)
        assertTrue(validator.isSlotPairCompatible(slotA, slotB));
    }

    @Test
    @DisplayName("DD48: Position occupied check is lightweight")
    void positionOccupiedCheckIsLightweight() {
        SpawnSlot slot = testSlots.get(0);

        assertFalse(validator.isPositionOccupied(slot));

        validator.markOccupied(slot);
        assertTrue(validator.isPositionOccupied(slot));

        validator.markUnoccupied(slot);
        assertFalse(validator.isPositionOccupied(slot));
    }

    @Test
    @DisplayName("isPositionOccupied works with UUID")
    void isPositionOccupiedWorksWithUuid() {
        SpawnSlot slot = testSlots.get(0);
        UUID slotId = slot.id();

        assertFalse(validator.isPositionOccupied(slotId));

        validator.markOccupied(slot);
        assertTrue(validator.isPositionOccupied(slotId));
    }

    @Test
    @DisplayName("Clear occupied works")
    void clearOccupiedWorks() {
        validator.markOccupied(testSlots.get(0));
        validator.markOccupied(testSlots.get(1));

        assertEquals(2, validator.getOccupiedSlots().size());

        validator.clearOccupied();

        assertTrue(validator.getOccupiedSlots().isEmpty());
    }

    @Test
    @DisplayName("Find compatible slots works")
    void findCompatibleSlotsWorks() {
        validator.buildCache();

        SpawnSlot assigned = testSlots.get(0);
        List<SpawnSlot> compatible = validator.findCompatibleSlots(Collections.singletonList(assigned));

        // All compatible slots should be within constraints of the assigned slot
        for (SpawnSlot slot : compatible) {
            assertTrue(validator.isSlotPairCompatible(slot, assigned));
        }
    }

    @Test
    @DisplayName("Validate assignment works")
    void validateAssignmentWorks() {
        validator.buildCache();

        // Valid assignment - slots are within distance constraints
        List<SpawnSlot> validAssignment = Arrays.asList(testSlots.get(0), testSlots.get(1));
        RuntimeSpawnValidator.ValidationResult result = validator.validateAssignment(validAssignment);

        assertTrue(result.isValid());
        assertTrue(result.errors().isEmpty());
    }

    @Test
    @DisplayName("Validate assignment detects incompatible pairs")
    void validateAssignmentDetectsIncompatible() {
        // Create slots that are too far apart for melee
        List<SpawnSlot> farSlots = Arrays.asList(
            SpawnSlot.at(0, 64, 0),
            SpawnSlot.at(100, 64, 0) // 100 blocks apart - too far for melee (max 15)
        );

        SpawnSlotResolver farResolver = new SpawnSlotResolver(farSlots, SpawnSlotConstraints.MELEE_DEFAULTS);
        farResolver.setGroundChecker(slot -> true);
        RuntimeSpawnValidator farValidator = new RuntimeSpawnValidator(farResolver);
        farValidator.buildCache();

        RuntimeSpawnValidator.ValidationResult result = farValidator.validateAssignment(farSlots);

        assertFalse(result.isValid());
        assertFalse(result.errors().isEmpty());
    }

    @Test
    @DisplayName("Cache can be invalidated")
    void cacheCanBeInvalidated() {
        validator.buildCache();
        assertTrue(validator.isCacheBuilt());

        validator.invalidateCache();

        assertFalse(validator.isCacheBuilt());
    }

    @Test
    @DisplayName("Fallback to direct check when cache not built")
    void fallbackToDirectCheckWhenNoCacheBuilt() {
        // Don't build cache
        SpawnSlot slot = testSlots.get(0);

        // Should still work (with warning logged)
        boolean valid = validator.isSlotValid(slot);
        assertTrue(valid); // Slot should be valid
    }
}
