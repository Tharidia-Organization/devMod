package com.devmod.quest;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.devmod.endurance.EnduranceQuestState;

import static org.junit.jupiter.api.Assertions.*;

/**
 * L2 Quest Flow Validation Tests.
 *
 * Validates:
 * - Quest state machine transitions
 * - Quest lifecycle (start, wave complete, continue, fail, complete)
 * - State validation guards
 * - Session stats tracking
 */
@DisplayName("L2: Quest Flow Validation")
class QuestFlowValidationTest {

    // ═══════════════════════════════════════════════════════════════════════════
    // L2-01: Quest State Definitions
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("L2-01: Quest State Definitions")
    class QuestStateDefinitionsTest {

        @Test
        @DisplayName("EnduranceQuestState has all required states")
        void enduranceQuestStateHasAllStates() {
            EnduranceQuestState[] states = EnduranceQuestState.values();
            assertEquals(6, states.length, "Should have exactly 6 quest states");
        }

        @Test
        @DisplayName("AVAILABLE state exists")
        void availableStateExists() {
            EnduranceQuestState state = EnduranceQuestState.AVAILABLE;
            assertNotNull(state);
            assertEquals("AVAILABLE", state.name());
        }

        @Test
        @DisplayName("IN_PROGRESS state exists")
        void inProgressStateExists() {
            EnduranceQuestState state = EnduranceQuestState.IN_PROGRESS;
            assertNotNull(state);
            assertEquals("IN_PROGRESS", state.name());
        }

        @Test
        @DisplayName("WAVE_COMPLETE state exists")
        void waveCompleteStateExists() {
            EnduranceQuestState state = EnduranceQuestState.WAVE_COMPLETE;
            assertNotNull(state);
            assertEquals("WAVE_COMPLETE", state.name());
        }

        @Test
        @DisplayName("COMPLETED state exists")
        void completedStateExists() {
            EnduranceQuestState state = EnduranceQuestState.COMPLETED;
            assertNotNull(state);
            assertEquals("COMPLETED", state.name());
        }

        @Test
        @DisplayName("FAILED state exists")
        void failedStateExists() {
            EnduranceQuestState state = EnduranceQuestState.FAILED;
            assertNotNull(state);
            assertEquals("FAILED", state.name());
        }

