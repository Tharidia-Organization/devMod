package com.devmod.mailbox.scheduler;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.devmod.mailbox.MessageType;
import com.devmod.mailbox.scheduler.MessageScheduler.RecurrencePattern;
import com.devmod.mailbox.scheduler.MessageScheduler.ScheduledMessage;
import com.devmod.mailbox.scheduler.MessageScheduler.SchedulerStats;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for MessageScheduler.
 */
@DisplayName("MessageScheduler Tests")
class MessageSchedulerTest {

    @BeforeEach
    void setUp() {
        MessageScheduler.INSTANCE.start();
    }

    @AfterEach
    void tearDown() {
        MessageScheduler.INSTANCE.stop();
    }

    @Nested
    @DisplayName("Scheduling Messages")
    class SchedulingTests {

        @Test
        @DisplayName("Schedule a future message")
        void scheduleFutureMessage() {
            UUID recipient = UUID.randomUUID();
            Instant deliverAt = Instant.now().plus(Duration.ofHours(1));

            UUID scheduleId = MessageScheduler.INSTANCE.scheduleMessage(
                recipient,
                "Test Subject",
                "Test Body",
                deliverAt,
                "System",
                MessageType.SYSTEM,
                null
            );

            assertNotNull(scheduleId);

            Optional<ScheduledMessage> msg = MessageScheduler.INSTANCE.getScheduledMessage(scheduleId);
            assertTrue(msg.isPresent());
            assertEquals("Test Subject", msg.get().subject());
        }

        @Test
        @DisplayName("Schedule message with attachment")
        void scheduleMessageWithAttachment() {
            UUID recipient = UUID.randomUUID();
            Instant deliverAt = Instant.now().plus(Duration.ofMinutes(30));
            String attachmentData = "{\"type\":\"currency\",\"currency\":\"gold\",\"amount\":100}";

            UUID scheduleId = MessageScheduler.INSTANCE.scheduleMessage(
                recipient,
                "Reward",
                "Here's your gold!",
                deliverAt,
                "Admin",
                MessageType.REWARD,
                attachmentData
            );

            assertNotNull(scheduleId);

            Optional<ScheduledMessage> msg = MessageScheduler.INSTANCE.getScheduledMessage(scheduleId);
            assertTrue(msg.isPresent());
            assertNotNull(msg.get().attachmentData());
        }

        @Test
        @DisplayName("Schedule broadcast to multiple recipients")
        void scheduleBroadcastToMultipleRecipients() {
            List<UUID> recipients = List.of(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID()
            );
            Instant deliverAt = Instant.now().plus(Duration.ofHours(2));

            UUID batchId = MessageScheduler.INSTANCE.scheduleBroadcast(
                recipients,
                "Server Announcement",
                "Server maintenance in 2 hours",
                deliverAt,
                "Admin",
                MessageType.ADMIN
            );

            assertNotNull(batchId);

            var stats = MessageScheduler.INSTANCE.getStats();
            assertTrue(stats.pendingMessages() >= 3);
        }
    }

    @Nested
    @DisplayName("Cancellation")
    class CancellationTests {

        @Test
        @DisplayName("Cancel a scheduled message")
        void cancelScheduledMessage() {
            UUID recipient = UUID.randomUUID();
            Instant deliverAt = Instant.now().plus(Duration.ofHours(1));

            UUID scheduleId = MessageScheduler.INSTANCE.scheduleMessage(
                recipient,
                "Test",
                "Body",
                deliverAt,
                "System",
                MessageType.SYSTEM,
                null
            );

            boolean cancelled = MessageScheduler.INSTANCE.cancelMessage(scheduleId);
            assertTrue(cancelled);
        }

        @Test
        @DisplayName("Cancel non-existent message returns false")
        void cancelNonExistentMessageReturnsFalse() {
            UUID fakeId = UUID.randomUUID();
            boolean cancelled = MessageScheduler.INSTANCE.cancelMessage(fakeId);
            assertFalse(cancelled);
        }
    }

    @Nested
    @DisplayName("Recurring Templates")
    class RecurringTemplateTests {

        @Test
        @DisplayName("Create daily recurring template")
        void createDailyRecurringTemplate() {
            List<UUID> recipients = List.of(UUID.randomUUID(), UUID.randomUUID());

            UUID templateId = MessageScheduler.INSTANCE.createRecurringTemplate(
                recipients,
                "Daily Digest",
                "Here's your daily summary",
                "System",
                MessageType.SYSTEM,
                RecurrencePattern.DAILY,
                Instant.now(),
                null
            );

            assertNotNull(templateId);

            var stats = MessageScheduler.INSTANCE.getStats();
            assertTrue(stats.recurringTemplates() >= 1);
        }

