package com.devmod.actions.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.devmod.actions.ActionContext;
import com.devmod.actions.ActionResult;
import com.devmod.actions.catalog.ActionCatalog;
import com.devmod.actions.domains.HandlerRegistry;
import com.devmod.actions.engine.steps.ConfirmationStep;
import com.devmod.actions.engine.steps.ExecuteStep;
import com.devmod.actions.engine.steps.FeedbackStep;
import com.devmod.actions.engine.steps.PolicyAuthorizeStep;
import com.devmod.actions.engine.steps.PreconditionStep;
import com.devmod.actions.engine.steps.ResolveStep;
import com.devmod.actions.engine.steps.TelemetryStep;
import com.devmod.actions.engine.steps.ValidatePayloadStep;
import com.devmod.actions.policy.PolicyChain;

/**
 * The main execution engine for Actions V2. Runs a configurable pipeline of
 * {@link PipelineStep} instances in order, short-circuiting on ABORT.
 *
 * <p>The standard pipeline is:
 * <ol>
 *   <li>Resolve (lookup ActionSpec from catalog)</li>
 *   <li>ValidatePayload</li>
 *   <li>PolicyAuthorize (run PolicyChain)</li>
 *   <li>Precondition (check ActionPrecondition)</li>
 *   <li>Execute (call handler)</li>
 *   <li>Feedback (UI feedback)</li>
 *   <li>Telemetry (log invocation)</li>
 * </ol>
 *
 * <p>Create instances via {@link ActionExecutor#builder()}.
 */
public final class ActionExecutor {

    private static final Logger LOGGER = LoggerFactory.getLogger(ActionExecutor.class);

    private final List<PipelineStep> steps;

    private ActionExecutor(List<PipelineStep> steps) {
        this.steps = List.copyOf(steps);
    }

    /**
     * Executes the full pipeline for the given action invocation.
     *
     * @param actionId the ID of the action to invoke
     * @param context  the invocation context
     * @return the final ActionResult
     */
    public ActionResult execute(String actionId, ActionContext context) {
        Objects.requireNonNull(actionId, "actionId");
        Objects.requireNonNull(context, "context");

        ExecutionContext ctx = new ExecutionContext(actionId, context);

        for (PipelineStep step : steps) {
            try {
                StepResult stepResult = step.process(ctx);
                if (stepResult.isAbort()) {
                    ctx.setAbortReason(stepResult.reason());
                    LOGGER.debug("[ActionExecutor] Pipeline aborted at step '{}': {}",
                        step.stepName(), stepResult.reason());

                    if (ctx.result() == null) {
                        ctx.setResult(ActionResult.blocked(
                            ActionResult.ERROR_PRECONDITION_FAILED,
                            Objects.requireNonNullElse(stepResult.reason(), "Pipeline aborted")
                        ));
                    }
                    return ctx.result();
                }
            } catch (Exception e) {
                LOGGER.error("[ActionExecutor] Exception in step '{}' for action '{}'",
                    step.stepName(), actionId, e);
                ActionResult errorResult = ActionResult.failed(
                    ActionResult.ERROR_EXCEPTION,
                    Objects.requireNonNullElse(e.getMessage(), "Unknown error"),
                    ctx.elapsedMs()
                );
                ctx.setResult(errorResult);
                return errorResult;
            }
        }

        // If no step set a result, default to OK
        if (ctx.result() == null) {
            ctx.setResult(ActionResult.ok(ctx.elapsedMs()));
        }

        return ctx.result();
    }

    /**
     * Returns the number of pipeline steps.
     */
    public int stepCount() {
        return steps.size();
    }

    /**
     * Creates a default ActionExecutor with the standard pipeline:
     * Resolve -> ValidatePayload -> PolicyAuthorize -> Precondition ->
     * Confirmation -> Execute -> Feedback -> Telemetry.
     *
     * @param catalog         the action catalog for spec resolution
     * @param handlerRegistry the handler registry for execution
     * @param policyChain     the policy chain for authorization
     * @return a fully configured ActionExecutor
     */
    public static ActionExecutor createDefault(ActionCatalog catalog,
                                                HandlerRegistry handlerRegistry,
                                                PolicyChain policyChain) {
        return builder()
            .addStep(new ResolveStep(catalog))
            .addStep(new ValidatePayloadStep())
            .addStep(new PolicyAuthorizeStep(policyChain))
            .addStep(new PreconditionStep())
            .addStep(new ConfirmationStep())
            .addStep(new ExecuteStep(handlerRegistry))
            .addStep(new FeedbackStep())
            .addStep(new TelemetryStep())
            .build();
    }

    /**
     * Creates a default ActionExecutor with a custom telemetry sink.
     * Useful for testing without the real TelemetryService.
     */
    public static ActionExecutor createDefault(ActionCatalog catalog,
                                                HandlerRegistry handlerRegistry,
                                                PolicyChain policyChain,
                                                TelemetryStep.TelemetrySink telemetrySink) {
        return builder()
            .addStep(new ResolveStep(catalog))
            .addStep(new ValidatePayloadStep())
            .addStep(new PolicyAuthorizeStep(policyChain))
            .addStep(new PreconditionStep())
            .addStep(new ConfirmationStep())
            .addStep(new ExecuteStep(handlerRegistry))
            .addStep(new FeedbackStep())
            .addStep(new TelemetryStep(telemetrySink))
            .build();
    }

    /**
     * Creates a new builder for configuring the executor pipeline.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for constructing an ActionExecutor with a custom pipeline.
     */
    public static final class Builder {
        private final List<PipelineStep> steps = new ArrayList<>();

        private Builder() {}

        /**
         * Adds a step to the pipeline. Steps are executed in the order added.
         */
        public Builder addStep(PipelineStep step) {
            steps.add(Objects.requireNonNull(step, "step"));
            return this;
        }

        /**
         * Builds the ActionExecutor.
         */
        public ActionExecutor build() {
            return new ActionExecutor(steps);
        }
    }
}
