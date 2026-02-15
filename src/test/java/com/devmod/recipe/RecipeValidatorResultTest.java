package com.devmod.recipe;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("RecipeValidator.ValidationResult")
class RecipeValidatorResultTest {

    // ========================================================================
    // Factory Methods
    // ========================================================================

    @Nested
    @DisplayName("Factory Methods")
    class FactoryTests {

        @Test
        @DisplayName("ok() creates valid result with no errors or warnings")
        void okCreatesValidResult() {
            var result = RecipeValidator.ValidationResult.ok();
            assertTrue(result.valid());
            assertTrue(result.errors().isEmpty());
            assertTrue(result.warnings().isEmpty());
            assertFalse(result.hasWarnings());
        }

        @Test
        @DisplayName("error() creates invalid result with errors")
        void errorCreatesInvalidResult() {
            var result = RecipeValidator.ValidationResult.error("Missing ID", "No result");
            assertFalse(result.valid());
            assertEquals(2, result.errors().size());
            assertEquals("Missing ID", result.getFirstError());
        }

        @Test
        @DisplayName("withWarnings() creates valid result with warnings")
        void withWarningsCreatesValidWithWarnings() {
            var result = RecipeValidator.ValidationResult.withWarnings(
                List.of("Unused key", "Long cooking time"));
            assertTrue(result.valid());
            assertTrue(result.hasWarnings());
            assertEquals(2, result.warnings().size());
        }
    }

    // ========================================================================
    // Query Methods
    // ========================================================================

    @Nested
    @DisplayName("Query Methods")
    class QueryTests {

        @Test
        @DisplayName("getFirstError returns null when no errors")
        void getFirstErrorReturnsNullWhenEmpty() {
            var result = RecipeValidator.ValidationResult.ok();
            assertNull(result.getFirstError());
        }

        @Test
        @DisplayName("getFirstError returns first error")
        void getFirstErrorReturnsFirst() {
            var result = RecipeValidator.ValidationResult.error("First", "Second");
            assertEquals("First", result.getFirstError());
        }

        @Test
        @DisplayName("hasWarnings returns false when no warnings")
        void hasWarningsReturnsFalse() {
            var result = RecipeValidator.ValidationResult.ok();
            assertFalse(result.hasWarnings());
        }

        @Test
        @DisplayName("hasWarnings returns true when warnings present")
        void hasWarningsReturnsTrue() {
            var result = RecipeValidator.ValidationResult.withWarnings(List.of("warn"));
            assertTrue(result.hasWarnings());
        }
    }

    // ========================================================================
    // getSummary
    // ========================================================================

    @Nested
    @DisplayName("getSummary")
    class SummaryTests {

        @Test
        @DisplayName("Valid with no warnings returns 'Valid'")
        void validNoWarnings() {
            var result = RecipeValidator.ValidationResult.ok();
            assertEquals("Valid", result.getSummary());
        }

        @Test
        @DisplayName("Valid with warnings returns warning count")
        void validWithWarnings() {
            var result = RecipeValidator.ValidationResult.withWarnings(List.of("w1", "w2"));
            assertTrue(result.getSummary().contains("2 warning(s)"));
        }

        @Test
        @DisplayName("Invalid returns error count")
        void invalidReturnsErrorCount() {
            var result = RecipeValidator.ValidationResult.error("e1", "e2", "e3");
            assertTrue(result.getSummary().contains("3 error(s)"));
        }
    }

    // ========================================================================
    // isValidId
    // ========================================================================

    @Nested
    @DisplayName("isValidId")
    class IsValidIdTests {

        @Test
        @DisplayName("Valid IDs pass")
        void validIdsPass() {
            assertTrue(RecipeValidator.isValidId("minecraft:diamond"));
            assertTrue(RecipeValidator.isValidId("devmod:custom_recipe"));
            assertTrue(RecipeValidator.isValidId("modid:path/to/item"));
        }

        @Test
        @DisplayName("Null ID fails")
        void nullIdFails() {
            assertFalse(RecipeValidator.isValidId(null));
        }

        @Test
        @DisplayName("IDs with uppercase fail")
        void uppercaseIdsFail() {
            assertFalse(RecipeValidator.isValidId("Minecraft:Diamond"));
        }

        @Test
        @DisplayName("IDs with spaces fail")
        void spacesIdsFail() {
            assertFalse(RecipeValidator.isValidId("minecraft:diamond sword"));
        }

        @Test
        @DisplayName("Empty ID fails")
        void emptyIdFails() {
            assertFalse(RecipeValidator.isValidId(""));
        }

        @Test
        @DisplayName("ID without colon fails")
        void noColonFails() {
            assertFalse(RecipeValidator.isValidId("diamond"));
        }
    }
}
