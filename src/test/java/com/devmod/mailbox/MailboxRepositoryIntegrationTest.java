package com.devmod.mailbox;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.devmod.mailbox.news.NewsArticle;
import com.devmod.mailbox.news.NewsCategory;
import com.devmod.mailbox.persistence.DuckDbMailboxRepository;
import com.devmod.mailbox.task.TaskAuditEntry;
import com.devmod.mailbox.task.TestTask;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for DuckDbMailboxRepository.
 */
@DisplayName("Mailbox Repository Integration Tests")
class MailboxRepositoryIntegrationTest {

    private Path tempDir;
    private DuckDbMailboxRepository repository;

    @BeforeEach
    void setUp() throws Exception {
        tempDir = Files.createTempDirectory("mailbox-test");
        Path dbPath = tempDir.resolve("test-mailbox.duckdb");
        repository = new DuckDbMailboxRepository(dbPath);
        repository.initialize().get(5, TimeUnit.SECONDS);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (repository != null) {
            repository.shutdown().get(5, TimeUnit.SECONDS);
        }
        // Clean up temp files
        if (tempDir != null) {
            try (Stream<Path> paths = Files.walk(tempDir)) {
                paths.sorted((a, b) -> b.compareTo(a))
                    .forEach(p -> {
                        try { Files.deleteIfExists(p); } catch (Exception e) { /* ignore */ }
                    });
            }
        }
    }

    @Nested
    @DisplayName("Message Operations")
    class MessageOperations {

        @Test
        @DisplayName("Save and retrieve message")
        void saveAndRetrieveMessage() throws Exception {
            UUID recipientUuid = UUID.randomUUID();
            MailboxMessage message = MailboxMessage.builder()
                .sender(null, "TestAdmin")
                .recipient(recipientUuid)
                .subject("Test Subject")
                .body("Test body content")
                .messageType(MessageType.ADMIN)
                .build();

            MailboxMessage saved = repository.saveMessage(message).get(5, TimeUnit.SECONDS);
            assertNotNull(saved);
            assertEquals(message.id(), saved.id());

            var retrieved = repository.getMessage(saved.id()).get(5, TimeUnit.SECONDS);
            assertTrue(retrieved.isPresent());
            assertEquals("Test Subject", retrieved.get().subject());
        }

        @Test
        @DisplayName("Get messages for player")
        void getMessagesForPlayer() throws Exception {
            UUID playerUuid = UUID.randomUUID();

            for (int i = 0; i < 3; i++) {
                MailboxMessage msg = MailboxMessage.builder()
                    .sender(null, "Admin")
                    .recipient(playerUuid)
                    .subject("Message " + i)
                    .messageType(MessageType.ADMIN)
                    .build();
                repository.saveMessage(msg).get(5, TimeUnit.SECONDS);
            }

            List<MailboxMessage> messages = repository.getMessagesForPlayer(playerUuid, false)
                .get(5, TimeUnit.SECONDS);
            assertEquals(3, messages.size());
        }

        @Test
        @DisplayName("Mark message as read")
        void markAsRead() throws Exception {
            UUID recipientUuid = UUID.randomUUID();
            MailboxMessage message = MailboxMessage.builder()
                .sender(null, "Admin")
                .recipient(recipientUuid)
                .subject("Unread")
                .messageType(MessageType.ADMIN)
                .build();

            MailboxMessage saved = repository.saveMessage(message).get(5, TimeUnit.SECONDS);
            assertNull(saved.readAt());

            boolean marked = repository.markAsRead(saved.id(), Instant.now()).get(5, TimeUnit.SECONDS);
            assertTrue(marked);

            var retrieved = repository.getMessage(saved.id()).get(5, TimeUnit.SECONDS);
            assertTrue(retrieved.isPresent());
            assertNotNull(retrieved.get().readAt());
        }

        @Test
        @DisplayName("Unread count")
        void unreadCount() throws Exception {
            UUID playerUuid = UUID.randomUUID();

            for (int i = 0; i < 5; i++) {
                MailboxMessage msg = MailboxMessage.builder()
                    .sender(null, "Admin")
                    .recipient(playerUuid)
                    .subject("Message " + i)
                    .messageType(MessageType.ADMIN)
                    .build();
                MailboxMessage saved = repository.saveMessage(msg).get(5, TimeUnit.SECONDS);

                if (i < 2) {
                    repository.markAsRead(saved.id(), Instant.now()).get(5, TimeUnit.SECONDS);
                }
            }

            int unread = repository.getUnreadCount(playerUuid).get(5, TimeUnit.SECONDS);
            assertEquals(3, unread);
        }
    }

    @Nested
    @DisplayName("News Operations")
    class NewsOperations {

