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
}
