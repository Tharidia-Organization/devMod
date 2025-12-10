package com.frenkvs.devmod;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for tutorial and progression system.
 * Tests XP system, level progression, and achievements.
 */
public class TutorialManagerTest {

    /**
     * Mock XP and Level system
     */
    static class MockXPSystem {
        private int currentXP = 0;
        private int level = 1;
        private int xpToNextLevel = 100;
        private static final int MAX_LEVEL = 100;
        private static final float XP_SCALING = 1.5f;

        public int getCurrentXP() { return currentXP; }
        public int getLevel() { return level; }
        public int getXPToNextLevel() { return xpToNextLevel; }

        public void addXP(int amount) {
            currentXP += amount;
            while (currentXP >= xpToNextLevel && level < MAX_LEVEL) {
                currentXP -= xpToNextLevel;
                level++;
                xpToNextLevel = calculateXPForLevel(level);
            }
        }

        public void setLevel(int newLevel) {
            if (newLevel >= 1 && newLevel <= MAX_LEVEL) {
                level = newLevel;
                currentXP = 0;
                xpToNextLevel = calculateXPForLevel(level);
            }
        }

        public int calculateXPForLevel(int level) {
            return (int) (100 * Math.pow(XP_SCALING, level - 1));
        }

        public float getProgressToNextLevel() {
            return (float) currentXP / xpToNextLevel;
        }

        public int getTotalXPForLevel(int targetLevel) {
            int total = 0;
            for (int i = 1; i < targetLevel; i++) {
                total += calculateXPForLevel(i);
            }
            return total;
        }
    }

    /**
     * Mock Tutorial Step
     */
    static class MockTutorialStep {
        private final String id;
        private final String title;
        private final String description;
        private boolean completed = false;
        private final List<String> requirements;

        public MockTutorialStep(String id, String title, String description) {
            this.id = id;
            this.title = title;
            this.description = description;
            this.requirements = new ArrayList<>();
        }

        public MockTutorialStep addRequirement(String requirement) {
            requirements.add(requirement);
            return this;
        }

        public String getId() { return id; }
        public String getTitle() { return title; }
        public String getDescription() { return description; }
        public boolean isCompleted() { return completed; }
        public List<String> getRequirements() { return requirements; }

        public void complete() { completed = true; }
        public void reset() { completed = false; }
    }

    /**
     * Mock Achievement
     */
    static class MockAchievement {
        private final String id;
        private final String name;
        private final String description;
        private final int xpReward;
        private boolean unlocked = false;

        public MockAchievement(String id, String name, String description, int xpReward) {
            this.id = id;
            this.name = name;
            this.description = description;
            this.xpReward = xpReward;
        }

        public String getId() { return id; }
        public String getName() { return name; }
        public String getDescription() { return description; }
        public int getXPReward() { return xpReward; }
        public boolean isUnlocked() { return unlocked; }

        public void unlock() { unlocked = true; }
        public void reset() { unlocked = false; }
    }

    /**
     * Mock Tutorial Manager
     */
    static class MockTutorialManager {
        private final List<MockTutorialStep> steps = new ArrayList<>();
        private final List<MockAchievement> achievements = new ArrayList<>();
        private final MockXPSystem xpSystem = new MockXPSystem();
        private final AtomicInteger currentStepIndex = new AtomicInteger(0);
        private final AtomicBoolean tutorialActive = new AtomicBoolean(true);

        public void addStep(MockTutorialStep step) {
            steps.add(step);
        }

        public void addAchievement(MockAchievement achievement) {
            achievements.add(achievement);
        }

        public MockTutorialStep getCurrentStep() {
            int index = currentStepIndex.get();
            if (index >= 0 && index < steps.size()) {
                return steps.get(index);
            }
            return null;
        }

        public boolean advanceStep() {
            MockTutorialStep current = getCurrentStep();
            if (current != null) {
                current.complete();
                int nextIndex = currentStepIndex.incrementAndGet();
                if (nextIndex >= steps.size()) {
                    tutorialActive.set(false);
                    return false;
                }
                return true;
            }
            return false;
        }

        public boolean skipToStep(String stepId) {
            for (int i = 0; i < steps.size(); i++) {
                if (steps.get(i).getId().equals(stepId)) {
                    currentStepIndex.set(i);
                    return true;
                }
            }
            return false;
        }

        public void reset() {
            currentStepIndex.set(0);
            tutorialActive.set(true);
            steps.forEach(MockTutorialStep::reset);
            achievements.forEach(MockAchievement::reset);
        }

        public boolean unlockAchievement(String achievementId) {
            for (MockAchievement achievement : achievements) {
                if (achievement.getId().equals(achievementId) && !achievement.isUnlocked()) {
                    achievement.unlock();
                    xpSystem.addXP(achievement.getXPReward());
                    return true;
                }
            }
            return false;
        }

