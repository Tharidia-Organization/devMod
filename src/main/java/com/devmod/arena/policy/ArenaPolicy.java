package com.devmod.arena.policy;

import java.util.Set;

import javax.annotation.Nullable;

/**
 * Arena Policy definition (L2 Gameplay layer).
 *
 * <p>Policies define gameplay rules that get applied on top of templates:
 * which template to use for which mob/quest/difficulty combination.
 */
public record ArenaPolicy(
    /** Unique policy identifier */
    String id,

    /** Policy version (higher wins on tie-break) */
    int version,

    /** Template ID this policy uses */
    String templateId,

    /** Minimum template version supported (inclusive) */
    @Nullable Integer minTemplateVersion,

    /** Maximum template version supported (inclusive) */
    @Nullable Integer maxTemplateVersion,

    /** Mob type(s) this policy applies to */
    @Nullable Set<String> mobTypes,

    /** Quest type filters (e.g., "boss", "normal", "event") */
    @Nullable Set<String> questTypes,

    /** Difficulty tag filters */
    @Nullable Set<String> difficultyTags,

    /** Player count range - min */
    @Nullable Integer minPlayers,

    /** Player count range - max */
    @Nullable Integer maxPlayers,

    /** Tags for matching */
    @Nullable Set<String> tags,

    /** Priority for explicit ordering */
    int priority,

    /** Routing weight (0.1 - 10.0, default 1.0) */
    double weight,

    /** Perk bindings (suggested/excluded/required) */
    @Nullable PerkBindings perkBindings,

    /** Mutator bindings (suggested/excluded/required) */
    @Nullable MutatorBindings mutatorBindings,

    /** Reward modifiers */
    @Nullable RewardModifiers rewardModifiers,

    /** Balance overrides */
    @Nullable BalanceOverrides balanceOverrides,

    /** Complete gameplay overrides (per-instance config) */
    @Nullable GameplayOverrides gameplayOverrides,

    /** Whether this policy is enabled */
    boolean enabled,

    /** Human-readable description */
    @Nullable String description
) {
    /**
     * Default policy using default_flat_64 template.
     */
    public static final ArenaPolicy DEFAULT = new ArenaPolicy(
        "default",
        1,
        "default_flat_64",
        null,
        null,
        null,
        Set.of("endurance"),
        Set.of("normal"),
        null,
        null,
        Set.of(),
        0,
        1.0,
        null,
        null,
        null,
        null,
        null,
        true,
        "Default fallback policy"
    );

    /**
     * Boss routing policy for boss_ring_80.
     */
    public static final ArenaPolicy BOSS_RING_80 = new ArenaPolicy(
        "boss_ring_80_default",
        1,
        "boss_ring_80",
        null,
        null,
        null,
        Set.of("boss"),
        Set.of("hard"),
        null,
        null,
        Set.of("boss"),
        3,
        1.3,
        null,
        null,
        null,
        null,
        null,
        true,
        "Boss routing policy for boss_ring_80"
    );

    /**
     * Creates a builder for this policy.
     */
    public static Builder builder(String id) {
        return new Builder(id);
    }

    /**
     * Returns a copy of this policy with the provided weight.
     */
    public ArenaPolicy withWeight(double newWeight) {
        return new ArenaPolicy(
            id, version, templateId, minTemplateVersion, maxTemplateVersion, mobTypes, questTypes, difficultyTags,
            minPlayers, maxPlayers, tags, priority, newWeight, perkBindings, mutatorBindings, rewardModifiers,
            balanceOverrides, gameplayOverrides, enabled, description
        );
    }

    /**
     * Builder for ArenaPolicy.
     */
    public static class Builder {
        private final String id;
        private int version = 1;
        private String templateId;
        private Integer minTemplateVersion;
        private Integer maxTemplateVersion;
        private Set<String> mobTypes;
        private Set<String> questTypes;
        private Set<String> difficultyTags;
        private Integer minPlayers;
        private Integer maxPlayers;
        private Set<String> tags;
        private int priority = 0;
        private double weight = 1.0;
        private PerkBindings perkBindings;
        private MutatorBindings mutatorBindings;
        private RewardModifiers rewardModifiers;
        private BalanceOverrides balanceOverrides;
        private GameplayOverrides gameplayOverrides;
        private boolean enabled = true;
        private String description;

        public Builder(String id) {
            this.id = id;
        }

        public Builder version(int version) { this.version = version; return this; }
        public Builder templateId(String templateId) { this.templateId = templateId; return this; }
        public Builder minTemplateVersion(@Nullable Integer minTemplateVersion) { this.minTemplateVersion = minTemplateVersion; return this; }
        public Builder maxTemplateVersion(@Nullable Integer maxTemplateVersion) { this.maxTemplateVersion = maxTemplateVersion; return this; }
        public Builder mobTypes(Set<String> mobTypes) { this.mobTypes = mobTypes; return this; }
        public Builder questTypes(Set<String> questTypes) { this.questTypes = questTypes; return this; }
        public Builder questType(String questType) {
            this.questTypes = questType != null ? Set.of(questType) : null;
            return this;
        }
        public Builder difficultyTags(Set<String> difficultyTags) { this.difficultyTags = difficultyTags; return this; }
        public Builder difficulty(String difficulty) {
            this.difficultyTags = difficulty != null ? Set.of(difficulty) : null;
            return this;
        }
        public Builder minPlayers(Integer minPlayers) { this.minPlayers = minPlayers; return this; }
        public Builder maxPlayers(Integer maxPlayers) { this.maxPlayers = maxPlayers; return this; }
        public Builder tags(Set<String> tags) { this.tags = tags; return this; }
        public Builder priority(int priority) { this.priority = priority; return this; }
        public Builder weight(double weight) { this.weight = weight; return this; }
        public Builder perkBindings(@Nullable PerkBindings perkBindings) { this.perkBindings = perkBindings; return this; }
        public Builder mutatorBindings(@Nullable MutatorBindings mutatorBindings) { this.mutatorBindings = mutatorBindings; return this; }
        public Builder rewardModifiers(@Nullable RewardModifiers rewardModifiers) { this.rewardModifiers = rewardModifiers; return this; }
        public Builder balanceOverrides(@Nullable BalanceOverrides balanceOverrides) { this.balanceOverrides = balanceOverrides; return this; }
        public Builder gameplayOverrides(@Nullable GameplayOverrides gameplayOverrides) { this.gameplayOverrides = gameplayOverrides; return this; }
        public Builder enabled(boolean enabled) { this.enabled = enabled; return this; }
        public Builder description(String description) { this.description = description; return this; }

        public ArenaPolicy build() {
            return new ArenaPolicy(
                id, version, templateId, minTemplateVersion, maxTemplateVersion, mobTypes, questTypes, difficultyTags,
                minPlayers, maxPlayers, tags, priority, weight,
                perkBindings, mutatorBindings, rewardModifiers, balanceOverrides, gameplayOverrides,
                enabled, description
            );
        }
    }

    public record PerkBindings(
        @Nullable Set<String> suggested,
        @Nullable Set<String> excluded,
        @Nullable Set<String> required
    ) {}

    public record MutatorBindings(
        @Nullable Set<String> suggested,
        @Nullable Set<String> excluded,
        @Nullable Set<String> required
    ) {}

    public record RewardModifiers(
        double baseMultiplier,
        double firstCompletionBonus,
        double hazardBonus,
        double streakMultiplier
    ) {}

    public record BalanceOverrides(
        @Nullable Double spawnRateMultiplier,
        @Nullable Double damageMultiplier,
        @Nullable Double waveScaling
    ) {}

    /**
     * Complete gameplay overrides for per-instance configuration.
     * All fields are nullable - null means use global config value.
     *
     * @see com.devmod.config.GameMechanicsConfig
     */
    public record GameplayOverrides(
        // Execution System
        @Nullable ExecutionOverrides execution,
        // Combo System
        @Nullable ComboOverrides combo,
        // Style Rank System
        @Nullable StyleRankOverrides styleRank,
        // Momentum/Flow State
        @Nullable MomentumOverrides momentum,
        // Devil's Bargain
        @Nullable BargainOverrides bargain,
        // Arena Hazards
        @Nullable HazardOverrides hazards,
        // Perk Synergy
        @Nullable PerkOverrides perks,
        // Wave Config
        @Nullable WaveOverrides waves,
        // Reward Config
        @Nullable RewardOverrides rewards,
        // Season Pass
        @Nullable ArenaPolicy.SeasonPassOverrides seasonPass,
        // Guild System
        @Nullable ArenaPolicy.GuildOverrides guild,
        // Ascension/Prestige
        @Nullable ArenaPolicy.AscensionOverrides ascension,
        // Tension System
        @Nullable ArenaPolicy.TensionOverrides tension,
        // Challenge System
        @Nullable ArenaPolicy.ChallengeOverrides challenges,
        // Extended Perk Rarity
        @Nullable ArenaPolicy.PerkRarityOverrides perkRarity
    ) {
        public static final GameplayOverrides EMPTY = new GameplayOverrides(
            null, null, null, null, null, null, null, null, null,
            null, null, null, null, null, null
        );

        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private ExecutionOverrides execution;
            private ComboOverrides combo;
            private StyleRankOverrides styleRank;
            private MomentumOverrides momentum;
            private BargainOverrides bargain;
            private HazardOverrides hazards;
            private PerkOverrides perks;
            private WaveOverrides waves;
            private RewardOverrides rewards;
            private ArenaPolicy.SeasonPassOverrides seasonPass;
            private ArenaPolicy.GuildOverrides guild;
            private ArenaPolicy.AscensionOverrides ascension;
            private ArenaPolicy.TensionOverrides tension;
            private ArenaPolicy.ChallengeOverrides challenges;
            private ArenaPolicy.PerkRarityOverrides perkRarity;

            public Builder execution(ExecutionOverrides execution) { this.execution = execution; return this; }
            public Builder combo(ComboOverrides combo) { this.combo = combo; return this; }
            public Builder styleRank(StyleRankOverrides styleRank) { this.styleRank = styleRank; return this; }
            public Builder momentum(MomentumOverrides momentum) { this.momentum = momentum; return this; }
            public Builder bargain(BargainOverrides bargain) { this.bargain = bargain; return this; }
            public Builder hazards(HazardOverrides hazards) { this.hazards = hazards; return this; }
            public Builder perks(PerkOverrides perks) { this.perks = perks; return this; }
            public Builder waves(WaveOverrides waves) { this.waves = waves; return this; }
            public Builder rewards(RewardOverrides rewards) { this.rewards = rewards; return this; }
            public Builder seasonPass(ArenaPolicy.SeasonPassOverrides seasonPass) { this.seasonPass = seasonPass; return this; }
            public Builder guild(ArenaPolicy.GuildOverrides guild) { this.guild = guild; return this; }
            public Builder ascension(ArenaPolicy.AscensionOverrides ascension) { this.ascension = ascension; return this; }
            public Builder tension(ArenaPolicy.TensionOverrides tension) { this.tension = tension; return this; }
            public Builder challenges(ArenaPolicy.ChallengeOverrides challenges) { this.challenges = challenges; return this; }
            public Builder perkRarity(ArenaPolicy.PerkRarityOverrides perkRarity) { this.perkRarity = perkRarity; return this; }

            public GameplayOverrides build() {
                return new GameplayOverrides(
                    execution, combo, styleRank, momentum, bargain, hazards, perks, waves, rewards,
                    seasonPass, guild, ascension, tension, challenges, perkRarity
                );
            }
        }
    }

    /**
     * Execution system per-instance overrides.
     */
    public record ExecutionOverrides(
        @Nullable Double hpThreshold,
        @Nullable Integer durationTicks,
        @Nullable Integer cooldownTicks,
        @Nullable Integer styleReward,
        @Nullable Double hpRegenPercent,
        @Nullable Double dropBoost,
        @Nullable Double interruptVulnerability,
        @Nullable Double range
    ) {
        public static ExecutionOverrides of(Double hpThreshold, Integer styleReward) {
            return new ExecutionOverrides(hpThreshold, null, null, styleReward, null, null, null, null);
        }
    }

    /**
     * Combo system per-instance overrides.
     */
    public record ComboOverrides(
        @Nullable Integer timeoutTicks,
        @Nullable Integer basePoints,
        @Nullable Double multiplierIncrement,
        @Nullable Double maxMultiplier,
        @Nullable Integer finisherThreshold,
        @Nullable Integer juggleBonus,
        @Nullable Integer headshotBonus,
        @Nullable Integer executionBonus
    ) {
        public static ComboOverrides faster() {
            return new ComboOverrides(40, null, 0.15, 5.0, 8, null, null, null);
        }
    }

    /**
     * Style rank system per-instance overrides.
     */
    public record StyleRankOverrides(
        @Nullable Integer dThreshold,
        @Nullable Integer cThreshold,
        @Nullable Integer bThreshold,
        @Nullable Integer aThreshold,
        @Nullable Integer sThreshold,
        @Nullable Integer ssThreshold,
        @Nullable Integer sssThreshold,
        @Nullable Double decayRate,
        @Nullable Integer decayDelayTicks
    ) {
        public static StyleRankOverrides harder() {
            return new StyleRankOverrides(0, 150, 400, 800, 1500, 2500, 4000, 8.0, 80);
        }

        public static StyleRankOverrides easier() {
            return new StyleRankOverrides(0, 50, 150, 350, 600, 1000, 1500, 3.0, 150);
        }
    }

    /**
     * Momentum/Flow state per-instance overrides.
     */
    public record MomentumOverrides(
        @Nullable Double decayRate,
        @Nullable Double killBoost,
        @Nullable Double hitBoost,
        @Nullable Double overdriveThreshold,
        @Nullable Integer overdriveDurationTicks,
        @Nullable Integer staleThresholdTicks,
        @Nullable Double freshBonusMultiplier,
        @Nullable Double virtuosoThreshold,
        @Nullable Integer virtuosoDurationTicks
    ) {}

    /**
     * Devil's Bargain per-instance overrides.
     */
    public record BargainOverrides(
        @Nullable Boolean enabled,
        @Nullable Integer altarSpawnWave,
        @Nullable Integer altarIntervalWaves,
        @Nullable Integer maxCursesPerRun,
        @Nullable Integer choiceTimeoutTicks,
        @Nullable Double cursePowerMultiplier,
        @Nullable Double boonPowerMultiplier
    ) {
        public static BargainOverrides disabled() {
            return new BargainOverrides(false, null, null, null, null, null, null);
        }

        public static BargainOverrides highRisk() {
            return new BargainOverrides(true, 2, 1, 10, 200, 1.5, 1.5);
        }
    }

    /**
     * Arena hazards per-instance overrides.
     */
    public record HazardOverrides(
        @Nullable Boolean enabled,
        @Nullable Integer floorCrumbleWave,
        @Nullable Integer bloodMoonWave,
        @Nullable Integer arenaShrinkWave,
        @Nullable Integer lightningStormWave,
        @Nullable Integer voidRiftsWave,
        @Nullable Integer floorCrumbleDuration,
        @Nullable Integer bloodMoonDuration,
        @Nullable Integer arenaShrinkDuration,
        @Nullable Integer lightningStormDuration,
        @Nullable Integer voidRiftsDuration,
        @Nullable Double lightningDamage,
        @Nullable Double voidRiftDamage,
        @Nullable Double bloodMoonMobBuff,
        @Nullable Double arenaShrinkRate
    ) {
        public static HazardOverrides disabled() {
            return new HazardOverrides(false, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null);
        }

        public static HazardOverrides earlyHazards() {
            return new HazardOverrides(true, 2, 3, 5, 7, 9,
                null, null, null, null, null, null, null, null, null);
        }
    }

    /**
     * Perk system per-instance overrides.
     */
    public record PerkOverrides(
        @Nullable Boolean synergyEnabled,
        @Nullable Boolean hiddenPerksEnabled,
        @Nullable Boolean sacrificeEnabled,
        @Nullable Integer sacrificeStyleCost,
        @Nullable Integer discoveryXpBase,
        @Nullable Double synergyBonusMultiplier
    ) {}

    /**
     * Wave configuration per-instance overrides.
     */
    public record WaveOverrides(
        @Nullable Integer baseMobCount,
        @Nullable Double mobScaling,
        @Nullable Integer intermissionTicks,
        @Nullable Double eliteChanceBase,
        @Nullable Double eliteChanceScaling,
        @Nullable Integer bossInterval
    ) {
        public static WaveOverrides intense() {
            return new WaveOverrides(8, 1.4, 60, 0.2, 0.03, 3);
        }

        public static WaveOverrides relaxed() {
            return new WaveOverrides(3, 1.1, 150, 0.05, 0.01, 8);
        }
    }

    /**
     * Reward configuration per-instance overrides.
     */
    public record RewardOverrides(
        @Nullable Double xpMultiplier,
        @Nullable Double styleMultiplier,
        @Nullable Double dropRateBonus,
        @Nullable Integer bonusChestWaveInterval
    ) {
        public static RewardOverrides generous() {
            return new RewardOverrides(2.0, 1.5, 0.5, 2);
        }

        public static RewardOverrides sparse() {
            return new RewardOverrides(0.5, 0.75, 0.0, 5);
        }
    }

    // ============================================
    // ENDURANCE QUEST OVERRIDE RECORDS
    // ============================================

    /**
     * Season Pass per-instance overrides.
     */
    public record SeasonPassOverrides(
        @Nullable Integer maxTier,
        @Nullable Integer xpPerTier,
        @Nullable Integer xpPerKill,
        @Nullable Integer xpPerWave,
        @Nullable Integer xpPerBoss,
        @Nullable Integer xpDailyChallenge,
        @Nullable Integer xpWeeklyChallenge,
        @Nullable Integer xpStyleS,
        @Nullable Integer xpStyleSS,
        @Nullable Integer xpStyleSSS,
        @Nullable Integer xpFlawlessWave,
        @Nullable Integer xpHighCombo50,
        @Nullable Integer xpHighCombo100,
        @Nullable Double xpBoostMultiplier,
        @Nullable Double xpGlobalMultiplier
    ) {
        public static SeasonPassOverrides doubleXP() {
            return new SeasonPassOverrides(
                null, null, null, null, null, null, null, null, null, null, null, null, null, null, 2.0
            );
        }

        public static SeasonPassOverrides fastProgression() {
            return new SeasonPassOverrides(
                50, 500, 5, 100, 400, 1000, 4000, 200, 400, 1000, 150, 300, 600, null, null
            );
        }
    }

    /**
     * Guild system per-instance overrides.
     */
    public record GuildOverrides(
        @Nullable Integer maxSize,
        @Nullable Integer maxLevel,
        @Nullable Integer createCost,
        @Nullable Integer xpPerLevelBase,
        @Nullable Double xpScaling,
        @Nullable Integer bankTaxPercent,
        @Nullable Integer objectiveKillsTarget,
        @Nullable Integer objectiveWavesTarget,
        @Nullable Integer objectiveBossesTarget,
        @Nullable Integer objectiveTokensTarget,
        @Nullable Double bonusTokensMultiplier,
        @Nullable Double bonusXpMultiplier
    ) {
        public static GuildOverrides smallGuilds() {
            return new GuildOverrides(20, 10, 2000, null, null, 5, 5000, 250, 50, 25000, null, null);
        }

        public static GuildOverrides largeGuilds() {
            return new GuildOverrides(100, 30, 10000, null, null, 15, 20000, 1000, 200, 100000, null, null);
        }
    }

    /**
     * Ascension/Prestige per-instance overrides.
     */
    public record AscensionOverrides(
        @Nullable Integer maxLevel,
        @Nullable Integer minPrestige,
        @Nullable Integer costScaling,
        @Nullable Double damageBonusPerLevel,
        @Nullable Double defenseBonusPerLevel,
        @Nullable Double tokenMultiplierPerLevel,
        @Nullable Double lifestealBonusPerLevel,
        @Nullable Double critBonusPerLevel,
        @Nullable Double comboDecayReductionPerLevel,
        @Nullable Integer extraPerkSlotsPerLevel,
        @Nullable Double startingHpBonusPerLevel
    ) {
        public static AscensionOverrides easy() {
            return new AscensionOverrides(
                5, 50, 25, 0.10, 0.06, 0.20, 0.02, 0.04, 0.10, 1, 0.10
            );
        }

        public static AscensionOverrides hardcore() {
            return new AscensionOverrides(
                20, 200, 100, 0.03, 0.02, 0.05, 0.005, 0.01, 0.03, 0, 0.02
            );
        }
    }

    /**
     * Tension system per-instance overrides.
     */
    public record TensionOverrides(
        @Nullable Double baseWaveGain,
        @Nullable Double noHitBonus,
        @Nullable Double minThreshold,
        @Nullable Double maxThreshold,
        @Nullable Double eliteKillBonus,
        @Nullable Double comboBonusThreshold,
        @Nullable Double styleSBonus,
        @Nullable Integer minWavesBeforeBoss,
        @Nullable Integer maxWavesWithoutBoss,
        @Nullable Double decayAfterBoss
    ) {
        public static TensionOverrides fastBosses() {
            return new TensionOverrides(0.20, 0.30, 0.50, 1.0, 0.10, 0.05, 0.15, 2, 5, 0.30);
        }

        public static TensionOverrides slowBosses() {
            return new TensionOverrides(0.08, 0.10, 0.85, 1.0, 0.03, 0.02, 0.05, 5, 12, 0.70);
        }

        public static TensionOverrides consistent() {
            return new TensionOverrides(0.20, 0.0, 1.0, 1.0, 0.0, 0.0, 0.0, 5, 5, 1.0);
        }
    }

    /**
     * Challenge system per-instance overrides.
     */
    public record ChallengeOverrides(
        @Nullable Integer dailyCount,
        @Nullable Integer weeklyCount,
        @Nullable Integer dailyResetHour,
        @Nullable Integer weeklyResetDay,
        @Nullable Integer killsTargetMin,
        @Nullable Integer killsTargetMax,
        @Nullable Integer wavesTargetMin,
        @Nullable Integer wavesTargetMax,
        @Nullable Integer comboTargetMin,
        @Nullable Integer comboTargetMax,
        @Nullable Integer dailyTokenReward,
        @Nullable Integer weeklyTokenReward,
        @Nullable Integer dailyXpReward,
        @Nullable Integer weeklyXpReward
    ) {
        public static ChallengeOverrides easy() {
            return new ChallengeOverrides(
                3, 2, null, null, 25, 100, 3, 10, 15, 50, 150, 750, 750, 3000
            );
        }

        public static ChallengeOverrides hard() {
            return new ChallengeOverrides(
                5, 3, null, null, 100, 500, 10, 30, 50, 200, 200, 1000, 1000, 5000
            );
        }
    }

    /**
     * Perk rarity and values per-instance overrides.
     */
    public record PerkRarityOverrides(
        @Nullable Integer commonWeight,
        @Nullable Integer uncommonWeight,
        @Nullable Integer rareWeight,
        @Nullable Integer epicWeight,
        @Nullable Integer legendaryWeight,
        @Nullable Integer choicesPerSelection,
        @Nullable Integer rerollCostBase,
        @Nullable Integer rerollCostIncrement,
        @Nullable Double damageBonusCommon,
        @Nullable Double damageBonusUncommon,
        @Nullable Double damageBonusRare,
        @Nullable Double damageBonusEpic,
        @Nullable Double damageBonusLegendary
    ) {
        public static PerkRarityOverrides luckyDrops() {
            return new PerkRarityOverrides(40, 30, 18, 8, 4, 4, 25, 15, null, null, null, null, null);
        }

        public static PerkRarityOverrides commonOnly() {
            return new PerkRarityOverrides(90, 10, 0, 0, 0, 3, 100, 50, null, null, null, null, null);
        }

        public static PerkRarityOverrides highRarity() {
            return new PerkRarityOverrides(20, 25, 25, 20, 10, 3, 50, 25, null, null, null, null, null);
        }
    }
}
