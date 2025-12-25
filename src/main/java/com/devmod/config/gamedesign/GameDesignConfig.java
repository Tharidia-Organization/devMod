package com.devmod.config.gamedesign;

import com.google.gson.annotations.SerializedName;

/**
 * Master configuration for all Game Design systems.
 *
 * Contains sub-configurations for:
 * - Resonance Chain System
 * - Blood Contracts System
 * - Signature Weapons System
 * - Nemesis Evolution System
 * - The Tide System
 *
 * All values have sensible defaults matching the original implementations.
 */
public class GameDesignConfig {

    @SerializedName("version")
    public int version = 1;

    @SerializedName("resonance")
    public ResonanceConfig resonance = new ResonanceConfig();

    @SerializedName("contracts")
    public ContractsConfig contracts = new ContractsConfig();

    @SerializedName("signature_weapons")
    public SignatureWeaponsConfig signatureWeapons = new SignatureWeaponsConfig();

    @SerializedName("nemesis")
    public NemesisConfig nemesis = new NemesisConfig();

    @SerializedName("tide")
    public TideConfig tide = new TideConfig();

    /**
     * Resonance Chain System configuration.
     */
    public static class ResonanceConfig {
        @SerializedName("enabled")
        public boolean enabled = true;

        // Time windows (milliseconds)
        @SerializedName("duo_window_ms")
        public long duoWindowMs = 500;

        @SerializedName("trinity_window_ms")
        public long trinityWindowMs = 300;

        @SerializedName("apocalypse_window_ms")
        public long apocalypseWindowMs = 200;

        // Damage multipliers
        @SerializedName("duo_damage_multiplier")
        public float duoDamageMultiplier = 1.5f;

        @SerializedName("trinity_damage_multiplier")
        public float trinityDamageMultiplier = 2.5f;

        @SerializedName("apocalypse_damage_multiplier")
        public float apocalypseDamageMultiplier = 5.0f;

        // Style bonuses
        @SerializedName("duo_style_bonus")
        public int duoStyleBonus = 200;

        @SerializedName("trinity_style_bonus")
        public int trinityStyleBonus = 500;

        @SerializedName("apocalypse_style_bonus")
        public int apocalypseStyleBonus = 1000;

        // Cooldown
        @SerializedName("cooldown_ms")
        public long cooldownMs = 1500;

        // Shockwave effects
        @SerializedName("trinity_shockwave_radius")
        public double trinityShockwaveRadius = 3.0;

        @SerializedName("apocalypse_shockwave_radius")
        public double apocalypseShockwaveRadius = 5.0;

        @SerializedName("trinity_shockwave_damage")
        public float trinityShockwaveDamage = 4.0f;

        @SerializedName("apocalypse_shockwave_damage")
        public float apocalypseShockwaveDamage = 8.0f;
    }

    /**
     * Blood Contracts System configuration.
     */
    public static class ContractsConfig {
        @SerializedName("enabled")
        public boolean enabled = true;

        @SerializedName("max_contracts_per_wave")
        public int maxContractsPerWave = 2;

        // Tier unlock wave thresholds
        @SerializedName("major_tier_min_wave")
        public int majorTierMinWave = 5;

        @SerializedName("blood_tier_min_wave")
        public int bloodTierMinWave = 10;

        // Contract-specific multipliers (keyed by contract ID)
        @SerializedName("aggression_damage_dealt")
        public float aggressionDamageDealt = 1.5f;

        @SerializedName("aggression_damage_taken")
        public float aggressionDamageTaken = 2.0f;

        @SerializedName("aggression_reward")
        public float aggressionReward = 2.0f;

        @SerializedName("hubris_reward")
        public float hubrisReward = 3.0f;

        @SerializedName("haste_time_limit_seconds")
        public int hasteTimeLimitSeconds = 60;

        @SerializedName("haste_reward")
        public float hasteReward = 2.5f;

        @SerializedName("glass_reward")
        public float glassReward = 4.0f;

        @SerializedName("pacifist_reward")
        public float pacifistReward = 2.5f;

        @SerializedName("vampire_reward")
        public float vampireReward = 2.0f;

        @SerializedName("sacrifice_party_damage_multiplier")
        public float sacrificePartyDamageMultiplier = 2.0f;

        @SerializedName("sacrifice_party_reward")
        public float sacrificePartyReward = 2.0f;
    }

