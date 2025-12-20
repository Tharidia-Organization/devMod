package com.devmod.arena.metrics;

import com.devmod.arena.telemetry.ArenaTelemetry;

import java.util.HashMap;
import java.util.Map;

/**
 * Telemetry wrapper that enforces context in all events (DD13).
 *
 * <p>All arena.build.* events MUST be emitted through this wrapper
 * to ensure consistent context inclusion.
 */
public class BuildTelemetry {

    private final ArenaTelemetry telemetry;
    private final ArenaMetricsContext context;

    public BuildTelemetry(ArenaTelemetry telemetry, ArenaMetricsContext context) {
        this.telemetry = telemetry;
        this.context = context;
    }

    /**
     * Emits build start event.
     */
    public void emitStart(int estimatedBlocks, long estimatedMs) {
        emit("arena.build.start", Map.of(
            "estimatedBlocks", estimatedBlocks,
            "estimatedMs", estimatedMs
        ));
    }

    /**
     * Emits build progress event.
     */
    public void emitProgress(int blocksPlaced, int totalBlocks, double percentComplete) {
        emit("arena.build.progress", Map.of(
            "blocksPlaced", blocksPlaced,
            "totalBlocks", totalBlocks,
            "percentComplete", percentComplete
        ));
    }

    /**
     * Emits build completion event.
     */
    public void emitComplete(int actualBlocks, long actualMs) {
        emit("arena.build.complete", Map.of(
            "actualBlocks", actualBlocks,
            "actualMs", actualMs,
            "blocksPerSecond", actualMs > 0 ? (actualBlocks * 1000.0 / actualMs) : 0
        ));
    }

    /**
     * Emits build failure event.
     */
    public void emitFailure(String reason, String errorClass, int blocksPlaced, long elapsedMs) {
        emit("arena.build.failure", Map.of(
            "reason", reason,
            "errorClass", errorClass,
            "blocksPlaced", blocksPlaced,
            "elapsedMs", elapsedMs
        ));
    }

    /**
     * Emits rollback event.
     */
    public void emitRollback(int blocksReverted, int entitiesRemoved, long rollbackMs, boolean success) {
        emit("arena.build.rollback", Map.of(
            "blocksReverted", blocksReverted,
            "entitiesRemoved", entitiesRemoved,
            "rollbackMs", rollbackMs,
            "success", success
        ));
    }

    /**
     * Emits chunk loading event.
     */
    public void emitChunkLoad(int chunksLoaded, long loadTimeMs, boolean success) {
        emit("arena.build.chunk_load", Map.of(
            "chunksLoaded", chunksLoaded,
            "loadTimeMs", loadTimeMs,
            "success", success
        ));
    }

    /**
     * Emits budget warning event.
     */
    public void emitBudgetWarning(String limitType, int current, int max, double ratio) {
        emit("arena.build.budget_warning", Map.of(
            "limitType", limitType,
            "current", current,
            "max", max,
            "ratio", ratio
        ));
    }

    /**
     * Emits backpressure event.
     */
    public void emitBackpressure(double currentMspt, int blocksPerTick, boolean triggered) {
        emit("arena.build.backpressure", Map.of(
            "currentMspt", currentMspt,
            "blocksPerTick", blocksPerTick,
            "triggered", triggered
        ));
    }

    /**
     * Emits a custom event with context.
     */
    public void emit(String eventName, Map<String, Object> additionalData) {
        Map<String, Object> fullData = new HashMap<>(context.toMap());
        fullData.putAll(additionalData);
        telemetry.emit(eventName, fullData);
    }

    /**
     * Returns the context for this telemetry instance.
     */
    public ArenaMetricsContext getContext() {
        return context;
    }

    /**
     * Creates a new BuildTelemetry with updated context.
     */
    public BuildTelemetry withContext(ArenaMetricsContext newContext) {
        return new BuildTelemetry(telemetry, newContext);
    }
}
