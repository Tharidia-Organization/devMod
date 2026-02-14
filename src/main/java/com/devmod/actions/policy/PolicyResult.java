package com.devmod.actions.policy;

import java.util.Objects;

import javax.annotation.Nullable;

/**
 * Result of a policy evaluation. Immutable value indicating whether an action
 * invocation is allowed or denied, with a reason and the name of the policy
 * that made the decision.
 */
public record PolicyResult(
    boolean allowed,
    @Nullable String reason,
    @Nullable String policyName
) {

    private static final PolicyResult ALLOW_INSTANCE = new PolicyResult(true, null, null);

    /**
     * Returns a cached allow result (no reason, no policy name).
     */
    public static PolicyResult allow() {
        return ALLOW_INSTANCE;
    }

    /**
     * Creates a deny result with a reason and the policy name that denied it.
     *
     * @param reason     human-readable reason for denial
     * @param policyName name of the policy that denied the action
     */
    public static PolicyResult deny(String reason, String policyName) {
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(policyName, "policyName");
        return new PolicyResult(false, reason, policyName);
    }

    /**
     * Returns true if the action is denied.
     */
    public boolean isDenied() {
        return !allowed;
    }
}
