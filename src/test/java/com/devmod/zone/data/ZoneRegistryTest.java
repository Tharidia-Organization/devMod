package com.devmod.zone.data;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;

import com.devmod.TestBootstrap;

/**
 * Unit tests for ZoneRegistry: CRUD operations, aliases, markers,
 * zone resolution, and area linking.
 *
 * Uses direct ZoneRegistry instances (not via MinecraftServer) to test core logic.
 */
@DisplayName("ZoneRegistry")
class ZoneRegistryTest {

    @BeforeAll
    static void setup() {
        TestBootstrap.init();
    }

    private ZoneRegistry registry;

    @BeforeEach
    void createRegistry() {
        registry = new ZoneRegistry();
    }

    // ========================================================================
    // Helper
    // ========================================================================

    private static ZoneDefinition createZone(String zoneId, int priority) {
        return createZone(zoneId, priority, new ZoneBounds(0, 100, 0, 100, 0, 100));
    }

    private static ZoneDefinition createZone(String zoneId, int priority, ZoneBounds bounds) {
        return ZoneDefinition.builder(zoneId)
            .bounds(bounds)
            .cueSound(SoundEvents.NOTE_BLOCK_CHIME.value())
            .hintMessage("")
            .priority(priority)
            .build();
    }

    // ========================================================================
    // Register (Create)
    // ========================================================================

    @Nested
    @DisplayName("registerZone()")
    class RegisterTests {

        @Test
        @DisplayName("registers zone and returns UUID")
        void registersSuccessfully() {
            ZoneDefinition zone = createZone("hub", 0);
            UUID id = registry.registerZone(zone);

            assertNotNull(id);
            assertEquals(zone.id(), id);
            assertEquals(1, registry.getZoneCount());
        }

        @Test
        @DisplayName("registered zone is retrievable by UUID")
        void retrievableByUuid() {
            ZoneDefinition zone = createZone("hub", 0);
            UUID id = registry.registerZone(zone);

            Optional<ZoneDefinition> found = registry.getZone(id);
            assertTrue(found.isPresent());
            assertEquals("hub", found.get().zoneId());
        }

        @Test
        @DisplayName("registered zone is retrievable by string ID")
        void retrievableByStringId() {
            ZoneDefinition zone = createZone("hub", 0);
            registry.registerZone(zone);

            Optional<ZoneDefinition> found = registry.getZoneById("hub");
            assertTrue(found.isPresent());
        }

        @Test
        @DisplayName("throws on duplicate zoneId")
        void duplicateThrows() {
            registry.registerZone(createZone("hub", 0));

            assertThrows(IllegalArgumentException.class, () ->
                registry.registerZone(createZone("hub", 5)));
        }

        @Test
        @DisplayName("increments modification version")
        void incrementsVersion() {
            long v0 = registry.getModificationVersion();
            registry.registerZone(createZone("hub", 0));
            assertTrue(registry.getModificationVersion() > v0);
        }

        @Test
        @DisplayName("createZone is alias for registerZone")
        void createAlias() {
            ZoneDefinition zone = createZone("hub", 0);
            UUID id = registry.createZone(zone);

            assertNotNull(id);
            assertTrue(registry.getZone(id).isPresent());
        }
    }

    // ========================================================================
    // Update
    // ========================================================================

    @Nested
    @DisplayName("updateZone()")
    class UpdateTests {

        @Test
        @DisplayName("updates existing zone")
        void updatesExisting() {
            ZoneDefinition original = createZone("hub", 0);
            UUID id = registry.registerZone(original);

            ZoneDefinition updated = original.toBuilder()
                .displayName("New Hub")
                .priority(5)
                .build();
            boolean result = registry.updateZone(id, updated);

            assertTrue(result);
            assertEquals("New Hub", registry.getZone(id).get().displayName());
            assertEquals(5, registry.getZone(id).get().priority());
        }

        @Test
        @DisplayName("returns false for non-existent zone")
        void nonExistentReturnsFalse() {
            UUID bogus = UUID.randomUUID();
            ZoneDefinition zone = createZone("hub", 0);

            assertFalse(registry.updateZone(bogus, zone));
        }

        @Test
        @DisplayName("handles zoneId change atomically")
        void zoneIdChange() {
            ZoneDefinition zone = createZone("old_id", 0);
            UUID id = registry.registerZone(zone);

            ZoneDefinition renamed = ZoneDefinition.builder("new_id")
                .id(zone.id())
                .bounds(zone.bounds())
                .cueSound(zone.cueSound())
                .hintMessage("")
                .build();
            boolean result = registry.updateZone(id, renamed);

            assertTrue(result);
            assertFalse(registry.getZoneById("old_id").isPresent());
            assertTrue(registry.getZoneById("new_id").isPresent());
        }

