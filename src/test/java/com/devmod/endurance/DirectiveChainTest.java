package com.devmod.endurance;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.devmod.endurance.DirectiveChain.*;

import static org.junit.jupiter.api.Assertions.*;

class DirectiveChainTest {

    private static WaveDirective testDirective(String id) {
        return new WaveDirective(id, "Test", "Test desc", 1.0f, 1.0f, 1.0f, null, null);
    }

    private static DirectiveChain threeStepChain() {
        return new DirectiveChain(
            "test_chain", "Test Chain", "A test chain",
            ChainTheme.HUNT,
            List.of(
                ChainStep.of(1, "Step 1", "First", testDirective("d1")),
                ChainStep.of(2, "Step 2", "Second", testDirective("d2")),
                ChainStep.of(3, "Step 3", "Third", testDirective("d3"))
            ),
            100, 50, 1.5f
        );
    }

    @Nested
    @DisplayName("ChainTheme")
    class ChainThemeTests {
        @Test
        @DisplayName("All themes have descriptions")
        void allThemesHaveDescriptions() {
            for (ChainTheme theme : ChainTheme.values()) {
                assertNotNull(theme.getDescription());
                assertFalse(theme.getDescription().isEmpty());
            }
        }

        @Test
        @DisplayName("Six themes are defined")
        void sixThemes() {
            assertEquals(6, ChainTheme.values().length);
        }
    }

    @Nested
    @DisplayName("ChainStep")
    class ChainStepTests {
        @Test
        @DisplayName("Factory method creates step with default multiplier and no condition")
        void factoryDefaultStep() {
            ChainStep step = ChainStep.of(1, "Title", "Narrative", testDirective("d1"));
            assertEquals(1, step.stepNumber());
            assertEquals("Title", step.title());
            assertEquals(1.0f, step.stepRewardMultiplier(), 0.001f);
            assertNull(step.condition());
        }

        @Test
        @DisplayName("Factory with multiplier sets reward correctly")
        void factoryWithMultiplier() {
            ChainStep step = ChainStep.of(2, "Title", "Narrative", testDirective("d2"), 1.5f);
            assertEquals(1.5f, step.stepRewardMultiplier(), 0.001f);
        }

        @Test
        @DisplayName("Conditional step has condition")
        void conditionalStep() {
            ChainCondition cond = ChainCondition.minKills(10);
            ChainStep step = ChainStep.conditional(3, "Title", "Narrative", testDirective("d3"), cond);
            assertNotNull(step.condition());
            assertEquals(ChainCondition.ConditionType.MIN_KILLS_PREVIOUS, step.condition().type());
            assertEquals(10, step.condition().value());
        }
    }

    @Nested
    @DisplayName("ChainCondition")
    class ChainConditionTests {
        @Test
        @DisplayName("minKills factory creates correct condition")
        void minKillsFactory() {
            ChainCondition c = ChainCondition.minKills(15);
            assertEquals(ChainCondition.ConditionType.MIN_KILLS_PREVIOUS, c.type());
            assertEquals(15, c.value());
        }

        @Test
        @DisplayName("noDeath factory creates correct condition")
        void noDeathFactory() {
            ChainCondition c = ChainCondition.noDeath();
            assertEquals(ChainCondition.ConditionType.NO_DEATHS_PREVIOUS, c.type());
        }

        @Test
        @DisplayName("minCombo factory creates correct condition")
        void minComboFactory() {
            ChainCondition c = ChainCondition.minCombo(25);
            assertEquals(ChainCondition.ConditionType.MIN_COMBO_PREVIOUS, c.type());
            assertEquals(25, c.value());
        }

        @Test
        @DisplayName("minRank factory creates correct condition")
        void minRankFactory() {
            ChainCondition c = ChainCondition.minRank(3);
            assertEquals(ChainCondition.ConditionType.MIN_STYLE_RANK, c.type());
            assertEquals(3, c.value());
        }

        @Test
        @DisplayName("flawless factory creates correct condition")
        void flawlessFactory() {
            ChainCondition c = ChainCondition.flawless();
            assertEquals(ChainCondition.ConditionType.FLAWLESS_STEP, c.type());
        }
    }

    @Nested
    @DisplayName("ChainProgress")
    class ChainProgressTests {
        @Test
        @DisplayName("Initial state is correct")
        void initialState() {
            DirectiveChain chain = threeStepChain();
            ChainProgress progress = new ChainProgress(UUID.randomUUID(), chain);

            assertEquals(0, progress.getCurrentStep());
            assertFalse(progress.isFailed());
            assertFalse(progress.isCompleted());
            assertEquals(0, progress.getStepsCompletedFlawlessly());
            assertEquals(0, progress.getTotalKillsInChain());
            assertEquals(0, progress.getMaxComboInChain());
            assertEquals(3, progress.getTotalSteps());
            assertEquals(0.0f, progress.getProgressPercent(), 0.001f);
            assertTrue(progress.hasNextStep());
        }

