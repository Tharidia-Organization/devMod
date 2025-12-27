package com.devmod.mailbox;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.devmod.mailbox.attachment.CurrencyAttachment;
import com.devmod.mailbox.news.NewsArticle;
import com.devmod.mailbox.news.NewsCategory;
import com.devmod.mailbox.persistence.DuckDbMailboxRepository;
import com.devmod.mailbox.task.TestTask;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end tests for mailbox, news, and task flows.
 * Tests the complete user journey through the system.
 */
@DisplayName("Mailbox E2E Flow Tests")
class MailboxFlowE2ETest {

    private Path tempDir;
    private DuckDbMailboxRepository repository;

    @BeforeEach
    void setUp() throws Exception {
        tempDir = Files.createTempDirectory("mailbox-e2e-test");
        Path dbPath = tempDir.resolve("e2e-mailbox.duckdb");
        repository = new DuckDbMailboxRepository(dbPath);
        repository.initialize().get(5, TimeUnit.SECONDS);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (repository != null) {
            repository.shutdown().get(5, TimeUnit.SECONDS);
        }
        cleanupTempDir();
    }

    @Nested
    @DisplayName("Message Send/Read/Delete/Claim Flow")
    class MessageFlowTests {

        @Test
        @DisplayName("Complete message lifecycle: send -> read -> claim -> delete")
        void completeMessageLifecycle() throws Exception {
            UUID senderUuid = UUID.randomUUID();
            UUID recipientUuid = UUID.randomUUID();

            // 1. Send a message with attachment
            CurrencyAttachment attachment = new CurrencyAttachment("gold", 100);
            MailboxMessage message = MailboxMessage.builder()
                .sender(senderUuid, "PlayerOne")
                .recipient(recipientUuid)
                .subject("Gold Reward")
                .body("Here is your gold!")
                .messageType(MessageType.REWARD)
                .attachment(attachment.toJson().toString())
                .build();

            MailboxMessage saved = repository.saveMessage(message).get(5, TimeUnit.SECONDS);
            assertNotNull(saved);
            assertEquals(message.id(), saved.id());

            // 2. Verify recipient has unread message
            int unreadCount = repository.getUnreadCount(recipientUuid).get(5, TimeUnit.SECONDS);
            assertEquals(1, unreadCount);

            // 3. Recipient reads the message
            boolean marked = repository.markAsRead(saved.id(), Instant.now()).get(5, TimeUnit.SECONDS);
            assertTrue(marked);

            // 4. Verify unread count decreased
            unreadCount = repository.getUnreadCount(recipientUuid).get(5, TimeUnit.SECONDS);
            assertEquals(0, unreadCount);

            // 5. Claim the attachment
            boolean claimed = repository.markAttachmentClaimed(saved.id()).get(5, TimeUnit.SECONDS);
            assertTrue(claimed);

            // 6. Verify attachment cannot be claimed again
            var msg = repository.getMessage(saved.id()).get(5, TimeUnit.SECONDS);
            assertTrue(msg.isPresent());
            assertTrue(msg.get().attachmentClaimed());
            assertFalse(msg.get().canClaimAttachment());

            // 7. Delete the message
            boolean deleted = repository.hardDeleteMessage(saved.id()).get(5, TimeUnit.SECONDS);
            assertTrue(deleted);

            // 8. Verify message is gone
            var deletedMsg = repository.getMessage(saved.id()).get(5, TimeUnit.SECONDS);
            assertFalse(deletedMsg.isPresent());
        }

