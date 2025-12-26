package com.devmod.telemetry.duckdb.lvc;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import javax.annotation.Nullable;

import com.devmod.telemetry.duckdb.aggregation.AggregationConfig;

/**
 * Per-player Last Value Cache entry.
 * Stores real-time combat statistics for instant queries (<1ms).
 *
 * All fields use atomic operations for thread-safety.
 * Memory footprint: ~1.5KB per player.
 *
 * <p>Updated in real-time as events arrive, before aggregation.
 * Queries never touch the database - all data is in memory.
 */
public class PlayerLVCEntry {

    private final UUID playerId;
    private final Instant sessionStart;

    // ============================================
    // COMBAT STATISTICS (atomic for thread-safety)
    // ============================================

    /** Total damage dealt (stored as damage * 100 for precision without floating point) */
    private final AtomicLong totalDamageCents = new AtomicLong(0);

    /** Total hits landed */
    private final AtomicInteger hitCount = new AtomicInteger(0);

    /** Total kills */
    private final AtomicInteger killCount = new AtomicInteger(0);

    /** Total misses (attacks that didn't hit) */
    private final AtomicInteger missCount = new AtomicInteger(0);

    /** Player death count */
    private final AtomicInteger deathCount = new AtomicInteger(0);

    /** Critical hit count */
    private final AtomicInteger criticalHitCount = new AtomicInteger(0);

    // ============================================
    // DPS TRACKING
    // ============================================

    /** Rolling DPS window (60 second circular buffer) */
    private final RollingDPSWindow dpsWindow;

    // ============================================
    // COMBO TRACKING
    // ============================================

    /** Current combo streak */
    private final AtomicInteger currentCombo = new AtomicInteger(0);

    /** Maximum combo reached this session */
    private final AtomicInteger maxCombo = new AtomicInteger(0);

    // ============================================
    // ABILITY USAGE
    // ============================================

    /** Per-ability usage counts */
    private final ConcurrentHashMap<String, AtomicInteger> abilityUsage = new ConcurrentHashMap<>();

    /** Per-ability success counts */
    private final ConcurrentHashMap<String, AtomicInteger> abilitySuccesses = new ConcurrentHashMap<>();

    /** Total stamina spent on abilities */
    private final AtomicLong totalStaminaSpentCents = new AtomicLong(0);

    /** Total damage negated by defensive abilities */
    private final AtomicLong totalDamageNegatedCents = new AtomicLong(0);

    // ============================================
    // WAVE/QUEST PROGRESS
    // ============================================

    /** Highest wave reached this session */
    private final AtomicInteger highestWave = new AtomicInteger(0);

    /** Current wave number */
    private final AtomicInteger currentWave = new AtomicInteger(0);

    /** Waves completed count */
    private final AtomicInteger wavesCompleted = new AtomicInteger(0);

    // ============================================
    // XP TRACKING
    // ============================================

    /** Total XP earned this session */
    private final AtomicLong totalXpEarned = new AtomicLong(0);

    // ============================================
    // PER-WEAPON STATISTICS (LRU-limited)
    // ============================================

    /** Per-weapon kill counts (max 8 weapons, LRU eviction) */
    private final Map<String, WeaponStats> weaponStats;

    /** Lock for weapon stats map modification */
    private final Object weaponStatsLock = new Object();

    // ============================================
    // METADATA
    // ============================================

    /** Last update timestamp (millis) */
    private volatile long lastUpdateMs;

    /** Active quest ID (null if not in quest) */
    private volatile @Nullable UUID activeQuestId = null;

    /** Active session ID */
    private volatile UUID sessionId;

