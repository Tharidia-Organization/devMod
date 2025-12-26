package com.devmod.telemetry.duckdb.lvc;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.devmod.telemetry.duckdb.aggregation.AggregationConfig;
import com.devmod.telemetry.duckdb.lvc.PlayerLVCEntry.PlayerLVCSnapshot;
import com.devmod.telemetry.network.LVCSyncPayload;

/**
 * Last Value Cache - In-memory cache for instant real-time telemetry queries.
 *
 * <p>This is the primary query interface for real-time telemetry data. All queries
 * are served from memory with <1ms latency - no database access required.
 *
 * <p>Data is updated in real-time as events arrive, before aggregation to DuckDB.
 * This ensures HUDs, dashboards, and leaderboards always show current data.
 *
 * <p>Usage:
 * <pre>
 * // Update (called from TelemetryService)
 * TelemetryLVC.INSTANCE.recordHit(playerId, 25.5, true, "diamond_sword");
 *
 * // Query (called from HUD/Dashboard)
 * double dps = TelemetryLVC.INSTANCE.getCurrentDPS(playerId);
 * List&lt;PlayerDamageSummary&gt; top = TelemetryLVC.INSTANCE.getTopDamageDealers(10);
 * </pre>
 *
 * Memory: ~1.5KB per player, ~600KB for 400 players
 */
public class TelemetryLVC {

    private static final Logger LOGGER = LoggerFactory.getLogger(TelemetryLVC.class);

    /** Singleton instance */
    public static final TelemetryLVC INSTANCE = new TelemetryLVC();

    // ============================================
    // PER-PLAYER CACHE
    // ============================================

    /** Per-player LVC entries */
    private final ConcurrentHashMap<UUID, PlayerLVCEntry> playerEntries = new ConcurrentHashMap<>();

    // ============================================
    // GLOBAL SERVER STATISTICS
    // ============================================

    /** Total damage dealt by all players (cents for precision) */
    private final AtomicLong serverTotalDamageCents = new AtomicLong(0);

    /** Total kills by all players */
    private final AtomicInteger serverTotalKills = new AtomicInteger(0);

    /** Total deaths of all players */
    private final AtomicInteger serverTotalDeaths = new AtomicInteger(0);

    /** Total XP earned by all players */
    private final AtomicLong serverTotalXp = new AtomicLong(0);

    private TelemetryLVC() {
        LOGGER.info("[LVC] Last Value Cache initialized");
    }

    // ============================================
    // LIFECYCLE MANAGEMENT
    // ============================================

    /**
     * Create an LVC entry for a player (called on player join).
     *
     * @param playerId the player's UUID
     */
    public void createEntry(UUID playerId) {
        if (!AggregationConfig.LVC_ENABLED) return;

        playerEntries.computeIfAbsent(playerId, PlayerLVCEntry::new);
        LOGGER.debug("[LVC] Created entry for player {}", playerId);
    }

    /**
     * Remove an LVC entry for a player (called on player leave).
     *
     * @param playerId the player's UUID
     * @return the removed entry, or null if not found
     */
    @Nullable
    public PlayerLVCEntry removeEntry(UUID playerId) {
        PlayerLVCEntry entry = playerEntries.remove(playerId);
        if (entry != null) {
            LOGGER.debug("[LVC] Removed entry for player {}", playerId);
        }
        return entry;
    }

    /**
     * Get or create an entry for a player.
     */
    public PlayerLVCEntry getOrCreateEntry(UUID playerId) {
        return playerEntries.computeIfAbsent(playerId, PlayerLVCEntry::new);
    }

    /**
     * Get entry for a player (may return null).
     */
    @Nullable
    public PlayerLVCEntry getEntry(UUID playerId) {
        return playerEntries.get(playerId);
    }

    /**
     * Check if an entry exists for a player.
     */
    public boolean hasEntry(UUID playerId) {
        return playerEntries.containsKey(playerId);
    }

    /**
     * Clear all entries (called on server shutdown).
     */
    public void clear() {
        playerEntries.clear();
        serverTotalDamageCents.set(0);
        serverTotalKills.set(0);
        serverTotalDeaths.set(0);
        serverTotalXp.set(0);
        LOGGER.info("[LVC] Cache cleared");
    }