        @Test
        @DisplayName("getCurrentChainStep returns correct step")
        void getCurrentStep() {
            DirectiveChain chain = threeStepChain();
            ChainProgress progress = new ChainProgress(UUID.randomUUID(), chain);
            assertNotNull(progress.getCurrentChainStep());
            assertEquals(1, progress.getCurrentChainStep().stepNumber());
        }

        @Test
        @DisplayName("advanceStep updates kills, combo, and progress")
        void advanceStepUpdatesStats() {
            DirectiveChain chain = threeStepChain();
            ChainProgress progress = new ChainProgress(UUID.randomUUID(), chain);

            progress.advanceStep(10, 15, true);
            assertEquals(1, progress.getCurrentStep());
            assertEquals(10, progress.getTotalKillsInChain());
            assertEquals(15, progress.getMaxComboInChain());
            assertEquals(1, progress.getStepsCompletedFlawlessly());
            assertFalse(progress.isCompleted());
        }

        @Test
        @DisplayName("Advancing all steps marks chain completed")
        void advanceAllStepsCompletes() {
            DirectiveChain chain = threeStepChain();
            ChainProgress progress = new ChainProgress(UUID.randomUUID(), chain);

            progress.advanceStep(5, 10, false);
            progress.advanceStep(8, 20, true);
            progress.advanceStep(3, 5, false);

            assertTrue(progress.isCompleted());
            assertEquals(16, progress.getTotalKillsInChain()); // 5+8+3
            assertEquals(20, progress.getMaxComboInChain());
            assertEquals(1, progress.getStepsCompletedFlawlessly());
        }

        @Test
        @DisplayName("markFailed sets failed flag")
        void markFailed() {
            DirectiveChain chain = threeStepChain();
            ChainProgress progress = new ChainProgress(UUID.randomUUID(), chain);
            progress.markFailed();
            assertTrue(progress.isFailed());
        }

        @Test
        @DisplayName("getProgressPercent tracks fractional progress")
        void progressPercent() {
            DirectiveChain chain = threeStepChain();
            ChainProgress progress = new ChainProgress(UUID.randomUUID(), chain);

            assertEquals(0f / 3f, progress.getProgressPercent(), 0.001f);
            progress.advanceStep(0, 0, false);
            assertEquals(1f / 3f, progress.getProgressPercent(), 0.001f);
            progress.advanceStep(0, 0, false);
            assertEquals(2f / 3f, progress.getProgressPercent(), 0.001f);
        }

        @Test
        @DisplayName("getCurrentChainStep returns null past last step")
        void getCurrentStepPastEnd() {
            DirectiveChain chain = threeStepChain();
            ChainProgress progress = new ChainProgress(UUID.randomUUID(), chain);
            progress.advanceStep(0, 0, false);
            progress.advanceStep(0, 0, false);
            progress.advanceStep(0, 0, false);
            assertNull(progress.getCurrentChainStep());
        }

        @Test
        @DisplayName("hasNextStep is false on last step")
        void hasNextStepFalseOnLast() {
            DirectiveChain chain = threeStepChain();
            ChainProgress progress = new ChainProgress(UUID.randomUUID(), chain);
            progress.advanceStep(0, 0, false);
            progress.advanceStep(0, 0, false);
            assertFalse(progress.hasNextStep());
        }

        @Test
        @DisplayName("Max combo tracks highest across steps")
        void maxComboTracksHighest() {
            DirectiveChain chain = threeStepChain();
            ChainProgress progress = new ChainProgress(UUID.randomUUID(), chain);
            progress.advanceStep(0, 50, false);
            progress.advanceStep(0, 30, false); // Lower combo - should not override
            assertEquals(50, progress.getMaxComboInChain());
        }
    }

    @Nested
    @DisplayName("ChainProgress condition checks")
    class ConditionCheckTests {
        private ChainProgress makeProgress() {
            return new ChainProgress(UUID.randomUUID(), threeStepChain());
        }

        @Test
        @DisplayName("Null condition always passes")
        void nullConditionPasses() {
            assertTrue(makeProgress().checkCondition(null, 0, 0, false, false, 0));
        }

