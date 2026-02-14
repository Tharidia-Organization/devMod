package com.devmod.actions.policy;

import com.devmod.actions.ActionContext;
import com.devmod.actions.catalog.ActionSpec;

/**
 * Base interface for all action policies. A policy evaluates whether an action
 * invocation should be allowed based on a single concern (origin trust, permission
 * level, rate limiting, command safety, etc.).
 *
 * <p>Policies are stateless evaluators (except {@link RateLimitPolicy} which maintains
 * token buckets). They must be thread-safe.
 */
public interface ActionPolicy {

    /**
     * Evaluates this policy against the given action spec and invocation context.
     *
     * @param spec    the action specification being invoked
     * @param context the invocation context (player, origin, etc.)
     * @return a PolicyResult indicating allow or deny with reason
     */
    PolicyResult evaluate(ActionSpec spec, ActionContext context);

    /**
     * Returns the human-readable name of this policy (used in logs and deny reasons).
     */
    String policyName();

    /**
     * Returns the evaluation priority. Lower numbers are evaluated first.
     * Recommended ranges:
     * <ul>
     *   <li>0-99: Origin and trust policies</li>
     *   <li>100-199: Permission policies</li>
     *   <li>200-299: Safety policies (command sanitizer)</li>
     *   <li>300-399: Rate limiting</li>
     *   <li>400+: Custom policies</li>
     * </ul>
     */
    int priority();
}
