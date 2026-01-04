package com.devmod.client.endurance.wis;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

/**
 * Collects and aggregates combat telemetry during a single wave.
 * This is the "heart" of the Wave Intelligence System - all data flows through here.
 *
 * Thread-safe for concurrent event recording from multiple sources.
 * Data is collected during COMBAT phase and analyzed during DEBRIEF phase.
 */
@SuppressWarnings("NonAtomicVolatileUpdate") // Client-side counters, main thread access only
public class WaveTelemetryCollector {
    private static final Logger LOGGER = LoggerFactory.getLogger(WaveTelemetryCollector.class);

    // Maximum events to store per wave to prevent memory issues
    private static final int MAX_EVENTS = 10_000;
    private static final int POSITION_SNAPSHOT_INTERVAL_TICKS = 10; // Every 0.5 seconds

    // Wave identification
    private final UUID questId;
    private final int waveNumber;
    private final long waveStartTimeMs;
    private final int waveStartTick;

    // Event storage (thread-safe)
    private final List<CombatEvent> events = new CopyOnWriteArrayList<>();

    // Real-time aggregates (updated on each event)
    private volatile int totalKills = 0;
    private volatile int eliteKills = 0;
    private volatile float totalDamageDealt = 0f;
    private volatile float totalDamageTaken = 0f;
    private volatile int maxCombo = 0;
    private volatile int currentCombo = 0;
    private volatile int criticalHits = 0;
    private volatile int dodges = 0;
    private volatile int parries = 0;

    // Per-mob type statistics
    private final Map<ResourceLocation, MobTypeStats> mobStats = Collections.synchronizedMap(new HashMap<>());

    // Per-weapon statistics
    private final Map<String, WeaponStats> weaponStats = Collections.synchronizedMap(new HashMap<>());

    // Spatial data for heatmaps
    private final List<BlockPos> playerPositions = new CopyOnWriteArrayList<>();
    private final List<BlockPos> killPositions = new CopyOnWriteArrayList<>();
    private final List<BlockPos> deathPositions = new CopyOnWriteArrayList<>();
    private final List<BlockPos> damagePositions = new CopyOnWriteArrayList<>();

    // Time tracking
    private volatile int lastPositionSnapshotTick = 0;
    private volatile long firstKillTimeMs = -1;
    private volatile long lastKillTimeMs = -1;

    // State
    private volatile boolean collecting = true;

    public WaveTelemetryCollector(UUID questId, int waveNumber, int currentTick) {
        this.questId = questId;
        this.waveNumber = waveNumber;
        this.waveStartTimeMs = System.currentTimeMillis();
        this.waveStartTick = currentTick;
    }

    // ========== Event Recording ==========

    /**
     * Record a mob kill.
     */
    public void recordKill(UUID mobId, ResourceLocation mobType, BlockPos position,
                           float damageDealt, String weaponUsed, boolean isElite,
                           boolean isCritical, int comboCount, long timeToKillMs, int currentTick) {
        if (!collecting || events.size() >= MAX_EVENTS) return;

        int ticksSinceStart = currentTick - waveStartTick;
        CombatEvent.KillEvent event = new CombatEvent.KillEvent(
            ticksSinceStart, mobId, mobType, position, damageDealt,
            weaponUsed, isElite, isCritical, comboCount, timeToKillMs
        );
        events.add(event);

        // Update aggregates
        totalKills++;
        if (isElite) eliteKills++;
        if (isCritical) criticalHits++;
        totalDamageDealt += damageDealt;
        if (comboCount > maxCombo) maxCombo = comboCount;
        currentCombo = comboCount;

        // Time tracking
        long now = System.currentTimeMillis();
        if (firstKillTimeMs < 0) firstKillTimeMs = now;
        lastKillTimeMs = now;

        // Spatial data
        killPositions.add(position);

        // Per-mob stats
        MobTypeStats stats = mobStats.computeIfAbsent(mobType, k -> new MobTypeStats());
        stats.kills++;
        stats.totalDamageDealt += damageDealt;
        stats.totalTimeToKillMs += timeToKillMs;
        if (isElite) stats.eliteKills++;

        // Per-weapon stats
        WeaponStats wStats = weaponStats.computeIfAbsent(weaponUsed, k -> new WeaponStats());
        wStats.kills++;
        wStats.totalDamage += damageDealt;
        if (isCritical) wStats.criticalHits++;
    }

    /**
     * Record damage dealt to a mob.
     */
    public void recordDamageDealt(UUID mobId, ResourceLocation mobType, float damage,
                                   String damageSource, boolean isCritical, BlockPos position,
                                   int currentTick) {
        if (!collecting || events.size() >= MAX_EVENTS) return;

        int ticksSinceStart = currentTick - waveStartTick;
        CombatEvent.DamageDealtEvent event = new CombatEvent.DamageDealtEvent(
            ticksSinceStart, mobId, mobType, damage, damageSource, isCritical, position
        );
        events.add(event);

        totalDamageDealt += damage;
        if (isCritical) criticalHits++;

        // Per-mob stats
        MobTypeStats stats = mobStats.computeIfAbsent(mobType, k -> new MobTypeStats());
        stats.totalDamageDealt += damage;
    }

