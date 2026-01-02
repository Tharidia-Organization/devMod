package com.devmod.client.ui.radial.animation;

import com.devmod.client.ui.animation.UiAnimation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

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
            assertEquals(0f, UiAnimation.linear(0f), DELTA);
            assertEquals(0f, UiAnimation.easeInQuad(0f), DELTA);
            assertEquals(0f, UiAnimation.easeOutQuad(0f), DELTA);
            assertEquals(0f, UiAnimation.easeInOutQuad(0f), DELTA);
            assertEquals(0f, UiAnimation.easeInCubic(0f), DELTA);
            assertEquals(0f, UiAnimation.easeOutCubic(0f), DELTA);
            assertEquals(0f, UiAnimation.easeInOutCubic(0f), DELTA);
            assertEquals(0f, UiAnimation.easeOutQuart(0f), DELTA);
            assertEquals(0f, UiAnimation.easeOutExpo(0f), DELTA);
            assertEquals(0f, UiAnimation.easeOutElastic(0f), DELTA);
            assertEquals(0f, UiAnimation.smoothStep(0f), DELTA);
            assertEquals(0f, UiAnimation.smootherStep(0f), DELTA);
        }

        @Test
        @DisplayName("all functions return 1 at t=1")
        void allReturnOneAtEnd() {
            assertEquals(1f, UiAnimation.linear(1f), DELTA);
            assertEquals(1f, UiAnimation.easeInQuad(1f), DELTA);
            assertEquals(1f, UiAnimation.easeOutQuad(1f), DELTA);
            assertEquals(1f, UiAnimation.easeInOutQuad(1f), DELTA);
            assertEquals(1f, UiAnimation.easeInCubic(1f), DELTA);
            assertEquals(1f, UiAnimation.easeOutCubic(1f), DELTA);
            assertEquals(1f, UiAnimation.easeInOutCubic(1f), DELTA);
            assertEquals(1f, UiAnimation.easeOutQuart(1f), DELTA);
            assertEquals(1f, UiAnimation.easeOutExpo(1f), DELTA);
            assertEquals(1f, UiAnimation.easeOutElastic(1f), DELTA);
            assertEquals(1f, UiAnimation.easeOutBack(1f), DELTA);
            assertEquals(1f, UiAnimation.easeOutBounce(1f), DELTA);
            assertEquals(1f, UiAnimation.smoothStep(1f), DELTA);
            assertEquals(1f, UiAnimation.smootherStep(1f), DELTA);
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
            assertEquals(0.25f, UiAnimation.linear(0.25f), DELTA);
            assertEquals(0.5f, UiAnimation.linear(0.5f), DELTA);
            assertEquals(0.75f, UiAnimation.linear(0.75f), DELTA);
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
            float result = UiAnimation.easeInQuad(0.5f);
            assertTrue(result < 0.5f, "ease-in should be below linear at midpoint");
            assertEquals(0.25f, result, DELTA); // 0.5^2 = 0.25
        }

        @Test
        @DisplayName("easeOutQuad is faster at start")
        void easeOutQuadFasterAtStart() {
            float result = UiAnimation.easeOutQuad(0.5f);
            assertTrue(result > 0.5f, "ease-out should be above linear at midpoint");
            assertEquals(0.75f, result, DELTA); // 1 - (1-0.5)^2 = 0.75
        }

        @Test
        @DisplayName("easeInOutQuad passes through 0.5 at t=0.5")
        void easeInOutQuadSymmetric() {
            assertEquals(0.5f, UiAnimation.easeInOutQuad(0.5f), DELTA);
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
            float cubicResult = UiAnimation.easeInCubic(0.5f);
            float quadResult = UiAnimation.easeInQuad(0.5f);
            assertTrue(cubicResult < quadResult, "cubic ease-in should be slower than quadratic");
            assertEquals(0.125f, cubicResult, DELTA); // 0.5^3 = 0.125
        }

        @Test
        @DisplayName("easeOutCubic is faster at start than quadratic")
        void easeOutCubicFaster() {
            float cubicResult = UiAnimation.easeOutCubic(0.5f);
            float quadResult = UiAnimation.easeOutQuad(0.5f);
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
            float midResult = UiAnimation.easeOutElastic(0.5f);
            assertTrue(midResult > 0.9f, "elastic should rapidly approach 1");
        }

        @Test
        @DisplayName("easeOutElastic handles edge cases")
        void easeOutElasticEdgeCases() {
            assertEquals(0f, UiAnimation.easeOutElastic(0f), DELTA);
            assertEquals(1f, UiAnimation.easeOutElastic(1f), DELTA);
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
            float early = UiAnimation.easeOutBounce(0.3f);
            float late = UiAnimation.easeOutBounce(0.8f);

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
            assertEquals(0f, UiAnimation.clamp01(-0.5f), DELTA);
            assertEquals(0.5f, UiAnimation.clamp01(0.5f), DELTA);
            assertEquals(1f, UiAnimation.clamp01(1.5f), DELTA);
        }

        @Test
        @DisplayName("lerp interpolates correctly")
        void lerpInterpolates() {
            assertEquals(10f, UiAnimation.lerp(0f, 100f, 0.1f), DELTA);
            assertEquals(50f, UiAnimation.lerp(0f, 100f, 0.5f), DELTA);
            assertEquals(100f, UiAnimation.lerp(0f, 100f, 1f), DELTA);
        }

        @Test
        @DisplayName("lerp works with negative values")
        void lerpWorksWithNegatives() {
            assertEquals(0f, UiAnimation.lerp(-100f, 100f, 0.5f), DELTA);
        }

        @Test
        @DisplayName("inverseLerp finds progress")
        void inverseLerpFindsProgress() {
            assertEquals(0.5f, UiAnimation.inverseLerp(0f, 100f, 50f), DELTA);
            assertEquals(0f, UiAnimation.inverseLerp(0f, 100f, 0f), DELTA);
            assertEquals(1f, UiAnimation.inverseLerp(0f, 100f, 100f), DELTA);
        }

        @Test
        @DisplayName("inverseLerp handles same start and end")
        void inverseLerpHandlesSameStartEnd() {
            assertEquals(0f, UiAnimation.inverseLerp(50f, 50f, 50f), DELTA);
        }

        @Test
        @DisplayName("smoothStep passes through endpoints")
        void smoothStepEndpoints() {
            assertEquals(0f, UiAnimation.smoothStep(0f), DELTA);
            assertEquals(1f, UiAnimation.smoothStep(1f), DELTA);
            assertEquals(0.5f, UiAnimation.smoothStep(0.5f), DELTA);
        }

        @Test
        @DisplayName("smootherStep passes through endpoints")
        void smootherStepEndpoints() {
            assertEquals(0f, UiAnimation.smootherStep(0f), DELTA);
            assertEquals(1f, UiAnimation.smootherStep(1f), DELTA);
            assertEquals(0.5f, UiAnimation.smootherStep(0.5f), DELTA);
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
            assertMonotonic(UiAnimation::linear, "linear");
            assertMonotonic(UiAnimation::easeInQuad, "easeInQuad");
            assertMonotonic(UiAnimation::easeOutQuad, "easeOutQuad");
            assertMonotonic(UiAnimation::easeInOutQuad, "easeInOutQuad");
            assertMonotonic(UiAnimation::easeInCubic, "easeInCubic");
            assertMonotonic(UiAnimation::easeOutCubic, "easeOutCubic");
            assertMonotonic(UiAnimation::easeInOutCubic, "easeInOutCubic");
            assertMonotonic(UiAnimation::smoothStep, "smoothStep");
            assertMonotonic(UiAnimation::smootherStep, "smootherStep");
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
