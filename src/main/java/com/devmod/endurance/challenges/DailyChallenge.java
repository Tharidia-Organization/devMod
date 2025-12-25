package com.devmod.endurance.challenges;

import com.devmod.endurance.EnduranceQuest;
import com.devmod.endurance.RewardSystem;
import net.minecraft.network.chat.Component;

import java.util.UUID;
import java.util.function.BiPredicate;

/**
 * Represents a daily challenge that players can complete for bonus rewards.
 *
 * Challenges rotate daily and provide engagement hooks for retention.
 * Players can complete each challenge once per day for bonus tokens and prestige.
 */
public class DailyChallenge {

    private final String id;
    private final String nameKey;
    private final String descriptionKey;
    private final ChallengeType type;
    private final ChallengeDifficulty difficulty;
    private final int targetValue;
    private final int tokenReward;
    private final int prestigeReward;
    private final BiPredicate<EnduranceQuest, ChallengeProgress> completionCheck;

    /**
     * Challenge types that determine how progress is tracked.
     */
    public enum ChallengeType {
        WAVE_REACH,         // Reach a specific wave
        KILLS,              // Kill a number of mobs
        STYLE_RANK,         // Achieve a style rank
        NO_DEATH,           // Complete without dying
        BOSS_KILLS,         // Kill a number of bosses
        COMBO,              // Achieve a combo count
        CRITICAL_KILLS,     // Kill with critical hits
        SPEED_RUN,          // Complete in time limit
        PERK_RESTRICTION,   // Complete with perk restrictions
        CURSE_RUN           // Complete with curse perks
    }

    /**
     * Challenge difficulty affects rewards and rotation probability.
     */
    public enum ChallengeDifficulty {
        EASY(1.0f, 0.4f),       // 40% chance, 1x rewards
        MEDIUM(1.5f, 0.35f),    // 35% chance, 1.5x rewards
        HARD(2.0f, 0.2f),       // 20% chance, 2x rewards
        EXTREME(3.0f, 0.05f);   // 5% chance, 3x rewards

        public final float rewardMultiplier;
        public final float rotationWeight;

        ChallengeDifficulty(float rewardMultiplier, float rotationWeight) {
            this.rewardMultiplier = rewardMultiplier;
            this.rotationWeight = rotationWeight;
        }
    }

    public DailyChallenge(
            String id,
            String nameKey,
            String descriptionKey,
            ChallengeType type,
            ChallengeDifficulty difficulty,
            int targetValue,
            int baseTokenReward,
            int basePrestigeReward,
            BiPredicate<EnduranceQuest, ChallengeProgress> completionCheck
    ) {
        this.id = id;
        this.nameKey = nameKey;
        this.descriptionKey = descriptionKey;
        this.type = type;
        this.difficulty = difficulty;
        this.targetValue = targetValue;
        this.tokenReward = (int) (baseTokenReward * difficulty.rewardMultiplier);
        this.prestigeReward = (int) (basePrestigeReward * difficulty.rewardMultiplier);
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

    public ChallengeType getType() {
        return type;
    }

    public ChallengeDifficulty getDifficulty() {
        return difficulty;
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
     * Check if the challenge is complete based on quest state and progress.
     */
    public boolean isComplete(EnduranceQuest quest, ChallengeProgress progress) {
        if (completionCheck != null) {
            return completionCheck.test(quest, progress);
        }
        return progress.getCurrentValue() >= targetValue;
    }

    /**
     * Calculate progress percentage (0.0 - 1.0).
     */
    public float getProgressPercent(ChallengeProgress progress) {
        if (targetValue <= 0) return 1.0f;
        return Math.min(1.0f, (float) progress.getCurrentValue() / targetValue);
    }

    /**
     * Tracks progress for a specific player on this challenge.
     */
    public static class ChallengeProgress {
        private final UUID playerId;
        private final String challengeId;
        private int currentValue;
        private boolean completed;
        private boolean rewarded;
        private long completedAt;

        public ChallengeProgress(UUID playerId, String challengeId) {
            this.playerId = playerId;
            this.challengeId = challengeId;
            this.currentValue = 0;
            this.completed = false;
            this.rewarded = false;
            this.completedAt = 0;
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
    }

    @Override
    public String toString() {
        return "DailyChallenge{" +
                "id='" + id + '\'' +
                ", type=" + type +
                ", difficulty=" + difficulty +
                ", target=" + targetValue +
                ", tokens=" + tokenReward +
                ", prestige=" + prestigeReward +
                '}';
    }
}
