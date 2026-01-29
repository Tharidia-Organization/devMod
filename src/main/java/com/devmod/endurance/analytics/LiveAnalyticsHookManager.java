package com.devmod.endurance.analytics;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.devmod.endurance.CombatTracker;
import com.devmod.endurance.combat.ComboSystemFacade;
import com.devmod.endurance.combat.api.IComboSession;

public class LiveAnalyticsHookManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(LiveAnalyticsHookManager.class);

    public static final LiveAnalyticsHookManager INSTANCE = new LiveAnalyticsHookManager();

    // Registered hooks - thread-safe list
    private final List<AnalyticsHook> hooks = new CopyOnWriteArrayList<>();

    // Active session tracking
    private UUID activeQuestId = null;
    private UUID activePlayerId = null;
    private long sessionStartTime = 0;
    private int currentWave = 1;
    private long waveStartTime = 0;

    // Metrics update tracking
    private long lastMetricsUpdateTime = 0;
    private static final long METRICS_UPDATE_INTERVAL_MS = 1000; // Every second

    // Struggle detection state
    private long lastStruggleNotifyTime = 0;
    private static final long STRUGGLE_COOLDOWN_MS = 15000; // 15 second cooldown
    private int recentDeaths = 0;

    // Opportunity detection state
    private long lastOpportunityNotifyTime = 0;
    private static final long OPPORTUNITY_COOLDOWN_MS = 10000; // 10 second cooldown
    private int previousCombo = 0;

    // Rolling DPS calculation
    private static final int DPS_SAMPLE_COUNT = 10;
    private final float[] dpsHistory = new float[DPS_SAMPLE_COUNT];
    private int dpsHistoryIndex = 0;

    private LiveAnalyticsHookManager() {}

    // === Hook Registration ===

    /**
     * Registers an analytics hook to receive callbacks.
     * Safe to call from any thread.
     */
    public void registerHook(AnalyticsHook hook) {
        if (hook != null && !hooks.contains(hook)) {
            hooks.add(hook);
            LOGGER.debug("[DevMod] Registered analytics hook: {}", hook.getClass().getSimpleName());
        }
    }

    /**
     * Unregisters an analytics hook.
     */
    public void unregisterHook(AnalyticsHook hook) {
        hooks.remove(hook);
    }

    /**
     * Clears all registered hooks.
     */
    public void clearHooks() {
        hooks.clear();
    }

    // === Session Management ===

    /**
     * Called when an Endurance Quest starts.
     * Initializes tracking state.
     */
    public void onQuestStart(UUID questId, UUID playerId) {
        this.activeQuestId = questId;
        this.activePlayerId = playerId;
        this.sessionStartTime = System.currentTimeMillis();
        this.currentWave = 1;
        this.waveStartTime = sessionStartTime;
        this.recentDeaths = 0;
        this.previousCombo = 0;
        this.lastStruggleNotifyTime = 0;
        this.lastOpportunityNotifyTime = 0;

        // Reset DPS history
        for (int i = 0; i < DPS_SAMPLE_COUNT; i++) {
            dpsHistory[i] = 0f;
        }
        dpsHistoryIndex = 0;

        LOGGER.debug("[DevMod] Analytics session started for quest {} player {}", questId, playerId);
    }

    /**
     * Called when an Endurance Quest ends.
     * Notifies hooks with final result.
     */
    public void onQuestEnd(QuestResult result) {
        if (hooks.isEmpty()) return;

        for (AnalyticsHook hook : hooks) {
            try {
                hook.onQuestComplete(result);
            } catch (Exception e) {
                LOGGER.error("[DevMod] Error in analytics hook onQuestComplete: {}", e.getMessage());
            }
        }

        // Clear session state
        activeQuestId = null;
        activePlayerId = null;
        LOGGER.debug("[DevMod] Analytics session ended");
    }

    /**
     * Called when a wave completes.
     * Dispatches wave transition event with summary.
     */
    public void onWaveComplete(int waveNumber, WaveSummary summary, int nextWaveNumber) {
        this.currentWave = nextWaveNumber;
        this.waveStartTime = System.currentTimeMillis();
        this.recentDeaths = 0; // Reset per-wave death counter

        if (hooks.isEmpty()) return;

        for (AnalyticsHook hook : hooks) {
            try {
                hook.onWaveTransition(waveNumber, summary, nextWaveNumber);
            } catch (Exception e) {
                LOGGER.error("[DevMod] Error in analytics hook onWaveTransition: {}", e.getMessage());
            }
        }
    }

    // === Tick Update ===

    /**
     * Called every server tick to check for metric updates.
     * Throttled to once per second.
     */
    public void tick() {
        if (activeQuestId == null || hooks.isEmpty()) return;

        long now = System.currentTimeMillis();
        if (now - lastMetricsUpdateTime < METRICS_UPDATE_INTERVAL_MS) {
            return;
        }
        lastMetricsUpdateTime = now;

        // Build current metrics snapshot
        CombatMetrics metrics = buildCurrentMetrics();

        // Dispatch metrics update
        for (AnalyticsHook hook : hooks) {
            try {
                hook.onCombatMetricsUpdate(metrics);
            } catch (Exception e) {
                LOGGER.error("[DevMod] Error in analytics hook onCombatMetricsUpdate: {}", e.getMessage());
            }
        }

        // Check for struggle indicators
        checkStrugglePatterns(metrics, now);

        // Check for opportunity indicators
        checkOpportunityPatterns(metrics, now);

        // Update DPS history for rolling average
        dpsHistory[dpsHistoryIndex] = metrics.currentDPS();
        dpsHistoryIndex = (dpsHistoryIndex + 1) % DPS_SAMPLE_COUNT;
    }

    // === Metrics Building ===

    /**
     * Builds current combat metrics from active trackers.
     */
    private CombatMetrics buildCurrentMetrics() {
        long now = System.currentTimeMillis();
        long sessionDuration = now - sessionStartTime;
        long waveDuration = now - waveStartTime;

        // Get combat session if available (Optional unwrap)
        CombatTracker.QuestCombatSession combatSession = null;
        if (activeQuestId != null) {
            combatSession = CombatTracker.INSTANCE.getSession(activeQuestId).orElse(null);
        }

        // Get combo state if available (Optional unwrap) - via facade
        IComboSession comboSession = null;
        if (activePlayerId != null && ComboSystemFacade.isInitialized()) {
            comboSession = ComboSystemFacade.get().getSession(activePlayerId).orElse(null);
        }

        // Build metrics from available data
        float totalDamageDealt = combatSession != null ? combatSession.getTotalDamageDealt() : 0f;
        float totalDamageTaken = combatSession != null ? combatSession.getTotalDamageTaken() : 0f;
        int totalHits = combatSession != null ? combatSession.getTotalHitsLanded() : 0;
        int hitsTaken = combatSession != null ? combatSession.getTotalHitsTaken() : 0;
        int criticalHits = combatSession != null ? combatSession.getCriticalHits() : 0;
        int totalKills = combatSession != null ? combatSession.getKills() : 0;
        int deaths = combatSession != null ? combatSession.getDeaths() : 0;

        float currentDPS = combatSession != null ? combatSession.getDPS() : 0f;
        float avgDamagePerHit = combatSession != null ? combatSession.getAverageDamagePerHit() : 0f;
        float critRate = combatSession != null ? combatSession.getCriticalHitRate() : 0f;
        float avgKillTime = combatSession != null ? combatSession.getAverageKillTime() : 0f;

        Map<String, CombatTracker.WeaponStats> weaponStats =
            combatSession != null ? combatSession.getWeaponStats() : Map.of();
        Map<String, Integer> bodyPartHits =
            combatSession != null ? combatSession.getBodyPartHits() : Map.of();

        // Wave-specific stats
        float waveDamageDealt = 0f;
        float waveDamageTaken = 0f;
        int waveKills = 0;
        if (combatSession != null && combatSession.getCurrentWaveStats() != null) {
            var waveStats = combatSession.getCurrentWaveStats();
            waveDamageDealt = waveStats.damageDealt;
            waveDamageTaken = waveStats.damageTaken;
            waveKills = waveStats.kills;
        }

        // Combo stats
        int currentCombo = comboSession != null ? comboSession.getCurrentCombo() : 0;
        String comboRank = comboSession != null ? comboSession.getCurrentRank().name() : "D";
        int maxCombo = comboSession != null ? comboSession.getMaxCombo() : 0;

        // Calculate rolling DPS
        float rollingDPS = 0f;
        for (float dps : dpsHistory) {
            rollingDPS += dps;
        }
        rollingDPS /= DPS_SAMPLE_COUNT;

        // Find best weapon
        String bestWeapon = "minecraft:air";
        float bestWeaponDamage = 0f;
        Map<String, Float> weaponDamageMap = new java.util.HashMap<>();
        for (var entry : weaponStats.entrySet()) {
            weaponDamageMap.put(entry.getKey(), entry.getValue().totalDamage);
            if (entry.getValue().totalDamage > bestWeaponDamage) {
                bestWeaponDamage = entry.getValue().totalDamage;
                bestWeapon = entry.getKey();
            }
        }

        // Calculate headshot stats
        int headshots = bodyPartHits.getOrDefault("HEAD", 0);
        float headshotRate = totalHits > 0 ? (float) headshots / totalHits : 0f;

        return new CombatMetrics(
                sessionDuration,
                currentWave,
                waveDuration,
                totalDamageDealt,
                totalDamageTaken,
                waveDamageDealt,
                waveDamageTaken,
                currentDPS,
                rollingDPS,
                totalHits,
                hitsTaken,
                criticalHits,
                critRate,
                avgDamagePerHit,
                totalKills,
                waveKills,
                0, // mobsRemaining - set by caller if needed
                avgKillTime,
                deaths,
                1.0f, // healthPercent - set by caller if needed
                currentCombo,
                comboRank,
                maxCombo,
                bestWeapon, // currentWeapon approximation
                bestWeaponDamage,
                bestWeapon,
                weaponDamageMap,
                headshots,
                headshotRate,
                bodyPartHits
        );
    }

    // === Pattern Detection ===

    /**
     * Checks for struggle patterns and notifies hooks if detected.
     */
    private void checkStrugglePatterns(CombatMetrics metrics, long now) {
        if (now - lastStruggleNotifyTime < STRUGGLE_COOLDOWN_MS) {
            return; // Still in cooldown
        }

        AnalyticsHook.StruggleIndicator indicator = null;
        float severity = 0f;
        String context = "";

        // Check for repeated deaths
        if (metrics.deaths() > recentDeaths) {
            recentDeaths = metrics.deaths();
            if (recentDeaths >= 2) {
                indicator = AnalyticsHook.StruggleIndicator.REPEATED_DEATHS;
                severity = Math.min(1.0f, recentDeaths * 0.3f);
                context = recentDeaths + " deaths on wave " + metrics.currentWave();
            }
        }

        // Check for low DPS after warmup period
        if (indicator == null && metrics.sessionDurationMs() > 30000) {
            if (metrics.rollingDPS() < 3.0f) {
                indicator = AnalyticsHook.StruggleIndicator.LOW_DPS;
                severity = Math.max(0.3f, 1.0f - (metrics.rollingDPS() / 3.0f));
                context = String.format("DPS: %.1f", metrics.rollingDPS());
            }
        }

        // Check for high damage taken
        if (indicator == null && metrics.totalDamageDealt() > 0) {
            float damageRatio = metrics.totalDamageTaken() / metrics.totalDamageDealt();
            if (damageRatio > 0.7f) {
                indicator = AnalyticsHook.StruggleIndicator.HIGH_DAMAGE_TAKEN;
                severity = Math.min(1.0f, damageRatio - 0.5f);
                context = String.format("Taking %.0f%% of dealt damage", damageRatio * 100);
            }
        }

        // Check for low headshot rate (if enough hits)
        if (indicator == null && metrics.totalHits() > 20 && metrics.headshotRate() < 0.1f) {
            indicator = AnalyticsHook.StruggleIndicator.NO_HEADSHOTS;
            severity = 0.5f;
            context = String.format("Headshot rate: %.0f%%", metrics.headshotRate() * 100);
        }

        // Check for combo breaking
        if (indicator == null && previousCombo > 10 && metrics.currentCombo() == 0) {
            indicator = AnalyticsHook.StruggleIndicator.COMBO_BREAKING;
            severity = 0.4f;
            context = "Lost " + previousCombo + " combo";
        }

        // Dispatch if indicator found
        if (indicator != null) {
            lastStruggleNotifyTime = now;
            final AnalyticsHook.StruggleIndicator finalIndicator = indicator;
            final float finalSeverity = severity;
            final String finalContext = context;

            for (AnalyticsHook hook : hooks) {
                try {
                    hook.onStruggleDetected(finalIndicator, finalSeverity, finalContext);
                } catch (Exception e) {
                    LOGGER.error("[DevMod] Error in analytics hook onStruggleDetected: {}", e.getMessage());
                }
            }
        }

        previousCombo = metrics.currentCombo();
    }

    /**
     * Checks for opportunity patterns and notifies hooks if detected.
     */
    private void checkOpportunityPatterns(CombatMetrics metrics, long now) {
        if (now - lastOpportunityNotifyTime < OPPORTUNITY_COOLDOWN_MS) {
            return; // Still in cooldown
        }

        AnalyticsHook.OpportunityIndicator indicator = null;
        String context = "";

        // Check for high combo (S rank or above)
        if (metrics.comboRank().startsWith("S") && metrics.currentCombo() >= 20) {
            indicator = AnalyticsHook.OpportunityIndicator.HIGH_COMBO;
            context = metrics.comboRank() + " rank with " + metrics.currentCombo() + " combo";
        }

        // Check for burst damage (high rolling DPS)
        if (indicator == null && metrics.rollingDPS() > 15.0f) {
            indicator = AnalyticsHook.OpportunityIndicator.BURST_DAMAGE;
            context = String.format("%.1f DPS burst", metrics.rollingDPS());
        }

        // Check for crit streak (high crit rate with enough hits)
        if (indicator == null && metrics.totalHits() > 15 && metrics.critRate() > 0.4f) {
            indicator = AnalyticsHook.OpportunityIndicator.CRIT_STREAK;
            context = String.format("%.0f%% crit rate", metrics.critRate() * 100);
        }

        // Dispatch if indicator found
        if (indicator != null) {
            lastOpportunityNotifyTime = now;
            final AnalyticsHook.OpportunityIndicator finalIndicator = indicator;
            final String finalContext = context;

            for (AnalyticsHook hook : hooks) {
                try {
                    hook.onOpportunityDetected(finalIndicator, finalContext);
                } catch (Exception e) {
                    LOGGER.error("[DevMod] Error in analytics hook onOpportunityDetected: {}", e.getMessage());
                }
            }
        }
    }

    // === Utility ===

    /**
     * Checks if an analytics session is currently active.
     */
    public boolean isSessionActive() {
        return activeQuestId != null;
    }

    /**
     * Gets the number of registered hooks.
     */
    public int getHookCount() {
        return hooks.size();
    }

    /**
     * Gets the active quest ID (for external coordination).
     */
    public UUID getActiveQuestId() {
        return activeQuestId;
    }
}