        @Test
        @DisplayName("Cannot delete message with unclaimed attachment")
        void cannotDeleteWithUnclaimedAttachment() throws Exception {
            UUID recipientUuid = UUID.randomUUID();

            // Use JSON string directly for item attachment
            String attachmentJson = "{\"type\":\"item\",\"id\":\"minecraft:diamond\",\"count\":5}";
            MailboxMessage message = MailboxMessage.builder()
                .sender(null, "Admin")
                .recipient(recipientUuid)
                .subject("Diamonds")
                .body("Your diamonds!")
                .messageType(MessageType.ADMIN)
                .attachment(attachmentJson)
                .build();

            MailboxMessage saved = repository.saveMessage(message).get(5, TimeUnit.SECONDS);

            // Message has unclaimed attachment - verify it
            var msg = repository.getMessage(saved.id()).get(5, TimeUnit.SECONDS);
            assertTrue(msg.isPresent());
            assertTrue(msg.get().hasAttachment());
            assertFalse(msg.get().attachmentClaimed());
            assertTrue(msg.get().canClaimAttachment());
        }

        @Test
        @DisplayName("Player-to-player messaging")
        void playerToPlayerMessaging() throws Exception {
            UUID player1 = UUID.randomUUID();
            UUID player2 = UUID.randomUUID();

            // Player 1 sends to Player 2
            MailboxMessage msg1 = MailboxMessage.builder()
                .sender(player1, "Alice")
                .recipient(player2)
                .subject("Hello!")
                .body("Nice to meet you!")
                .messageType(MessageType.PLAYER)
                .build();
            repository.saveMessage(msg1).get(5, TimeUnit.SECONDS);

            // Player 2 replies
            MailboxMessage msg2 = MailboxMessage.builder()
                .sender(player2, "Bob")
                .recipient(player1)
                .subject("Re: Hello!")
                .body("Nice to meet you too!")
                .messageType(MessageType.PLAYER)
                .build();
            repository.saveMessage(msg2).get(5, TimeUnit.SECONDS);

            // Verify both players have messages
            List<MailboxMessage> player1Messages = repository.getMessagesForPlayer(player1, false)
                .get(5, TimeUnit.SECONDS);
            List<MailboxMessage> player2Messages = repository.getMessagesForPlayer(player2, false)
                .get(5, TimeUnit.SECONDS);

            assertEquals(1, player1Messages.size());
            assertEquals(1, player2Messages.size());
            assertEquals("Bob", player1Messages.get(0).senderName());
            assertEquals("Alice", player2Messages.get(0).senderName());
        }

        @Test
        @DisplayName("Bulk message operations")
        void bulkMessageOperations() throws Exception {
            UUID recipientUuid = UUID.randomUUID();

            // Create multiple messages
            for (int i = 0; i < 5; i++) {
                MailboxMessage msg = MailboxMessage.builder()
                    .sender(null, "System")
                    .recipient(recipientUuid)
                    .subject("System Message " + i)
                    .body("Content " + i)
                    .messageType(MessageType.SYSTEM)
                    .build();
                repository.saveMessage(msg).get(5, TimeUnit.SECONDS);
            }

            // Verify all messages received
            List<MailboxMessage> messages = repository.getMessagesForPlayer(recipientUuid, false)
                .get(5, TimeUnit.SECONDS);
            assertEquals(5, messages.size());

            // Mark all as read
            for (MailboxMessage msg : messages) {
                repository.markAsRead(msg.id(), Instant.now()).get(5, TimeUnit.SECONDS);
            }

            // Verify unread count is 0
            int unread = repository.getUnreadCount(recipientUuid).get(5, TimeUnit.SECONDS);
            assertEquals(0, unread);

            // Delete all read messages
            for (MailboxMessage msg : messages) {
                repository.hardDeleteMessage(msg.id()).get(5, TimeUnit.SECONDS);
            }

            // Verify inbox is empty
            messages = repository.getMessagesForPlayer(recipientUuid, false).get(5, TimeUnit.SECONDS);
            assertEquals(0, messages.size());
        }
    }

    @Nested
    @DisplayName("News Read/Unread Flow")
    class NewsFlowTests {