        @Test
        @DisplayName("Create weekly recurring template with end date")
        void createWeeklyRecurringTemplateWithEndDate() {
            List<UUID> recipients = List.of(UUID.randomUUID());
            Instant endAt = Instant.now().plus(Duration.ofDays(30));

            UUID templateId = MessageScheduler.INSTANCE.createRecurringTemplate(
                recipients,
                "Weekly Report",
                "Weekly stats",
                "System",
                MessageType.SYSTEM,
                RecurrencePattern.WEEKLY,
                Instant.now(),
                endAt
            );

            assertNotNull(templateId);
        }

        @Test
        @DisplayName("Pause recurring template")
        void pauseRecurringTemplate() {
            List<UUID> recipients = List.of(UUID.randomUUID());

            UUID templateId = MessageScheduler.INSTANCE.createRecurringTemplate(
                recipients,
                "Hourly Alert",
                "Status update",
                "System",
                MessageType.SYSTEM,
                RecurrencePattern.HOURLY,
                Instant.now(),
                null
            );

            boolean paused = MessageScheduler.INSTANCE.pauseRecurringTemplate(templateId);
            assertTrue(paused);
        }

        @Test
        @DisplayName("Resume paused recurring template")
        void resumePausedRecurringTemplate() {
            List<UUID> recipients = List.of(UUID.randomUUID());

            UUID templateId = MessageScheduler.INSTANCE.createRecurringTemplate(
                recipients,
                "Periodic Message",
                "Content",
                "System",
                MessageType.SYSTEM,
                RecurrencePattern.DAILY,
                Instant.now(),
                null
            );

            MessageScheduler.INSTANCE.pauseRecurringTemplate(templateId);
            boolean resumed = MessageScheduler.INSTANCE.resumeRecurringTemplate(templateId);
            assertTrue(resumed);
        }

        @Test
        @DisplayName("Cancel recurring template")
        void cancelRecurringTemplate() {
            List<UUID> recipients = List.of(UUID.randomUUID());

            UUID templateId = MessageScheduler.INSTANCE.createRecurringTemplate(
                recipients,
                "To Cancel",
                "Content",
                "System",
                MessageType.SYSTEM,
                RecurrencePattern.MONTHLY,
                Instant.now(),
                null
            );

            boolean cancelled = MessageScheduler.INSTANCE.cancelRecurringTemplate(templateId);
            assertTrue(cancelled);
        }
    }

    @Nested
    @DisplayName("Statistics")
    class StatisticsTests {

        @Test
        @DisplayName("Get scheduler stats")
        void getSchedulerStats() {
            for (int i = 0; i < 3; i++) {
                UUID recipient = UUID.randomUUID();
                MessageScheduler.INSTANCE.scheduleMessage(
                    recipient,
                    "Message " + i,
                    "Body",
                    Instant.now().plus(Duration.ofHours(i + 1)),
                    "System",
                    MessageType.SYSTEM,
                    null
                );
            }

            SchedulerStats stats = MessageScheduler.INSTANCE.getStats();

            assertNotNull(stats);
            assertTrue(stats.pendingMessages() >= 3);
            assertTrue(stats.running());
        }
    }

    @Nested
    @DisplayName("Lifecycle")
    class LifecycleTests {

        @Test
        @DisplayName("Start and stop scheduler")
        void startAndStopScheduler() {
            MessageScheduler.INSTANCE.stop();

            var stats = MessageScheduler.INSTANCE.getStats();
            assertFalse(stats.running());

            MessageScheduler.INSTANCE.start();
            stats = MessageScheduler.INSTANCE.getStats();
            assertTrue(stats.running());
        }

        @Test
        @DisplayName("Double start is safe")
        void doubleStartIsSafe() {
            MessageScheduler.INSTANCE.start();
            var stats = MessageScheduler.INSTANCE.getStats();
            assertTrue(stats.running());
        }
    }

    @Nested
    @DisplayName("Delivery Callback")
    class DeliveryCallbackTests {

        @Test
        @DisplayName("Set delivery callback")
        void setDeliveryCallback() {
            MessageScheduler.INSTANCE.setDeliveryCallback(result -> {
                // Log or handle result
            });

            MessageScheduler.INSTANCE.setDeliveryCallback(null);
        }
    }
}
