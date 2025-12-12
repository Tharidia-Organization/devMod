package com.frenkvs.devmod.telemetry.duckdb.packets;

import com.frenkvs.devmod.telemetry.duckdb.packets.TelemetryBatchPayload.CompressedEvent;
import com.frenkvs.devmod.telemetry.duckdb.packets.TelemetryBatchPayload.EventType;
import com.mojang.logging.LogUtils;
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.Logger;

import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Client-side buffer for telemetry events in multiplayer.
 *
 * Collects events locally and sends them to the server in batches.
 * This avoids spamming the network with individual telemetry packets.
 *
 * Drain conditions:
 * - Every 10 seconds (configurable)
 * - When buffer reaches 50 events
 * - On client disconnect (flush remaining)
 *
 * Thread-safe for use from any thread (render, tick, network).
 */
public class ClientTelemetryBuffer {
    private static final Logger LOGGER = LogUtils.getLogger();

    public static final ClientTelemetryBuffer INSTANCE = new ClientTelemetryBuffer();

    // Configuration
    private static final int MAX_BUFFER_SIZE = 500;
    private static final int DRAIN_THRESHOLD = 50;
    private static final long DRAIN_INTERVAL_MS = 10_000; // 10 seconds

    // State
    private final ConcurrentLinkedQueue<BufferedEvent> eventQueue = new ConcurrentLinkedQueue<>();
    private final AtomicLong lastDrainTime = new AtomicLong(System.currentTimeMillis());
    private final AtomicLong eventCount = new AtomicLong(0);
    private volatile boolean enabled = false;
    private volatile boolean connected = false;

    private ClientTelemetryBuffer() {}

    /**
     * Enable the buffer (called when joining multiplayer server).
     */
    public void enable() {
        this.enabled = true;
        this.connected = true;
        this.lastDrainTime.set(System.currentTimeMillis());
        LOGGER.info("[ClientTelemetry] Buffer enabled for multiplayer");
    }

    /**
     * Disable the buffer (called on disconnect or singleplayer).
     */
    public void disable() {
        if (enabled) {
            // Flush remaining events before disabling
            flush();
        }
        this.enabled = false;
        this.connected = false;
        eventQueue.clear();
        eventCount.set(0);
        LOGGER.info("[ClientTelemetry] Buffer disabled");
    }

    /**
     * Check if buffer is active (multiplayer mode).
     */
    public boolean isEnabled() {
        return enabled && connected;
    }

    /**
     * Queue a telemetry event for sending to server.
     *
     * @param type Event type
     * @param jsonData Event data as JSON string
     */
    public void queueEvent(EventType type, String jsonData) {
        if (!enabled || !connected) return;

        // Drop oldest events if buffer is full
        while (eventCount.get() >= MAX_BUFFER_SIZE) {
            BufferedEvent dropped = eventQueue.poll();
            if (dropped != null) {
                eventCount.decrementAndGet();
            } else {
                break;
            }
        }

        // Add new event
        eventQueue.add(new BufferedEvent(type, System.currentTimeMillis(), jsonData));
        long count = eventCount.incrementAndGet();

        // Check if we should drain
        if (count >= DRAIN_THRESHOLD) {
            drain();
        }
    }

    /**
     * Called every client tick to check for time-based drain.
     */
    public void tick() {
        if (!enabled || !connected) return;

        long now = System.currentTimeMillis();
        long lastDrain = lastDrainTime.get();

        if (now - lastDrain >= DRAIN_INTERVAL_MS && !eventQueue.isEmpty()) {
            drain();
        }
    }

    /**
     * Drain the buffer and send events to server.
     */
    public void drain() {
        if (!connected || eventQueue.isEmpty()) return;

        lastDrainTime.set(System.currentTimeMillis());

        TelemetryBatchPayload.Builder builder = new TelemetryBatchPayload.Builder();
        int drained = 0;

        while (!builder.isFull() && !eventQueue.isEmpty()) {
            BufferedEvent event = eventQueue.poll();
            if (event != null) {
                eventCount.decrementAndGet();
                int deltaMs = (int) (event.timestamp - builder.build().batchTimestamp());
                builder.addEvent(CompressedEvent.fromString(event.type, Math.max(0, deltaMs), event.jsonData));
                drained++;
            }
        }

        if (drained > 0) {
            TelemetryBatchPayload payload = builder.build();
            try {
                PacketDistributor.sendToServer(Objects.requireNonNull(payload));
                LOGGER.debug("[ClientTelemetry] Sent batch of {} events to server", drained);
            } catch (Exception e) {
                LOGGER.warn("[ClientTelemetry] Failed to send batch: {}", e.getMessage());
                // Events are lost on failure - acceptable for telemetry
            }
        }
    }

