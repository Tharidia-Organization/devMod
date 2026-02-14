package com.devmod.actions.engine;

import java.util.Objects;

import javax.annotation.Nullable;

/**
 * Result of a pipeline step execution. Controls pipeline flow.
 */
public record StepResult(
    Outcome outcome,
    @Nullable String reason
) {

    /**
     * Pipeline flow control outcomes.
     */
    public enum Outcome {
        /** Step succeeded, continue to next step. */
        CONTINUE,
        /** Step decided to abort the pipeline (e.g., policy deny). */
        ABORT,
        /** Step decided to skip remaining steps of this type. */
        SKIP
    }

    private static final StepResult CONTINUE_INSTANCE = new StepResult(Outcome.CONTINUE, null);
    private static final StepResult SKIP_INSTANCE = new StepResult(Outcome.SKIP, null);

    /**
     * Returns a cached CONTINUE result.
     */
    public static StepResult continueStep() {
        return CONTINUE_INSTANCE;
    }

    /**
     * Creates an ABORT result with a reason.
     */
    public static StepResult abort(String reason) {
        Objects.requireNonNull(reason, "reason");
        return new StepResult(Outcome.ABORT, reason);
    }

    /**
     * Returns a cached SKIP result.
     */
    public static StepResult skip() {
        return SKIP_INSTANCE;
    }

    /**
     * Returns true if the pipeline should continue.
     */
    public boolean shouldContinue() {
        return outcome == Outcome.CONTINUE;
    }

    /**
     * Returns true if the pipeline should abort.
     */
    public boolean isAbort() {
        return outcome == Outcome.ABORT;
    }
}
