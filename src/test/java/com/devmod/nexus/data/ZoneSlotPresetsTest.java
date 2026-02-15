package com.devmod.nexus.data;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("ZoneSlotPresets")
class ZoneSlotPresetsTest {

    // ========================================================================
    // Layout Constants
    // ========================================================================

    @Nested
    @DisplayName("Layout Constants")
    class LayoutConstantsTests {

        @Test
        @DisplayName("HUB_SIZE is positive and reasonable")
        void hubSizeIsPositive() {
            assertTrue(ZoneSlotPresets.HUB_SIZE > 0);
            assertTrue(ZoneSlotPresets.HUB_SIZE <= 2048, "Hub should fit in reasonable area");
        }

        @Test
        @DisplayName("CENTER_SIZE is smaller than HUB_SIZE")
        void centerSizeSmallerThanHub() {
            assertTrue(ZoneSlotPresets.CENTER_SIZE > 0);
            assertTrue(ZoneSlotPresets.CENTER_SIZE < ZoneSlotPresets.HUB_SIZE);
        }

        @Test
        @DisplayName("ZONE_SIZE is positive")
        void zoneSizeIsPositive() {
            assertTrue(ZoneSlotPresets.ZONE_SIZE > 0);
        }

        @Test
        @DisplayName("CORRIDOR_WIDTH is positive and smaller than ZONE_SIZE")
        void corridorWidthIsReasonable() {
            assertTrue(ZoneSlotPresets.CORRIDOR_WIDTH > 0);
            assertTrue(ZoneSlotPresets.CORRIDOR_WIDTH < ZoneSlotPresets.ZONE_SIZE);
        }

        @Test
        @DisplayName("FLOOR_Y is in valid MC build range")
        void floorYIsValid() {
            assertTrue(ZoneSlotPresets.FLOOR_Y >= -64);
            assertTrue(ZoneSlotPresets.FLOOR_Y <= 320);
        }

        @Test
        @DisplayName("ZONE_HEIGHT is positive")
        void zoneHeightIsPositive() {
            assertTrue(ZoneSlotPresets.ZONE_HEIGHT > 0);
        }
    }

    // ========================================================================
    // Config Fallback Tests
    // ========================================================================

    @Nested
    @DisplayName("Config Fallbacks")
    class ConfigFallbackTests {

        @Test
        @DisplayName("getHubSize falls back to constant when config unavailable")
        void hubSizeFallback() {
            int size = ZoneSlotPresets.getHubSize();
            assertEquals(ZoneSlotPresets.HUB_SIZE, size);
        }

        @Test
        @DisplayName("getCenterSize falls back to constant")
        void centerSizeFallback() {
            int size = ZoneSlotPresets.getCenterSize();
            assertEquals(ZoneSlotPresets.CENTER_SIZE, size);
        }

        @Test
        @DisplayName("getZoneSize falls back to constant")
        void zoneSizeFallback() {
            int size = ZoneSlotPresets.getZoneSize();
            assertEquals(ZoneSlotPresets.ZONE_SIZE, size);
        }

        @Test
        @DisplayName("getCorridorWidth falls back to constant")
        void corridorWidthFallback() {
            int width = ZoneSlotPresets.getCorridorWidth();
            assertEquals(ZoneSlotPresets.CORRIDOR_WIDTH, width);
        }

        @Test
        @DisplayName("getFloorY falls back to constant")
        void floorYFallback() {
            int y = ZoneSlotPresets.getFloorY();
            assertEquals(ZoneSlotPresets.FLOOR_Y, y);
        }

        @Test
        @DisplayName("getZoneHeight falls back to constant")
        void zoneHeightFallback() {
            int height = ZoneSlotPresets.getZoneHeight();
            assertEquals(ZoneSlotPresets.ZONE_HEIGHT, height);
        }

        @Test
        @DisplayName("isAutoCreateZonesEnabled defaults to true")
        void autoCreateZonesDefault() {
            assertTrue(ZoneSlotPresets.isAutoCreateZonesEnabled());
        }

        @Test
        @DisplayName("isAutoCreatePortalsEnabled defaults to true")
        void autoCreatePortalsDefault() {
            assertTrue(ZoneSlotPresets.isAutoCreatePortalsEnabled());
        }
    }
}
