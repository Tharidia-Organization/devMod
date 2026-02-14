package com.devmod.zone.data;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;

import com.devmod.TestBootstrap;

/**
 * Unit tests for ZoneDefinition: builder, validation, containment,
 * and spawn/portal position calculation.
 */
@DisplayName("ZoneDefinition")
class ZoneDefinitionTest {

    @BeforeAll
    static void setup() {
        TestBootstrap.init();
    }

    // ========================================================================
    // Helper
    // ========================================================================

    private static ZoneDefinition.Builder defaultBuilder(String zoneId) {
        return ZoneDefinition.builder(zoneId)
            .bounds(new ZoneBounds(0, 100, 0, 100, 0, 100))
            .cueSound(SoundEvents.NOTE_BLOCK_CHIME.value())
            .hintMessage("Test zone");
    }

    // ========================================================================
    // Builder Tests
    // ========================================================================

    @Nested
    @DisplayName("Builder")
    class BuilderTests {

        @Test
        @DisplayName("builds valid definition with defaults")
        void defaultBuild() {
            ZoneDefinition zone = defaultBuilder("test_zone").build();

            assertNotNull(zone.id());
            assertEquals("test_zone", zone.zoneId());
            assertEquals("test_zone", zone.displayName()); // defaults to zoneId
            assertFalse(zone.allowBuilding());
            assertFalse(zone.isSpecial());
            assertTrue(zone.hasTeleport());
            assertEquals(0, zone.priority());
            assertNull(zone.spawnOffset());
            assertNull(zone.portalOffset());
            assertNull(zone.portalColor());
        }

        @Test
        @DisplayName("sets all fields correctly")
        void setsAllFields() {
            UUID id = UUID.randomUUID();
            BlockPos spawn = new BlockPos(10, 0, 10);

            ZoneDefinition zone = defaultBuilder("combat")
                .id(id)
                .displayName("Combat Zone")
                .spawnOffset(spawn)
                .color(0xFF4444)
                .allowBuilding(true)
                .isSpecial(true)
                .hasTeleport(false)
                .priority(10)
                .build();

            assertEquals(id, zone.id());
            assertEquals("combat", zone.zoneId());
            assertEquals("Combat Zone", zone.displayName());
            assertEquals(spawn, zone.spawnOffset());
            assertEquals(0xFF4444, zone.color());
            assertTrue(zone.allowBuilding());
            assertTrue(zone.isSpecial());
            assertFalse(zone.hasTeleport());
            assertEquals(10, zone.priority());
        }

        @Test
        @DisplayName("build fails without bounds")
        void failsWithoutBounds() {
            assertThrows(NullPointerException.class, () ->
                ZoneDefinition.builder("test").build());
        }

        @Test
        @DisplayName("generates unique UUIDs")
        void uniqueIds() {
            ZoneDefinition a = defaultBuilder("zone_a").build();
            ZoneDefinition b = defaultBuilder("zone_b").build();
            assertNotEquals(a.id(), b.id());
        }

        @Test
        @DisplayName("toBuilder preserves all fields")
        void toBuilderPreserves() {
            ZoneDefinition original = defaultBuilder("combat")
                .displayName("Combat Test")
                .priority(10)
                .allowBuilding(true)
                .build();

            ZoneDefinition copy = original.toBuilder().build();

            assertEquals(original.id(), copy.id());
            assertEquals(original.zoneId(), copy.zoneId());
            assertEquals(original.displayName(), copy.displayName());
            assertEquals(original.bounds(), copy.bounds());
            assertEquals(original.priority(), copy.priority());
            assertEquals(original.allowBuilding(), copy.allowBuilding());
        }

        @Test
        @DisplayName("toBuilder allows overriding fields")
        void toBuilderOverride() {
            ZoneDefinition original = defaultBuilder("combat")
                .priority(10)
                .build();

            ZoneDefinition modified = original.toBuilder()
                .priority(20)
                .displayName("Updated Name")
                .build();

            assertEquals(original.id(), modified.id()); // preserved
            assertEquals(20, modified.priority());
            assertEquals("Updated Name", modified.displayName());
        }
    }

    // ========================================================================
    // Validation
    // ========================================================================

    @Nested
    @DisplayName("Validation")
    class ValidationTests {

        @Test
        @DisplayName("rejects null id")
        void nullId() {
            assertThrows(NullPointerException.class, () ->
                new ZoneDefinition(null, "test", "Test", new ZoneBounds(0, 10, 0, 10, 0, 10),
                    null, 0, SoundEvents.NOTE_BLOCK_CHIME.value(), "",
                    false, false, true, null, null, 0, 0L, 0L));
        }

