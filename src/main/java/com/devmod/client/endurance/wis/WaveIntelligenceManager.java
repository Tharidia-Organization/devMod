package com.devmod.client.endurance.wis;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

import com.devmod.config.WISClientConfig;

/**
 * Central controller for the Wave Intelligence System.
 * Manages phase transitions, telemetry collection, and UI coordination.
 *
 * <p>Singleton instance - use WaveIntelligenceManager.INSTANCE
 *
 * <h3>KNOWN LIMITATION: Singleton and Multiplayer</h3>
 * <p>This is a singleton pattern which means all state (including hudFadeProgress)
 * is shared across the entire client. In scenarios with multiple local players
 * (e.g., split-screen mods, replay systems), each "player view" shares the same
 * WIS state. This is acceptable for standard Minecraft client usage but would
 * need refactoring to a per-player instance model for multi-view support.
 *
 * <p>Configuration is persisted via {@link WISClientConfig} and loaded on first access.
 */
@SuppressWarnings("NonAtomicVolatileUpdate") // Client-side only, accessed from main thread
public final class WaveIntelligenceManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(WaveIntelligenceManager.class);

    public static final WaveIntelligenceManager INSTANCE = new WaveIntelligenceManager();

    // Current state
    private volatile WavePhase currentPhase = WavePhase.IDLE;
    private volatile int phaseTicksRemaining = -1;
    private volatile int currentTick = 0;

    // Active wave data
    @Nullable private volatile UUID currentQuestId;
    @Nullable private volatile WaveTelemetryCollector currentCollector;
    private volatile int currentWaveNumber = 0;

    // Wave briefing data (populated before PRE_BRIEF phase)
    @Nullable private volatile WaveBriefingData briefingData;

    // History of completed waves (for comparison)
    private final List<WaveTelemetryCollector> waveHistory = new ArrayList<>();
    private static final int MAX_WAVE_HISTORY = 10;

    // Listeners for phase changes
    private final List<Consumer<WavePhase>> phaseChangeListeners = new ArrayList<>();

    // Configuration
    private volatile boolean enabled = true;
    private volatile boolean autoShowDebrief = true;
    private volatile float minimalHudOpacity = 0.2f;
    private volatile HudPosition hudPosition = HudPosition.TOP_RIGHT;
    private volatile int briefingMobListLimit = 5;

    // Per-instance fade state (Fix #8: NOT static for multiplayer safety)
    private volatile float hudFadeProgress = 0f;
    private static final float FADE_SPEED = 0.1f;

    /**
     * HUD position options for MinimalCombatHUD.
     */
    public enum HudPosition {
        TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT
    }

    private WaveIntelligenceManager() {
        loadFromConfig();
    }

    // ========== Config Sync ==========

    /**
     * Load settings from WISClientConfig.
     * Called on construction and can be called to refresh settings.
     */
    public void loadFromConfig() {
        try {
            if (WISClientConfig.SPEC.isLoaded()) {
                this.enabled = WISClientConfig.WIS_ENABLED.get();
                this.autoShowDebrief = WISClientConfig.AUTO_SHOW_DEBRIEF.get();
                this.minimalHudOpacity = WISClientConfig.HUD_OPACITY.get().floatValue();
                this.briefingMobListLimit = WISClientConfig.BRIEFING_MOB_LIST_LIMIT.get();

                // Convert config enum to internal enum
                WISClientConfig.HudPosition configPos = WISClientConfig.HUD_POSITION.get();
                this.hudPosition = switch (configPos) {
                    case TOP_LEFT -> HudPosition.TOP_LEFT;
                    case TOP_RIGHT -> HudPosition.TOP_RIGHT;
                    case BOTTOM_LEFT -> HudPosition.BOTTOM_LEFT;
                    case BOTTOM_RIGHT -> HudPosition.BOTTOM_RIGHT;
                };

                LOGGER.debug("[WIS] Config loaded: enabled={}, hudPos={}, opacity={}",
                    enabled, hudPosition, minimalHudOpacity);
            }
        } catch (Exception e) {
            LOGGER.warn("[WIS] Failed to load config, using defaults", e);
        }
    }

    /**
     * Save current settings to WISClientConfig.
     * Call this after changing settings programmatically.
     */
    public void syncToConfig() {
        try {
            if (WISClientConfig.SPEC.isLoaded()) {
                WISClientConfig.WIS_ENABLED.set(this.enabled);
                WISClientConfig.AUTO_SHOW_DEBRIEF.set(this.autoShowDebrief);
                WISClientConfig.HUD_OPACITY.set((double) this.minimalHudOpacity);
                WISClientConfig.BRIEFING_MOB_LIST_LIMIT.set(this.briefingMobListLimit);

                // Convert internal enum to config enum
                WISClientConfig.HudPosition configPos = switch (this.hudPosition) {
                    case TOP_LEFT -> WISClientConfig.HudPosition.TOP_LEFT;
                    case TOP_RIGHT -> WISClientConfig.HudPosition.TOP_RIGHT;
                    case BOTTOM_LEFT -> WISClientConfig.HudPosition.BOTTOM_LEFT;
                    case BOTTOM_RIGHT -> WISClientConfig.HudPosition.BOTTOM_RIGHT;
                };
                WISClientConfig.HUD_POSITION.set(configPos);

                LOGGER.debug("[WIS] Config saved");
            }
        } catch (Exception e) {
            LOGGER.warn("[WIS] Failed to save config", e);
        }
    }

    // ========== Lifecycle ==========

    /**
     * Initialize WIS for a new quest.
     */
    public void startQuest(UUID questId) {
        currentQuestId = questId;
        currentWaveNumber = 0;
        waveHistory.clear();
        transitionTo(WavePhase.IDLE);
        LOGGER.info("[WIS] Started tracking quest {}", questId);
    }

    /**
     * End WIS tracking for current quest.
     */
    public void endQuest() {
        if (currentCollector != null) {
            currentCollector.stopCollecting();
        }
        currentQuestId = null;
        currentCollector = null;
        briefingData = null;
        transitionTo(WavePhase.IDLE);
        LOGGER.info("[WIS] Quest tracking ended");
    }

    /**
     * Begin pre-wave briefing phase.
     * Call this when wave countdown starts (10 second countdown).
     */
    public void beginPreBrief(int waveNumber, WaveBriefingData briefing) {
        if (!enabled) return;

        this.currentWaveNumber = waveNumber;
        this.briefingData = briefing;

        // Create new collector for this wave
        currentCollector = new WaveTelemetryCollector(
            currentQuestId != null ? currentQuestId : UUID.randomUUID(),
            waveNumber,
            currentTick
        );

        transitionTo(WavePhase.PRE_BRIEF);
        LOGGER.debug("[WIS] Pre-brief started for wave {}", waveNumber);
    }

    /**
     * Begin combat phase.
     * Call this when wave actually starts (mobs begin spawning).
     */
    public void beginCombat() {
        if (!enabled) return;

        transitionTo(WavePhase.COMBAT);
        LOGGER.debug("[WIS] Combat started for wave {}", currentWaveNumber);
    }

    /**
     * End combat and begin debrief phase.
     * Call this when wave is complete (all mobs killed or objective met).
     */
    public void endCombat() {
        if (!enabled) return;

        if (currentCollector != null) {
            currentCollector.stopCollecting();

            // Archive to history
            if (waveHistory.size() >= MAX_WAVE_HISTORY) {
                waveHistory.remove(0);
            }
            waveHistory.add(currentCollector);
        }

        if (autoShowDebrief) {
            transitionTo(WavePhase.DEBRIEF);
        } else {
            transitionTo(WavePhase.IDLE);
        }

        LOGGER.debug("[WIS] Combat ended for wave {}, {} events collected",
            currentWaveNumber, currentCollector != null ? currentCollector.getEventCount() : 0);
    }

    /**
     * Skip debrief and return to idle.
     * Called when player wants to skip the debrief screen.
     */
    public void skipDebrief() {
        if (currentPhase == WavePhase.DEBRIEF) {
            transitionTo(WavePhase.IDLE);
        }
    }

    // ========== Event Recording (delegate to collector) ==========

    public void recordKill(UUID mobId, ResourceLocation mobType, BlockPos position,
                           float damageDealt, String weaponUsed, boolean isElite,
                           boolean isCritical, int comboCount, long timeToKillMs) {
        if (currentCollector != null && currentPhase == WavePhase.COMBAT) {
            currentCollector.recordKill(mobId, mobType, position, damageDealt,
                weaponUsed, isElite, isCritical, comboCount, timeToKillMs, currentTick);
        }
    }

    public void recordDamageDealt(UUID mobId, ResourceLocation mobType, float damage,
                                   String damageSource, boolean isCritical, BlockPos position) {
        if (currentCollector != null && currentPhase == WavePhase.COMBAT) {
            currentCollector.recordDamageDealt(mobId, mobType, damage, damageSource,
                isCritical, position, currentTick);
        }
    }

    public void recordDamageTaken(@Nullable UUID sourceId, @Nullable ResourceLocation sourceType,
                                   float damage, String damageSource, float remainingHealth,
                                   BlockPos playerPosition) {
        if (currentCollector != null && currentPhase == WavePhase.COMBAT) {
            currentCollector.recordDamageTaken(sourceId, sourceType, damage, damageSource,
                remainingHealth, playerPosition, currentTick);
        }
    }

    public void recordComboEvent(CombatEvent.ComboEvent.ComboEventType type, int comboCount,
                                  int styleScore, String rankName) {
        if (currentCollector != null && currentPhase == WavePhase.COMBAT) {
            currentCollector.recordComboEvent(type, comboCount, styleScore, rankName, currentTick);
        }
    }

    public void recordPositionSnapshot(BlockPos position, float health, int mobsNearby, boolean inCombat) {
        if (currentCollector != null && currentPhase == WavePhase.COMBAT) {
            currentCollector.recordPositionSnapshot(position, health, mobsNearby, inCombat, currentTick);
        }
    }

    public void recordSpecialAction(String actionType, int pointsEarned, int styleEarned, BlockPos position) {
        if (currentCollector != null && currentPhase == WavePhase.COMBAT) {
            currentCollector.recordSpecialAction(actionType, pointsEarned, styleEarned, position, currentTick);
        }
    }

    public void recordMobSpawn(UUID mobId, ResourceLocation mobType, BlockPos position,
                                boolean isElite, String affix) {
        if (currentCollector != null) {
            currentCollector.recordMobSpawn(mobId, mobType, position, isElite, affix, currentTick);
        }
    }

    public void recordPlayerDeath(BlockPos position) {
        if (currentCollector != null) {
            currentCollector.recordPlayerDeath(position);
        }
    }

    // ========== Tick Update ==========

    /**
     * Called every client tick to update phase timers.
     */
    public void tick() {
        currentTick++;

        // Update phase timer
        if (phaseTicksRemaining > 0) {
            phaseTicksRemaining--;

            if (phaseTicksRemaining == 0) {
                onPhaseTimerExpired();
            }
        }

        // Position snapshot during combat
        if (currentPhase == WavePhase.COMBAT && currentCollector != null) {
            Minecraft mc = Minecraft.getInstance();
            Player player = mc.player;
            if (player != null) {
                int mobsNearby = player.level().getEntitiesOfClass(
                    net.minecraft.world.entity.Mob.class,
                    Objects.requireNonNull(player.getBoundingBox().inflate(10))
                ).size();
                recordPositionSnapshot(player.blockPosition(), player.getHealth(), mobsNearby, true);
            }
        }
    }

    private void onPhaseTimerExpired() {
        switch (currentPhase) {
            case PRE_BRIEF -> {
                // Pre-brief countdown finished, combat should start
                // Note: actual combat start is triggered externally
                LOGGER.debug("[WIS] Pre-brief timer expired");
            }
            case DEBRIEF -> {
                // Auto-close debrief after timeout
                skipDebrief();
            }
            default -> {}
        }
    }

    // ========== Phase Transitions ==========

    private void transitionTo(WavePhase newPhase) {
        WavePhase oldPhase = currentPhase;
        currentPhase = newPhase;
        phaseTicksRemaining = newPhase.hasFixedDuration() ? newPhase.getDefaultDurationTicks() : -1;

        LOGGER.debug("[WIS] Phase transition: {} -> {}", oldPhase, newPhase);

        // Notify listeners
        for (Consumer<WavePhase> listener : phaseChangeListeners) {
            try {
                listener.accept(newPhase);
            } catch (Exception e) {
                LOGGER.error("[WIS] Error in phase change listener", e);
            }
        }
    }

    // ========== Getters ==========

    public WavePhase getCurrentPhase() { return currentPhase; }
    public int getPhaseTicksRemaining() { return phaseTicksRemaining; }
    public int getCurrentWaveNumber() { return currentWaveNumber; }
    public boolean isEnabled() { return enabled; }
    public float getMinimalHudOpacity() { return minimalHudOpacity; }

    @Nullable
    public WaveTelemetryCollector getCurrentCollector() { return currentCollector; }

    @Nullable
    public WaveBriefingData getBriefingData() { return briefingData; }

    /**
     * Get the previous wave's collector for comparison.
     */
    @Nullable
    public WaveTelemetryCollector getPreviousWaveCollector() {
        return waveHistory.isEmpty() ? null : waveHistory.get(waveHistory.size() - 1);
    }

    /**
     * Get wave history for trend analysis.
     */
    public List<WaveTelemetryCollector> getWaveHistory() {
        return new ArrayList<>(waveHistory);
    }

    // ========== Configuration ==========

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        syncToConfig();
    }

    public void setAutoShowDebrief(boolean autoShow) {
        this.autoShowDebrief = autoShow;
        syncToConfig();
    }

    public void setMinimalHudOpacity(float opacity) {
        this.minimalHudOpacity = Math.max(0f, Math.min(1f, opacity));
        syncToConfig();
    }

    public HudPosition getHudPosition() { return hudPosition; }

    public void setHudPosition(HudPosition position) {
        this.hudPosition = position;
        syncToConfig();
    }

    public int getBriefingMobListLimit() { return briefingMobListLimit; }

    public void setBriefingMobListLimit(int limit) {
        this.briefingMobListLimit = Math.max(1, Math.min(20, limit));
        syncToConfig();
    }

    // ========== HUD Fade State (Fix #8: instance-based, not static) ==========

    public float getHudFadeProgress() { return hudFadeProgress; }

    /**
     * Update fade progress based on current phase.
     * Call this from MinimalCombatHUD.render() instead of using static field.
     */
    public void updateHudFade() {
        if (currentPhase == WavePhase.COMBAT) {
            hudFadeProgress = Math.min(1f, hudFadeProgress + FADE_SPEED);
        } else {
            hudFadeProgress = Math.max(0f, hudFadeProgress - FADE_SPEED);
        }
    }

    public void resetHudFade() {
        hudFadeProgress = 0f;
    }

    // ========== Listeners ==========

    public void addPhaseChangeListener(Consumer<WavePhase> listener) {
        phaseChangeListeners.add(listener);
    }

    public void removePhaseChangeListener(Consumer<WavePhase> listener) {
        phaseChangeListeners.remove(listener);
    }

    // ========== Cleanup ==========

    /**
     * Reset all state (called on disconnect, etc).
     */
    public void reset() {
        endQuest();
        waveHistory.clear();
        phaseChangeListeners.clear();
    }
}
