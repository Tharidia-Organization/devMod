package com.devmod.arena.registry;

import java.util.List;

/**
 * Result of template validation.
 */
public record ValidationResult(
    boolean valid,
    List<String> errors,
    List<String> warnings
) {
    /**
     * Constructor without warnings (backward compatible).
     */
    public ValidationResult(boolean valid, List<String> errors) {
        this(valid, errors, List.of());
    }

    public static ValidationResult success() {
        return new ValidationResult(true, List.of(), List.of());
    }

    public static ValidationResult failure(List<String> errors) {
        return new ValidationResult(false, errors, List.of());
    }

    public static ValidationResult failure(String error) {
        return new ValidationResult(false, List.of(error), List.of());
    }

    public String errorsAsString() {
        return String.join("; ", errors);
    }

    public String warningsAsString() {
        return String.join("; ", warnings);
    }

    public boolean hasWarnings() {
        return !warnings.isEmpty();
    }
}
