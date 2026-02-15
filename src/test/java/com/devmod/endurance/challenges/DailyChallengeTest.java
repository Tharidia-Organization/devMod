package com.devmod.endurance.challenges;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.devmod.endurance.challenges.DailyChallenge.ChallengeDifficulty;
import com.devmod.endurance.challenges.DailyChallenge.ChallengeProgress;
import com.devmod.endurance.challenges.DailyChallenge.ChallengeType;

import static org.junit.jupiter.api.Assertions.*;

class DailyChallengeTest {

    @Nested
    @DisplayName("ChallengeType")
    class ChallengeTypeTests {
        @Test
        @DisplayName("Ten challenge types are defined")
        void tenTypesDefined() {
            assertEquals(10, ChallengeType.values().length);
        }
    }

    @Nested
    @DisplayName("ChallengeDifficulty")
    class ChallengeDifficultyTests {
        @Test
        @DisplayName("Four difficulty levels are defined")
        void fourDifficulties() {
            assertEquals(4, ChallengeDifficulty.values().length);
        }

        @Test
        @DisplayName("Reward multipliers increase with difficulty")
        void rewardMultipliersIncrease() {
            assertTrue(ChallengeDifficulty.EASY.getRewardMultiplier() < ChallengeDifficulty.MEDIUM.getRewardMultiplier());
            assertTrue(ChallengeDifficulty.MEDIUM.getRewardMultiplier() < ChallengeDifficulty.HARD.getRewardMultiplier());
            assertTrue(ChallengeDifficulty.HARD.getRewardMultiplier() < ChallengeDifficulty.EXTREME.getRewardMultiplier());
        }

        @Test
        @DisplayName("Rotation weights sum close to 1.0")
        void rotationWeightsSum() {
            float sum = 0;
            for (ChallengeDifficulty d : ChallengeDifficulty.values()) {
                sum += d.getRotationWeight();
            }
            assertEquals(1.0f, sum, 0.01f);
        }

        @Test
        @DisplayName("EASY has 1x reward multiplier")
        void easyMultiplier() {
            assertEquals(1.0f, ChallengeDifficulty.EASY.getRewardMultiplier(), 0.001f);
        }

        @Test
        @DisplayName("EXTREME has 3x reward multiplier")
        void extremeMultiplier() {
            assertEquals(3.0f, ChallengeDifficulty.EXTREME.getRewardMultiplier(), 0.001f);
        }
    }

    @Nested
    @DisplayName("DailyChallenge rewards")
    class RewardTests {
        @Test
        @DisplayName("Token reward is base * difficulty multiplier")
        void tokenRewardScaled() {
            DailyChallenge easy = new DailyChallenge(
                "test", "name", "desc", ChallengeType.KILLS,
                ChallengeDifficulty.EASY, 10, 100, 10, null
            );
            assertEquals(100, easy.getTokenReward()); // 100 * 1.0

            DailyChallenge hard = new DailyChallenge(
                "test", "name", "desc", ChallengeType.KILLS,
                ChallengeDifficulty.HARD, 10, 100, 10, null
            );
            assertEquals(200, hard.getTokenReward()); // 100 * 2.0
        }

        @Test
        @DisplayName("Prestige reward is base * difficulty multiplier")
        void prestigeRewardScaled() {
            DailyChallenge extreme = new DailyChallenge(
                "test", "name", "desc", ChallengeType.BOSS_KILLS,
                ChallengeDifficulty.EXTREME, 5, 50, 20, null
            );
            assertEquals(60, extreme.getPrestigeReward()); // 20 * 3.0
        }
    }

    @Nested
    @DisplayName("ChallengeProgress")
    class ChallengeProgressTests {
        @Test
        @DisplayName("Initial state is zeroed")
        void initialState() {
            ChallengeProgress p = new ChallengeProgress(UUID.randomUUID(), "test_challenge");
            assertEquals(0, p.getCurrentValue());
            assertFalse(p.isCompleted());
            assertFalse(p.isRewarded());
            assertEquals(0L, p.getCompletedAt());
        }

        @Test
        @DisplayName("increment() increases by 1")
        void incrementByOne() {
            ChallengeProgress p = new ChallengeProgress(UUID.randomUUID(), "test");
            p.increment();
            assertEquals(1, p.getCurrentValue());
            p.increment();
            assertEquals(2, p.getCurrentValue());
        }

