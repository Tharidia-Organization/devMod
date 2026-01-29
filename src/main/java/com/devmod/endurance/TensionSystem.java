package com.devmod.endurance;

import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.devmod.endurance.config.EnduranceConfigManager;
import com.devmod.endurance.lifecycle.QuestLifecycleEvent.QuestEnded;
import com.devmod.endurance.lifecycle.QuestLifecycleEvent.QuestStarted;
import com.devmod.endurance.lifecycle.QuestLifecycleListener;

/*
 * Dynamic boss spawning system based on player performance.
 *
 * Implements QuestLifecycleListener to receive quest lifecycle events.
 * Note: TensionSystem is quest-scoped (shared between party members).
 *
 * Priority: 800 (Core gameplay)
 */
public class TensionSystem implements QuestLifecycleListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(TensionSystem.class);

    public static final TensionSystem INSTANCE = new TensionSystem();

    // Fallback tension gain values (used when no quest context available)
    private static final float DEFAULT_HIGH_COMBO_BONUS = 0.08f;
    private static final float DEFAULT_STYLE_RANK_BONUS = 0.10f;
    private static final float DEFAULT_KILL_STREAK_BONUS = 0.05f;

    // Fallback boss threshold range
    private static final float DEFAULT_MIN_THRESHOLD = 0.70f;
    private static final float DEFAULT_MAX_THRESHOLD = 1.00f;

    // Config manager reference
    private final EnduranceConfigManager config = EnduranceConfigManager.INSTANCE;

    private final Random random = new Random();

    // Per-quest tension state
    private final Map<UUID, TensionState> questStates = new ConcurrentHashMap<>();

    /*
     * Tension state for a single quest.
     */
    public static class TensionState {
        private float currentTension = 0f;
        private float bossThreshold;
        private int wavesSinceLastBoss = 0;
        private int totalBossesSpawned = 0;
        private boolean bossWavePending = false;

        // Tracking for current wave
        private boolean currentWaveNoHit = true;
        private int currentWaveMaxCombo = 0;
        private ComboSystem.StyleRank currentWaveMaxRank = ComboSystem.StyleRank.D;
        private int rapidKillStreak = 0;
        private long lastKillTime = 0;

        public TensionState(float initialThreshold) {
            this.bossThreshold = initialThreshold;
        }

        public float getCurrentTension() { return currentTension; }
        public float getBossThreshold() { return bossThreshold; }
        public int getWavesSinceLastBoss() { return wavesSinceLastBoss; }
        public int getTotalBossesSpawned() { return totalBossesSpawned; }
        public boolean isBossWavePending() { return bossWavePending; }

        /*
         * Get tension as a percentage of threshold (for UI).
         */
        public float getTensionPercent() {
            return Math.min(1.0f, currentTension / bossThreshold);
        }

        /*
         * Get visual tension level (0-4 for UI intensity).
         */
        public int getTensionLevel() {
            float percent = getTensionPercent();
            if (percent < 0.25f) return 0;
            if (percent < 0.50f) return 1;
            if (percent < 0.75f) return 2;
            if (percent < 0.90f) return 3;
            return 4; // Critical - boss imminent
        }
    }

    private TensionSystem() {}

    // ========== Session Management ==========

    /*
     * Start tension tracking for a quest.
     */
    public TensionState startSession(UUID questId) {
        float initialThreshold = randomThreshold();
        TensionState state = new TensionState(initialThreshold);
        questStates.put(questId, state);
        LOGGER.info("[TensionSystem] Started session for quest {} with threshold {}", questId, initialThreshold);
        return state;
    }

    /*
     * End tension tracking for a quest.
     */
    public TensionState endSession(UUID questId) {
        return questStates.remove(questId);
    }

    /*
     * Get tension state for a quest.
     */
    public TensionState getState(UUID questId) {
        return questStates.get(questId);
    }

    // ========== QuestLifecycleListener Implementation ==========

    @Override
    public void onQuestStarted(QuestStarted event) {
        UUID questId = event.questId();
        // Only create session if none exists (shared per quest)
        if (getState(questId) == null) {
            startSession(questId);
        }
    }

    @Override
    public void onQuestEnded(QuestEnded event) {
        // Only end if cleanupShared is true (last player leaving)
        if (event.cleanupShared()) {
            endSession(event.questId());
        }
    }

    @Override
    public int getPriority() {
        return 800; // Core gameplay
    }

    @Override
    public String getListenerName() {
        return "TensionSystem";
    }

    @Override
    public boolean receivePracticeModeEvents() {
        return false; // Tension/boss system disabled in practice mode
    }

    // ========== Tension Accumulation ==========

    /*
     * Called when a wave is completed. Accumulates tension and checks for boss trigger.
     * Returns true if the next wave should be a boss wave.
     */
    public boolean onWaveComplete(UUID questId, int waveNumber) {
        TensionState state = questStates.get(questId);
        if (state == null) return false;

        // Get config values for this quest
        float baseWaveTension = (float) config.getTensionBaseWaveGain(questId);
        float noHitBonus = (float) config.getTensionNoHitBonus(questId);

        // Calculate tension gain for this wave
        float tensionGain = baseWaveTension;

        // No-hit bonus
        if (state.currentWaveNoHit) {
            tensionGain += noHitBonus;
            LOGGER.debug("[TensionSystem] No-hit wave bonus: +{}", noHitBonus);
        }

        // High combo bonus (50+ max combo)
        if (state.currentWaveMaxCombo >= 50) {
            tensionGain += DEFAULT_HIGH_COMBO_BONUS;
            LOGGER.debug("[TensionSystem] High combo bonus: +{}", DEFAULT_HIGH_COMBO_BONUS);
        }

        // Style rank bonus (S or higher)
        if (state.currentWaveMaxRank.ordinal() >= ComboSystem.StyleRank.S.ordinal()) {
            tensionGain += DEFAULT_STYLE_RANK_BONUS;
            LOGGER.debug("[TensionSystem] Style rank bonus: +{}", DEFAULT_STYLE_RANK_BONUS);
        }

        // Kill streak bonus (10+ rapid kills in wave)
        if (state.rapidKillStreak >= 10) {
            tensionGain += DEFAULT_KILL_STREAK_BONUS;
            LOGGER.debug("[TensionSystem] Kill streak bonus: +{}", DEFAULT_KILL_STREAK_BONUS);
        }

        // Apply tension gain (synchronized to prevent race with onPlayerDamaged/onMobKill)
        synchronized (state) {
            state.currentTension += tensionGain;
            state.wavesSinceLastBoss++;

            // Reset wave tracking
            resetWaveTracking(state);
        }

        LOGGER.info("[TensionSystem] Wave {} complete. Tension: {}/{} ({}%)",
            waveNumber, state.currentTension, state.bossThreshold,
            (int)(state.getTensionPercent() * 100));

        // Check for boss trigger
        boolean shouldSpawnBoss = checkBossTrigger(state, waveNumber, questId);
        state.bossWavePending = shouldSpawnBoss;

        return shouldSpawnBoss;
    }

    /*
     * Called when a boss wave is completed. Resets tension.
     * Thread-safe: Synchronized to prevent race conditions during multi-field reset.
     */
    public void onBossDefeated(UUID questId) {
        TensionState state = questStates.get(questId);
        if (state == null) return;

        float newThreshold = randomThreshold(questId);
        synchronized (state) {
            state.currentTension = 0f;
            state.bossThreshold = newThreshold;
            state.wavesSinceLastBoss = 0;
            state.totalBossesSpawned++;
            state.bossWavePending = false;
        }

        LOGGER.info("[TensionSystem] Boss defeated. Tension reset. New threshold: {}",
            newThreshold);
    }

    /*
     * Check if boss should spawn.
     */
    private boolean checkBossTrigger(TensionState state, int waveNumber, UUID questId) {
        // Get config values
        int minWavesBeforeBoss = config.getTensionMinWavesBeforeBoss(questId);
        int maxWavesWithoutBoss = config.getTensionMaxWavesWithoutBoss(questId);

        // Minimum waves before first boss
        if (waveNumber < minWavesBeforeBoss) {
            return false;
        }

        // Safety valve: force boss after max waves without one
        if (state.wavesSinceLastBoss >= maxWavesWithoutBoss) {
            LOGGER.info("[TensionSystem] Safety valve: forcing boss after {} waves", state.wavesSinceLastBoss);
            return true;
        }

        // Check if tension exceeds threshold
        if (state.currentTension >= state.bossThreshold) {
            LOGGER.info("[TensionSystem] Tension threshold exceeded: {} >= {}",
                state.currentTension, state.bossThreshold);
            return true;
        }

        return false;
    }

    // ========== Event Tracking ==========

    /*
     * Called when player takes damage in current wave.
     * Thread-safe: Synchronized to prevent race with wave tracking reads.
     */
    public void onPlayerDamaged(UUID questId) {
        TensionState state = questStates.get(questId);
        if (state != null) {
            synchronized (state) {
                state.currentWaveNoHit = false;
            }
        }
    }

    /*
     * Called when combo is updated.
     * Thread-safe: Synchronized to prevent race with wave completion reads.
     */
    public void onComboUpdate(UUID questId, int comboCount, ComboSystem.StyleRank rank) {
        TensionState state = questStates.get(questId);
        if (state != null) {
            synchronized (state) {
                if (comboCount > state.currentWaveMaxCombo) {
                    state.currentWaveMaxCombo = comboCount;
                }
                if (rank.ordinal() > state.currentWaveMaxRank.ordinal()) {
                    state.currentWaveMaxRank = rank;
                }
            }
        }
    }

    /*
     * Called when player kills a mob. Tracks rapid kill streaks.
     * Thread-safe: Synchronized to prevent race with wave completion reads.
     */
    public void onMobKill(UUID questId) {
        TensionState state = questStates.get(questId);
        if (state != null) {
            long now = System.currentTimeMillis();
            synchronized (state) {
                // Rapid kill = within 2 seconds of last kill
                if (now - state.lastKillTime < 2000) {
                    state.rapidKillStreak++;
                } else {
                    state.rapidKillStreak = 1;
                }
                state.lastKillTime = now;
            }
        }
    }

    /*
     * Reset wave-specific tracking.
     */
    private void resetWaveTracking(TensionState state) {
        state.currentWaveNoHit = true;
        state.currentWaveMaxCombo = 0;
        state.currentWaveMaxRank = ComboSystem.StyleRank.D;
        state.rapidKillStreak = 0;
        state.lastKillTime = 0;
    }

    // ========== Utility ==========

    /*
     * Generate a random boss threshold using default values.
     */
    private float randomThreshold() {
        return DEFAULT_MIN_THRESHOLD + random.nextFloat() * (DEFAULT_MAX_THRESHOLD - DEFAULT_MIN_THRESHOLD);
    }

    /*
     * Generate a random boss threshold using config values for a specific quest.
     */
    private float randomThreshold(UUID questId) {
        float minThreshold = (float) config.getTensionMinThreshold(questId);
        float maxThreshold = (float) config.getTensionMaxThreshold(questId);
        return minThreshold + random.nextFloat() * (maxThreshold - minThreshold);
    }

    /*
     * Manually add tension (for special events, mutators, etc).
     */
    public void addTension(UUID questId, float amount, String reason) {
        TensionState state = questStates.get(questId);
        if (state != null) {
            state.currentTension += amount;
            LOGGER.debug("[TensionSystem] Added {} tension ({}). Total: {}",
                amount, reason, state.currentTension);
        }
    }

    /*
     * Get tension info for UI display.
     */
    public TensionInfo getTensionInfo(UUID questId) {
        TensionState state = questStates.get(questId);
        if (state == null) {
            return new TensionInfo(0f, 0, false);
        }
        return new TensionInfo(
            state.getTensionPercent(),
            state.getTensionLevel(),
            state.bossWavePending
        );
    }

    /*
     * Tension info for UI/network sync.
     */
    public record TensionInfo(
        float percent,
        int level,
        boolean bossImminent
    ) {}
}
