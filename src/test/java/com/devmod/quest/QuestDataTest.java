package com.devmod.quest;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("QuestData")
class QuestDataTest {

    // ========================================================================
    // Basic Construction
    // ========================================================================

    @Nested
    @DisplayName("Construction")
    class ConstructionTests {

        @Test
        @DisplayName("Creates with id and name")
        void createsWithIdAndName() {
            QuestData quest = new QuestData("test_quest", "Test Quest");
            assertEquals("test_quest", quest.getId());
            assertEquals("Test Quest", quest.getName());
        }

        @Test
        @DisplayName("New quest has empty tasks list")
        void newQuestHasEmptyTasks() {
            QuestData quest = new QuestData("q1", "Q1");
            assertTrue(quest.getTasks().isEmpty());
        }

        @Test
        @DisplayName("New quest starts at task index 0")
        void newQuestStartsAtIndexZero() {
            QuestData quest = new QuestData("q1", "Q1");
            assertEquals(0, quest.getCurrentTaskIndex());
        }

        @Test
        @DisplayName("New quest is not complete")
        void newQuestIsNotComplete() {
            QuestData quest = new QuestData("q1", "Q1");
            assertFalse(quest.isComplete());
        }

        @Test
        @DisplayName("New quest has default type MANUAL")
        void newQuestDefaultTypeManual() {
            QuestData quest = new QuestData("q1", "Q1");
            assertEquals(QuestData.QuestType.MANUAL, quest.getQuestType());
            assertFalse(quest.isEnduranceQuest());
        }
    }

    // ========================================================================
    // Task Management
    // ========================================================================

    @Nested
    @DisplayName("Task Management")
    class TaskManagementTests {

        @Test
        @DisplayName("addTask increases tasks list")
        void addTaskIncreasesList() {
            QuestData quest = new QuestData("q1", "Q1");
            quest.addTask(new QuestTask("t1", "Task 1"));
            assertEquals(1, quest.getTasks().size());
        }

        @Test
        @DisplayName("getCurrentTask returns first task")
        void getCurrentTaskReturnsFirst() {
            QuestData quest = new QuestData("q1", "Q1");
            quest.addTask(new QuestTask("t1", "Task 1"));
            quest.addTask(new QuestTask("t2", "Task 2"));
            assertNotNull(quest.getCurrentTask());
            assertEquals("t1", quest.getCurrentTask().getId());
        }

        @Test
        @DisplayName("getCurrentTask returns null for empty quest")
        void getCurrentTaskReturnsNullForEmpty() {
            QuestData quest = new QuestData("q1", "Q1");
            assertNull(quest.getCurrentTask());
        }

        @Test
        @DisplayName("setCurrentTaskIndex within bounds works")
        void setCurrentTaskIndexWithinBounds() {
            QuestData quest = new QuestData("q1", "Q1");
            quest.addTask(new QuestTask("t1", "Task 1"));
            quest.addTask(new QuestTask("t2", "Task 2"));
            quest.setCurrentTaskIndex(1);
            assertEquals(1, quest.getCurrentTaskIndex());
            assertEquals("t2", quest.getCurrentTask().getId());
        }

        @Test
        @DisplayName("setCurrentTaskIndex out of bounds is ignored")
        void setCurrentTaskIndexOutOfBoundsIgnored() {
            QuestData quest = new QuestData("q1", "Q1");
            quest.addTask(new QuestTask("t1", "Task 1"));
            quest.setCurrentTaskIndex(5);
            assertEquals(0, quest.getCurrentTaskIndex());
        }

        @Test
        @DisplayName("setCurrentTaskIndex negative is ignored")
        void setCurrentTaskIndexNegativeIgnored() {
            QuestData quest = new QuestData("q1", "Q1");
            quest.addTask(new QuestTask("t1", "Task 1"));
            quest.setCurrentTaskIndex(-1);
            assertEquals(0, quest.getCurrentTaskIndex());
        }
    }

    // ========================================================================
    // Task Advancement
    // ========================================================================

    @Nested
    @DisplayName("Task Advancement")
    class TaskAdvancementTests {

        @Test
        @DisplayName("advanceToNextTask moves to next task")
        void advanceMovesToNext() {
            QuestData quest = new QuestData("q1", "Q1");
            quest.addTask(new QuestTask("t1", "Task 1"));
            quest.addTask(new QuestTask("t2", "Task 2"));

            boolean hasNext = quest.advanceToNextTask();
            assertTrue(hasNext);
            assertEquals(1, quest.getCurrentTaskIndex());
            assertTrue(quest.getTasks().get(0).isCompleted());
        }

