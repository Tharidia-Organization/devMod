package com.devmod.clone;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Method;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.devmod.clone.network.CloneNetworkHandler;

/**
 * Tests for CloneNetworkHandler logic, particularly the normalizeAngle utility.
 */
class CloneNetworkHandlerTest {

    /**
     * Access the private normalizeAngle method via reflection for testing.
     */
    private static float normalizeAngle(float angle) {
        try {
            Method method = CloneNetworkHandler.class.getDeclaredMethod("normalizeAngle", float.class);
            method.setAccessible(true);
            return (float) method.invoke(null, angle);
        } catch (Exception e) {
            throw new RuntimeException("Failed to invoke normalizeAngle", e);
        }
    }

    @Nested
    @DisplayName("normalizeAngle")
    class NormalizeAngleTests {

        @Test
        @DisplayName("Zero stays zero")
        void zero() {
            assertEquals(0.0f, normalizeAngle(0.0f), 0.001f);
        }

        @Test
        @DisplayName("180 stays 180")
        void halfTurn() {
            assertEquals(180.0f, normalizeAngle(180.0f), 0.001f);
        }

        @Test
        @DisplayName("359.9 stays 359.9")
        void justBelow360() {
            assertEquals(359.9f, normalizeAngle(359.9f), 0.01f);
        }

        @Test
        @DisplayName("360 wraps to 0")
        void fullTurn() {
            assertEquals(0.0f, normalizeAngle(360.0f), 0.001f);
        }

        @Test
        @DisplayName("720 wraps to 0")
        void doubleTurn() {
            assertEquals(0.0f, normalizeAngle(720.0f), 0.001f);
        }

        @Test
        @DisplayName("Negative angle wraps to positive")
        void negative() {
            float result = normalizeAngle(-90.0f);
            assertEquals(270.0f, result, 0.001f);
        }

        @Test
        @DisplayName("-360 wraps to 0")
        void negativeFullTurn() {
            assertEquals(0.0f, normalizeAngle(-360.0f), 0.001f);
        }

        @ParameterizedTest
        @CsvSource({
            "45.0, 45.0",
            "90.0, 90.0",
            "270.0, 270.0",
            "361.0, 1.0",
            "-1.0, 359.0",
            "-180.0, 180.0",
            "-270.0, 90.0",
            "450.0, 90.0"
        })
        @DisplayName("Various angles normalize correctly")
        void variousAngles(float input, float expected) {
            assertEquals(expected, normalizeAngle(input), 0.01f);
        }

        @Test
        @DisplayName("Result is always in [0, 360)")
        void resultRange() {
            float[] testAngles = {-1000, -360, -180, -90, -1, 0, 1, 90, 180, 270, 359, 360, 500, 1000};
            for (float angle : testAngles) {
                float result = normalizeAngle(angle);
                assertTrue(result >= 0.0f, "Angle " + angle + " -> " + result + " should be >= 0");
                assertTrue(result < 360.0f, "Angle " + angle + " -> " + result + " should be < 360");
            }
        }
    }

    @Nested
    @DisplayName("Handler metadata")
    class HandlerMetadataTests {

        @Test
        @DisplayName("Domain name is 'clone'")
        void domainName() {
            assertEquals("clone", CloneNetworkHandler.INSTANCE.getDomainName());
        }

        @Test
        @DisplayName("INSTANCE is a singleton")
        void singleton() {
            assertNotNull(CloneNetworkHandler.INSTANCE);
            assertSame(CloneNetworkHandler.INSTANCE, CloneNetworkHandler.INSTANCE);
        }
    }
}