        @Test
        @DisplayName("MIN_KILLS_PREVIOUS passes when kills >= threshold")
        void minKillsPasses() {
            ChainCondition c = ChainCondition.minKills(10);
            ChainProgress p = makeProgress();
            assertTrue(p.checkCondition(c, 10, 0, false, false, 0));
            assertTrue(p.checkCondition(c, 15, 0, false, false, 0));
            assertFalse(p.checkCondition(c, 9, 0, false, false, 0));
        }

        @Test
        @DisplayName("NO_DEATHS_PREVIOUS passes when no death")
        void noDeathPasses() {
            ChainCondition c = ChainCondition.noDeath();
            ChainProgress p = makeProgress();
            assertTrue(p.checkCondition(c, 0, 0, false, false, 0));
            assertFalse(p.checkCondition(c, 0, 0, true, false, 0));
        }

        @Test
        @DisplayName("MIN_COMBO_PREVIOUS passes when combo >= threshold")
        void minComboPasses() {
            ChainCondition c = ChainCondition.minCombo(20);
            ChainProgress p = makeProgress();
            assertTrue(p.checkCondition(c, 0, 20, false, false, 0));
            assertFalse(p.checkCondition(c, 0, 19, false, false, 0));
        }

        @Test
        @DisplayName("MIN_STYLE_RANK passes when rank ordinal >= threshold")
        void minStyleRankPasses() {
            ChainCondition c = ChainCondition.minRank(3);
            ChainProgress p = makeProgress();
            assertTrue(p.checkCondition(c, 0, 0, false, false, 3));
            assertTrue(p.checkCondition(c, 0, 0, false, false, 5));
            assertFalse(p.checkCondition(c, 0, 0, false, false, 2));
        }

        @Test
        @DisplayName("FLAWLESS_STEP passes when no damage taken")
        void flawlessPasses() {
            ChainCondition c = ChainCondition.flawless();
            ChainProgress p = makeProgress();
            assertTrue(p.checkCondition(c, 0, 0, false, false, 0));
            assertFalse(p.checkCondition(c, 0, 0, false, true, 0));
        }
    }

    @Nested
    @DisplayName("DirectiveChain record")
    class DirectiveChainRecordTests {
        @Test
        @DisplayName("getDifficultyRating is clamped 1-5")
        void difficultyRatingClamped() {
            // 3 steps, no conditions: rating = 3-2 + 0 = 1
            DirectiveChain chain = threeStepChain();
            assertEquals(1, chain.getDifficultyRating());
        }

        @Test
        @DisplayName("More steps increase difficulty rating")
        void moreStepsHigherDifficulty() {
            DirectiveChain fiveStep = new DirectiveChain(
                "test", "Test", "Desc", ChainTheme.GAUNTLET,
                List.of(
                    ChainStep.of(1, "S1", "N1", testDirective("d1")),
                    ChainStep.of(2, "S2", "N2", testDirective("d2")),
                    ChainStep.of(3, "S3", "N3", testDirective("d3")),
                    ChainStep.of(4, "S4", "N4", testDirective("d4")),
                    ChainStep.of(5, "S5", "N5", testDirective("d5"))
                ),
                200, 100, 2.0f
            );
            // 5-2 + 0 = 3
            assertEquals(3, fiveStep.getDifficultyRating());
        }

        @Test
        @DisplayName("Conditional steps increase difficulty rating")
        void conditionalStepsIncreaseDifficulty() {
            DirectiveChain chain = new DirectiveChain(
                "test", "Test", "Desc", ChainTheme.NIGHTMARE,
                List.of(
                    ChainStep.of(1, "S1", "N1", testDirective("d1")),
                    ChainStep.of(2, "S2", "N2", testDirective("d2")),
                    ChainStep.conditional(3, "S3", "N3", testDirective("d3"), ChainCondition.flawless()),
                    ChainStep.conditional(4, "S4", "N4", testDirective("d4"), ChainCondition.noDeath()),
                    ChainStep.conditional(5, "S5", "N5", testDirective("d5"), ChainCondition.minKills(20))
                ),
                300, 150, 2.5f
            );
            // 5-2 + 3 conditions = 6, clamped to 5
            assertEquals(5, chain.getDifficultyRating());
        }

        @Test
        @DisplayName("Bonus tokens and prestige are accessible")
        void bonusAccessors() {
            DirectiveChain chain = threeStepChain();
            assertEquals(100, chain.getTotalBonusTokens());
            assertEquals(50, chain.getTotalBonusPrestige());
        }

        @Test
        @DisplayName("Completion multiplier is accessible")
        void completionMultiplier() {
            DirectiveChain chain = threeStepChain();
            assertEquals(1.5f, chain.completionMultiplier(), 0.001f);
        }
    }
}
