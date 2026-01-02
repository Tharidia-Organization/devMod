package com.devmod.client.config;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.nbt.CompoundTag;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import com.devmod.client.state.ClientStateManager;
import com.devmod.client.state.stores.ConfigStateStore;

/**
 * Client-side cache for game mechanics configuration.
 *
 * <p>This class provides the public API for accessing mechanics config values.
 * It maintains backward compatibility while delegating state storage to
 * {@link ClientStateManager} and {@link ConfigStateStore}.
 *
 * <p>Config values use a 3-tier fallback system:
 * <ol>
 *   <li>Quest-specific overrides (from arena policy)</li>
 *   <li>Global config (synced from server on login)</li>
 *   <li>Hardcoded defaults</li>
 * </ol>
 */
@OnlyIn(Dist.CLIENT)
public final class ClientMechanicsCache {
    public static final ClientMechanicsCache INSTANCE = new ClientMechanicsCache();

    // Global defaults (synced on login)
    private volatile CompoundTag globalConfig = new CompoundTag();

    // Per-quest overrides (synced when entering quest)
    private final Map<UUID, CompoundTag> questOverrides = new ConcurrentHashMap<>();

    // Current quest context (set by client when entering quest)
    @Nullable
    private volatile UUID activeQuestId;

    private ClientMechanicsCache() {}

    // ============================================================================
    // SYNC METHODS (called by GameMechanicsSyncPayload)
    // ============================================================================

    /**
     * Apply global config sync from server.
     * Called on login and config reload.
     */
    public synchronized void applyGlobalSync(CompoundTag config) {
        globalConfig = config.copy();

        // Sync to unified state store
        syncToStateStore();
    }

    /**
     * Apply quest-specific overrides from server.
     * Called when entering a quest with arena policy overrides.
     */
    public void applyQuestSync(UUID questId, CompoundTag overrides) {
        if (questId != null && overrides != null) {
            questOverrides.put(questId, overrides.copy());
            syncToStateStore();
        }
    }

    /**
     * Set the currently active quest for config lookups.
     */
    public void setActiveQuest(@Nullable UUID questId) {
        this.activeQuestId = questId;

        // Update state store with active context
        syncToStateStore();
    }

    /**
     * Clear quest-specific overrides when quest ends.
     */
    public void clearQuestOverride(UUID questId) {
        if (questId != null) {
            questOverrides.remove(questId);
            if (questId.equals(activeQuestId)) {
                activeQuestId = null;
            }
            syncToStateStore();
        }
    }

    /**
     * Clear all cached data.
     */
    public synchronized void clearAll() {
        globalConfig = new CompoundTag();
        questOverrides.clear();
        activeQuestId = null;

        // Clear state store
        ClientStateManager.INSTANCE.getConfigStore().clear();
    }

    /**
     * Clear the active quest context and its overrides.
     * Called when quest ends on client (via QuestSyncPayload.empty()).
     */
    public void clearActiveQuestContext() {
        UUID questId = this.activeQuestId;
        if (questId != null) {
            clearQuestOverride(questId);
        }
    }

    // ============================================================================
    // STATE STORE SYNC
    // ============================================================================

    /**
     * Sync current state to the unified ConfigStateStore.
     */
    private void syncToStateStore() {
        Map<String, Double> mechanics = extractMechanicsMap();

        ConfigStateStore.ConfigState state = ConfigStateStore.ConfigState.builder()
            .mechanics(mechanics)
            .activeQuestContext(activeQuestId != null ? activeQuestId.toString() : null)
            .build();

        ClientStateManager.INSTANCE.getConfigStore().setState(state);
    }

    /**
     * Extract all mechanics values as a flat map for the state store.
     */
    private Map<String, Double> extractMechanicsMap() {
        Map<String, Double> map = new HashMap<>();

        // Combat multipliers
        map.put("combat.headMultiplier", getDouble("combat", "headMultiplier", 2.0));
        map.put("combat.bodyMultiplier", getDouble("combat", "bodyMultiplier", 1.0));
        map.put("combat.limbsMultiplier", getDouble("combat", "limbsMultiplier", 0.75));
        map.put("combat.bodyPartDetection", getDouble("combat", "bodyPartDetection", 1.0));

        // Combo system
        map.put("combo.timeoutTicks", (double) getComboTimeoutTicks());
        map.put("combo.basePoints", (double) getComboBasePoints());
        map.put("combo.multiplierIncrement", getComboMultiplierIncrement());
        map.put("combo.maxMultiplier", getComboMaxMultiplier());

        // Wave config
        map.put("waves.baseMobCount", (double) getWaveBaseMobCount());
        map.put("waves.mobScaling", getWaveMobScaling());

        return map;
    }

    // ============================================================================
    // RAW TAG ACCESS
    // ============================================================================

    public synchronized CompoundTag getGlobalConfig() {
        return globalConfig.copy();
    }

    @Nullable
    public CompoundTag getQuestOverrides(UUID questId) {
        CompoundTag overrides = questOverrides.get(questId);
        return overrides != null ? overrides.copy() : null;
    }

    // ============================================================================
    // CONFIG VALUE GETTERS (3-tier fallback)
    // ============================================================================

    // --- Combo System ---

    public int getComboTimeoutTicks() {
        return getInt("combo", "timeoutTicks", 60);
    }