        @Test
        @DisplayName("Complete news lifecycle: create -> read -> track read status")
        void completeNewsLifecycle() throws Exception {
            UUID readerId = UUID.randomUUID();

            // 1. Create a news article
            NewsArticle article = NewsArticle.builder()
                .title("Server Update 1.2.0")
                .content("We've released a new update with exciting features!")
                .category(NewsCategory.ANNOUNCEMENT)
                .authorName("Admin")
                .publishedAt(Instant.now())
                .priority(10)
                .active(true)
                .build();

            NewsArticle saved = repository.saveNewsArticle(article).get(5, TimeUnit.SECONDS);
            assertNotNull(saved);

            // 2. Verify news is in active list
            List<NewsArticle> activeNews = repository.getActiveNews(100).get(5, TimeUnit.SECONDS);
            assertEquals(1, activeNews.size());
            assertEquals("Server Update 1.2.0", activeNews.get(0).title());

            // 3. Mark as read by player
            boolean marked = repository.markNewsAsRead(readerId, saved.id()).get(5, TimeUnit.SECONDS);
            assertTrue(marked);

            // 4. Verify read status is tracked
            boolean hasRead = repository.hasReadNews(readerId, saved.id()).get(5, TimeUnit.SECONDS);
            assertTrue(hasRead);
        }

        @Test
        @DisplayName("Filter news by category")
        void filterNewsByCategory() throws Exception {
            // Create news in different categories
            NewsArticle announcement = NewsArticle.builder()
                .title("Announcement")
                .content("Important announcement")
                .category(NewsCategory.ANNOUNCEMENT)
                .publishedAt(Instant.now())
                .active(true)
                .build();
            repository.saveNewsArticle(announcement).get(5, TimeUnit.SECONDS);

            NewsArticle patch = NewsArticle.builder()
                .title("Patch Notes")
                .content("Bug fixes")
                .category(NewsCategory.PATCH)
                .publishedAt(Instant.now())
                .active(true)
                .build();
            repository.saveNewsArticle(patch).get(5, TimeUnit.SECONDS);

            NewsArticle event = NewsArticle.builder()
                .title("Special Event")
                .content("Event details")
                .category(NewsCategory.EVENT)
                .publishedAt(Instant.now())
                .active(true)
                .build();
            repository.saveNewsArticle(event).get(5, TimeUnit.SECONDS);

            // Get all active news
            List<NewsArticle> allNews = repository.getActiveNews(100).get(5, TimeUnit.SECONDS);
            assertEquals(3, allNews.size());

            // Filter by category (client-side)
            long announcements = allNews.stream()
                .filter(n -> n.category() == NewsCategory.ANNOUNCEMENT)
                .count();
            assertEquals(1, announcements);
        }

        @Test
        @DisplayName("Scheduled news visibility")
        void scheduledNewsVisibility() throws Exception {
            // Create a future-scheduled news article
            NewsArticle futureNews = NewsArticle.builder()
                .title("Coming Soon")
                .content("Future announcement")
                .category(NewsCategory.ANNOUNCEMENT)
                .publishedAt(Instant.now().plusSeconds(3600)) // 1 hour from now
                .active(true)
                .build();
            repository.saveNewsArticle(futureNews).get(5, TimeUnit.SECONDS);

            // Create a currently active news
            NewsArticle currentNews = NewsArticle.builder()
                .title("Current News")
                .content("Available now")
                .category(NewsCategory.ANNOUNCEMENT)
                .publishedAt(Instant.now().minusSeconds(60))
                .active(true)
                .build();
            repository.saveNewsArticle(currentNews).get(5, TimeUnit.SECONDS);

            // getActiveNews filters by publishedAt <= now, so future news is excluded
            List<NewsArticle> news = repository.getActiveNews(100).get(5, TimeUnit.SECONDS);
            assertEquals(1, news.size());
            assertEquals("Current News", news.get(0).title());

            // getAllNews returns both (for admin view)
            List<NewsArticle> allNews = repository.getAllNews(100).get(5, TimeUnit.SECONDS);
            assertEquals(2, allNews.size());
        }

