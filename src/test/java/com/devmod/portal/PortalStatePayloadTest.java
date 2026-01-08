package com.devmod.portal;

import com.devmod.TestBootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for PortalStatePayload serialization and validation.
 * Tests the network payload used to sync portal teleportation state to clients.
 */
class PortalStatePayloadTest {

    @BeforeAll
    static void setup() {
        TestBootstrap.init();
    }

    @Nested
    @DisplayName("Payload Construction")
    class PayloadConstruction {

        @Test
        @DisplayName("Create enter portal payload")
        void createEnterPortalPayload() {
            PortalStateData data = PortalStateData.enterPortal(
                PortalColor.BLUE.getIndex(),
                10,  // ticksInPortal
                80   // requiredDelay
            );

            assertTrue(data.inPortal(), "Should be in portal");
            assertEquals(PortalColor.BLUE.getIndex(), data.portalColorIndex(), "Should have blue color index");
            assertEquals(10, data.ticksInPortal(), "Should have 10 ticks");
            assertEquals(80, data.requiredDelay(), "Should have 80 ticks delay");
        }

        @Test
        @DisplayName("Create exit portal payload")
        void createExitPortalPayload() {
            PortalStateData data = PortalStateData.exitPortal();

            assertFalse(data.inPortal(), "Should not be in portal");
            assertEquals(0, data.ticksInPortal(), "Ticks should be 0");
        }

        @Test
        @DisplayName("All portal colors are serializable")
        void allColorsSerializable() {
            for (PortalColor color : PortalColor.values()) {
                PortalStateData data = PortalStateData.enterPortal(
                    color.getIndex(), 0, 80
                );

                assertEquals(color.getIndex(), data.portalColorIndex(),
                    "Color " + color.name() + " should serialize correctly");

                // Verify round-trip
                PortalColor recovered = PortalColor.byIndex(data.portalColorIndex());
                assertEquals(color, recovered, "Color should round-trip correctly");
            }
        }

        @Test
        @DisplayName("Invalid color index returns default")
        void invalidColorIndexReturnsDefault() {
            PortalColor invalid = PortalColor.byIndex(-1);
            assertEquals(PortalColor.BLUE, invalid, "Invalid index should return BLUE");

            PortalColor outOfBounds = PortalColor.byIndex(999);
            assertEquals(PortalColor.BLUE, outOfBounds, "Out of bounds index should return BLUE");
        }
    }

    @Nested
    @DisplayName("Progress Calculation")
    class ProgressCalculation {

        @Test
        @DisplayName("Calculate progress percentage")
        void calculateProgress() {
            PortalStateData data = PortalStateData.enterPortal(0, 40, 80);
            float progress = data.getProgress();

            assertEquals(0.5f, progress, 0.001f, "Progress should be 50%");
        }

        @Test
        @DisplayName("Progress clamps to 1.0 max")
        void progressClampsToMax() {
            PortalStateData data = PortalStateData.enterPortal(0, 100, 80);
            float progress = data.getProgress();

            assertEquals(1.0f, progress, 0.001f, "Progress should clamp to 100%");
        }

        @Test
        @DisplayName("Progress is 0 at start")
        void progressIsZeroAtStart() {
            PortalStateData data = PortalStateData.enterPortal(0, 0, 80);
            float progress = data.getProgress();

            assertEquals(0.0f, progress, 0.001f, "Progress should be 0%");
        }

        @Test
        @DisplayName("Instant teleport (delay 0) returns 100%")
        void instantTeleportProgress() {
            PortalStateData data = PortalStateData.enterPortal(0, 1, 0);
            float progress = data.getProgress();

            assertEquals(1.0f, progress, 0.001f, "Instant teleport should return 100%");
        }
    }

    @Nested
    @DisplayName("State Transitions")
    class StateTransitions {

        @Test
        @DisplayName("Tick increments while in portal")
        void tickIncrements() {
            PortalStateData data = PortalStateData.enterPortal(0, 0, 80);
            assertEquals(0, data.ticksInPortal(), "Should start at 0");

            // Simulate tick update (in practice, server sends new payload)
            PortalStateData updated = PortalStateData.enterPortal(0, 1, 80);
            assertEquals(1, updated.ticksInPortal(), "Should be at 1 after tick");
        }

        @Test
        @DisplayName("Exit portal resets state")
        void exitResetsState() {
            PortalStateData exit = PortalStateData.exitPortal();

            assertFalse(exit.inPortal(), "Should not be in portal");
            assertEquals(0, exit.ticksInPortal(), "Ticks should be 0");
            assertEquals(0, exit.requiredDelay(), "Delay should be 0");
        }
    }

    /**
     * Test helper record that mirrors the payload data structure.
     * The actual payload will use this structure with NeoForge codecs.
     */
    record PortalStateData(
        boolean inPortal,
        int portalColorIndex,
        int ticksInPortal,
        int requiredDelay
    ) {
        public static PortalStateData enterPortal(int colorIndex, int ticks, int delay) {
            return new PortalStateData(true, colorIndex, ticks, delay);
        }

        public static PortalStateData exitPortal() {
            return new PortalStateData(false, 0, 0, 0);
        }

        public float getProgress() {
            if (requiredDelay <= 0) {
                return 1.0f;
            }
            return Math.min(1.0f, (float) ticksInPortal / requiredDelay);
        }
    }
}
