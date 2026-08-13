/**
 * Actions V2 Policy Layer - authorization and safety checks before execution.
 *
 * <h2>Purpose</h2>
 * Extracts the scattered authorization logic (origin whitelist, permission checks,
 * command sanitization, rate limiting) from {@code ActionRegistry} into composable,
 * testable policy objects. Each policy evaluates a single concern and returns an
 * allow/deny decision with a reason.
 *
 * <h2>Policy Types</h2>
 * <ul>
 *   <li>{@link com.devmod.actions.policy.OriginPolicy} - Validates that the action's
 *       origin is in the allowed set (replaces {@code CLIENT_SAFE_ACTIONS} whitelist)</li>
 *   <li>{@link com.devmod.actions.policy.PermissionPolicy} - Checks Minecraft permission
 *       levels (replaces {@code ActionPreconditions.requiresPermission})</li>
 *   <li>{@link com.devmod.actions.policy.CommandSafetyPolicy} - Wraps
 *       {@link com.devmod.actions.CommandSanitizer} for command-type actions</li>
 *   <li>{@link com.devmod.actions.policy.RateLimitPolicy} - Prevents abuse via
 *       per-player, per-action token bucket rate limiting</li>
 * </ul>
 *
 * <h2>Evaluation Order</h2>
 * Policies are evaluated by {@link com.devmod.actions.policy.PolicyChain} in priority
 * order (lower priority number = evaluated first). The chain short-circuits on the
 * first deny. An empty chain allows by default.
 *
 * <h2>Contracts</h2>
 * <ul>
 *   <li>Policies are stateless (except RateLimitPolicy which maintains token buckets)</li>
 *   <li>Policies must not modify the ActionSpec or ActionContext</li>
 *   <li>PolicyResult.reason() must be human-readable for debugging</li>
 *   <li>Policies must be thread-safe</li>
 * </ul>
 *
 * <h2>Boundaries</h2>
 * <ul>
 *   <li>This package only contains authorization logic, not runtime preconditions
 *       (player-has-item, world-state checks stay in {@link com.devmod.actions.ActionPrecondition})</li>
 *   <li>This package does not execute actions - that is the engine's job</li>
 * </ul>
 *
 * @see com.devmod.actions.engine.ActionExecutor
 */
@ParametersAreNonnullByDefault
package com.devmod.actions.policy;

import javax.annotation.ParametersAreNonnullByDefault;
