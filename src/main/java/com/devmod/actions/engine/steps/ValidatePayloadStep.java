package com.devmod.actions.engine.steps;

import com.devmod.actions.catalog.ActionSpec;
import com.devmod.actions.engine.ExecutionContext;
import com.devmod.actions.engine.PipelineStep;
import com.devmod.actions.engine.StepResult;

/**
 * Validates the payload in ActionContext against what the ActionSpec expects.
 * Currently performs basic validation: ensures resolvedSpec is present.
 * Future versions can add schema-based payload validation.
 */
public final class ValidatePayloadStep implements PipelineStep {

    @Override
    public StepResult process(ExecutionContext ctx) {
        ActionSpec spec = ctx.resolvedSpec();
        if (spec == null) {
            return StepResult.abort("No resolved ActionSpec - ResolveStep must run first");
        }
        // Payload validation is currently permissive.
        // ActionSpec does not yet define required payload schemas.
        // When payload schemas are added, validate here.
        return StepResult.continueStep();
    }

    @Override
    public String stepName() {
        return "ValidatePayload";
    }
}
