package com.devmod.endurance.season;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.devmod.endurance.config.EnduranceConfigManager;
import com.devmod.notification.NotificationService;

public class SeasonPassSystem {
    private static final Logger LOGGER = LoggerFactory.getLogger(SeasonPassSystem.class);

    // Default values (used when no quest context or as fallback)
    public static final int DEFAULT_MAX_TIER = 100;
    public static final int DEFAULT_XP_PER_TIER = 1000;
    public static final int DEFAULT_SEASON_DURATION_DAYS = 90;

    // Default XP sources (fallback values)
    public static final int DEFAULT_XP_PER_KILL = 2;
    public static final int DEFAULT_XP_PER_WAVE = 50;
    public static final int DEFAULT_XP_PER_BOSS_KILL = 200;
    public static final int DEFAULT_XP_DAILY_CHALLENGE = 500;
    public static final int DEFAULT_XP_WEEKLY_CHALLENGE = 2000;
    public static final int DEFAULT_XP_STYLE_BONUS_S = 100;
    public static final int DEFAULT_XP_STYLE_BONUS_SS = 200;
    public static final int DEFAULT_XP_STYLE_BONUS_SSS = 500;
    public static final int DEFAULT_XP_FLAWLESS_WAVE = 75;
    public static final int DEFAULT_XP_HIGH_COMBO_50 = 150;
    public static final int DEFAULT_XP_HIGH_COMBO_100 = 300;

    // Backwards-compatible constants used across the season system.
    public static final int MAX_TIER = DEFAULT_MAX_TIER;
    public static final int XP_PER_TIER = DEFAULT_XP_PER_TIER;
    public static final int SEASON_DURATION_DAYS = DEFAULT_SEASON_DURATION_DAYS;
    public static final int XP_PER_KILL = DEFAULT_XP_PER_KILL;
    public static final int XP_PER_WAVE = DEFAULT_XP_PER_WAVE;
    public static final int XP_PER_BOSS_KILL = DEFAULT_XP_PER_BOSS_KILL;
    public static final int XP_DAILY_CHALLENGE = DEFAULT_XP_DAILY_CHALLENGE;
    public static final int XP_WEEKLY_CHALLENGE = DEFAULT_XP_WEEKLY_CHALLENGE;
    public static final int XP_STYLE_BONUS_S = DEFAULT_XP_STYLE_BONUS_S;
    public static final int XP_STYLE_BONUS_SS = DEFAULT_XP_STYLE_BONUS_SS;
    public static final int XP_STYLE_BONUS_SSS = DEFAULT_XP_STYLE_BONUS_SSS;
    public static final int XP_FLAWLESS_WAVE = DEFAULT_XP_FLAWLESS_WAVE;
    public static final int XP_HIGH_COMBO_50 = DEFAULT_XP_HIGH_COMBO_50;
    public static final int XP_HIGH_COMBO_100 = DEFAULT_XP_HIGH_COMBO_100;

    // Config manager reference
    private final EnduranceConfigManager config = EnduranceConfigManager.INSTANCE;

    private static SeasonPassSystem instance;

    // Current season data
    private int currentSeasonNumber = 1;
    private String seasonName = "Season of the Gladiator";
    private String seasonTheme = "GLADIATOR";
    private Instant seasonStartTime;
    private Instant seasonEndTime;

    // Reward definitions
    private final Map<Integer, SeasonReward> freeTrackRewards = new HashMap<>();
    private final Map<Integer, SeasonReward> premiumTrackRewards = new HashMap<>();

    // Player progress (UUID -> PlayerSeasonProgress)
    private final Map<UUID, PlayerSeasonProgress> playerProgress = new HashMap<>();

    private SeasonPassSystem() {
        initializeCurrentSeason();
        initializeRewards();
    }

    public static SeasonPassSystem getInstance() {
        if (instance == null) {
            instance = new SeasonPassSystem();
        }
        return instance;
    }