    /**
     * Create a new LVC entry for a player.
     *
     * @param playerId the player's UUID
     */
    public PlayerLVCEntry(UUID playerId) {
        this.playerId = playerId;
        this.sessionStart = Instant.now();
        this.lastUpdateMs = System.currentTimeMillis();
        this.dpsWindow = new RollingDPSWindow();
        this.sessionId = UUID.randomUUID();

        // LRU map for weapon stats (max 8 entries)
        this.weaponStats = Collections.synchronizedMap(
            new LinkedHashMap<String, WeaponStats>(
                AggregationConfig.MAX_WEAPONS_PER_PLAYER + 1,
                0.75f,
                true  // access-order for LRU
            ) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, WeaponStats> eldest) {
                    return size() > AggregationConfig.MAX_WEAPONS_PER_PLAYER;
                }
            }
        );
    }

    // ============================================
    // UPDATE METHODS (called from TelemetryLVC)
    // ============================================

    /**
     * Record a hit event.
     *
     * @param damage damage dealt
     * @param isKill whether this hit resulted in a kill
     * @param isCritical whether this was a critical hit
     * @param weapon weapon identifier
     */
    public void recordHit(double damage, boolean isKill, boolean isCritical, String weapon) {
        totalDamageCents.addAndGet((long) (damage * 100));
        hitCount.incrementAndGet();
        dpsWindow.recordDamage(damage);

        if (isKill) {
            killCount.incrementAndGet();
            incrementCombo();

            // Update weapon stats
            if (weapon != null && !weapon.isEmpty()) {
                synchronized (weaponStatsLock) {
                    weaponStats.computeIfAbsent(weapon, k -> new WeaponStats())
                        .recordKill(damage);
                }
            }
        }

        if (isCritical) {
            criticalHitCount.incrementAndGet();
        }

        lastUpdateMs = System.currentTimeMillis();
    }

    /**
     * Record a miss (attack that didn't connect).
     */
    public void recordMiss() {
        missCount.incrementAndGet();
        lastUpdateMs = System.currentTimeMillis();
    }

    /**
     * Record a player death.
     */
    public void recordDeath() {
        deathCount.incrementAndGet();
        breakCombo();
        lastUpdateMs = System.currentTimeMillis();
    }

    /**
     * Record ability usage.
     *
     * @param abilityType type of ability (dash, dodge, perfect_dodge, etc.)
     * @param success whether the ability succeeded
     * @param staminaCost stamina spent
     * @param damageNegated damage negated (for defensive abilities)
     */
    public void recordAbility(String abilityType, boolean success, double staminaCost, double damageNegated) {
        if (abilityType == null || abilityType.isEmpty()) return;

        abilityUsage.computeIfAbsent(abilityType, k -> new AtomicInteger(0)).incrementAndGet();

        if (success) {
            abilitySuccesses.computeIfAbsent(abilityType, k -> new AtomicInteger(0)).incrementAndGet();
        }

        if (staminaCost > 0) {
            totalStaminaSpentCents.addAndGet((long) (staminaCost * 100));
        }

        if (damageNegated > 0) {
            totalDamageNegatedCents.addAndGet((long) (damageNegated * 100));
        }

        lastUpdateMs = System.currentTimeMillis();
    }

    /**
     * Record XP gain.
     *
     * @param amount XP earned
     */
    public void recordXp(int amount) {
        if (amount > 0) {
            totalXpEarned.addAndGet(amount);
            lastUpdateMs = System.currentTimeMillis();
        }
    }

    /**
     * Record wave completion.
     *
     * @param waveNumber wave that was completed
     */
    public void recordWaveComplete(int waveNumber) {
        wavesCompleted.incrementAndGet();
        currentWave.set(waveNumber + 1);
        if (waveNumber > highestWave.get()) {
            highestWave.set(waveNumber);
        }
        lastUpdateMs = System.currentTimeMillis();
    }

    /**
     * Set current wave (called when wave starts).
     */
    public void setCurrentWave(int wave) {
        currentWave.set(wave);
        if (wave > highestWave.get()) {
            highestWave.set(wave);
        }
    }

    /**
     * Set active quest ID.
     */
    public void setActiveQuest(UUID questId) {
        this.activeQuestId = questId;
        if (questId != null) {
            // Reset session stats for new quest
            resetSessionStats();
        }
    }

    // ============================================
    // COMBO MANAGEMENT
    // ============================================

    private void incrementCombo() {
        int newCombo = currentCombo.incrementAndGet();
        if (newCombo > maxCombo.get()) {
            maxCombo.set(newCombo);
        }
    }

    private void breakCombo() {
        currentCombo.set(0);
    }

    /**
     * Manually break combo (called on combo timeout).
     */
    public void onComboBreak() {
        breakCombo();
    }

    // ============================================
    // QUERY METHODS (instant, <1ms)
    // ============================================

    public UUID getPlayerId() {
        return playerId;
    }

    public Instant getSessionStart() {
        return sessionStart;
    }

    public double getTotalDamage() {
        return totalDamageCents.get() / 100.0;
    }

    public int getHitCount() {
        return hitCount.get();
    }

    public int getKillCount() {
        return killCount.get();
    }

    public int getMissCount() {
        return missCount.get();
    }

    public int getDeathCount() {
        return deathCount.get();
    }

    public int getCriticalHitCount() {
        return criticalHitCount.get();
    }

    /**
     * Get current DPS (rolling 60-second window).
     */
    public double getCurrentDPS() {
        return dpsWindow.getCurrentDPS();
    }

    /**
     * Get peak DPS (highest single second).
     */
    public double getPeakDPS() {
        return dpsWindow.getPeakDPS();
    }

    /**
     * Get hit accuracy (hits / (hits + misses)).
     */
    public double getAccuracy() {
        int hits = hitCount.get();
        int misses = missCount.get();
        int total = hits + misses;
        return total > 0 ? (double) hits / total : 0.0;
    }

    /**
     * Get kills-to-deaths ratio.
     */
    public double getKDRatio() {
        int deaths = deathCount.get();
        return deaths > 0 ? (double) killCount.get() / deaths : killCount.get();
    }

    public int getCurrentCombo() {
        return currentCombo.get();
    }

    public int getMaxCombo() {
        return maxCombo.get();
    }

    public int getHighestWave() {
        return highestWave.get();
    }

    public int getCurrentWave() {
        return currentWave.get();
    }

    public int getWavesCompleted() {
        return wavesCompleted.get();
    }

    public long getTotalXpEarned() {
        return totalXpEarned.get();
    }

    public double getTotalStaminaSpent() {
        return totalStaminaSpentCents.get() / 100.0;
    }

    public double getTotalDamageNegated() {
        return totalDamageNegatedCents.get() / 100.0;
    }

    /**
     * Get usage count for a specific ability.
     */
    public int getAbilityUsage(String abilityType) {
        AtomicInteger count = abilityUsage.get(abilityType);
        return count != null ? count.get() : 0;
    }

    /**
     * Get success count for a specific ability.
     */
    public int getAbilitySuccesses(String abilityType) {
        AtomicInteger count = abilitySuccesses.get(abilityType);
        return count != null ? count.get() : 0;
    }

    /**
     * Get all ability usage counts.
     */
    public Map<String, Integer> getAllAbilityUsage() {
        Map<String, Integer> result = new ConcurrentHashMap<>();
        abilityUsage.forEach((k, v) -> result.put(k, v.get()));
        return result;
    }

    /**
     * Get per-weapon kill counts.
     */
    public Map<String, Integer> getWeaponKills() {
        Map<String, Integer> result = new LinkedHashMap<>();
        synchronized (weaponStatsLock) {
            weaponStats.forEach((weapon, stats) -> result.put(weapon, stats.kills.get()));
        }
        return result;
    }

    /**
     * Get per-weapon damage.
     */
    public Map<String, Double> getWeaponDamage() {
        Map<String, Double> result = new LinkedHashMap<>();
        synchronized (weaponStatsLock) {
            weaponStats.forEach((weapon, stats) -> result.put(weapon, stats.getDamage()));
        }
        return result;
    }

    public long getLastUpdateMs() {
        return lastUpdateMs;
    }

    @Nullable
    public UUID getActiveQuestId() {
        return activeQuestId;
    }

    public UUID getSessionId() {
        return sessionId;
    }

    /**
     * Check if entry is stale (no updates for configured threshold).
     */
    public boolean isStale() {
        return System.currentTimeMillis() - lastUpdateMs > AggregationConfig.LVC_STALE_THRESHOLD_MS;
    }

    // ============================================
    // RESET / LIFECYCLE
    // ============================================

    /**
     * Reset session-specific stats (called on new quest).
     */
    public void resetSessionStats() {
        totalDamageCents.set(0);
        hitCount.set(0);
        killCount.set(0);
        missCount.set(0);
        deathCount.set(0);
        criticalHitCount.set(0);
        currentCombo.set(0);
        maxCombo.set(0);
        currentWave.set(0);
        highestWave.set(0);
        wavesCompleted.set(0);
        totalXpEarned.set(0);
        totalStaminaSpentCents.set(0);
        totalDamageNegatedCents.set(0);
        dpsWindow.reset();
        abilityUsage.clear();
        abilitySuccesses.clear();
        synchronized (weaponStatsLock) {
            weaponStats.clear();
        }
        sessionId = UUID.randomUUID();
        lastUpdateMs = System.currentTimeMillis();
    }

    /**
     * Create a snapshot of current stats for external use.
     */
    public PlayerLVCSnapshot snapshot() {
        return new PlayerLVCSnapshot(
            playerId,
            getTotalDamage(),
            getHitCount(),
            getKillCount(),
            getMissCount(),
            getDeathCount(),
            getCriticalHitCount(),
            getCurrentDPS(),
            getPeakDPS(),
            getAccuracy(),
            getKDRatio(),
            getCurrentCombo(),
            getMaxCombo(),
            getHighestWave(),
            getCurrentWave(),
            getWavesCompleted(),
            getTotalXpEarned(),
            getTotalStaminaSpent(),
            getTotalDamageNegated(),
            getWeaponKills(),
            getAllAbilityUsage(),
            sessionStart,
            lastUpdateMs
        );
    }

    // ============================================
    // INNER CLASSES
    // ============================================

    /**
     * Per-weapon statistics.
     */
    public static class WeaponStats {
        final AtomicInteger kills = new AtomicInteger(0);
        final AtomicLong damageCents = new AtomicLong(0);
        final AtomicInteger hits = new AtomicInteger(0);

        void recordKill(double damage) {
            kills.incrementAndGet();
            damageCents.addAndGet((long) (damage * 100));
            hits.incrementAndGet();
        }

        double getDamage() {
            return damageCents.get() / 100.0;
        }
    }

    /**
     * Immutable snapshot of player stats for external use.
     */
    public record PlayerLVCSnapshot(
        UUID playerId,
        double totalDamage,
        int hitCount,
        int killCount,
        int missCount,
        int deathCount,
        int criticalHitCount,
        double currentDPS,
        double peakDPS,
        double accuracy,
        double kdRatio,
        int currentCombo,
        int maxCombo,
        int highestWave,
        int currentWave,
        int wavesCompleted,
        long totalXpEarned,
        double totalStaminaSpent,
        double totalDamageNegated,
        Map<String, Integer> weaponKills,
        Map<String, Integer> abilityUsage,
        Instant sessionStart,
        long lastUpdateMs
    ) {}
}
