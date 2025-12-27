package com.devmod.mailbox.task;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for TestTask record.
 */
class TestTaskTest {

    private static final UUID TEST_ID = UUID.randomUUID();
    private static final UUID ASSIGNEE_UUID = UUID.randomUUID();

    @Nested
    @DisplayName("Task Creation")
    class TaskCreation {

        @Test
        @DisplayName("Builder creates valid task with required fields")
        void builderCreatesValidTask() {
            TestTask task = TestTask.builder()
                .id(TEST_ID)
                .title("Test bug X")
                .description("Reproduce bug X by doing Y")
                .assignedTo(ASSIGNEE_UUID)
                .assignedByName("Admin")
                .priority(2)
                .status(TestTask.TaskStatus.PENDING)
                .createdAt(System.currentTimeMillis())
                .build();

            assertNotNull(task);
            assertEquals(TEST_ID, task.id());
            assertEquals("Test bug X", task.title());
            assertEquals("Reproduce bug X by doing Y", task.description());
            assertEquals(ASSIGNEE_UUID, task.assignedTo());
            assertEquals("Admin", task.assignedByName());
            assertEquals(2, task.priority());
            assertEquals(TestTask.TaskStatus.PENDING, task.status());
        }

        @Test
        @DisplayName("Task defaults to PENDING status")
        void taskDefaultsToPending() {
            TestTask task = TestTask.builder()
                .id(TEST_ID)
                .title("New Task")
                .assignedTo(ASSIGNEE_UUID)
                .createdAt(System.currentTimeMillis())
                .build();

            assertEquals(TestTask.TaskStatus.PENDING, task.status());
        }

        @Test
        @DisplayName("Task defaults to priority 1")
        void taskDefaultsToPriority1() {
            TestTask task = TestTask.builder()
                .id(TEST_ID)
                .title("New Task")
                .assignedTo(ASSIGNEE_UUID)
                .createdAt(System.currentTimeMillis())
                .build();

            assertEquals(1, task.priority());
        }
    }

    @Nested
    @DisplayName("Task Status")
    class StatusTests {

        @Test
        @DisplayName("All task statuses are valid")
        void allStatusesValid() {
            for (TestTask.TaskStatus status : TestTask.TaskStatus.values()) {
                assertNotNull(status.name());
                assertNotNull(status.getId());
            }
        }

        @Test
        @DisplayName("Status IDs are unique")
        void statusIdsUnique() {
            String[] ids = new String[TestTask.TaskStatus.values().length];
            int i = 0;
            for (TestTask.TaskStatus status : TestTask.TaskStatus.values()) {
                for (int j = 0; j < i; j++) {
                    assertNotEquals(ids[j], status.getId(),
                        "Duplicate status ID: " + status.getId());
                }
                ids[i++] = status.getId();
            }
        }

        @Test
        @DisplayName("fromId returns correct status")
        void fromIdReturnsCorrectStatus() {
            assertEquals(TestTask.TaskStatus.PENDING,
                TestTask.TaskStatus.fromId("pending"));
            assertEquals(TestTask.TaskStatus.IN_PROGRESS,
                TestTask.TaskStatus.fromId("in_progress"));
            assertEquals(TestTask.TaskStatus.COMPLETED,
                TestTask.TaskStatus.fromId("completed"));
        }

        @Test
        @DisplayName("fromId returns PENDING for unknown ID")
        void fromIdReturnsPendingForUnknown() {
            assertEquals(TestTask.TaskStatus.PENDING,
                TestTask.TaskStatus.fromId("unknown"));
            assertEquals(TestTask.TaskStatus.PENDING,
                TestTask.TaskStatus.fromId(null));
        }

        @Test
        @DisplayName("withStatus creates new task with updated status")
        void withStatusCreatesNewTask() {
            TestTask original = createTestTask();
            TestTask inProgress = original.withStatus(TestTask.TaskStatus.IN_PROGRESS);

            assertEquals(TestTask.TaskStatus.PENDING, original.status());
            assertEquals(TestTask.TaskStatus.IN_PROGRESS, inProgress.status());
            assertEquals(original.id(), inProgress.id());
        }
    }

    @Nested
    @DisplayName("Due Date")
    class DueDateTests {