        @Test
        @DisplayName("Save and retrieve news article")
        void saveAndRetrieveNews() throws Exception {
            NewsArticle article = NewsArticle.builder()
                .title("Test News")
                .content("This is test content")
                .category(NewsCategory.ANNOUNCEMENT)
                .authorName("Admin")
                .publishedAt(Instant.now())
                .active(true)
                .build();

            NewsArticle saved = repository.saveNewsArticle(article).get(5, TimeUnit.SECONDS);
            assertNotNull(saved);

            var retrieved = repository.getNewsArticle(saved.id()).get(5, TimeUnit.SECONDS);
            assertTrue(retrieved.isPresent());
            assertEquals("Test News", retrieved.get().title());
        }

        @Test
        @DisplayName("Get active news")
        void getActiveNews() throws Exception {
            for (int i = 0; i < 3; i++) {
                NewsArticle article = NewsArticle.builder()
                    .title("News " + i)
                    .content("Content " + i)
                    .category(NewsCategory.ANNOUNCEMENT)
                    .publishedAt(Instant.now())
                    .active(true)
                    .build();
                repository.saveNewsArticle(article).get(5, TimeUnit.SECONDS);
            }

            List<NewsArticle> active = repository.getActiveNews(100).get(5, TimeUnit.SECONDS);
            assertEquals(3, active.size());
        }
    }

    @Nested
    @DisplayName("Task Operations")
    class TaskOperations {

        @Test
        @DisplayName("Save and retrieve task")
        void saveAndRetrieveTask() throws Exception {
            UUID assigneeUuid = UUID.randomUUID();
            TestTask task = TestTask.builder()
                .title("Test Task")
                .description("Test description")
                .assignedTo(assigneeUuid)
                .assignedByName("Admin")
                .priority(2)
                .build();

            TestTask saved = repository.saveTask(task).get(5, TimeUnit.SECONDS);
            assertNotNull(saved);

            var retrieved = repository.getTask(saved.id()).get(5, TimeUnit.SECONDS);
            assertTrue(retrieved.isPresent());
            assertEquals("Test Task", retrieved.get().title());
        }

        @Test
        @DisplayName("Get tasks for user")
        void getTasksForUser() throws Exception {
            UUID userUuid = UUID.randomUUID();

            for (int i = 0; i < 3; i++) {
                TestTask task = TestTask.builder()
                    .title("Task " + i)
                    .assignedTo(userUuid)
                    .build();
                repository.saveTask(task).get(5, TimeUnit.SECONDS);
            }

            List<TestTask> tasks = repository.getTasksForUser(userUuid).get(5, TimeUnit.SECONDS);
            assertEquals(3, tasks.size());
        }

        @Test
        @DisplayName("Update task status")
        void updateTaskStatus() throws Exception {
            UUID userUuid = UUID.randomUUID();
            TestTask task = TestTask.builder()
                .title("Task to update")
                .assignedTo(userUuid)
                .status(TestTask.TaskStatus.PENDING)
                .build();

            TestTask saved = repository.saveTask(task).get(5, TimeUnit.SECONDS);
            assertEquals(TestTask.TaskStatus.PENDING, saved.status());

            TestTask updated = saved.withStatus(TestTask.TaskStatus.COMPLETED);
            repository.updateTask(updated).get(5, TimeUnit.SECONDS);

            var retrieved = repository.getTask(saved.id()).get(5, TimeUnit.SECONDS);
            assertTrue(retrieved.isPresent());
            assertEquals(TestTask.TaskStatus.COMPLETED, retrieved.get().status());
        }
    }

    @Nested
    @DisplayName("Task Audit Operations")
    class TaskAuditOperations {

        @Test
        @DisplayName("Save and retrieve task audit entry")
        void saveAndRetrieveAudit() throws Exception {
            UUID taskId = UUID.randomUUID();
            TaskAuditEntry entry = TaskAuditEntry.builder()
                .taskId(taskId)
                .action(TaskAuditEntry.Actions.CREATED)
                .actorName("Admin")
                .newValue("New Task")
                .build();

            TaskAuditEntry saved = repository.saveTaskAudit(entry).get(5, TimeUnit.SECONDS);
            assertNotNull(saved);

            List<TaskAuditEntry> history = repository.getTaskAuditHistory(taskId).get(5, TimeUnit.SECONDS);
            assertEquals(1, history.size());
            assertEquals(TaskAuditEntry.Actions.CREATED, history.get(0).action());
        }

        @Test
        @DisplayName("Get recent audits")
        void getRecentAudits() throws Exception {
            for (int i = 0; i < 5; i++) {
                TaskAuditEntry entry = TaskAuditEntry.builder()
                    .taskId(UUID.randomUUID())
                    .action(TaskAuditEntry.Actions.STATUS_CHANGED)
                    .actorName("Admin")
                    .oldValue("pending")
                    .newValue("completed")
                    .build();
                repository.saveTaskAudit(entry).get(5, TimeUnit.SECONDS);
            }

            List<TaskAuditEntry> recent = repository.getRecentTaskAudits(10).get(5, TimeUnit.SECONDS);
            assertEquals(5, recent.size());
        }
    }
}