    /**
     * Initialize the current season with dates and theme.
     */
    private void initializeCurrentSeason() {
        // For now, set a fixed start date (would be configurable in production)
        seasonStartTime = Instant.now().truncatedTo(ChronoUnit.DAYS);
        seasonEndTime = seasonStartTime.plus(SEASON_DURATION_DAYS, ChronoUnit.DAYS);

        LOGGER.info("[SeasonPass] Season {} '{}' initialized. Ends: {}",
            currentSeasonNumber, seasonName, seasonEndTime);
    }

    /**
     * Initialize reward tracks for the season.
     */
    private void initializeRewards() {
        // Free track rewards - every 5 tiers
        for (int tier = 5; tier <= MAX_TIER; tier += 5) {
            freeTrackRewards.put(tier, generateFreeReward(tier));
        }

        // Premium track rewards - every tier has something
        for (int tier = 1; tier <= MAX_TIER; tier++) {
            premiumTrackRewards.put(tier, generatePremiumReward(tier));
        }

        LOGGER.info("[SeasonPass] Initialized {} free rewards, {} premium rewards",
            freeTrackRewards.size(), premiumTrackRewards.size());
    }

    /**
     * Generate a free track reward for a tier.
     */
    private SeasonReward generateFreeReward(int tier) {
        RewardType type;
        int amount;
        String itemId = null;

        if (tier % 25 == 0) {
            // Major milestone - exclusive item
            type = RewardType.EXCLUSIVE_ITEM;
            amount = 1;
            itemId = "season_" + currentSeasonNumber + "_free_tier_" + tier;
        } else if (tier % 10 == 0) {
            // Every 10 tiers - prestige points
            type = RewardType.PRESTIGE;
            amount = 5 + (tier / 10);
        } else {
            // Every 5 tiers - tokens
            type = RewardType.TOKENS;
            amount = 200 + (tier * 10);
        }

        return new SeasonReward(type, amount, itemId, false);
    }

    /**
     * Generate a premium track reward for a tier.
     */
    private SeasonReward generatePremiumReward(int tier) {
        RewardType type;
        int amount;
        String itemId = null;

        if (tier == DEFAULT_MAX_TIER) {
            // Tier 100 - Ultimate exclusive
            type = RewardType.EXCLUSIVE_TITLE;
            amount = 1;
            itemId = "season_" + currentSeasonNumber + "_champion";
        } else if (tier % 25 == 0) {
            // Every 25 tiers - Exclusive cosmetic
            type = RewardType.EXCLUSIVE_COSMETIC;
            amount = 1;
            itemId = "season_" + currentSeasonNumber + "_cosmetic_" + tier;
        } else if (tier % 10 == 0) {
            // Every 10 tiers - Exclusive perk unlock
            type = RewardType.EXCLUSIVE_PERK;
            amount = 1;
            itemId = getSeasonPerkId(tier);
        } else if (tier % 5 == 0) {
            // Every 5 tiers - Bonus prestige
            type = RewardType.PRESTIGE;
            amount = 10 + (tier / 5);
        } else if (tier % 2 == 0) {
            // Even tiers - Tokens
            type = RewardType.TOKENS;
            amount = 100 + (tier * 5);
        } else {
            // Odd tiers - XP Boost
            type = RewardType.XP_BOOST;
            amount = 10 + (tier / 10); // 10-20% boost duration in minutes
        }

        return new SeasonReward(type, amount, itemId, true);
    }

    /**
     * Get season-exclusive perk ID for a tier.
     */
    private String getSeasonPerkId(int tier) {
        return switch (tier) {
            case 10 -> "season_striker";      // +5% damage during season
            case 20 -> "season_guardian";     // +5% defense during season
            case 30 -> "season_collector";    // +10% token gain during season
            case 40 -> "season_veteran";      // +5% XP gain during season
            case 50 -> "season_elite";        // Start with 1 extra perk choice
            case 60 -> "season_champion";     // +10% combo duration
            case 70 -> "season_legend";       // +1 reroll per run
            case 80 -> "season_mythic";       // Enemies drop bonus loot
            case 90 -> "season_immortal";     // +15% all stats during season
            case 100 -> "season_eternal";     // Permanent +2% all stats
            default -> "season_bonus_" + tier;
        };
    }

