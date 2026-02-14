package com.devmod.actions.engine.steps;

import com.devmod.actions.ActionResult;
import com.devmod.actions.catalog.ActionSpec;
import com.devmod.actions.engine.ExecutionContext;
import com.devmod.actions.engine.PipelineStep;
import com.devmod.actions.engine.StepResult;

/**
 * Checks if the action requires user confirmation and whether confirmation
 * has been provided. If requiresConfirm is true and context.isConfirmed()
 * is false, ABORTs with REQUIRES_CONFIRM.
 */
public final class ConfirmationStep implements PipelineStep {

    @Override
    public StepResult process(ExecutionContext ctx) {
        ActionSpec spec = ctx.resolvedSpec();
        if (spec == null) {
            return StepResult.abort("No resolved ActionSpec");
        }

        if (spec.policyMeta().requiresConfirm() && !ctx.actionContext().isConfirmed()) {
            ctx.setResult(ActionResult.blocked(
                ActionResult.ERROR_REQUIRES_CONFIRM,
                "Action requires confirmation"
            ));
            return StepResult.abort("Action requires confirmation");
        }

        return StepResult.continueStep();
    }

    @Override
    public String stepName() {
        return "Confirmation";
    }
}
