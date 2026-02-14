package com.devmod.actions.engine.steps;

import java.util.Objects;

import com.devmod.actions.ActionResult;
import com.devmod.actions.catalog.ActionSpec;
import com.devmod.actions.engine.ExecutionContext;
import com.devmod.actions.engine.PipelineStep;
import com.devmod.actions.engine.StepResult;
import com.devmod.actions.policy.PolicyChain;
import com.devmod.actions.policy.PolicyResult;

/**
 * Runs the PolicyChain against the ActionSpec and ActionContext.
 * If any policy denies, ABORTs the pipeline with the policy's reason.
 */
public final class PolicyAuthorizeStep implements PipelineStep {

    private final PolicyChain policyChain;

    public PolicyAuthorizeStep(PolicyChain policyChain) {
        this.policyChain = Objects.requireNonNull(policyChain, "policyChain");
    }

    @Override
    public StepResult process(ExecutionContext ctx) {
        ActionSpec spec = ctx.resolvedSpec();
        if (spec == null) {
            return StepResult.abort("No resolved ActionSpec");
        }

        PolicyResult result = policyChain.evaluate(spec, ctx.actionContext());
        if (result.isDenied()) {
            String errorCode = mapPolicyToErrorCode(result.policyName());
            ctx.setResult(ActionResult.blocked(errorCode, result.reason()));
            return StepResult.abort(result.reason());
        }

        return StepResult.continueStep();
    }

    /**
     * Maps a policy name to the appropriate ActionResult error code.
     */
    private static String mapPolicyToErrorCode(String policyName) {
        if (policyName == null) {
            return ActionResult.ERROR_PRECONDITION_FAILED;
        }
        return switch (policyName) {
            case "OriginPolicy" -> ActionResult.ERROR_UNTRUSTED_ACTION;
            case "PermissionPolicy" -> ActionResult.ERROR_PERMISSION_DENIED;
            case "CommandSafetyPolicy" -> ActionResult.ERROR_UNTRUSTED_ACTION;
            case "RateLimitPolicy" -> "RATE_LIMITED";
            default -> ActionResult.ERROR_PRECONDITION_FAILED;
        };
    }

    @Override
    public String stepName() {
        return "PolicyAuthorize";
    }
}
