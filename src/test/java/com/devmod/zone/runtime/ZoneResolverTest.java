package com.devmod.zone.runtime;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;

import com.devmod.TestBootstrap;
import com.devmod.zone.data.ZoneBounds;
import com.devmod.zone.data.ZoneDefinition;

/**
 * Unit tests for ZoneResolver: priority-based resolution from collections,
 * overlapping zone handling, and edge cases.
 *
 * Uses resolveFromCollection() to test core resolution without MinecraftServer.
 */
@DisplayName("ZoneResolver")
class ZoneResolverTest {

    @BeforeAll
    static void setup() {
        TestBootstrap.init();
    }

    private static final ZoneResolver resolver = ZoneResolver.INSTANCE;

    // ========================================================================
    // Helper
    // ========================================================================

    private static ZoneDefinition zone(String id, int priority, ZoneBounds bounds) {
        return ZoneDefinition.builder(id)
            .bounds(bounds)
            .cueSound(SoundEvents.NOTE_BLOCK_CHIME.value())
            .hintMessage("")
            .priority(priority)
            .build();
    }

    private static ZoneDefinition zone(String id, int priority, int minX, int maxX, int minZ, int maxZ) {
        return zone(id, priority, new ZoneBounds(minX, maxX, 0, 100, minZ, maxZ));
    }

    // ========================================================================
    // Basic Resolution
    // ========================================================================

    @Nested
    @DisplayName("resolveFromCollection()")
    class ResolveTests {

        @Test
        @DisplayName("resolves single zone containing position")
        void singleZone() {
            ZoneDefinition hub = zone("hub", 0, 0, 100, 0, 100);
            Optional<ZoneDefinition> result = resolver.resolveFromCollection(
                List.of(hub), new BlockPos(50, 50, 50));

            assertTrue(result.isPresent());
            assertEquals("hub", result.get().zoneId());
        }

        @Test
        @DisplayName("returns empty when no zone contains position")
        void noContainingZone() {
            ZoneDefinition hub = zone("hub", 0, 0, 100, 0, 100);
            Optional<ZoneDefinition> result = resolver.resolveFromCollection(
                List.of(hub), new BlockPos(500, 50, 500));

            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("returns empty for empty zone collection")
        void emptyCollection() {
            Optional<ZoneDefinition> result = resolver.resolveFromCollection(
                List.of(), new BlockPos(50, 50, 50));

            assertTrue(result.isEmpty());
        }
    }

    // ========================================================================
    // Priority Resolution
    // ========================================================================

    @Nested
    @DisplayName("Priority Resolution")
    class PriorityTests {

        @Test
        @DisplayName("higher priority wins when zones overlap")
        void higherPriorityWins() {
            ZoneDefinition hub = zone("hub", 0, 0, 100, 0, 100);
            ZoneDefinition combat = zone("combat", 10, 0, 50, 0, 50);

            Optional<ZoneDefinition> result = resolver.resolveFromCollection(
                List.of(hub, combat), new BlockPos(25, 50, 25));

            assertTrue(result.isPresent());
            assertEquals("combat", result.get().zoneId());
        }

        @Test
        @DisplayName("lower priority zone resolves when outside high priority")
        void fallbackToLowerPriority() {
            ZoneDefinition hub = zone("hub", 0, 0, 100, 0, 100);
            ZoneDefinition combat = zone("combat", 10, 0, 50, 0, 50);

            Optional<ZoneDefinition> result = resolver.resolveFromCollection(
                List.of(hub, combat), new BlockPos(75, 50, 75));

            assertTrue(result.isPresent());
            assertEquals("hub", result.get().zoneId());
        }

        @Test
        @DisplayName("three-level nesting: highest priority wins")
        void threeLevelNesting() {
            ZoneDefinition hub = zone("hub", 0, 0, 200, 0, 200);
            ZoneDefinition sandbox = zone("sandbox", 10, 0, 100, 0, 100);
            ZoneDefinition performance = zone("performance", 20, 0, 50, 0, 50);

            Optional<ZoneDefinition> result = resolver.resolveFromCollection(
                List.of(hub, sandbox, performance), new BlockPos(25, 50, 25));

            assertTrue(result.isPresent());
            assertEquals("performance", result.get().zoneId());
        }

