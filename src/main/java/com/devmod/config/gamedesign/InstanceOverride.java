package com.devmod.config.gamedesign;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import javax.annotation.Nullable;

public class InstanceOverride {

    private final String instanceName;
    private final Map<String, Object> overrides = new HashMap<>();

    public InstanceOverride(String instanceName) {
        this.instanceName = instanceName;
    }

    public String getInstanceName() {
        return instanceName;
    }

    // ========== Resonance Overrides ==========

    public InstanceOverride setResonanceEnabled(boolean enabled) {
        overrides.put("resonance.enabled", enabled);
        return this;
    }

    public InstanceOverride setDuoWindowMs(long ms) {
        overrides.put("resonance.duoWindowMs", ms);
        return this;
    }

    public InstanceOverride setTrinityWindowMs(long ms) {
        overrides.put("resonance.trinityWindowMs", ms);
        return this;
    }

    public InstanceOverride setApocalypseWindowMs(long ms) {
        overrides.put("resonance.apocalypseWindowMs", ms);
        return this;
    }

    public InstanceOverride setDuoDamageMultiplier(float multiplier) {
        overrides.put("resonance.duoDamageMultiplier", multiplier);
        return this;
    }

    public InstanceOverride setTrinityDamageMultiplier(float multiplier) {
        overrides.put("resonance.trinityDamageMultiplier", multiplier);
        return this;
    }

    public InstanceOverride setApocalypseDamageMultiplier(float multiplier) {
        overrides.put("resonance.apocalypseDamageMultiplier", multiplier);
        return this;
    }

    public InstanceOverride setResonanceCooldownMs(long ms) {
        overrides.put("resonance.cooldownMs", ms);
        return this;
    }

    // ========== Contracts Overrides ==========

    public InstanceOverride setContractsEnabled(boolean enabled) {
        overrides.put("contracts.enabled", enabled);
        return this;
    }

    public InstanceOverride setMaxContractsPerWave(int max) {
        overrides.put("contracts.maxContractsPerWave", max);
        return this;
    }

    // ========== Nemesis Overrides ==========

    public InstanceOverride setNemesisEnabled(boolean enabled) {
        overrides.put("nemesis.enabled", enabled);
        return this;
    }

    public InstanceOverride setProjectileDeflectionChance(float chance) {
        overrides.put("nemesis.projectileDeflectionChance", chance);
        return this;
    }

    public InstanceOverride setWeaponTypeResistance(float resistance) {
        overrides.put("nemesis.weaponTypeResistance", resistance);
        return this;
    }

    // ========== Tide Overrides ==========

    public InstanceOverride setTideEnabled(boolean enabled) {
        overrides.put("tide.enabled", enabled);
        return this;
    }

    public InstanceOverride setPlayerDeathTide(int value) {
        overrides.put("tide.playerDeath", value);
        return this;
    }

    public InstanceOverride setSSSWaveTide(int value) {
        overrides.put("tide.sssWave", value);
        return this;
    }

    // ========== Generic Override ==========

    public InstanceOverride set(String key, Object value) {
        overrides.put(key, value);
        return this;
    }

    @Nullable
    public Object get(String key) {
        return overrides.get(key);
    }

    public boolean has(String key) {
        return overrides.containsKey(key);
    }

    // ========== Apply Overrides ==========

