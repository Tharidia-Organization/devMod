package com.devmod.endurance;

import java.util.List;
import java.util.UUID;
public record DirectiveChain(
    String id,
    String name,
    String description,
    ChainTheme theme,
    List<ChainStep> steps,
    int bonusTokens,
    int bonusPrestige,
    float completionMultiplier  // Applied to all wave rewards if chain completed
) {

    /**
     * Thematic category for chain flavor.
     */
    public enum ChainTheme {
        HUNT("Hunting a powerful target"),
        SIEGE("Defending against overwhelming force"),
        GAUNTLET("Facing elite challenges"),
        SWARM("Surviving escalating waves"),
        TACTICAL("Strategic objective sequences"),
        NIGHTMARE("Extreme difficulty challenges");

        public final String description;

        ChainTheme(String description) {
            this.description = description;
        }
    }

    /**
     * A single step in the directive chain.
     */
    public record ChainStep(
        int stepNumber,
        String title,
        String narrative,
        WaveDirective directive,
        float stepRewardMultiplier,
        @javax.annotation.Nullable ChainCondition condition  // Optional unlock condition
    ) {
        public static ChainStep of(int step, String title, String narrative, WaveDirective directive) {
            return new ChainStep(step, title, narrative, directive, 1.0f, null);
        }

        public static ChainStep of(int step, String title, String narrative, WaveDirective directive, float multiplier) {
            return new ChainStep(step, title, narrative, directive, multiplier, null);
        }

        public static ChainStep conditional(int step, String title, String narrative, WaveDirective directive, ChainCondition condition) {
            return new ChainStep(step, title, narrative, directive, 1.0f, condition);
        }
    }

    /**
     * Optional condition to unlock or modify a chain step.
     */
    public record ChainCondition(
        ConditionType type,
        int value
    ) {
        public enum ConditionType {
            MIN_KILLS_PREVIOUS,      // Must have killed X mobs in previous step
            NO_DEATHS_PREVIOUS,      // Must not have died in previous step
            MIN_COMBO_PREVIOUS,      // Must have reached X combo in previous step
            MIN_STYLE_RANK,          // Must be at least rank X (ordinal)
            FLAWLESS_STEP            // No damage taken in previous step
        }

        public static ChainCondition minKills(int kills) {
            return new ChainCondition(ConditionType.MIN_KILLS_PREVIOUS, kills);
        }

        public static ChainCondition noDeath() {
            return new ChainCondition(ConditionType.NO_DEATHS_PREVIOUS, 0);
        }

        public static ChainCondition minCombo(int combo) {
            return new ChainCondition(ConditionType.MIN_COMBO_PREVIOUS, combo);
        }

        public static ChainCondition minRank(int rankOrdinal) {
            return new ChainCondition(ConditionType.MIN_STYLE_RANK, rankOrdinal);
        }

        public static ChainCondition flawless() {
            return new ChainCondition(ConditionType.FLAWLESS_STEP, 0);
        }
    }

    /**
     * Tracks active chain progress for a quest.
     */
    public static class ChainProgress {
        private final UUID questId;
        private final DirectiveChain chain;
        private int currentStep;
        private boolean failed;
        private boolean completed;
        private int stepsCompletedFlawlessly;
        private int totalKillsInChain;
        private int maxComboInChain;
        private long chainStartTime;

        public ChainProgress(UUID questId, DirectiveChain chain) {
            this.questId = questId;
            this.chain = chain;
            this.currentStep = 0;
            this.failed = false;
            this.completed = false;
            this.stepsCompletedFlawlessly = 0;
            this.totalKillsInChain = 0;
            this.maxComboInChain = 0;
            this.chainStartTime = System.currentTimeMillis();
        }

        public UUID getQuestId() { return questId; }
        public DirectiveChain getChain() { return chain; }
        public int getCurrentStep() { return currentStep; }
        public boolean isFailed() { return failed; }
        public boolean isCompleted() { return completed; }
        public int getStepsCompletedFlawlessly() { return stepsCompletedFlawlessly; }
        public int getTotalKillsInChain() { return totalKillsInChain; }
        public int getMaxComboInChain() { return maxComboInChain; }
        public long getChainStartTime() { return chainStartTime; }

        public ChainStep getCurrentChainStep() {
            if (currentStep < chain.steps().size()) {
                return chain.steps().get(currentStep);
            }
            return null;
        }

        public boolean hasNextStep() {
            return currentStep < chain.steps().size() - 1;
        }

        public int getTotalSteps() {
            return chain.steps().size();
        }

        public float getProgressPercent() {
            return (float) currentStep / chain.steps().size();
        }

        /**
         * Advance to next step after successful completion.
         */
        public void advanceStep(int killsThisWave, int maxComboThisWave, boolean wasFlawless) {
            totalKillsInChain += killsThisWave;
            if (maxComboThisWave > maxComboInChain) {
                maxComboInChain = maxComboThisWave;
            }
            if (wasFlawless) {
                stepsCompletedFlawlessly++;
            }

            currentStep++;
            if (currentStep >= chain.steps().size()) {
                completed = true;
            }
        }

        /**
         * Mark chain as failed (abandonment or condition failure).
         */
        public void markFailed() {
            this.failed = true;
        }

        /**
         * Check if a condition is met for the next step.
         */
        public boolean checkCondition(ChainCondition condition, int lastWaveKills, int lastWaveCombo,
                                       boolean diedLastWave, boolean tookDamageLastWave, int currentRankOrdinal) {
            if (condition == null) return true;

            return switch (condition.type()) {
                case MIN_KILLS_PREVIOUS -> lastWaveKills >= condition.value();
                case NO_DEATHS_PREVIOUS -> !diedLastWave;
                case MIN_COMBO_PREVIOUS -> lastWaveCombo >= condition.value();
                case MIN_STYLE_RANK -> currentRankOrdinal >= condition.value();
                case FLAWLESS_STEP -> !tookDamageLastWave;
            };
        }
    }

    /**
     * Calculate total potential reward for completing entire chain.
     */
    public int getTotalBonusTokens() {
        return bonusTokens;
    }

    /**
     * Calculate total potential prestige for completing entire chain.
     */
    public int getTotalBonusPrestige() {
        return bonusPrestige;
    }

    /**
     * Get difficulty rating (1-5 based on step count and conditions).
     */
    public int getDifficultyRating() {
        int rating = steps.size() - 2;  // Base: 3 steps = 1, 5 steps = 3
        int conditionalSteps = (int) steps.stream()
            .filter(s -> s.condition() != null)
            .count();
        rating += conditionalSteps;
        return Math.max(1, Math.min(5, rating));
    }
}