        @Test
        @DisplayName("increment(amount) increases by specified amount")
        void incrementByAmount() {
            ChallengeProgress p = new ChallengeProgress(UUID.randomUUID(), "test");
            p.increment(5);
            assertEquals(5, p.getCurrentValue());
        }

        @Test
        @DisplayName("setCurrentValue sets directly")
        void setValueDirectly() {
            ChallengeProgress p = new ChallengeProgress(UUID.randomUUID(), "test");
            p.setCurrentValue(42);
            assertEquals(42, p.getCurrentValue());
        }

        @Test
        @DisplayName("markCompleted sets flag and timestamp")
        void markCompleted() {
            ChallengeProgress p = new ChallengeProgress(UUID.randomUUID(), "test");
            p.markCompleted();
            assertTrue(p.isCompleted());
            assertTrue(p.getCompletedAt() > 0);
        }

        @Test
        @DisplayName("markRewarded sets flag")
        void markRewarded() {
            ChallengeProgress p = new ChallengeProgress(UUID.randomUUID(), "test");
            p.markRewarded();
            assertTrue(p.isRewarded());
        }
    }

    @Nested
    @DisplayName("Completion and progress")
    class CompletionTests {
        @Test
        @DisplayName("isComplete uses default check when no predicate")
        void isCompleteDefaultCheck() {
            DailyChallenge challenge = new DailyChallenge(
                "test", "name", "desc", ChallengeType.KILLS,
                ChallengeDifficulty.EASY, 10, 100, 10, null
            );
            ChallengeProgress p = new ChallengeProgress(UUID.randomUUID(), "test");
            assertFalse(challenge.isComplete(null, p));
            p.setCurrentValue(10);
            assertTrue(challenge.isComplete(null, p));
        }

        @Test
        @DisplayName("getProgressPercent calculates correctly")
        void progressPercent() {
            DailyChallenge challenge = new DailyChallenge(
                "test", "name", "desc", ChallengeType.KILLS,
                ChallengeDifficulty.EASY, 20, 100, 10, null
            );
            ChallengeProgress p = new ChallengeProgress(UUID.randomUUID(), "test");
            assertEquals(0f, challenge.getProgressPercent(p), 0.001f);

            p.setCurrentValue(10);
            assertEquals(0.5f, challenge.getProgressPercent(p), 0.001f);

            p.setCurrentValue(20);
            assertEquals(1.0f, challenge.getProgressPercent(p), 0.001f);
        }

        @Test
        @DisplayName("getProgressPercent clamps at 1.0")
        void progressPercentClamped() {
            DailyChallenge challenge = new DailyChallenge(
                "test", "name", "desc", ChallengeType.KILLS,
                ChallengeDifficulty.EASY, 10, 100, 10, null
            );
            ChallengeProgress p = new ChallengeProgress(UUID.randomUUID(), "test");
            p.setCurrentValue(50); // Over target
            assertEquals(1.0f, challenge.getProgressPercent(p), 0.001f);
        }

        @Test
        @DisplayName("getProgressPercent returns 1.0 for zero target")
        void progressPercentZeroTarget() {
            DailyChallenge challenge = new DailyChallenge(
                "test", "name", "desc", ChallengeType.NO_DEATH,
                ChallengeDifficulty.EASY, 0, 100, 10, null
            );
            ChallengeProgress p = new ChallengeProgress(UUID.randomUUID(), "test");
            assertEquals(1.0f, challenge.getProgressPercent(p), 0.001f);
        }
    }

    @Test
    @DisplayName("Getters return correct values")
    void gettersCorrect() {
        DailyChallenge challenge = new DailyChallenge(
            "test_id", "test.name", "test.desc", ChallengeType.WAVE_REACH,
            ChallengeDifficulty.MEDIUM, 15, 200, 30, null
        );
        assertEquals("test_id", challenge.getId());
        assertEquals("test.name", challenge.getNameKey());
        assertEquals("test.desc", challenge.getDescriptionKey());
        assertEquals(ChallengeType.WAVE_REACH, challenge.getType());
        assertEquals(ChallengeDifficulty.MEDIUM, challenge.getDifficulty());
        assertEquals(15, challenge.getTargetValue());
    }
}
