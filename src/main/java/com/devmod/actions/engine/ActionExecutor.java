package com.devmod.actions.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.devmod.actions.ActionContext;
import com.devmod.actions.ActionPreconditionRegistry;
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
 *   <li>Feedback (UI feedback) - terminal</li>
 *   <li>Telemetry (log invocation) - terminal</li>
 * </ol>
 *
 * <p>Steps reporting {@link PipelineStep#isTerminal()} run on every invocation,
 * including ones an earlier step aborted or threw out of. Without that, the only
 * invocations a player or a dashboard ever hears about are the successful ones.
 *
 * <p>Create instances via {@link ActionExecutor#builder()}.
 */
public final class ActionExecutor {

    private static final Logger LOGGER = LoggerFactory.getLogger(ActionExecutor.class);

    private final List<PipelineStep> mainSteps;
    private final List<PipelineStep> terminalSteps;

    private ActionExecutor(List<PipelineStep> steps) {
        this.mainSteps = steps.stream().filter(s -> !s.isTerminal()).toList();
        this.terminalSteps = steps.stream().filter(PipelineStep::isTerminal).toList();
    }

    /**
     * Executes the full pipeline for the given action invocation.
     *
     * @param actionId the ID of the action to invoke
     * @param context  the invocation context
     * @return the final ActionResult
     */
    public ActionResult execute(String actionId, ActionContext context) {
        return executeDetailed(actionId, context, false).result();
    }

    /**
     * Executes the pipeline and reports how far it got, so callers that may retry
     * elsewhere can tell a refusal from a partially-applied action.
     *
     * @param actionId the ID of the action to invoke
     * @param context  the invocation context
     * @param dryRun   true to evaluate the gates without running the handler
     * @return the result plus whether the handler was entered
     */
    public ExecutionOutcome executeDetailed(String actionId, ActionContext context,
                                             boolean dryRun) {
        Objects.requireNonNull(actionId, "actionId");
        Objects.requireNonNull(context, "context");

        ExecutionContext ctx = new ExecutionContext(actionId, context, dryRun);

        try {
            runMainSteps(ctx);
        } finally {
            runTerminalSteps(ctx);
        }

        return new ExecutionOutcome(ctx.result(), ctx.handlerStarted());
    }

    /**
     * Runs the decision-making portion of the pipeline, stopping at the first
     * abort or exception. Always leaves a non-null result on the context.
     */
    private void runMainSteps(ExecutionContext ctx) {
        for (PipelineStep step : mainSteps) {
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
                    return;
                }
            } catch (Exception e) {
                LOGGER.error("[ActionExecutor] Exception in step '{}' for action '{}'",
                    step.stepName(), ctx.actionId(), e);
                ctx.setResult(ActionResult.failed(
                    ActionResult.ERROR_EXCEPTION,
                    Objects.requireNonNullElse(e.getMessage(), "Unknown error"),
                    ctx.elapsedMs()
                ));
                return;
            }
        }

        // If no step set a result, default to OK
        if (ctx.result() == null) {
            ctx.setResult(ActionResult.ok(ctx.elapsedMs()));
        }
    }

    /**
     * Runs every terminal step, isolating each one: reporting the outcome must not
     * be able to change it, nor let one failing reporter suppress the others.
     */
    private void runTerminalSteps(ExecutionContext ctx) {
        for (PipelineStep step : terminalSteps) {
            try {
                step.process(ctx);
            } catch (Exception e) {
                LOGGER.error("[ActionExecutor] Exception in terminal step '{}' for action '{}'",
                    step.stepName(), ctx.actionId(), e);
            }
        }
    }

    /**
     * Returns the number of pipeline steps.
     */
    public int stepCount() {
        return mainSteps.size() + terminalSteps.size();
    }

    /**
     * The outcome of a pipeline run.
     *
     * @param result         the final result; never null
     * @param handlerStarted whether control reached the action's handler. False means
     *                       nothing was applied, so another engine may safely retry.
     */
    public record ExecutionOutcome(ActionResult result, boolean handlerStarted) {
        public ExecutionOutcome {
            Objects.requireNonNull(result, "result");
        }
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
        return createDefault(catalog, handlerRegistry, policyChain,
            ActionPreconditionRegistry.createDefault(), new TelemetryStep());
    }

    /**
     * Creates a default ActionExecutor with a custom telemetry sink.
     * Useful for testing without the real TelemetryService.
     */
    public static ActionExecutor createDefault(ActionCatalog catalog,
                                                HandlerRegistry handlerRegistry,
                                                PolicyChain policyChain,
                                                TelemetryStep.TelemetrySink telemetrySink) {
        return createDefault(catalog, handlerRegistry, policyChain,
            ActionPreconditionRegistry.createDefault(), new TelemetryStep(telemetrySink));
    }

    /**
     * Creates a default ActionExecutor with an explicit precondition registry.
     * The registry must resolve every {@code preconditionRef} in the catalog;
     * {@link com.devmod.actions.catalog.ActionCatalogValidator} checks that.
     */
    public static ActionExecutor createDefault(ActionCatalog catalog,
                                                HandlerRegistry handlerRegistry,
                                                PolicyChain policyChain,
                                                ActionPreconditionRegistry preconditions,
                                                TelemetryStep telemetryStep) {
        return builder()
            .addStep(new ResolveStep(catalog))
            .addStep(new ValidatePayloadStep())
            .addStep(new PolicyAuthorizeStep(policyChain))
            .addStep(new PreconditionStep(preconditions))
            .addStep(new ConfirmationStep())
            .addStep(new ExecuteStep(handlerRegistry))
            .addStep(new FeedbackStep())
            .addStep(telemetryStep)
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