    /**
     * Remove stale entries (no activity for configured threshold).
     *
     * @return number of entries removed
     */
    public int pruneStaleEntries() {
        int removed = 0;
        for (Map.Entry<UUID, PlayerLVCEntry> entry : playerEntries.entrySet()) {
            if (entry.getValue().isStale()) {
                playerEntries.remove(entry.getKey());
                removed++;
            }
        }
        if (removed > 0) {
            LOGGER.debug("[LVC] Pruned {} stale entries", removed);
        }
        return removed;
    }

    // ============================================
    // UPDATE METHODS (called from TelemetryService)
    // ============================================

    /**
     * Record a hit event.
     *
     * @param playerId attacker's UUID
     * @param damage damage dealt
     * @param isKill whether this hit killed the target
     * @param weapon weapon identifier
     */
    public void recordHit(UUID playerId, double damage, boolean isKill, String weapon) {
        if (!AggregationConfig.LVC_ENABLED || playerId == null) return;

        PlayerLVCEntry entry = getOrCreateEntry(playerId);
        entry.recordHit(damage, isKill, false, weapon);

        // Update server totals
        serverTotalDamageCents.addAndGet((long) (damage * 100));
        if (isKill) {
            serverTotalKills.incrementAndGet();
        }
    }

    /**
     * Record a hit with critical hit flag.
     */
    public void recordHit(UUID playerId, double damage, boolean isKill, boolean isCritical, String weapon) {
        if (!AggregationConfig.LVC_ENABLED || playerId == null) return;

        PlayerLVCEntry entry = getOrCreateEntry(playerId);
        entry.recordHit(damage, isKill, isCritical, weapon);

        serverTotalDamageCents.addAndGet((long) (damage * 100));
        if (isKill) {
            serverTotalKills.incrementAndGet();
        }
    }

    /**
     * Record a player death.
     *
     * @param playerId the player who died
     */
    public void recordDeath(UUID playerId) {
        if (!AggregationConfig.LVC_ENABLED || playerId == null) return;

        PlayerLVCEntry entry = getEntry(playerId);
        if (entry != null) {
            entry.recordDeath();
        }
        serverTotalDeaths.incrementAndGet();
    }

    /**
     * Record ability usage.
     */
    public void recordAbility(UUID playerId, String abilityType, boolean success,
                              double staminaCost, double damageNegated) {
        if (!AggregationConfig.LVC_ENABLED || playerId == null) return;

        PlayerLVCEntry entry = getOrCreateEntry(playerId);
        entry.recordAbility(abilityType, success, staminaCost, damageNegated);
    }

    /**
     * Record XP gain.
     */
    public void recordXp(UUID playerId, int amount) {
        if (!AggregationConfig.LVC_ENABLED || playerId == null || amount <= 0) return;

        PlayerLVCEntry entry = getOrCreateEntry(playerId);
        entry.recordXp(amount);
        serverTotalXp.addAndGet(amount);
    }

    /**
     * Record wave completion.
     */
    public void recordWaveComplete(UUID playerId, int waveNumber) {
        if (!AggregationConfig.LVC_ENABLED || playerId == null) return;

        PlayerLVCEntry entry = getOrCreateEntry(playerId);
        entry.recordWaveComplete(waveNumber);
    }

    /**
     * Record wave start.
     */
    public void recordWaveStart(UUID playerId, int waveNumber) {
        if (!AggregationConfig.LVC_ENABLED || playerId == null) return;

        PlayerLVCEntry entry = getOrCreateEntry(playerId);
        entry.setCurrentWave(waveNumber);
    }

    /**
     * Record quest start (resets session stats).
     */
    public void recordQuestStart(UUID playerId, UUID questId) {
        if (!AggregationConfig.LVC_ENABLED || playerId == null) return;

        PlayerLVCEntry entry = getOrCreateEntry(playerId);
        entry.setActiveQuest(questId);
    }

    /**
     * Record combo break.
     */
    public void recordComboBreak(UUID playerId) {
        if (!AggregationConfig.LVC_ENABLED || playerId == null) return;

        PlayerLVCEntry entry = getEntry(playerId);
        if (entry != null) {
            entry.onComboBreak();
        }
    }

    // ============================================
    // PER-PLAYER QUERIES (instant, <1ms)
    // ============================================

    /**
     * Get player's current DPS (rolling 60-second window).
     */
    public double getCurrentDPS(UUID playerId) {
        PlayerLVCEntry entry = getEntry(playerId);
        return entry != null ? entry.getCurrentDPS() : 0.0;
    }

    /**
     * Get player's total damage dealt this session.
     */
    public double getTotalDamageDealt(UUID playerId) {
        PlayerLVCEntry entry = getEntry(playerId);
        return entry != null ? entry.getTotalDamage() : 0.0;
    }