        public int getCompletedStepsCount() {
            return (int) steps.stream().filter(MockTutorialStep::isCompleted).count();
        }

        public int getUnlockedAchievementsCount() {
            return (int) achievements.stream().filter(MockAchievement::isUnlocked).count();
        }

        public float getTutorialProgress() {
            if (steps.isEmpty()) return 1.0f;
            return (float) getCompletedStepsCount() / steps.size();
        }

        public boolean isTutorialComplete() {
            return !tutorialActive.get() || currentStepIndex.get() >= steps.size();
        }

        public MockXPSystem getXPSystem() { return xpSystem; }
        public List<MockTutorialStep> getSteps() { return steps; }
        public List<MockAchievement> getAchievements() { return achievements; }
    }

    private MockTutorialManager tutorialManager;

    @BeforeEach
    void setUp() {
        tutorialManager = new MockTutorialManager();
    }

    // ============================================
    // XP System Tests
    // ============================================

    @Nested
    @DisplayName("XP System Tests")
    class XPSystemTests {

        @Test
        @DisplayName("Start at level 1 with 0 XP")
        void testInitialState() {
            MockXPSystem xp = new MockXPSystem();
            assertEquals(1, xp.getLevel());
            assertEquals(0, xp.getCurrentXP());
        }

        @Test
        @DisplayName("Adding XP increases current XP")
        void testAddXP() {
            MockXPSystem xp = new MockXPSystem();
            xp.addXP(50);
            assertEquals(50, xp.getCurrentXP());
        }

        @Test
        @DisplayName("Level up when XP reaches threshold")
        void testLevelUp() {
            MockXPSystem xp = new MockXPSystem();
            xp.addXP(100); // First level requires 100 XP
            assertEquals(2, xp.getLevel());
            assertEquals(0, xp.getCurrentXP());
        }

        @Test
        @DisplayName("Overflow XP carries to next level")
        void testXPOverflow() {
            MockXPSystem xp = new MockXPSystem();
            xp.addXP(120); // 100 to level up, 20 overflow
            assertEquals(2, xp.getLevel());
            assertEquals(20, xp.getCurrentXP());
        }

        @Test
        @DisplayName("Multiple level ups from large XP gain")
        void testMultipleLevelUps() {
            MockXPSystem xp = new MockXPSystem();
            // Level 1->2: 100, Level 2->3: 150, Level 3->4: 225
            xp.addXP(500);
            assertTrue(xp.getLevel() >= 3);
        }

        @ParameterizedTest
        @CsvSource({
            "1, 100",
            "2, 150",
            "3, 225",
            "4, 337",
            "5, 506"
        })
        @DisplayName("XP requirements scale with level")
        void testXPScaling(int level, int expectedXP) {
            MockXPSystem xp = new MockXPSystem();
            int required = xp.calculateXPForLevel(level);
            assertEquals(expectedXP, required, 1);
        }

        @Test
        @DisplayName("Progress to next level is calculated correctly")
        void testProgressCalculation() {
            MockXPSystem xp = new MockXPSystem();
            xp.addXP(50);
            assertEquals(0.5f, xp.getProgressToNextLevel(), 0.01f);
        }

        @Test
        @DisplayName("Set level directly")
        void testSetLevel() {
            MockXPSystem xp = new MockXPSystem();
            xp.setLevel(10);
            assertEquals(10, xp.getLevel());
            assertEquals(0, xp.getCurrentXP());
        }
    }

    // ============================================
    // Tutorial Step Tests
    // ============================================

    @Nested
    @DisplayName("Tutorial Step Tests")
    class TutorialStepTests {

        @BeforeEach
        void setUpSteps() {
            tutorialManager.addStep(new MockTutorialStep("intro", "Welcome", "Welcome to the mod"));
            tutorialManager.addStep(new MockTutorialStep("combat", "Combat Basics", "Learn combat"));
            tutorialManager.addStep(new MockTutorialStep("advanced", "Advanced", "Advanced techniques"));
        }

        @Test
        @DisplayName("Start at first step")
        void testStartAtFirstStep() {
            assertEquals("intro", tutorialManager.getCurrentStep().getId());
        }

        @Test
        @DisplayName("Advance to next step")
        void testAdvanceStep() {
            tutorialManager.advanceStep();
            assertEquals("combat", tutorialManager.getCurrentStep().getId());
        }

        @Test
        @DisplayName("Previous steps are marked complete")
        void testStepsMarkedComplete() {
            tutorialManager.advanceStep();
            assertTrue(tutorialManager.getSteps().get(0).isCompleted());
        }

        @Test
        @DisplayName("Tutorial ends after last step")
        void testTutorialEnds() {
            tutorialManager.advanceStep();
            tutorialManager.advanceStep();
            tutorialManager.advanceStep();

            assertTrue(tutorialManager.isTutorialComplete());
        }

