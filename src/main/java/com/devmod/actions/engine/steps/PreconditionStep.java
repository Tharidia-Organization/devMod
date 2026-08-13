package com.devmod.actions.engine.steps;

import java.util.Map;
import java.util.Objects;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.devmod.actions.ActionPrecondition;
import com.devmod.actions.ActionPreconditionRegistry;
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

    private static final Logger LOGGER = LoggerFactory.getLogger(PreconditionStep.class);

    private final ActionPreconditionRegistry preconditions;

    /**
     * Creates a PreconditionStep backed by the given registry.
     */
    public PreconditionStep(ActionPreconditionRegistry preconditions) {
        this.preconditions = Objects.requireNonNull(preconditions, "preconditions");
    }

    /**
     * Creates a PreconditionStep with an explicit map of named preconditions.
     *
     * @param preconditions map from precondition ref names to implementations
     */
    public PreconditionStep(Map<String, ActionPrecondition> preconditions) {
        this(ActionPreconditionRegistry.of(preconditions));
    }

    /**
     * Creates a PreconditionStep with no named preconditions. Intended for pipelines
     * assembled by hand in tests; the default pipeline built by
     * {@code ActionExecutor.createDefault} always supplies a populated registry.
     */
    public PreconditionStep() {
        this(ActionPreconditionRegistry.empty());
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

        ActionPrecondition precondition = preconditions.resolve(ref);
        if (precondition == null) {
            // Fail open: refusing every action whose ref went missing would be a worse
            // outage than letting it through. ActionCatalogValidator's preconditionRef
            // rule is what stops an unresolvable ref reaching production.
            LOGGER.warn("[PreconditionStep] Unresolvable preconditionRef '{}' on action '{}' - allowing",
                ref, ctx.actionId());
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