    public int getComboBasePoints() {
        return getInt("combo", "basePoints", 10);
    }

    public double getComboMultiplierIncrement() {
        return getDouble("combo", "multiplierIncrement", 0.1);
    }

    public double getComboMaxMultiplier() {
        return getDouble("combo", "maxMultiplier", 4.0);
    }

    public int getComboFinisherThreshold() {
        return getInt("combo", "finisherThreshold", 10);
    }

    // --- Style Rank System ---

    public int getStyleRankDThreshold() {
        return getInt("styleRank", "dThreshold", 0);
    }

    public int getStyleRankCThreshold() {
        return getInt("styleRank", "cThreshold", 100);
    }

    public int getStyleRankBThreshold() {
        return getInt("styleRank", "bThreshold", 300);
    }

    public int getStyleRankAThreshold() {
        return getInt("styleRank", "aThreshold", 600);
    }

    public int getStyleRankSThreshold() {
        return getInt("styleRank", "sThreshold", 1000);
    }

    public int getStyleRankSSThreshold() {
        return getInt("styleRank", "ssThreshold", 1500);
    }

    public int getStyleRankSSSThreshold() {
        return getInt("styleRank", "sssThreshold", 2500);
    }

    public double getStyleDecayRate() {
        return getDouble("styleRank", "decayRate", 5.0);
    }

    // --- Tension System ---

    public double getTensionMinThreshold() {
        return getDouble("tension", "minThreshold", 0.70);
    }

    public double getTensionMaxThreshold() {
        return getDouble("tension", "maxThreshold", 1.0);
    }

    public int getTensionMinWavesBeforeBoss() {
        return getInt("tension", "minWavesBeforeBoss", 3);
    }

    public int getTensionMaxWavesWithoutBoss() {
        return getInt("tension", "maxWavesWithoutBoss", 8);
    }

    // --- Season Pass ---

    public int getSeasonMaxTier() {
        return getInt("seasonPass", "maxTier", 100);
    }

    public int getSeasonXpPerTier() {
        return getInt("seasonPass", "xpPerTier", 1000);
    }

    // --- Challenge System ---

    public int getChallengeDailyCount() {
        return getInt("challenges", "dailyCount", 3);
    }

    public int getChallengeWeeklyCount() {
        return getInt("challenges", "weeklyCount", 2);
    }

    public int getChallengeDailyTokenReward() {
        return getInt("challenges", "dailyTokenReward", 100);
    }

    public int getChallengeWeeklyTokenReward() {
        return getInt("challenges", "weeklyTokenReward", 500);
    }

    // --- Wave Configuration ---

    public int getWaveBaseMobCount() {
        return getInt("waves", "baseMobCount", 5);
    }

    public double getWaveMobScaling() {
        return getDouble("waves", "mobScaling", 1.2);
    }

    public int getWaveIntermissionTicks() {
        return getInt("waves", "intermissionTicks", 100);
    }

    public int getWaveBossInterval() {
        return getInt("waves", "bossInterval", 5);
    }

    // --- Execution System ---

    public double getExecutionHpThreshold() {
        return getDouble("execution", "hpThreshold", 0.15);
    }

    public int getExecutionStyleReward() {
        return getInt("execution", "styleReward", 200);
    }

    // --- Perk System ---

    public int getPerkChoicesPerSelection() {
        return getInt("perkRarity", "choicesPerSelection", 3);
    }

    public int getPerkRerollCostBase() {
        return getInt("perkRarity", "rerollCostBase", 50);
    }

    public int getPerkRerollCostIncrement() {
        return getInt("perkRarity", "rerollCostIncrement", 25);
    }

    // ============================================================================
    // INTERNAL HELPERS
    // ============================================================================

    /**
     * Get int value with 3-tier fallback: quest → global → default.
     */
    private int getInt(@Nonnull String section, @Nonnull String key, int defaultValue) {
        // 1. Check quest-specific override
        if (activeQuestId != null) {
            CompoundTag override = questOverrides.get(activeQuestId);
            if (override != null) {
                CompoundTag sectionTag = override.getCompound(section);
                if (sectionTag.contains(key)) {
                    return sectionTag.getInt(key);
                }
            }
        }

        // 2. Check global config
        synchronized (this) {
            if (globalConfig.contains(section)) {
                CompoundTag sectionTag = globalConfig.getCompound(section);
                if (sectionTag.contains(key)) {
                    return sectionTag.getInt(key);
                }
            }
        }

        // 3. Return hardcoded default
        return defaultValue;
    }

    /**
     * Get double value with 3-tier fallback: quest → global → default.
     */
    private double getDouble(@Nonnull String section, @Nonnull String key, double defaultValue) {
        // 1. Check quest-specific override
        if (activeQuestId != null) {
            CompoundTag override = questOverrides.get(activeQuestId);
            if (override != null) {
                CompoundTag sectionTag = override.getCompound(section);
                if (sectionTag.contains(key)) {
                    return sectionTag.getDouble(key);
                }
            }
        }

        // 2. Check global config
        synchronized (this) {
            if (globalConfig.contains(section)) {
                CompoundTag sectionTag = globalConfig.getCompound(section);
                if (sectionTag.contains(key)) {
                    return sectionTag.getDouble(key);
                }
            }
        }

        // 3. Return hardcoded default
        return defaultValue;
    }

}
