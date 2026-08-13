package com.devmod.actions.engine;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import javax.annotation.Nullable;

import com.devmod.actions.ActionContext;
import com.devmod.actions.ActionResult;
import com.devmod.actions.catalog.ActionSpec;

/**
 * Mutable context that flows through the action execution pipeline. Wraps the immutable
 * {@link ActionSpec} and {@link ActionContext} with accumulated pipeline state.
 *
 * <p>Each invocation gets its own ExecutionContext instance - they are not shared
 * between concurrent invocations.
 */
public final class ExecutionContext {

    private final String actionId;
    private final UUID invocationId;
    private final ActionContext actionContext;
    private final long startTimeMs;
    private final boolean dryRun;
    private final Map<String, Object> attributes = new HashMap<>();

    @Nullable
    private ActionSpec resolvedSpec;
    @Nullable
    private ActionResult result;
    @Nullable
    private String abortReason;
    private boolean handlerStarted;

    /**
     * Creates a new execution context for a normal (side-effecting) invocation.
     *
     * @param actionId      the ID of the action being invoked
     * @param actionContext  the invocation context (player, origin, etc.)
     */
    public ExecutionContext(String actionId, ActionContext actionContext) {
        this(actionId, actionContext, false);
    }

    /**
     * Creates a new execution context for the given action invocation.
     *
     * @param actionId      the ID of the action being invoked
     * @param actionContext  the invocation context (player, origin, etc.)
     * @param dryRun        true to evaluate the pipeline without running the handler
     */
    public ExecutionContext(String actionId, ActionContext actionContext, boolean dryRun) {
        this.actionId = Objects.requireNonNull(actionId, "actionId");
        this.actionContext = Objects.requireNonNull(actionContext, "actionContext");
        this.dryRun = dryRun;
        this.invocationId = UUID.randomUUID();
        this.startTimeMs = System.currentTimeMillis();
    }

    /**
     * Returns true if this invocation must not apply side effects. Steps that would
     * mutate game state, message the player, or run the handler are skipped; the
     * pipeline still resolves, authorizes and reports, so the caller learns what
     * <em>would</em> have happened.
     */
    public boolean isDryRun() {
        return dryRun;
    }

    /**
     * Returns true once the handler has been entered. Distinguishes "the pipeline
     * refused before Execute" from "the handler ran and threw partway through" —
     * only the former is safe to retry on another engine, since the latter may have
     * already applied part of its side effects.
     */
    public boolean handlerStarted() {
        return handlerStarted;
    }

    /**
     * Marks the handler as entered (called by the Execute step immediately before
     * control passes to the handler).
     */
    public void markHandlerStarted() {
        this.handlerStarted = true;
    }

    /**
     * Returns the action ID being invoked.
     */
    public String actionId() {
        return actionId;
    }

    /**
     * Returns a unique ID for this invocation (useful for telemetry correlation).
     */
    public UUID invocationId() {
        return invocationId;
    }

    /**
     * Returns the immutable invocation context.
     */
    public ActionContext actionContext() {
        return actionContext;
    }

    /**
     * Returns the time (millis) when this invocation started.
     */
    public long startTimeMs() {
        return startTimeMs;
    }

    /**
     * Returns the elapsed time in milliseconds since invocation started.
     */
    public long elapsedMs() {
        return System.currentTimeMillis() - startTimeMs;
    }

    /**
     * Returns the resolved ActionSpec, or null if not yet resolved.
     */
    @Nullable
    public ActionSpec resolvedSpec() {
        return resolvedSpec;
    }

    /**
     * Sets the resolved ActionSpec (called by the Resolve step).
     */
    public void setResolvedSpec(ActionSpec spec) {
        this.resolvedSpec = Objects.requireNonNull(spec, "spec");
    }

    /**
     * Returns the final result, or null if not yet set.
     */
    @Nullable
    public ActionResult result() {
        return result;
    }

    /**
     * Sets the final result (called by Execute or error-handling steps).
     */
    public void setResult(ActionResult result) {
        this.result = Objects.requireNonNull(result, "result");
    }

    /**
     * Returns the abort reason, or null if pipeline was not aborted.
     */
    @Nullable
    public String abortReason() {
        return abortReason;
    }

    /**
     * Sets the abort reason (called when a step returns ABORT).
     */
    public void setAbortReason(@Nullable String reason) {
        this.abortReason = reason;
    }

    /**
     * Gets a named attribute from this context with type checking.
     */
    @Nullable
    public <T> T getAttribute(String key, Class<T> type) {
        Object val = attributes.get(key);
        return type.isInstance(val) ? type.cast(val) : null;
    }

    /**
     * Sets a named attribute on this context.
     */
    public void setAttribute(String key, Object value) {
        attributes.put(key, value);
    }
}