        @Test
        @DisplayName("advanceToNextTask on last task returns false")
        void advanceOnLastReturnsFalse() {
            QuestData quest = new QuestData("q1", "Q1");
            quest.addTask(new QuestTask("t1", "Task 1"));

            boolean hasNext = quest.advanceToNextTask();
            assertFalse(hasNext);
            assertTrue(quest.getTasks().get(0).isCompleted());
        }

        @Test
        @DisplayName("Multiple advances complete quest")
        void multipleAdvancesCompleteQuest() {
            QuestData quest = new QuestData("q1", "Q1");
            quest.addTask(new QuestTask("t1", "Task 1"));
            quest.addTask(new QuestTask("t2", "Task 2"));

            quest.advanceToNextTask(); // t1 done
            quest.advanceToNextTask(); // t2 done

            assertTrue(quest.isComplete());
        }
    }

    // ========================================================================
    // Completion Percentage
    // ========================================================================

    @Nested
    @DisplayName("Completion Percentage")
    class CompletionPercentageTests {

        @Test
        @DisplayName("0% for empty quest")
        void zeroForEmpty() {
            QuestData quest = new QuestData("q1", "Q1");
            assertEquals(0f, quest.getCompletionPercentage(), 0.001f);
        }

        @Test
        @DisplayName("0% for quest with no completed tasks")
        void zeroForNoCompleted() {
            QuestData quest = new QuestData("q1", "Q1");
            quest.addTask(new QuestTask("t1", "Task 1"));
            quest.addTask(new QuestTask("t2", "Task 2"));
            assertEquals(0f, quest.getCompletionPercentage(), 0.001f);
        }

        @Test
        @DisplayName("50% for half completed tasks")
        void fiftyForHalf() {
            QuestData quest = new QuestData("q1", "Q1");
            quest.addTask(new QuestTask("t1", "Task 1"));
            quest.addTask(new QuestTask("t2", "Task 2"));
            quest.advanceToNextTask(); // t1 completed
            assertEquals(50f, quest.getCompletionPercentage(), 0.001f);
        }

        @Test
        @DisplayName("100% when all tasks completed")
        void hundredForAll() {
            QuestData quest = new QuestData("q1", "Q1");
            quest.addTask(new QuestTask("t1", "Task 1"));
            quest.addTask(new QuestTask("t2", "Task 2"));
            quest.advanceToNextTask();
            quest.advanceToNextTask();
            assertEquals(100f, quest.getCompletionPercentage(), 0.001f);
        }
    }

    // ========================================================================
    // Quest Notes
    // ========================================================================

    @Nested
    @DisplayName("Quest Notes")
    class QuestNoteTests {

        @Test
        @DisplayName("New quest has empty note")
        void newQuestEmptyNote() {
            QuestData quest = new QuestData("q1", "Q1");
            assertEquals("", quest.getQuestNote());
            assertFalse(quest.hasQuestNote());
        }

        @Test
        @DisplayName("Setting note works")
        void settingNoteWorks() {
            QuestData quest = new QuestData("q1", "Q1");
            quest.setQuestNote("Remember to bring potions");
            assertEquals("Remember to bring potions", quest.getQuestNote());
            assertTrue(quest.hasQuestNote());
        }

        @Test
        @DisplayName("Setting null note becomes empty string")
        void settingNullNoteBecomesEmpty() {
            QuestData quest = new QuestData("q1", "Q1");
            quest.setQuestNote("something");
            quest.setQuestNote(null);
            assertEquals("", quest.getQuestNote());
            assertFalse(quest.hasQuestNote());
        }
    }

    // ========================================================================
    // Progress Summary
    // ========================================================================

    @Nested
    @DisplayName("Progress Summary")
    class ProgressSummaryTests {

        @Test
        @DisplayName("Manual quest shows task count")
        void manualQuestShowsTaskCount() {
            QuestData quest = new QuestData("q1", "Q1");
            quest.addTask(new QuestTask("t1", "Task 1"));
            quest.addTask(new QuestTask("t2", "Task 2"));
            assertEquals("0/2 tasks", quest.getProgressSummary());
        }

        @Test
        @DisplayName("Manual quest after advancing shows progress")
        void manualQuestAfterAdvance() {
            QuestData quest = new QuestData("q1", "Q1");
            quest.addTask(new QuestTask("t1", "Task 1"));
            quest.addTask(new QuestTask("t2", "Task 2"));
            quest.advanceToNextTask();
            assertEquals("1/2 tasks", quest.getProgressSummary());
        }
    }

    // ========================================================================
    // Endurance Quest Factory
    // ========================================================================

    @Nested
    @DisplayName("Endurance Quest")
    class EnduranceQuestTests {