    // === XP Gain Methods ===

    /**
     * Award XP to a player for kills.
     */
    public void awardKillXP(UUID playerId, int killCount) {
        awardXP(playerId, killCount * DEFAULT_XP_PER_KILL, "kills");
    }

    /**
     * Award XP to a player for kills with quest context.
     */
    public void awardKillXP(UUID playerId, UUID questId, int killCount) {
        int xpPerKill = config.getSeasonXpPerKill(questId);
        awardXP(playerId, killCount * xpPerKill, "kills");
    }

    /**
     * Award XP for completing a wave.
     */
    public void awardWaveXP(UUID playerId, boolean flawless) {
        int xp = DEFAULT_XP_PER_WAVE;
        if (flawless) {
            xp += DEFAULT_XP_FLAWLESS_WAVE;
        }
        awardXP(playerId, xp, "wave");
    }

    /**
     * Award XP for completing a wave with quest context.
     */
    public void awardWaveXP(UUID playerId, UUID questId, boolean flawless) {
        int xp = config.getSeasonXpPerWave(questId);
        if (flawless) {
            xp += config.getSeasonXpFlawlessWave(questId);
        }
        awardXP(playerId, xp, "wave");
    }

    /**
     * Award XP for boss kill.
     */
    public void awardBossXP(UUID playerId) {
        awardXP(playerId, DEFAULT_XP_PER_BOSS_KILL, "boss");
    }

    /**
     * Award XP for boss kill with quest context.
     */
    public void awardBossXP(UUID playerId, UUID questId) {
        awardXP(playerId, config.getSeasonXpPerBoss(questId), "boss");
    }

    /**
     * Award XP for style rank.
     */
    public void awardStyleXP(UUID playerId, String styleRank) {
        int xp = switch (styleRank.toUpperCase(Locale.ROOT)) {
            case "S" -> DEFAULT_XP_STYLE_BONUS_S;
            case "SS" -> DEFAULT_XP_STYLE_BONUS_SS;
            case "SSS" -> DEFAULT_XP_STYLE_BONUS_SSS;
            default -> 0;
        };
        if (xp > 0) {
            awardXP(playerId, xp, "style_" + styleRank);
        }
    }

    /**
     * Award XP for style rank with quest context.
     */
    public void awardStyleXP(UUID playerId, UUID questId, String styleRank) {
        int xp = switch (styleRank.toUpperCase(Locale.ROOT)) {
            case "S" -> config.getSeasonXpStyleS(questId);
            case "SS" -> config.getSeasonXpStyleSS(questId);
            case "SSS" -> config.getSeasonXpStyleSSS(questId);
            default -> 0;
        };
        if (xp > 0) {
            awardXP(playerId, xp, "style_" + styleRank);
        }
    }

    /**
     * Award XP for high combo.
     */
    public void awardComboXP(UUID playerId, int maxCombo) {
        if (maxCombo >= 100) {
            awardXP(playerId, DEFAULT_XP_HIGH_COMBO_100, "combo_100");
        } else if (maxCombo >= 50) {
            awardXP(playerId, DEFAULT_XP_HIGH_COMBO_50, "combo_50");
        }
    }

    /**
     * Award XP for high combo with quest context.
     */
    public void awardComboXP(UUID playerId, UUID questId, int maxCombo) {
        if (maxCombo >= 100) {
            awardXP(playerId, config.getSeasonXpHighCombo100(questId), "combo_100");
        } else if (maxCombo >= 50) {
            awardXP(playerId, config.getSeasonXpHighCombo50(questId), "combo_50");
        }
    }

    /**
     * Award XP for completing daily challenge.
     */
    public void awardDailyChallengeXP(UUID playerId) {
        awardXP(playerId, DEFAULT_XP_DAILY_CHALLENGE, "daily_challenge");
    }

    /**
     * Award XP for completing daily challenge with quest context.
     */
    public void awardDailyChallengeXP(UUID playerId, UUID questId) {
        awardXP(playerId, config.getSeasonXpDailyChallenge(questId), "daily_challenge");
    }

