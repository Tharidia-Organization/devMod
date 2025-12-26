package com.devmod.endurance.config;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.devmod.arena.policy.ArenaPolicy.AscensionOverrides;
import com.devmod.arena.policy.ArenaPolicy.ChallengeOverrides;
import com.devmod.arena.policy.ArenaPolicy.ComboOverrides;
import com.devmod.arena.policy.ArenaPolicy.GameplayOverrides;
import com.devmod.arena.policy.ArenaPolicy.GuildOverrides;
import com.devmod.arena.policy.ArenaPolicy.PerkRarityOverrides;
import com.devmod.arena.policy.ArenaPolicy.SeasonPassOverrides;
import com.devmod.arena.policy.ArenaPolicy.StyleRankOverrides;
import com.devmod.arena.policy.ArenaPolicy.TensionOverrides;
import com.devmod.config.GameMechanicsConfig;

public class EnduranceConfigManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(EnduranceConfigManager.class);

    public static final EnduranceConfigManager INSTANCE = new EnduranceConfigManager();

    // Runtime overrides per quest instance (for testing)
    private final Map<UUID, RuntimeOverrides> questOverrides = new ConcurrentHashMap<>();

    // Current active ArenaPolicy override (set when entering an arena)
    private final Map<UUID, GameplayOverrides> activeArenaOverrides = new ConcurrentHashMap<>();

    private EnduranceConfigManager() {}

    @Nullable
    private <T> T resolveOverride(UUID questId,
            Function<RuntimeOverrides, T> runtimeGetter,
            Function<GameplayOverrides, T> arenaGetter) {
        RuntimeOverrides runtime = questOverrides.get(questId);
        if (runtime != null) {
            T value = runtimeGetter.apply(runtime);
            if (value != null) {
                return value;
            }
        }

        GameplayOverrides arena = activeArenaOverrides.get(questId);
        if (arena != null) {
            T value = arenaGetter.apply(arena);
            if (value != null) {
                return value;
            }
        }

        return null;
    }

    @Nullable
    private static <T, R> R resolveNested(@Nullable T overrides, Function<T, R> getter) {
        return overrides != null ? getter.apply(overrides) : null;
    }

    // Safe accessor helpers for nullable sub-configs (avoids NullAway warnings in lambdas)
    @Nullable
    private static <R> R safeSeasonPass(GameplayOverrides arena, Function<SeasonPassOverrides, R> getter) {
        var sp = arena.seasonPass();
        return sp != null ? getter.apply(sp) : null;
    }

    @Nullable
    private static <R> R safeGuild(GameplayOverrides arena, Function<GuildOverrides, R> getter) {
        var g = arena.guild();
        return g != null ? getter.apply(g) : null;
    }

    @Nullable
    private static <R> R safeAscension(GameplayOverrides arena, Function<AscensionOverrides, R> getter) {
        var a = arena.ascension();
        return a != null ? getter.apply(a) : null;
    }

    @Nullable
    private static <R> R safeTension(GameplayOverrides arena, Function<TensionOverrides, R> getter) {
        var t = arena.tension();
        return t != null ? getter.apply(t) : null;
    }

    @Nullable
    private static <R> R safePerkRarity(GameplayOverrides arena, Function<PerkRarityOverrides, R> getter) {
        var pr = arena.perkRarity();
        return pr != null ? getter.apply(pr) : null;
    }

    @Nullable
    private static <R> R safeChallenges(GameplayOverrides arena, Function<ChallengeOverrides, R> getter) {
        var c = arena.challenges();
        return c != null ? getter.apply(c) : null;
    }

    @Nullable
    private static <R> R safeCombo(GameplayOverrides arena, Function<ComboOverrides, R> getter) {
        var c = arena.combo();
        return c != null ? getter.apply(c) : null;
    }

    @Nullable
    private static <R> R safeStyleRank(GameplayOverrides arena, Function<StyleRankOverrides, R> getter) {
        var sr = arena.styleRank();
        return sr != null ? getter.apply(sr) : null;
    }

    private static int resolveIntOverride(@Nullable Integer overrideValue, @Nullable Integer configValue, String label) {
        if (overrideValue != null) {
            return overrideValue.intValue();
        }
        if (configValue == null) {
            throw new IllegalStateException(label + " is null");
        }
        return configValue.intValue();
    }

    private static double resolveDoubleOverride(@Nullable Double overrideValue, @Nullable Double configValue, String label) {
        if (overrideValue != null) {
            return overrideValue.doubleValue();
        }
        if (configValue == null) {
            throw new IllegalStateException(label + " is null");
        }
        return configValue.doubleValue();
    }

    // ========== Runtime Override Management ==========

    /**
     * Set runtime overrides for a specific quest instance.
     * Used for testing specific mobs/mods with custom parameters.
     */
    public void setRuntimeOverride(UUID questId, RuntimeOverrides overrides) {
        if (overrides != null) {
            questOverrides.put(questId, overrides);
            LOGGER.info("[EnduranceConfig] Set runtime override for quest {}", questId);
        }
    }

    /**
     * Clear runtime overrides for a quest.
     */
    public void clearRuntimeOverride(UUID questId) {
        if (questOverrides.remove(questId) != null) {
            LOGGER.info("[EnduranceConfig] Cleared runtime override for quest {}", questId);
        }
    }

    /**
     * Get runtime overrides for a quest.
     */
    @Nullable
    public RuntimeOverrides getRuntimeOverride(UUID questId) {
        return questOverrides.get(questId);
    }

    // ========== Arena Override Management ==========

    /**
     * Set active arena override for a quest.
     * Called when player enters an arena with custom policy.
     */
    public void setArenaOverride(UUID questId, GameplayOverrides overrides) {
        if (overrides != null) {
            activeArenaOverrides.put(questId, overrides);
        }
    }

    /**
     * Clear arena override for a quest.
     */
    public void clearArenaOverride(UUID questId) {
        activeArenaOverrides.remove(questId);
    }

    /**
     * Get arena override for a quest.
     */
    @Nullable
    public GameplayOverrides getArenaOverride(UUID questId) {
        return activeArenaOverrides.get(questId);
    }

    // ========== SEASON PASS CONFIG ==========

    public int getSeasonMaxTier(UUID questId) {
        Integer override = resolveOverride(questId,
            runtime -> resolveNested(runtime.seasonPass, SeasonPassOverrides::maxTier),
            arena -> safeSeasonPass(arena, SeasonPassOverrides::maxTier));
        return resolveIntOverride(override, GameMechanicsConfig.SEASON_MAX_TIER.get(), "SEASON_MAX_TIER");
    }

    public int getSeasonXpPerTier(UUID questId) {
        Integer override = resolveOverride(questId,
            runtime -> resolveNested(runtime.seasonPass, SeasonPassOverrides::xpPerTier),
            arena -> safeSeasonPass(arena, SeasonPassOverrides::xpPerTier));
        return resolveIntOverride(override, GameMechanicsConfig.SEASON_XP_PER_TIER.get(), "SEASON_XP_PER_TIER");
    }

    public int getSeasonXpPerKill(UUID questId) {
        Integer override = resolveOverride(questId,
            runtime -> resolveNested(runtime.seasonPass, SeasonPassOverrides::xpPerKill),
            arena -> safeSeasonPass(arena, SeasonPassOverrides::xpPerKill));
        return resolveIntOverride(override, GameMechanicsConfig.SEASON_XP_PER_KILL.get(), "SEASON_XP_PER_KILL");
    }

    public int getSeasonXpPerWave(UUID questId) {
        Integer override = resolveOverride(questId,
            runtime -> resolveNested(runtime.seasonPass, SeasonPassOverrides::xpPerWave),
            arena -> safeSeasonPass(arena, SeasonPassOverrides::xpPerWave));
        return resolveIntOverride(override, GameMechanicsConfig.SEASON_XP_PER_WAVE.get(), "SEASON_XP_PER_WAVE");
    }

    public int getSeasonXpPerBoss(UUID questId) {
        Integer override = resolveOverride(questId,
            runtime -> resolveNested(runtime.seasonPass, SeasonPassOverrides::xpPerBoss),
            arena -> safeSeasonPass(arena, SeasonPassOverrides::xpPerBoss));
        return resolveIntOverride(override, GameMechanicsConfig.SEASON_XP_PER_BOSS.get(), "SEASON_XP_PER_BOSS");
    }

    public int getSeasonXpDailyChallenge(UUID questId) {
        Integer override = resolveOverride(questId,
            runtime -> resolveNested(runtime.seasonPass, SeasonPassOverrides::xpDailyChallenge),
            arena -> safeSeasonPass(arena, SeasonPassOverrides::xpDailyChallenge));
        return resolveIntOverride(override, GameMechanicsConfig.SEASON_XP_DAILY_CHALLENGE.get(), "SEASON_XP_DAILY_CHALLENGE");
    }

    public int getSeasonXpWeeklyChallenge(UUID questId) {
        Integer override = resolveOverride(questId,
            runtime -> resolveNested(runtime.seasonPass, SeasonPassOverrides::xpWeeklyChallenge),
            arena -> safeSeasonPass(arena, SeasonPassOverrides::xpWeeklyChallenge));
        return resolveIntOverride(override, GameMechanicsConfig.SEASON_XP_WEEKLY_CHALLENGE.get(), "SEASON_XP_WEEKLY_CHALLENGE");
    }

    public int getSeasonXpStyleS(UUID questId) {
        Integer override = resolveOverride(questId,
            runtime -> resolveNested(runtime.seasonPass, SeasonPassOverrides::xpStyleS),
            arena -> safeSeasonPass(arena, SeasonPassOverrides::xpStyleS));
        return resolveIntOverride(override, GameMechanicsConfig.SEASON_XP_STYLE_S.get(), "SEASON_XP_STYLE_S");
    }

    public int getSeasonXpStyleSS(UUID questId) {
        Integer override = resolveOverride(questId,
            runtime -> resolveNested(runtime.seasonPass, SeasonPassOverrides::xpStyleSS),
            arena -> safeSeasonPass(arena, SeasonPassOverrides::xpStyleSS));
        return resolveIntOverride(override, GameMechanicsConfig.SEASON_XP_STYLE_SS.get(), "SEASON_XP_STYLE_SS");
    }

    public int getSeasonXpStyleSSS(UUID questId) {
        Integer override = resolveOverride(questId,
            runtime -> resolveNested(runtime.seasonPass, SeasonPassOverrides::xpStyleSSS),
            arena -> safeSeasonPass(arena, SeasonPassOverrides::xpStyleSSS));
        return resolveIntOverride(override, GameMechanicsConfig.SEASON_XP_STYLE_SSS.get(), "SEASON_XP_STYLE_SSS");
    }

    public int getSeasonXpFlawlessWave(UUID questId) {
        Integer override = resolveOverride(questId,
            runtime -> resolveNested(runtime.seasonPass, SeasonPassOverrides::xpFlawlessWave),
            arena -> safeSeasonPass(arena, SeasonPassOverrides::xpFlawlessWave));
        return resolveIntOverride(override, GameMechanicsConfig.SEASON_XP_FLAWLESS_WAVE.get(), "SEASON_XP_FLAWLESS_WAVE");
    }

    public int getSeasonXpHighCombo50(UUID questId) {
        Integer override = resolveOverride(questId,
            runtime -> resolveNested(runtime.seasonPass, SeasonPassOverrides::xpHighCombo50),
            arena -> safeSeasonPass(arena, SeasonPassOverrides::xpHighCombo50));
        return resolveIntOverride(override, GameMechanicsConfig.SEASON_XP_HIGH_COMBO_50.get(), "SEASON_XP_HIGH_COMBO_50");
    }

    public int getSeasonXpHighCombo100(UUID questId) {
        Integer override = resolveOverride(questId,
            runtime -> resolveNested(runtime.seasonPass, SeasonPassOverrides::xpHighCombo100),
            arena -> safeSeasonPass(arena, SeasonPassOverrides::xpHighCombo100));
        return resolveIntOverride(override, GameMechanicsConfig.SEASON_XP_HIGH_COMBO_100.get(), "SEASON_XP_HIGH_COMBO_100");
    }

    // ========== GUILD CONFIG ==========

    public int getGuildMaxSize(UUID questId) {
        Integer override = resolveOverride(questId,
            runtime -> resolveNested(runtime.guild, GuildOverrides::maxSize),
            arena -> safeGuild(arena, GuildOverrides::maxSize));
        return resolveIntOverride(override, GameMechanicsConfig.GUILD_MAX_SIZE.get(), "GUILD_MAX_SIZE");
    }

    public int getGuildMaxLevel(UUID questId) {
        Integer override = resolveOverride(questId,
            runtime -> resolveNested(runtime.guild, GuildOverrides::maxLevel),
            arena -> safeGuild(arena, GuildOverrides::maxLevel));
        return resolveIntOverride(override, GameMechanicsConfig.GUILD_MAX_LEVEL.get(), "GUILD_MAX_LEVEL");
    }

    public int getGuildCreateCost(UUID questId) {
        Integer override = resolveOverride(questId,
            runtime -> resolveNested(runtime.guild, GuildOverrides::createCost),
            arena -> safeGuild(arena, GuildOverrides::createCost));
        return resolveIntOverride(override, GameMechanicsConfig.GUILD_CREATE_COST.get(), "GUILD_CREATE_COST");
    }

    /**
     * Get guild token bonus multiplier.
     * @return The token bonus multiplier (1.0 = no bonus, 1.1 = 10% bonus)
     */
    public double getGuildBonusTokensMultiplier(UUID questId) {
        Double override = resolveOverride(questId,
            runtime -> resolveNested(runtime.guild, GuildOverrides::bonusTokensMultiplier),
            arena -> safeGuild(arena, GuildOverrides::bonusTokensMultiplier));
        // Default: use the base 5% bonus value
        return resolveDoubleOverride(override, GameMechanicsConfig.GUILD_BONUS_TOKENS_5.get(), "GUILD_BONUS_TOKENS_5");
    }

    /**
     * Get guild XP bonus multiplier.
     * @return The XP bonus multiplier (1.0 = no bonus, 1.1 = 10% bonus)
     */
    public double getGuildBonusXpMultiplier(UUID questId) {
        Double override = resolveOverride(questId,
            runtime -> resolveNested(runtime.guild, GuildOverrides::bonusXpMultiplier),
            arena -> safeGuild(arena, GuildOverrides::bonusXpMultiplier));
        // Default: use the base 5% bonus value
        return resolveDoubleOverride(override, GameMechanicsConfig.GUILD_BONUS_XP_5.get(), "GUILD_BONUS_XP_5");
    }

    // ========== ASCENSION CONFIG ==========

    public int getAscensionMaxLevel(UUID questId) {
        Integer override = resolveOverride(questId,
            runtime -> resolveNested(runtime.ascension, AscensionOverrides::maxLevel),
            arena -> safeAscension(arena, AscensionOverrides::maxLevel));
        return resolveIntOverride(override, GameMechanicsConfig.ASCENSION_MAX_LEVEL.get(), "ASCENSION_MAX_LEVEL");
    }

    public int getAscensionMinPrestige(UUID questId) {
        Integer override = resolveOverride(questId,
            runtime -> resolveNested(runtime.ascension, AscensionOverrides::minPrestige),
            arena -> safeAscension(arena, AscensionOverrides::minPrestige));
        return resolveIntOverride(override, GameMechanicsConfig.ASCENSION_MIN_PRESTIGE.get(), "ASCENSION_MIN_PRESTIGE");
    }

    public int getAscensionCostScaling(UUID questId) {
        Integer override = resolveOverride(questId,
            runtime -> resolveNested(runtime.ascension, AscensionOverrides::costScaling),
            arena -> safeAscension(arena, AscensionOverrides::costScaling));
        return resolveIntOverride(override, GameMechanicsConfig.ASCENSION_COST_SCALING.get(), "ASCENSION_COST_SCALING");
    }

    public double getAscensionDamageBonusPerLevel(UUID questId) {
        Double override = resolveOverride(questId,
            runtime -> resolveNested(runtime.ascension, AscensionOverrides::damageBonusPerLevel),
            arena -> safeAscension(arena, AscensionOverrides::damageBonusPerLevel));
        return resolveDoubleOverride(override, GameMechanicsConfig.ASCENSION_DAMAGE_BONUS_PER_LEVEL.get(), "ASCENSION_DAMAGE_BONUS_PER_LEVEL");
    }

    public double getAscensionDefenseBonusPerLevel(UUID questId) {
        Double override = resolveOverride(questId,
            runtime -> resolveNested(runtime.ascension, AscensionOverrides::defenseBonusPerLevel),
            arena -> safeAscension(arena, AscensionOverrides::defenseBonusPerLevel));
        return resolveDoubleOverride(override, GameMechanicsConfig.ASCENSION_DEFENSE_BONUS_PER_LEVEL.get(), "ASCENSION_DEFENSE_BONUS_PER_LEVEL");
    }

    public double getAscensionTokenMultiplierPerLevel(UUID questId) {
        Double override = resolveOverride(questId,
            runtime -> resolveNested(runtime.ascension, AscensionOverrides::tokenMultiplierPerLevel),
            arena -> safeAscension(arena, AscensionOverrides::tokenMultiplierPerLevel));
        return resolveDoubleOverride(override, GameMechanicsConfig.ASCENSION_TOKEN_MULTIPLIER_PER_LEVEL.get(), "ASCENSION_TOKEN_MULTIPLIER_PER_LEVEL");
    }

    // ========== TENSION SYSTEM CONFIG ==========

    public double getTensionBaseWaveGain(UUID questId) {
        Double override = resolveOverride(questId,
            runtime -> resolveNested(runtime.tension, TensionOverrides::baseWaveGain),
            arena -> safeTension(arena, TensionOverrides::baseWaveGain));
        return resolveDoubleOverride(override, GameMechanicsConfig.TENSION_BASE_WAVE_GAIN.get(), "TENSION_BASE_WAVE_GAIN");
    }

    public double getTensionNoHitBonus(UUID questId) {
        Double override = resolveOverride(questId,
            runtime -> resolveNested(runtime.tension, TensionOverrides::noHitBonus),
            arena -> safeTension(arena, TensionOverrides::noHitBonus));
        return resolveDoubleOverride(override, GameMechanicsConfig.TENSION_NO_HIT_BONUS.get(), "TENSION_NO_HIT_BONUS");
    }

    public double getTensionMinThreshold(UUID questId) {
        Double override = resolveOverride(questId,
            runtime -> resolveNested(runtime.tension, TensionOverrides::minThreshold),
            arena -> safeTension(arena, TensionOverrides::minThreshold));
        return resolveDoubleOverride(override, GameMechanicsConfig.TENSION_MIN_THRESHOLD.get(), "TENSION_MIN_THRESHOLD");
    }

    public double getTensionMaxThreshold(UUID questId) {
        Double override = resolveOverride(questId,
            runtime -> resolveNested(runtime.tension, TensionOverrides::maxThreshold),
            arena -> safeTension(arena, TensionOverrides::maxThreshold));
        return resolveDoubleOverride(override, GameMechanicsConfig.TENSION_MAX_THRESHOLD.get(), "TENSION_MAX_THRESHOLD");
    }

    public int getTensionMinWavesBeforeBoss(UUID questId) {
        Integer override = resolveOverride(questId,
            runtime -> resolveNested(runtime.tension, TensionOverrides::minWavesBeforeBoss),
            arena -> safeTension(arena, TensionOverrides::minWavesBeforeBoss));
        return resolveIntOverride(override, GameMechanicsConfig.TENSION_MIN_WAVES_BEFORE_BOSS.get(), "TENSION_MIN_WAVES_BEFORE_BOSS");
    }

    public int getTensionMaxWavesWithoutBoss(UUID questId) {
        Integer override = resolveOverride(questId,
            runtime -> resolveNested(runtime.tension, TensionOverrides::maxWavesWithoutBoss),
            arena -> safeTension(arena, TensionOverrides::maxWavesWithoutBoss));
        return resolveIntOverride(override, GameMechanicsConfig.TENSION_MAX_WAVES_WITHOUT_BOSS.get(), "TENSION_MAX_WAVES_WITHOUT_BOSS");
    }

    // ========== PERK RARITY CONFIG ==========

    public int getPerkCommonWeight(UUID questId) {
        Integer override = resolveOverride(questId,
            runtime -> resolveNested(runtime.perkRarity, PerkRarityOverrides::commonWeight),
            arena -> safePerkRarity(arena, PerkRarityOverrides::commonWeight));
        return resolveIntOverride(override, GameMechanicsConfig.PERK_COMMON_WEIGHT.get(), "PERK_COMMON_WEIGHT");
    }

    public int getPerkUncommonWeight(UUID questId) {
        Integer override = resolveOverride(questId,
            runtime -> resolveNested(runtime.perkRarity, PerkRarityOverrides::uncommonWeight),
            arena -> safePerkRarity(arena, PerkRarityOverrides::uncommonWeight));
        return resolveIntOverride(override, GameMechanicsConfig.PERK_UNCOMMON_WEIGHT.get(), "PERK_UNCOMMON_WEIGHT");
    }

    public int getPerkRareWeight(UUID questId) {
        Integer override = resolveOverride(questId,
            runtime -> resolveNested(runtime.perkRarity, PerkRarityOverrides::rareWeight),
            arena -> safePerkRarity(arena, PerkRarityOverrides::rareWeight));
        return resolveIntOverride(override, GameMechanicsConfig.PERK_RARE_WEIGHT.get(), "PERK_RARE_WEIGHT");
    }

    public int getPerkEpicWeight(UUID questId) {
        Integer override = resolveOverride(questId,
            runtime -> resolveNested(runtime.perkRarity, PerkRarityOverrides::epicWeight),
            arena -> safePerkRarity(arena, PerkRarityOverrides::epicWeight));
        return resolveIntOverride(override, GameMechanicsConfig.PERK_EPIC_WEIGHT.get(), "PERK_EPIC_WEIGHT");
    }

    public int getPerkLegendaryWeight(UUID questId) {
        Integer override = resolveOverride(questId,
            runtime -> resolveNested(runtime.perkRarity, PerkRarityOverrides::legendaryWeight),
            arena -> safePerkRarity(arena, PerkRarityOverrides::legendaryWeight));
        return resolveIntOverride(override, GameMechanicsConfig.PERK_LEGENDARY_WEIGHT.get(), "PERK_LEGENDARY_WEIGHT");
    }

    public int getPerkChoicesPerSelection(UUID questId) {
        Integer override = resolveOverride(questId,
            runtime -> resolveNested(runtime.perkRarity, PerkRarityOverrides::choicesPerSelection),
            arena -> safePerkRarity(arena, PerkRarityOverrides::choicesPerSelection));
        return resolveIntOverride(override, GameMechanicsConfig.PERK_CHOICES_PER_SELECTION.get(), "PERK_CHOICES_PER_SELECTION");
    }

    public int getPerkRerollCostBase(UUID questId) {
        Integer override = resolveOverride(questId,
            runtime -> resolveNested(runtime.perkRarity, PerkRarityOverrides::rerollCostBase),
            arena -> safePerkRarity(arena, PerkRarityOverrides::rerollCostBase));
        return resolveIntOverride(override, GameMechanicsConfig.PERK_REROLL_COST_BASE.get(), "PERK_REROLL_COST_BASE");
    }

    // ========== CHALLENGE CONFIG ==========

    public int getChallengeDailyCount(UUID questId) {
        Integer override = resolveOverride(questId,
            runtime -> resolveNested(runtime.challenges, ChallengeOverrides::dailyCount),
            arena -> safeChallenges(arena, ChallengeOverrides::dailyCount));
        return resolveIntOverride(override, GameMechanicsConfig.CHALLENGE_DAILY_COUNT.get(), "CHALLENGE_DAILY_COUNT");
    }

    public int getChallengeWeeklyCount(UUID questId) {
        Integer override = resolveOverride(questId,
            runtime -> resolveNested(runtime.challenges, ChallengeOverrides::weeklyCount),
            arena -> safeChallenges(arena, ChallengeOverrides::weeklyCount));
        return resolveIntOverride(override, GameMechanicsConfig.CHALLENGE_WEEKLY_COUNT.get(), "CHALLENGE_WEEKLY_COUNT");
    }

    public int getChallengeDailyTokenReward(UUID questId) {
        Integer override = resolveOverride(questId,
            runtime -> resolveNested(runtime.challenges, ChallengeOverrides::dailyTokenReward),
            arena -> safeChallenges(arena, ChallengeOverrides::dailyTokenReward));
        return resolveIntOverride(override, GameMechanicsConfig.CHALLENGE_DAILY_TOKEN_REWARD.get(), "CHALLENGE_DAILY_TOKEN_REWARD");
    }

    public int getChallengeWeeklyTokenReward(UUID questId) {
        Integer override = resolveOverride(questId,
            runtime -> resolveNested(runtime.challenges, ChallengeOverrides::weeklyTokenReward),
            arena -> safeChallenges(arena, ChallengeOverrides::weeklyTokenReward));
        return resolveIntOverride(override, GameMechanicsConfig.CHALLENGE_WEEKLY_TOKEN_REWARD.get(), "CHALLENGE_WEEKLY_TOKEN_REWARD");
    }

    public int getChallengeDailyXpReward(UUID questId) {
        Integer override = resolveOverride(questId,
            runtime -> resolveNested(runtime.challenges, ChallengeOverrides::dailyXpReward),
            arena -> safeChallenges(arena, ChallengeOverrides::dailyXpReward));
        return resolveIntOverride(override, GameMechanicsConfig.CHALLENGE_DAILY_XP_REWARD.get(), "CHALLENGE_DAILY_XP_REWARD");
    }

    public int getChallengeWeeklyXpReward(UUID questId) {
        Integer override = resolveOverride(questId,
            runtime -> resolveNested(runtime.challenges, ChallengeOverrides::weeklyXpReward),
            arena -> safeChallenges(arena, ChallengeOverrides::weeklyXpReward));
        return resolveIntOverride(override, GameMechanicsConfig.CHALLENGE_WEEKLY_XP_REWARD.get(), "CHALLENGE_WEEKLY_XP_REWARD");
    }

    // ========== COMBO SYSTEM CONFIG ==========

    public int getComboTimeoutTicks(UUID questId) {
        Integer override = resolveOverride(questId,
            runtime -> resolveNested(runtime.combo, ComboOverrides::timeoutTicks),
            arena -> safeCombo(arena, ComboOverrides::timeoutTicks));
        return resolveIntOverride(override, GameMechanicsConfig.COMBO_TIMEOUT_TICKS.get(), "COMBO_TIMEOUT_TICKS");
    }

    public int getComboBasePoints(UUID questId) {
        Integer override = resolveOverride(questId,
            runtime -> resolveNested(runtime.combo, ComboOverrides::basePoints),
            arena -> safeCombo(arena, ComboOverrides::basePoints));
        return resolveIntOverride(override, GameMechanicsConfig.COMBO_BASE_POINTS.get(), "COMBO_BASE_POINTS");
    }

    public double getComboMultiplierIncrement(UUID questId) {
        Double override = resolveOverride(questId,
            runtime -> resolveNested(runtime.combo, ComboOverrides::multiplierIncrement),
            arena -> safeCombo(arena, ComboOverrides::multiplierIncrement));
        return resolveDoubleOverride(override, GameMechanicsConfig.COMBO_MULTIPLIER_INCREMENT.get(), "COMBO_MULTIPLIER_INCREMENT");
    }

    public double getComboMaxMultiplier(UUID questId) {
        Double override = resolveOverride(questId,
            runtime -> resolveNested(runtime.combo, ComboOverrides::maxMultiplier),
            arena -> safeCombo(arena, ComboOverrides::maxMultiplier));
        return resolveDoubleOverride(override, GameMechanicsConfig.COMBO_MAX_MULTIPLIER.get(), "COMBO_MAX_MULTIPLIER");
    }

    // ========== STYLE RANK CONFIG ==========

    public double getStyleDecayRate(UUID questId) {
        Double override = resolveOverride(questId,
            runtime -> resolveNested(runtime.styleRank, StyleRankOverrides::decayRate),
            arena -> safeStyleRank(arena, StyleRankOverrides::decayRate));
        return resolveDoubleOverride(override, GameMechanicsConfig.STYLE_DECAY_RATE.get(), "STYLE_DECAY_RATE");
    }

    public int getStyleDecayDelayTicks(UUID questId) {
        Integer override = resolveOverride(questId,
            runtime -> resolveNested(runtime.styleRank, StyleRankOverrides::decayDelayTicks),
            arena -> safeStyleRank(arena, StyleRankOverrides::decayDelayTicks));
        return resolveIntOverride(override, GameMechanicsConfig.STYLE_DECAY_DELAY_TICKS.get(), "STYLE_DECAY_DELAY_TICKS");
    }

    public int getStyleRankDThreshold(UUID questId) {
        Integer override = resolveOverride(questId,
            runtime -> resolveNested(runtime.styleRank, StyleRankOverrides::dThreshold),
            arena -> safeStyleRank(arena, StyleRankOverrides::dThreshold));
        return resolveIntOverride(override, GameMechanicsConfig.STYLE_RANK_D_THRESHOLD.get(), "STYLE_RANK_D_THRESHOLD");
    }

    public int getStyleRankCThreshold(UUID questId) {
        Integer override = resolveOverride(questId,
            runtime -> resolveNested(runtime.styleRank, StyleRankOverrides::cThreshold),
            arena -> safeStyleRank(arena, StyleRankOverrides::cThreshold));
        return resolveIntOverride(override, GameMechanicsConfig.STYLE_RANK_C_THRESHOLD.get(), "STYLE_RANK_C_THRESHOLD");
    }

    public int getStyleRankBThreshold(UUID questId) {
        Integer override = resolveOverride(questId,
            runtime -> resolveNested(runtime.styleRank, StyleRankOverrides::bThreshold),
            arena -> safeStyleRank(arena, StyleRankOverrides::bThreshold));
        return resolveIntOverride(override, GameMechanicsConfig.STYLE_RANK_B_THRESHOLD.get(), "STYLE_RANK_B_THRESHOLD");
    }

    public int getStyleRankAThreshold(UUID questId) {
        Integer override = resolveOverride(questId,
            runtime -> resolveNested(runtime.styleRank, StyleRankOverrides::aThreshold),
            arena -> safeStyleRank(arena, StyleRankOverrides::aThreshold));
        return resolveIntOverride(override, GameMechanicsConfig.STYLE_RANK_A_THRESHOLD.get(), "STYLE_RANK_A_THRESHOLD");
    }

    public int getStyleRankSThreshold(UUID questId) {
        Integer override = resolveOverride(questId,
            runtime -> resolveNested(runtime.styleRank, StyleRankOverrides::sThreshold),
            arena -> safeStyleRank(arena, StyleRankOverrides::sThreshold));
        return resolveIntOverride(override, GameMechanicsConfig.STYLE_RANK_S_THRESHOLD.get(), "STYLE_RANK_S_THRESHOLD");
    }

    public int getStyleRankSSThreshold(UUID questId) {
        Integer override = resolveOverride(questId,
            runtime -> resolveNested(runtime.styleRank, StyleRankOverrides::ssThreshold),
            arena -> safeStyleRank(arena, StyleRankOverrides::ssThreshold));
        return resolveIntOverride(override, GameMechanicsConfig.STYLE_RANK_SS_THRESHOLD.get(), "STYLE_RANK_SS_THRESHOLD");
    }

    public int getStyleRankSSSThreshold(UUID questId) {
        Integer override = resolveOverride(questId,
            runtime -> resolveNested(runtime.styleRank, StyleRankOverrides::sssThreshold),
            arena -> safeStyleRank(arena, StyleRankOverrides::sssThreshold));
        return resolveIntOverride(override, GameMechanicsConfig.STYLE_RANK_SSS_THRESHOLD.get(), "STYLE_RANK_SSS_THRESHOLD");
    }

    // ========== GLOBAL GETTERS (no quest context) ==========

    /**
     * Get global default value (no override checking).
     * Use when not in a quest context.
     */
    public static int getGlobalSeasonMaxTier() {
        return resolveIntOverride(null, GameMechanicsConfig.SEASON_MAX_TIER.get(), "SEASON_MAX_TIER");
    }

    public static int getGlobalSeasonXpPerTier() {
        return resolveIntOverride(null, GameMechanicsConfig.SEASON_XP_PER_TIER.get(), "SEASON_XP_PER_TIER");
    }

    public static int getGlobalGuildMaxSize() {
        return resolveIntOverride(null, GameMechanicsConfig.GUILD_MAX_SIZE.get(), "GUILD_MAX_SIZE");
    }

    public static int getGlobalGuildMaxLevel() {
        return resolveIntOverride(null, GameMechanicsConfig.GUILD_MAX_LEVEL.get(), "GUILD_MAX_LEVEL");
    }

    public static int getGlobalAscensionMaxLevel() {
        return resolveIntOverride(null, GameMechanicsConfig.ASCENSION_MAX_LEVEL.get(), "ASCENSION_MAX_LEVEL");
    }

    public static int getGlobalAscensionMinPrestige() {
        return resolveIntOverride(null, GameMechanicsConfig.ASCENSION_MIN_PRESTIGE.get(), "ASCENSION_MIN_PRESTIGE");
    }

    // ========== CLEANUP ==========

    /**
     * Clear all overrides for a quest (call when quest ends).
     */
    public void cleanupQuest(UUID questId) {
        questOverrides.remove(questId);
        activeArenaOverrides.remove(questId);
    }

    /**
     * Clear all cached overrides.
     */
    public void clearAll() {
        questOverrides.clear();
        activeArenaOverrides.clear();
        LOGGER.info("[EnduranceConfig] Cleared all config overrides");
    }

    // ========== RUNTIME OVERRIDE CONTAINER ==========

    /**
     * Container for runtime overrides per quest instance.
     * Used for testing specific mobs/mods with custom parameters.
     */
    public static class RuntimeOverrides {
        @Nullable public SeasonPassOverrides seasonPass;
        @Nullable public GuildOverrides guild;
        @Nullable public AscensionOverrides ascension;
        @Nullable public TensionOverrides tension;
        @Nullable public PerkRarityOverrides perkRarity;
        @Nullable public ChallengeOverrides challenges;
        @Nullable public ComboOverrides combo;
        @Nullable public StyleRankOverrides styleRank;

        public RuntimeOverrides() {}

        public RuntimeOverrides withSeasonPass(SeasonPassOverrides seasonPass) {
            this.seasonPass = seasonPass;
            return this;
        }

        public RuntimeOverrides withGuild(GuildOverrides guild) {
            this.guild = guild;
            return this;
        }

        public RuntimeOverrides withAscension(AscensionOverrides ascension) {
            this.ascension = ascension;
            return this;
        }

        public RuntimeOverrides withTension(TensionOverrides tension) {
            this.tension = tension;
            return this;
        }

        public RuntimeOverrides withPerkRarity(PerkRarityOverrides perkRarity) {
            this.perkRarity = perkRarity;
            return this;
        }

        public RuntimeOverrides withChallenges(ChallengeOverrides challenges) {
            this.challenges = challenges;
            return this;
        }

        public RuntimeOverrides withCombo(ComboOverrides combo) {
            this.combo = combo;
            return this;
        }

        public RuntimeOverrides withStyleRank(StyleRankOverrides styleRank) {
            this.styleRank = styleRank;
            return this;
        }

        /**
         * Create a builder for runtime overrides.
         */
        public static RuntimeOverrides builder() {
            return new RuntimeOverrides();
        }

        /**
         * Create fast boss spawn preset for testing.
         */
        public static RuntimeOverrides fastBosses() {
            return new RuntimeOverrides()
                .withTension(TensionOverrides.fastBosses());
        }

        /**
         * Create double XP preset for testing.
         */
        public static RuntimeOverrides doubleXp() {
            return new RuntimeOverrides()
                .withSeasonPass(SeasonPassOverrides.doubleXP());
        }

        /**
         * Create high rarity loot preset for testing.
         */
        public static RuntimeOverrides highRarityLoot() {
            return new RuntimeOverrides()
                .withPerkRarity(PerkRarityOverrides.highRarity());
        }
    }
}
