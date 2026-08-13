package com.devmod.actions.engine.steps;

import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.devmod.actions.ActionResult;
import com.devmod.actions.catalog.ActionSpec;
import com.devmod.actions.domains.ActionHandler;
import com.devmod.actions.domains.HandlerRegistry;
import com.devmod.actions.engine.ExecutionContext;
import com.devmod.actions.engine.PipelineStep;
import com.devmod.actions.engine.StepResult;

/**
 * Invokes the handler from HandlerRegistry. Catches exceptions, measures
 * duration, and wraps the outcome in an ActionResult.
 */
public final class ExecuteStep implements PipelineStep {

    private static final Logger LOGGER = LoggerFactory.getLogger(ExecuteStep.class);

    private final HandlerRegistry handlerRegistry;

    public ExecuteStep(HandlerRegistry handlerRegistry) {
        this.handlerRegistry = Objects.requireNonNull(handlerRegistry, "handlerRegistry");
    }

    @Override
    public StepResult process(ExecutionContext ctx) {
        ActionSpec spec = ctx.resolvedSpec();
        if (spec == null) {
            return StepResult.abort("No resolved ActionSpec");
        }

        ActionHandler handler = handlerRegistry.getHandler(ctx.actionId());
        if (handler == null) {
            ctx.setResult(ActionResult.blocked(
                ActionResult.ERROR_UNKNOWN_ACTION,
                "No handler registered for action: " + ctx.actionId()
            ));
            return StepResult.abort("No handler for: " + ctx.actionId());
        }

        if (ctx.isDryRun()) {
            // Everything the pipeline could refuse has already passed, so the action
            // would have run. Report that without touching game state.
            ctx.setResult(ActionResult.ok(ctx.elapsedMs()));
            return StepResult.continueStep();
        }

        try {
            ctx.markHandlerStarted();
            ActionHandler.Outcome outcome = handler.handle(ctx.actionContext());
            ctx.setResult(outcome.toActionResult(ctx.elapsedMs()));
            return outcome.success()
                ? StepResult.continueStep()
                : StepResult.abort("Handler reported failure: " + outcome.errorCode());
        } catch (Exception e) {
            LOGGER.error("[ExecuteStep] Handler threw exception for action '{}'",
                ctx.actionId(), e);
            ctx.setResult(ActionResult.failed(
                ActionResult.ERROR_EXCEPTION,
                Objects.requireNonNullElse(e.getMessage(), "Unknown error"),
                ctx.elapsedMs()
            ));
            return StepResult.abort("Handler exception: " + e.getMessage());
        }
    }

    @Override
    public String stepName() {
        return "Execute";
    }
}