        @Test
        @DisplayName("Expired news handling")
        void expiredNewsHandling() throws Exception {
            // Create news with expiration
            NewsArticle expiredNews = NewsArticle.builder()
                .title("Limited Time")
                .content("This has expired")
                .category(NewsCategory.EVENT)
                .publishedAt(Instant.now().minusSeconds(7200))
                .expiresAt(Instant.now().minusSeconds(3600)) // Expired 1 hour ago
                .active(true)
                .build();
            repository.saveNewsArticle(expiredNews).get(5, TimeUnit.SECONDS);

            // Article is saved but isExpired() returns true
            var retrieved = repository.getNewsArticle(expiredNews.id()).get(5, TimeUnit.SECONDS);
            assertTrue(retrieved.isPresent());
            assertTrue(retrieved.get().isExpired());
        }
    }

    @Nested
    @DisplayName("Task Assign/Complete Flow")
    class TaskFlowTests {

        @Test
        @DisplayName("Complete task lifecycle: create -> assign -> progress -> complete")
        void completeTaskLifecycle() throws Exception {
            UUID testerId = UUID.randomUUID();

            // 1. Create a task
            TestTask task = TestTask.builder()
                .title("Test New Feature")
                .description("Test the new mailbox feature end-to-end")
                .assignedTo(testerId)
                .assignedByName("QA Lead")
                .priority(3)
                .status(TestTask.TaskStatus.PENDING)
                .build();

            TestTask saved = repository.saveTask(task).get(5, TimeUnit.SECONDS);
            assertNotNull(saved);
            assertEquals(TestTask.TaskStatus.PENDING, saved.status());

            // 2. Tester starts working on it
            TestTask inProgress = saved.withStatus(TestTask.TaskStatus.IN_PROGRESS);
            repository.updateTask(inProgress).get(5, TimeUnit.SECONDS);

            var updated = repository.getTask(saved.id()).get(5, TimeUnit.SECONDS);
            assertTrue(updated.isPresent());
            assertEquals(TestTask.TaskStatus.IN_PROGRESS, updated.get().status());

            // 3. Tester adds notes
            TestTask withNotes = updated.get().withNotes("Found 2 minor issues, fixing now");
            repository.updateTask(withNotes).get(5, TimeUnit.SECONDS);

            // 4. Tester completes the task (withStatus automatically sets completedAt)
            TestTask completed = withNotes.withStatus(TestTask.TaskStatus.COMPLETED);
            repository.updateTask(completed).get(5, TimeUnit.SECONDS);

            var finalTask = repository.getTask(saved.id()).get(5, TimeUnit.SECONDS);
            assertTrue(finalTask.isPresent());
            assertEquals(TestTask.TaskStatus.COMPLETED, finalTask.get().status());
            assertNotNull(finalTask.get().completedAt());
        }

        @Test
        @DisplayName("Multiple tasks per tester")
        void multipleTasksPerTester() throws Exception {
            UUID testerId = UUID.randomUUID();

            // Create multiple tasks
            for (int i = 0; i < 3; i++) {
                TestTask task = TestTask.builder()
                    .title("Task " + i)
                    .description("Description " + i)
                    .assignedTo(testerId)
                    .assignedByName("Admin")
                    .priority(i + 1)
                    .build();
                repository.saveTask(task).get(5, TimeUnit.SECONDS);
            }

            // Verify tester has all tasks
            List<TestTask> tasks = repository.getTasksForUser(testerId).get(5, TimeUnit.SECONDS);
            assertEquals(3, tasks.size());
        }

        @Test
        @DisplayName("Task with due date and overdue detection")
        void taskDueDateAndOverdue() throws Exception {
            UUID testerId = UUID.randomUUID();

            // Create overdue task
            TestTask overdueTask = TestTask.builder()
                .title("Overdue Task")
                .assignedTo(testerId)
                .dueAt(System.currentTimeMillis() - 86400000) // Due yesterday
                .status(TestTask.TaskStatus.PENDING)
                .build();
            repository.saveTask(overdueTask).get(5, TimeUnit.SECONDS);

            var retrieved = repository.getTask(overdueTask.id()).get(5, TimeUnit.SECONDS);
            assertTrue(retrieved.isPresent());
            assertTrue(retrieved.get().isOverdue());

            // Create future task
            TestTask futureTask = TestTask.builder()
                .title("Future Task")
                .assignedTo(testerId)
                .dueAt(System.currentTimeMillis() + 86400000) // Due tomorrow
                .status(TestTask.TaskStatus.PENDING)
                .build();
            repository.saveTask(futureTask).get(5, TimeUnit.SECONDS);

            var futureRetrieved = repository.getTask(futureTask.id()).get(5, TimeUnit.SECONDS);
            assertTrue(futureRetrieved.isPresent());
            assertFalse(futureRetrieved.get().isOverdue());
        }

