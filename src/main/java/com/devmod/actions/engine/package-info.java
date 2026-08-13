/**
 * Actions V2 Execution Engine - unified pipeline for action invocation.
 *
 * <h2>Purpose</h2>
 * Replaces the monolithic {@code ActionRegistry.invokeWithResult()} method with a
 * composable pipeline of discrete steps. Each step has a single responsibility and
 * can be tested, monitored, and replaced independently.
 *
 * <h2>Pipeline Steps (in order)</h2>
 * <ol>
 *   <li><b>Resolve</b> - Look up {@code ActionSpec} from the catalog by action ID</li>
 *   <li><b>ValidatePayload</b> - Verify the payload matches the action's expected schema</li>
 *   <li><b>PolicyAuthorize</b> - Run the {@code PolicyChain} (origin, permission, rate-limit, command safety)</li>
 *   <li><b>Precondition</b> - Evaluate the action's runtime precondition (player state, world state)</li>
 *   <li><b>Execute</b> - Invoke the handler function</li>
 *   <li><b>Feedback</b> - Deliver UI feedback (chat, toast, sound) based on result</li>
 *   <li><b>Telemetry</b> - Log the invocation result to the telemetry pipeline</li>
 * </ol>
 *
 * <h2>Key Classes</h2>
 * <ul>
 *   <li>{@link com.devmod.actions.engine.PipelineStep} - Interface for a single pipeline step</li>
 *   <li>{@link com.devmod.actions.engine.StepResult} - Result of a step (CONTINUE, ABORT, SKIP)</li>
 *   <li>{@link com.devmod.actions.engine.ExecutionContext} - Mutable context flowing through the pipeline</li>
 *   <li>{@link com.devmod.actions.engine.ActionExecutor} - Orchestrates the pipeline</li>
 * </ul>
 *
 * <h2>Contracts</h2>
 * <ul>
 *   <li>Steps are executed in priority order; the pipeline short-circuits on ABORT</li>
 *   <li>The ExecutionContext is the only mutable state shared between steps</li>
 *   <li>Steps must not catch exceptions from downstream steps</li>
 *   <li>The engine is thread-safe: one ExecutionContext per invocation, no shared mutable state</li>
 * </ul>
 *
 * <h2>Boundaries</h2>
 * <ul>
 *   <li>This package does NOT contain action definitions - those live in {@code catalog/} and {@code domains/}</li>
 *   <li>This package does NOT contain policy implementations - those live in {@code policy/}</li>
 *   <li>This package does NOT contain binding adapters - those live in {@code bindings/}</li>
 * </ul>
 *
 * @see com.devmod.actions.catalog.ActionSpec
 * @see com.devmod.actions.policy.PolicyChain
 */
@ParametersAreNonnullByDefault
package com.devmod.actions.engine;

import javax.annotation.ParametersAreNonnullByDefault;