    /**
     * Award XP for completing weekly challenge.
     */
    public void awardWeeklyChallengeXP(UUID playerId) {
        awardXP(playerId, DEFAULT_XP_WEEKLY_CHALLENGE, "weekly_challenge");
    }

    /**
     * Award XP for completing weekly challenge with quest context.
     */
    public void awardWeeklyChallengeXP(UUID playerId, UUID questId) {
        awardXP(playerId, config.getSeasonXpWeeklyChallenge(questId), "weekly_challenge");
    }

    /**
     * Core XP award method.
     */
    public void awardXP(UUID playerId, int amount, String source) {
        if (!isSeasonActive()) {
            return;
        }

        PlayerSeasonProgress progress = getOrCreateProgress(playerId);
        int oldTier = progress.getCurrentTier();

        // Apply any XP boost
        if (progress.hasXpBoost()) {
            amount = (int) (amount * progress.getXpBoostMultiplier());
        }

        progress.addXP(amount);
        int newTier = progress.getCurrentTier();

        // Check for tier-ups
        if (newTier > oldTier) {
            for (int tier = oldTier + 1; tier <= newTier; tier++) {
                onTierUp(playerId, progress, tier);
            }
        }

        LOGGER.debug("[SeasonPass] {} earned {} XP from {}. Now tier {} ({}/{})",
            playerId, amount, source, newTier, progress.getCurrentXP() % DEFAULT_XP_PER_TIER, DEFAULT_XP_PER_TIER);
    }

    /**
     * Handle tier-up event.
     */
    private void onTierUp(UUID playerId, PlayerSeasonProgress progress, int newTier) {
        LOGGER.info("[SeasonPass] {} reached tier {}!", playerId, newTier);

        // Mark rewards as available
        if (freeTrackRewards.containsKey(newTier)) {
            progress.unlockFreeReward(newTier);
        }

        if (progress.isPremium() && premiumTrackRewards.containsKey(newTier)) {
            progress.unlockPremiumReward(newTier);
        }

        notifyTierUp(playerId, progress, newTier);
    }

    private void notifyTierUp(UUID playerId, PlayerSeasonProgress progress, int newTier) {
        // Get reward names for display
        SeasonReward freeReward = freeTrackRewards.get(newTier);
        SeasonReward premiumReward = premiumTrackRewards.get(newTier);
        String freeRewardName = freeReward != null ? freeReward.getDisplayName() : "";
        String premiumRewardName = premiumReward != null ? premiumReward.getDisplayName() : "";

        NotificationService.INSTANCE.notifySeasonTierUp(playerId, newTier, freeRewardName, premiumRewardName);
    }

    // === Progress Management ===

    /**
     * Get or create player progress.
     */
    public PlayerSeasonProgress getOrCreateProgress(UUID playerId) {
        return playerProgress.computeIfAbsent(playerId,
            id -> new PlayerSeasonProgress(id, currentSeasonNumber));
    }

    /**
     * Get player's current tier.
     */
    public int getPlayerTier(UUID playerId) {
        return getOrCreateProgress(playerId).getCurrentTier();
    }

    /**
     * Get player's XP progress in current tier.
     */
    public int getPlayerTierProgress(UUID playerId) {
        return getOrCreateProgress(playerId).getCurrentXP() % XP_PER_TIER;
    }

    /**
     * Check if player has premium pass.
     */
    public boolean hasPremiumPass(UUID playerId) {
        return getOrCreateProgress(playerId).isPremium();
    }

    /**
     * Grant premium pass to player.
     */
    public void grantPremiumPass(UUID playerId) {
        PlayerSeasonProgress progress = getOrCreateProgress(playerId);
        progress.setPremium(true);

        // Unlock all premium rewards up to current tier
        for (int tier = 1; tier <= progress.getCurrentTier(); tier++) {
            if (premiumTrackRewards.containsKey(tier)) {
                progress.unlockPremiumReward(tier);
            }
        }

        LOGGER.info("[SeasonPass] {} upgraded to Premium Pass!", playerId);
    }

