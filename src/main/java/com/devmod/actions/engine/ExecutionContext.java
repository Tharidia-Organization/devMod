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
    private final Map<String, Object> attributes = new HashMap<>();

    @Nullable
    private ActionSpec resolvedSpec;
    @Nullable
    private ActionResult result;
    @Nullable
    private String abortReason;

    /**
     * Creates a new execution context for the given action invocation.
     *
     * @param actionId      the ID of the action being invoked
     * @param actionContext  the invocation context (player, origin, etc.)
     */
    public ExecutionContext(String actionId, ActionContext actionContext) {
        this.actionId = Objects.requireNonNull(actionId, "actionId");
        this.actionContext = Objects.requireNonNull(actionContext, "actionContext");
        this.invocationId = UUID.randomUUID();
        this.startTimeMs = System.currentTimeMillis();
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
