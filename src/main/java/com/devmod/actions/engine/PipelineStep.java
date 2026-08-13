package com.devmod.actions.engine;

/**
 * Interface for a single step in the action execution pipeline.
 * Steps are executed in order; the pipeline short-circuits if any step returns ABORT.
 *
 * <p>Implementations should be stateless. All mutable state flows through
 * the {@link ExecutionContext}.
 */
public interface PipelineStep {

    /**
     * Processes this step using the given execution context.
     *
     * @param ctx the mutable execution context flowing through the pipeline
     * @return CONTINUE to proceed, ABORT to stop the pipeline, SKIP to skip similar steps
     */
    StepResult process(ExecutionContext ctx);

    /**
     * Returns the human-readable name of this step (used in logs and telemetry).
     */
    String stepName();

    /**
     * Returns true if this step must run on every invocation, including ones where an
     * earlier step aborted or threw. Terminal steps run after the non-terminal portion
     * of the pipeline has finished or short-circuited, in the order they were added.
     *
     * <p>Steps that report an outcome rather than decide it (user feedback, telemetry)
     * must be terminal; otherwise a blocked or failed invocation reports nothing.
     * A terminal step's own {@link StepResult} cannot abort the pipeline and cannot
     * change the {@link com.devmod.actions.ActionResult} already recorded on the context.
     */
    default boolean isTerminal() {
        return false;
    }
}