    /**
     * Get player's kill count.
     */
    public int getKillCount(UUID playerId) {
        PlayerLVCEntry entry = getEntry(playerId);
        return entry != null ? entry.getKillCount() : 0;
    }

    /**
     * Get player's death count.
     */
    public int getDeathCount(UUID playerId) {
        PlayerLVCEntry entry = getEntry(playerId);
        return entry != null ? entry.getDeathCount() : 0;
    }

    /**
     * Get player's current combo streak.
     */
    public int getCurrentCombo(UUID playerId) {
        PlayerLVCEntry entry = getEntry(playerId);
        return entry != null ? entry.getCurrentCombo() : 0;
    }

    /**
     * Get player's max combo this session.
     */
    public int getMaxCombo(UUID playerId) {
        PlayerLVCEntry entry = getEntry(playerId);
        return entry != null ? entry.getMaxCombo() : 0;
    }

    /**
     * Get player's hit accuracy (0.0 - 1.0).
     */
    public double getAccuracy(UUID playerId) {
        PlayerLVCEntry entry = getEntry(playerId);
        return entry != null ? entry.getAccuracy() : 0.0;
    }

    /**
     * Get player's K/D ratio.
     */
    public double getKDRatio(UUID playerId) {
        PlayerLVCEntry entry = getEntry(playerId);
        return entry != null ? entry.getKDRatio() : 0.0;
    }

    /**
     * Get per-weapon kill counts for a player.
     */
    public Map<String, Integer> getWeaponKills(UUID playerId) {
        PlayerLVCEntry entry = getEntry(playerId);
        return entry != null ? entry.getWeaponKills() : Map.of();
    }

    /**
     * Get ability usage count for a player.
     */
    public int getAbilityUsage(UUID playerId, String abilityType) {
        PlayerLVCEntry entry = getEntry(playerId);
        return entry != null ? entry.getAbilityUsage(abilityType) : 0;
    }

    /**
     * Get player's highest wave reached.
     */
    public int getHighestWave(UUID playerId) {
        PlayerLVCEntry entry = getEntry(playerId);
        return entry != null ? entry.getHighestWave() : 0;
    }

    /**
     * Get player's current wave.
     */
    public int getCurrentWave(UUID playerId) {
        PlayerLVCEntry entry = getEntry(playerId);
        return entry != null ? entry.getCurrentWave() : 0;
    }

    /**
     * Get full snapshot of player stats.
     */
    @Nullable
    public PlayerLVCSnapshot getPlayerSnapshot(UUID playerId) {
        PlayerLVCEntry entry = getEntry(playerId);
        return entry != null ? entry.snapshot() : null;
    }

    // ============================================
    // GLOBAL SERVER QUERIES
    // ============================================

    /**
     * Get total damage dealt by all players.
     */
    public double getServerTotalDamage() {
        return serverTotalDamageCents.get() / 100.0;
    }

    /**
     * Get total kills by all players.
     */
    public int getServerTotalKills() {
        return serverTotalKills.get();
    }

    /**
     * Get total player deaths.
     */
    public int getServerTotalDeaths() {
        return serverTotalDeaths.get();
    }

    /**
     * Get total XP earned by all players.
     */
    public long getServerTotalXp() {
        return serverTotalXp.get();
    }

    /**
     * Get number of active players in cache.
     */
    public int getActivePlayerCount() {
        return playerEntries.size();
    }

    /**
     * Get estimated memory usage in bytes.
     */
    public long getEstimatedMemoryUsage() {
        // ~1.5KB per player
        return playerEntries.size() * 1500L;
    }

    // ============================================
    // LEADERBOARD QUERIES
    // ============================================

    /**
     * Get top damage dealers.
     *
     * @param limit maximum entries to return
     * @return sorted list of player damage summaries
     */
    public List<PlayerDamageSummary> getTopDamageDealers(int limit) {
        int effectiveLimit = Math.min(limit, AggregationConfig.LVC_TOP_N_LIMIT);

        return playerEntries.values().stream()
            .map(e -> new PlayerDamageSummary(e.getPlayerId(), e.getTotalDamage(), e.getCurrentDPS()))
            .sorted(Comparator.comparingDouble(PlayerDamageSummary::totalDamage).reversed())
            .limit(effectiveLimit)
            .collect(Collectors.toList());
    }

