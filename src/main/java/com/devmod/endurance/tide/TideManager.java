package com.devmod.endurance.tide;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.nbt.CompoundTag;

import com.devmod.config.gamedesign.GameDesignConfig;
import com.devmod.config.gamedesign.GameDesignConfigManager;

/**
 * Tide Manager - Tracks and manages the global threat level.
 *
 * The Tide rises with player failures (deaths, failed quests) and falls
 * with successes (quest completions, boss kills, SSS waves).
 *
 * When Tide reaches 1000, The Harbinger appears across all active quests.
 */
public class TideManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(TideManager.class);

    public static final TideManager INSTANCE = new TideManager();

    // Global tide value (0-1000)
    private int currentTide = 0;
    private TideLevel currentLevel = TideLevel.CALM;

    // Tide Boss state
    private boolean tideBossActive = false;
    private UUID tideBossId = null;
    private float tideBossSharedHealth = 0f;
    private float tideBossMaxHealth = 0f;

    // Tracking recent events for anti-exploit
    private final Map<UUID, Long> recentDeaths = new ConcurrentHashMap<>();
    private final Map<UUID, Long> recentResonances = new ConcurrentHashMap<>();
    private static final long COOLDOWN_MS = 5000; // 5 second cooldown per player

    // Event listeners
    private final List<Consumer<TideEvent>> listeners = new ArrayList<>();

    // Min tide (max is now configurable via GameDesignConfig.TideConfig.maxTide)
    private static final int MIN_TIDE = 0;

    private TideManager() {}

    /**
     * Get config for the given quest instance.
     */
    private GameDesignConfig.TideConfig getConfig(@Nullable UUID questId) {
        return GameDesignConfigManager.INSTANCE.getTideConfig(questId);
    }

    /**
     * Check if Tide system is enabled.
     */
    public boolean isEnabled(@Nullable UUID questId) {
        return getConfig(questId).enabled;
    }

    // ========== Tide Modification ==========

    /**
     * Add tide (positive = increase threat, negative = decrease).
     *
     * @param amount Amount to add (positive or negative)
     * @param reason Reason for the change (for logging)
     * @param questId Quest ID for config lookup (nullable)
     */
    public void addTide(int amount, String reason, @Nullable UUID questId) {
        // Check if enabled
        GameDesignConfig.TideConfig config = getConfig(questId);
        if (!config.enabled) {
            return;
        }

        int oldTide = currentTide;
        TideLevel oldLevel = currentLevel;
        int maxTide = config.maxTide;

        currentTide = Math.max(MIN_TIDE, Math.min(maxTide, currentTide + amount));
        currentLevel = TideLevel.fromTide(currentTide);

        if (currentTide != oldTide) {
            LOGGER.debug("[Tide] {} {} (now {}, level: {})",
                amount > 0 ? "+" : "", amount, currentTide, currentLevel.displayName);

            // Check for level change
            if (currentLevel != oldLevel) {
                notifyLevelChange(oldLevel, currentLevel);
            }

            // Check for Tide Boss threshold
            if (currentTide >= maxTide && !tideBossActive) {
                triggerTideBoss();
            }
        }
    }

    /**
     * Add tide (backward compatible, no questId).
     * @deprecated Use {@link #addTide(int, String, UUID)} instead.
     */
    @Deprecated
    public void addTide(int amount, String reason) {
        addTide(amount, reason, null);
    }

    // ========== Event Recording ==========

    /**
     * Record player death in quest.
     */
    public void onPlayerDeath(UUID playerId, @Nullable UUID questId) {
        if (!checkCooldown(recentDeaths, playerId)) return;
        GameDesignConfig.TideConfig config = getConfig(questId);
        addTide(config.playerDeath, "player_death", questId);
    }

    /** @deprecated Use {@link #onPlayerDeath(UUID, UUID)} instead. */
    @Deprecated
    public void onPlayerDeath(UUID playerId) {
        onPlayerDeath(playerId, null);
    }

    /**
     * Record quest failure (before wave 5).
     */
    public void onQuestFailedEarly(UUID questId) {
        GameDesignConfig.TideConfig config = getConfig(questId);
        addTide(config.questFailedEarly, "quest_failed_early", questId);
    }

    /**
     * Record quest completion.
     */
    public void onQuestCompleted(UUID questId, boolean perfect) {
        GameDesignConfig.TideConfig config = getConfig(questId);
        addTide(config.questCompleted, "quest_completed", questId);
        if (perfect) {
            addTide(config.perfectQuest, "perfect_quest", questId);
        }
    }

    /**
     * Record SSS wave completion.
     */
    public void onSSSWave(UUID playerId, @Nullable UUID questId) {
        GameDesignConfig.TideConfig config = getConfig(questId);
        addTide(config.sssWave, "sss_wave", questId);
    }

    /** @deprecated Use {@link #onSSSWave(UUID, UUID)} instead. */
    @Deprecated
    public void onSSSWave(UUID playerId) {
        onSSSWave(playerId, null);
    }

    /**
     * Record boss kill.
     */
    public void onBossKilled(UUID bossId, @Nullable UUID questId) {
        GameDesignConfig.TideConfig config = getConfig(questId);
        addTide(config.bossKilled, "boss_killed", questId);
    }

    /** @deprecated Use {@link #onBossKilled(UUID, UUID)} instead. */
    @Deprecated
    public void onBossKilled(UUID bossId) {
        onBossKilled(bossId, null);
    }

    /**
     * Record resonance chain trigger.
     */
    public void onResonance(UUID playerId, @Nullable UUID questId) {
        if (!checkCooldown(recentResonances, playerId)) return;
        GameDesignConfig.TideConfig config = getConfig(questId);
        addTide(config.resonance, "resonance", questId);
    }

    /** @deprecated Use {@link #onResonance(UUID, UUID)} instead. */
    @Deprecated
    public void onResonance(UUID playerId) {
        onResonance(playerId, null);
    }

    /**
     * Record no-hit wave completion.
     */
    public void onNoHitWave(UUID playerId, @Nullable UUID questId) {
        GameDesignConfig.TideConfig config = getConfig(questId);
        addTide(config.noHitWave, "no_hit_wave", questId);
    }

    /** @deprecated Use {@link #onNoHitWave(UUID, UUID)} instead. */
    @Deprecated
    public void onNoHitWave(UUID playerId) {
        onNoHitWave(playerId, null);
    }

    // ========== Tide Boss Event ==========

    /**
     * Trigger the Tide Boss event across all active quests.
     */
    private void triggerTideBoss() {
        tideBossActive = true;
        tideBossId = UUID.randomUUID();
        tideBossMaxHealth = calculateTideBossHealth();
        tideBossSharedHealth = tideBossMaxHealth;

        LOGGER.warn("[Tide] THE HARBINGER HAS ARRIVED! Tide Boss spawning in all active quests.");

        // Notify listeners
        notifyEvent(new TideEvent(TideEventType.TIDE_BOSS_SPAWN, currentTide, currentLevel));
    }

    /**
     * Record damage dealt to the Tide Boss (shared health pool).
     */
    public void onTideBossDamage(float damage) {
        if (!tideBossActive) return;

        tideBossSharedHealth = Math.max(0, tideBossSharedHealth - damage);

        if (tideBossSharedHealth <= 0) {
            onTideBossDefeated();
        }
    }

    /**
     * Handle Tide Boss defeat.
     */
    private void onTideBossDefeated() {
        LOGGER.info("[Tide] THE HARBINGER HAS BEEN VANQUISHED! Tide reset to 0.");

        tideBossActive = false;
        tideBossId = null;
        currentTide = 0;
        currentLevel = TideLevel.CALM;

        // Notify listeners
        notifyEvent(new TideEvent(TideEventType.TIDE_BOSS_DEFEATED, currentTide, currentLevel));
    }

    /**
     * Calculate Tide Boss health based on active players.
     */
    private float calculateTideBossHealth() {
        // Base health scaled by number of active quest players
        // This would need to query EnduranceQuestManager for active player count
        int playerCount = Math.max(1, 4); // Placeholder - would get actual count
        return 1000f * playerCount; // 1000 HP per player
    }

    // ========== Stat Modifiers ==========

    /**
     * Get the mob stat multiplier for current tide level.
     */
    public float getMobStatMultiplier() {
        return 1f + currentLevel.statBonus;
    }

    /**
     * Check if curse mutators should be enabled.
     */
    public boolean shouldEnableCurseMutators() {
        return currentLevel.curseMutators;
    }

    /**
     * Check if this wave should force a boss.
     */
    public boolean shouldForceBooss(int waveNumber) {
        if (!currentLevel.forcedBosses) return false;
        if (currentLevel.bossWaveInterval <= 0) return false;
        return waveNumber % currentLevel.bossWaveInterval == 0;
    }

    // ========== Event System ==========

    /**
     * Register a listener for tide events.
     */
    public void addListener(Consumer<TideEvent> listener) {
        listeners.add(listener);
    }

    /**
     * Remove a listener.
     */
    public void removeListener(Consumer<TideEvent> listener) {
        listeners.remove(listener);
    }

    private void notifyLevelChange(TideLevel oldLevel, TideLevel newLevel) {
        TideEventType type = newLevel.ordinal() > oldLevel.ordinal() ?
            TideEventType.LEVEL_INCREASED : TideEventType.LEVEL_DECREASED;
        notifyEvent(new TideEvent(type, currentTide, newLevel));
    }

    private void notifyEvent(TideEvent event) {
        for (Consumer<TideEvent> listener : listeners) {
            try {
                listener.accept(event);
            } catch (Exception e) {
                LOGGER.error("[Tide] Error notifying listener", e);
            }
        }
    }

    // ========== Utility ==========

    /**
     * Check cooldown to prevent exploit spam.
     */
    private boolean checkCooldown(Map<UUID, Long> cooldowns, UUID playerId) {
        long now = System.currentTimeMillis();
        Long lastTime = cooldowns.get(playerId);
        if (lastTime != null && now - lastTime < COOLDOWN_MS) {
            return false;
        }
        cooldowns.put(playerId, now);
        return true;
    }

    // ========== NBT Persistence ==========

    /**
     * Save tide state to NBT.
     */
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("tide", currentTide);
        tag.putBoolean("bossActive", tideBossActive);
        if (tideBossActive && tideBossId != null) {
            tag.putUUID("bossId", tideBossId);
            tag.putFloat("bossHealth", tideBossSharedHealth);
            tag.putFloat("bossMaxHealth", tideBossMaxHealth);
        }
        return tag;
    }

    /**
     * Load tide state from NBT.
     */
    public void load(CompoundTag tag) {
        currentTide = tag.getInt("tide");
        currentLevel = TideLevel.fromTide(currentTide);
        tideBossActive = tag.getBoolean("bossActive");
        if (tideBossActive && tag.hasUUID("bossId")) {
            tideBossId = tag.getUUID("bossId");
            tideBossSharedHealth = tag.getFloat("bossHealth");
            tideBossMaxHealth = tag.getFloat("bossMaxHealth");
        }

        LOGGER.info("[Tide] Loaded tide state: {} ({})",
            currentTide, currentLevel.displayName);
    }

    /**
     * Reset tide state.
     */
    public void reset() {
        currentTide = 0;
        currentLevel = TideLevel.CALM;
        tideBossActive = false;
        tideBossId = null;
        recentDeaths.clear();
        recentResonances.clear();
    }

    // ========== Getters ==========

    public int getCurrentTide() { return currentTide; }
    public TideLevel getCurrentLevel() { return currentLevel; }
    public boolean isTideBossActive() { return tideBossActive; }
    public UUID getTideBossId() { return tideBossId; }
    public float getTideBossHealth() { return tideBossSharedHealth; }
    public float getTideBossMaxHealth() { return tideBossMaxHealth; }
    public float getTideBossHealthPercent() {
        return tideBossMaxHealth > 0 ? tideBossSharedHealth / tideBossMaxHealth : 0f;
    }

    // ========== Event Types ==========

    public enum TideEventType {
        LEVEL_INCREASED,
        LEVEL_DECREASED,
        TIDE_BOSS_SPAWN,
        TIDE_BOSS_DEFEATED
    }

    public record TideEvent(
        TideEventType type,
        int tide,
        TideLevel level
    ) {}
}