    /**
     * Claim a reward.
     */
    public Optional<SeasonReward> claimReward(UUID playerId, int tier, boolean premium) {
        PlayerSeasonProgress progress = getOrCreateProgress(playerId);

        if (premium) {
            if (!progress.isPremium()) {
                return Optional.empty();
            }
            if (!progress.canClaimPremiumReward(tier)) {
                return Optional.empty();
            }
            progress.claimPremiumReward(tier);
            return Optional.ofNullable(premiumTrackRewards.get(tier));
        } else {
            if (!progress.canClaimFreeReward(tier)) {
                return Optional.empty();
            }
            progress.claimFreeReward(tier);
            return Optional.ofNullable(freeTrackRewards.get(tier));
        }
    }

    // === Season State ===

    /**
     * Check if the current season is active.
     */
    public boolean isSeasonActive() {
        Instant now = Instant.now();
        return now.isAfter(seasonStartTime) && now.isBefore(seasonEndTime);
    }

    /**
     * Get remaining days in the season.
     */
    public long getRemainingDays() {
        if (!isSeasonActive()) {
            return 0;
        }
        return ChronoUnit.DAYS.between(Instant.now(), seasonEndTime);
    }

    /**
     * Get season info for display.
     */
    public SeasonInfo getSeasonInfo() {
        return new SeasonInfo(
            currentSeasonNumber,
            seasonName,
            seasonTheme,
            seasonStartTime,
            seasonEndTime,
            getRemainingDays(),
            isSeasonActive()
        );
    }

    /**
     * Get rewards for a tier range.
     */
    public List<TierRewardInfo> getRewardsForTiers(int startTier, int endTier) {
        List<TierRewardInfo> rewards = new ArrayList<>();
        for (int tier = startTier; tier <= Math.min(endTier, MAX_TIER); tier++) {
            SeasonReward free = freeTrackRewards.get(tier);
            SeasonReward premium = premiumTrackRewards.get(tier);
            if (free != null || premium != null) {
                rewards.add(new TierRewardInfo(tier, free, premium));
            }
        }
        return rewards;
    }

    // === Data Records ===

    /**
     * Reward types available in season pass.
     */
    public enum RewardType {
        TOKENS,
        PRESTIGE,
        XP_BOOST,
        EXCLUSIVE_ITEM,
        EXCLUSIVE_COSMETIC,
        EXCLUSIVE_PERK,
        EXCLUSIVE_TITLE
    }

    /**
     * A season reward definition.
     */
    public record SeasonReward(
        RewardType type,
        int amount,
        String itemId,
        boolean premium
    ) {
        public String getDisplayName() {
            return switch (type) {
                case TOKENS -> amount + " Tokens";
                case PRESTIGE -> amount + " Prestige";
                case XP_BOOST -> amount + " min XP Boost";
                case EXCLUSIVE_ITEM -> "Exclusive Item";
                case EXCLUSIVE_COSMETIC -> "Exclusive Cosmetic";
                case EXCLUSIVE_PERK -> "Exclusive Perk";
                case EXCLUSIVE_TITLE -> "Exclusive Title";
            };
        }
    }

    /**
     * Season info for UI display.
     */
    public record SeasonInfo(
        int seasonNumber,
        String name,
        String theme,
        Instant startTime,
        Instant endTime,
        long remainingDays,
        boolean active
    ) {}

    /**
     * Tier reward info for UI.
     */
    public record TierRewardInfo(
        int tier,
        SeasonReward freeReward,
        SeasonReward premiumReward
    ) {}

    // === Getters ===

    public int getCurrentSeasonNumber() {
        return currentSeasonNumber;
    }

    public String getSeasonName() {
        return seasonName;
    }

    public Map<Integer, SeasonReward> getFreeTrackRewards() {
        return Collections.unmodifiableMap(freeTrackRewards);
    }

    public Map<Integer, SeasonReward> getPremiumTrackRewards() {
        return Collections.unmodifiableMap(premiumTrackRewards);
    }
}
