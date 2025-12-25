package com.devmod.endurance.config;

import com.devmod.arena.policy.ArenaPolicy;
import com.devmod.arena.policy.ArenaPolicy.*;
import com.devmod.config.GameMechanicsConfig;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Centralized configuration manager for Endurance Quest systems.
 *
 * Implements a 3-tier configuration hierarchy:
 * <ol>
 *   <li><b>Runtime Override</b> - Per-instance overrides for testing specific mobs/mods</li>
 *   <li><b>ArenaPolicy Override</b> - Per-arena/quest overrides from JSON files</li>
 *   <li><b>GameMechanicsConfig</b> - Global defaults from NeoForge config</li>
 * </ol>
 *
 * All getter methods follow this fallback pattern:
 * <pre>
 * 1. Check runtime override (if set for this quest/instance)
 * 2. Check ArenaPolicy override (if arena has custom config)
 * 3. Return global default from GameMechanicsConfig
 * </pre>
 *
 * @see GameMechanicsConfig
 * @see ArenaPolicy.GameplayOverrides
 */
public class EnduranceConfigManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(EnduranceConfigManager.class);

    public static final EnduranceConfigManager INSTANCE = new EnduranceConfigManager();

    // Runtime overrides per quest instance (for testing)
    private final Map<UUID, RuntimeOverrides> questOverrides = new ConcurrentHashMap<>();

    // Current active ArenaPolicy override (set when entering an arena)
    private final Map<UUID, GameplayOverrides> activeArenaOverrides = new ConcurrentHashMap<>();

    private EnduranceConfigManager() {}

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
        RuntimeOverrides runtime = questOverrides.get(questId);
        if (runtime != null && runtime.seasonPass != null && runtime.seasonPass.maxTier() != null) {
            return runtime.seasonPass.maxTier();
        }
        GameplayOverrides arena = activeArenaOverrides.get(questId);
        if (arena != null && arena.seasonPass() != null && arena.seasonPass().maxTier() != null) {
            return arena.seasonPass().maxTier();
        }
        return GameMechanicsConfig.SEASON_MAX_TIER.get();
    }

    public int getSeasonXpPerTier(UUID questId) {
        RuntimeOverrides runtime = questOverrides.get(questId);
        if (runtime != null && runtime.seasonPass != null && runtime.seasonPass.xpPerTier() != null) {
            return runtime.seasonPass.xpPerTier();
        }
        GameplayOverrides arena = activeArenaOverrides.get(questId);
        if (arena != null && arena.seasonPass() != null && arena.seasonPass().xpPerTier() != null) {
            return arena.seasonPass().xpPerTier();
        }
        return GameMechanicsConfig.SEASON_XP_PER_TIER.get();
    }

    public int getSeasonXpPerKill(UUID questId) {
        RuntimeOverrides runtime = questOverrides.get(questId);
        if (runtime != null && runtime.seasonPass != null && runtime.seasonPass.xpPerKill() != null) {
            return runtime.seasonPass.xpPerKill();
        }
        GameplayOverrides arena = activeArenaOverrides.get(questId);
        if (arena != null && arena.seasonPass() != null && arena.seasonPass().xpPerKill() != null) {
            return arena.seasonPass().xpPerKill();
        }
        return GameMechanicsConfig.SEASON_XP_PER_KILL.get();
    }

    public int getSeasonXpPerWave(UUID questId) {
        RuntimeOverrides runtime = questOverrides.get(questId);
        if (runtime != null && runtime.seasonPass != null && runtime.seasonPass.xpPerWave() != null) {
            return runtime.seasonPass.xpPerWave();
        }
        GameplayOverrides arena = activeArenaOverrides.get(questId);
        if (arena != null && arena.seasonPass() != null && arena.seasonPass().xpPerWave() != null) {
            return arena.seasonPass().xpPerWave();
        }
        return GameMechanicsConfig.SEASON_XP_PER_WAVE.get();
    }

    public int getSeasonXpPerBoss(UUID questId) {
        RuntimeOverrides runtime = questOverrides.get(questId);
        if (runtime != null && runtime.seasonPass != null && runtime.seasonPass.xpPerBoss() != null) {
            return runtime.seasonPass.xpPerBoss();
        }
        GameplayOverrides arena = activeArenaOverrides.get(questId);
        if (arena != null && arena.seasonPass() != null && arena.seasonPass().xpPerBoss() != null) {
            return arena.seasonPass().xpPerBoss();
        }
        return GameMechanicsConfig.SEASON_XP_PER_BOSS.get();
    }

    public int getSeasonXpDailyChallenge(UUID questId) {
        RuntimeOverrides runtime = questOverrides.get(questId);
        if (runtime != null && runtime.seasonPass != null && runtime.seasonPass.xpDailyChallenge() != null) {
            return runtime.seasonPass.xpDailyChallenge();
        }
        GameplayOverrides arena = activeArenaOverrides.get(questId);
        if (arena != null && arena.seasonPass() != null && arena.seasonPass().xpDailyChallenge() != null) {
            return arena.seasonPass().xpDailyChallenge();
        }
        return GameMechanicsConfig.SEASON_XP_DAILY_CHALLENGE.get();
    }

    public int getSeasonXpWeeklyChallenge(UUID questId) {
        RuntimeOverrides runtime = questOverrides.get(questId);
        if (runtime != null && runtime.seasonPass != null && runtime.seasonPass.xpWeeklyChallenge() != null) {
            return runtime.seasonPass.xpWeeklyChallenge();
        }
        GameplayOverrides arena = activeArenaOverrides.get(questId);
        if (arena != null && arena.seasonPass() != null && arena.seasonPass().xpWeeklyChallenge() != null) {
            return arena.seasonPass().xpWeeklyChallenge();
        }
        return GameMechanicsConfig.SEASON_XP_WEEKLY_CHALLENGE.get();
    }

    public int getSeasonXpStyleS(UUID questId) {
        RuntimeOverrides runtime = questOverrides.get(questId);
        if (runtime != null && runtime.seasonPass != null && runtime.seasonPass.xpStyleS() != null) {
            return runtime.seasonPass.xpStyleS();
        }
        GameplayOverrides arena = activeArenaOverrides.get(questId);
        if (arena != null && arena.seasonPass() != null && arena.seasonPass().xpStyleS() != null) {
            return arena.seasonPass().xpStyleS();
        }
        return GameMechanicsConfig.SEASON_XP_STYLE_S.get();
    }

    public int getSeasonXpStyleSS(UUID questId) {
        RuntimeOverrides runtime = questOverrides.get(questId);
        if (runtime != null && runtime.seasonPass != null && runtime.seasonPass.xpStyleSS() != null) {
            return runtime.seasonPass.xpStyleSS();
        }
        GameplayOverrides arena = activeArenaOverrides.get(questId);
        if (arena != null && arena.seasonPass() != null && arena.seasonPass().xpStyleSS() != null) {
            return arena.seasonPass().xpStyleSS();
        }
        return GameMechanicsConfig.SEASON_XP_STYLE_SS.get();
    }

    public int getSeasonXpStyleSSS(UUID questId) {
        RuntimeOverrides runtime = questOverrides.get(questId);
        if (runtime != null && runtime.seasonPass != null && runtime.seasonPass.xpStyleSSS() != null) {
            return runtime.seasonPass.xpStyleSSS();
        }
        GameplayOverrides arena = activeArenaOverrides.get(questId);
        if (arena != null && arena.seasonPass() != null && arena.seasonPass().xpStyleSSS() != null) {
            return arena.seasonPass().xpStyleSSS();
        }
        return GameMechanicsConfig.SEASON_XP_STYLE_SSS.get();
    }

    public int getSeasonXpFlawlessWave(UUID questId) {
        RuntimeOverrides runtime = questOverrides.get(questId);
        if (runtime != null && runtime.seasonPass != null && runtime.seasonPass.xpFlawlessWave() != null) {
            return runtime.seasonPass.xpFlawlessWave();
        }
        GameplayOverrides arena = activeArenaOverrides.get(questId);
        if (arena != null && arena.seasonPass() != null && arena.seasonPass().xpFlawlessWave() != null) {
            return arena.seasonPass().xpFlawlessWave();
        }
        return GameMechanicsConfig.SEASON_XP_FLAWLESS_WAVE.get();
    }

    public int getSeasonXpHighCombo50(UUID questId) {
        RuntimeOverrides runtime = questOverrides.get(questId);
        if (runtime != null && runtime.seasonPass != null && runtime.seasonPass.xpHighCombo50() != null) {
            return runtime.seasonPass.xpHighCombo50();
        }
        GameplayOverrides arena = activeArenaOverrides.get(questId);
        if (arena != null && arena.seasonPass() != null && arena.seasonPass().xpHighCombo50() != null) {
            return arena.seasonPass().xpHighCombo50();
        }
        return GameMechanicsConfig.SEASON_XP_HIGH_COMBO_50.get();
    }

    public int getSeasonXpHighCombo100(UUID questId) {
        RuntimeOverrides runtime = questOverrides.get(questId);
        if (runtime != null && runtime.seasonPass != null && runtime.seasonPass.xpHighCombo100() != null) {
            return runtime.seasonPass.xpHighCombo100();
        }
        GameplayOverrides arena = activeArenaOverrides.get(questId);
        if (arena != null && arena.seasonPass() != null && arena.seasonPass().xpHighCombo100() != null) {
            return arena.seasonPass().xpHighCombo100();
        }
        return GameMechanicsConfig.SEASON_XP_HIGH_COMBO_100.get();
    }

    // ========== GUILD CONFIG ==========

    public int getGuildMaxSize(UUID questId) {
        RuntimeOverrides runtime = questOverrides.get(questId);
        if (runtime != null && runtime.guild != null && runtime.guild.maxSize() != null) {
            return runtime.guild.maxSize();
        }
        GameplayOverrides arena = activeArenaOverrides.get(questId);
        if (arena != null && arena.guild() != null && arena.guild().maxSize() != null) {
            return arena.guild().maxSize();
        }
        return GameMechanicsConfig.GUILD_MAX_SIZE.get();
    }

    public int getGuildMaxLevel(UUID questId) {
        RuntimeOverrides runtime = questOverrides.get(questId);
        if (runtime != null && runtime.guild != null && runtime.guild.maxLevel() != null) {
            return runtime.guild.maxLevel();
        }
        GameplayOverrides arena = activeArenaOverrides.get(questId);
        if (arena != null && arena.guild() != null && arena.guild().maxLevel() != null) {
            return arena.guild().maxLevel();
        }
        return GameMechanicsConfig.GUILD_MAX_LEVEL.get();
    }

    public int getGuildCreateCost(UUID questId) {
        RuntimeOverrides runtime = questOverrides.get(questId);
        if (runtime != null && runtime.guild != null && runtime.guild.createCost() != null) {
            return runtime.guild.createCost();
        }
        GameplayOverrides arena = activeArenaOverrides.get(questId);
        if (arena != null && arena.guild() != null && arena.guild().createCost() != null) {
            return arena.guild().createCost();
        }
        return GameMechanicsConfig.GUILD_CREATE_COST.get();
    }

    /**
     * Get guild token bonus multiplier.
     * @return The token bonus multiplier (1.0 = no bonus, 1.1 = 10% bonus)
     */
    public double getGuildBonusTokensMultiplier(UUID questId) {
        RuntimeOverrides runtime = questOverrides.get(questId);
        if (runtime != null && runtime.guild != null && runtime.guild.bonusTokensMultiplier() != null) {
            return runtime.guild.bonusTokensMultiplier();
        }
        GameplayOverrides arena = activeArenaOverrides.get(questId);
        if (arena != null && arena.guild() != null && arena.guild().bonusTokensMultiplier() != null) {
            return arena.guild().bonusTokensMultiplier();
        }
        // Default: use the base 5% bonus value
        return GameMechanicsConfig.GUILD_BONUS_TOKENS_5.get();
    }

    /**
     * Get guild XP bonus multiplier.
     * @return The XP bonus multiplier (1.0 = no bonus, 1.1 = 10% bonus)
     */
    public double getGuildBonusXpMultiplier(UUID questId) {
        RuntimeOverrides runtime = questOverrides.get(questId);
        if (runtime != null && runtime.guild != null && runtime.guild.bonusXpMultiplier() != null) {
            return runtime.guild.bonusXpMultiplier();
        }
        GameplayOverrides arena = activeArenaOverrides.get(questId);
        if (arena != null && arena.guild() != null && arena.guild().bonusXpMultiplier() != null) {
            return arena.guild().bonusXpMultiplier();
        }
        // Default: use the base 5% bonus value
        return GameMechanicsConfig.GUILD_BONUS_XP_5.get();
    }

    // ========== ASCENSION CONFIG ==========

    public int getAscensionMaxLevel(UUID questId) {
        RuntimeOverrides runtime = questOverrides.get(questId);
        if (runtime != null && runtime.ascension != null && runtime.ascension.maxLevel() != null) {
            return runtime.ascension.maxLevel();
        }
        GameplayOverrides arena = activeArenaOverrides.get(questId);
        if (arena != null && arena.ascension() != null && arena.ascension().maxLevel() != null) {
            return arena.ascension().maxLevel();
        }
        return GameMechanicsConfig.ASCENSION_MAX_LEVEL.get();
    }

    public int getAscensionMinPrestige(UUID questId) {
        RuntimeOverrides runtime = questOverrides.get(questId);
        if (runtime != null && runtime.ascension != null && runtime.ascension.minPrestige() != null) {
            return runtime.ascension.minPrestige();
        }
        GameplayOverrides arena = activeArenaOverrides.get(questId);
        if (arena != null && arena.ascension() != null && arena.ascension().minPrestige() != null) {
            return arena.ascension().minPrestige();
        }
        return GameMechanicsConfig.ASCENSION_MIN_PRESTIGE.get();
    }

    public int getAscensionCostScaling(UUID questId) {
        RuntimeOverrides runtime = questOverrides.get(questId);
        if (runtime != null && runtime.ascension != null && runtime.ascension.costScaling() != null) {
            return runtime.ascension.costScaling();
        }
        GameplayOverrides arena = activeArenaOverrides.get(questId);
        if (arena != null && arena.ascension() != null && arena.ascension().costScaling() != null) {
            return arena.ascension().costScaling();
        }
        return GameMechanicsConfig.ASCENSION_COST_SCALING.get();
    }

    public double getAscensionDamageBonusPerLevel(UUID questId) {
        RuntimeOverrides runtime = questOverrides.get(questId);
        if (runtime != null && runtime.ascension != null && runtime.ascension.damageBonusPerLevel() != null) {
            return runtime.ascension.damageBonusPerLevel();
        }
        GameplayOverrides arena = activeArenaOverrides.get(questId);
        if (arena != null && arena.ascension() != null && arena.ascension().damageBonusPerLevel() != null) {
            return arena.ascension().damageBonusPerLevel();
        }
        return GameMechanicsConfig.ASCENSION_DAMAGE_BONUS_PER_LEVEL.get();
    }

    public double getAscensionDefenseBonusPerLevel(UUID questId) {
        RuntimeOverrides runtime = questOverrides.get(questId);
        if (runtime != null && runtime.ascension != null && runtime.ascension.defenseBonusPerLevel() != null) {
            return runtime.ascension.defenseBonusPerLevel();
        }
        GameplayOverrides arena = activeArenaOverrides.get(questId);
        if (arena != null && arena.ascension() != null && arena.ascension().defenseBonusPerLevel() != null) {
            return arena.ascension().defenseBonusPerLevel();
        }
        return GameMechanicsConfig.ASCENSION_DEFENSE_BONUS_PER_LEVEL.get();
    }

    public double getAscensionTokenMultiplierPerLevel(UUID questId) {
        RuntimeOverrides runtime = questOverrides.get(questId);
        if (runtime != null && runtime.ascension != null && runtime.ascension.tokenMultiplierPerLevel() != null) {
            return runtime.ascension.tokenMultiplierPerLevel();
        }
        GameplayOverrides arena = activeArenaOverrides.get(questId);
        if (arena != null && arena.ascension() != null && arena.ascension().tokenMultiplierPerLevel() != null) {
            return arena.ascension().tokenMultiplierPerLevel();
        }
        return GameMechanicsConfig.ASCENSION_TOKEN_MULTIPLIER_PER_LEVEL.get();
    }

    // ========== TENSION SYSTEM CONFIG ==========

    public double getTensionBaseWaveGain(UUID questId) {
        RuntimeOverrides runtime = questOverrides.get(questId);
        if (runtime != null && runtime.tension != null && runtime.tension.baseWaveGain() != null) {
            return runtime.tension.baseWaveGain();
        }
        GameplayOverrides arena = activeArenaOverrides.get(questId);
        if (arena != null && arena.tension() != null && arena.tension().baseWaveGain() != null) {
            return arena.tension().baseWaveGain();
        }
        return GameMechanicsConfig.TENSION_BASE_WAVE_GAIN.get();
    }

    public double getTensionNoHitBonus(UUID questId) {
        RuntimeOverrides runtime = questOverrides.get(questId);
        if (runtime != null && runtime.tension != null && runtime.tension.noHitBonus() != null) {
            return runtime.tension.noHitBonus();
        }
        GameplayOverrides arena = activeArenaOverrides.get(questId);
        if (arena != null && arena.tension() != null && arena.tension().noHitBonus() != null) {
            return arena.tension().noHitBonus();
        }
        return GameMechanicsConfig.TENSION_NO_HIT_BONUS.get();
    }

    public double getTensionMinThreshold(UUID questId) {
        RuntimeOverrides runtime = questOverrides.get(questId);
        if (runtime != null && runtime.tension != null && runtime.tension.minThreshold() != null) {
            return runtime.tension.minThreshold();
        }
        GameplayOverrides arena = activeArenaOverrides.get(questId);
        if (arena != null && arena.tension() != null && arena.tension().minThreshold() != null) {
            return arena.tension().minThreshold();
        }
        return GameMechanicsConfig.TENSION_MIN_THRESHOLD.get();
    }

    public double getTensionMaxThreshold(UUID questId) {
        RuntimeOverrides runtime = questOverrides.get(questId);
        if (runtime != null && runtime.tension != null && runtime.tension.maxThreshold() != null) {
            return runtime.tension.maxThreshold();
        }
        GameplayOverrides arena = activeArenaOverrides.get(questId);
        if (arena != null && arena.tension() != null && arena.tension().maxThreshold() != null) {
            return arena.tension().maxThreshold();
        }
        return GameMechanicsConfig.TENSION_MAX_THRESHOLD.get();
    }

    public int getTensionMinWavesBeforeBoss(UUID questId) {
        RuntimeOverrides runtime = questOverrides.get(questId);
        if (runtime != null && runtime.tension != null && runtime.tension.minWavesBeforeBoss() != null) {
            return runtime.tension.minWavesBeforeBoss();
        }
        GameplayOverrides arena = activeArenaOverrides.get(questId);
        if (arena != null && arena.tension() != null && arena.tension().minWavesBeforeBoss() != null) {
            return arena.tension().minWavesBeforeBoss();
        }
        return GameMechanicsConfig.TENSION_MIN_WAVES_BEFORE_BOSS.get();
    }

    public int getTensionMaxWavesWithoutBoss(UUID questId) {
        RuntimeOverrides runtime = questOverrides.get(questId);
        if (runtime != null && runtime.tension != null && runtime.tension.maxWavesWithoutBoss() != null) {
            return runtime.tension.maxWavesWithoutBoss();
        }
        GameplayOverrides arena = activeArenaOverrides.get(questId);
        if (arena != null && arena.tension() != null && arena.tension().maxWavesWithoutBoss() != null) {
            return arena.tension().maxWavesWithoutBoss();
        }
        return GameMechanicsConfig.TENSION_MAX_WAVES_WITHOUT_BOSS.get();
    }

    // ========== PERK RARITY CONFIG ==========

    public int getPerkCommonWeight(UUID questId) {
        RuntimeOverrides runtime = questOverrides.get(questId);
        if (runtime != null && runtime.perkRarity != null && runtime.perkRarity.commonWeight() != null) {
            return runtime.perkRarity.commonWeight();
        }
        GameplayOverrides arena = activeArenaOverrides.get(questId);
        if (arena != null && arena.perkRarity() != null && arena.perkRarity().commonWeight() != null) {
            return arena.perkRarity().commonWeight();
        }
        return GameMechanicsConfig.PERK_COMMON_WEIGHT.get();
    }

    public int getPerkUncommonWeight(UUID questId) {
        RuntimeOverrides runtime = questOverrides.get(questId);
        if (runtime != null && runtime.perkRarity != null && runtime.perkRarity.uncommonWeight() != null) {
            return runtime.perkRarity.uncommonWeight();
        }
        GameplayOverrides arena = activeArenaOverrides.get(questId);
        if (arena != null && arena.perkRarity() != null && arena.perkRarity().uncommonWeight() != null) {
            return arena.perkRarity().uncommonWeight();
        }
        return GameMechanicsConfig.PERK_UNCOMMON_WEIGHT.get();
    }

    public int getPerkRareWeight(UUID questId) {
        RuntimeOverrides runtime = questOverrides.get(questId);
        if (runtime != null && runtime.perkRarity != null && runtime.perkRarity.rareWeight() != null) {
            return runtime.perkRarity.rareWeight();
        }
        GameplayOverrides arena = activeArenaOverrides.get(questId);
        if (arena != null && arena.perkRarity() != null && arena.perkRarity().rareWeight() != null) {
            return arena.perkRarity().rareWeight();
        }
        return GameMechanicsConfig.PERK_RARE_WEIGHT.get();
    }

    public int getPerkEpicWeight(UUID questId) {
        RuntimeOverrides runtime = questOverrides.get(questId);
        if (runtime != null && runtime.perkRarity != null && runtime.perkRarity.epicWeight() != null) {
            return runtime.perkRarity.epicWeight();
        }
        GameplayOverrides arena = activeArenaOverrides.get(questId);
        if (arena != null && arena.perkRarity() != null && arena.perkRarity().epicWeight() != null) {
            return arena.perkRarity().epicWeight();
        }
        return GameMechanicsConfig.PERK_EPIC_WEIGHT.get();
    }

    public int getPerkLegendaryWeight(UUID questId) {
        RuntimeOverrides runtime = questOverrides.get(questId);
        if (runtime != null && runtime.perkRarity != null && runtime.perkRarity.legendaryWeight() != null) {
            return runtime.perkRarity.legendaryWeight();
        }
        GameplayOverrides arena = activeArenaOverrides.get(questId);
        if (arena != null && arena.perkRarity() != null && arena.perkRarity().legendaryWeight() != null) {
            return arena.perkRarity().legendaryWeight();
        }
        return GameMechanicsConfig.PERK_LEGENDARY_WEIGHT.get();
    }

    public int getPerkChoicesPerSelection(UUID questId) {
        RuntimeOverrides runtime = questOverrides.get(questId);
        if (runtime != null && runtime.perkRarity != null && runtime.perkRarity.choicesPerSelection() != null) {
            return runtime.perkRarity.choicesPerSelection();
        }
        GameplayOverrides arena = activeArenaOverrides.get(questId);
        if (arena != null && arena.perkRarity() != null && arena.perkRarity().choicesPerSelection() != null) {
            return arena.perkRarity().choicesPerSelection();
        }
        return GameMechanicsConfig.PERK_CHOICES_PER_SELECTION.get();
    }

    public int getPerkRerollCostBase(UUID questId) {
        RuntimeOverrides runtime = questOverrides.get(questId);
        if (runtime != null && runtime.perkRarity != null && runtime.perkRarity.rerollCostBase() != null) {
            return runtime.perkRarity.rerollCostBase();
        }
        GameplayOverrides arena = activeArenaOverrides.get(questId);
        if (arena != null && arena.perkRarity() != null && arena.perkRarity().rerollCostBase() != null) {
            return arena.perkRarity().rerollCostBase();
        }
        return GameMechanicsConfig.PERK_REROLL_COST_BASE.get();
    }

    // ========== CHALLENGE CONFIG ==========

    public int getChallengeDailyCount(UUID questId) {
        RuntimeOverrides runtime = questOverrides.get(questId);
        if (runtime != null && runtime.challenges != null && runtime.challenges.dailyCount() != null) {
            return runtime.challenges.dailyCount();
        }
        GameplayOverrides arena = activeArenaOverrides.get(questId);
        if (arena != null && arena.challenges() != null && arena.challenges().dailyCount() != null) {
            return arena.challenges().dailyCount();
        }
        return GameMechanicsConfig.CHALLENGE_DAILY_COUNT.get();
    }

    public int getChallengeWeeklyCount(UUID questId) {
        RuntimeOverrides runtime = questOverrides.get(questId);
        if (runtime != null && runtime.challenges != null && runtime.challenges.weeklyCount() != null) {
            return runtime.challenges.weeklyCount();
        }
        GameplayOverrides arena = activeArenaOverrides.get(questId);
        if (arena != null && arena.challenges() != null && arena.challenges().weeklyCount() != null) {
            return arena.challenges().weeklyCount();
        }
        return GameMechanicsConfig.CHALLENGE_WEEKLY_COUNT.get();
    }

    public int getChallengeDailyTokenReward(UUID questId) {
        RuntimeOverrides runtime = questOverrides.get(questId);
        if (runtime != null && runtime.challenges != null && runtime.challenges.dailyTokenReward() != null) {
            return runtime.challenges.dailyTokenReward();
        }
        GameplayOverrides arena = activeArenaOverrides.get(questId);
        if (arena != null && arena.challenges() != null && arena.challenges().dailyTokenReward() != null) {
            return arena.challenges().dailyTokenReward();
        }
        return GameMechanicsConfig.CHALLENGE_DAILY_TOKEN_REWARD.get();
    }

    public int getChallengeWeeklyTokenReward(UUID questId) {
        RuntimeOverrides runtime = questOverrides.get(questId);
        if (runtime != null && runtime.challenges != null && runtime.challenges.weeklyTokenReward() != null) {
            return runtime.challenges.weeklyTokenReward();
        }
        GameplayOverrides arena = activeArenaOverrides.get(questId);
        if (arena != null && arena.challenges() != null && arena.challenges().weeklyTokenReward() != null) {
            return arena.challenges().weeklyTokenReward();
        }
        return GameMechanicsConfig.CHALLENGE_WEEKLY_TOKEN_REWARD.get();
    }

    public int getChallengeDailyXpReward(UUID questId) {
        RuntimeOverrides runtime = questOverrides.get(questId);
        if (runtime != null && runtime.challenges != null && runtime.challenges.dailyXpReward() != null) {
            return runtime.challenges.dailyXpReward();
        }
        GameplayOverrides arena = activeArenaOverrides.get(questId);
        if (arena != null && arena.challenges() != null && arena.challenges().dailyXpReward() != null) {
            return arena.challenges().dailyXpReward();
        }
        return GameMechanicsConfig.CHALLENGE_DAILY_XP_REWARD.get();
    }

    public int getChallengeWeeklyXpReward(UUID questId) {
        RuntimeOverrides runtime = questOverrides.get(questId);
        if (runtime != null && runtime.challenges != null && runtime.challenges.weeklyXpReward() != null) {
            return runtime.challenges.weeklyXpReward();
        }
        GameplayOverrides arena = activeArenaOverrides.get(questId);
        if (arena != null && arena.challenges() != null && arena.challenges().weeklyXpReward() != null) {
            return arena.challenges().weeklyXpReward();
        }
        return GameMechanicsConfig.CHALLENGE_WEEKLY_XP_REWARD.get();
    }

    // ========== COMBO SYSTEM CONFIG ==========

    public int getComboTimeoutTicks(UUID questId) {
        RuntimeOverrides runtime = questOverrides.get(questId);
        if (runtime != null && runtime.combo != null && runtime.combo.timeoutTicks() != null) {
            return runtime.combo.timeoutTicks();
        }
        GameplayOverrides arena = activeArenaOverrides.get(questId);
        if (arena != null && arena.combo() != null && arena.combo().timeoutTicks() != null) {
            return arena.combo().timeoutTicks();
        }
        return GameMechanicsConfig.COMBO_TIMEOUT_TICKS.get();
    }

    public int getComboBasePoints(UUID questId) {
        RuntimeOverrides runtime = questOverrides.get(questId);
        if (runtime != null && runtime.combo != null && runtime.combo.basePoints() != null) {
            return runtime.combo.basePoints();
        }
        GameplayOverrides arena = activeArenaOverrides.get(questId);
        if (arena != null && arena.combo() != null && arena.combo().basePoints() != null) {
            return arena.combo().basePoints();
        }
        return GameMechanicsConfig.COMBO_BASE_POINTS.get();
    }

    public double getComboMultiplierIncrement(UUID questId) {
        RuntimeOverrides runtime = questOverrides.get(questId);
        if (runtime != null && runtime.combo != null && runtime.combo.multiplierIncrement() != null) {
            return runtime.combo.multiplierIncrement();
        }
        GameplayOverrides arena = activeArenaOverrides.get(questId);
        if (arena != null && arena.combo() != null && arena.combo().multiplierIncrement() != null) {
            return arena.combo().multiplierIncrement();
        }
        return GameMechanicsConfig.COMBO_MULTIPLIER_INCREMENT.get();
    }

    public double getComboMaxMultiplier(UUID questId) {
        RuntimeOverrides runtime = questOverrides.get(questId);
        if (runtime != null && runtime.combo != null && runtime.combo.maxMultiplier() != null) {
            return runtime.combo.maxMultiplier();
        }
        GameplayOverrides arena = activeArenaOverrides.get(questId);
        if (arena != null && arena.combo() != null && arena.combo().maxMultiplier() != null) {
            return arena.combo().maxMultiplier();
        }
        return GameMechanicsConfig.COMBO_MAX_MULTIPLIER.get();
    }

    // ========== STYLE RANK CONFIG ==========

    public double getStyleDecayRate(UUID questId) {
        RuntimeOverrides runtime = questOverrides.get(questId);
        if (runtime != null && runtime.styleRank != null && runtime.styleRank.decayRate() != null) {
            return runtime.styleRank.decayRate();
        }
        GameplayOverrides arena = activeArenaOverrides.get(questId);
        if (arena != null && arena.styleRank() != null && arena.styleRank().decayRate() != null) {
            return arena.styleRank().decayRate();
        }
        return GameMechanicsConfig.STYLE_DECAY_RATE.get();
    }

    public int getStyleDecayDelayTicks(UUID questId) {
        RuntimeOverrides runtime = questOverrides.get(questId);
        if (runtime != null && runtime.styleRank != null && runtime.styleRank.decayDelayTicks() != null) {
            return runtime.styleRank.decayDelayTicks();
        }
        GameplayOverrides arena = activeArenaOverrides.get(questId);
        if (arena != null && arena.styleRank() != null && arena.styleRank().decayDelayTicks() != null) {
            return arena.styleRank().decayDelayTicks();
        }
        return GameMechanicsConfig.STYLE_DECAY_DELAY_TICKS.get();
    }

    public int getStyleRankDThreshold(UUID questId) {
        RuntimeOverrides runtime = questOverrides.get(questId);
        if (runtime != null && runtime.styleRank != null && runtime.styleRank.dThreshold() != null) {
            return runtime.styleRank.dThreshold();
        }
        GameplayOverrides arena = activeArenaOverrides.get(questId);
        if (arena != null && arena.styleRank() != null && arena.styleRank().dThreshold() != null) {
            return arena.styleRank().dThreshold();
        }
        return GameMechanicsConfig.STYLE_RANK_D_THRESHOLD.get();
    }

    public int getStyleRankCThreshold(UUID questId) {
        RuntimeOverrides runtime = questOverrides.get(questId);
        if (runtime != null && runtime.styleRank != null && runtime.styleRank.cThreshold() != null) {
            return runtime.styleRank.cThreshold();
        }
        GameplayOverrides arena = activeArenaOverrides.get(questId);
        if (arena != null && arena.styleRank() != null && arena.styleRank().cThreshold() != null) {
            return arena.styleRank().cThreshold();
        }
        return GameMechanicsConfig.STYLE_RANK_C_THRESHOLD.get();
    }

    public int getStyleRankBThreshold(UUID questId) {
        RuntimeOverrides runtime = questOverrides.get(questId);
        if (runtime != null && runtime.styleRank != null && runtime.styleRank.bThreshold() != null) {
            return runtime.styleRank.bThreshold();
        }
        GameplayOverrides arena = activeArenaOverrides.get(questId);
        if (arena != null && arena.styleRank() != null && arena.styleRank().bThreshold() != null) {
            return arena.styleRank().bThreshold();
        }
        return GameMechanicsConfig.STYLE_RANK_B_THRESHOLD.get();
    }

    public int getStyleRankAThreshold(UUID questId) {
        RuntimeOverrides runtime = questOverrides.get(questId);
        if (runtime != null && runtime.styleRank != null && runtime.styleRank.aThreshold() != null) {
            return runtime.styleRank.aThreshold();
        }
        GameplayOverrides arena = activeArenaOverrides.get(questId);
        if (arena != null && arena.styleRank() != null && arena.styleRank().aThreshold() != null) {
            return arena.styleRank().aThreshold();
        }
        return GameMechanicsConfig.STYLE_RANK_A_THRESHOLD.get();
    }

    public int getStyleRankSThreshold(UUID questId) {
        RuntimeOverrides runtime = questOverrides.get(questId);
        if (runtime != null && runtime.styleRank != null && runtime.styleRank.sThreshold() != null) {
            return runtime.styleRank.sThreshold();
        }
        GameplayOverrides arena = activeArenaOverrides.get(questId);
        if (arena != null && arena.styleRank() != null && arena.styleRank().sThreshold() != null) {
            return arena.styleRank().sThreshold();
        }
        return GameMechanicsConfig.STYLE_RANK_S_THRESHOLD.get();
    }

    public int getStyleRankSSThreshold(UUID questId) {
        RuntimeOverrides runtime = questOverrides.get(questId);
        if (runtime != null && runtime.styleRank != null && runtime.styleRank.ssThreshold() != null) {
            return runtime.styleRank.ssThreshold();
        }
        GameplayOverrides arena = activeArenaOverrides.get(questId);
        if (arena != null && arena.styleRank() != null && arena.styleRank().ssThreshold() != null) {
            return arena.styleRank().ssThreshold();
        }
        return GameMechanicsConfig.STYLE_RANK_SS_THRESHOLD.get();
    }

    public int getStyleRankSSSThreshold(UUID questId) {
        RuntimeOverrides runtime = questOverrides.get(questId);
        if (runtime != null && runtime.styleRank != null && runtime.styleRank.sssThreshold() != null) {
            return runtime.styleRank.sssThreshold();
        }
        GameplayOverrides arena = activeArenaOverrides.get(questId);
        if (arena != null && arena.styleRank() != null && arena.styleRank().sssThreshold() != null) {
            return arena.styleRank().sssThreshold();
        }
        return GameMechanicsConfig.STYLE_RANK_SSS_THRESHOLD.get();
    }

    // ========== GLOBAL GETTERS (no quest context) ==========

    /**
     * Get global default value (no override checking).
     * Use when not in a quest context.
     */
    public static int getGlobalSeasonMaxTier() {
        return GameMechanicsConfig.SEASON_MAX_TIER.get();
    }

    public static int getGlobalSeasonXpPerTier() {
        return GameMechanicsConfig.SEASON_XP_PER_TIER.get();
    }

    public static int getGlobalGuildMaxSize() {
        return GameMechanicsConfig.GUILD_MAX_SIZE.get();
    }

    public static int getGlobalGuildMaxLevel() {
        return GameMechanicsConfig.GUILD_MAX_LEVEL.get();
    }

    public static int getGlobalAscensionMaxLevel() {
        return GameMechanicsConfig.ASCENSION_MAX_LEVEL.get();
    }

    public static int getGlobalAscensionMinPrestige() {
        return GameMechanicsConfig.ASCENSION_MIN_PRESTIGE.get();
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