    /**
     * Force flush all remaining events (called on disconnect).
     */
    public void flush() {
        if (!connected) return;

        int totalFlushed = 0;
        while (!eventQueue.isEmpty()) {
            TelemetryBatchPayload.Builder builder = new TelemetryBatchPayload.Builder();

            while (!builder.isFull() && !eventQueue.isEmpty()) {
                BufferedEvent event = eventQueue.poll();
                if (event != null) {
                    eventCount.decrementAndGet();
                    int deltaMs = (int) (event.timestamp - builder.build().batchTimestamp());
                    builder.addEvent(CompressedEvent.fromString(event.type, Math.max(0, deltaMs), event.jsonData));
                    totalFlushed++;
                }
            }

            if (!builder.isEmpty()) {
                try {
                    PacketDistributor.sendToServer(Objects.requireNonNull(builder.build()));
                } catch (Exception e) {
                    LOGGER.warn("[ClientTelemetry] Failed to flush: {}", e.getMessage());
                    break;
                }
            }
        }

        if (totalFlushed > 0) {
            LOGGER.info("[ClientTelemetry] Flushed {} events on disconnect", totalFlushed);
        }
    }

    /**
     * Get current buffer size.
     */
    public int getBufferSize() {
        return (int) eventCount.get();
    }

    /**
     * Get buffer capacity percentage (0-100).
     */
    public int getBufferUsagePercent() {
        return (int) (eventCount.get() * 100 / MAX_BUFFER_SIZE);
    }

    /**
     * Internal buffered event structure.
     */
    private record BufferedEvent(EventType type, long timestamp, String jsonData) {}

    // ============================================
    // CONVENIENCE METHODS FOR SPECIFIC EVENT TYPES
    // ============================================

    /**
     * Queue a combat hit event.
     */
    public void queueCombatHit(String jsonData) {
        queueEvent(EventType.COMBAT_HIT, jsonData);
    }

    /**
     * Queue a combat death event.
     */
    public void queueCombatDeath(String jsonData) {
        queueEvent(EventType.COMBAT_DEATH, jsonData);
    }

    /**
     * Queue a player snapshot event.
     */
    public void queuePlayerSnapshot(String jsonData) {
        queueEvent(EventType.PLAYER_SNAPSHOT, jsonData);
    }

    /**
     * Queue a player ability event.
     */
    public void queuePlayerAbility(String jsonData) {
        queueEvent(EventType.PLAYER_ABILITY, jsonData);
    }

    /**
     * Queue a heatmap update event.
     */
    public void queueHeatmapUpdate(String jsonData) {
        queueEvent(EventType.HEATMAP_UPDATE, jsonData);
    }

    /**
     * Queue a room transition event.
     */
    public void queueRoomTransition(String jsonData) {
        queueEvent(EventType.ROOM_TRANSITION, jsonData);
    }

    /**
     * Queue an endurance session event.
     */
    public void queueEnduranceSession(String jsonData) {
        queueEvent(EventType.ENDURANCE_SESSION, jsonData);
    }

    /**
     * Queue an endurance wave event.
     */
    public void queueEnduranceWave(String jsonData) {
        queueEvent(EventType.ENDURANCE_WAVE, jsonData);
    }

    /**
     * Queue an endurance kill event.
     */
    public void queueEnduranceKill(String jsonData) {
        queueEvent(EventType.ENDURANCE_KILL, jsonData);
    }

    /**
     * Queue a performance sample event.
     */
    public void queuePerformanceSample(String jsonData) {
        queueEvent(EventType.PERFORMANCE_SAMPLE, jsonData);
    }
}
