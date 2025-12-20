package com.devmod.arena.naming;

import java.util.Random;
import java.util.regex.Pattern;

/**
 * Arena instance name with validation (DD26).
 *
 * <p>Rules:
 * <ul>
 *   <li>Min 3, Max 32 characters</li>
 *   <li>Only lowercase alphanumeric and single underscores [a-z0-9_]</li>
 *   <li>Must start with letter</li>
 *   <li>Cannot end with underscore</li>
 *   <li>No consecutive underscores</li>
 * </ul>
 */
public record InstanceName(String value) {

    private static final int MIN_LENGTH = 3;  // DD26: minimum length
    private static final int MAX_LENGTH = 32;
    // DD26: More restrictive pattern - no double underscores, min 3 chars
    private static final Pattern VALID_PATTERN = Pattern.compile("^[a-z](?:[a-z0-9]|_(?!_))*[a-z0-9]$");
    private static final Pattern DOUBLE_UNDERSCORE = Pattern.compile("__");
    private static final Random RANDOM = new Random();

    // Adjectives and nouns for random name generation
    private static final String[] ADJECTIVES = {
        "red", "blue", "dark", "swift", "iron", "gold", "stone", "fire",
        "ice", "storm", "shadow", "bright", "wild", "silent", "ancient", "eternal"
    };

    private static final String[] NOUNS = {
        "arena", "pit", "dome", "ring", "cage", "hall", "field", "court",
        "colosseum", "chamber", "vault", "forge", "nexus", "sanctum", "bastion", "citadel"
    };

    public InstanceName {
        ValidationResult result = validate(value);
        if (!result.isValid()) {
            throw new IllegalArgumentException("Invalid instance name: " + result.message());
        }
    }

    /**
     * Validates a potential instance name.
     * DD26: More restrictive validation with min length and no double underscores.
     */
    public static ValidationResult validate(String name) {
        if (name == null || name.isEmpty()) {
            return ValidationResult.invalid("Name cannot be empty");
        }

        if (name.length() < MIN_LENGTH) {
            return ValidationResult.invalid("Name too short (min " + MIN_LENGTH + " chars)");
        }

        if (name.length() > MAX_LENGTH) {
            return ValidationResult.invalid("Name too long (max " + MAX_LENGTH + " chars)");
        }

        // DD26: Check for consecutive underscores
        if (DOUBLE_UNDERSCORE.matcher(name).find()) {
            return ValidationResult.invalid("Name cannot contain consecutive underscores");
        }

        if (!VALID_PATTERN.matcher(name).matches()) {
            if (!Character.isLetter(name.charAt(0))) {
                return ValidationResult.invalid("Name must start with a letter");
            }
            if (name.endsWith("_")) {
                return ValidationResult.invalid("Name cannot end with underscore");
            }
            return ValidationResult.invalid("Name can only contain lowercase letters, numbers, and underscores");
        }

        return ValidationResult.valid();
    }

    /**
     * Sanitizes a string into a valid instance name.
     */
    public static String sanitize(String input) {
        if (input == null || input.isEmpty()) {
            return generate();
        }

        // Lowercase and replace invalid chars
        String sanitized = input.toLowerCase()
            .replaceAll("[^a-z0-9_]", "_")
            .replaceAll("_+", "_");

        // Ensure starts with letter
        if (!sanitized.isEmpty() && !Character.isLetter(sanitized.charAt(0))) {
            sanitized = "arena_" + sanitized;
        }

        // Remove trailing underscore
        while (sanitized.endsWith("_")) {
            sanitized = sanitized.substring(0, sanitized.length() - 1);
        }

        // Truncate if too long
        if (sanitized.length() > MAX_LENGTH) {
            sanitized = sanitized.substring(0, MAX_LENGTH);
            // Ensure doesn't end with underscore after truncation
            while (sanitized.endsWith("_")) {
                sanitized = sanitized.substring(0, sanitized.length() - 1);
            }
        }

        // Fallback if empty
        if (sanitized.isEmpty()) {
            return generate();
        }

        return sanitized;
    }

    /**
     * Generates a random valid instance name.
     */
    public static String generate() {
        String adjective = ADJECTIVES[RANDOM.nextInt(ADJECTIVES.length)];
        String noun = NOUNS[RANDOM.nextInt(NOUNS.length)];
        int suffix = RANDOM.nextInt(1000);
        return "%s_%s_%03d".formatted(adjective, noun, suffix);
    }

    /**
     * Creates an InstanceName from a potentially invalid string by sanitizing it.
     */
    public static InstanceName fromUnsafe(String input) {
        return new InstanceName(sanitize(input));
    }

    @Override
    public String toString() {
        return value;
    }

    /**
     * Validation result for instance names.
     */
    public record ValidationResult(boolean isValid, String message) {
        public static ValidationResult valid() {
            return new ValidationResult(true, null);
        }

        public static ValidationResult invalid(String message) {
            return new ValidationResult(false, message);
        }
    }
}
