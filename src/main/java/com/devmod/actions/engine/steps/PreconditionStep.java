package com.devmod.actions.engine.steps;

import java.util.Map;
import java.util.Objects;

import javax.annotation.Nullable;

import com.devmod.actions.ActionPrecondition;
import com.devmod.actions.ActionResult;
import com.devmod.actions.catalog.ActionSpec;
import com.devmod.actions.engine.ExecutionContext;
import com.devmod.actions.engine.PipelineStep;
import com.devmod.actions.engine.StepResult;

/**
 * Evaluates the named precondition from ActionSpec.policyMeta().preconditionRef().
 * Uses a registry of named preconditions (mapped from string refs to ActionPrecondition
 * instances). If no preconditionRef is set, the step passes.
 *
 * <p>Compatible with existing {@link com.devmod.actions.ActionPreconditions} factory methods.
 */
public final class PreconditionStep implements PipelineStep {

    private final Map<String, ActionPrecondition> preconditions;

    /**
     * Creates a PreconditionStep with a registry of named preconditions.
     *
     * @param preconditions map from precondition ref names to implementations
     */
    public PreconditionStep(Map<String, ActionPrecondition> preconditions) {
        this.preconditions = Map.copyOf(Objects.requireNonNull(preconditions, "preconditions"));
    }

    /**
     * Creates a PreconditionStep with no named preconditions.
     * Actions with preconditionRef will log a warning and pass.
     */
    public PreconditionStep() {
        this(Map.of());
    }

    @Override
    public StepResult process(ExecutionContext ctx) {
        ActionSpec spec = ctx.resolvedSpec();
        if (spec == null) {
            return StepResult.abort("No resolved ActionSpec");
        }

        String ref = spec.policyMeta().preconditionRef();
        if (ref == null || ref.isEmpty()) {
            return StepResult.continueStep();
        }

        ActionPrecondition precondition = preconditions.get(ref);
        if (precondition == null) {
            // Unknown precondition ref - pass with warning (logged by executor)
            return StepResult.continueStep();
        }

        if (!precondition.test(ctx.actionContext())) {
            String failureMsg = precondition.failureMessage(ctx.actionContext()).getString();
            ctx.setResult(ActionResult.blocked(
                ActionResult.ERROR_PRECONDITION_FAILED,
                failureMsg
            ));
            return StepResult.abort(failureMsg);
        }

        return StepResult.continueStep();
    }

    @Override
    public String stepName() {
        return "Precondition";
    }
}