    /**
     * Signature Weapons System configuration.
     */
    public static class SignatureWeaponsConfig {
        @SerializedName("enabled")
        public boolean enabled = true;

        // Trait unlock thresholds
        @SerializedName("headshot_threshold")
        public int headshotThreshold = 100;

        @SerializedName("boss_kills_threshold")
        public int bossKillsThreshold = 50;

        @SerializedName("sss_waves_threshold")
        public int sssWavesThreshold = 10;

        @SerializedName("total_kills_threshold")
        public int totalKillsThreshold = 500;

        @SerializedName("perfect_resonances_threshold")
        public int perfectResonancesThreshold = 10;

        @SerializedName("critical_hits_threshold")
        public int criticalHitsThreshold = 200;

        @SerializedName("high_combos_threshold")
        public int highCombosThreshold = 25;

        @SerializedName("no_hit_waves_threshold")
        public int noHitWavesThreshold = 10;

        @SerializedName("total_damage_threshold")
        public int totalDamageThreshold = 50000;

        @SerializedName("execute_kills_threshold")
        public int executeKillsThreshold = 50;

        @SerializedName("multi_kills_threshold")
        public int multiKillsThreshold = 100;

        @SerializedName("parry_kills_threshold")
        public int parryKillsThreshold = 30;

        // Trait effect values
        @SerializedName("executioner_bonus")
        public float executionerBonus = 0.15f;

        @SerializedName("tyrant_slayer_bonus")
        public float tyrantSlayerBonus = 0.30f;

        @SerializedName("stylish_bonus")
        public float stylishBonus = 0.20f;

        @SerializedName("bloodthirsty_lifesteal")
        public float bloodthirstyLifesteal = 0.005f;

        @SerializedName("harmonic_bonus")
        public float harmonicBonus = 0.50f;

        @SerializedName("precision_crit_chance")
        public float precisionCritChance = 0.10f;

        @SerializedName("relentless_combo_decay")
        public float relentlessComboDecay = 0.20f;

        @SerializedName("guardian_damage_reduction")
        public float guardianDamageReduction = 0.05f;

        // Evolution stages
        @SerializedName("stage2_traits_required")
        public int stage2TraitsRequired = 1;

        @SerializedName("stage3_traits_required")
        public int stage3TraitsRequired = 3;

        @SerializedName("stage4_traits_required")
        public int stage4TraitsRequired = 5;
    }

    /**
     * Nemesis Evolution System configuration.
     */
    public static class NemesisConfig {
        @SerializedName("enabled")
        public boolean enabled = true;

        // Adaptation trigger thresholds (percentage of behavior)
        @SerializedName("ranged_attack_threshold")
        public float rangedAttackThreshold = 0.70f;

        @SerializedName("dodge_direction_threshold")
        public float dodgeDirectionThreshold = 0.60f;

        @SerializedName("fast_kill_threshold_seconds")
        public int fastKillThresholdSeconds = 30;

        @SerializedName("same_weapon_threshold")
        public float sameWeaponThreshold = 0.70f;

        @SerializedName("headshot_ratio_threshold")
        public float headshotRatioThreshold = 0.50f;

        // Resistance/bonus values
        @SerializedName("projectile_deflection_chance")
        public float projectileDeflectionChance = 0.30f;

        @SerializedName("weapon_type_resistance")
        public float weaponTypeResistance = 0.25f;

        @SerializedName("headbox_reduction")
        public float headboxReduction = 0.30f;

        // Scar level thresholds
        @SerializedName("scar_level_1_defeats")
        public int scarLevel1Defeats = 1;

        @SerializedName("scar_level_2_defeats")
        public int scarLevel2Defeats = 3;

        @SerializedName("scar_level_3_defeats")
        public int scarLevel3Defeats = 5;

        // Memory persistence
        @SerializedName("memory_duration_days")
        public int memoryDurationDays = 30;

        // Adaptation stat modifiers
        @SerializedName("improved_reflexes_speed_bonus")
        public float improvedReflexesSpeedBonus = 0.15f;

        @SerializedName("enraged_damage_bonus")
        public float enragedDamageBonus = 0.20f;

        @SerializedName("veteran_stat_bonus")
        public float veteranStatBonus = 0.15f;

        @SerializedName("regen_rate_per_second")
        public float regenRatePerSecond = 0.01f;
    }

