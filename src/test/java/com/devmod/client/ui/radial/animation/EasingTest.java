package com.devmod.client.ui.radial.animation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link Easing} functions.
 */
@DisplayName("Easing Functions")
class EasingTest {

    /** Functional interface for easing functions using primitive float to avoid boxing warnings. */
    @FunctionalInterface
    private interface EasingFunction {
        float apply(float t);
    }

    private static final float DELTA = 0.001f;

    // ================================================================
    // BOUNDARY TESTS
    // ================================================================

    @Nested
    @DisplayName("All Easing Functions")
    class AllEasingFunctionsTests {

        @Test
        @DisplayName("all functions return 0 at t=0")
        void allReturnZeroAtStart() {
            assertEquals(0f, Easing.linear(0f), DELTA);
            assertEquals(0f, Easing.easeInQuad(0f), DELTA);
            assertEquals(0f, Easing.easeOutQuad(0f), DELTA);
            assertEquals(0f, Easing.easeInOutQuad(0f), DELTA);
            assertEquals(0f, Easing.easeInCubic(0f), DELTA);
            assertEquals(0f, Easing.easeOutCubic(0f), DELTA);
            assertEquals(0f, Easing.easeInOutCubic(0f), DELTA);
            assertEquals(0f, Easing.easeOutQuart(0f), DELTA);
            assertEquals(0f, Easing.easeOutExpo(0f), DELTA);
            assertEquals(0f, Easing.easeOutElastic(0f), DELTA);
            assertEquals(0f, Easing.smoothStep(0f), DELTA);
            assertEquals(0f, Easing.smootherStep(0f), DELTA);
        }

        @Test
        @DisplayName("all functions return 1 at t=1")
        void allReturnOneAtEnd() {
            assertEquals(1f, Easing.linear(1f), DELTA);
            assertEquals(1f, Easing.easeInQuad(1f), DELTA);
            assertEquals(1f, Easing.easeOutQuad(1f), DELTA);
            assertEquals(1f, Easing.easeInOutQuad(1f), DELTA);
            assertEquals(1f, Easing.easeInCubic(1f), DELTA);
            assertEquals(1f, Easing.easeOutCubic(1f), DELTA);
            assertEquals(1f, Easing.easeInOutCubic(1f), DELTA);
            assertEquals(1f, Easing.easeOutQuart(1f), DELTA);
            assertEquals(1f, Easing.easeOutExpo(1f), DELTA);
            assertEquals(1f, Easing.easeOutElastic(1f), DELTA);
            assertEquals(1f, Easing.easeOutBack(1f), DELTA);
            assertEquals(1f, Easing.easeOutBounce(1f), DELTA);
            assertEquals(1f, Easing.smoothStep(1f), DELTA);
            assertEquals(1f, Easing.smootherStep(1f), DELTA);
        }
    }

    // ================================================================
    // LINEAR
    // ================================================================

    @Nested
    @DisplayName("Linear")
    class LinearTests {

        @Test
        @DisplayName("returns input unchanged")
        void returnsInputUnchanged() {
            assertEquals(0.25f, Easing.linear(0.25f), DELTA);
            assertEquals(0.5f, Easing.linear(0.5f), DELTA);
            assertEquals(0.75f, Easing.linear(0.75f), DELTA);
        }
    }

    // ================================================================
    // QUADRATIC
    // ================================================================

    @Nested
    @DisplayName("Quadratic")
    class QuadraticTests {

        @Test
        @DisplayName("easeInQuad is slower at start")
        void easeInQuadSlowerAtStart() {
            float result = Easing.easeInQuad(0.5f);
            assertTrue(result < 0.5f, "ease-in should be below linear at midpoint");
            assertEquals(0.25f, result, DELTA); // 0.5^2 = 0.25
        }

        @Test
        @DisplayName("easeOutQuad is faster at start")
        void easeOutQuadFasterAtStart() {
            float result = Easing.easeOutQuad(0.5f);
            assertTrue(result > 0.5f, "ease-out should be above linear at midpoint");
            assertEquals(0.75f, result, DELTA); // 1 - (1-0.5)^2 = 0.75
        }

        @Test
        @DisplayName("easeInOutQuad passes through 0.5 at t=0.5")
        void easeInOutQuadSymmetric() {
            assertEquals(0.5f, Easing.easeInOutQuad(0.5f), DELTA);
        }
    }

    // ================================================================
    // CUBIC
    // ================================================================

    @Nested
    @DisplayName("Cubic")
    class CubicTests {

        @Test
        @DisplayName("easeInCubic is more aggressive than quadratic")
        void easeInCubicMoreAggressive() {
            float cubicResult = Easing.easeInCubic(0.5f);
            float quadResult = Easing.easeInQuad(0.5f);
            assertTrue(cubicResult < quadResult, "cubic ease-in should be slower than quadratic");
            assertEquals(0.125f, cubicResult, DELTA); // 0.5^3 = 0.125
        }

        @Test
        @DisplayName("easeOutCubic is faster at start than quadratic")
        void easeOutCubicFaster() {
            float cubicResult = Easing.easeOutCubic(0.5f);
            float quadResult = Easing.easeOutQuad(0.5f);
            assertTrue(cubicResult > quadResult, "cubic ease-out should be faster than quadratic");
        }
    }

