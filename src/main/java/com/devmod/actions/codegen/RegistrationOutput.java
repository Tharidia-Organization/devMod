package com.devmod.actions.codegen;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Holds the output of a code generation run.
 *
 * @param generatedSource the generated Java source code
 * @param actionIds       all action IDs in generation order (sorted)
 * @param warnings        any issues found during generation
 * @param timestamp       when the generation was performed
 */
public record RegistrationOutput(
    String generatedSource,
    List<String> actionIds,
    List<String> warnings,
    Instant timestamp
) {
    public RegistrationOutput {
        Objects.requireNonNull(generatedSource, "generatedSource");
        Objects.requireNonNull(actionIds, "actionIds");
        Objects.requireNonNull(warnings, "warnings");
        Objects.requireNonNull(timestamp, "timestamp");
        actionIds = List.copyOf(actionIds);
        warnings = List.copyOf(warnings);
    }

    /**
     * Returns true if the generation produced any warnings.
     */
    public boolean hasWarnings() {
        return !warnings.isEmpty();
    }

    /**
     * Returns the number of actions in the generated output.
     */
    public int actionCount() {
        return actionIds.size();
    }
}
