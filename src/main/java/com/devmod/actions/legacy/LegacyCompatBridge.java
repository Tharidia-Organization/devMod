package com.devmod.actions.legacy;

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.devmod.actions.ActionContext;
import com.devmod.actions.ActionRegistry;
import com.devmod.actions.ActionResult;
import com.devmod.actions.engine.ActionExecutor;
import com.devmod.actions.engine.EngineFeatureFlags;
import com.devmod.actions.engine.EngineFeatureFlags.RoutingMode;

/**
 * Bridge between the old {@link ActionRegistry} and the new {@link ActionExecutor}.
 * Routes action invocations based on the current {@link RoutingMode} determined
 * by {@link EngineFeatureFlags} and per-action migration status.
 *
 * <p>Routing modes:
 * <ul>
 *   <li><b>V1_ONLY</b>: All invocations go through V1 (engine disabled)</li>
 *   <li><b>SHADOW</b>: V1 is primary. For migrated actions, V2 also runs
 *       read-only and results are compared via {@link ShadowModeComparator}</li>
 *   <li><b>V2_ACTIVE</b>: For migrated actions, V2 is primary with V1 fallback
 *       on V2 failure. Unmigrated actions still use V1.</li>
 *   <li><b>V2_ONLY</b>: For migrated actions, V2 is the sole path with no fallback.
 *       Unmigrated actions still use V1.</li>
 * </ul>
 *
 * <p>Usage: Install as the new entry point for all action invocations.
 * The routing mode is read from feature flags on every invocation, allowing
 * runtime toggling without restart.
 *
 * <p>This class is thread-safe. The migratedActionIds set uses ConcurrentHashMap.
 */
public final class LegacyCompatBridge {

    private static final Logger LOGGER = LoggerFactory.getLogger(LegacyCompatBridge.class);

    private final ActionExecutor v2Executor;
    private final Set<String> migratedActionIds;

    // Metrics
    private final AtomicLong v1Invocations = new AtomicLong(0);
    private final AtomicLong v2Invocations = new AtomicLong(0);
    private final AtomicLong v2Fallbacks = new AtomicLong(0);
    private final AtomicLong shadowRuns = new AtomicLong(0);

    /**
     * Creates a compatibility bridge.
     *
     * @param v2Executor        the V2 action executor
     * @param migratedActionIds set of action IDs that have been migrated to V2
     */
    public LegacyCompatBridge(ActionExecutor v2Executor, Set<String> migratedActionIds) {
        this.v2Executor = Objects.requireNonNull(v2Executor, "v2Executor");
        this.migratedActionIds = ConcurrentHashMap.newKeySet();
        this.migratedActionIds.addAll(migratedActionIds);
    }

    /**
     * Legacy constructor for backward compatibility with architect's skeleton.
     *
     * @param v2Executor        the V2 action executor
     * @param migratedActionIds set of action IDs that have been migrated to V2
     * @param shadowMode        ignored; routing mode is now read from EngineFeatureFlags
     * @deprecated Use {@link #LegacyCompatBridge(ActionExecutor, Set)} instead.
     *             Shadow mode is now controlled via EngineFeatureFlags.
     */
    @Deprecated
    public LegacyCompatBridge(ActionExecutor v2Executor, Set<String> migratedActionIds,
                               boolean shadowMode) {
        this(v2Executor, migratedActionIds);
    }

    /**
     * Invokes an action, routing based on feature flags and migration status.
     *
     * @param actionId the action ID to invoke
     * @param context  the invocation context
     * @return the action result
     */
    public ActionResult invoke(String actionId, ActionContext context) {
        Objects.requireNonNull(actionId, "actionId");
        Objects.requireNonNull(context, "context");

        RoutingMode mode = EngineFeatureFlags.currentMode();

        // V1_ONLY: everything goes through V1 regardless of migration status
        if (mode == RoutingMode.V1_ONLY) {
            v1Invocations.incrementAndGet();
            return ActionRegistry.invokeWithResult(actionId, context);
        }

        // Not migrated: always use V1
        if (!migratedActionIds.contains(actionId)) {
            v1Invocations.incrementAndGet();
            return ActionRegistry.invokeWithResult(actionId, context);
        }

        // Action is migrated - route based on mode
        return switch (mode) {
            case SHADOW -> invokeShadow(actionId, context);
            case V2_ACTIVE -> invokeV2Active(actionId, context);
            case V2_ONLY -> invokeV2Only(actionId, context);
            case V1_ONLY -> throw new IllegalStateException("unreachable");
        };
    }