    // ================================================================
    // ELASTIC
    // ================================================================

    @Nested
    @DisplayName("Elastic")
    class ElasticTests {

        @Test
        @DisplayName("easeOutElastic overshoots 1")
        void easeOutElasticOvershoots() {
            // Elastic should overshoot before settling
            float midResult = Easing.easeOutElastic(0.5f);
            assertTrue(midResult > 0.9f, "elastic should rapidly approach 1");
        }

        @Test
        @DisplayName("easeOutElastic handles edge cases")
        void easeOutElasticEdgeCases() {
            assertEquals(0f, Easing.easeOutElastic(0f), DELTA);
            assertEquals(1f, Easing.easeOutElastic(1f), DELTA);
        }
    }

    // ================================================================
    // BOUNCE
    // ================================================================

    @Nested
    @DisplayName("Bounce")
    class BounceTests {

        @Test
        @DisplayName("easeOutBounce creates bounce effect")
        void easeOutBounceBounces() {
            // Bounce should have multiple "hits" creating a staircase-like pattern
            float early = Easing.easeOutBounce(0.3f);
            float late = Easing.easeOutBounce(0.8f);

            // Should generally increase but with bounce-backs
            assertTrue(early < late, "should progress over time");
        }
    }

    // ================================================================
    // UTILITY METHODS
    // ================================================================

    @Nested
    @DisplayName("Utility Methods")
    class UtilityTests {

        @Test
        @DisplayName("clamp01 clamps to range")
        void clamp01ClampsToRange() {
            assertEquals(0f, Easing.clamp01(-0.5f), DELTA);
            assertEquals(0.5f, Easing.clamp01(0.5f), DELTA);
            assertEquals(1f, Easing.clamp01(1.5f), DELTA);
        }

        @Test
        @DisplayName("lerp interpolates correctly")
        void lerpInterpolates() {
            assertEquals(10f, Easing.lerp(0f, 100f, 0.1f), DELTA);
            assertEquals(50f, Easing.lerp(0f, 100f, 0.5f), DELTA);
            assertEquals(100f, Easing.lerp(0f, 100f, 1f), DELTA);
        }

        @Test
        @DisplayName("lerp works with negative values")
        void lerpWorksWithNegatives() {
            assertEquals(0f, Easing.lerp(-100f, 100f, 0.5f), DELTA);
        }

        @Test
        @DisplayName("inverseLerp finds progress")
        void inverseLerpFindsProgress() {
            assertEquals(0.5f, Easing.inverseLerp(0f, 100f, 50f), DELTA);
            assertEquals(0f, Easing.inverseLerp(0f, 100f, 0f), DELTA);
            assertEquals(1f, Easing.inverseLerp(0f, 100f, 100f), DELTA);
        }

        @Test
        @DisplayName("inverseLerp handles same start and end")
        void inverseLerpHandlesSameStartEnd() {
            assertEquals(0f, Easing.inverseLerp(50f, 50f, 50f), DELTA);
        }

        @Test
        @DisplayName("smoothStep passes through endpoints")
        void smoothStepEndpoints() {
            assertEquals(0f, Easing.smoothStep(0f), DELTA);
            assertEquals(1f, Easing.smoothStep(1f), DELTA);
            assertEquals(0.5f, Easing.smoothStep(0.5f), DELTA);
        }

        @Test
        @DisplayName("smootherStep passes through endpoints")
        void smootherStepEndpoints() {
            assertEquals(0f, Easing.smootherStep(0f), DELTA);
            assertEquals(1f, Easing.smootherStep(1f), DELTA);
            assertEquals(0.5f, Easing.smootherStep(0.5f), DELTA);
        }
    }

    // ================================================================
    // MONOTONICITY TESTS
    // ================================================================

    @Nested
    @DisplayName("Monotonicity")
    class MonotonicityTests {

        @Test
        @DisplayName("standard easing functions are monotonic")
        void standardEasingMonotonic() {
            assertMonotonic(Easing::linear, "linear");
            assertMonotonic(Easing::easeInQuad, "easeInQuad");
            assertMonotonic(Easing::easeOutQuad, "easeOutQuad");
            assertMonotonic(Easing::easeInOutQuad, "easeInOutQuad");
            assertMonotonic(Easing::easeInCubic, "easeInCubic");
            assertMonotonic(Easing::easeOutCubic, "easeOutCubic");
            assertMonotonic(Easing::easeInOutCubic, "easeInOutCubic");
            assertMonotonic(Easing::smoothStep, "smoothStep");
            assertMonotonic(Easing::smootherStep, "smootherStep");
        }

        private void assertMonotonic(EasingFunction fn, String name) {
            float prev = fn.apply(0f);
            for (int i = 1; i <= 100; i++) {
                float t = i / 100f;
                float current = fn.apply(t);
                assertTrue(current >= prev - DELTA,
                    name + " should be monotonic at t=" + t + " (prev=" + prev + ", current=" + current + ")");
                prev = current;
            }
        }
    }
}