    /**
     * Get top killers.
     *
     * @param limit maximum entries to return
     * @return sorted list of player kill summaries
     */
    public List<PlayerKillSummary> getTopKillers(int limit) {
        int effectiveLimit = Math.min(limit, AggregationConfig.LVC_TOP_N_LIMIT);

        return playerEntries.values().stream()
            .map(e -> new PlayerKillSummary(e.getPlayerId(), e.getKillCount(), e.getKDRatio()))
            .sorted(Comparator.comparingInt(PlayerKillSummary::killCount).reversed())
            .limit(effectiveLimit)
            .collect(Collectors.toList());
    }

    /**
     * Get players with highest DPS.
     *
     * @param limit maximum entries to return
     * @return sorted list of player DPS summaries
     */
    public List<PlayerDPSSummary> getTopDPS(int limit) {
        int effectiveLimit = Math.min(limit, AggregationConfig.LVC_TOP_N_LIMIT);

        return playerEntries.values().stream()
            .map(e -> new PlayerDPSSummary(e.getPlayerId(), e.getCurrentDPS(), e.getPeakDPS()))
            .sorted(Comparator.comparingDouble(PlayerDPSSummary::currentDPS).reversed())
            .limit(effectiveLimit)
            .collect(Collectors.toList());
    }

    /**
     * Get players with highest wave reached.
     *
     * @param limit maximum entries to return
     * @return sorted list of player wave summaries
     */
    public List<PlayerWaveSummary> getTopWaves(int limit) {
        int effectiveLimit = Math.min(limit, AggregationConfig.LVC_TOP_N_LIMIT);

        return playerEntries.values().stream()
            .filter(e -> e.getHighestWave() > 0)
            .map(e -> new PlayerWaveSummary(e.getPlayerId(), e.getHighestWave(), e.getWavesCompleted()))
            .sorted(Comparator.comparingInt(PlayerWaveSummary::highestWave).reversed())
            .limit(effectiveLimit)
            .collect(Collectors.toList());
    }

    /**
     * Get all active player IDs.
     */
    public List<UUID> getActivePlayerIds() {
        return new ArrayList<>(playerEntries.keySet());
    }

    // ============================================
    // SUMMARY RECORDS
    // ============================================

    public record PlayerDamageSummary(UUID playerId, double totalDamage, double currentDPS) {}
    public record PlayerKillSummary(UUID playerId, int killCount, double kdRatio) {}
    public record PlayerDPSSummary(UUID playerId, double currentDPS, double peakDPS) {}
    public record PlayerWaveSummary(UUID playerId, int highestWave, int wavesCompleted) {}

    /**
     * Server-wide combat summary.
     */
    public record ServerCombatSummary(
        double totalDamage,
        int totalKills,
        int totalDeaths,
        long totalXp,
        int activePlayerCount
    ) {}

    /**
     * Get server-wide combat summary.
     */
    public ServerCombatSummary getServerCombatSummary() {
        return new ServerCombatSummary(
            getServerTotalDamage(),
            getServerTotalKills(),
            getServerTotalDeaths(),
            getServerTotalXp(),
            getActivePlayerCount()
        );
    }

    // ============================================
    // NETWORK SYNC
    // ============================================

    /**
     * Build a sync payload for a player to send to the client.
     *
     * @param playerId player UUID
     * @return sync payload, or empty payload if player not found
     */
    public LVCSyncPayload buildSyncPayload(UUID playerId) {
        PlayerLVCEntry entry = getEntry(playerId);
        if (entry == null) {
            return LVCSyncPayload.empty();
        }

        // Build top weapons string (name:kills pairs, max 3)
        Map<String, Integer> weaponKills = entry.getWeaponKills();
        String topWeapons = weaponKills.entrySet().stream()
            .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
            .limit(3)
            .map(e -> e.getKey() + ":" + e.getValue())
            .collect(Collectors.joining(","));

        return new LVCSyncPayload(
            entry.getCurrentDPS(),
            entry.getPeakDPS(),
            entry.getHitCount(),
            entry.getMissCount(),
            entry.getCriticalHitCount(),
            entry.getTotalDamage(),
            entry.getKillCount(),
            entry.getDeathCount(),
            entry.getCurrentCombo(),
            entry.getMaxCombo(),
            entry.getHighestWave(),
            entry.getWavesCompleted(),
            entry.getAbilityUsage("dash"),
            entry.getAbilityUsage("dodge"),
            entry.getAbilityUsage("perfect_dodge"),
            entry.getTotalDamageNegated(),
            entry.getTotalStaminaSpent(),
            topWeapons
        );
    }
}
