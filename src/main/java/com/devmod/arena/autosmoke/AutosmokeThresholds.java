package com.devmod.arena.autosmoke;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * DD33: Autosmoke Assert Exceptions - soglie per size + whitelist
 *
 * Defines threshold configurations for autosmoke tests.
 * Three preset modes: STRICT, LARGE, ASYNC
 */
public record AutosmokeThresholds(
    /** Maximum players allowed in test arena */
    int maxPlayers,

    /** Maximum arena duration in seconds */
    long maxDurationSeconds,

    /** Maximum memory usage in MB */
    int maxMemoryMb,

    /** Timeout for individual test assertions */
    Duration assertTimeout,

    /** Timeout for entire test run */
    Duration testTimeout,

    /** Whether to allow async operations */
    boolean allowAsync,

    /** Maximum concurrent async operations */
    int maxAsyncOperations,

    /** Threshold mode name */
    String modeName
) {
    /** STRICT mode - tight limits for quick unit tests */
    public static final AutosmokeThresholds STRICT = new AutosmokeThresholds(
        4,                          // maxPlayers
        60,                         // maxDurationSeconds (1 min)
        256,                        // maxMemoryMb
        Duration.ofSeconds(5),      // assertTimeout
        Duration.ofSeconds(30),     // testTimeout
        false,                      // allowAsync
        0,                          // maxAsyncOperations
        "STRICT"
    );

    /** LARGE mode - relaxed limits for integration tests with larger arenas */
    public static final AutosmokeThresholds LARGE = new AutosmokeThresholds(
        32,                         // maxPlayers
        300,                        // maxDurationSeconds (5 min)
        1024,                       // maxMemoryMb
        Duration.ofSeconds(15),     // assertTimeout
        Duration.ofMinutes(5),      // testTimeout
        false,                      // allowAsync
        0,                          // maxAsyncOperations
        "LARGE"
    );

    /** ASYNC mode - allows async operations for stress/load tests */
    public static final AutosmokeThresholds ASYNC = new AutosmokeThresholds(
        64,                         // maxPlayers
        600,                        // maxDurationSeconds (10 min)
        2048,                       // maxMemoryMb
        Duration.ofSeconds(30),     // assertTimeout
        Duration.ofMinutes(10),     // testTimeout
        true,                       // allowAsync
        16,                         // maxAsyncOperations
        "ASYNC"
    );

    /** Template-specific threshold overrides */
    private static final Map<String, AutosmokeThresholds> TEMPLATE_THRESHOLDS = new HashMap<>();

    static {
        // Pre-register some common template overrides
        // (can be extended via registerTemplateThresholds)
    }

    /**
     * Gets the appropriate thresholds for a template
     *
     * @param templateId The template ID (e.g., "devmod:arena_1v1")
     * @return The thresholds to use
     */
    public static AutosmokeThresholds forTemplate(String templateId) {
        // Check for explicit template override
        AutosmokeThresholds override = TEMPLATE_THRESHOLDS.get(templateId);
        if (override != null) {
            return override;
        }

        // Infer from template naming conventions
        if (templateId != null) {
            String lower = templateId.toLowerCase();

            // Large arena templates
            if (lower.contains("large") || lower.contains("mega") ||
                lower.contains("64") || lower.contains("32")) {
                return LARGE;
            }

            // Async/stress test templates
            if (lower.contains("stress") || lower.contains("load") ||
                lower.contains("async") || lower.contains("perf")) {
                return ASYNC;
            }
        }

        // Default to STRICT
        return STRICT;
    }

    /**
     * Registers custom thresholds for a specific template
     *
     * @param templateId The template ID
     * @param thresholds The thresholds to use
     */
    public static void registerTemplateThresholds(String templateId, AutosmokeThresholds thresholds) {
        TEMPLATE_THRESHOLDS.put(templateId, thresholds);
    }

    /**
     * Creates custom thresholds based on STRICT with modifications
     *
     * @param maxPlayers Override max players
     * @param maxDurationSeconds Override max duration
     * @return New thresholds instance
     */
    public static AutosmokeThresholds customStrict(int maxPlayers, long maxDurationSeconds) {
        return new AutosmokeThresholds(
            maxPlayers,
            maxDurationSeconds,
            STRICT.maxMemoryMb,
            STRICT.assertTimeout,
            STRICT.testTimeout,
            STRICT.allowAsync,
            STRICT.maxAsyncOperations,
            "CUSTOM_STRICT"
        );
    }

    /**
     * Creates custom thresholds based on LARGE with modifications
     *
     * @param maxPlayers Override max players
     * @param testTimeoutMinutes Override test timeout in minutes
     * @return New thresholds instance
     */
    public static AutosmokeThresholds customLarge(int maxPlayers, int testTimeoutMinutes) {
        return new AutosmokeThresholds(
            maxPlayers,
            LARGE.maxDurationSeconds,
            LARGE.maxMemoryMb,
            LARGE.assertTimeout,
            Duration.ofMinutes(testTimeoutMinutes),
            LARGE.allowAsync,
            LARGE.maxAsyncOperations,
            "CUSTOM_LARGE"
        );
    }

    /**
     * Validates that current values are within these thresholds
     *
     * @param actualPlayers Actual player count
     * @param actualDurationSeconds Actual duration
     * @param actualMemoryMb Actual memory usage
     * @return Validation result
     */
    public ValidationResult validate(int actualPlayers, long actualDurationSeconds, int actualMemoryMb) {
        boolean playersOk = actualPlayers <= maxPlayers;
        boolean durationOk = actualDurationSeconds <= maxDurationSeconds;
        boolean memoryOk = actualMemoryMb <= maxMemoryMb;

        return new ValidationResult(
            playersOk && durationOk && memoryOk,
            playersOk, durationOk, memoryOk,
            actualPlayers, actualDurationSeconds, actualMemoryMb,
            this
        );
    }

    /**
     * Result of threshold validation
     */
    public record ValidationResult(
        boolean passed,
        boolean playersOk,
        boolean durationOk,
        boolean memoryOk,
        int actualPlayers,
        long actualDurationSeconds,
        int actualMemoryMb,
        AutosmokeThresholds thresholds
    ) {
        public String getViolations() {
            if (passed) {
                return "None";
            }

            StringBuilder violations = new StringBuilder();
            if (!playersOk) {
                violations.append(String.format("Players: %d > %d; ",
                    actualPlayers, thresholds.maxPlayers));
            }
            if (!durationOk) {
                violations.append(String.format("Duration: %ds > %ds; ",
                    actualDurationSeconds, thresholds.maxDurationSeconds));
            }
            if (!memoryOk) {
                violations.append(String.format("Memory: %dMB > %dMB; ",
                    actualMemoryMb, thresholds.maxMemoryMb));
            }
            return violations.toString().trim();
        }
    }
}