        @Test
        @DisplayName("Creates endurance quest with correct type")
        void createsWithCorrectType() {
            QuestData quest = QuestData.createEnduranceQuest("minecraft:zombie", "Zombie", 5, false);
            assertTrue(quest.isEnduranceQuest());
            assertEquals(QuestData.QuestType.ENDURANCE, quest.getQuestType());
        }

        @Test
        @DisplayName("Has correct wave settings")
        void hasCorrectWaveSettings() {
            QuestData quest = QuestData.createEnduranceQuest("minecraft:zombie", "Zombie", 10, false);
            assertEquals(10, quest.getTotalWaves());
            assertEquals(0, quest.getCurrentWave());
            assertFalse(quest.isEndlessMode());
        }

        @Test
        @DisplayName("Endless mode is set correctly")
        void endlessModeSetCorrectly() {
            QuestData quest = QuestData.createEnduranceQuest("minecraft:zombie", "Zombie", 10, true);
            assertTrue(quest.isEndlessMode());
        }

        @Test
        @DisplayName("Has target mob ID")
        void hasTargetMobId() {
            QuestData quest = QuestData.createEnduranceQuest("minecraft:skeleton", "Skeleton", 5, false);
            assertEquals("minecraft:skeleton", quest.getTargetMobId());
        }

        @Test
        @DisplayName("Starts with prepare task")
        void startsWithPrepareTask() {
            QuestData quest = QuestData.createEnduranceQuest("minecraft:zombie", "Zombie", 5, false);
            assertFalse(quest.getTasks().isEmpty());
            assertEquals("prepare", quest.getTasks().get(0).getId());
        }

        @Test
        @DisplayName("Has correct number of wave tasks for non-endless")
        void hasCorrectWaveTasks() {
            QuestData quest = QuestData.createEnduranceQuest("minecraft:zombie", "Zombie", 3, false);
            // Should have: prepare, wave_1, wave_2, wave_3
            assertTrue(quest.getTasks().size() >= 4);
        }

        @Test
        @DisplayName("Endless quest has survive task")
        void endlessQuestHasSurviveTask() {
            QuestData quest = QuestData.createEnduranceQuest("minecraft:zombie", "Zombie", 10, true);
            boolean hasEndlessTask = quest.getTasks().stream()
                .anyMatch(t -> "endless".equals(t.getId()));
            assertTrue(hasEndlessTask);
        }

        @Test
        @DisplayName("Kill tracking works")
        void killTrackingWorks() {
            QuestData quest = QuestData.createEnduranceQuest("minecraft:zombie", "Zombie", 5, false);
            assertEquals(0, quest.getTotalKills());
            quest.addKill();
            assertEquals(1, quest.getTotalKills());
            quest.addKills(5);
            assertEquals(6, quest.getTotalKills());
        }

        @Test
        @DisplayName("Point tracking works")
        void pointTrackingWorks() {
            QuestData quest = QuestData.createEnduranceQuest("minecraft:zombie", "Zombie", 5, false);
            assertEquals(0, quest.getTotalPoints());
            quest.addPoints(100);
            assertEquals(100, quest.getTotalPoints());
            quest.addPoints(50);
            assertEquals(150, quest.getTotalPoints());
        }

        @Test
        @DisplayName("Wave increment works")
        void waveIncrementWorks() {
            QuestData quest = QuestData.createEnduranceQuest("minecraft:zombie", "Zombie", 5, false);
            assertEquals(0, quest.getCurrentWave());
            quest.incrementWave();
            assertEquals(1, quest.getCurrentWave());
            quest.setCurrentWave(4);
            assertEquals(4, quest.getCurrentWave());
        }

        @Test
        @DisplayName("Quest active state toggle")
        void questActiveStateToggle() {
            QuestData quest = QuestData.createEnduranceQuest("minecraft:zombie", "Zombie", 5, false);
            assertFalse(quest.isQuestActive());
            quest.startQuest();
            assertTrue(quest.isQuestActive());
            quest.stopQuest();
            assertFalse(quest.isQuestActive());
        }

        @Test
        @DisplayName("Endurance progress summary shows wave info")
        void enduranceProgressSummary() {
            QuestData quest = QuestData.createEnduranceQuest("minecraft:zombie", "Zombie", 5, false);
            quest.addKills(10);
            String summary = quest.getProgressSummary();
            assertTrue(summary.contains("Wave"));
            assertTrue(summary.contains("10 kills"));
        }

        @Test
        @DisplayName("Endless progress summary omits total waves")
        void endlessProgressSummary() {
            QuestData quest = QuestData.createEnduranceQuest("minecraft:zombie", "Zombie", 10, true);
            quest.addKills(3);
            String summary = quest.getProgressSummary();
            assertTrue(summary.contains("Wave"));
            assertTrue(summary.contains("3 kills"));
            assertFalse(summary.contains("/10"));
        }
    }
}
