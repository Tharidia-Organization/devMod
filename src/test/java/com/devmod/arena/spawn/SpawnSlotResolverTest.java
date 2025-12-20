package com.devmod.arena.spawn;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for SpawnSlotResolver.
 * DD47: LOS check, forbidden zone rejection.
 */
@DisplayName("SpawnSlotResolver Tests")
class SpawnSlotResolverTest {

    private List<SpawnSlot> testSlots;

    @BeforeEach
    void setUp() {
        // Create a grid of spawn slots
        testSlots = Arrays.asList(
            SpawnSlot.at(0, 64, 0),
            SpawnSlot.at(10, 64, 0),   // 10 blocks apart
            SpawnSlot.at(20, 64, 0),   // 20 blocks apart
            SpawnSlot.at(0, 64, 10),   // 10 blocks apart
            SpawnSlot.at(10, 64, 10),  // Diagonal from first
            SpawnSlot.at(100, 64, 0),  // Far away
            SpawnSlot.at(2, 64, 0)     // Too close to first (2 blocks)
        );
    }

    @Test
    @DisplayName("Resolves correct number of slots")
    void resolvesCorrectNumberOfSlots() {
        SpawnSlotResolver resolver = new SpawnSlotResolver(testSlots, SpawnSlotConstraints.MELEE_DEFAULTS);

        List<SpawnSlot> resolved = resolver.resolveSlots(2);

        assertEquals(2, resolved.size());
    }

    @Test
    @DisplayName("DD47: Respects distance constraints")
    void respectsDistanceConstraints() {
        SpawnSlotConstraints constraints = SpawnSlotConstraints.MELEE_DEFAULTS; // 3-15
        SpawnSlotResolver resolver = new SpawnSlotResolver(testSlots, constraints);

        List<SpawnSlot> resolved = resolver.resolveSlots(2);

        // All pairs should be within distance constraints
        for (int i = 0; i < resolved.size(); i++) {
            for (int j = i + 1; j < resolved.size(); j++) {
                double distance = resolved.get(i).distanceTo(resolved.get(j));
                assertTrue(constraints.isDistanceValid(distance),
                    "Distance " + distance + " should be valid");
            }
        }
    }

    @Test
    @DisplayName("DD47: Forbidden zone rejection")
    void forbiddenZoneRejection() {
        ForbiddenZone zone = ForbiddenZone.box(0, 60, 0, 5, 70, 5);
        SpawnSlotResolver resolver = new SpawnSlotResolver(
            testSlots,
            SpawnSlotConstraints.MELEE_DEFAULTS,
            Collections.singletonList(zone)
        );

        List<SpawnSlot> resolved = resolver.resolveSlots(4);

        // No resolved slots should be in forbidden zone
        for (SpawnSlot slot : resolved) {
            assertFalse(zone.contains(slot), "Slot should not be in forbidden zone");
        }
    }

    @Test
    @DisplayName("DD47: LOS check is called")
    void losCheckIsCalled() {
        SpawnSlotResolver resolver = new SpawnSlotResolver(testSlots, SpawnSlotConstraints.MELEE_DEFAULTS);

        // Set custom LOS checker that blocks everything
        resolver.setLineOfSightChecker((from, to) -> false);

        // With LOS blocked, should only get 1 slot
        List<SpawnSlot> resolved = resolver.resolveSlots(2);

        // Should not be able to find valid second slot with LOS blocked
        assertTrue(resolved.size() <= 1);
    }

    @Test
    @DisplayName("hasLineOfSight returns true by default")
    void hasLineOfSightDefaultTrue() {
        SpawnSlotResolver resolver = new SpawnSlotResolver(testSlots, SpawnSlotConstraints.MELEE_DEFAULTS);

        SpawnSlot a = testSlots.get(0);
        SpawnSlot b = testSlots.get(1);

        // Default implementation should generally return true for level ground
        assertTrue(resolver.hasLineOfSight(a, b));
    }

    @Test
    @DisplayName("Validates slot assignment correctly")
    void validatesSlotAssignmentCorrectly() {
        SpawnSlotResolver resolver = new SpawnSlotResolver(testSlots, SpawnSlotConstraints.MELEE_DEFAULTS);

        SpawnSlot validSlot = SpawnSlot.at(10, 64, 0);
        SpawnSlot existing = SpawnSlot.at(0, 64, 0);

        assertTrue(resolver.validateSlotAssignment(validSlot, Collections.singletonList(existing)));

        // Too close
        SpawnSlot tooClose = SpawnSlot.at(2, 64, 0);
        assertFalse(resolver.validateSlotAssignment(tooClose, Collections.singletonList(existing)));
    }

    @Test
    @DisplayName("Empty input returns empty result")
    void emptyInputReturnsEmpty() {
        SpawnSlotResolver resolver = new SpawnSlotResolver(Collections.emptyList());

        List<SpawnSlot> resolved = resolver.resolveSlots(2);

        assertTrue(resolved.isEmpty());
    }

    @Test
    @DisplayName("Zero count returns empty result")
    void zeroCountReturnsEmpty() {
        SpawnSlotResolver resolver = new SpawnSlotResolver(testSlots);

        List<SpawnSlot> resolved = resolver.resolveSlots(0);

        assertTrue(resolved.isEmpty());
    }

    @Test
    @DisplayName("Get valid slots filters correctly")
    void getValidSlotsFiltersCorrectly() {
        ForbiddenZone zone = ForbiddenZone.box(0, 60, 0, 5, 70, 5);
        SpawnSlotResolver resolver = new SpawnSlotResolver(
            testSlots,
            SpawnSlotConstraints.MELEE_DEFAULTS,
            Collections.singletonList(zone)
        );

        List<SpawnSlot> validSlots = resolver.getValidSlots();

        // Original slot at (0,64,0) and (2,64,0) should be filtered out
        assertTrue(validSlots.size() < testSlots.size());
    }

    @Test
    @DisplayName("Add/remove forbidden zones works")
    void addRemoveForbiddenZonesWorks() {
        SpawnSlotResolver resolver = new SpawnSlotResolver(testSlots);
        ForbiddenZone zone = ForbiddenZone.box(0, 60, 0, 5, 70, 5);

        assertTrue(resolver.getForbiddenZones().isEmpty());

        resolver.addForbiddenZone(zone);
        assertEquals(1, resolver.getForbiddenZones().size());

        resolver.removeForbiddenZone(zone);
        assertTrue(resolver.getForbiddenZones().isEmpty());
    }

    @Test
    @DisplayName("Avoids occupied slots")
    void avoidsOccupiedSlots() {
        SpawnSlotResolver resolver = new SpawnSlotResolver(testSlots, SpawnSlotConstraints.MELEE_DEFAULTS);

        SpawnSlot occupied = testSlots.get(0);
        List<SpawnSlot> resolved = resolver.resolveSlots(2, Collections.singletonList(occupied));

        assertFalse(resolved.contains(occupied));
    }
}
