package com.devmod.config;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.devmod.config.ConfigValidator.Severity;
import com.devmod.config.ConfigValidator.ValidationError;
import com.devmod.config.ConfigValidator.ValidationResult;

/**
 * Tests for ConfigValidator.
 */
class ConfigValidatorTest {

    @BeforeEach
    void setUp() {
        ConfigValidator.clearCache();
    }

    @AfterEach
    void tearDown() {
        ConfigValidator.clearCache();
    }

    // ============================================================================
    // VALIDATION ERROR
    // ============================================================================

    @Nested
    @DisplayName("ValidationError")
    class ValidationErrorTests {

        @Test
        @DisplayName("Should create error with ERROR severity")
        void shouldCreateErrorSeverity() {
            ValidationError error = ValidationError.error("subsystem", "field", "message");

            assertEquals(Severity.ERROR, error.severity());
            assertEquals("subsystem", error.subsystem());
            assertEquals("field", error.field());
            assertEquals("message", error.message());
            assertTrue(error.isError());
            assertFalse(error.isWarning());
        }

        @Test
        @DisplayName("Should create warning with WARNING severity")
        void shouldCreateWarningSeverity() {
            ValidationError warning = ValidationError.warning("subsystem", "field", "message");

            assertEquals(Severity.WARNING, warning.severity());
            assertTrue(warning.isWarning());
            assertFalse(warning.isError());
        }

        @Test
        @DisplayName("Should create info with INFO severity")
        void shouldCreateInfoSeverity() {
            ValidationError info = ValidationError.info("subsystem", "field", "message");

            assertEquals(Severity.INFO, info.severity());
            assertFalse(info.isError());
            assertFalse(info.isWarning());
        }

        @Test
        @DisplayName("Should format toString correctly")
        void shouldFormatToString() {
            ValidationError error = ValidationError.error("weapon", "damage", "Value out of range");

            String str = error.toString();

            assertTrue(str.contains("ERROR"));
            assertTrue(str.contains("weapon"));
            assertTrue(str.contains("damage"));
            assertTrue(str.contains("Value out of range"));
        }
    }

    // ============================================================================
    // VALIDATION RESULT
    // ============================================================================

    @Nested
    @DisplayName("ValidationResult")
    class ValidationResultTests {

        @Test
        @DisplayName("Should report no errors when empty")
        void shouldReportNoErrorsWhenEmpty() {
            ValidationResult result = new ValidationResult(List.of());

            assertFalse(result.hasErrors());
            assertFalse(result.hasWarnings());
            assertTrue(result.isValid());
            assertEquals(0, result.getErrorCount());
            assertEquals(0, result.getWarningCount());
            assertEquals(0, result.getTotalCount());
        }

        @Test
        @DisplayName("Should report errors correctly")
        void shouldReportErrorsCorrectly() {
            List<ValidationError> errors = List.of(
                ValidationError.error("sub1", "field1", "Error 1"),
                ValidationError.error("sub2", "field2", "Error 2"),
                ValidationError.warning("sub3", "field3", "Warning 1")
            );

            ValidationResult result = new ValidationResult(errors);

            assertTrue(result.hasErrors());
            assertTrue(result.hasWarnings());
            assertFalse(result.isValid());
            assertEquals(2, result.getErrorCount());
            assertEquals(1, result.getWarningCount());
            assertEquals(3, result.getTotalCount());
        }

        @Test
        @DisplayName("Should filter errors by subsystem")
        void shouldFilterBySubsystem() {
            List<ValidationError> errors = List.of(
                ValidationError.error("weapon", "damage", "Error 1"),
                ValidationError.error("weapon", "speed", "Error 2"),
                ValidationError.error("armor", "defense", "Error 3")
            );

            ValidationResult result = new ValidationResult(errors);

            List<ValidationError> weaponErrors = result.getBySubsystem("weapon");
            List<ValidationError> armorErrors = result.getBySubsystem("armor");

            assertEquals(2, weaponErrors.size());
            assertEquals(1, armorErrors.size());
        }