    /**
     * The Tide System configuration.
     */
    public static class TideConfig {
        @SerializedName("enabled")
        public boolean enabled = true;

        @SerializedName("max_tide")
        public int maxTide = 1000;

        // Tide accumulation values
        @SerializedName("player_death")
        public int playerDeath = 1;

        @SerializedName("quest_failed_early")
        public int questFailedEarly = 5;

        @SerializedName("quest_completed")
        public int questCompleted = -3;

        @SerializedName("sss_wave")
        public int sssWave = -10;

        @SerializedName("boss_killed")
        public int bossKilled = -5;

        @SerializedName("resonance")
        public int resonance = -1;

        @SerializedName("no_hit_wave")
        public int noHitWave = -2;

        @SerializedName("perfect_quest")
        public int perfectQuest = -15;

        // Level thresholds
        @SerializedName("calm_max")
        public int calmMax = 100;

        @SerializedName("rising_max")
        public int risingMax = 300;

        @SerializedName("high_max")
        public int highMax = 500;

        @SerializedName("storm_max")
        public int stormMax = 800;

        // Level effects
        @SerializedName("rising_stat_bonus")
        public float risingStatBonus = 0.10f;

        @SerializedName("high_stat_bonus")
        public float highStatBonus = 0.20f;

        @SerializedName("storm_stat_bonus")
        public float stormStatBonus = 0.35f;

        @SerializedName("apocalypse_stat_bonus")
        public float apocalypseStatBonus = 0.50f;

        @SerializedName("storm_boss_wave_interval")
        public int stormBossWaveInterval = 3;

        // Tide Boss
        @SerializedName("tide_boss_health_per_player")
        public float tideBossHealthPerPlayer = 1000f;

        // Cooldowns
        @SerializedName("event_cooldown_ms")
        public long eventCooldownMs = 5000;
    }

    /**
     * Create a deep copy of this config.
     */
    public GameDesignConfig copy() {
        GameDesignConfig copy = new GameDesignConfig();
        copy.version = this.version;
        // Copy sub-configs (shallow copy for now - all primitive fields)
        copy.resonance = copyResonance(this.resonance);
        copy.contracts = copyContracts(this.contracts);
        copy.signatureWeapons = copySignatureWeapons(this.signatureWeapons);
        copy.nemesis = copyNemesis(this.nemesis);
        copy.tide = copyTide(this.tide);
        return copy;
    }

    private static ResonanceConfig copyResonance(ResonanceConfig src) {
        ResonanceConfig copy = new ResonanceConfig();
        copy.enabled = src.enabled;
        copy.duoWindowMs = src.duoWindowMs;
        copy.trinityWindowMs = src.trinityWindowMs;
        copy.apocalypseWindowMs = src.apocalypseWindowMs;
        copy.duoDamageMultiplier = src.duoDamageMultiplier;
        copy.trinityDamageMultiplier = src.trinityDamageMultiplier;
        copy.apocalypseDamageMultiplier = src.apocalypseDamageMultiplier;
        copy.duoStyleBonus = src.duoStyleBonus;
        copy.trinityStyleBonus = src.trinityStyleBonus;
        copy.apocalypseStyleBonus = src.apocalypseStyleBonus;
        copy.cooldownMs = src.cooldownMs;
        copy.trinityShockwaveRadius = src.trinityShockwaveRadius;
        copy.apocalypseShockwaveRadius = src.apocalypseShockwaveRadius;
        copy.trinityShockwaveDamage = src.trinityShockwaveDamage;
        copy.apocalypseShockwaveDamage = src.apocalypseShockwaveDamage;
        return copy;
    }

    private static ContractsConfig copyContracts(ContractsConfig src) {
        ContractsConfig copy = new ContractsConfig();
        copy.enabled = src.enabled;
        copy.maxContractsPerWave = src.maxContractsPerWave;
        copy.aggressionDamageDealt = src.aggressionDamageDealt;
        copy.aggressionDamageTaken = src.aggressionDamageTaken;
        copy.aggressionReward = src.aggressionReward;
        copy.hubrisReward = src.hubrisReward;
        copy.hasteTimeLimitSeconds = src.hasteTimeLimitSeconds;
        copy.hasteReward = src.hasteReward;
        copy.glassReward = src.glassReward;
        copy.pacifistReward = src.pacifistReward;
        copy.vampireReward = src.vampireReward;
        copy.sacrificePartyDamageMultiplier = src.sacrificePartyDamageMultiplier;
        copy.sacrificePartyReward = src.sacrificePartyReward;
        return copy;
    }

