package com.devmod.endurance.config;

import net.minecraft.resources.ResourceLocation;

import com.devmod.endurance.EnduranceQuestRegistry;
import com.devmod.endurance.EnduranceQuestRegistry.MobDifficultyPreset;
import com.devmod.endurance.EnduranceQuestRegistry.MobQuestConfig;
import com.devmod.endurance.EnduranceQuestRegistry.MobTier;

/**
 * Per-mob configuration for Endurance mode.
 * Can be used as session override or global configuration.
 *
 * This record captures all the tunable parameters for a mob type:
 * - enabled: Whether this mob can spawn in waves
 * - Base stats: health, damage, speed, armor
 * - Wave config: count per wave, scaling, max cap
 * - Elite chance: probability of spawning as elite variant
 */
public record EnduranceMobConfig(
    ResourceLocation mobId,
    boolean enabled,
    float baseHealth,
    float baseDamage,
    float baseSpeed,
    float baseArmor,
    int baseCountPerWave,
    float countScalingPerWave,
    int maxPerWave,
    float eliteChance,
    MobTier tier,
    MobDifficultyPreset difficultyPreset
) {
    /**
     * Default constructor with all parameters.
     */
    public EnduranceMobConfig {
        if (mobId == null) {
            throw new IllegalArgumentException("mobId cannot be null");
        }
        // Clamp values to sensible ranges
        baseHealth = Math.max(1f, baseHealth);
        baseDamage = Math.max(0f, baseDamage);
        baseSpeed = Math.max(0.01f, Math.min(2f, baseSpeed));
        baseArmor = Math.max(0f, Math.min(30f, baseArmor));
        baseCountPerWave = Math.max(1, baseCountPerWave);
        countScalingPerWave = Math.max(0f, countScalingPerWave);
        maxPerWave = Math.max(1, maxPerWave);
        eliteChance = Math.max(0f, Math.min(1f, eliteChance));
    }

    /**
     * Create from existing MobQuestConfig (from EnduranceQuestRegistry).
     * This is the primary way to get default values for a mob type.
     */
    public static EnduranceMobConfig fromMobQuestConfig(MobQuestConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("config cannot be null");
        }
        return new EnduranceMobConfig(
            config.mobId,
            true, // enabled by default
            config.baseHealth,
            config.baseDamage,
            config.baseSpeed,
            0f, // baseArmor - not in MobQuestConfig, default to 0
            config.baseCountPerWave,
            config.countScalingPerWave,
            config.maxPerWave,
            config.eliteChance,
            config.tier,
            config.difficultyPreset
        );
    }

    /**
     * Create a default config for a mob ID when no MobQuestConfig exists.
     * Uses MEDIUM tier defaults.
     */
    public static EnduranceMobConfig defaultFor(ResourceLocation mobId) {
        return new EnduranceMobConfig(
            mobId,
            true,
            20f,   // baseHealth
            3f,    // baseDamage
            0.23f, // baseSpeed
            0f,    // baseArmor
            4,     // baseCountPerWave
            1.2f,  // countScalingPerWave
            15,    // maxPerWave
            0.15f, // eliteChance
            MobTier.MEDIUM,
            MobDifficultyPreset.STANDARD
        );
    }

    /**
     * Create from registry, falling back to defaults if not found.
     */
    public static EnduranceMobConfig fromRegistryOrDefault(ResourceLocation mobId) {
        return EnduranceQuestRegistry.INSTANCE.getMobConfig(mobId)
            .map(EnduranceMobConfig::fromMobQuestConfig)
            .orElseGet(() -> defaultFor(mobId));
    }

    /**
     * Create a modified copy with enabled flag changed.
     */
    public EnduranceMobConfig withEnabled(boolean newEnabled) {
        return new EnduranceMobConfig(
            mobId, newEnabled, baseHealth, baseDamage, baseSpeed, baseArmor,
            baseCountPerWave, countScalingPerWave, maxPerWave, eliteChance,
            tier, difficultyPreset
        );
    }

    /**
     * Create a modified copy with stats changed.
     */
    public EnduranceMobConfig withStats(float health, float damage, float speed, float armor) {
        return new EnduranceMobConfig(
            mobId, enabled, health, damage, speed, armor,
            baseCountPerWave, countScalingPerWave, maxPerWave, eliteChance,
            tier, difficultyPreset
        );
    }

    /**
     * Create a modified copy with wave config changed.
     */
    public EnduranceMobConfig withWaveConfig(int countPerWave, float scaling, int max) {
        return new EnduranceMobConfig(
            mobId, enabled, baseHealth, baseDamage, baseSpeed, baseArmor,
            countPerWave, scaling, max, eliteChance,
            tier, difficultyPreset
        );
    }

    /**
     * Create a modified copy with elite chance changed.
     */
    public EnduranceMobConfig withEliteChance(float newEliteChance) {
        return new EnduranceMobConfig(
            mobId, enabled, baseHealth, baseDamage, baseSpeed, baseArmor,
            baseCountPerWave, countScalingPerWave, maxPerWave, newEliteChance,
            tier, difficultyPreset
        );
    }

    /**
     * Create a modified copy with difficulty preset applied.
     * Applies preset multipliers to stats.
     */
    public EnduranceMobConfig withPreset(MobDifficultyPreset preset) {
        float adjustedHealth = baseHealth * preset.hpMultiplier;
        float adjustedDamage = baseDamage * preset.damageMultiplier;
        int adjustedCount = (int) Math.ceil(baseCountPerWave * preset.countMultiplier);

        return new EnduranceMobConfig(
            mobId, enabled, adjustedHealth, adjustedDamage, baseSpeed, baseArmor,
            adjustedCount, countScalingPerWave, maxPerWave, eliteChance,
            tier, preset
        );
    }

    /**
     * Get display name from mob ID.
     */
    public String getDisplayName() {
        String path = mobId.getPath();
        String[] parts = path.split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (!part.isEmpty()) {
                if (sb.length() > 0) sb.append(" ");
                sb.append(Character.toUpperCase(part.charAt(0)));
                sb.append(part.substring(1));
            }
        }
        return sb.toString();
    }

    /**
     * Get namespace (mod ID) from mob ID.
     */
    public String getNamespace() {
        return mobId.getNamespace();
    }

    /**
     * Check if this is a vanilla Minecraft mob.
     */
    public boolean isVanilla() {
        return "minecraft".equals(mobId.getNamespace());
    }

    /**
     * Get a summary string for debugging/logging.
     */
    public String getSummary() {
        return String.format("%s [%s] HP:%.0f DMG:%.1f SPD:%.2f %s%s",
            mobId, tier.name(),
            baseHealth, baseDamage, baseSpeed,
            difficultyPreset.displayName,
            enabled ? "" : " (DISABLED)");
    }
}