        @Test
        @DisplayName("Should separate errors and warnings")
        void shouldSeparateErrorsAndWarnings() {
            List<ValidationError> all = List.of(
                ValidationError.error("sub", "f1", "Error"),
                ValidationError.warning("sub", "f2", "Warning"),
                ValidationError.info("sub", "f3", "Info")
            );

            ValidationResult result = new ValidationResult(all);

            assertEquals(1, result.getErrors().size());
            assertEquals(1, result.getWarnings().size());
            assertEquals(3, result.getAll().size());
        }

        @Test
        @DisplayName("Should have timestamp")
        void shouldHaveTimestamp() {
            long before = System.currentTimeMillis();
            ValidationResult result = new ValidationResult(List.of());
            long after = System.currentTimeMillis();

            assertTrue(result.getTimestamp() >= before);
            assertTrue(result.getTimestamp() <= after);
        }

        @Test
        @DisplayName("Should generate summary")
        void shouldGenerateSummary() {
            List<ValidationError> errors = List.of(
                ValidationError.error("sub", "f1", "Error"),
                ValidationError.warning("sub", "f2", "Warning")
            );

            ValidationResult result = new ValidationResult(errors);
            String summary = result.getSummary();

            assertNotNull(summary);
            assertTrue(summary.contains("1 errors"));
            assertTrue(summary.contains("1 warnings"));
            assertTrue(summary.contains("INVALID"));
        }

        @Test
        @DisplayName("Valid result should not contain INVALID")
        void validResultShouldNotContainInvalid() {
            ValidationResult result = new ValidationResult(List.of(
                ValidationError.warning("sub", "field", "Just a warning")
            ));

            assertTrue(result.isValid());
            assertFalse(result.getSummary().contains("INVALID"));
        }
    }

    // ============================================================================
    // VALIDATION HELPERS
    // ============================================================================

    @Nested
    @DisplayName("Validation Helpers")
    class ValidationHelpers {

        @Test
        @DisplayName("validateRange should return null for valid values")
        void validateRangeShouldReturnNullForValid() {
            assertNull(ConfigValidator.validateRange("sub", "field", 5.0, 0.0, 10.0));
            assertNull(ConfigValidator.validateRange("sub", "field", 0.0, 0.0, 10.0));
            assertNull(ConfigValidator.validateRange("sub", "field", 10.0, 0.0, 10.0));
        }

        @Test
        @DisplayName("validateRange should return error for out of range")
        void validateRangeShouldReturnErrorForOutOfRange() {
            ValidationError error1 = ConfigValidator.validateRange("sub", "field", -1.0, 0.0, 10.0);
            ValidationError error2 = ConfigValidator.validateRange("sub", "field", 11.0, 0.0, 10.0);

            assertNotNull(error1);
            assertNotNull(error2);
            assertTrue(error1.isError());
            assertTrue(error2.isError());
        }

        @Test
        @DisplayName("validatePositive should work correctly")
        void validatePositiveShouldWork() {
            assertNull(ConfigValidator.validatePositive("sub", "field", 1.0));
            assertNull(ConfigValidator.validatePositive("sub", "field", 0.001));

            assertNotNull(ConfigValidator.validatePositive("sub", "field", 0.0));
            assertNotNull(ConfigValidator.validatePositive("sub", "field", -1.0));
        }

        @Test
        @DisplayName("validateNonNegative should work correctly")
        void validateNonNegativeShouldWork() {
            assertNull(ConfigValidator.validateNonNegative("sub", "field", 0.0));
            assertNull(ConfigValidator.validateNonNegative("sub", "field", 1.0));

            assertNotNull(ConfigValidator.validateNonNegative("sub", "field", -0.001));
        }

        @Test
        @DisplayName("validateNotEmpty should work correctly")
        void validateNotEmptyShouldWork() {
            assertNull(ConfigValidator.validateNotEmpty("sub", "field", "value"));
            assertNull(ConfigValidator.validateNotEmpty("sub", "field", " value "));

            assertNotNull(ConfigValidator.validateNotEmpty("sub", "field", null));
            assertNotNull(ConfigValidator.validateNotEmpty("sub", "field", ""));
            assertNotNull(ConfigValidator.validateNotEmpty("sub", "field", "   "));
        }
    }

