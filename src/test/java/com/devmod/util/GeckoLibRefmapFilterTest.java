package com.devmod.util;

import java.lang.reflect.Method;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests the shouldFilter logic of GeckoLibRefmapFilter via reflection,
 * since the method is private static.
 */
@DisplayName("GeckoLibRefmapFilter")
class GeckoLibRefmapFilterTest {

    private static boolean shouldFilter(String loggerName, String message) throws Exception {
        Method m = GeckoLibRefmapFilter.class.getDeclaredMethod("shouldFilter", String.class, String.class);
        m.setAccessible(true);
        return (boolean) m.invoke(null, loggerName, message);
    }

    @Nested
    @DisplayName("shouldFilter")
    class ShouldFilterTests {

        @Test
        @DisplayName("filters mixin logger with geckolib.refmap.json message")
        void filtersMixinRefmap() throws Exception {
            assertTrue(shouldFilter(
                "org.spongepowered.mixin",
                "Could not find geckolib.refmap.json in resources"));
        }

        @Test
        @DisplayName("does not filter non-mixin logger")
        void doesNotFilterNonMixin() throws Exception {
            assertFalse(shouldFilter(
                "net.minecraft.server",
                "geckolib.refmap.json not found"));
        }

        @Test
        @DisplayName("does not filter mixin logger without refmap token")
        void doesNotFilterWithoutToken() throws Exception {
            assertFalse(shouldFilter(
                "org.spongepowered.mixin",
                "Some other warning message"));
        }

        @Test
        @DisplayName("handles null loggerName gracefully")
        void handlesNullLoggerName() throws Exception {
            assertFalse(shouldFilter(null, "geckolib.refmap.json"));
        }

        @Test
        @DisplayName("handles null message gracefully")
        void handlesNullMessage() throws Exception {
            assertFalse(shouldFilter("org.spongepowered.mixin", null));
        }

        @Test
        @DisplayName("case insensitive mixin check in logger name")
        void caseInsensitiveMixin() throws Exception {
            assertTrue(shouldFilter(
                "org.spongepowered.MIXIN.transformer",
                "Warning about geckolib.refmap.json"));
        }
    }
}
