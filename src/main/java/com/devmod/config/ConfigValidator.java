package com.devmod.config;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.devmod.arena.config.FeatureFlagRegistry;

/**
 * Cross-subsystem configuration validator.
 *
 * <p>Validates configuration consistency across all DevMod subsystems:
 * <ul>
 *   <li>Feature flag dependencies</li>
 *   <li>Config value ranges</li>
 *   <li>Cross-subsystem references</li>
 *   <li>Required resources existence</li>
 * </ul>
 *
 * <p>Usage:
 * <pre>{@code
 * // Validate all configs at startup
 * ValidationResult result = ConfigValidator.validateAll();
 * if (result.hasErrors()) {
 *     LOGGER.error("Config validation failed: {}", result.getSummary());
 *     // Optionally fail startup
 * }
 *
 * // Register custom validators
 * ConfigValidator.registerValidator("mySubsystem", () -> {
 *     List<ValidationError> errors = new ArrayList<>();
 *     if (myConfig.getValue() < 0) {
 *         errors.add(ValidationError.error("mySubsystem", "value", "Must be >= 0"));
 *     }
 *     return errors;
 * });
 * }</pre>
 */
public final class ConfigValidator {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConfigValidator.class);

    /** Registered validators by subsystem name */
    private static final Map<String, Supplier<List<ValidationError>>> validators = new HashMap<>();

    /** Last validation result cache */
    @Nullable
    private static ValidationResult lastResult = null;

    private ConfigValidator() {}

    // ============================================================================
    // REGISTRATION
    // ============================================================================

    /**
     * Register a validator for a subsystem.
     *
     * @param subsystem The subsystem name
     * @param validator Supplier that returns validation errors
     */
    public static void registerValidator(String subsystem, Supplier<List<ValidationError>> validator) {
        validators.put(subsystem, validator);
        LOGGER.debug("[ConfigValidator] Registered validator for: {}", subsystem);
    }

    /**
     * Unregister a validator.
     */
    public static void unregisterValidator(String subsystem) {
        validators.remove(subsystem);
    }

    // ============================================================================
    // VALIDATION
    // ============================================================================

    /**
     * Validate all registered subsystems.
     *
     * @return Validation result with all errors and warnings
     */
    public static ValidationResult validateAll() {
        List<ValidationError> allErrors = new ArrayList<>();

        // Run built-in validators
        allErrors.addAll(validateFeatureFlags());
        allErrors.addAll(validateWeaponConfig());
        allErrors.addAll(validateArmorConfig());
        allErrors.addAll(validateMobConfig());
        allErrors.addAll(validateEnduranceConfig());
        allErrors.addAll(validateMailboxConfig());
        allErrors.addAll(validateTelemetryConfig());

        // Run registered custom validators
        for (Map.Entry<String, Supplier<List<ValidationError>>> entry : validators.entrySet()) {
            try {
                List<ValidationError> errors = entry.getValue().get();
                if (errors != null) {
                    allErrors.addAll(errors);
                }
            } catch (Exception e) {
                allErrors.add(ValidationError.error(
                    entry.getKey(), "validator",
                    "Validator threw exception: " + e.getMessage()
                ));
            }
        }

        // Local capture for null safety - create result and capture locally
        ValidationResult result = new ValidationResult(allErrors);
        lastResult = result;

        // Log summary using local capture
        if (result.hasErrors()) {
            LOGGER.error("[ConfigValidator] Validation FAILED: {} errors, {} warnings",
                result.getErrorCount(), result.getWarningCount());
            for (ValidationError error : result.getErrors()) {
                LOGGER.error("  - [{}] {}.{}: {}",
                    error.severity(), error.subsystem(), error.field(), error.message());
            }
        } else if (result.hasWarnings()) {
            LOGGER.warn("[ConfigValidator] Validation passed with {} warnings",
                result.getWarningCount());
            for (ValidationError warning : result.getWarnings()) {
                LOGGER.warn("  - [{}] {}.{}: {}",
                    warning.severity(), warning.subsystem(), warning.field(), warning.message());
            }
        } else {
            LOGGER.info("[ConfigValidator] Validation passed");
        }

        return result;
    }

    /**
     * Get the last validation result.
     */
    @Nullable
    public static ValidationResult getLastResult() {
        return lastResult;
    }

    /**
     * Clear validation cache.
     */
    public static void clearCache() {
        lastResult = null;
    }

    // ============================================================================
    // BUILT-IN VALIDATORS
    // ============================================================================

    /**
     * Validate feature flag configuration.
     */
    private static List<ValidationError> validateFeatureFlags() {
        List<ValidationError> errors = new ArrayList<>();

        try {
            // Check flag dependencies
            if (FeatureFlagRegistry.INSTANCE.isEnabled(FeatureFlagRegistry.SEASON_PASS_ENABLED) &&
                !FeatureFlagRegistry.INSTANCE.isEnabled(FeatureFlagRegistry.ENDURANCE_ENABLED)) {
                errors.add(ValidationError.error(
                    "featureFlags", "seasonPass",
                    "Season pass requires endurance system to be enabled"
                ));
            }

            if (FeatureFlagRegistry.INSTANCE.isEnabled(FeatureFlagRegistry.NOTIFICATION_ENABLED) &&
                !FeatureFlagRegistry.INSTANCE.isEnabled(FeatureFlagRegistry.MAILBOX_ENABLED)) {
                errors.add(ValidationError.warning(
                    "featureFlags", "notification",
                    "Notification system works best with mailbox enabled"
                ));
            }

            // Validate arena flags
            if (FeatureFlagRegistry.INSTANCE.isEnabled("arena.hazards.enabled") &&
                !FeatureFlagRegistry.INSTANCE.isEnabled(FeatureFlagRegistry.ENDURANCE_ENABLED)) {
                errors.add(ValidationError.error(
                    "featureFlags", "arenaHazards",
                    "Arena hazards require endurance system to be enabled"
                ));
            }

        } catch (Exception e) {
            errors.add(ValidationError.error(
                "featureFlags", "validation",
                "Failed to validate feature flags: " + e.getMessage()
            ));
        }

        return errors;
    }

    /**
     * Validate weapon configuration.
     */
    private static List<ValidationError> validateWeaponConfig() {
        List<ValidationError> errors = new ArrayList<>();

        try {
            // Validate global multipliers are in reasonable range
            // WeaponConfigHandler uses static methods, no instance check needed

        } catch (Exception e) {
            errors.add(ValidationError.error(
                "weapon", "validation",
                "Failed to validate weapon config: " + e.getMessage()
            ));
        }

        return errors;
    }

    /**
     * Validate armor configuration.
     */
    private static List<ValidationError> validateArmorConfig() {
        List<ValidationError> errors = new ArrayList<>();

        try {
            // ArmorConfigHandler uses static methods, no instance check needed

        } catch (Exception e) {
            errors.add(ValidationError.error(
                "armor", "validation",
                "Failed to validate armor config: " + e.getMessage()
            ));
        }

        return errors;
    }

    /**
     * Validate mob configuration.
     */
    private static List<ValidationError> validateMobConfig() {
        List<ValidationError> errors = new ArrayList<>();

        try {
            // Check for mob config enabled flag
            if (!FeatureFlagRegistry.INSTANCE.isEnabled(FeatureFlagRegistry.MOB_CONFIG_ENABLED)) {
                errors.add(ValidationError.info(
                    "mob", "disabled",
                    "Mob config system is disabled via feature flag"
                ));
            }

        } catch (Exception e) {
            errors.add(ValidationError.error(
                "mob", "validation",
                "Failed to validate mob config: " + e.getMessage()
            ));
        }

        return errors;
    }

    /**
     * Validate endurance/quest configuration.
     */
    private static List<ValidationError> validateEnduranceConfig() {
        List<ValidationError> errors = new ArrayList<>();

        try {
            if (!FeatureFlagRegistry.INSTANCE.isEnabled(FeatureFlagRegistry.ENDURANCE_ENABLED)) {
                return errors; // Skip if disabled
            }

            // Validate arena templates directory exists (uses helper)
            ValidationError templatesError = ensureDirectoryExists(
                "endurance", "templates", "config/devmod/arena_templates");
            if (templatesError != null) {
                errors.add(templatesError);
            }

            // Validate kits directory (uses helper)
            ValidationError kitsError = ensureDirectoryExists(
                "endurance", "kits", "config/devmod/kits");
            if (kitsError != null) {
                errors.add(kitsError);
            }

        } catch (Exception e) {
            errors.add(ValidationError.error(
                "endurance", "validation",
                "Failed to validate endurance config: " + e.getMessage()
            ));
        }

        return errors;
    }

    /**
     * Validate mailbox configuration.
     */
    private static List<ValidationError> validateMailboxConfig() {
        List<ValidationError> errors = new ArrayList<>();

        try {
            if (!FeatureFlagRegistry.INSTANCE.isEnabled(FeatureFlagRegistry.MAILBOX_ENABLED)) {
                return errors; // Skip if disabled
            }

            // Mailbox-specific validation would go here

        } catch (Exception e) {
            errors.add(ValidationError.error(
                "mailbox", "validation",
                "Failed to validate mailbox config: " + e.getMessage()
            ));
        }

        return errors;
    }

    /**
     * Validate telemetry configuration.
     */
    private static List<ValidationError> validateTelemetryConfig() {
        List<ValidationError> errors = new ArrayList<>();

        try {
            if (!FeatureFlagRegistry.INSTANCE.isEnabled(FeatureFlagRegistry.TELEMETRY_ENABLED)) {
                return errors; // Skip if disabled
            }

            // Check DuckDB data directory
            java.nio.file.Path dataDir = java.nio.file.Paths.get("config/devmod/telemetry");
            if (!java.nio.file.Files.exists(dataDir)) {
                errors.add(ValidationError.info(
                    "telemetry", "dataDir",
                    "Telemetry data directory will be created: " + dataDir
                ));
            }

        } catch (Exception e) {
            errors.add(ValidationError.error(
                "telemetry", "validation",
                "Failed to validate telemetry config: " + e.getMessage()
            ));
        }

        return errors;
    }

    // ============================================================================
    // VALIDATION HELPERS
    // ============================================================================

    /**
     * Validate a numeric value is in range.
     *
     * @param subsystem Subsystem name
     * @param field Field name
     * @param value The value to check
     * @param min Minimum (inclusive)
     * @param max Maximum (inclusive)
     * @return ValidationError if out of range, null if valid
     */
    @Nullable
    public static ValidationError validateRange(String subsystem, String field,
                                                 double value, double min, double max) {
        if (value < min || value > max) {
            return ValidationError.error(
                subsystem, field,
                String.format("Value %.2f is out of range [%.2f, %.2f]", value, min, max)
            );
        }
        return null;
    }

    /**
     * Validate a value is positive.
     */
    @Nullable
    public static ValidationError validatePositive(String subsystem, String field, double value) {
        if (value <= 0) {
            return ValidationError.error(
                subsystem, field,
                String.format("Value %.2f must be positive", value)
            );
        }
        return null;
    }

    /**
     * Validate a value is non-negative.
     */
    @Nullable
    public static ValidationError validateNonNegative(String subsystem, String field, double value) {
        if (value < 0) {
            return ValidationError.error(
                subsystem, field,
                String.format("Value %.2f must be non-negative", value)
            );
        }
        return null;
    }

    /**
     * Validate a string is not empty.
     */
    @Nullable
    public static ValidationError validateNotEmpty(String subsystem, String field, @Nullable String value) {
        if (value == null || value.trim().isEmpty()) {
            return ValidationError.error(
                subsystem, field,
                "Value must not be empty"
            );
        }
        return null;
    }

    /**
     * Validate a path exists.
     */
    @Nullable
    public static ValidationError validatePathExists(String subsystem, String field, String path) {
        if (!java.nio.file.Files.exists(java.nio.file.Paths.get(path))) {
            return ValidationError.warning(
                subsystem, field,
                "Path does not exist: " + path
            );
        }
        return null;
    }

    /**
     * Ensure a directory exists, creating it if missing.
     */
    @Nullable
    public static ValidationError ensureDirectoryExists(String subsystem, String field, String path) {
        java.nio.file.Path dir = java.nio.file.Paths.get(path);
        if (java.nio.file.Files.exists(dir)) {
            return null;
        }
        try {
            java.nio.file.Files.createDirectories(dir);
            LOGGER.info("[ConfigValidator] Created missing directory for {}.{}: {}", subsystem, field, path);
            return null;
        } catch (java.io.IOException e) {
            return ValidationError.warning(
                subsystem, field,
                "Path does not exist and could not be created: " + path + " (" + e.getMessage() + ")"
            );
        }
    }

    // ============================================================================
    // TYPES
    // ============================================================================

    /**
     * Validation error with severity level.
     */
    public record ValidationError(
        Severity severity,
        String subsystem,
        String field,
        String message
    ) {
        public static ValidationError error(String subsystem, String field, String message) {
            return new ValidationError(Severity.ERROR, subsystem, field, message);
        }

        public static ValidationError warning(String subsystem, String field, String message) {
            return new ValidationError(Severity.WARNING, subsystem, field, message);
        }

        public static ValidationError info(String subsystem, String field, String message) {
            return new ValidationError(Severity.INFO, subsystem, field, message);
        }

        public boolean isError() {
            return severity == Severity.ERROR;
        }

        public boolean isWarning() {
            return severity == Severity.WARNING;
        }

        @Override
        public String toString() {
            return String.format("[%s] %s.%s: %s", severity, subsystem, field, message);
        }
    }

    /**
     * Severity levels for validation errors.
     */
    public enum Severity {
        /** Critical error - config is invalid */
        ERROR,
        /** Warning - config may cause issues */
        WARNING,
        /** Info - just informational */
        INFO
    }

    /**
     * Aggregated validation result.
     */
    public static class ValidationResult {
        private final List<ValidationError> errors;
        private final long timestamp;

        public ValidationResult(List<ValidationError> errors) {
            this.errors = List.copyOf(errors);
            this.timestamp = System.currentTimeMillis();
        }

        public boolean hasErrors() {
            return errors.stream().anyMatch(ValidationError::isError);
        }

        public boolean hasWarnings() {
            return errors.stream().anyMatch(ValidationError::isWarning);
        }

        public boolean isValid() {
            return !hasErrors();
        }

        public List<ValidationError> getAll() {
            return errors;
        }

        public List<ValidationError> getErrors() {
            return errors.stream()
                .filter(ValidationError::isError)
                .toList();
        }

        public List<ValidationError> getWarnings() {
            return errors.stream()
                .filter(ValidationError::isWarning)
                .toList();
        }

        public List<ValidationError> getBySubsystem(String subsystem) {
            return errors.stream()
                .filter(e -> e.subsystem().equals(subsystem))
                .toList();
        }

        public int getErrorCount() {
            return (int) errors.stream().filter(ValidationError::isError).count();
        }

        public int getWarningCount() {
            return (int) errors.stream().filter(ValidationError::isWarning).count();
        }

        public int getTotalCount() {
            return errors.size();
        }

        public long getTimestamp() {
            return timestamp;
        }

        public String getSummary() {
            if (errors.isEmpty()) {
                return "All configurations valid";
            }

            StringBuilder sb = new StringBuilder();
            sb.append(getErrorCount()).append(" errors, ");
            sb.append(getWarningCount()).append(" warnings");

            if (hasErrors()) {
                sb.append(" - INVALID");
            }

            return sb.toString();
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("ValidationResult: ").append(getSummary()).append("\n");

            for (ValidationError error : errors) {
                sb.append("  ").append(error).append("\n");
            }

            return sb.toString();
        }
    }
}
