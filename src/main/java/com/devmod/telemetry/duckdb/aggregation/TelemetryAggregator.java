package com.devmod.telemetry.duckdb.aggregation;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.devmod.telemetry.duckdb.aggregation.AbilityAggregateWindow.AbilityAggregate;
import com.devmod.telemetry.duckdb.aggregation.CombatAggregateWindow.CombatAggregate;
import com.devmod.telemetry.duckdb.aggregation.HeatmapAggregateWindow.HeatmapAggregate;
import com.devmod.telemetry.duckdb.aggregation.SnapshotSampler.SnapshotData;
import com.devmod.telemetry.duckdb.lvc.TelemetryLVC;

/**
 * Per-player event aggregator.
 *
 * <p>Aggregates high-volume events (hits, abilities, heatmaps) into time windows,
 * reducing database writes by ~121x while maintaining real-time LVC updates.
 *
 * <p>Memory: ~5KB per player
 *
 * <p>Lifecycle:
 * <ol>
 *   <li>Created on player join via TelemetryAggregatorRegistry</li>
 *   <li>Events routed through process*() methods</li>
 *   <li>Periodic flush() every 60 seconds</li>
 *   <li>forceFlush() on player disconnect</li>
 *   <li>Destroyed after flush completes</li>
 * </ol>
 */
public class TelemetryAggregator {

    private static final Logger LOGGER = LoggerFactory.getLogger(TelemetryAggregator.class);

    private final UUID playerId;
    private final Instant createdAt;

    // ============================================
    // AGGREGATION WINDOWS
    // ============================================

    private final CombatAggregateWindow combatWindow;
    private final AbilityAggregateWindow abilityWindow;
    private final HeatmapAggregateWindow heatmapWindow;
    private final SnapshotSampler snapshotSampler;

    // ============================================
    // LVC REFERENCE
    // ============================================

    private final TelemetryLVC lvc;

    // ============================================
    // LIFECYCLE
    // ============================================

    private volatile boolean active = true;
    private Instant lastFlushTime;

    /** Events folded into a window; the denominator of the aggregation ratio. */
    private final AtomicLong eventsAggregated = new AtomicLong(0);

    // ============================================
    // CONTEXT
    // ============================================

    private UUID sessionId;
    private @Nullable UUID questId;

    /**
     * Create an aggregator for a player.
     *
     * @param playerId the player's UUID
     */
    public TelemetryAggregator(UUID playerId) {
        this(playerId, TelemetryLVC.INSTANCE);
    }

    /**
     * Create an aggregator with custom LVC (for testing).
     */
    public TelemetryAggregator(UUID playerId, TelemetryLVC lvc) {
        this.playerId = playerId;
        this.lvc = lvc;
        this.createdAt = Instant.now();
        this.lastFlushTime = Instant.now();
        this.sessionId = UUID.randomUUID();

        // Initialize windows
        this.combatWindow = new CombatAggregateWindow();
        this.abilityWindow = new AbilityAggregateWindow();
        this.heatmapWindow = new HeatmapAggregateWindow();
        this.snapshotSampler = new SnapshotSampler();

        // Set session context
        combatWindow.setSessionId(sessionId);
        abilityWindow.setSessionId(sessionId);
        heatmapWindow.setSessionId(sessionId);

        LOGGER.debug("[Aggregator] Created for player {}", playerId);
    }

    // ============================================
    // EVENT PROCESSING
    // ============================================

    /**
     * Process a combat hit event.
     *
     * <p>Updates LVC immediately for real-time queries.
     * Aggregates into combat window for batch write.
     *
     * @param damage damage dealt
     * @param isKill whether this hit killed the target
     * @param isCritical whether this was a critical hit
     * @param weapon weapon identifier
     * @param targetType target entity type
     * @return true if event should BYPASS aggregation and write immediately
     */
    public boolean processHit(double damage, boolean isKill, boolean isCritical,
                              String weapon, String targetType) {
        if (!active) return true;  // Bypass if shutting down

        // Update LVC immediately
        lvc.recordHit(playerId, damage, isKill, isCritical, weapon);

        // Aggregate
        combatWindow.recordHit(damage, isKill, isCritical, weapon, targetType);
        eventsAggregated.incrementAndGet();

        return false;  // Aggregated, don't write immediately
    }

    /**
     * Process a combat miss event.
     */
    public void processMiss() {
        if (!active) return;
        combatWindow.recordMiss();
        eventsAggregated.incrementAndGet();
    }