        @Test
        @DisplayName("rejects zoneId change to existing ID")
        void rejectsConflictingIdChange() {
            ZoneDefinition zone1 = createZone("zone_a", 0);
            ZoneDefinition zone2 = createZone("zone_b", 0,
                new ZoneBounds(200, 300, 0, 100, 200, 300));
            UUID id1 = registry.registerZone(zone1);
            registry.registerZone(zone2);

            ZoneDefinition renamed = ZoneDefinition.builder("zone_b")
                .id(zone1.id())
                .bounds(zone1.bounds())
                .cueSound(zone1.cueSound())
                .hintMessage("")
                .build();
            boolean result = registry.updateZone(id1, renamed);

            assertFalse(result);
            // Original is still intact
            assertTrue(registry.getZoneById("zone_a").isPresent());
        }
    }

    // ========================================================================
    // Unregister (Delete)
    // ========================================================================

    @Nested
    @DisplayName("unregisterZone()")
    class UnregisterTests {

        @Test
        @DisplayName("removes zone by UUID")
        void removesZone() {
            ZoneDefinition zone = createZone("hub", 0);
            UUID id = registry.registerZone(zone);

            boolean result = registry.unregisterZone(id);

            assertTrue(result);
            assertEquals(0, registry.getZoneCount());
            assertTrue(registry.getZone(id).isEmpty());
            assertTrue(registry.getZoneById("hub").isEmpty());
        }

        @Test
        @DisplayName("returns false for non-existent UUID")
        void nonExistentReturnsFalse() {
            assertFalse(registry.unregisterZone(UUID.randomUUID()));
        }

        @Test
        @DisplayName("cleans up associated markers")
        void cleansUpMarkers() {
            ZoneDefinition zone = createZone("hub", 0);
            UUID id = registry.registerZone(zone);
            BlockPos markerPos = new BlockPos(5, 64, 5);
            registry.registerMarker(markerPos, id);

            registry.unregisterZone(id);

            assertTrue(registry.getMarkerZone(markerPos).isEmpty());
        }

        @Test
        @DisplayName("cleans up associated aliases")
        void cleansUpAliases() {
            ZoneDefinition zone = createZone("hub", 0);
            UUID id = registry.registerZone(zone);
            registry.registerAlias("home", "hub");

            registry.unregisterZone(id);

            assertNull(registry.resolveAlias("home"));
        }

        @Test
        @DisplayName("deleteZone is alias for unregisterZone")
        void deleteAlias() {
            ZoneDefinition zone = createZone("hub", 0);
            UUID id = registry.registerZone(zone);

            assertTrue(registry.deleteZone(id));
            assertEquals(0, registry.getZoneCount());
        }
    }

    // ========================================================================
    // Query Operations
    // ========================================================================

    @Nested
    @DisplayName("Query Operations")
    class QueryTests {

        @Test
        @DisplayName("getAllZones returns all registered zones")
        void getAllZones() {
            registry.registerZone(createZone("hub", 0));
            registry.registerZone(createZone("combat", 10,
                new ZoneBounds(200, 300, 0, 100, 0, 100)));

            assertEquals(2, registry.getAllZones().size());
        }

        @Test
        @DisplayName("getZoneById is case-insensitive")
        void caseInsensitiveLookup() {
            registry.registerZone(createZone("hub", 0));

            // Zone IDs must be lowercase in the constructor, but lookup is case-insensitive
            assertTrue(registry.getZoneById("hub").isPresent());
            assertTrue(registry.getZoneById("HUB").isPresent());
            assertTrue(registry.getZoneById("Hub").isPresent());
        }

        @Test
        @DisplayName("hasZoneId checks existence by string ID")
        void hasZoneId() {
            registry.registerZone(createZone("hub", 0));

            assertTrue(registry.hasZoneId("hub"));
            assertTrue(registry.hasZoneId("HUB"));
            assertFalse(registry.hasZoneId("combat"));
        }

        @Test
        @DisplayName("getTeleportableZones filters and sorts by zoneId")
        void teleportableZones() {
            registry.registerZone(createZone("combat", 10));

            ZoneDefinition special = ZoneDefinition.builder("quiet")
                .bounds(new ZoneBounds(200, 300, 0, 100, 200, 300))
                .cueSound(SoundEvents.NOTE_BLOCK_CHIME.value())
                .hintMessage("")
                .hasTeleport(false)
                .build();
            registry.registerZone(special);

            registry.registerZone(createZone("arena", 10,
                new ZoneBounds(400, 500, 0, 100, 0, 100)));

            var teleportable = registry.getTeleportableZones();
            assertEquals(2, teleportable.size());
            // Sorted alphabetically: arena before combat
            assertEquals("arena", teleportable.get(0).zoneId());
            assertEquals("combat", teleportable.get(1).zoneId());
        }
    }

