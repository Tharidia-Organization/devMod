package com.devmod.arena.telemetry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Telemetry service for Arena events.
 *
 * <p>Implements DD4 (Weight Taratura) - emits detailed scoring events for analysis.
 * <p>Implements DD20 (NDJSON Write Non-blocking) - async buffer with non-blocking offer.
 *
 * @see <a href="TODO_ARENA_TEMPLATE.md">Arena Template Design Document</a>
 */
public class ArenaTelemetry {
    private static final Logger LOGGER = LoggerFactory.getLogger(ArenaTelemetry.class);
    private static final int BUFFER_SIZE = 10_000;
    private static volatile Consumer<TelemetryEvent> globalHandler;

    private final BlockingQueue<TelemetryEvent> buffer = new LinkedBlockingQueue<>(BUFFER_SIZE);
    private final AtomicLong droppedCount = new AtomicLong(0);
    private final AtomicLong emittedCount = new AtomicLong(0);

    private Consumer<TelemetryEvent> eventHandler;
    private volatile boolean enabled = true;

    public ArenaTelemetry() {
        Consumer<TelemetryEvent> handler = globalHandler;
        this.eventHandler = handler != null ? handler : this::defaultHandler;
    }

    public ArenaTelemetry(Consumer<TelemetryEvent> eventHandler) {
        this.eventHandler = eventHandler != null ? eventHandler : this::defaultHandler;
    }

    /**
     * Sets a global handler used by default constructors.
     */
    public static void setGlobalHandler(Consumer<TelemetryEvent> handler) {
        globalHandler = handler;
    }

    /**
     * Emits a telemetry event with the given name and data.
     *
     * @param eventName The event name (e.g., "arena.template.loaded")
     * @param data The event data
     */
    public void emit(String eventName, Map<String, Object> data) {
        if (!enabled) return;

        TelemetryEvent event = new TelemetryEvent(
            eventName,
            Instant.now(),
            new LinkedHashMap<>(data)
        );

        // Non-blocking offer (DD20)
        boolean accepted = buffer.offer(event);
        if (!accepted) {
            long dropped = droppedCount.incrementAndGet();
            if (dropped % 1000 == 0) {
                LOGGER.warn("Telemetry buffer full, dropped {} events total", dropped);
            }
        } else {
            emittedCount.incrementAndGet();
            // Process immediately for now (can be made async with background thread)
            try {
                TelemetryEvent e = buffer.poll();
                if (e != null) {
                    eventHandler.accept(e);
                }
            } catch (Exception e) {
                LOGGER.debug("Error processing telemetry event: {}", e.getMessage());
            }
        }
    }

    /**
     * Emits a policy resolution event with detailed scoring for weight taratura (DD4).
     */
    public void emitPolicyResolved(
            String policyId,
            String templateId,
            double finalScore,
            Map<String, Double> scoringDetails,
            int alternativeCount,
            String topAlternative,
            double scoreDelta) {

        emit("arena.policy.resolved", Map.of(
            "policyId", policyId,
            "templateId", templateId,
            "score", finalScore,
            "scoringDetails", scoringDetails,
            "alternativeCount", alternativeCount,
            "topAlternative", topAlternative != null ? topAlternative : "",
            "scoreDelta", scoreDelta
        ));
    }

    /**
     * Emits a lock timeout event (DD6).
     */
    public void emitLockTimeout(java.util.UUID playerId, long timeoutMs) {
        emit("arena.resolve.lock_timeout", Map.of(
            "playerId", playerId.toString(),
            "timeoutMs", timeoutMs
        ));
    }

    /**
     * Emits a lock contention event for bottleneck analysis (DD62).
     */
    public void emitLockContention(java.util.UUID playerId, String templateId, long waitTimeMs) {
        emit("arena.resolve.lock_contention", Map.of(
            "playerId", playerId.toString(),
            "templateId", templateId,
            "waitTimeMs", waitTimeMs
        ));
    }

    /**
     * Emits an override cleared event (DD5).
     */
    public void emitOverrideCleared(java.util.UUID playerId, String reason, String outcome) {
        emit("arena.override.cleared", Map.of(
            "playerId", playerId.toString(),
            "reason", reason,
            "outcome", outcome != null ? outcome : ""
        ));
    }

    /**
     * Emits an override set event (DD5).
     */
    public void emitOverrideSet(java.util.UUID playerId, String templateId, String policyId, String scope, String source) {
        emit("arena.override.set", Map.of(
            "playerId", playerId.toString(),
            "templateId", templateId,
            "policyId", policyId != null ? policyId : "",
            "scope", scope,
            "source", source
        ));
    }

    /**
     * Sets a custom event handler.
     */
    public void setEventHandler(Consumer<TelemetryEvent> handler) {
        this.eventHandler = handler != null ? handler : this::defaultHandler;
    }

    /**
     * Enables or disables telemetry.
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * Returns the count of dropped events due to buffer overflow.
     */
    public long getDroppedCount() {
        return droppedCount.get();
    }

    /**
     * Returns the count of emitted events.
     */
    public long getEmittedCount() {
        return emittedCount.get();
    }

    /**
     * Returns telemetry stats.
     */
    public Map<String, Long> getStats() {
        return Map.of(
            "emitted", emittedCount.get(),
            "dropped", droppedCount.get(),
            "bufferSize", (long) buffer.size()
        );
    }

    private void defaultHandler(TelemetryEvent event) {
        LOGGER.debug("[TELEMETRY] {} - {}", event.name(), event.data());
    }

    /**
     * Telemetry event record.
     */
    public record TelemetryEvent(
        String name,
        Instant timestamp,
        Map<String, Object> data
    ) {
        public Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("event", name);
            map.put("ts", timestamp.toString());
            map.putAll(data);
            return map;
        }
    }
}