    /**
     * Record damage taken by player.
     */
    public void recordDamageTaken(@Nullable UUID sourceId, @Nullable ResourceLocation sourceType,
                                   float damage, String damageSource, float remainingHealth,
                                   BlockPos playerPosition, int currentTick) {
        if (!collecting || events.size() >= MAX_EVENTS) return;

        int ticksSinceStart = currentTick - waveStartTick;
        CombatEvent.DamageTakenEvent event = new CombatEvent.DamageTakenEvent(
            ticksSinceStart, sourceId, sourceType, damage, damageSource,
            remainingHealth, playerPosition
        );
        events.add(event);

        totalDamageTaken += damage;
        damagePositions.add(playerPosition);

        // Per-mob stats (damage dealt TO player)
        if (sourceType != null) {
            MobTypeStats stats = mobStats.computeIfAbsent(sourceType, k -> new MobTypeStats());
            stats.totalDamageToPlayer += damage;
        }
    }

    /**
     * Record combo event (milestone, rank change, break).
     */
    public void recordComboEvent(CombatEvent.ComboEvent.ComboEventType type, int comboCount,
                                  int styleScore, String rankName, int currentTick) {
        if (!collecting || events.size() >= MAX_EVENTS) return;

        int ticksSinceStart = currentTick - waveStartTick;
        CombatEvent.ComboEvent event = new CombatEvent.ComboEvent(
            ticksSinceStart, type, comboCount, styleScore, rankName
        );
        events.add(event);

        if (comboCount > maxCombo) maxCombo = comboCount;
        currentCombo = comboCount;
    }

    /**
     * Record periodic position snapshot.
     */
    public void recordPositionSnapshot(BlockPos position, float health, int mobsNearby,
                                        boolean inCombat, int currentTick) {
        if (!collecting) return;

        // Rate limit position snapshots
        if (currentTick - lastPositionSnapshotTick < POSITION_SNAPSHOT_INTERVAL_TICKS) {
            return;
        }
        lastPositionSnapshotTick = currentTick;

        if (events.size() < MAX_EVENTS) {
            int ticksSinceStart = currentTick - waveStartTick;
            CombatEvent.PositionSnapshot event = new CombatEvent.PositionSnapshot(
                ticksSinceStart, position, health, mobsNearby, inCombat
            );
            events.add(event);
        }

        playerPositions.add(position);
    }

    /**
     * Record special action (dodge, parry, counter).
     */
    public void recordSpecialAction(String actionType, int pointsEarned, int styleEarned,
                                     BlockPos position, int currentTick) {
        if (!collecting || events.size() >= MAX_EVENTS) return;

        int ticksSinceStart = currentTick - waveStartTick;
        CombatEvent.SpecialActionEvent event = new CombatEvent.SpecialActionEvent(
            ticksSinceStart, actionType, pointsEarned, styleEarned, position
        );
        events.add(event);

        switch (actionType.toLowerCase(java.util.Locale.ROOT)) {
            case "dodge", "perfect_dodge" -> dodges++;
            case "parry", "perfect_parry" -> parries++;
        }
    }

    /**
     * Record mob spawn.
     */
    public void recordMobSpawn(UUID mobId, ResourceLocation mobType, BlockPos position,
                                boolean isElite, String affix, int currentTick) {
        if (!collecting || events.size() >= MAX_EVENTS) return;

        int ticksSinceStart = currentTick - waveStartTick;
        CombatEvent.MobSpawnEvent event = new CombatEvent.MobSpawnEvent(
            ticksSinceStart, mobId, mobType, position, isElite, affix
        );
        events.add(event);

        MobTypeStats stats = mobStats.computeIfAbsent(mobType, k -> new MobTypeStats());
        stats.spawned++;
        if (isElite) stats.eliteSpawned++;
    }

    /**
     * Record player death position.
     */
    public void recordPlayerDeath(BlockPos position) {
        deathPositions.add(position);
    }

    // ========== State Control ==========

    /**
     * Stop collecting data (called when wave ends).
     */
    public void stopCollecting() {
        collecting = false;
        LOGGER.debug("[WIS] Wave {} telemetry collection stopped: {} events, {} kills",
            waveNumber, events.size(), totalKills);
    }

    /**
     * Clear all data (for reuse or cleanup).
     */
    public void clear() {
        collecting = false;
        events.clear();
        mobStats.clear();
        weaponStats.clear();
        playerPositions.clear();
        killPositions.clear();
        deathPositions.clear();
        damagePositions.clear();
        totalKills = 0;
        eliteKills = 0;
        totalDamageDealt = 0f;
        totalDamageTaken = 0f;
        maxCombo = 0;
        currentCombo = 0;
        criticalHits = 0;
        dodges = 0;
        parries = 0;
    }

