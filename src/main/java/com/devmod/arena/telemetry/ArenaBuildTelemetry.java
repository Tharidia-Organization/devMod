package com.devmod.arena.telemetry;

import java.util.HashMap;
import java.util.Map;

import com.devmod.arena.metrics.ArenaMetricsContext;

/**
 * Telemetry for arena build operations with contention tracking (DD62).
 *
 * <p>All events include:
 * <ul>
 *   <li>waitTimeMs - time spent waiting for permit</li>
 *   <li>templateId - for bottleneck analysis</li>
 * </ul>
 */
public class ArenaBuildTelemetry {

    private static final long CONTENTION_THRESHOLD_MS = 1000;
    private static final int HIGH_CONTENTION_WAITING = 2;

    private final ArenaTelemetry telemetry;
    private final ArenaMetricsContext context;

    public ArenaBuildTelemetry(ArenaTelemetry telemetry, ArenaMetricsContext context) {
        this.telemetry = telemetry;
        this.context = context;
    }

    /**
     * Emits build permit requested event.
     * DD62: Includes templateId for bottleneck analysis.
     */
    public void emitPermitRequested(String requesterId, String templateId) {
        Map<String, Object> data = new HashMap<>();
        data.put("requesterId", requesterId);
        data.put("templateId", templateId);
        emit("arena.build.permit_requested", data);
    }

    /**
     * Emits build permit granted event with wait time.
     * DD62: Includes templateId and waitTimeMs for bottleneck analysis.
     */
    public void emitPermitGranted(String permitId, String templateId, long waitTimeMs) {
        Map<String, Object> data = new HashMap<>();
        data.put("permitId", permitId);
        data.put("templateId", templateId);
        data.put("waitTimeMs", waitTimeMs);

        emit("arena.build.permit_granted", data);

        // Emit contention warning if wait was significant
        if (waitTimeMs > CONTENTION_THRESHOLD_MS) {
            emitContention(templateId, waitTimeMs, determineSeverity(waitTimeMs));
        }
    }

    /**
     * Emits build permit rejected event.
     * DD62: Includes templateId for bottleneck analysis.
     */
    public void emitPermitRejected(String templateId, String reason, long retryAfterMs, int queuePosition) {
        Map<String, Object> data = new HashMap<>();
        data.put("templateId", templateId);
        data.put("reason", reason);
        data.put("retryAfterMs", retryAfterMs);
        data.put("queuePosition", queuePosition);
        emit("arena.build.permit_rejected", data);
    }

    /**
     * Emits contention event for high wait times (DD62).
     * DD62: Includes templateId and waitTimeMs for bottleneck analysis.
     */
    public void emitContention(String templateId, long waitTimeMs, ContentionSeverity severity) {
        Map<String, Object> data = new HashMap<>();
        data.put("templateId", templateId);
        data.put("waitTimeMs", waitTimeMs);
        data.put("severity", severity.name());
        data.put("threshold", CONTENTION_THRESHOLD_MS);
        emit("arena.build.contention", data);
    }

    /**
     * Emits queue status event.
     */
    public void emitQueueStatus(int activeBuilds, int queueSize, int waitingCount) {
        Map<String, Object> data = new HashMap<>();
        data.put("activeBuilds", activeBuilds);
        data.put("queueSize", queueSize);
        data.put("waitingCount", waitingCount);
        data.put("highContention", waitingCount > HIGH_CONTENTION_WAITING);

        emit("arena.build.queue_status", data);
    }

    /**
     * Emits lock acquired event.
     */
    public void emitLockAcquired(String templateId, String owner, long waitTimeMs) {
        emit("arena.build.lock_acquired", Map.of(
            "lockedTemplateId", templateId,
            "owner", owner,
            "waitTimeMs", waitTimeMs
        ));
    }

    /**
     * Emits lock released event.
     */
    public void emitLockReleased(String templateId, String owner, long heldForMs) {
        emit("arena.build.lock_released", Map.of(
            "lockedTemplateId", templateId,
            "owner", owner,
            "heldForMs", heldForMs
        ));
    }

    /**
     * Emits lock timeout event.
     */
    public void emitLockTimeout(String templateId, String owner, long waitTimeMs) {
        emit("arena.build.lock_timeout", Map.of(
            "lockedTemplateId", templateId,
            "owner", owner,
            "waitTimeMs", waitTimeMs
        ));
    }

    /**
     * Returns the context for this telemetry instance.
     */
    public ArenaMetricsContext getContext() {
        return context;
    }

    /**
     * Creates a new telemetry instance with updated context.
     */
    public ArenaBuildTelemetry withContext(ArenaMetricsContext newContext) {
        return new ArenaBuildTelemetry(telemetry, newContext);
    }

    private void emit(String eventName, Map<String, Object> additionalData) {
        Map<String, Object> fullData = new HashMap<>(context.toMap());
        fullData.putAll(additionalData);
        telemetry.emit(eventName, fullData);
    }

    private ContentionSeverity determineSeverity(long waitTimeMs) {
        if (waitTimeMs > 10000) {
            return ContentionSeverity.CRITICAL;
        } else if (waitTimeMs > 5000) {
            return ContentionSeverity.HIGH;
        } else if (waitTimeMs > 2000) {
            return ContentionSeverity.MEDIUM;
        } else {
            return ContentionSeverity.LOW;
        }
    }

    /**
     * Contention severity levels.
     */
    public enum ContentionSeverity {
        /** Wait time 1-2 seconds */
        LOW,
        /** Wait time 2-5 seconds */
        MEDIUM,
        /** Wait time 5-10 seconds */
        HIGH,
        /** Wait time >10 seconds */
        CRITICAL
    }
}