        @Test
        @DisplayName("equal priority zones: one is returned (deterministic via max)")
        void equalPriority() {
            ZoneDefinition zoneA = zone("zone_a", 10, 0, 100, 0, 100);
            ZoneDefinition zoneB = zone("zone_b", 10, 50, 150, 0, 100);

            // Position in overlap region
            Optional<ZoneDefinition> result = resolver.resolveFromCollection(
                List.of(zoneA, zoneB), new BlockPos(75, 50, 50));

            // Both have equal priority, max() will pick one deterministically
            assertTrue(result.isPresent());
        }

        @Test
        @DisplayName("resolution order is independent of list order")
        void orderIndependent() {
            ZoneDefinition hub = zone("hub", 0, 0, 100, 0, 100);
            ZoneDefinition combat = zone("combat", 10, 0, 50, 0, 50);

            // Position inside both
            BlockPos pos = new BlockPos(25, 50, 25);

            Optional<ZoneDefinition> result1 = resolver.resolveFromCollection(
                List.of(hub, combat), pos);
            Optional<ZoneDefinition> result2 = resolver.resolveFromCollection(
                List.of(combat, hub), pos);

            assertEquals(result1.get().zoneId(), result2.get().zoneId());
            assertEquals("combat", result1.get().zoneId());
        }

        @Test
        @DisplayName("negative priority is valid and loses to zero")
        void negativePriority() {
            ZoneDefinition low = zone("low", -5, 0, 100, 0, 100);
            ZoneDefinition normal = zone("normal", 0, 0, 100, 0, 100);

            Optional<ZoneDefinition> result = resolver.resolveFromCollection(
                List.of(low, normal), new BlockPos(50, 50, 50));

            assertTrue(result.isPresent());
            assertEquals("normal", result.get().zoneId());
        }
    }

    // ========================================================================
    // Boundary/Edge Cases
    // ========================================================================

    @Nested
    @DisplayName("Boundary Cases")
    class BoundaryTests {

        @Test
        @DisplayName("resolves at exact boundary of zone")
        void exactBoundary() {
            ZoneDefinition zone = zone("hub", 0, 0, 10, 0, 10);

            assertTrue(resolver.resolveFromCollection(
                List.of(zone), new BlockPos(0, 0, 0)).isPresent());
            assertTrue(resolver.resolveFromCollection(
                List.of(zone), new BlockPos(10, 100, 10)).isPresent());
        }

        @Test
        @DisplayName("one block outside boundary returns empty")
        void justOutside() {
            ZoneDefinition zone = zone("hub", 0, 0, 10, 0, 10);

            assertTrue(resolver.resolveFromCollection(
                List.of(zone), new BlockPos(11, 50, 5)).isEmpty());
            assertTrue(resolver.resolveFromCollection(
                List.of(zone), new BlockPos(-1, 50, 5)).isEmpty());
        }

        @Test
        @DisplayName("single-block zone resolves correctly")
        void singleBlockZone() {
            ZoneDefinition tiny = zone("tiny", 0, new ZoneBounds(5, 5, 10, 10, 15, 15));

            assertTrue(resolver.resolveFromCollection(
                List.of(tiny), new BlockPos(5, 10, 15)).isPresent());
            assertTrue(resolver.resolveFromCollection(
                List.of(tiny), new BlockPos(4, 10, 15)).isEmpty());
        }

        @Test
        @DisplayName("adjacent zones with no overlap resolve independently")
        void adjacentZones() {
            ZoneDefinition left = zone("left", 10, 0, 10, 0, 100);
            ZoneDefinition right = zone("right", 10, 12, 22, 0, 100);

            Optional<ZoneDefinition> leftResult = resolver.resolveFromCollection(
                List.of(left, right), new BlockPos(5, 50, 50));
            Optional<ZoneDefinition> gapResult = resolver.resolveFromCollection(
                List.of(left, right), new BlockPos(11, 50, 50));
            Optional<ZoneDefinition> rightResult = resolver.resolveFromCollection(
                List.of(left, right), new BlockPos(15, 50, 50));

            assertEquals("left", leftResult.get().zoneId());
            assertTrue(gapResult.isEmpty());
            assertEquals("right", rightResult.get().zoneId());
        }
    }

    // ========================================================================
    // Round-Robin Reset
    // ========================================================================

    @Nested
    @DisplayName("resetRoundRobin()")
    class RoundRobinTests {

        @Test
        @DisplayName("resetRoundRobin does not throw")
        void resetDoesNotThrow() {
            assertDoesNotThrow(() -> resolver.resetRoundRobin());
        }
    }
}
