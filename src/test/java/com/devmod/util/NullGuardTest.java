package com.devmod.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("NullGuard")
class NullGuardTest {

    // ========================================================================
    // require(value, context, fieldName)
    // ========================================================================

    @Nested
    @DisplayName("require(value, context, fieldName)")
    class RequireTests {

        @Test
        @DisplayName("returns non-null value unchanged")
        void returnsNonNull() {
            String result = NullGuard.require("hello", "TestContext", "testField");
            assertEquals("hello", result);
        }

        @Test
        @DisplayName("throws NPE with context message when null")
        void throwsOnNull() {
            NullPointerException ex = assertThrows(NullPointerException.class,
                () -> NullGuard.require(null, "PartySync", "memberId"));
            assertTrue(ex.getMessage().contains("PartySync"), "message should contain context");
            assertTrue(ex.getMessage().contains("memberId"), "message should contain field name");
        }

        @Test
        @DisplayName("works with various object types")
        void worksWithVariousTypes() {
            assertEquals(42, NullGuard.require(42, "ctx", "field"));
            java.util.List<?> list = NullGuard.require(java.util.List.of(), "ctx", "field");
            assertNotNull(list);
        }
    }

    // ========================================================================
    // require(value, context, fieldName, extraInfo)
    // ========================================================================

    @Nested
    @DisplayName("require(value, context, fieldName, extraInfo)")
    class RequireWithExtraInfoTests {

        @Test
        @DisplayName("returns non-null value unchanged")
        void returnsNonNull() {
            String result = NullGuard.require("value", "Ctx", "field", "extra");
            assertEquals("value", result);
        }

        @Test
        @DisplayName("throws NPE with context, field, and extra info when null")
        void throwsWithExtraInfo() {
            NullPointerException ex = assertThrows(NullPointerException.class,
                () -> NullGuard.require(null, "WaveManager", "bossEntity", "wave=5"));
            assertTrue(ex.getMessage().contains("WaveManager"));
            assertTrue(ex.getMessage().contains("bossEntity"));
            assertTrue(ex.getMessage().contains("wave=5"));
        }
    }

    // ========================================================================
    // orDefault(value, default, context, fieldName)
    // ========================================================================

    @Nested
    @DisplayName("orDefault")
    class OrDefaultTests {

        @Test
        @DisplayName("returns value when non-null")
        void returnsValueWhenNonNull() {
            String result = NullGuard.orDefault("actual", "fallback", "ctx", "field");
            assertEquals("actual", result);
        }

        @Test
        @DisplayName("returns default when null (with warning)")
        void returnsDefaultWhenNull() {
            String result = NullGuard.orDefault(null, "fallback", "ctx", "field");
            assertEquals("fallback", result);
        }

        @Test
        @DisplayName("default can be null")
        void defaultCanBeNull() {
            // orDefault does not throw if defaultValue is null
            String result = NullGuard.orDefault(null, null, "ctx", "field");
            assertNull(result);
        }
    }

    // ========================================================================
    // orDefaultSilent(value, default)
    // ========================================================================

    @Nested
    @DisplayName("orDefaultSilent")
    class OrDefaultSilentTests {

        @Test
        @DisplayName("returns value when non-null")
        void returnsValueWhenNonNull() {
            assertEquals("hello", NullGuard.orDefaultSilent("hello", "fallback"));
        }

        @Test
        @DisplayName("returns default when null")
        void returnsDefaultWhenNull() {
            assertEquals("fallback", NullGuard.orDefaultSilent(null, "fallback"));
        }

        @Test
        @DisplayName("returns null when both value and default are null")
        void bothNull() {
            assertNull(NullGuard.orDefaultSilent(null, null));
        }

        @Test
        @DisplayName("works with non-string types")
        void worksWithNonStrings() {
            assertEquals(42, NullGuard.orDefaultSilent(null, 42));
            assertEquals(99, NullGuard.orDefaultSilent(99, 42));
        }
    }

}