    // ============================================================================
    // CUSTOM VALIDATORS
    // ============================================================================

    @Nested
    @DisplayName("Custom Validators")
    class CustomValidators {

        @Test
        @DisplayName("Should register and run custom validator")
        void shouldRegisterAndRunCustomValidator() {
            // Given a custom validator
            ConfigValidator.registerValidator("mySubsystem", () -> {
                List<ValidationError> errors = new ArrayList<>();
                errors.add(ValidationError.warning("mySubsystem", "testField", "Custom warning"));
                return errors;
            });

            // When validating
            ValidationResult result = ConfigValidator.validateAll();

            // Then custom validator errors should be included
            List<ValidationError> myErrors = result.getBySubsystem("mySubsystem");
            assertFalse(myErrors.isEmpty());
            assertEquals("Custom warning", myErrors.get(0).message());

            // Cleanup
            ConfigValidator.unregisterValidator("mySubsystem");
        }

        @Test
        @DisplayName("Should handle validator that throws exception")
        void shouldHandleValidatorException() {
            // Given a validator that throws
            ConfigValidator.registerValidator("throwingValidator", () -> {
                throw new RuntimeException("Validator exploded");
            });

            // When validating
            ValidationResult result = ConfigValidator.validateAll();

            // Then should capture the exception as an error
            List<ValidationError> errors = result.getBySubsystem("throwingValidator");
            assertFalse(errors.isEmpty());
            assertTrue(errors.get(0).message().contains("exception"));

            // Cleanup
            ConfigValidator.unregisterValidator("throwingValidator");
        }

        @Test
        @DisplayName("Should unregister validator")
        void shouldUnregisterValidator() {
            // Given a registered validator
            ConfigValidator.registerValidator("tempValidator", () -> List.of(
                ValidationError.info("tempValidator", "test", "Info")
            ));

            // Verify it runs
            ValidationResult result1 = ConfigValidator.validateAll();
            assertFalse(result1.getBySubsystem("tempValidator").isEmpty());

            // When unregistering
            ConfigValidator.unregisterValidator("tempValidator");

            // Then it should not run
            ConfigValidator.clearCache();
            ValidationResult result2 = ConfigValidator.validateAll();
            assertTrue(result2.getBySubsystem("tempValidator").isEmpty());
        }
    }

    // ============================================================================
    // CACHING
    // ============================================================================

    @Nested
    @DisplayName("Caching")
    class Caching {

        @Test
        @DisplayName("Should cache last result")
        void shouldCacheLastResult() {
            // Given no cached result
            assertNull(ConfigValidator.getLastResult());

            // When validating
            ValidationResult result = ConfigValidator.validateAll();

            // Then result should be cached
            assertSame(result, ConfigValidator.getLastResult());
        }

        @Test
        @DisplayName("Should clear cache")
        void shouldClearCache() {
            // Given a cached result
            ConfigValidator.validateAll();
            assertNotNull(ConfigValidator.getLastResult());

            // When clearing cache
            ConfigValidator.clearCache();

            // Then cache should be empty
            assertNull(ConfigValidator.getLastResult());
        }
    }

    // ============================================================================
    // INTEGRATION
    // ============================================================================

    @Nested
    @DisplayName("Integration")
    class Integration {

        @Test
        @DisplayName("Should run all built-in validators without exception")
        void shouldRunAllBuiltInValidators() {
            // This test ensures the validator doesn't throw during normal operation
            // even if some subsystems are not fully initialized

            assertDoesNotThrow(() -> {
                ValidationResult result = ConfigValidator.validateAll();
                assertNotNull(result);
                assertNotNull(result.getSummary());
            });
        }

        @Test
        @DisplayName("ValidationResult should be immutable")
        void validationResultShouldBeImmutable() {
            List<ValidationError> errors = new ArrayList<>();
            errors.add(ValidationError.error("sub", "field", "Error"));

            ValidationResult result = new ValidationResult(errors);

            // Modify original list
            errors.add(ValidationError.error("sub2", "field2", "Another error"));

            // Result should not change
            assertEquals(1, result.getTotalCount());
        }
    }
}
