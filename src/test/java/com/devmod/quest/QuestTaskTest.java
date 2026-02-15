package com.devmod.quest;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("QuestTask")
class QuestTaskTest {

    @Nested
    @DisplayName("Construction")
    class ConstructionTests {

        @Test
        @DisplayName("Creates with id and description")
        void createsWithIdAndDescription() {
            QuestTask task = new QuestTask("kill_zombies", "Kill 10 zombies");
            assertEquals("kill_zombies", task.getId());
            assertEquals("Kill 10 zombies", task.getDescription());
        }

        @Test
        @DisplayName("New task is not completed")
        void newTaskNotCompleted() {
            QuestTask task = new QuestTask("t1", "Task");
            assertFalse(task.isCompleted());
        }

        @Test
        @DisplayName("New task has empty note")
        void newTaskEmptyNote() {
            QuestTask task = new QuestTask("t1", "Task");
            assertEquals("", task.getNote());
            assertFalse(task.hasNote());
        }
    }

    @Nested
    @DisplayName("Completion")
    class CompletionTests {

        @Test
        @DisplayName("setCompleted changes state")
        void setCompletedChangesState() {
            QuestTask task = new QuestTask("t1", "Task");
            task.setCompleted(true);
            assertTrue(task.isCompleted());
        }

        @Test
        @DisplayName("Can toggle completion")
        void canToggleCompletion() {
            QuestTask task = new QuestTask("t1", "Task");
            task.setCompleted(true);
            assertTrue(task.isCompleted());
            task.setCompleted(false);
            assertFalse(task.isCompleted());
        }
    }

    @Nested
    @DisplayName("Notes")
    class NoteTests {

        @Test
        @DisplayName("setNote stores value")
        void setNoteStoresValue() {
            QuestTask task = new QuestTask("t1", "Task");
            task.setNote("Bring armor");
            assertEquals("Bring armor", task.getNote());
            assertTrue(task.hasNote());
        }

        @Test
        @DisplayName("setNote null becomes empty string")
        void setNoteNullBecomesEmpty() {
            QuestTask task = new QuestTask("t1", "Task");
            task.setNote("something");
            task.setNote(null);
            assertEquals("", task.getNote());
            assertFalse(task.hasNote());
        }

        @Test
        @DisplayName("hasNote returns false for empty string")
        void hasNoteReturnsFalseForEmpty() {
            QuestTask task = new QuestTask("t1", "Task");
            task.setNote("");
            assertFalse(task.hasNote());
        }
    }
}
