package com.devmod.endurance.season;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
public class PlayerSeasonProgress {

    private final UUID playerId;
    private final int seasonNumber;

    // XP and tier
    private int totalXP = 0;

    // Premium status
    private boolean premium = false;

    // Reward tracking
    private final Set<Integer> unlockedFreeRewards = new HashSet<>();
    private final Set<Integer> claimedFreeRewards = new HashSet<>();
    private final Set<Integer> unlockedPremiumRewards = new HashSet<>();
    private final Set<Integer> claimedPremiumRewards = new HashSet<>();

    // XP boost (from rewards)
    private float xpBoostMultiplier = 1.0f;
    private Instant xpBoostExpiry = null;

    // Stats
    private int totalKills = 0;
    private int totalWaves = 0;
    private int bossKills = 0;
    private int challengesCompleted = 0;
    private int highestCombo = 0;
    private String bestStyleRank = "";

    public PlayerSeasonProgress(UUID playerId, int seasonNumber) {
        this.playerId = playerId;
        this.seasonNumber = seasonNumber;
    }

    // === XP Management ===

    /**
     * Add XP to the player's total.
     */
    public void addXP(int amount) {
        if (amount <= 0) return;
        totalXP += amount;
    }

    /**
     * Get current tier (1-100).
     */
    public int getCurrentTier() {
        return Math.min(SeasonPassSystem.MAX_TIER,
            totalXP / SeasonPassSystem.XP_PER_TIER + 1);
    }

    /**
     * Get XP within current tier.
     */
    public int getCurrentTierXP() {
        return totalXP % SeasonPassSystem.XP_PER_TIER;
    }

    /**
     * Get total XP earned this season.
     */
    public int getTotalXP() {
        return totalXP;
    }

    /**
     * Get progress percentage within current tier (0.0 - 1.0).
     */
    public float getTierProgress() {
        return (float) getCurrentTierXP() / SeasonPassSystem.XP_PER_TIER;
    }

    /**
     * Get XP needed to reach next tier.
     */
    public int getXPToNextTier() {
        if (getCurrentTier() >= SeasonPassSystem.MAX_TIER) {
            return 0;
        }
        return SeasonPassSystem.XP_PER_TIER - getCurrentTierXP();
    }

    // === Premium Status ===

    public boolean isPremium() {
        return premium;
    }

    public void setPremium(boolean premium) {
        this.premium = premium;
    }

    // === Reward Management ===

    /**
     * Unlock a free track reward (player reached the tier).
     */
    public void unlockFreeReward(int tier) {
        unlockedFreeRewards.add(tier);
    }

    /**
     * Unlock a premium track reward.
     */
    public void unlockPremiumReward(int tier) {
        unlockedPremiumRewards.add(tier);
    }

    /**
     * Check if a free reward can be claimed.
     */
    public boolean canClaimFreeReward(int tier) {
        return unlockedFreeRewards.contains(tier) && !claimedFreeRewards.contains(tier);
    }

    /**
     * Check if a premium reward can be claimed.
     */
    public boolean canClaimPremiumReward(int tier) {
        return premium && unlockedPremiumRewards.contains(tier) && !claimedPremiumRewards.contains(tier);
    }

    /**
     * Claim a free reward.
     */
    public void claimFreeReward(int tier) {
        claimedFreeRewards.add(tier);
    }

    /**
     * Claim a premium reward.
     */
    public void claimPremiumReward(int tier) {
        claimedPremiumRewards.add(tier);
    }

    /**
     * Get count of unclaimed free rewards.
     */
    public int getUnclaimedFreeRewardCount() {
        int count = 0;
        for (int tier : unlockedFreeRewards) {
            if (!claimedFreeRewards.contains(tier)) {
                count++;
            }
        }
        return count;
    }

    /**
     * Get count of unclaimed premium rewards.
     */
    public int getUnclaimedPremiumRewardCount() {
        if (!premium) return 0;
        int count = 0;
        for (int tier : unlockedPremiumRewards) {
            if (!claimedPremiumRewards.contains(tier)) {
                count++;
            }
        }
        return count;
    }

    /**
     * Get total unclaimed rewards.
     */
    public int getTotalUnclaimedRewards() {
        return getUnclaimedFreeRewardCount() + getUnclaimedPremiumRewardCount();
    }

    // === XP Boost ===

