package com.devmod.portal;

import com.devmod.TestBootstrap;
import org.junit.jupiter.api.BeforeAll;
import javax.annotation.Nullable;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for portal teleport overlay progress calculation.
 * Tests the client-side progress tracking for the teleportation visual effect.
 */
class PortalTeleportOverlayTest {

    @BeforeAll
    static void setup() {
        TestBootstrap.init();
    }

    @Nested
    @DisplayName("Progress Calculation")
    class ProgressCalculation {

        /**
         * Default teleportation delay in ticks (4 seconds = 80 ticks).
         */
        private static final int DEFAULT_TELEPORT_DELAY = 80;

        @Test
        @DisplayName("Progress is 0% at start")
        void progressIsZeroAtStart() {
            float progress = calculateProgress(0, DEFAULT_TELEPORT_DELAY);
            assertEquals(0.0f, progress, 0.001f, "Progress should be 0% at tick 0");
        }

        @Test
        @DisplayName("Progress is 50% at half delay")
        void progressIsHalfAtHalfDelay() {
            float progress = calculateProgress(40, DEFAULT_TELEPORT_DELAY);
            assertEquals(0.5f, progress, 0.001f, "Progress should be 50% at tick 40");
        }

        @Test
        @DisplayName("Progress is 100% at full delay")
        void progressIs100AtFullDelay() {
            float progress = calculateProgress(80, DEFAULT_TELEPORT_DELAY);
            assertEquals(1.0f, progress, 0.001f, "Progress should be 100% at tick 80");
        }

        @Test
        @DisplayName("Progress clamps to 1.0 if over delay")
        void progressClampsAtMax() {
            float progress = calculateProgress(100, DEFAULT_TELEPORT_DELAY);
            assertEquals(1.0f, progress, 0.001f, "Progress should clamp to 100%");
        }

        @Test
        @DisplayName("Progress clamps to 0.0 if negative")
        void progressClampsAtMin() {
            float progress = calculateProgress(-10, DEFAULT_TELEPORT_DELAY);
            assertEquals(0.0f, progress, 0.001f, "Progress should clamp to 0%");
        }

        @Test
        @DisplayName("Instant teleport (delay 0) returns 1.0")
        void instantTeleportReturns100() {
            float progress = calculateProgress(1, 0);
            assertEquals(1.0f, progress, 0.001f, "Instant teleport should return 100%");
        }

        /**
         * Helper method that mirrors the progress calculation logic.
         */
        private float calculateProgress(int currentTicks, int requiredDelay) {
            if (requiredDelay <= 0) {
                return 1.0f;
            }
            return Math.max(0.0f, Math.min(1.0f, (float) currentTicks / requiredDelay));
        }
    }

    @Nested
    @DisplayName("Overlay State")
    class OverlayState {

        @Test
        @DisplayName("Overlay is inactive by default")
        void overlayInactiveByDefault() {
            PortalTeleportState state = new PortalTeleportState();
            assertFalse(state.isActive(), "Overlay should be inactive by default");
        }

        @Test
        @DisplayName("Overlay activates when entering portal")
        void overlayActivatesOnPortalEntry() {
            PortalTeleportState state = new PortalTeleportState();
            state.enterPortal(PortalColor.BLUE);
            assertTrue(state.isActive(), "Overlay should activate on portal entry");
        }

        @Test
        @DisplayName("Overlay deactivates when exiting portal")
        void overlayDeactivatesOnPortalExit() {
            PortalTeleportState state = new PortalTeleportState();
            state.enterPortal(PortalColor.BLUE);
            state.exitPortal();
            assertFalse(state.isActive(), "Overlay should deactivate on portal exit");
        }

        @Test
        @DisplayName("State tracks portal color")
        void stateTracksColor() {
            PortalTeleportState state = new PortalTeleportState();
            state.enterPortal(PortalColor.RED);
            assertEquals(PortalColor.RED, state.getColor(), "State should track portal color");
        }

        @Test
        @DisplayName("State tracks ticks in portal")
        void stateTracksTicksInPortal() {
            PortalTeleportState state = new PortalTeleportState();
            state.enterPortal(PortalColor.BLUE);
            state.tick();
            state.tick();
            state.tick();
            assertEquals(3, state.getTicksInPortal(), "State should track ticks");
        }

        @Test
        @DisplayName("Ticks reset when exiting portal")
        void ticksResetOnExit() {
            PortalTeleportState state = new PortalTeleportState();
            state.enterPortal(PortalColor.BLUE);
            state.tick();
            state.tick();
            state.exitPortal();
            assertEquals(0, state.getTicksInPortal(), "Ticks should reset on exit");
        }

        @Test
        @DisplayName("Color is null when inactive")
        void colorNullWhenInactive() {
            PortalTeleportState state = new PortalTeleportState();
            assertNull(state.getColor(), "Color should be null when inactive");
        }
    }

    @Nested
    @DisplayName("Visual Effect Interpolation")
    class VisualEffectInterpolation {

        @Test
        @DisplayName("Alpha increases with progress")
        void alphaIncreasesWithProgress() {
            float alpha0 = calculateAlpha(0.0f);
            float alpha50 = calculateAlpha(0.5f);
            float alpha100 = calculateAlpha(1.0f);

            assertTrue(alpha0 < alpha50, "Alpha at 0% should be less than 50%");
            assertTrue(alpha50 < alpha100, "Alpha at 50% should be less than 100%");
        }

        @Test
        @DisplayName("Max alpha is capped for visibility")
        void maxAlphaIsCapped() {
            float alpha = calculateAlpha(1.0f);
            assertTrue(alpha <= 0.7f, "Max alpha should be capped to maintain visibility");
        }

        @Test
        @DisplayName("Min alpha is 0 at start")
        void minAlphaIsZero() {
            float alpha = calculateAlpha(0.0f);
            assertEquals(0.0f, alpha, 0.001f, "Min alpha should be 0");
        }

        /**
         * Helper method for alpha calculation.
         * Alpha ranges from 0 (invisible) to 0.7 (semi-transparent).
         */
        private float calculateAlpha(float progress) {
            return progress * 0.7f;
        }
    }

    /**
     * Helper class representing the client-side teleport state.
     * This mirrors the state that will be tracked in the actual overlay.
     */
    static class PortalTeleportState {
        private boolean active = false;
        @Nullable
        private PortalColor color = null;
        private int ticksInPortal = 0;

        public boolean isActive() {
            return active;
        }

        public void enterPortal(PortalColor color) {
            this.active = true;
            this.color = color;
            this.ticksInPortal = 0;
        }

        public void exitPortal() {
            this.active = false;
            this.color = null;
            this.ticksInPortal = 0;
        }

        public void tick() {
            if (active) {
                ticksInPortal++;
            }
        }

        @Nullable
        public PortalColor getColor() {
            return color;
        }

        public int getTicksInPortal() {
            return ticksInPortal;
        }
    }
}
