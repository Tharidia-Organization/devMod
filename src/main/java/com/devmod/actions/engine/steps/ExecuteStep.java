package com.devmod.actions.engine.steps;

import java.util.Objects;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.devmod.actions.ActionContext;
import com.devmod.actions.ActionResult;
import com.devmod.actions.catalog.ActionSpec;
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

        Consumer<ActionContext> handler = handlerRegistry.getHandler(ctx.actionId());
        if (handler == null) {
            ctx.setResult(ActionResult.blocked(
                ActionResult.ERROR_UNKNOWN_ACTION,
                "No handler registered for action: " + ctx.actionId()
            ));
            return StepResult.abort("No handler for: " + ctx.actionId());
        }

        try {
            handler.accept(ctx.actionContext());
            ctx.setResult(ActionResult.ok(ctx.elapsedMs()));
            return StepResult.continueStep();
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