    // ========================================================================
    // Zone Resolution
    // ========================================================================

    @Nested
    @DisplayName("resolveZone()")
    class ZoneResolutionTests {

        @Test
        @DisplayName("resolves position to containing zone")
        void resolvesContaining() {
            ZoneDefinition zone = createZone("hub", 0);
            registry.registerZone(zone);

            Optional<ZoneDefinition> result = registry.resolveZone(
                new BlockPos(50, 50, 50), BlockPos.ZERO);

            assertTrue(result.isPresent());
            assertEquals("hub", result.get().zoneId());
        }

        @Test
        @DisplayName("returns empty for position outside all zones")
        void emptyForOutside() {
            registry.registerZone(createZone("hub", 0));

            Optional<ZoneDefinition> result = registry.resolveZone(
                new BlockPos(500, 500, 500), BlockPos.ZERO);

            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("resolves overlapping zones by priority (highest wins)")
        void priorityResolution() {
            ZoneDefinition lowPriority = createZone("hub", 0);
            ZoneDefinition highPriority = createZone("combat", 10,
                new ZoneBounds(0, 50, 0, 50, 0, 50));

            registry.registerZone(lowPriority);
            registry.registerZone(highPriority);

            // Position inside both zones
            Optional<ZoneDefinition> result = registry.resolveZone(
                new BlockPos(25, 25, 25), BlockPos.ZERO);

            assertTrue(result.isPresent());
            assertEquals("combat", result.get().zoneId());
        }

        @Test
        @DisplayName("falls back to lower priority when position only in larger zone")
        void fallsBackToLowerPriority() {
            ZoneDefinition lowPriority = createZone("hub", 0);
            ZoneDefinition highPriority = createZone("combat", 10,
                new ZoneBounds(0, 50, 0, 50, 0, 50));

            registry.registerZone(lowPriority);
            registry.registerZone(highPriority);

            // Position only inside hub (outside combat)
            Optional<ZoneDefinition> result = registry.resolveZone(
                new BlockPos(75, 75, 75), BlockPos.ZERO);

            assertTrue(result.isPresent());
            assertEquals("hub", result.get().zoneId());
        }
    }

    // ========================================================================
    // Alias Management
    // ========================================================================

    @Nested
    @DisplayName("Alias Management")
    class AliasTests {

        @Test
        @DisplayName("registers and resolves alias")
        void registerAndResolve() {
            registry.registerZone(createZone("hub", 0));
            registry.registerAlias("home", "hub");

            Optional<ZoneDefinition> result = registry.resolveByAlias("home");
            assertTrue(result.isPresent());
            assertEquals("hub", result.get().zoneId());
        }

        @Test
        @DisplayName("alias resolution is case-insensitive")
        void caseInsensitive() {
            registry.registerZone(createZone("hub", 0));
            registry.registerAlias("Home", "hub");

            Optional<ZoneDefinition> result = registry.resolveByAlias("HOME");
            assertTrue(result.isPresent());
        }

        @Test
        @DisplayName("resolveByAlias falls back to direct zoneId lookup")
        void fallsBackToDirectId() {
            registry.registerZone(createZone("hub", 0));

            Optional<ZoneDefinition> result = registry.resolveByAlias("hub");
            assertTrue(result.isPresent());
            assertEquals("hub", result.get().zoneId());
        }

        @Test
        @DisplayName("resolveByAlias returns empty for unknown alias and zoneId")
        void unknownAlias() {
            Optional<ZoneDefinition> result = registry.resolveByAlias("nonexistent");
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("unregisterAlias removes alias")
        void unregisterAlias() {
            registry.registerAlias("home", "hub");

            assertTrue(registry.unregisterAlias("home"));
            assertNull(registry.resolveAlias("home"));
        }

        @Test
        @DisplayName("unregisterAlias returns false for unknown alias")
        void unregisterUnknown() {
            assertFalse(registry.unregisterAlias("nonexistent"));
        }

        @Test
        @DisplayName("getAllAliases returns all registered aliases")
        void getAllAliases() {
            registry.registerAlias("home", "hub");
            registry.registerAlias("spawn", "hub");

            assertEquals(2, registry.getAllAliases().size());
        }
    }

    // ========================================================================
    // Marker Management
    // ========================================================================

    @Nested
    @DisplayName("Marker Management")
    class MarkerTests {