    /**
     * Process an ability usage event.
     *
     * @param abilityType type of ability
     * @param success whether the ability succeeded
     * @param staminaCost stamina spent
     * @param damageNegated damage negated (for defensive abilities)
     * @return true if should bypass aggregation
     */
    public boolean processAbility(String abilityType, boolean success,
                                  double staminaCost, double damageNegated) {
        if (!active) return true;

        // Update LVC
        lvc.recordAbility(playerId, abilityType, success, staminaCost, damageNegated);

        // Aggregate
        abilityWindow.recordAbility(abilityType, success, staminaCost, damageNegated);
        eventsAggregated.incrementAndGet();

        return false;
    }

    /**
     * Process XP gain.
     *
     * @param amount XP earned
     * @param source XP source
     * @return true if should bypass (XP is always aggregated)
     */
    public boolean processXp(int amount, String source) {
        if (!active) return true;

        // Update LVC
        lvc.recordXp(playerId, amount);

        // Note: XP is already batched at the TelemetryService level,
        // so we don't need additional aggregation here
        return true;  // Let existing XP batching handle it
    }

    /**
     * Process heatmap position.
     *
     * @param x world X coordinate
     * @param y world Y coordinate
     * @param z world Z coordinate
     * @param type heatmap type
     * @return true if should bypass
     */
    public boolean processHeatmap(int x, int y, int z, String type) {
        if (!active) return true;

        heatmapWindow.recordPosition(x, y, z, type);
        eventsAggregated.incrementAndGet();

        return false;
    }

    /**
     * Process player snapshot.
     *
     * @param snapshot snapshot data
     * @return true if snapshot was accepted (should write), false if rate-limited
     */
    public boolean processSnapshot(SnapshotData snapshot) {
        if (!active) return true;

        return snapshotSampler.tryRecord(snapshot);
    }

    /**
     * Process wave start event.
     * Updates LVC and context - always bypasses aggregation.
     */
    public void processWaveStart(int waveNumber) {
        lvc.recordWaveStart(playerId, waveNumber);
    }

    /**
     * Process wave complete event.
     * Updates LVC - always bypasses aggregation.
     */
    public void processWaveComplete(int waveNumber) {
        lvc.recordWaveComplete(playerId, waveNumber);
    }

    /**
     * Process death event.
     * Updates LVC - always bypasses aggregation.
     */
    public void processDeath() {
        lvc.recordDeath(playerId);
    }

    /**
     * Process combo break.
     * Updates LVC.
     */
    public void processComboBreak() {
        lvc.recordComboBreak(playerId);
    }

    // ============================================
    // FLUSH OPERATIONS
    // ============================================

    /**
     * Flush all windows that are ready.
     * Called periodically by TelemetryAggregatorRegistry.
     *
     * @return list of aggregated events to write to database
     */
    public List<AggregatedEvent> flush() {
        List<AggregatedEvent> events = new ArrayList<>();

        // Flush combat window if ready
        if (combatWindow.shouldFlush()) {
            CombatAggregate combat = combatWindow.flush();
            events.add(new AggregatedEvent(
                AggregatedEvent.Type.COMBAT,
                playerId,
                combat
            ));

            if (AggregationConfig.LOG_FLUSH_OPERATIONS) {
                LOGGER.debug("[Aggregator] Flushed combat: {} hits, {} damage for {}",
                    combat.hitCount(), combat.totalDamage(), playerId);
            }
        }

        // Flush ability window if ready
        if (abilityWindow.shouldFlush()) {
            AbilityAggregate abilities = abilityWindow.flush();
            events.add(new AggregatedEvent(
                AggregatedEvent.Type.ABILITY,
                playerId,
                abilities
            ));

            if (AggregationConfig.LOG_FLUSH_OPERATIONS) {
                LOGGER.debug("[Aggregator] Flushed abilities: {} attempts for {}",
                    abilities.getTotalAttempts(), playerId);
            }
        }

        // Flush heatmap window if ready
        if (heatmapWindow.shouldFlush()) {
            HeatmapAggregate heatmap = heatmapWindow.flush();
            events.add(new AggregatedEvent(
                AggregatedEvent.Type.HEATMAP,
                playerId,
                heatmap
            ));

            if (AggregationConfig.LOG_FLUSH_OPERATIONS) {
                LOGGER.debug("[Aggregator] Flushed heatmap: {} samples for {}",
                    heatmap.totalSamples(), playerId);
            }
        }

        lastFlushTime = Instant.now();
        return events;
    }