    /**
     * Shadow mode: V1 is primary, V2 runs alongside for comparison.
     */
    private ActionResult invokeShadow(String actionId, ActionContext context) {
        // V1 is the primary (its result is returned)
        ActionResult v1Result = ActionRegistry.invokeWithResult(actionId, context);
        v1Invocations.incrementAndGet();

        // V2 runs read-only alongside
        try {
            ActionResult v2Result = v2Executor.execute(actionId, context);
            shadowRuns.incrementAndGet();
            ShadowModeComparator.compare(actionId, v1Result, v2Result);
        } catch (Exception e) {
            shadowRuns.incrementAndGet();
            LOGGER.warn("[Bridge] Shadow V2 threw exception for '{}': {}",
                actionId, e.getMessage());
            // Compare with a failed result
            ActionResult failedV2 = ActionResult.failed(
                ActionResult.ERROR_EXCEPTION,
                Objects.requireNonNullElse(e.getMessage(), "Shadow V2 exception"),
                0
            );
            ShadowModeComparator.compare(actionId, v1Result, failedV2);
        }

        return v1Result;
    }

    /**
     * V2 Active mode: V2 is primary, falls back to V1 on V2 failure.
     */
    private ActionResult invokeV2Active(String actionId, ActionContext context) {
        try {
            ActionResult v2Result = v2Executor.execute(actionId, context);
            v2Invocations.incrementAndGet();

            if (v2Result.isFailed()) {
                // V2 execution failed - fall back to V1
                LOGGER.warn("[Bridge] V2 failed for '{}' ({}), falling back to V1",
                    actionId, v2Result.errorCode());
                v2Fallbacks.incrementAndGet();
                v1Invocations.incrementAndGet();
                return ActionRegistry.invokeWithResult(actionId, context);
            }

            return v2Result;
        } catch (Exception e) {
            // V2 threw - fall back to V1
            LOGGER.warn("[Bridge] V2 exception for '{}', falling back to V1: {}",
                actionId, e.getMessage());
            v2Fallbacks.incrementAndGet();
            v1Invocations.incrementAndGet();
            return ActionRegistry.invokeWithResult(actionId, context);
        }
    }

    /**
     * V2 Only mode: V2 is the sole path, no fallback.
     */
    private ActionResult invokeV2Only(String actionId, ActionContext context) {
        v2Invocations.incrementAndGet();
        return v2Executor.execute(actionId, context);
    }

    // =========================================================================
    // Migration management
    // =========================================================================

    /**
     * Marks an action as migrated to V2.
     */
    public void markMigrated(String actionId) {
        migratedActionIds.add(actionId);
        LOGGER.info("Action '{}' marked as migrated to V2", actionId);
    }

    /**
     * Removes an action from the migrated set (rolls it back to V1).
     */
    public void markUnmigrated(String actionId) {
        if (migratedActionIds.remove(actionId)) {
            LOGGER.info("Action '{}' rolled back to V1", actionId);
        }
    }

    /**
     * Returns true if the given action has been migrated to V2.
     */
    public boolean isMigrated(String actionId) {
        return migratedActionIds.contains(actionId);
    }

    /**
     * Returns the number of migrated actions.
     */
    public int migratedCount() {
        return migratedActionIds.size();
    }

    // =========================================================================
    // Metrics
    // =========================================================================

    /** Returns the total number of V1 invocations. */
    public long getV1Invocations() { return v1Invocations.get(); }

    /** Returns the total number of V2 invocations. */
    public long getV2Invocations() { return v2Invocations.get(); }

    /** Returns the number of times V2 failed and fell back to V1. */
    public long getV2Fallbacks() { return v2Fallbacks.get(); }

    /** Returns the number of shadow mode comparison runs. */
    public long getShadowRuns() { return shadowRuns.get(); }
}
