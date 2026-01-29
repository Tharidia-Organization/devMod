package com.devmod.endurance;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DifficultyScaler {
    private static final Logger LOGGER = LoggerFactory.getLogger(DifficultyScaler.class);

    /** Singleton instance */
    public static final DifficultyScaler INSTANCE = new DifficultyScaler();

    // Scaling constants
    private static final float BOSS_HP_SCALE_PER_PLAYER = 0.3f;
    private static final float BOSS_DMG_SCALE_PER_PLAYER = 0.1f;
    private static final float MOB_HP_SCALE_PER_PLAYER = 0.15f;
    private static final float MAX_MOB_COUNT_SCALE = 5.0f; // Cap at 5x mobs
    private static final float MAX_BOSS_HP_SCALE = 10.0f;  // Cap at 10x HP

    private DifficultyScaler() {
    }

    // === Mob Count Scaling ===

    /**
     * Scale mob count based on player count and quest type.
     * Uses sqrt(playerCount) to provide diminishing returns.
     *
     * @param baseMobCount Base number of mobs
     * @param playerCount Number of players (clamped to quest type limits)
     * @param questType Quest type for multiplier
     * @return Scaled mob count
     */
    public int scaleMobCount(int baseMobCount, int playerCount, QuestType questType) {
        if (baseMobCount <= 0 || playerCount <= 0) {
            return baseMobCount;
        }

        // Clamp player count to quest type limits
        int clampedPlayers = Math.max(1, Math.min(playerCount, questType.getMaxPlayers()));

        // Calculate scale: sqrt(players) * questMultiplier
        double scaleFactor = Math.sqrt(clampedPlayers) * questType.getDifficultyMultiplier();

        // Apply cap
        scaleFactor = Math.min(scaleFactor, MAX_MOB_COUNT_SCALE);

        int scaledCount = (int) Math.ceil(baseMobCount * scaleFactor);

        LOGGER.debug("[DifficultyScaler] Mob count: {} -> {} (players={}, type={}, scale={:.2f})",
                baseMobCount, scaledCount, clampedPlayers, questType, scaleFactor);

        return scaledCount;
    }

    /**
     * Scale mob count with default quest type (PVE_COOP).
     */
    public int scaleMobCount(int baseMobCount, int playerCount) {
        return scaleMobCount(baseMobCount, playerCount, QuestType.PVE_COOP);
    }

    // === Boss HP Scaling ===

    /**
     * Scale boss HP based on player count and quest type.
     * Formula: baseHP * (1 + (playerCount - 1) * 0.3) * questMultiplier
     *
     * @param baseHP Base boss health
     * @param playerCount Number of players
     * @param questType Quest type for multiplier
     * @return Scaled boss HP
     */
    public float scaleBossHealth(float baseHP, int playerCount, QuestType questType) {
        if (baseHP <= 0 || playerCount <= 0) {
            return baseHP;
        }

        int clampedPlayers = Math.max(1, Math.min(playerCount, questType.getMaxPlayers()));

        // Linear scaling per additional player
        float playerScale = 1.0f + (clampedPlayers - 1) * BOSS_HP_SCALE_PER_PLAYER;
        float totalScale = playerScale * questType.getDifficultyMultiplier();

        // Apply cap
        totalScale = Math.min(totalScale, MAX_BOSS_HP_SCALE);

        float scaledHP = baseHP * totalScale;

        LOGGER.debug("[DifficultyScaler] Boss HP: {} -> {} (players={}, type={}, scale={:.2f})",
                baseHP, scaledHP, clampedPlayers, questType, totalScale);

        return scaledHP;
    }

    /**
     * Scale boss HP with default quest type.
     */
    public float scaleBossHealth(float baseHP, int playerCount) {
        return scaleBossHealth(baseHP, playerCount, QuestType.PVE_COOP);
    }

    // === Boss Damage Scaling ===

    /**
     * Scale boss damage based on player count.
     * Formula: baseDamage * (1 + (playerCount - 1) * 0.1)
     * Note: Boss damage scales less aggressively than HP to keep fights fair.
     *
     * @param baseDamage Base boss damage
     * @param playerCount Number of players
     * @param questType Quest type (used for clamping)
     * @return Scaled boss damage
     */
    public float scaleBossDamage(float baseDamage, int playerCount, QuestType questType) {
        if (baseDamage <= 0 || playerCount <= 0) {
            return baseDamage;
        }

        int clampedPlayers = Math.max(1, Math.min(playerCount, questType.getMaxPlayers()));

        // Modest scaling - damage shouldn't scale as much as HP
        float damageScale = 1.0f + (clampedPlayers - 1) * BOSS_DMG_SCALE_PER_PLAYER;

        // Cap at 2x damage max
        damageScale = Math.min(damageScale, 2.0f);

        float scaledDamage = baseDamage * damageScale;

        LOGGER.debug("[DifficultyScaler] Boss damage: {} -> {} (players={}, scale={:.2f})",
                baseDamage, scaledDamage, clampedPlayers, damageScale);

        return scaledDamage;
    }

    /**
     * Scale boss damage with default quest type.
     */
    public float scaleBossDamage(float baseDamage, int playerCount) {
        return scaleBossDamage(baseDamage, playerCount, QuestType.PVE_COOP);
    }

    // === Regular Mob HP Scaling ===

    /**
     * Scale regular mob HP based on player count.
     * Uses gentler scaling than bosses (15% per player instead of 30%).
     *
     * @param baseHP Base mob health
     * @param playerCount Number of players
     * @param questType Quest type for multiplier
     * @return Scaled mob HP
     */
    public float scaleMobHealth(float baseHP, int playerCount, QuestType questType) {
        if (baseHP <= 0 || playerCount <= 0) {
            return baseHP;
        }

        int clampedPlayers = Math.max(1, Math.min(playerCount, questType.getMaxPlayers()));

        // Gentler scaling for regular mobs
        float playerScale = 1.0f + (clampedPlayers - 1) * MOB_HP_SCALE_PER_PLAYER;
        float totalScale = playerScale * questType.getDifficultyMultiplier();

        // Cap at 3x HP for regular mobs
        totalScale = Math.min(totalScale, 3.0f);

        return baseHP * totalScale;
    }

    // === Wave-based Scaling ===

    /**
     * Get wave difficulty multiplier based on wave number.
     * Higher waves = harder difficulty.
     *
     * @param waveNumber Current wave (1-based)
     * @param totalWaves Total waves in the quest (0 for endless)
     * @return Wave difficulty multiplier (1.0 = baseline)
     */
    public float getWaveMultiplier(int waveNumber, int totalWaves) {
        if (waveNumber <= 2) {
            return 1.0f;
        }

        // Progressive scaling: each wave adds ~5% difficulty
        float waveScale = 1.0f + (waveNumber - 2) * 0.05f;

        // In endless mode, scale more aggressively after wave 10
        if (totalWaves == 0 && waveNumber > 10) {
            waveScale += (waveNumber - 10) * 0.03f;
        }

        // Cap at 3x from waves alone
        return Math.min(waveScale, 3.0f);
    }

    // === Combined Scaling ===

    /**
     * Get total difficulty scale factor combining all factors.
     *
     * @param playerCount Number of players
     * @param questType Quest type
     * @param waveNumber Current wave
     * @param totalWaves Total waves (0 for endless)
     * @return Combined scale factor
     */
    public float getTotalScaleFactor(int playerCount, QuestType questType, int waveNumber, int totalWaves) {
        float playerScale = (float) Math.sqrt(Math.max(1, playerCount));
        float questScale = questType.getDifficultyMultiplier();
        float waveScale = getWaveMultiplier(waveNumber, totalWaves);

        return playerScale * questScale * waveScale;
    }

    // === Utility Methods ===

    /**
     * Calculate expected DPS needed to clear a wave.
     * Useful for balancing spawn rates.
     *
     * @param totalMobHP Total mob HP in the wave
     * @param waveDurationSeconds Expected wave duration
     * @param playerCount Number of players
     * @return Required team DPS
     */
    public float calculateRequiredTeamDPS(float totalMobHP, float waveDurationSeconds, int playerCount) {
        if (waveDurationSeconds <= 0 || playerCount <= 0) {
            return 0;
        }
        float teamDPS = totalMobHP / waveDurationSeconds;
        float perPlayerDPS = teamDPS / playerCount;

        LOGGER.debug("[DifficultyScaler] Required DPS: {:.1f} team ({:.1f} per player) for {} HP in {}s",
                teamDPS, perPlayerDPS, totalMobHP, waveDurationSeconds);

        return teamDPS;
    }

    /**
     * Get recommended arena size based on player count.
     *
     * @param questType Quest type (has base arena size)
     * @param playerCount Number of players
     * @return Recommended arena radius
     */
    public int getRecommendedArenaSize(QuestType questType, int playerCount) {
        // Base size from quest type, add ~10% per additional player
        int baseSize = questType.getDefaultArenaSize();
        int extraPerPlayer = baseSize / 10;
        int playerBonus = Math.max(0, playerCount - 1) * extraPerPlayer;

        return Math.min(baseSize + playerBonus, 150); // Cap at 150 blocks
    }

    @Override
    public String toString() {
        return "DifficultyScaler{" +
                "bossHPScalePerPlayer=" + BOSS_HP_SCALE_PER_PLAYER +
                ", bossDMGScalePerPlayer=" + BOSS_DMG_SCALE_PER_PLAYER +
                ", mobHPScalePerPlayer=" + MOB_HP_SCALE_PER_PLAYER +
                '}';
    }
}