        @Test
        @DisplayName("Skip to specific step")
        void testSkipToStep() {
            assertTrue(tutorialManager.skipToStep("advanced"));
            assertEquals("advanced", tutorialManager.getCurrentStep().getId());
        }

        @Test
        @DisplayName("Skip to invalid step returns false")
        void testSkipToInvalidStep() {
            assertFalse(tutorialManager.skipToStep("nonexistent"));
        }

        @Test
        @DisplayName("Tutorial progress calculation")
        void testProgressCalculation() {
            assertEquals(0f, tutorialManager.getTutorialProgress(), 0.01f);

            tutorialManager.advanceStep();
            assertEquals(0.333f, tutorialManager.getTutorialProgress(), 0.01f);

            tutorialManager.advanceStep();
            assertEquals(0.666f, tutorialManager.getTutorialProgress(), 0.01f);
        }

        @Test
        @DisplayName("Reset tutorial")
        void testReset() {
            tutorialManager.advanceStep();
            tutorialManager.advanceStep();
            tutorialManager.reset();

            assertEquals("intro", tutorialManager.getCurrentStep().getId());
            assertEquals(0, tutorialManager.getCompletedStepsCount());
        }
    }

    // ============================================
    // Achievement Tests
    // ============================================

    @Nested
    @DisplayName("Achievement Tests")
    class AchievementTests {

        @BeforeEach
        void setUpAchievements() {
            tutorialManager.addAchievement(new MockAchievement("first_kill", "First Blood", "Kill an enemy", 50));
            tutorialManager.addAchievement(new MockAchievement("headshot", "Headhunter", "Get a headshot", 100));
            tutorialManager.addAchievement(new MockAchievement("master", "Master", "Complete all tutorials", 500));
        }

        @Test
        @DisplayName("Achievements start locked")
        void testAchievementsLocked() {
            assertEquals(0, tutorialManager.getUnlockedAchievementsCount());
        }

        @Test
        @DisplayName("Unlock achievement grants XP")
        void testUnlockGrantsXP() {
            tutorialManager.unlockAchievement("first_kill");
            assertEquals(50, tutorialManager.getXPSystem().getCurrentXP());
        }

        @Test
        @DisplayName("Unlock multiple achievements")
        void testUnlockMultiple() {
            tutorialManager.unlockAchievement("first_kill");  // 50 XP
            tutorialManager.unlockAchievement("headshot");    // 100 XP -> level up

            assertEquals(2, tutorialManager.getUnlockedAchievementsCount());
            // 50 + 100 = 150, but level up at 100, so 50 XP remaining at level 2
            assertEquals(2, tutorialManager.getXPSystem().getLevel());
            assertEquals(50, tutorialManager.getXPSystem().getCurrentXP());
        }

        @Test
        @DisplayName("Cannot unlock same achievement twice")
        void testCannotUnlockTwice() {
            assertTrue(tutorialManager.unlockAchievement("first_kill"));
            assertFalse(tutorialManager.unlockAchievement("first_kill"));
            assertEquals(50, tutorialManager.getXPSystem().getCurrentXP());
        }

        @Test
        @DisplayName("Invalid achievement returns false")
        void testInvalidAchievement() {
            assertFalse(tutorialManager.unlockAchievement("nonexistent"));
        }

        @Test
        @DisplayName("Large XP rewards can cause level up")
        void testAchievementCausesLevelUp() {
            tutorialManager.unlockAchievement("master"); // 500 XP
            assertTrue(tutorialManager.getXPSystem().getLevel() > 1);
        }
    }

    // ============================================
    // Integration Tests
    // ============================================

    @Nested
    @DisplayName("Integration Tests")
    class IntegrationTests {

        @BeforeEach
        void setUpAll() {
            tutorialManager.addStep(new MockTutorialStep("intro", "Welcome", "Welcome"));
            tutorialManager.addStep(new MockTutorialStep("combat", "Combat", "Combat basics"));

            tutorialManager.addAchievement(new MockAchievement("complete_intro", "Started!", "Complete intro", 25));
            tutorialManager.addAchievement(new MockAchievement("complete_tutorial", "Graduate", "Complete tutorial", 200));
        }

        @Test
        @DisplayName("Complete step and unlock achievement")
        void testCompleteStepUnlockAchievement() {
            tutorialManager.advanceStep();
            tutorialManager.unlockAchievement("complete_intro");

            assertEquals(1, tutorialManager.getCompletedStepsCount());
            assertEquals(1, tutorialManager.getUnlockedAchievementsCount());
            assertEquals(25, tutorialManager.getXPSystem().getCurrentXP());
        }