        @Test
        @DisplayName("rejects null zoneId")
        void nullZoneId() {
            assertThrows(NullPointerException.class, () ->
                new ZoneDefinition(UUID.randomUUID(), null, "Test", new ZoneBounds(0, 10, 0, 10, 0, 10),
                    null, 0, SoundEvents.NOTE_BLOCK_CHIME.value(), "",
                    false, false, true, null, null, 0, 0L, 0L));
        }

        @Test
        @DisplayName("rejects invalid zoneId format")
        void invalidZoneIdFormat() {
            assertThrows(IllegalArgumentException.class, () ->
                new ZoneDefinition(UUID.randomUUID(), "INVALID-ID", "Test",
                    new ZoneBounds(0, 10, 0, 10, 0, 10),
                    null, 0, SoundEvents.NOTE_BLOCK_CHIME.value(), "",
                    false, false, true, null, null, 0, 0L, 0L));
        }

        @Test
        @DisplayName("rejects reserved zoneId")
        void reservedZoneId() {
            assertThrows(IllegalArgumentException.class, () ->
                defaultBuilder("outside").build());
        }

        @Test
        @DisplayName("rejects null bounds")
        void nullBounds() {
            assertThrows(NullPointerException.class, () ->
                new ZoneDefinition(UUID.randomUUID(), "test", "Test", null,
                    null, 0, SoundEvents.NOTE_BLOCK_CHIME.value(), "",
                    false, false, true, null, null, 0, 0L, 0L));
        }
    }

    // ========================================================================
    // Containment
    // ========================================================================

    @Nested
    @DisplayName("containsPosition()")
    class ContainsPositionTests {

        @Test
        @DisplayName("delegates to bounds.contains()")
        void delegatesToBounds() {
            ZoneDefinition zone = defaultBuilder("test_zone").build();

            assertTrue(zone.containsPosition(new BlockPos(50, 50, 50)));
            assertFalse(zone.containsPosition(new BlockPos(200, 200, 200)));
        }

        @Test
        @DisplayName("boundary positions are contained")
        void boundaryContained() {
            ZoneDefinition zone = defaultBuilder("test_zone")
                .bounds(new ZoneBounds(0, 10, 0, 10, 0, 10))
                .build();

            assertTrue(zone.containsPosition(new BlockPos(0, 0, 0)));
            assertTrue(zone.containsPosition(new BlockPos(10, 10, 10)));
        }
    }

    // ========================================================================
    // Spawn Position
    // ========================================================================

    @Nested
    @DisplayName("getAbsoluteSpawn()")
    class SpawnTests {

        @Test
        @DisplayName("returns null when no spawn offset")
        void noSpawnOffset() {
            ZoneDefinition zone = defaultBuilder("test_zone")
                .spawnOffset(null)
                .build();

            assertNull(zone.getAbsoluteSpawn(BlockPos.ZERO));
        }

        @Test
        @DisplayName("applies hub origin to spawn offset")
        void appliesOrigin() {
            BlockPos spawnOffset = new BlockPos(10, 0, -5);
            ZoneDefinition zone = defaultBuilder("test_zone")
                .spawnOffset(spawnOffset)
                .build();

            BlockPos hubOrigin = new BlockPos(100, 64, 200);
            BlockPos absolute = zone.getAbsoluteSpawn(hubOrigin);

            assertNotNull(absolute);
            assertEquals(110, absolute.getX());
            assertEquals(64, absolute.getY());
            assertEquals(195, absolute.getZ());
        }
    }

    // ========================================================================
    // Portal Position
    // ========================================================================

    @Nested
    @DisplayName("getAbsolutePortal()")
    class PortalTests {

        @Test
        @DisplayName("returns null when no portal offset")
        void noPortalOffset() {
            ZoneDefinition zone = defaultBuilder("test_zone")
                .portalOffset(null)
                .build();

            assertNull(zone.getAbsolutePortal(BlockPos.ZERO));
        }

        @Test
        @DisplayName("applies hub origin to portal offset")
        void appliesOrigin() {
            BlockPos portalOffset = new BlockPos(-12, 1, -12);
            ZoneDefinition zone = defaultBuilder("test_zone")
                .portalOffset(portalOffset)
                .build();

            BlockPos hubOrigin = new BlockPos(100, 64, 200);
            BlockPos absolute = zone.getAbsolutePortal(hubOrigin);

            assertNotNull(absolute);
            assertEquals(88, absolute.getX());
            assertEquals(65, absolute.getY());
            assertEquals(188, absolute.getZ());
        }
    }
}
