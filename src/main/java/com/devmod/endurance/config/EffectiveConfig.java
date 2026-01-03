package com.devmod.endurance.config;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.devmod.config.GameMechanicsConfig;
import com.devmod.endurance.EnduranceQuestManager;

/**
 * Utility class for reading effective config values that respect session overrides.
 *
 * Priority order:
 * 1. Session override (if session provided and key has override)
 * 2. Global GameMechanicsConfig value
 *
 * This ensures that session-specific settings (set by quest host) take effect
 * during the quest, without affecting the global config for other players.
 */
public final class EffectiveConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger(EffectiveConfig.class);

    private EffectiveConfig() {}

    // =========================================================================
    // WAVE CONFIG
    // =========================================================================

    /**
     * Get effective base mob count per wave.
     */
    public static int getBaseMobCount(@Nullable EnduranceQuestManager.ActiveQuestSession session) {
        String override = getOverride(session, "baseMobCount");
        if (override != null) {
            try {
                return Integer.parseInt(override);
            } catch (NumberFormatException e) {
                LOGGER.warn("[EffectiveConfig] Invalid baseMobCount override: {}", override);
            }
        }
        return GameMechanicsConfig.WAVE_BASE_MOB_COUNT.get();
    }

    /**
     * Get effective mob scaling per wave.
     */
    public static double getMobScaling(@Nullable EnduranceQuestManager.ActiveQuestSession session) {
        String override = getOverride(session, "mobScaling");
        if (override != null) {
            try {
                return Double.parseDouble(override);
            } catch (NumberFormatException e) {
                LOGGER.warn("[EffectiveConfig] Invalid mobScaling override: {}", override);
            }
        }
        return GameMechanicsConfig.WAVE_MOB_SCALING.get();
    }

    /**
     * Get effective elite chance base.
     */
    public static double getEliteChanceBase(@Nullable EnduranceQuestManager.ActiveQuestSession session) {
        String override = getOverride(session, "eliteChanceBase");
        if (override != null) {
            try {
                return Double.parseDouble(override);
            } catch (NumberFormatException e) {
                LOGGER.warn("[EffectiveConfig] Invalid eliteChanceBase override: {}", override);
            }
        }
        return GameMechanicsConfig.WAVE_ELITE_CHANCE_BASE.get();
    }

    /**
     * Get effective elite chance scaling per wave.
     */
    public static double getEliteChanceScaling(@Nullable EnduranceQuestManager.ActiveQuestSession session) {
        String override = getOverride(session, "eliteChanceScaling");
        if (override != null) {
            try {
                return Double.parseDouble(override);
            } catch (NumberFormatException e) {
                LOGGER.warn("[EffectiveConfig] Invalid eliteChanceScaling override: {}", override);
            }
        }
        return GameMechanicsConfig.WAVE_ELITE_CHANCE_SCALING.get();
    }

    // =========================================================================
    // DIFFICULTY SCALING (session-only, no global config equivalent)
    // =========================================================================

    /**
     * Get effective health scaling per player.
     * Default: 0.15 (15% more HP per additional player)
     */
    public static double getHealthScalingPerPlayer(@Nullable EnduranceQuestManager.ActiveQuestSession session) {
        String override = getOverride(session, "healthScalingPerPlayer");
        if (override != null) {
            try {
                return Double.parseDouble(override);
            } catch (NumberFormatException e) {
                LOGGER.warn("[EffectiveConfig] Invalid healthScalingPerPlayer override: {}", override);
            }
        }
        return 0.15; // Default: 15% HP scaling per player
    }

    /**
     * Get effective mob count scaling per player.
     * Default: 0.25 (25% more mobs per additional player)
     */
    public static double getMobCountScalingPerPlayer(@Nullable EnduranceQuestManager.ActiveQuestSession session) {
        String override = getOverride(session, "mobCountScalingPerPlayer");
        if (override != null) {
            try {
                return Double.parseDouble(override);
            } catch (NumberFormatException e) {
                LOGGER.warn("[EffectiveConfig] Invalid mobCountScalingPerPlayer override: {}", override);
            }
        }
        return 0.25; // Default: 25% mob count scaling per player
    }

    /**
     * Get effective max health multiplier cap.
     * Default: 3.0 (max 3x HP in multiplayer)
     */
    public static double getMaxHealthMultiplier(@Nullable EnduranceQuestManager.ActiveQuestSession session) {
        String override = getOverride(session, "maxHealthMultiplier");
        if (override != null) {
            try {
                return Double.parseDouble(override);
            } catch (NumberFormatException e) {
                LOGGER.warn("[EffectiveConfig] Invalid maxHealthMultiplier override: {}", override);
            }
        }
        return 3.0; // Default: max 3x HP multiplier
    }

    // =========================================================================
    // BOSS CONFIG (session-only, no global config equivalent)
    // =========================================================================

    /**
     * Get effective boss health multiplier.
     * Default: 2.0 (bosses have 2x base HP)
     */
    public static double getBossHealthMultiplier(@Nullable EnduranceQuestManager.ActiveQuestSession session) {
        String override = getOverride(session, "bossHealthMultiplier");
        if (override != null) {
            try {
                return Double.parseDouble(override);
            } catch (NumberFormatException e) {
                LOGGER.warn("[EffectiveConfig] Invalid bossHealthMultiplier override: {}", override);
            }
        }
        return 2.0; // Default: 2x boss HP
    }

    /**
     * Get effective boss damage multiplier.
     * Default: 1.5 (bosses deal 50% more damage)
     */
    public static double getBossDamageMultiplier(@Nullable EnduranceQuestManager.ActiveQuestSession session) {
        String override = getOverride(session, "bossDamageMultiplier");
        if (override != null) {
            try {
                return Double.parseDouble(override);
            } catch (NumberFormatException e) {
                LOGGER.warn("[EffectiveConfig] Invalid bossDamageMultiplier override: {}", override);
            }
        }
        return 1.5; // Default: 1.5x boss damage
    }

    // =========================================================================
    // PERK/SHOP CONFIG (uses correct field names from GameMechanicsConfig)
    // =========================================================================

    /**
     * Get effective base reroll cost.
     */
    public static int getBaseRerollCost(@Nullable EnduranceQuestManager.ActiveQuestSession session) {
        String override = getOverride(session, "baseRerollCost");
        if (override != null) {
            try {
                return Integer.parseInt(override);
            } catch (NumberFormatException e) {
                LOGGER.warn("[EffectiveConfig] Invalid baseRerollCost override: {}", override);
            }
        }
        return GameMechanicsConfig.PERK_REROLL_COST_BASE.get();
    }

    /**
     * Get effective reroll cost increase per use.
     */
    public static int getRerollCostIncrease(@Nullable EnduranceQuestManager.ActiveQuestSession session) {
        String override = getOverride(session, "rerollCostIncrease");
        if (override != null) {
            try {
                return Integer.parseInt(override);
            } catch (NumberFormatException e) {
                LOGGER.warn("[EffectiveConfig] Invalid rerollCostIncrease override: {}", override);
            }
        }
        return GameMechanicsConfig.PERK_REROLL_COST_INCREMENT.get();
    }

    /**
     * Get effective perk choices count.
     */
    public static int getPerkChoicesCount(@Nullable EnduranceQuestManager.ActiveQuestSession session) {
        String override = getOverride(session, "perkChoicesCount");
        if (override != null) {
            try {
                return Integer.parseInt(override);
            } catch (NumberFormatException e) {
                LOGGER.warn("[EffectiveConfig] Invalid perkChoicesCount override: {}", override);
            }
        }
        return GameMechanicsConfig.PERK_CHOICES_PER_SELECTION.get();
    }

    /**
     * Get effective rare perk weight.
     */
    public static int getRarePerkWeight(@Nullable EnduranceQuestManager.ActiveQuestSession session) {
        String override = getOverride(session, "rarePerkWeight");
        if (override != null) {
            try {
                return Integer.parseInt(override);
            } catch (NumberFormatException e) {
                LOGGER.warn("[EffectiveConfig] Invalid rarePerkWeight override: {}", override);
            }
        }
        return GameMechanicsConfig.PERK_RARE_WEIGHT.get();
    }

    /**
     * Get effective epic perk weight.
     */
    public static int getEpicPerkWeight(@Nullable EnduranceQuestManager.ActiveQuestSession session) {
        String override = getOverride(session, "epicPerkWeight");
        if (override != null) {
            try {
                return Integer.parseInt(override);
            } catch (NumberFormatException e) {
                LOGGER.warn("[EffectiveConfig] Invalid epicPerkWeight override: {}", override);
            }
        }
        return GameMechanicsConfig.PERK_EPIC_WEIGHT.get();
    }

    // =========================================================================
    // GENERIC GETTERS
    // =========================================================================

    /**
     * Get an integer config value with session override support.
     * @param session Optional session for override lookup
     * @param key The config key (simple name, e.g., "baseMobCount")
     * @param defaultValue Fallback if no override and config not found
     */
    public static int getInt(@Nullable EnduranceQuestManager.ActiveQuestSession session, String key, int defaultValue) {
        String override = getOverride(session, key);
        if (override != null) {
            try {
                return Integer.parseInt(override);
            } catch (NumberFormatException e) {
                LOGGER.warn("[EffectiveConfig] Invalid int override for {}: {}", key, override);
            }
        }
        return defaultValue;
    }

    /**
     * Get a double config value with session override support.
     */
    public static double getDouble(@Nullable EnduranceQuestManager.ActiveQuestSession session, String key, double defaultValue) {
        String override = getOverride(session, key);
        if (override != null) {
            try {
                return Double.parseDouble(override);
            } catch (NumberFormatException e) {
                LOGGER.warn("[EffectiveConfig] Invalid double override for {}: {}", key, override);
            }
        }
        return defaultValue;
    }

    /**
     * Get a boolean config value with session override support.
     */
    public static boolean getBoolean(@Nullable EnduranceQuestManager.ActiveQuestSession session, String key, boolean defaultValue) {
        String override = getOverride(session, key);
        if (override != null) {
            return Boolean.parseBoolean(override);
        }
        return defaultValue;
    }

    // =========================================================================
    // MOB CONFIG (per-mob configuration with session override support)
    // =========================================================================

    /**
     * Get effective mob config for a specific mob type.
     * Priority: session mobPoolConfig > registry default
     *
     * @param session Optional session for override lookup
     * @param mobId The mob's ResourceLocation
     * @return Effective mob config (never null)
     */
    public static EnduranceMobConfig getEffectiveMobConfig(
            @Nullable EnduranceQuestManager.ActiveQuestSession session,
            net.minecraft.resources.ResourceLocation mobId) {
        if (session != null) {
            EnduranceMobPoolConfig poolConfig = session.getMobPoolConfig();
            if (poolConfig != null) {
                EnduranceMobConfig override = poolConfig.getMobConfigOrNull(mobId);
                if (override != null) {
                    LOGGER.debug("[EffectiveConfig] Using session mob config for {}", mobId);
                    return override;
                }
            }
        }
        // Fallback to registry defaults
        return EnduranceMobConfig.fromRegistryOrDefault(mobId);
    }

    /**
     * Check if a mob type is enabled for spawning.
     * Priority: session mobPoolConfig > default (enabled)
     */
    public static boolean isMobEnabled(
            @Nullable EnduranceQuestManager.ActiveQuestSession session,
            net.minecraft.resources.ResourceLocation mobId) {
        if (session != null) {
            EnduranceMobPoolConfig poolConfig = session.getMobPoolConfig();
            if (poolConfig != null) {
                return poolConfig.isEnabled(mobId);
            }
        }
        return true; // Default: all mobs enabled
    }

    /**
     * Get effective global health multiplier from session.
     * Default: 1.0 (no multiplier)
     */
    public static float getGlobalHealthMult(@Nullable EnduranceQuestManager.ActiveQuestSession session) {
        if (session != null) {
            EnduranceMobPoolConfig poolConfig = session.getMobPoolConfig();
            if (poolConfig != null) {
                return poolConfig.getGlobalHealthMult();
            }
        }
        return 1.0f;
    }

    /**
     * Get effective global damage multiplier from session.
     * Default: 1.0 (no multiplier)
     */
    public static float getGlobalDamageMult(@Nullable EnduranceQuestManager.ActiveQuestSession session) {
        if (session != null) {
            EnduranceMobPoolConfig poolConfig = session.getMobPoolConfig();
            if (poolConfig != null) {
                return poolConfig.getGlobalDamageMult();
            }
        }
        return 1.0f;
    }

    /**
     * Get effective global speed multiplier from session.
     * Default: 1.0 (no multiplier)
     */
    public static float getGlobalSpeedMult(@Nullable EnduranceQuestManager.ActiveQuestSession session) {
        if (session != null) {
            EnduranceMobPoolConfig poolConfig = session.getMobPoolConfig();
            if (poolConfig != null) {
                return poolConfig.getGlobalSpeedMult();
            }
        }
        return 1.0f;
    }

    /**
     * Get effective global elite chance multiplier from session.
     * Default: 1.0 (no multiplier)
     */
    public static float getGlobalEliteChanceMult(@Nullable EnduranceQuestManager.ActiveQuestSession session) {
        if (session != null) {
            EnduranceMobPoolConfig poolConfig = session.getMobPoolConfig();
            if (poolConfig != null) {
                return poolConfig.getGlobalEliteChanceMult();
            }
        }
        return 1.0f;
    }

    /**
     * Get affix weight for a specific spawn affix.
     * Default: 1.0 (standard weight)
     */
    public static float getAffixWeight(
            @Nullable EnduranceQuestManager.ActiveQuestSession session,
            com.devmod.endurance.SpawnAffix affix) {
        if (session != null) {
            EnduranceMobPoolConfig poolConfig = session.getMobPoolConfig();
            if (poolConfig != null) {
                return poolConfig.getAffixWeight(affix);
            }
        }
        return 1.0f;
    }

    /**
     * Get effective base health for a mob (with global multiplier applied).
     */
    public static float getEffectiveMobHealth(
            @Nullable EnduranceQuestManager.ActiveQuestSession session,
            net.minecraft.resources.ResourceLocation mobId) {
        EnduranceMobConfig config = getEffectiveMobConfig(session, mobId);
        return config.baseHealth() * getGlobalHealthMult(session);
    }

    /**
     * Get effective base damage for a mob (with global multiplier applied).
     */
    public static float getEffectiveMobDamage(
            @Nullable EnduranceQuestManager.ActiveQuestSession session,
            net.minecraft.resources.ResourceLocation mobId) {
        EnduranceMobConfig config = getEffectiveMobConfig(session, mobId);
        return config.baseDamage() * getGlobalDamageMult(session);
    }

    /**
     * Get effective base speed for a mob (with global multiplier applied).
     */
    public static float getEffectiveMobSpeed(
            @Nullable EnduranceQuestManager.ActiveQuestSession session,
            net.minecraft.resources.ResourceLocation mobId) {
        EnduranceMobConfig config = getEffectiveMobConfig(session, mobId);
        return config.baseSpeed() * getGlobalSpeedMult(session);
    }

    /**
     * Get effective elite chance for a mob (with global multiplier applied).
     */
    public static float getEffectiveMobEliteChance(
            @Nullable EnduranceQuestManager.ActiveQuestSession session,
            net.minecraft.resources.ResourceLocation mobId) {
        EnduranceMobConfig config = getEffectiveMobConfig(session, mobId);
        return Math.min(1.0f, config.eliteChance() * getGlobalEliteChanceMult(session));
    }

    /**
     * Check if session has any mob pool configuration.
     */
    public static boolean hasMobPoolConfig(@Nullable EnduranceQuestManager.ActiveQuestSession session) {
        return session != null && session.hasMobPoolConfig();
    }

    // =========================================================================
    // INTERNAL HELPERS
    // =========================================================================

    /**
     * Get override value from session if available.
     */
    @Nullable
    private static String getOverride(@Nullable EnduranceQuestManager.ActiveQuestSession session, String key) {
        if (session == null || key == null) {
            return null;
        }
        String value = session.getConfigOverride(key);
        if (value != null) {
            LOGGER.debug("[EffectiveConfig] Using session override for {}: {}", key, value);
        }
        return value;
    }

    /**
     * Check if a session has any config overrides.
     */
    public static boolean hasOverrides(@Nullable EnduranceQuestManager.ActiveQuestSession session) {
        return session != null && !session.getConfigOverrides().isEmpty();
    }

    /**
     * Log all active overrides for debugging.
     */
    public static void logOverrides(@Nullable EnduranceQuestManager.ActiveQuestSession session) {
        if (session == null || session.getConfigOverrides().isEmpty()) {
            LOGGER.debug("[EffectiveConfig] No session overrides active");
            return;
        }
        LOGGER.info("[EffectiveConfig] Active session overrides:");
        for (var entry : session.getConfigOverrides().entrySet()) {
            LOGGER.info("  {} = {}", entry.getKey(), entry.getValue());
        }
    }
}