    /**
     * Apply an XP boost.
     */
    public void applyXPBoost(float multiplier, int durationMinutes) {
        this.xpBoostMultiplier = multiplier;
        this.xpBoostExpiry = Instant.now().plusSeconds(durationMinutes * 60L);
    }

    /**
     * Check if XP boost is active.
     */
    public boolean hasXpBoost() {
        if (xpBoostExpiry == null) return false;
        if (Instant.now().isAfter(xpBoostExpiry)) {
            // Boost expired
            xpBoostMultiplier = 1.0f;
            xpBoostExpiry = null;
            return false;
        }
        return xpBoostMultiplier > 1.0f;
    }

    /**
     * Get current XP boost multiplier.
     */
    public float getXpBoostMultiplier() {
        return hasXpBoost() ? xpBoostMultiplier : 1.0f;
    }

    /**
     * Get remaining boost time in seconds.
     */
    public long getXpBoostRemainingSeconds() {
        if (!hasXpBoost()) return 0;
        return Math.max(0, xpBoostExpiry.getEpochSecond() - Instant.now().getEpochSecond());
    }

    // === Stats Tracking ===

    public void addKills(int count) {
        totalKills += count;
    }

    public void addWave() {
        totalWaves++;
    }

    public void addBossKill() {
        bossKills++;
    }

    public void addChallengeCompleted() {
        challengesCompleted++;
    }

    public void updateHighestCombo(int combo) {
        if (combo > highestCombo) {
            highestCombo = combo;
        }
    }

    public void updateBestStyleRank(String rank) {
        // Compare ranks: SSS > SS > S > A > B > C > D
        if (compareStyleRanks(rank, bestStyleRank) > 0) {
            bestStyleRank = rank;
        }
    }

    private int compareStyleRanks(String a, String b) {
        int scoreA = getStyleRankScore(a);
        int scoreB = getStyleRankScore(b);
        return Integer.compare(scoreA, scoreB);
    }

    private int getStyleRankScore(String rank) {
        if (rank == null || rank.isEmpty()) return 0;
        return switch (rank.toUpperCase()) {
            case "SSS" -> 7;
            case "SS" -> 6;
            case "S" -> 5;
            case "A" -> 4;
            case "B" -> 3;
            case "C" -> 2;
            case "D" -> 1;
            default -> 0;
        };
    }

    // === Getters ===

    public UUID getPlayerId() {
        return playerId;
    }

    public int getSeasonNumber() {
        return seasonNumber;
    }

    public int getTotalKills() {
        return totalKills;
    }

    public int getTotalWaves() {
        return totalWaves;
    }

    public int getBossKills() {
        return bossKills;
    }

    public int getChallengesCompleted() {
        return challengesCompleted;
    }

    public int getHighestCombo() {
        return highestCombo;
    }

    public String getBestStyleRank() {
        return bestStyleRank;
    }

    public Set<Integer> getClaimedFreeRewards() {
        return Set.copyOf(claimedFreeRewards);
    }

    public Set<Integer> getClaimedPremiumRewards() {
        return Set.copyOf(claimedPremiumRewards);
    }

    /**
     * Get a summary for UI display.
     */
    public ProgressSummary getSummary() {
        return new ProgressSummary(
            playerId,
            seasonNumber,
            getCurrentTier(),
            getTierProgress(),
            totalXP,
            getXPToNextTier(),
            premium,
            getTotalUnclaimedRewards(),
            hasXpBoost(),
            getXpBoostMultiplier(),
            getXpBoostRemainingSeconds()
        );
    }

    /**
     * Progress summary for UI.
     */
    public record ProgressSummary(
        UUID playerId,
        int seasonNumber,
        int currentTier,
        float tierProgress,
        int totalXP,
        int xpToNextTier,
        boolean premium,
        int unclaimedRewards,
        boolean hasBoost,
        float boostMultiplier,
        long boostRemainingSeconds
    ) {}

    /**
     * Season stats for end-of-season summary.
     */
    public record SeasonStats(
        int totalKills,
        int totalWaves,
        int bossKills,
        int challengesCompleted,
        int highestCombo,
        String bestStyleRank,
        int finalTier,
        int totalXP
    ) {}

    public SeasonStats getSeasonStats() {
        return new SeasonStats(
            totalKills,
            totalWaves,
            bossKills,
            challengesCompleted,
            highestCombo,
            bestStyleRank,
            getCurrentTier(),
            totalXP
        );
    }

    public int getCurrentXP() {
        return totalXP;
    }
}
