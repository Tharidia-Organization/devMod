package com.devmod.actions.engine.steps;

import java.util.Objects;

import com.devmod.actions.ActionResult;
import com.devmod.actions.catalog.ActionCatalog;
import com.devmod.actions.catalog.ActionSpec;
import com.devmod.actions.engine.ExecutionContext;
import com.devmod.actions.engine.PipelineStep;
import com.devmod.actions.engine.StepResult;

/**
 * Looks up the ActionSpec from the catalog by action ID.
 * If not found, ABORTs with UNKNOWN_ACTION.
 */
public final class ResolveStep implements PipelineStep {

    private final ActionCatalog catalog;

    public ResolveStep(ActionCatalog catalog) {
        this.catalog = Objects.requireNonNull(catalog, "catalog");
    }

    @Override
    public StepResult process(ExecutionContext ctx) {
        ActionSpec spec = catalog.get(ctx.actionId());
        if (spec == null) {
            ctx.setResult(ActionResult.blocked(
                ActionResult.ERROR_UNKNOWN_ACTION,
                "Unknown action: " + ctx.actionId()
            ));
            return StepResult.abort("Unknown action: " + ctx.actionId());
        }
        ctx.setResolvedSpec(spec);
        return StepResult.continueStep();
    }

    @Override
    public String stepName() {
        return "Resolve";
    }
}