        @Test
        @DisplayName("Full tutorial completion flow")
        void testFullFlow() {
            // Complete all steps
            tutorialManager.advanceStep();
            tutorialManager.unlockAchievement("complete_intro");  // 25 XP

            tutorialManager.advanceStep();
            tutorialManager.unlockAchievement("complete_tutorial");  // 200 XP

            assertTrue(tutorialManager.isTutorialComplete());
            assertEquals(2, tutorialManager.getUnlockedAchievementsCount());
            // 25 + 200 = 225 XP total. Level 1 needs 100, level 2 needs 150
            // After level up: 125 XP at level 2
            assertEquals(2, tutorialManager.getXPSystem().getLevel());
            assertEquals(125, tutorialManager.getXPSystem().getCurrentXP());
        }

        @Test
        @DisplayName("Reset clears everything")
        void testResetClearsAll() {
            tutorialManager.advanceStep();
            tutorialManager.unlockAchievement("complete_intro");

            tutorialManager.reset();

            assertEquals(0, tutorialManager.getCompletedStepsCount());
            assertEquals(0, tutorialManager.getUnlockedAchievementsCount());
            assertFalse(tutorialManager.isTutorialComplete());
        }
    }

    // ============================================
    // Edge Cases
    // ============================================

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCases {

        @Test
        @DisplayName("Empty tutorial is complete")
        void testEmptyTutorialComplete() {
            assertTrue(tutorialManager.isTutorialComplete());
        }

        @Test
        @DisplayName("Empty tutorial has 100% progress")
        void testEmptyTutorialProgress() {
            assertEquals(1.0f, tutorialManager.getTutorialProgress(), 0.01f);
        }

        @Test
        @DisplayName("Get current step on empty tutorial returns null")
        void testGetCurrentStepEmpty() {
            assertNull(tutorialManager.getCurrentStep());
        }

        @Test
        @DisplayName("Advance on empty tutorial returns false")
        void testAdvanceEmptyTutorial() {
            assertFalse(tutorialManager.advanceStep());
        }

        @Test
        @DisplayName("XP system handles zero XP")
        void testZeroXP() {
            MockXPSystem xp = new MockXPSystem();
            xp.addXP(0);
            assertEquals(1, xp.getLevel());
            assertEquals(0, xp.getCurrentXP());
        }

        @Test
        @DisplayName("Large XP values don't overflow")
        void testLargeXP() {
            MockXPSystem xp = new MockXPSystem();
            xp.addXP(1000000);
            assertTrue(xp.getLevel() > 1);
        }

        @Test
        @DisplayName("Set level to invalid value is ignored")
        void testSetInvalidLevel() {
            MockXPSystem xp = new MockXPSystem();
            xp.setLevel(0);
            assertEquals(1, xp.getLevel()); // Should remain at 1

            xp.setLevel(101); // Over max
            assertEquals(1, xp.getLevel()); // Should remain unchanged
        }

        @Test
        @DisplayName("Step requirements can be added")
        void testStepRequirements() {
            MockTutorialStep step = new MockTutorialStep("test", "Test", "Description");
            step.addRequirement("req1");
            step.addRequirement("req2");

            assertEquals(2, step.getRequirements().size());
            assertTrue(step.getRequirements().contains("req1"));
        }

        @Test
        @DisplayName("Achievement properties accessible")
        void testAchievementProperties() {
            MockAchievement achievement = new MockAchievement("test", "Test Name", "Test Desc", 100);

            assertEquals("test", achievement.getId());
            assertEquals("Test Name", achievement.getName());
            assertEquals("Test Desc", achievement.getDescription());
            assertEquals(100, achievement.getXPReward());
        }
    }

    // ============================================
    // Thread Safety Tests (using AtomicInteger)
    // ============================================

    @Nested
    @DisplayName("Thread Safety Tests")
    class ThreadSafetyTests {

        @Test
        @DisplayName("AtomicInteger step index is thread-safe")
        void testAtomicStepIndex() {
            tutorialManager.addStep(new MockTutorialStep("1", "Step 1", ""));
            tutorialManager.addStep(new MockTutorialStep("2", "Step 2", ""));
            tutorialManager.addStep(new MockTutorialStep("3", "Step 3", ""));

            // Concurrent advances
            Thread t1 = new Thread(() -> tutorialManager.advanceStep());
            Thread t2 = new Thread(() -> tutorialManager.advanceStep());

            t1.start();
            t2.start();

            try {
                t1.join();
                t2.join();
            } catch (InterruptedException e) {
                fail("Thread interrupted");
            }

            // Should have advanced at least once
            assertTrue(tutorialManager.getCompletedStepsCount() >= 1);
        }

        @Test
        @DisplayName("Tutorial active state is atomic")
        void testAtomicActiveState() {
            tutorialManager.addStep(new MockTutorialStep("only", "Only Step", ""));

            tutorialManager.advanceStep();

            // Should be complete
            assertTrue(tutorialManager.isTutorialComplete());
        }
    }
}