    /**
     * Apply all overrides to a config copy and return the result.
     */
    public GameDesignConfig applyTo(GameDesignConfig config) {
        // Resonance overrides
        if (has("resonance.enabled")) {
            config.resonance.enabled = (Boolean) get("resonance.enabled");
        }
        if (has("resonance.duoWindowMs")) {
            config.resonance.duoWindowMs = ((Number) get("resonance.duoWindowMs")).longValue();
        }
        if (has("resonance.trinityWindowMs")) {
            config.resonance.trinityWindowMs = ((Number) get("resonance.trinityWindowMs")).longValue();
        }
        if (has("resonance.apocalypseWindowMs")) {
            config.resonance.apocalypseWindowMs = ((Number) get("resonance.apocalypseWindowMs")).longValue();
        }
        if (has("resonance.duoDamageMultiplier")) {
            config.resonance.duoDamageMultiplier = ((Number) get("resonance.duoDamageMultiplier")).floatValue();
        }
        if (has("resonance.trinityDamageMultiplier")) {
            config.resonance.trinityDamageMultiplier = ((Number) get("resonance.trinityDamageMultiplier")).floatValue();
        }
        if (has("resonance.apocalypseDamageMultiplier")) {
            config.resonance.apocalypseDamageMultiplier = ((Number) get("resonance.apocalypseDamageMultiplier")).floatValue();
        }
        if (has("resonance.cooldownMs")) {
            config.resonance.cooldownMs = ((Number) get("resonance.cooldownMs")).longValue();
        }

        // Contracts overrides
        if (has("contracts.enabled")) {
            config.contracts.enabled = (Boolean) get("contracts.enabled");
        }
        if (has("contracts.maxContractsPerWave")) {
            config.contracts.maxContractsPerWave = ((Number) get("contracts.maxContractsPerWave")).intValue();
        }

        // Nemesis overrides
        if (has("nemesis.enabled")) {
            config.nemesis.enabled = (Boolean) get("nemesis.enabled");
        }
        if (has("nemesis.projectileDeflectionChance")) {
            config.nemesis.projectileDeflectionChance = ((Number) get("nemesis.projectileDeflectionChance")).floatValue();
        }
        if (has("nemesis.weaponTypeResistance")) {
            config.nemesis.weaponTypeResistance = ((Number) get("nemesis.weaponTypeResistance")).floatValue();
        }

        // Tide overrides
        if (has("tide.enabled")) {
            config.tide.enabled = (Boolean) get("tide.enabled");
        }
        if (has("tide.playerDeath")) {
            config.tide.playerDeath = ((Number) get("tide.playerDeath")).intValue();
        }
        if (has("tide.sssWave")) {
            config.tide.sssWave = ((Number) get("tide.sssWave")).intValue();
        }

        return config;
    }

    /**
     * Create an override from a preset name.
     */
    public static InstanceOverride fromPreset(String presetName) {
        return switch (presetName.toLowerCase(Locale.ROOT)) {
            case "easy" -> new InstanceOverride("Easy Mode")
                .setDuoWindowMs(700)
                .setTrinityWindowMs(500)
                .setApocalypseWindowMs(400)
                .setPlayerDeathTide(0)
                .setContractsEnabled(false);

            case "hard" -> new InstanceOverride("Hard Mode")
                .setDuoWindowMs(300)
                .setTrinityWindowMs(200)
                .setApocalypseWindowMs(100)
                .setPlayerDeathTide(3)
                .setProjectileDeflectionChance(0.50f);

            case "chaos" -> new InstanceOverride("Chaos Mode")
                .setDuoDamageMultiplier(3.0f)
                .setTrinityDamageMultiplier(5.0f)
                .setApocalypseDamageMultiplier(10.0f)
                .setSSSWaveTide(-25)
                .setMaxContractsPerWave(5);

            case "tutorial" -> new InstanceOverride("Tutorial Mode")
                .setResonanceEnabled(true)
                .setContractsEnabled(false)
                .setNemesisEnabled(false)
                .setTideEnabled(false);

            case "speedrun" -> new InstanceOverride("Speedrun Mode")
                .setDuoWindowMs(1000)
                .setResonanceCooldownMs(500)
                .setPlayerDeathTide(10);

            default -> new InstanceOverride(presetName);
        };
    }

    @Override
    public String toString() {
        return "InstanceOverride{" +
            "name='" + instanceName + '\'' +
            ", overrides=" + overrides.size() +
            '}';
    }
}