    // ========== Getters for Analysis ==========

    public UUID getQuestId() { return questId; }
    public int getWaveNumber() { return waveNumber; }
    public long getWaveStartTimeMs() { return waveStartTimeMs; }
    public long getLastKillTimeMs() { return lastKillTimeMs; }
    public boolean isCollecting() { return collecting; }

    public int getTotalKills() { return totalKills; }
    public int getEliteKills() { return eliteKills; }
    public float getTotalDamageDealt() { return totalDamageDealt; }
    public float getTotalDamageTaken() { return totalDamageTaken; }
    public int getMaxCombo() { return maxCombo; }
    public int getCurrentCombo() { return currentCombo; }
    public int getCriticalHits() { return criticalHits; }
    public int getDodges() { return dodges; }
    public int getParries() { return parries; }

    public List<CombatEvent> getEvents() {
        return Collections.unmodifiableList(new ArrayList<>(events));
    }

    public int getEventCount() { return events.size(); }

    public Map<ResourceLocation, MobTypeStats> getMobStats() {
        return Collections.unmodifiableMap(new HashMap<>(mobStats));
    }

    public Map<String, WeaponStats> getWeaponStats() {
        return Collections.unmodifiableMap(new HashMap<>(weaponStats));
    }

    public List<BlockPos> getPlayerPositions() {
        return Collections.unmodifiableList(new ArrayList<>(playerPositions));
    }

    public List<BlockPos> getKillPositions() {
        return Collections.unmodifiableList(new ArrayList<>(killPositions));
    }

    public List<BlockPos> getDeathPositions() {
        return Collections.unmodifiableList(new ArrayList<>(deathPositions));
    }

    public List<BlockPos> getDamagePositions() {
        return Collections.unmodifiableList(new ArrayList<>(damagePositions));
    }

    /**
     * Get wave duration in milliseconds.
     */
    public long getWaveDurationMs() {
        return System.currentTimeMillis() - waveStartTimeMs;
    }

    /**
     * Get kills per second.
     */
    public float getKillsPerSecond() {
        long durationMs = getWaveDurationMs();
        if (durationMs <= 0) return 0f;
        return totalKills * 1000f / durationMs;
    }

    /**
     * Get DPS (damage per second).
     */
    public float getDPS() {
        long durationMs = getWaveDurationMs();
        if (durationMs <= 0) return 0f;
        return totalDamageDealt * 1000f / durationMs;
    }

    /**
     * Get DTPS (damage taken per second).
     */
    public float getDTPS() {
        long durationMs = getWaveDurationMs();
        if (durationMs <= 0) return 0f;
        return totalDamageTaken * 1000f / durationMs;
    }

    /**
     * Get average time to kill in milliseconds.
     */
    public float getAverageTTK() {
        if (totalKills == 0) return 0f;
        long totalTTK = 0;
        int count = 0;
        for (CombatEvent event : events) {
            if (event instanceof CombatEvent.KillEvent kill && kill.timeToKillMs() > 0) {
                totalTTK += kill.timeToKillMs();
                count++;
            }
        }
        return count > 0 ? (float) totalTTK / count : 0f;
    }

    /**
     * Check if this was a "no damage" wave.
     */
    public boolean wasNoDamageWave() {
        return totalDamageTaken == 0f;
    }

    /**
     * Get critical hit rate (0.0 - 1.0).
     */
    public float getCriticalHitRate() {
        long damageEvents = events.stream()
            .filter(e -> e instanceof CombatEvent.DamageDealtEvent || e instanceof CombatEvent.KillEvent)
            .count();
        return damageEvents > 0 ? (float) criticalHits / damageEvents : 0f;
    }

    // ========== Aggregate Stats Classes ==========

    /**
     * Statistics for a specific mob type.
     */
    public static class MobTypeStats {
        public int spawned = 0;
        public int eliteSpawned = 0;
        public int kills = 0;
        public int eliteKills = 0;
        public float totalDamageDealt = 0f;
        public float totalDamageToPlayer = 0f;
        public long totalTimeToKillMs = 0;

        public float getAverageTimeToKill() {
            return kills > 0 ? (float) totalTimeToKillMs / kills : 0f;
        }

        public float getKillRate() {
            return spawned > 0 ? (float) kills / spawned : 0f;
        }
    }

    /**
     * Statistics for a specific weapon.
     */
    public static class WeaponStats {
        public int kills = 0;
        public float totalDamage = 0f;
        public int criticalHits = 0;

        public float getAverageDamagePerKill() {
            return kills > 0 ? totalDamage / kills : 0f;
        }

        public float getCriticalRate() {
            return kills > 0 ? (float) criticalHits / kills : 0f;
        }
    }
}
