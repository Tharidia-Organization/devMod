package com.devmod.client.ui;

import java.util.Optional;
import java.util.function.Predicate;

/**
 * Centralized input validation for UI fields.
 * Provides safe parsing with range checking and optional results.
 */
public class InputValidator {

    /**
     * Parse a double value with range validation.
     *
     * @param input String input to parse
     * @param min Minimum allowed value (inclusive)
     * @param max Maximum allowed value (inclusive)
     * @return Optional containing the value if valid, empty otherwise
     */
    public static Optional<Double> parseDouble(String input, double min, double max) {
        if (input == null || input.isBlank()) {
            return Optional.empty();
        }
        try {
            double value = Double.parseDouble(input.trim());
            if (value >= min && value <= max && Double.isFinite(value)) {
                return Optional.of(value);
            }
        } catch (NumberFormatException ignored) {}
        return Optional.empty();
    }

    /**
     * Parse a double value without range validation.
     */
    public static Optional<Double> parseDouble(String input) {
        return parseDouble(input, -Double.MAX_VALUE, Double.MAX_VALUE);
    }

    /**
     * Parse an integer value with range validation.
     *
     * @param input String input to parse
     * @param min Minimum allowed value (inclusive)
     * @param max Maximum allowed value (inclusive)
     * @return Optional containing the value if valid, empty otherwise
     */
    public static Optional<Integer> parseInt(String input, int min, int max) {
        if (input == null || input.isBlank()) {
            return Optional.empty();
        }
        try {
            int value = Integer.parseInt(input.trim());
            if (value >= min && value <= max) {
                return Optional.of(value);
            }
        } catch (NumberFormatException ignored) {}
        return Optional.empty();
    }

    /**
     * Parse an integer value without range validation.
     */
    public static Optional<Integer> parseInt(String input) {
        return parseInt(input, Integer.MIN_VALUE, Integer.MAX_VALUE);
    }

    /**
     * Parse a long value with range validation.
     */
    public static Optional<Long> parseLong(String input, long min, long max) {
        if (input == null || input.isBlank()) {
            return Optional.empty();
        }
        try {
            long value = Long.parseLong(input.trim());
            if (value >= min && value <= max) {
                return Optional.of(value);
            }
        } catch (NumberFormatException ignored) {}
        return Optional.empty();
    }

    /**
     * Parse a float value with range validation.
     */
    public static Optional<Float> parseFloat(String input, float min, float max) {
        if (input == null || input.isBlank()) {
            return Optional.empty();
        }
        try {
            float value = Float.parseFloat(input.trim());
            if (value >= min && value <= max && Float.isFinite(value)) {
                return Optional.of(value);
            }
        } catch (NumberFormatException ignored) {}
        return Optional.empty();
    }

    /**
     * Parse a percentage value (0-100) and return as decimal (0.0-1.0).
     */
    public static Optional<Float> parsePercentage(String input) {
        return parseFloat(input, 0f, 100f).map(v -> v / 100f);
    }

    /**
     * Validate a string matches a custom predicate.
     *
     * @param input String to validate
     * @param validator Predicate to test
     * @return Optional containing the input if valid, empty otherwise
     */
    public static Optional<String> validateString(String input, Predicate<String> validator) {
        if (input != null && validator.test(input)) {
            return Optional.of(input);
        }
        return Optional.empty();
    }

    /**
     * Validate a non-empty trimmed string.
     */
    public static Optional<String> requireNonEmpty(String input) {
        if (input != null && !input.trim().isEmpty()) {
            return Optional.of(input.trim());
        }
        return Optional.empty();
    }

    /**
     * Validate string length is within bounds.
     */
    public static Optional<String> validateLength(String input, int minLength, int maxLength) {
        if (input != null) {
            String trimmed = input.trim();
            if (trimmed.length() >= minLength && trimmed.length() <= maxLength) {
                return Optional.of(trimmed);
            }
        }
        return Optional.empty();
    }

    /**
     * Parse a positive double (greater than 0).
     */
    public static Optional<Double> parsePositiveDouble(String input) {
        return parseDouble(input, Double.MIN_VALUE, Double.MAX_VALUE);
    }

    /**
     * Parse a non-negative double (>= 0).
     */
    public static Optional<Double> parseNonNegativeDouble(String input) {
        return parseDouble(input, 0.0, Double.MAX_VALUE);
    }

    /**
     * Parse a positive integer (greater than 0).
     */
    public static Optional<Integer> parsePositiveInt(String input) {
        return parseInt(input, 1, Integer.MAX_VALUE);
    }

    /**
     * Parse a non-negative integer (>= 0).
     */
    public static Optional<Integer> parseNonNegativeInt(String input) {
        return parseInt(input, 0, Integer.MAX_VALUE);
    }

    /**
     * Clamp a value to a range, returning the clamped value or default if invalid.
     */
    public static double clampDouble(String input, double min, double max, double defaultValue) {
        return parseDouble(input)
            .map(v -> Math.max(min, Math.min(max, v)))
            .orElse(defaultValue);
    }

    /**
     * Clamp an integer to a range, returning the clamped value or default if invalid.
     */
    public static int clampInt(String input, int min, int max, int defaultValue) {
        return parseInt(input)
            .map(v -> Math.max(min, Math.min(max, v)))
            .orElse(defaultValue);
    }

    /**
     * Check if input is a valid number.
     */
    public static boolean isNumber(String input) {
        return parseDouble(input).isPresent();
    }

    /**
     * Check if input is a valid integer.
     */
    public static boolean isInteger(String input) {
        return parseInt(input).isPresent();
    }

    /**
     * Check if input is a valid positive number.
     */
    public static boolean isPositive(String input) {
        return parseDouble(input).filter(v -> v > 0).isPresent();
    }
}