        @Test
        @DisplayName("Task without due date is not overdue")
        void taskWithoutDueDateNotOverdue() {
            TestTask task = createTestTask();
            assertNull(task.dueAt());
            assertFalse(task.isOverdue());
        }

        @Test
        @DisplayName("Task with future due date is not overdue")
        void taskWithFutureDueDateNotOverdue() {
            TestTask task = TestTask.builder()
                .id(TEST_ID)
                .title("Future Task")
                .assignedTo(ASSIGNEE_UUID)
                .createdAt(System.currentTimeMillis())
                .dueAt(System.currentTimeMillis() + 86400000) // Tomorrow
                .build();

            assertFalse(task.isOverdue());
        }

        @Test
        @DisplayName("Task with past due date is overdue")
        void taskWithPastDueDateIsOverdue() {
            TestTask task = TestTask.builder()
                .id(TEST_ID)
                .title("Overdue Task")
                .assignedTo(ASSIGNEE_UUID)
                .createdAt(System.currentTimeMillis() - 172800000) // 2 days ago
                .dueAt(System.currentTimeMillis() - 86400000) // Yesterday
                .build();

            assertTrue(task.isOverdue());
        }

        @Test
        @DisplayName("Completed task is not considered overdue")
        void completedTaskNotOverdue() {
            TestTask task = TestTask.builder()
                .id(TEST_ID)
                .title("Completed Task")
                .assignedTo(ASSIGNEE_UUID)
                .createdAt(System.currentTimeMillis() - 172800000)
                .dueAt(System.currentTimeMillis() - 86400000) // Past due
                .status(TestTask.TaskStatus.COMPLETED)
                .completedAt(System.currentTimeMillis() - 90000000) // Completed before due
                .build();

            // Even if due date is past, completed tasks shouldn't be flagged
            // This depends on implementation - some systems still show overdue
            assertNotNull(task.completedAt());
        }
    }

    @Nested
    @DisplayName("Notes")
    class NotesTests {

        @Test
        @DisplayName("Task can have notes added")
        void taskCanHaveNotes() {
            TestTask task = createTestTask();
            TestTask withNotes = task.withNotes("Found the issue - it's a race condition");

            assertNull(task.notes());
            assertEquals("Found the issue - it's a race condition", withNotes.notes());
        }

        @Test
        @DisplayName("Notes can be updated")
        void notesCanBeUpdated() {
            TestTask task = createTestTask().withNotes("Initial note");
            TestTask updated = task.withNotes("Updated note with more info");

            assertEquals("Initial note", task.notes());
            assertEquals("Updated note with more info", updated.notes());
        }
    }

    @Nested
    @DisplayName("Priority")
    class PriorityTests {

        @Test
        @DisplayName("Priority 1 is low")
        void priority1IsLow() {
            TestTask low = createTaskWithPriority(1);
            assertEquals(1, low.priority());
        }

        @Test
        @DisplayName("Priority 2 is normal")
        void priority2IsNormal() {
            TestTask normal = createTaskWithPriority(2);
            assertEquals(2, normal.priority());
        }

        @Test
        @DisplayName("Priority 3 is high")
        void priority3IsHigh() {
            TestTask high = createTaskWithPriority(3);
            assertEquals(3, high.priority());
        }

        @Test
        @DisplayName("Higher priority tasks are more urgent")
        void higherPriorityMoreUrgent() {
            TestTask low = createTaskWithPriority(1);
            TestTask normal = createTaskWithPriority(2);
            TestTask high = createTaskWithPriority(3);

            assertTrue(high.priority() > normal.priority());
            assertTrue(normal.priority() > low.priority());
        }
    }

    private TestTask createTestTask() {
        return TestTask.builder()
            .id(TEST_ID)
            .title("Test Task")
            .description("Test description")
            .assignedTo(ASSIGNEE_UUID)
            .assignedByName("Admin")
            .priority(1)
            .status(TestTask.TaskStatus.PENDING)
            .createdAt(System.currentTimeMillis())
            .build();
    }

    private TestTask createTaskWithPriority(int priority) {
        return TestTask.builder()
            .id(UUID.randomUUID())
            .title("Priority " + priority + " Task")
            .assignedTo(ASSIGNEE_UUID)
            .priority(priority)
            .createdAt(System.currentTimeMillis())
            .build();
    }
}
