package com.devmod.endurance.challenges;

import java.util.UUID;
import java.util.function.BiPredicate;

import net.minecraft.network.chat.Component;
public class WeeklyChallenge {

    private final String id;
    private final String nameKey;
    private final String descriptionKey;
    private final WeeklyChallengeType type;
    private final WeeklyChallengeTier tier;
    private final int targetValue;
    private final int tokenReward;
    private final int prestigeReward;
    private final BiPredicate<WeeklyProgress, Integer> completionCheck;

    /**
     * Weekly challenge types for cumulative tracking across sessions.
     */
    public enum WeeklyChallengeType {
        TOTAL_KILLS,           // Cumulative kills over the week
        TOTAL_WAVES,           // Cumulative waves completed
        TOTAL_RUNS,            // Number of quest completions
        PERFECT_RUNS,          // Runs completed without dying
        BOSS_SLAYER,           // Total bosses killed
        STYLE_MASTER,          // Reach a style rank X times
        COMBO_MASTER,          // Achieve high combo X times
        PRESTIGE_GAIN,         // Earn X prestige over the week
        TOKEN_GAIN,            // Earn X tokens over the week
        FLAWLESS_WAVES,        // Complete X waves without taking damage
        MULTI_BOSS_RUN,        // Kill X bosses in a single run
        ENDLESS_WARRIOR        // Reach wave X in endless mode
    }

    /**
     * Weekly challenge tiers with scaling rewards.
     */
    public enum WeeklyChallengeTier {
        STANDARD(1.0f, 0.5f),      // 50% chance, 1x rewards (base: 2500 tokens, 15 prestige)
        EPIC(2.0f, 0.35f),         // 35% chance, 2x rewards
        LEGENDARY(4.0f, 0.15f);    // 15% chance, 4x rewards

        public final float rewardMultiplier;
        public final float rotationWeight;

        WeeklyChallengeTier(float rewardMultiplier, float rotationWeight) {
            this.rewardMultiplier = rewardMultiplier;
            this.rotationWeight = rotationWeight;
        }
    }

    public WeeklyChallenge(
            String id,
            String nameKey,
            String descriptionKey,
            WeeklyChallengeType type,
            WeeklyChallengeTier tier,
            int targetValue,
            int baseTokenReward,
            int basePrestigeReward,
            BiPredicate<WeeklyProgress, Integer> completionCheck
    ) {
        this.id = id;
        this.nameKey = nameKey;
        this.descriptionKey = descriptionKey;
        this.type = type;
        this.tier = tier;
        this.targetValue = targetValue;
        this.tokenReward = (int) (baseTokenReward * tier.rewardMultiplier);
        this.prestigeReward = (int) (basePrestigeReward * tier.rewardMultiplier);
        this.completionCheck = completionCheck;
    }

    // ========== Getters ==========

    public String getId() {
        return id;
    }

    public String getNameKey() {
        return nameKey;
    }

    public String getDescriptionKey() {
        return descriptionKey;
    }

    public Component getName() {
        return Component.translatable(nameKey);
    }

    public Component getDescription() {
        return Component.translatable(descriptionKey, targetValue);
    }

    public WeeklyChallengeType getType() {
        return type;
    }

    public WeeklyChallengeTier getTier() {
        return tier;
    }

    public int getTargetValue() {
        return targetValue;
    }

    public int getTokenReward() {
        return tokenReward;
    }

    public int getPrestigeReward() {
        return prestigeReward;
    }

    /**
     * Check if the challenge is complete based on weekly progress.
     */
    public boolean isComplete(WeeklyProgress progress) {
        if (completionCheck != null) {
            return completionCheck.test(progress, targetValue);
        }
        return progress.getCurrentValue() >= targetValue;
    }

    /**
     * Calculate progress percentage (0.0 - 1.0).
     */
    public float getProgressPercent(WeeklyProgress progress) {
        if (targetValue <= 0) return 1.0f;
        return Math.min(1.0f, (float) progress.getCurrentValue() / targetValue);
    }

    /**
     * Tracks cumulative weekly progress for a player.
     */
    public static class WeeklyProgress {
        private final UUID playerId;
        private final String challengeId;
        private int currentValue;
        private boolean completed;
        private boolean rewarded;
        private long completedAt;

        // Additional tracking for complex challenges
        private int highestComboThisWeek;
        private int highestWaveThisWeek;
        private int timesReachedTargetRank;
        private int perfectRunsThisWeek;

        public WeeklyProgress(UUID playerId, String challengeId) {
            this.playerId = playerId;
            this.challengeId = challengeId;
            this.currentValue = 0;
            this.completed = false;
            this.rewarded = false;
            this.completedAt = 0;
            this.highestComboThisWeek = 0;
            this.highestWaveThisWeek = 0;
            this.timesReachedTargetRank = 0;
            this.perfectRunsThisWeek = 0;
        }

        public UUID getPlayerId() {
            return playerId;
        }

        public String getChallengeId() {
            return challengeId;
        }

        public int getCurrentValue() {
            return currentValue;
        }

        public void setCurrentValue(int value) {
            this.currentValue = value;
        }

        public void increment() {
            this.currentValue++;
        }

        public void increment(int amount) {
            this.currentValue += amount;
        }

        public boolean isCompleted() {
            return completed;
        }

        public void markCompleted() {
            this.completed = true;
            this.completedAt = System.currentTimeMillis();
        }

        public boolean isRewarded() {
            return rewarded;
        }

        public void markRewarded() {
            this.rewarded = true;
        }

        public long getCompletedAt() {
            return completedAt;
        }

        // Extended tracking methods
        public int getHighestComboThisWeek() {
            return highestComboThisWeek;
        }

        public void updateHighestCombo(int combo) {
            if (combo > highestComboThisWeek) {
                highestComboThisWeek = combo;
            }
        }

        public int getHighestWaveThisWeek() {
            return highestWaveThisWeek;
        }

        public void updateHighestWave(int wave) {
            if (wave > highestWaveThisWeek) {
                highestWaveThisWeek = wave;
            }
        }

        public int getTimesReachedTargetRank() {
            return timesReachedTargetRank;
        }

        public void incrementRankAchievement() {
            timesReachedTargetRank++;
        }

        public int getPerfectRunsThisWeek() {
            return perfectRunsThisWeek;
        }

        public void incrementPerfectRuns() {
            perfectRunsThisWeek++;
        }
    }

    @Override
    public String toString() {
        return "WeeklyChallenge{" +
                "id='" + id + '\'' +
                ", type=" + type +
                ", tier=" + tier +
                ", target=" + targetValue +
                ", tokens=" + tokenReward +
                ", prestige=" + prestigeReward +
                '}';
    }
}
