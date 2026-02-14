package com.devmod.zone.data;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;

import com.devmod.TestBootstrap;

/**
 * Unit tests for ZonePresets: legacy zone creation, alias definitions,
 * zone ID uniqueness, and overlap / priority invariants.
 */
@DisplayName("ZonePresets")
class ZonePresetsTest {

    @BeforeAll
    static void setup() {
        TestBootstrap.init();
    }

    private static final BlockPos ORIGIN = new BlockPos(0, 64, 0);

    // ========================================================================
    // Legacy Zone Creation
    // ========================================================================

    @Nested
    @DisplayName("createLegacyZones()")
    class LegacyZoneTests {

        @Test
        @DisplayName("creates non-empty list of zones")
        void createsZones() {
            List<ZoneDefinition> zones = ZonePresets.createLegacyZones(ORIGIN);
            assertFalse(zones.isEmpty());
            assertTrue(zones.size() >= 10, "Expected at least 10 legacy zones");
        }

        @Test
        @DisplayName("all zones have unique IDs")
        void uniqueZoneIds() {
            List<ZoneDefinition> zones = ZonePresets.createLegacyZones(ORIGIN);
            Set<String> ids = new HashSet<>();
            for (ZoneDefinition zone : zones) {
                assertTrue(ids.add(zone.zoneId()),
                    "Duplicate zone ID: " + zone.zoneId());
            }
        }

        @Test
        @DisplayName("all zones have unique UUIDs")
        void uniqueUuids() {
            List<ZoneDefinition> zones = ZonePresets.createLegacyZones(ORIGIN);
            Set<java.util.UUID> uuids = new HashSet<>();
            for (ZoneDefinition zone : zones) {
                assertTrue(uuids.add(zone.id()),
                    "Duplicate UUID for zone: " + zone.zoneId());
            }
        }

        @Test
        @DisplayName("all zone IDs pass ZoneNaming validation")
        void validZoneIds() {
            List<ZoneDefinition> zones = ZonePresets.createLegacyZones(ORIGIN);
            for (ZoneDefinition zone : zones) {
                assertTrue(ZoneNaming.isValidZoneId(zone.zoneId()),
                    "Invalid zone ID: " + zone.zoneId());
            }
        }

        @Test
        @DisplayName("all zones have valid bounds")
        void validBounds() {
            List<ZoneDefinition> zones = ZonePresets.createLegacyZones(ORIGIN);
            for (ZoneDefinition zone : zones) {
                assertTrue(zone.bounds().isValid(),
                    "Invalid bounds for zone: " + zone.zoneId());
            }
        }

        @Test
        @DisplayName("hub zone has lowest priority (0)")
        void hubLowestPriority() {
            List<ZoneDefinition> zones = ZonePresets.createLegacyZones(ORIGIN);
            ZoneDefinition hub = zones.stream()
                .filter(z -> "hub".equals(z.zoneId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("hub zone not found"));

            assertEquals(0, hub.priority());
        }

        @Test
        @DisplayName("special zones have higher priority than parent zones")
        void specialZonesPriority() {
            List<ZoneDefinition> zones = ZonePresets.createLegacyZones(ORIGIN);

            int quietPriority = findZone(zones, "quiet").priority();
            int telemetryPriority = findZone(zones, "telemetry").priority();
            assertTrue(quietPriority > telemetryPriority,
                "quiet (" + quietPriority + ") should have higher priority than telemetry (" + telemetryPriority + ")");

            int perfPriority = findZone(zones, "performance").priority();
            int sandboxPriority = findZone(zones, "sandbox").priority();
            assertTrue(perfPriority > sandboxPriority,
                "performance (" + perfPriority + ") should have higher priority than sandbox (" + sandboxPriority + ")");
        }

        @Test
        @DisplayName("special zones are marked isSpecial")
        void specialZonesMarked() {
            List<ZoneDefinition> zones = ZonePresets.createLegacyZones(ORIGIN);

            assertTrue(findZone(zones, "quiet").isSpecial());
            assertTrue(findZone(zones, "performance").isSpecial());
            assertTrue(findZone(zones, "afk").isSpecial());
        }

        @Test
        @DisplayName("special zones are not teleportable")
        void specialZonesNotTeleportable() {
            List<ZoneDefinition> zones = ZonePresets.createLegacyZones(ORIGIN);

            assertFalse(findZone(zones, "quiet").hasTeleport());
            assertFalse(findZone(zones, "performance").hasTeleport());
            assertFalse(findZone(zones, "afk").hasTeleport());
        }

        @Test
        @DisplayName("sandbox allows building")
        void sandboxAllowsBuilding() {
            List<ZoneDefinition> zones = ZonePresets.createLegacyZones(ORIGIN);
            assertTrue(findZone(zones, "sandbox").allowBuilding());
        }

        @Test
        @DisplayName("hub does not allow building")
        void hubDisallowsBuilding() {
            List<ZoneDefinition> zones = ZonePresets.createLegacyZones(ORIGIN);
            assertFalse(findZone(zones, "hub").allowBuilding());
        }

        private ZoneDefinition findZone(List<ZoneDefinition> zones, String zoneId) {
            return zones.stream()
                .filter(z -> zoneId.equals(z.zoneId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Zone not found: " + zoneId));
        }
    }

    // ========================================================================
    // Default Aliases
    // ========================================================================

    @Nested
    @DisplayName("getDefaultAliases()")
    class AliasTests {

        @Test
        @DisplayName("returns non-empty alias map")
        void nonEmpty() {
            Map<String, String> aliases = ZonePresets.getDefaultAliases();
            assertFalse(aliases.isEmpty());
        }

        @Test
        @DisplayName("contains expected hub aliases")
        void hubAliases() {
            Map<String, String> aliases = ZonePresets.getDefaultAliases();
            assertEquals("hub", aliases.get("home"));
            assertEquals("hub", aliases.get("spawn"));
        }

        @Test
        @DisplayName("contains overview aliases")
        void overviewAliases() {
            Map<String, String> aliases = ZonePresets.getDefaultAliases();
            assertEquals("overview", aliases.get("view"));
            assertEquals("overview", aliases.get("deck"));
        }

        @Test
        @DisplayName("all alias targets are valid zone IDs")
        void aliasTargetsValid() {
            Map<String, String> aliases = ZonePresets.getDefaultAliases();
            List<ZoneDefinition> zones = ZonePresets.createLegacyZones(ORIGIN);
            Set<String> validIds = new HashSet<>();
            for (ZoneDefinition zone : zones) {
                validIds.add(zone.zoneId());
            }

            for (Map.Entry<String, String> entry : aliases.entrySet()) {
                assertTrue(validIds.contains(entry.getValue()),
                    "Alias '" + entry.getKey() + "' points to non-existent zone: " + entry.getValue());
            }
        }
    }
}
