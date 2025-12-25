package com.devmod.network;

import java.util.Objects;
import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;

import com.devmod.config.GameMechanicsConfig;

/**
 * Server-to-client payload for synchronizing GameMechanicsConfig values.
 *
 * <p>Supports two sync modes:
 * <ul>
 *   <li><b>Global sync</b>: questId is null, syncs global config defaults on login</li>
 *   <li><b>Quest sync</b>: questId is set, syncs per-quest overrides when entering a quest</li>
 * </ul>
 *
 * @see com.devmod.config.GameMechanicsConfig
 * @see com.devmod.endurance.config.EnduranceConfigManager
 */
public record GameMechanicsSyncPayload(
    CompoundTag mechanicsConfig,
    @Nullable UUID questId,
    @Nullable CompoundTag questOverrides
) implements CustomPacketPayload {

    public static final Type<GameMechanicsSyncPayload> TYPE = new Type<>(
        Objects.requireNonNull(ResourceLocation.fromNamespaceAndPath("devmod", "game_mechanics_sync"))
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, GameMechanicsSyncPayload> STREAM_CODEC = StreamCodec.of(
        GameMechanicsSyncPayload::encode,
        GameMechanicsSyncPayload::decode
    );

    private static void encode(RegistryFriendlyByteBuf buffer, GameMechanicsSyncPayload payload) {
        ByteBufCodecs.COMPOUND_TAG.encode(buffer, Objects.requireNonNull(payload.mechanicsConfig()));

        // Write optional questId
        boolean hasQuestId = payload.questId() != null;
        buffer.writeBoolean(hasQuestId);
        if (hasQuestId) {
            buffer.writeUUID(payload.questId());
        }

        // Write optional questOverrides
        boolean hasOverrides = payload.questOverrides() != null;
        buffer.writeBoolean(hasOverrides);
        if (hasOverrides) {
            ByteBufCodecs.COMPOUND_TAG.encode(buffer, payload.questOverrides());
        }
    }

    private static GameMechanicsSyncPayload decode(RegistryFriendlyByteBuf buffer) {
        CompoundTag mechanicsConfig = Objects.requireNonNull(ByteBufCodecs.COMPOUND_TAG.decode(buffer));

        // Read optional questId
        UUID questId = null;
        if (buffer.readBoolean()) {
            questId = buffer.readUUID();
        }

        // Read optional questOverrides
        CompoundTag questOverrides = null;
        if (buffer.readBoolean()) {
            questOverrides = ByteBufCodecs.COMPOUND_TAG.decode(buffer);
        }

        return new GameMechanicsSyncPayload(mechanicsConfig, questId, questOverrides);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    // ============================================================================
    // FACTORY METHODS
    // ============================================================================

    /**
     * Creates a payload from the current global GameMechanicsConfig.
     * Used on player login to sync defaults.
     */
    public static GameMechanicsSyncPayload fromGlobalConfig() {
        CompoundTag configTag = serializeGlobalConfig();
        return new GameMechanicsSyncPayload(configTag, null, null);
    }

    /**
     * Creates a payload for a specific quest with overrides.
     * Used when a player enters a quest with arena policy overrides.
     */
    public static GameMechanicsSyncPayload forQuest(UUID questId, CompoundTag overrides) {
        CompoundTag globalConfig = serializeGlobalConfig();
        return new GameMechanicsSyncPayload(globalConfig, questId, overrides);
    }

    // ============================================================================
    // SERIALIZATION
    // ============================================================================

    /**
     * Serializes all GameMechanicsConfig values to a CompoundTag.
     */
    private static CompoundTag serializeGlobalConfig() {
        CompoundTag tag = new CompoundTag();

        // Execution System
        CompoundTag execution = new CompoundTag();
        execution.putDouble("hpThreshold", GameMechanicsConfig.EXECUTION_HP_THRESHOLD.get());
        execution.putInt("durationTicks", GameMechanicsConfig.EXECUTION_DURATION_TICKS.get());
        execution.putInt("cooldownTicks", GameMechanicsConfig.EXECUTION_COOLDOWN_TICKS.get());
        execution.putInt("styleReward", GameMechanicsConfig.EXECUTION_STYLE_REWARD.get());
        execution.putDouble("hpRegenPercent", GameMechanicsConfig.EXECUTION_HP_REGEN_PERCENT.get());
        execution.putDouble("dropBoost", GameMechanicsConfig.EXECUTION_DROP_BOOST.get());
        execution.putDouble("interruptVulnerability", GameMechanicsConfig.EXECUTION_INTERRUPT_VULNERABILITY.get());
        execution.putDouble("range", GameMechanicsConfig.EXECUTION_RANGE.get());
        tag.put("execution", execution);

        // Combo System
        CompoundTag combo = new CompoundTag();
        combo.putInt("timeoutTicks", GameMechanicsConfig.COMBO_TIMEOUT_TICKS.get());
        combo.putInt("basePoints", GameMechanicsConfig.COMBO_BASE_POINTS.get());
        combo.putDouble("multiplierIncrement", GameMechanicsConfig.COMBO_MULTIPLIER_INCREMENT.get());
        combo.putDouble("maxMultiplier", GameMechanicsConfig.COMBO_MAX_MULTIPLIER.get());
        combo.putInt("finisherThreshold", GameMechanicsConfig.COMBO_FINISHER_THRESHOLD.get());
        combo.putInt("juggleBonus", GameMechanicsConfig.COMBO_JUGGLE_BONUS.get());
        combo.putInt("headshotBonus", GameMechanicsConfig.COMBO_HEADSHOT_BONUS.get());
        combo.putInt("executionBonus", GameMechanicsConfig.COMBO_EXECUTION_BONUS.get());
        tag.put("combo", combo);

        // Style Rank System
        CompoundTag styleRank = new CompoundTag();
        styleRank.putInt("dThreshold", GameMechanicsConfig.STYLE_RANK_D_THRESHOLD.get());
        styleRank.putInt("cThreshold", GameMechanicsConfig.STYLE_RANK_C_THRESHOLD.get());
        styleRank.putInt("bThreshold", GameMechanicsConfig.STYLE_RANK_B_THRESHOLD.get());
        styleRank.putInt("aThreshold", GameMechanicsConfig.STYLE_RANK_A_THRESHOLD.get());
        styleRank.putInt("sThreshold", GameMechanicsConfig.STYLE_RANK_S_THRESHOLD.get());
        styleRank.putInt("ssThreshold", GameMechanicsConfig.STYLE_RANK_SS_THRESHOLD.get());
        styleRank.putInt("sssThreshold", GameMechanicsConfig.STYLE_RANK_SSS_THRESHOLD.get());
        styleRank.putDouble("decayRate", GameMechanicsConfig.STYLE_DECAY_RATE.get());
        styleRank.putInt("decayDelayTicks", GameMechanicsConfig.STYLE_DECAY_DELAY_TICKS.get());
        tag.put("styleRank", styleRank);

        // Momentum / Flow State
        CompoundTag momentum = new CompoundTag();
        momentum.putDouble("decayRate", GameMechanicsConfig.MOMENTUM_DECAY_RATE.get());
        momentum.putDouble("killBoost", GameMechanicsConfig.MOMENTUM_KILL_BOOST.get());
        momentum.putDouble("hitBoost", GameMechanicsConfig.MOMENTUM_HIT_BOOST.get());
        momentum.putDouble("overdriveThreshold", GameMechanicsConfig.MOMENTUM_OVERDRIVE_THRESHOLD.get());
        momentum.putInt("overdriveDurationTicks", GameMechanicsConfig.MOMENTUM_OVERDRIVE_DURATION_TICKS.get());
        tag.put("momentum", momentum);

        CompoundTag flowState = new CompoundTag();
        flowState.putInt("staleThresholdTicks", GameMechanicsConfig.FLOW_STALE_THRESHOLD_TICKS.get());
        flowState.putDouble("freshBonusMultiplier", GameMechanicsConfig.FLOW_FRESH_BONUS_MULTIPLIER.get());
        flowState.putDouble("virtuosoThreshold", GameMechanicsConfig.FLOW_VIRTUOSO_THRESHOLD.get());
        flowState.putInt("virtuosoDurationTicks", GameMechanicsConfig.FLOW_VIRTUOSO_DURATION_TICKS.get());
        tag.put("flowState", flowState);

        // Devil's Bargain
        CompoundTag bargain = new CompoundTag();
        bargain.putBoolean("enabled", GameMechanicsConfig.BARGAIN_ENABLED.get());
        bargain.putInt("altarSpawnWave", GameMechanicsConfig.BARGAIN_ALTAR_SPAWN_WAVE.get());
        bargain.putInt("altarIntervalWaves", GameMechanicsConfig.BARGAIN_ALTAR_INTERVAL_WAVES.get());
        bargain.putInt("maxCursesPerRun", GameMechanicsConfig.BARGAIN_MAX_CURSES_PER_RUN.get());
        bargain.putInt("choiceTimeoutTicks", GameMechanicsConfig.BARGAIN_CHOICE_TIMEOUT_TICKS.get());
        bargain.putDouble("cursePowerMultiplier", GameMechanicsConfig.BARGAIN_CURSE_POWER_MULTIPLIER.get());
        bargain.putDouble("boonPowerMultiplier", GameMechanicsConfig.BARGAIN_BOON_POWER_MULTIPLIER.get());
        tag.put("bargain", bargain);

        // Arena Hazards
        CompoundTag hazards = new CompoundTag();
        hazards.putBoolean("enabled", GameMechanicsConfig.HAZARD_ENABLED.get());
        hazards.putInt("floorCrumbleWave", GameMechanicsConfig.HAZARD_FLOOR_CRUMBLE_WAVE.get());
        hazards.putInt("bloodMoonWave", GameMechanicsConfig.HAZARD_BLOOD_MOON_WAVE.get());
        hazards.putInt("arenaShrinkWave", GameMechanicsConfig.HAZARD_ARENA_SHRINK_WAVE.get());
        hazards.putInt("lightningStormWave", GameMechanicsConfig.HAZARD_LIGHTNING_STORM_WAVE.get());
        hazards.putInt("voidRiftsWave", GameMechanicsConfig.HAZARD_VOID_RIFTS_WAVE.get());
        hazards.putInt("floorCrumbleDuration", GameMechanicsConfig.HAZARD_FLOOR_CRUMBLE_DURATION.get());
        hazards.putInt("bloodMoonDuration", GameMechanicsConfig.HAZARD_BLOOD_MOON_DURATION.get());
        hazards.putInt("arenaShrinkDuration", GameMechanicsConfig.HAZARD_ARENA_SHRINK_DURATION.get());
        hazards.putInt("lightningStormDuration", GameMechanicsConfig.HAZARD_LIGHTNING_STORM_DURATION.get());
        hazards.putInt("voidRiftsDuration", GameMechanicsConfig.HAZARD_VOID_RIFTS_DURATION.get());
        hazards.putDouble("lightningDamage", GameMechanicsConfig.HAZARD_LIGHTNING_DAMAGE.get());
        hazards.putDouble("voidRiftDamage", GameMechanicsConfig.HAZARD_VOID_RIFT_DAMAGE.get());
        hazards.putDouble("bloodMoonMobBuff", GameMechanicsConfig.HAZARD_BLOOD_MOON_MOB_BUFF.get());
        hazards.putDouble("shrinkRate", GameMechanicsConfig.HAZARD_ARENA_SHRINK_RATE.get());
        tag.put("hazards", hazards);

        // Perk Synergy
        CompoundTag perkSynergy = new CompoundTag();
        perkSynergy.putBoolean("enabled", GameMechanicsConfig.PERK_SYNERGY_ENABLED.get());
        perkSynergy.putBoolean("hiddenPerksEnabled", GameMechanicsConfig.PERK_HIDDEN_PERKS_ENABLED.get());
        perkSynergy.putBoolean("sacrificeEnabled", GameMechanicsConfig.PERK_SACRIFICE_ENABLED.get());
        perkSynergy.putInt("sacrificeStyleCost", GameMechanicsConfig.PERK_SACRIFICE_STYLE_COST.get());
        perkSynergy.putInt("discoveryXpBase", GameMechanicsConfig.PERK_DISCOVERY_XP_BASE.get());
        perkSynergy.putDouble("synergyBonusMultiplier", GameMechanicsConfig.PERK_SYNERGY_BONUS_MULTIPLIER.get());
        tag.put("perkSynergy", perkSynergy);

        // Wave Configuration
        CompoundTag waves = new CompoundTag();
        waves.putInt("baseMobCount", GameMechanicsConfig.WAVE_BASE_MOB_COUNT.get());
        waves.putDouble("mobScaling", GameMechanicsConfig.WAVE_MOB_SCALING.get());
        waves.putInt("intermissionTicks", GameMechanicsConfig.WAVE_INTERMISSION_TICKS.get());
        waves.putDouble("eliteChanceBase", GameMechanicsConfig.WAVE_ELITE_CHANCE_BASE.get());
        waves.putDouble("eliteChanceScaling", GameMechanicsConfig.WAVE_ELITE_CHANCE_SCALING.get());
        waves.putInt("bossInterval", GameMechanicsConfig.WAVE_BOSS_INTERVAL.get());
        tag.put("waves", waves);

        // Rewards
        CompoundTag rewards = new CompoundTag();
        rewards.putDouble("xpMultiplier", GameMechanicsConfig.REWARD_XP_MULTIPLIER.get());
        rewards.putDouble("styleMultiplier", GameMechanicsConfig.REWARD_STYLE_MULTIPLIER.get());
        rewards.putDouble("dropRateBonus", GameMechanicsConfig.REWARD_DROP_RATE_BONUS.get());
        rewards.putInt("bonusChestWaveInterval", GameMechanicsConfig.REWARD_BONUS_CHEST_WAVE_INTERVAL.get());
        tag.put("rewards", rewards);

        // Season Pass
        CompoundTag seasonPass = new CompoundTag();
        seasonPass.putInt("maxTier", GameMechanicsConfig.SEASON_MAX_TIER.get());
        seasonPass.putInt("xpPerTier", GameMechanicsConfig.SEASON_XP_PER_TIER.get());
        seasonPass.putInt("durationDays", GameMechanicsConfig.SEASON_DURATION_DAYS.get());
        seasonPass.putInt("xpPerKill", GameMechanicsConfig.SEASON_XP_PER_KILL.get());
        seasonPass.putInt("xpPerWave", GameMechanicsConfig.SEASON_XP_PER_WAVE.get());
        seasonPass.putInt("xpPerBoss", GameMechanicsConfig.SEASON_XP_PER_BOSS.get());
        seasonPass.putInt("xpDailyChallenge", GameMechanicsConfig.SEASON_XP_DAILY_CHALLENGE.get());
        seasonPass.putInt("xpWeeklyChallenge", GameMechanicsConfig.SEASON_XP_WEEKLY_CHALLENGE.get());
        seasonPass.putInt("xpStyleS", GameMechanicsConfig.SEASON_XP_STYLE_S.get());
        seasonPass.putInt("xpStyleSS", GameMechanicsConfig.SEASON_XP_STYLE_SS.get());
        seasonPass.putInt("xpStyleSSS", GameMechanicsConfig.SEASON_XP_STYLE_SSS.get());
        seasonPass.putInt("xpFlawlessWave", GameMechanicsConfig.SEASON_XP_FLAWLESS_WAVE.get());
        seasonPass.putInt("xpHighCombo50", GameMechanicsConfig.SEASON_XP_HIGH_COMBO_50.get());
        seasonPass.putInt("xpHighCombo100", GameMechanicsConfig.SEASON_XP_HIGH_COMBO_100.get());
        seasonPass.putDouble("xpBoostMultiplier", GameMechanicsConfig.SEASON_XP_BOOST_MULTIPLIER.get());
        seasonPass.putInt("xpBoostDurationMinutes", GameMechanicsConfig.SEASON_XP_BOOST_DURATION_MINUTES.get());
        tag.put("seasonPass", seasonPass);

        // Guild System
        CompoundTag guild = new CompoundTag();
        guild.putInt("maxSize", GameMechanicsConfig.GUILD_MAX_SIZE.get());
        guild.putInt("maxLevel", GameMechanicsConfig.GUILD_MAX_LEVEL.get());
        guild.putInt("createCost", GameMechanicsConfig.GUILD_CREATE_COST.get());
        guild.putInt("xpPerLevelBase", GameMechanicsConfig.GUILD_XP_PER_LEVEL_BASE.get());
        guild.putDouble("xpScaling", GameMechanicsConfig.GUILD_XP_SCALING.get());
        guild.putInt("bankTaxPercent", GameMechanicsConfig.GUILD_BANK_TAX_PERCENT.get());
        guild.putInt("objectiveKillsTarget", GameMechanicsConfig.GUILD_OBJECTIVE_KILLS_TARGET.get());
        guild.putInt("objectiveWavesTarget", GameMechanicsConfig.GUILD_OBJECTIVE_WAVES_TARGET.get());
        guild.putInt("objectiveBossesTarget", GameMechanicsConfig.GUILD_OBJECTIVE_BOSSES_TARGET.get());
        guild.putInt("objectiveTokensTarget", GameMechanicsConfig.GUILD_OBJECTIVE_TOKENS_TARGET.get());
        guild.putInt("objectiveChallengesTarget", GameMechanicsConfig.GUILD_OBJECTIVE_CHALLENGES_TARGET.get());
        guild.putInt("perkUnlockLevel1", GameMechanicsConfig.GUILD_PERK_UNLOCK_LEVEL_1.get());
        guild.putInt("perkUnlockLevel2", GameMechanicsConfig.GUILD_PERK_UNLOCK_LEVEL_2.get());
        guild.putInt("perkUnlockLevel3", GameMechanicsConfig.GUILD_PERK_UNLOCK_LEVEL_3.get());
        guild.putDouble("bonusTokens5", GameMechanicsConfig.GUILD_BONUS_TOKENS_5.get());
        guild.putDouble("bonusTokens10", GameMechanicsConfig.GUILD_BONUS_TOKENS_10.get());
        guild.putDouble("bonusTokens15", GameMechanicsConfig.GUILD_BONUS_TOKENS_15.get());
        guild.putDouble("bonusXp5", GameMechanicsConfig.GUILD_BONUS_XP_5.get());
        guild.putDouble("bonusXp10", GameMechanicsConfig.GUILD_BONUS_XP_10.get());
        tag.put("guild", guild);

        // Ascension/Prestige
        CompoundTag ascension = new CompoundTag();
        ascension.putInt("maxLevel", GameMechanicsConfig.ASCENSION_MAX_LEVEL.get());
        ascension.putInt("minPrestige", GameMechanicsConfig.ASCENSION_MIN_PRESTIGE.get());
        ascension.putInt("costScaling", GameMechanicsConfig.ASCENSION_COST_SCALING.get());
        ascension.putDouble("damageBonusPerLevel", GameMechanicsConfig.ASCENSION_DAMAGE_BONUS_PER_LEVEL.get());
        ascension.putDouble("defenseBonusPerLevel", GameMechanicsConfig.ASCENSION_DEFENSE_BONUS_PER_LEVEL.get());
        ascension.putDouble("tokenMultiplierPerLevel", GameMechanicsConfig.ASCENSION_TOKEN_MULTIPLIER_PER_LEVEL.get());
        ascension.putDouble("lifestealBonusPerLevel", GameMechanicsConfig.ASCENSION_LIFESTEAL_BONUS_PER_LEVEL.get());
        ascension.putDouble("critBonusPerLevel", GameMechanicsConfig.ASCENSION_CRIT_BONUS_PER_LEVEL.get());
        ascension.putDouble("comboDecayReductionPerLevel", GameMechanicsConfig.ASCENSION_COMBO_DECAY_REDUCTION_PER_LEVEL.get());
        ascension.putInt("extraPerkSlotsPerLevel", GameMechanicsConfig.ASCENSION_EXTRA_PERK_SLOTS_PER_LEVEL.get());
        ascension.putDouble("startingHpBonusPerLevel", GameMechanicsConfig.ASCENSION_STARTING_HP_BONUS_PER_LEVEL.get());
        tag.put("ascension", ascension);

        // Tension System
        CompoundTag tension = new CompoundTag();
        tension.putDouble("baseWaveGain", GameMechanicsConfig.TENSION_BASE_WAVE_GAIN.get());
        tension.putDouble("noHitBonus", GameMechanicsConfig.TENSION_NO_HIT_BONUS.get());
        tension.putDouble("minThreshold", GameMechanicsConfig.TENSION_MIN_THRESHOLD.get());
        tension.putDouble("maxThreshold", GameMechanicsConfig.TENSION_MAX_THRESHOLD.get());
        tension.putDouble("eliteKillBonus", GameMechanicsConfig.TENSION_ELITE_KILL_BONUS.get());
        tension.putDouble("comboBonusThreshold", GameMechanicsConfig.TENSION_COMBO_BONUS_THRESHOLD.get());
        tension.putDouble("styleSBonus", GameMechanicsConfig.TENSION_STYLE_S_BONUS.get());
        tension.putInt("minWavesBeforeBoss", GameMechanicsConfig.TENSION_MIN_WAVES_BEFORE_BOSS.get());
        tension.putInt("maxWavesWithoutBoss", GameMechanicsConfig.TENSION_MAX_WAVES_WITHOUT_BOSS.get());
        tension.putDouble("decayAfterBoss", GameMechanicsConfig.TENSION_DECAY_AFTER_BOSS.get());
        tag.put("tension", tension);

        // Perk Rarity
        CompoundTag perkRarity = new CompoundTag();
        perkRarity.putInt("commonWeight", GameMechanicsConfig.PERK_COMMON_WEIGHT.get());
        perkRarity.putInt("uncommonWeight", GameMechanicsConfig.PERK_UNCOMMON_WEIGHT.get());
        perkRarity.putInt("rareWeight", GameMechanicsConfig.PERK_RARE_WEIGHT.get());
        perkRarity.putInt("epicWeight", GameMechanicsConfig.PERK_EPIC_WEIGHT.get());
        perkRarity.putInt("legendaryWeight", GameMechanicsConfig.PERK_LEGENDARY_WEIGHT.get());
        perkRarity.putInt("choicesPerSelection", GameMechanicsConfig.PERK_CHOICES_PER_SELECTION.get());
        perkRarity.putInt("rerollCostBase", GameMechanicsConfig.PERK_REROLL_COST_BASE.get());
        perkRarity.putInt("rerollCostIncrement", GameMechanicsConfig.PERK_REROLL_COST_INCREMENT.get());
        perkRarity.putDouble("damageBonusCommon", GameMechanicsConfig.PERK_DAMAGE_BONUS_COMMON.get());
        perkRarity.putDouble("damageBonusUncommon", GameMechanicsConfig.PERK_DAMAGE_BONUS_UNCOMMON.get());
        perkRarity.putDouble("damageBonusRare", GameMechanicsConfig.PERK_DAMAGE_BONUS_RARE.get());
        perkRarity.putDouble("damageBonusEpic", GameMechanicsConfig.PERK_DAMAGE_BONUS_EPIC.get());
        perkRarity.putDouble("damageBonusLegendary", GameMechanicsConfig.PERK_DAMAGE_BONUS_LEGENDARY.get());
        tag.put("perkRarity", perkRarity);

        // Challenge System
        CompoundTag challenges = new CompoundTag();
        challenges.putInt("dailyCount", GameMechanicsConfig.CHALLENGE_DAILY_COUNT.get());
        challenges.putInt("weeklyCount", GameMechanicsConfig.CHALLENGE_WEEKLY_COUNT.get());
        challenges.putInt("dailyResetHour", GameMechanicsConfig.CHALLENGE_DAILY_RESET_HOUR.get());
        challenges.putInt("weeklyResetDay", GameMechanicsConfig.CHALLENGE_WEEKLY_RESET_DAY.get());
        challenges.putInt("killsTargetMin", GameMechanicsConfig.CHALLENGE_KILLS_TARGET_MIN.get());
        challenges.putInt("killsTargetMax", GameMechanicsConfig.CHALLENGE_KILLS_TARGET_MAX.get());
        challenges.putInt("wavesTargetMin", GameMechanicsConfig.CHALLENGE_WAVES_TARGET_MIN.get());
        challenges.putInt("wavesTargetMax", GameMechanicsConfig.CHALLENGE_WAVES_TARGET_MAX.get());
        challenges.putInt("comboTargetMin", GameMechanicsConfig.CHALLENGE_COMBO_TARGET_MIN.get());
        challenges.putInt("comboTargetMax", GameMechanicsConfig.CHALLENGE_COMBO_TARGET_MAX.get());
        challenges.putInt("dailyTokenReward", GameMechanicsConfig.CHALLENGE_DAILY_TOKEN_REWARD.get());
        challenges.putInt("weeklyTokenReward", GameMechanicsConfig.CHALLENGE_WEEKLY_TOKEN_REWARD.get());
        challenges.putInt("dailyXpReward", GameMechanicsConfig.CHALLENGE_DAILY_XP_REWARD.get());
        challenges.putInt("weeklyXpReward", GameMechanicsConfig.CHALLENGE_WEEKLY_XP_REWARD.get());
        tag.put("challenges", challenges);

        return tag;
    }

    // ============================================================================
    // CLIENT-SIDE APPLICATION
    // ============================================================================

    /**
     * Applies the received config to the client-side cache.
     * Called on the render thread via context.enqueueWork().
     */
    public void applyToClient() {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            NetworkHandler.withClientHooks(hooks -> hooks.handleGameMechanicsSync(this));
        }
    }
}
