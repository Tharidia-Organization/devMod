package com.devmod.endurance.config;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.resources.ResourceLocation;

import com.devmod.endurance.EnduranceQuestRegistry;
import com.devmod.endurance.SpawnAffix;

/**
 * Aggregates all mob configurations for an Endurance session.
 * Contains:
 * - Per-mob configs (with enable/disable and stat overrides)
 * - Global multipliers (applied to all mobs)
 * - Affix weight overrides (change probability of spawn affixes)
 *
 * This class is mutable and can be updated during session setup.
 * Thread-safety is not guaranteed - meant for single-threaded access.
 */
public class EnduranceMobPoolConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger(EnduranceMobPoolConfig.class);

    // Per-mob configurations
    private final Map<ResourceLocation, EnduranceMobConfig> mobConfigs = new HashMap<>();

    // Set of explicitly enabled mobs (if empty, all mobs are enabled by default)
    private final Set<ResourceLocation> enabledMobs = new HashSet<>();

    // Set of explicitly disabled mobs
    private final Set<ResourceLocation> disabledMobs = new HashSet<>();

    // Global multipliers (applied on top of per-mob stats)
    private float globalHealthMult = 1.0f;
    private float globalDamageMult = 1.0f;
    private float globalSpeedMult = 1.0f;
    private float globalEliteChanceMult = 1.0f;

    // Affix weight overrides (default is 1.0 for all)
    private final Map<SpawnAffix, Float> affixWeights = new EnumMap<>(SpawnAffix.class);

    /**
     * Create an empty pool config with default settings.
     */
    public EnduranceMobPoolConfig() {
        // Initialize affix weights to default (1.0)
        for (SpawnAffix affix : SpawnAffix.values()) {
            affixWeights.put(affix, 1.0f);
        }
    }

    /**
     * Create a pool config from a collection of mob configs.
     */
    public EnduranceMobPoolConfig(Collection<EnduranceMobConfig> configs) {
        this();
        for (EnduranceMobConfig config : configs) {
            mobConfigs.put(config.mobId(), config);
            if (config.enabled()) {
                enabledMobs.add(config.mobId());
            } else {
                disabledMobs.add(config.mobId());
            }
        }
    }

    // ========== Mob Config Management ==========

    /**
     * Get config for a specific mob.
     * Returns the override if set, otherwise creates from registry defaults.
     */
    public EnduranceMobConfig getMobConfig(ResourceLocation mobId) {
        EnduranceMobConfig config = mobConfigs.get(mobId);
        if (config != null) {
            return config;
        }
        // Create from registry or use defaults
        return EnduranceMobConfig.fromRegistryOrDefault(mobId);
    }

    /**
     * Get config for a specific mob, returning null if no override exists.
     */
    @Nullable
    public EnduranceMobConfig getMobConfigOrNull(ResourceLocation mobId) {
        return mobConfigs.get(mobId);
    }

    /**
     * Set config for a specific mob.
     */
    public void setMobConfig(EnduranceMobConfig config) {
        mobConfigs.put(config.mobId(), config);
        if (config.enabled()) {
            enabledMobs.add(config.mobId());
            disabledMobs.remove(config.mobId());
        } else {
            disabledMobs.add(config.mobId());
            enabledMobs.remove(config.mobId());
        }
    }

    /**
     * Remove config override for a mob (reverts to registry defaults).
     */
    public void removeMobConfig(ResourceLocation mobId) {
        mobConfigs.remove(mobId);
        enabledMobs.remove(mobId);
        disabledMobs.remove(mobId);
    }

    /**
     * Get all mob configs that have been explicitly set.
     */
    public Collection<EnduranceMobConfig> getAllMobConfigs() {
        return Collections.unmodifiableCollection(mobConfigs.values());
    }

    /**
     * Get the count of explicitly configured mobs.
     */
    public int getConfiguredMobCount() {
        return mobConfigs.size();
    }

    // ========== Enable/Disable ==========

    /**
     * Check if a mob is enabled for spawning.
     * Priority: explicit disable > explicit enable > default (enabled)
     */
    public boolean isEnabled(ResourceLocation mobId) {
        if (disabledMobs.contains(mobId)) {
            return false;
        }
        if (enabledMobs.contains(mobId)) {
            return true;
        }
        // Check per-mob config
        EnduranceMobConfig config = mobConfigs.get(mobId);
        if (config != null) {
            return config.enabled();
        }
        // Default: enabled
        return true;
    }

    /**
     * Enable a specific mob.
     */
    public void enableMob(ResourceLocation mobId) {
        disabledMobs.remove(mobId);
        enabledMobs.add(mobId);

        // Update config if exists
        EnduranceMobConfig config = mobConfigs.get(mobId);
        if (config != null && !config.enabled()) {
            mobConfigs.put(mobId, config.withEnabled(true));
        }
    }

    /**
     * Disable a specific mob.
     */
    public void disableMob(ResourceLocation mobId) {
        enabledMobs.remove(mobId);
        disabledMobs.add(mobId);

        // Update config if exists
        EnduranceMobConfig config = mobConfigs.get(mobId);
        if (config != null && config.enabled()) {
            mobConfigs.put(mobId, config.withEnabled(false));
        }
    }

    /**
     * Get all explicitly enabled mobs.
     */
    public Set<ResourceLocation> getEnabledMobs() {
        return Collections.unmodifiableSet(enabledMobs);
    }

    /**
     * Get all explicitly disabled mobs.
     */
    public Set<ResourceLocation> getDisabledMobs() {
        return Collections.unmodifiableSet(disabledMobs);
    }

    /**
     * Get count of enabled mobs (including defaults).
     */
    public int getEnabledCount() {
        // Count all mobs from registry minus disabled ones
        int total = EnduranceQuestRegistry.INSTANCE.getTotalMobCount();
        return total - disabledMobs.size();
    }

    /**
     * Enable all mobs.
     */
    public void enableAll() {
        disabledMobs.clear();
        // Collect IDs first to avoid ConcurrentModificationException
        var toEnable = mobConfigs.values().stream()
            .filter(c -> !c.enabled())
            .map(EnduranceMobConfig::mobId)
            .toList();
        for (var mobId : toEnable) {
            EnduranceMobConfig config = mobConfigs.get(mobId);
            if (config != null) {
                mobConfigs.put(mobId, config.withEnabled(true));
            }
        }
    }

    /**
     * Disable all mobs (careful - this will prevent any spawning!).
     */
    public void disableAll() {
        enabledMobs.clear();
        for (var config : EnduranceQuestRegistry.INSTANCE.getAllMobConfigs()) {
            disabledMobs.add(config.getMobId());
        }
    }

    // ========== Global Multipliers ==========

    public float getGlobalHealthMult() {
        return globalHealthMult;
    }

    public void setGlobalHealthMult(float mult) {
        this.globalHealthMult = Math.max(0.1f, Math.min(10f, mult));
    }

    public float getGlobalDamageMult() {
        return globalDamageMult;
    }

    public void setGlobalDamageMult(float mult) {
        this.globalDamageMult = Math.max(0.1f, Math.min(10f, mult));
    }

    public float getGlobalSpeedMult() {
        return globalSpeedMult;
    }

    public void setGlobalSpeedMult(float mult) {
        this.globalSpeedMult = Math.max(0.1f, Math.min(5f, mult));
    }

    public float getGlobalEliteChanceMult() {
        return globalEliteChanceMult;
    }

    public void setGlobalEliteChanceMult(float mult) {
        this.globalEliteChanceMult = Math.max(0f, Math.min(5f, mult));
    }

    /**
     * Set all global multipliers at once.
     */
    public void setGlobalMultipliers(float health, float damage, float speed, float eliteChance) {
        setGlobalHealthMult(health);
        setGlobalDamageMult(damage);
        setGlobalSpeedMult(speed);
        setGlobalEliteChanceMult(eliteChance);
    }

    /**
     * Reset all global multipliers to 1.0.
     */
    public void resetGlobalMultipliers() {
        globalHealthMult = 1.0f;
        globalDamageMult = 1.0f;
        globalSpeedMult = 1.0f;
        globalEliteChanceMult = 1.0f;
    }

    // ========== Affix Weights ==========

    /**
     * Get weight for a spawn affix (default 1.0).
     */
    public float getAffixWeight(SpawnAffix affix) {
        return affixWeights.getOrDefault(affix, 1.0f);
    }

    /**
     * Set weight for a spawn affix.
     */
    public void setAffixWeight(SpawnAffix affix, float weight) {
        affixWeights.put(affix, Math.max(0f, Math.min(5f, weight)));
    }

    /**
     * Get all affix weights.
     */
    public Map<SpawnAffix, Float> getAffixWeights() {
        return Collections.unmodifiableMap(affixWeights);
    }

    /**
     * Reset all affix weights to 1.0.
     */
    public void resetAffixWeights() {
        for (SpawnAffix affix : SpawnAffix.values()) {
            affixWeights.put(affix, 1.0f);
        }
    }

    // ========== Effective Values ==========

    /**
     * Get effective health for a mob (base * global multiplier).
     */
    public float getEffectiveHealth(ResourceLocation mobId) {
        return getMobConfig(mobId).baseHealth() * globalHealthMult;
    }

    /**
     * Get effective damage for a mob (base * global multiplier).
     */
    public float getEffectiveDamage(ResourceLocation mobId) {
        return getMobConfig(mobId).baseDamage() * globalDamageMult;
    }

    /**
     * Get effective speed for a mob (base * global multiplier).
     */
    public float getEffectiveSpeed(ResourceLocation mobId) {
        return getMobConfig(mobId).baseSpeed() * globalSpeedMult;
    }

    /**
     * Get effective elite chance for a mob (base * global multiplier).
     */
    public float getEffectiveEliteChance(ResourceLocation mobId) {
        return Math.min(1f, getMobConfig(mobId).eliteChance() * globalEliteChanceMult);
    }

    // ========== Utility ==========

    /**
     * Check if this config has any modifications from defaults.
     */
    public boolean hasModifications() {
        return !mobConfigs.isEmpty() ||
               !disabledMobs.isEmpty() ||
               globalHealthMult != 1.0f ||
               globalDamageMult != 1.0f ||
               globalSpeedMult != 1.0f ||
               globalEliteChanceMult != 1.0f ||
               affixWeights.values().stream().anyMatch(w -> w != 1.0f);
    }

    /**
     * Clear all configurations (revert to defaults).
     */
    public void clear() {
        mobConfigs.clear();
        enabledMobs.clear();
        disabledMobs.clear();
        resetGlobalMultipliers();
        resetAffixWeights();
    }

    /**
     * Create a deep copy of this config.
     */
    public EnduranceMobPoolConfig copy() {
        EnduranceMobPoolConfig copy = new EnduranceMobPoolConfig();
        copy.mobConfigs.putAll(this.mobConfigs);
        copy.enabledMobs.addAll(this.enabledMobs);
        copy.disabledMobs.addAll(this.disabledMobs);
        copy.globalHealthMult = this.globalHealthMult;
        copy.globalDamageMult = this.globalDamageMult;
        copy.globalSpeedMult = this.globalSpeedMult;
        copy.globalEliteChanceMult = this.globalEliteChanceMult;
        copy.affixWeights.putAll(this.affixWeights);
        return copy;
    }

    /**
     * Log summary of this config for debugging.
     */
    public void logSummary() {
        LOGGER.info("[MobPoolConfig] Summary:");
        LOGGER.info("  - Configured mobs: {}", mobConfigs.size());
        LOGGER.info("  - Disabled mobs: {}", disabledMobs.size());
        LOGGER.info("  - Global multipliers: HP={}, DMG={}, SPD={}, Elite={}",
            globalHealthMult, globalDamageMult, globalSpeedMult, globalEliteChanceMult);

        long modifiedAffixes = affixWeights.values().stream().filter(w -> w != 1.0f).count();
        if (modifiedAffixes > 0) {
            LOGGER.info("  - Modified affix weights: {}", modifiedAffixes);
        }
    }
}