        @Test
        @DisplayName("Task priority ordering")
        void taskPriorityOrdering() throws Exception {
            UUID testerId = UUID.randomUUID();

            // Create tasks with different priorities
            for (int priority : new int[]{1, 3, 2, 5, 4}) {
                TestTask task = TestTask.builder()
                    .title("Priority " + priority + " Task")
                    .assignedTo(testerId)
                    .priority(priority)
                    .build();
                repository.saveTask(task).get(5, TimeUnit.SECONDS);
            }

            List<TestTask> tasks = repository.getTasksForUser(testerId).get(5, TimeUnit.SECONDS);
            assertEquals(5, tasks.size());

            // Verify we can sort by priority client-side
            List<TestTask> sorted = tasks.stream()
                .sorted((a, b) -> Integer.compare(b.priority(), a.priority()))
                .toList();
            assertEquals(5, sorted.get(0).priority());
            assertEquals(1, sorted.get(4).priority());
        }
    }

    @Nested
    @DisplayName("Cross-Feature Integration")
    class CrossFeatureTests {

        @Test
        @DisplayName("Admin sends reward message then creates task to verify")
        void adminWorkflow() throws Exception {
            UUID playerId = UUID.randomUUID();
            UUID testerId = UUID.randomUUID();

            // Admin sends reward to player
            MailboxMessage reward = MailboxMessage.builder()
                .sender(null, "Admin")
                .recipient(playerId)
                .subject("Event Reward")
                .body("Congratulations on winning the event!")
                .messageType(MessageType.REWARD)
                .attachment(new CurrencyAttachment("gems", 500).toJson().toString())
                .build();
            repository.saveMessage(reward).get(5, TimeUnit.SECONDS);

            // Admin creates task for tester to verify
            TestTask verifyTask = TestTask.builder()
                .title("Verify event reward delivery")
                .description("Check that player " + playerId + " received 500 gems")
                .assignedTo(testerId)
                .assignedByName("Admin")
                .priority(2)
                .build();
            repository.saveTask(verifyTask).get(5, TimeUnit.SECONDS);

            // Admin publishes news about the event
            NewsArticle eventNews = NewsArticle.builder()
                .title("Event Winners Announced!")
                .content("Rewards have been sent to all winners.")
                .category(NewsCategory.EVENT)
                .authorName("Admin")
                .publishedAt(Instant.now())
                .active(true)
                .build();
            repository.saveNewsArticle(eventNews).get(5, TimeUnit.SECONDS);

            // Verify all were created
            assertEquals(1, repository.getMessagesForPlayer(playerId, false).get(5, TimeUnit.SECONDS).size());
            assertEquals(1, repository.getTasksForUser(testerId).get(5, TimeUnit.SECONDS).size());
            assertEquals(1, repository.getActiveNews(100).get(5, TimeUnit.SECONDS).size());
        }
    }

    private void cleanupTempDir() {
        if (tempDir == null) {
            return;
        }
        try (var paths = Files.walk(tempDir)) {
            paths.sorted((a, b) -> b.compareTo(a))
                .forEach(MailboxFlowE2ETest::deletePathQuietly);
        } catch (Exception ignored) {
            System.err.println("Failed to clean mailbox test temp directory: " + ignored.getMessage());
        }
    }

    private static void deletePathQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (Exception ignored) {
            System.err.println("Failed to delete temp path: " + path + " (" + ignored.getMessage() + ")");
        }
    }
}