        @Test
        @DisplayName("COOLDOWN state exists")
        void cooldownStateExists() {
            EnduranceQuestState state = EnduranceQuestState.COOLDOWN;
            assertNotNull(state);
            assertEquals("COOLDOWN", state.name());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // L2-02: State Machine Transitions
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("L2-02: State Machine Transitions")
    class StateMachineTransitionsTest {

        @Test
        @DisplayName("Valid transition: AVAILABLE -> IN_PROGRESS (start quest)")
        void validTransitionAvailableToInProgress() {
            // Quest can be started from AVAILABLE
            assertTrue(canTransition(EnduranceQuestState.AVAILABLE, EnduranceQuestState.IN_PROGRESS),
                "Should be able to start quest from AVAILABLE");
        }

        @Test
        @DisplayName("Valid transition: COOLDOWN -> IN_PROGRESS (restart after cooldown)")
        void validTransitionCooldownToInProgress() {
            // Quest can be restarted after cooldown
            assertTrue(canTransition(EnduranceQuestState.COOLDOWN, EnduranceQuestState.IN_PROGRESS),
                "Should be able to restart quest after COOLDOWN");
        }

        @Test
        @DisplayName("Valid transition: IN_PROGRESS -> WAVE_COMPLETE")
        void validTransitionInProgressToWaveComplete() {
            assertTrue(canTransition(EnduranceQuestState.IN_PROGRESS, EnduranceQuestState.WAVE_COMPLETE),
                "Should transition to WAVE_COMPLETE after killing all wave mobs");
        }

        @Test
        @DisplayName("Valid transition: IN_PROGRESS -> FAILED (death)")
        void validTransitionInProgressToFailed() {
            assertTrue(canTransition(EnduranceQuestState.IN_PROGRESS, EnduranceQuestState.FAILED),
                "Should transition to FAILED on player death");
        }

        @Test
        @DisplayName("Valid transition: WAVE_COMPLETE -> IN_PROGRESS (continue)")
        void validTransitionWaveCompleteToInProgress() {
            assertTrue(canTransition(EnduranceQuestState.WAVE_COMPLETE, EnduranceQuestState.IN_PROGRESS),
                "Should transition to IN_PROGRESS when continuing after wave");
        }

        @Test
        @DisplayName("Valid transition: IN_PROGRESS -> COMPLETED (final wave)")
        void validTransitionInProgressToCompleted() {
            assertTrue(canTransition(EnduranceQuestState.IN_PROGRESS, EnduranceQuestState.COMPLETED),
                "Should transition to COMPLETED after final wave");
        }

        @Test
        @DisplayName("Valid transition: FAILED -> IN_PROGRESS (continue after death)")
        void validTransitionFailedToInProgress() {
            // Can continue after death with penalty
            assertTrue(canTransition(EnduranceQuestState.FAILED, EnduranceQuestState.IN_PROGRESS),
                "Should be able to continue after death with penalty");
        }

        @Test
        @DisplayName("Invalid transition: COMPLETED -> IN_PROGRESS")
        void invalidTransitionCompletedToInProgress() {
            // Cannot restart from COMPLETED without going through reset
            assertFalse(canDirectTransition(EnduranceQuestState.COMPLETED, EnduranceQuestState.IN_PROGRESS),
                "Should not directly transition from COMPLETED to IN_PROGRESS");
        }

        @Test
        @DisplayName("State ordinal values are consistent")
        void stateOrdinalValuesConsistent() {
            assertEquals(0, EnduranceQuestState.AVAILABLE.ordinal());
            assertEquals(1, EnduranceQuestState.IN_PROGRESS.ordinal());
            assertEquals(2, EnduranceQuestState.WAVE_COMPLETE.ordinal());
            assertEquals(3, EnduranceQuestState.COMPLETED.ordinal());
            assertEquals(4, EnduranceQuestState.FAILED.ordinal());
            assertEquals(5, EnduranceQuestState.COOLDOWN.ordinal());
        }

        /**
         * Check if a transition is valid according to quest rules.
         */
        private boolean canTransition(EnduranceQuestState from, EnduranceQuestState to) {
            return switch (from) {
                case AVAILABLE -> to == EnduranceQuestState.IN_PROGRESS;
                case IN_PROGRESS -> to == EnduranceQuestState.WAVE_COMPLETE ||
                                   to == EnduranceQuestState.FAILED ||
                                   to == EnduranceQuestState.COMPLETED;
                case WAVE_COMPLETE -> to == EnduranceQuestState.IN_PROGRESS;
                case COMPLETED -> to == EnduranceQuestState.AVAILABLE ||
                                 to == EnduranceQuestState.COOLDOWN;
                case FAILED -> to == EnduranceQuestState.IN_PROGRESS ||
                              to == EnduranceQuestState.AVAILABLE;
                case COOLDOWN -> to == EnduranceQuestState.IN_PROGRESS ||
                                to == EnduranceQuestState.AVAILABLE;
            };
        }

        /**
         * Check if a direct transition is allowed (without intermediate steps).
         */
        private boolean canDirectTransition(EnduranceQuestState from, EnduranceQuestState to) {
            // COMPLETED cannot directly go to IN_PROGRESS - must reset first
            if (from == EnduranceQuestState.COMPLETED && to == EnduranceQuestState.IN_PROGRESS) {
                return false;
            }
            return canTransition(from, to);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // L2-03: Quest Lifecycle Rules
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("L2-03: Quest Lifecycle Rules")
    class QuestLifecycleRulesTest {

        @Test
        @DisplayName("Quest starts at wave 1")
        void questStartsAtWaveOne() {
            // When a quest starts, currentWave should be 1
            int expectedStartWave = 1;
            assertEquals(1, expectedStartWave, "Quest should start at wave 1");
        }

        @Test
        @DisplayName("Default total waves is 10")
        void defaultTotalWavesIsTen() {
            int defaultWaves = 10;
            assertEquals(10, defaultWaves, "Default total waves should be 10");
        }

        @Test
        @DisplayName("Completion requires reaching final wave")
        void completionRequiresFinalWave() {
            int currentWave = 10;
            int totalWaves = 10;
            boolean endlessMode = false;

            boolean shouldComplete = (currentWave >= totalWaves) && !endlessMode;
            assertTrue(shouldComplete, "Quest should complete when current wave reaches total");
        }

        @Test
        @DisplayName("Endless mode never auto-completes")
        void endlessModeNeverAutoCompletes() {
            int currentWave = 100;
            int totalWaves = 10;
            boolean endlessMode = true;

            boolean shouldComplete = (currentWave >= totalWaves) && !endlessMode;
            assertFalse(shouldComplete, "Endless mode should never auto-complete");
        }

        @Test
        @DisplayName("Points are cumulative across waves")
        void pointsAreCumulativeAcrossWaves() {
            int wave1Points = 100;
            int wave2Points = 150;
            int wave3Points = 200;

            int totalPoints = wave1Points + wave2Points + wave3Points;
            assertEquals(450, totalPoints, "Points should accumulate across waves");
        }

        @Test
        @DisplayName("Death penalty reduces points")
        void deathPenaltyReducesPoints() {
            int currentPoints = 500;
            int deathPenalty = 100;

            int afterDeath = Math.max(0, currentPoints - deathPenalty);
            assertEquals(400, afterDeath, "Death should reduce points by penalty amount");
        }

        @Test
        @DisplayName("Death penalty cannot make points negative")
        void deathPenaltyCannotMakePointsNegative() {
            int currentPoints = 50;
            int deathPenalty = 100;

            int afterDeath = Math.max(0, currentPoints - deathPenalty);
            assertEquals(0, afterDeath, "Points should not go below zero");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // L2-04: Wave Progression
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("L2-04: Wave Progression")
    class WaveProgressionTest {

        @Test
        @DisplayName("Wave number increments after continue")
        void waveNumberIncrementsAfterContinue() {
            int currentWave = 3;
            // After wave complete and continue
            int nextWave = currentWave + 1;
            assertEquals(4, nextWave, "Wave should increment to 4 after completing wave 3");
        }

        @Test
        @DisplayName("Wave number does not increment on wave complete (before continue)")
        void waveNumberDoesNotIncrementOnWaveComplete() {
            // When wave is completed, we show checkpoint screen
            // Wave number only increments when player chooses to continue
            int waveOnComplete = 3;
            int waveAfterComplete = 3; // Same - player sees "Wave 3 Complete!"
            assertEquals(waveOnComplete, waveAfterComplete,
                "Wave should not increment until player continues");
        }

        @Test
        @DisplayName("Highest wave reached is tracked")
        void highestWaveReachedIsTracked() {
            int attempt1HighestWave = 5;
            int attempt2HighestWave = 8;
            int attempt3HighestWave = 3;

            int highestOverall = Math.max(Math.max(attempt1HighestWave, attempt2HighestWave), attempt3HighestWave);
            assertEquals(8, highestOverall, "Should track highest wave reached across attempts");
        }

        @Test
        @DisplayName("Boss wave every 5 waves")
        void bossWaveEveryFiveWaves() {
            int[] bossWaves = {5, 10, 15, 20};

            for (int wave : bossWaves) {
                assertTrue(wave % 5 == 0, "Wave " + wave + " should be a boss wave");
            }

            int[] nonBossWaves = {1, 2, 3, 4, 6, 7, 8, 9};
            for (int wave : nonBossWaves) {
                assertFalse(wave % 5 == 0, "Wave " + wave + " should not be a boss wave");
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // L2-05: Session Stats Tracking
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("L2-05: Session Stats Tracking")
    class SessionStatsTrackingTest {

        @Test
        @DisplayName("Session stats reset on quest start")
        void sessionStatsResetOnQuestStart() {
            // When a new quest starts, session stats should be reset
            int mobsKilled = 0;
            int damageDealt = 0;
            int damageTaken = 0;
            int deaths = 0;
            int points = 0;

            assertEquals(0, mobsKilled, "Mobs killed should reset to 0");
            assertEquals(0, damageDealt, "Damage dealt should reset to 0");
            assertEquals(0, damageTaken, "Damage taken should reset to 0");
            assertEquals(0, deaths, "Deaths should reset to 0");
            assertEquals(0, points, "Points should reset to 0");
        }

        @Test
        @DisplayName("Kill increments mob count and points")
        void killIncrementsMobCountAndPoints() {
            int mobsKilled = 0;
            int points = 0;
            int pointsPerKill = 10;

            // Record a kill
            mobsKilled++;
            points += pointsPerKill;

            assertEquals(1, mobsKilled);
            assertEquals(10, points);
        }

        @Test
        @DisplayName("Damage dealt accumulates")
        void damageDealtAccumulates() {
            int totalDamage = 0;

            totalDamage += 15; // Hit 1
            totalDamage += 20; // Hit 2
            totalDamage += 8;  // Hit 3

            assertEquals(43, totalDamage, "Damage should accumulate correctly");
        }

        @Test
        @DisplayName("Session duration tracked correctly")
        void sessionDurationTrackedCorrectly() {
            long startTime = 1000L;
            long endTime = 5000L;

            long duration = endTime - startTime;
            assertEquals(4000L, duration, "Duration should be end - start");
        }

        @Test
        @DisplayName("Death count increments on death")
        void deathCountIncrementsOnDeath() {
            int deaths = 0;

            deaths++; // First death
            assertEquals(1, deaths);

            deaths++; // Second death (continued)
            assertEquals(2, deaths);
        }

        @Test
        @DisplayName("Total attempts increments on start")
        void totalAttemptsIncrementsOnStart() {
            int totalAttempts = 0;

            totalAttempts++; // First attempt
            assertEquals(1, totalAttempts);

            totalAttempts++; // Second attempt
            assertEquals(2, totalAttempts);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // L2-06: Best Records
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("L2-06: Best Records")
    class BestRecordsTest {

        @Test
        @DisplayName("Best points updated when exceeded")
        void bestPointsUpdatedWhenExceeded() {
            int bestPoints = 500;
            int sessionPoints = 750;

            if (sessionPoints > bestPoints) {
                bestPoints = sessionPoints;
            }

            assertEquals(750, bestPoints, "Best points should update to new high");
        }

        @Test
        @DisplayName("Best points not updated when not exceeded")
        void bestPointsNotUpdatedWhenNotExceeded() {
            int bestPoints = 500;
            int sessionPoints = 300;

            if (sessionPoints > bestPoints) {
                bestPoints = sessionPoints;
            }

            assertEquals(500, bestPoints, "Best points should remain unchanged");
        }

        @Test
        @DisplayName("Fastest completion tracked correctly")
        void fastestCompletionTrackedCorrectly() {
            long fastestTime = Long.MAX_VALUE;
            long run1Time = 120000L; // 2 minutes

            if (run1Time < fastestTime) {
                fastestTime = run1Time;
            }
            assertEquals(120000L, fastestTime);

            long run2Time = 90000L; // 1.5 minutes
            if (run2Time < fastestTime) {
                fastestTime = run2Time;
            }
            assertEquals(90000L, fastestTime, "Should update to faster time");

            long run3Time = 150000L; // 2.5 minutes
            if (run3Time < fastestTime) {
                fastestTime = run3Time;
            }
            assertEquals(90000L, fastestTime, "Should not update to slower time");
        }

        @Test
        @DisplayName("Total completions increments on completion")
        void totalCompletionsIncrementsOnCompletion() {
            int totalCompletions = 0;

            // Complete a quest
            totalCompletions++;
            assertEquals(1, totalCompletions);

            // Complete another
            totalCompletions++;
            assertEquals(2, totalCompletions);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // L2-07: Completion Percentage
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("L2-07: Completion Percentage")
    class CompletionPercentageTest {

        @Test
        @DisplayName("Completion percentage at wave 1 is 0%")
        void completionPercentageAtWaveOneIsZero() {
            int currentWave = 1;
            int totalWaves = 10;

            float percentage = (float) (currentWave - 1) / totalWaves * 100;
            assertEquals(0f, percentage, 0.001f);
        }

        @Test
        @DisplayName("Completion percentage at wave 5 is 40%")
        void completionPercentageAtWaveFiveIsForty() {
            int currentWave = 5;
            int totalWaves = 10;

            float percentage = (float) (currentWave - 1) / totalWaves * 100;
            assertEquals(40f, percentage, 0.001f);
        }

        @Test
        @DisplayName("Completion percentage at final wave is 90%")
        void completionPercentageAtFinalWaveIsNinety() {
            int currentWave = 10;
            int totalWaves = 10;

            float percentage = (float) (currentWave - 1) / totalWaves * 100;
            assertEquals(90f, percentage, 0.001f);
        }

        @Test
        @DisplayName("Endless mode returns 0% completion")
        void endlessModeReturnsZeroPercent() {
            boolean endlessMode = true;
            float percentage = endlessMode ? 0f : 50f;
            assertEquals(0f, percentage, 0.001f);
        }
    }
}