        @Test
        @DisplayName("registers and retrieves marker")
        void registerAndRetrieve() {
            UUID zoneId = UUID.randomUUID();
            BlockPos pos = new BlockPos(10, 64, 20);

            registry.registerMarker(pos, zoneId);

            Optional<UUID> found = registry.getMarkerZone(pos);
            assertTrue(found.isPresent());
            assertEquals(zoneId, found.get());
        }

        @Test
        @DisplayName("getMarkersForZone returns all markers for a zone")
        void getMarkersForZone() {
            UUID zoneId = UUID.randomUUID();
            BlockPos pos1 = new BlockPos(10, 64, 20);
            BlockPos pos2 = new BlockPos(30, 64, 40);

            registry.registerMarker(pos1, zoneId);
            registry.registerMarker(pos2, zoneId);

            Set<BlockPos> markers = registry.getMarkersForZone(zoneId);
            assertEquals(2, markers.size());
            assertTrue(markers.contains(pos1));
            assertTrue(markers.contains(pos2));
        }

        @Test
        @DisplayName("unregisterMarker removes marker")
        void unregisterMarker() {
            UUID zoneId = UUID.randomUUID();
            BlockPos pos = new BlockPos(10, 64, 20);

            registry.registerMarker(pos, zoneId);
            registry.unregisterMarker(pos);

            assertTrue(registry.getMarkerZone(pos).isEmpty());
        }

        @Test
        @DisplayName("getMarkersForZone returns empty for unknown zone")
        void emptyForUnknownZone() {
            Set<BlockPos> markers = registry.getMarkersForZone(UUID.randomUUID());
            assertTrue(markers.isEmpty());
        }
    }

    // ========================================================================
    // Area Linking
    // ========================================================================

    @Nested
    @DisplayName("Area Linking")
    class AreaLinkingTests {

        @Test
        @DisplayName("links area to zone")
        void linkArea() {
            UUID areaId = UUID.randomUUID();
            registry.linkAreaToZone(areaId, "hub");

            Set<UUID> areas = registry.getAreasForZone("hub");
            assertEquals(1, areas.size());
            assertTrue(areas.contains(areaId));
        }

        @Test
        @DisplayName("links multiple areas to same zone")
        void multipleAreas() {
            UUID area1 = UUID.randomUUID();
            UUID area2 = UUID.randomUUID();

            registry.linkAreaToZone(area1, "hub");
            registry.linkAreaToZone(area2, "hub");

            assertEquals(2, registry.getAreasForZone("hub").size());
        }

        @Test
        @DisplayName("unlinks area from zone")
        void unlinkArea() {
            UUID areaId = UUID.randomUUID();
            registry.linkAreaToZone(areaId, "hub");
            registry.unlinkAreaFromZone(areaId, "hub");

            assertTrue(registry.getAreasForZone("hub").isEmpty());
        }

        @Test
        @DisplayName("hasLinkedAreas returns correct state")
        void hasLinkedAreas() {
            assertFalse(registry.hasLinkedAreas("hub"));

            UUID areaId = UUID.randomUUID();
            registry.linkAreaToZone(areaId, "hub");
            assertTrue(registry.hasLinkedAreas("hub"));

            registry.unlinkAreaFromZone(areaId, "hub");
            assertFalse(registry.hasLinkedAreas("hub"));
        }

        @Test
        @DisplayName("area linking is case-insensitive")
        void caseInsensitive() {
            UUID areaId = UUID.randomUUID();
            registry.linkAreaToZone(areaId, "HUB");

            assertTrue(registry.hasLinkedAreas("hub"));
            assertEquals(1, registry.getAreasForZone("Hub").size());
        }

        @Test
        @DisplayName("getAreasForZone returns empty for unlinked zone")
        void emptyForUnlinkedZone() {
            assertTrue(registry.getAreasForZone("nonexistent").isEmpty());
        }
    }

    // ========================================================================
    // Initialization
    // ========================================================================

    @Nested
    @DisplayName("Initialization")
    class InitializationTests {

        @Test
        @DisplayName("new registry is not initialized")
        void notInitialized() {
            assertFalse(registry.isInitialized());
        }

        @Test
        @DisplayName("markInitialized sets initialized flag")
        void markInitialized() {
            registry.markInitialized();
            assertTrue(registry.isInitialized());
        }

        @Test
        @DisplayName("markInitialized is idempotent")
        void markInitializedIdempotent() {
            long v1 = registry.getModificationVersion();
            registry.markInitialized();
            long v2 = registry.getModificationVersion();
            registry.markInitialized();
            long v3 = registry.getModificationVersion();

            assertTrue(v2 > v1);
            assertEquals(v2, v3); // second call doesn't increment
        }
    }
}
