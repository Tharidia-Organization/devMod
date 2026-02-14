package com.devmod.actions.policy;

import com.devmod.actions.ActionContext;
import com.devmod.actions.ActionOrigin;
import com.devmod.actions.catalog.ActionSpec;

/**
 * Validates that the invocation origin is allowed for this action.
 * Replaces the hardcoded {@code CLIENT_SAFE_ACTIONS} whitelist in ActionRegistry.
 *
 * <p>Each ActionSpec declares its allowed origins. This policy checks
 * that the current invocation origin is in that set.
 */
public final class OriginPolicy implements ActionPolicy {

    public static final OriginPolicy INSTANCE = new OriginPolicy();

    private OriginPolicy() {}

    @Override
    public PolicyResult evaluate(ActionSpec spec, ActionContext context) {
        ActionOrigin origin = context.getOrigin();
        if (spec.allowsOrigin(origin)) {
            return PolicyResult.allow();
        }
        return PolicyResult.deny(
            "Action '" + spec.id() + "' does not allow origin " + origin,
            policyName()
        );
    }

    @Override
    public String policyName() {
        return "OriginPolicy";
    }

    @Override
    public int priority() {
        return 10;
    }
}