    private static SignatureWeaponsConfig copySignatureWeapons(SignatureWeaponsConfig src) {
        SignatureWeaponsConfig copy = new SignatureWeaponsConfig();
        copy.enabled = src.enabled;
        copy.headshotThreshold = src.headshotThreshold;
        copy.bossKillsThreshold = src.bossKillsThreshold;
        copy.sssWavesThreshold = src.sssWavesThreshold;
        copy.totalKillsThreshold = src.totalKillsThreshold;
        copy.perfectResonancesThreshold = src.perfectResonancesThreshold;
        copy.criticalHitsThreshold = src.criticalHitsThreshold;
        copy.highCombosThreshold = src.highCombosThreshold;
        copy.noHitWavesThreshold = src.noHitWavesThreshold;
        copy.totalDamageThreshold = src.totalDamageThreshold;
        copy.executeKillsThreshold = src.executeKillsThreshold;
        copy.multiKillsThreshold = src.multiKillsThreshold;
        copy.parryKillsThreshold = src.parryKillsThreshold;
        copy.executionerBonus = src.executionerBonus;
        copy.tyrantSlayerBonus = src.tyrantSlayerBonus;
        copy.stylishBonus = src.stylishBonus;
        copy.bloodthirstyLifesteal = src.bloodthirstyLifesteal;
        copy.harmonicBonus = src.harmonicBonus;
        copy.precisionCritChance = src.precisionCritChance;
        copy.relentlessComboDecay = src.relentlessComboDecay;
        copy.guardianDamageReduction = src.guardianDamageReduction;
        copy.stage2TraitsRequired = src.stage2TraitsRequired;
        copy.stage3TraitsRequired = src.stage3TraitsRequired;
        copy.stage4TraitsRequired = src.stage4TraitsRequired;
        return copy;
    }

    private static NemesisConfig copyNemesis(NemesisConfig src) {
        NemesisConfig copy = new NemesisConfig();
        copy.enabled = src.enabled;
        copy.rangedAttackThreshold = src.rangedAttackThreshold;
        copy.dodgeDirectionThreshold = src.dodgeDirectionThreshold;
        copy.fastKillThresholdSeconds = src.fastKillThresholdSeconds;
        copy.sameWeaponThreshold = src.sameWeaponThreshold;
        copy.headshotRatioThreshold = src.headshotRatioThreshold;
        copy.projectileDeflectionChance = src.projectileDeflectionChance;
        copy.weaponTypeResistance = src.weaponTypeResistance;
        copy.headboxReduction = src.headboxReduction;
        copy.scarLevel1Defeats = src.scarLevel1Defeats;
        copy.scarLevel2Defeats = src.scarLevel2Defeats;
        copy.scarLevel3Defeats = src.scarLevel3Defeats;
        copy.memoryDurationDays = src.memoryDurationDays;
        return copy;
    }

    private static TideConfig copyTide(TideConfig src) {
        TideConfig copy = new TideConfig();
        copy.enabled = src.enabled;
        copy.maxTide = src.maxTide;
        copy.playerDeath = src.playerDeath;
        copy.questFailedEarly = src.questFailedEarly;
        copy.questCompleted = src.questCompleted;
        copy.sssWave = src.sssWave;
        copy.bossKilled = src.bossKilled;
        copy.resonance = src.resonance;
        copy.noHitWave = src.noHitWave;
        copy.perfectQuest = src.perfectQuest;
        copy.calmMax = src.calmMax;
        copy.risingMax = src.risingMax;
        copy.highMax = src.highMax;
        copy.stormMax = src.stormMax;
        copy.risingStatBonus = src.risingStatBonus;
        copy.highStatBonus = src.highStatBonus;
        copy.stormStatBonus = src.stormStatBonus;
        copy.apocalypseStatBonus = src.apocalypseStatBonus;
        copy.stormBossWaveInterval = src.stormBossWaveInterval;
        copy.tideBossHealthPerPlayer = src.tideBossHealthPerPlayer;
        copy.eventCooldownMs = src.eventCooldownMs;
        return copy;
    }
}