    /**
     * Force flush all windows regardless of time.
     * Called on player disconnect to ensure no data loss.
     *
     * @return list of all aggregated events
     */
    public List<AggregatedEvent> forceFlush() {
        List<AggregatedEvent> events = new ArrayList<>();

        // Force flush combat if has data
        if (!combatWindow.isEmpty()) {
            CombatAggregate combat = combatWindow.flush();
            events.add(new AggregatedEvent(
                AggregatedEvent.Type.COMBAT,
                playerId,
                combat
            ));
        }

        // Force flush abilities if has data
        if (!abilityWindow.isEmpty()) {
            AbilityAggregate abilities = abilityWindow.flush();
            events.add(new AggregatedEvent(
                AggregatedEvent.Type.ABILITY,
                playerId,
                abilities
            ));
        }

        // Force flush heatmap if has data
        if (!heatmapWindow.isEmpty()) {
            HeatmapAggregate heatmap = heatmapWindow.flush();
            events.add(new AggregatedEvent(
                AggregatedEvent.Type.HEATMAP,
                playerId,
                heatmap
            ));
        }

        LOGGER.debug("[Aggregator] Force flushed {} events for player {}", events.size(), playerId);
        return events;
    }

    // ============================================
    // CONTEXT MANAGEMENT
    // ============================================

    /**
     * Set quest context (called when quest starts).
     */
    public void setQuestContext(@Nullable UUID questId, @Nullable String templateId, @Nullable Integer templateVersion) {
        this.questId = questId;

        combatWindow.setQuestId(questId);
        combatWindow.setTemplateInfo(templateId, templateVersion);
        heatmapWindow.setTemplateId(templateId);

        // Reset session for new quest
        this.sessionId = UUID.randomUUID();
        combatWindow.setSessionId(sessionId);
        abilityWindow.setSessionId(sessionId);
        heatmapWindow.setSessionId(sessionId);

        // Notify LVC of quest start
        if (questId != null) {
            lvc.recordQuestStart(playerId, questId);
        }
    }

    /**
     * Clear quest context (called when quest ends).
     */
    public List<AggregatedEvent> clearQuestContext() {
        // Force flush quest data; the caller is responsible for persisting it.
        List<AggregatedEvent> pending = forceFlush();

        this.questId = null;

        // Start new session
        this.sessionId = UUID.randomUUID();
        combatWindow.setSessionId(sessionId);
        abilityWindow.setSessionId(sessionId);
        heatmapWindow.setSessionId(sessionId);

        return pending;
    }

    // ============================================
    // LIFECYCLE
    // ============================================

    /**
     * Shutdown aggregator.
     */
    public void shutdown() {
        active = false;
        LOGGER.debug("[Aggregator] Shutdown for player {}", playerId);
    }

    public boolean isActive() {
        return active;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getLastFlushTime() {
        return lastFlushTime;
    }

    /**
     * Number of events folded into aggregation windows over this aggregator's lifetime.
     * Events that bypass aggregation (XP, snapshots, LVC-only events) are not counted.
     */
    public long getEventsAggregated() {
        return eventsAggregated.get();
    }

    public UUID getSessionId() {
        return sessionId;
    }

    public @Nullable UUID getQuestId() {
        return questId;
    }

    // ============================================
    // MEMORY ESTIMATION
    // ============================================

    /**
     * Estimate memory usage in bytes.
     */
    public long getMemoryEstimate() {
        // ~5KB per aggregator
        return 5 * 1024;
    }

    // ============================================
    // STATISTICS
    // ============================================

    /**
     * Get aggregator statistics for monitoring.
     */
    public AggregatorStats getStats() {
        return new AggregatorStats(
            playerId,
            createdAt,
            lastFlushTime,
            combatWindow.getHitCount(),
            combatWindow.getTotalDamage(),
            abilityWindow.getTotalAttempts(),
            heatmapWindow.getTotalSamples(),
            snapshotSampler.getAcceptedCount(),
            snapshotSampler.getDroppedCount()
        );
    }

    // ============================================
    // INNER CLASSES
    // ============================================

    /**
     * Aggregated event wrapper for database writing.
     */
    public record AggregatedEvent(
        Type type,
        UUID playerId,
        Object data  // CombatAggregate, AbilityAggregate, or HeatmapAggregate
    ) {
        public enum Type {
            COMBAT,
            ABILITY,
            HEATMAP
        }

        public CombatAggregate asCombat() {
            return (CombatAggregate) data;
        }

        public AbilityAggregate asAbility() {
            return (AbilityAggregate) data;
        }

        public HeatmapAggregate asHeatmap() {
            return (HeatmapAggregate) data;
        }
    }

    /**
     * Aggregator statistics for monitoring.
     */
    public record AggregatorStats(
        UUID playerId,
        Instant createdAt,
        Instant lastFlushTime,
        int pendingHits,
        double pendingDamage,
        int pendingAbilities,
        int pendingHeatmapSamples,
        int snapshotsAccepted,
        int snapshotsDropped
    ) {}
}
