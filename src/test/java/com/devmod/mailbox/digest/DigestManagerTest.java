package com.devmod.mailbox.digest;

import java.time.Duration;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.devmod.mailbox.digest.DigestManager.DigestPreferences;
import com.devmod.mailbox.digest.DigestManager.DigestStats;
import com.devmod.mailbox.digest.DigestManager.NotificationType;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for DigestManager.
 */
@DisplayName("DigestManager Tests")
class DigestManagerTest {

    @BeforeEach
    void setUp() {
        DigestManager.INSTANCE.start();
    }

    @AfterEach
    void tearDown() {
        DigestManager.INSTANCE.stop();
    }

    @Nested
    @DisplayName("Notification Queueing")
    class NotificationQueueingTests {

        @Test
        @DisplayName("Queue low priority notification for digest")
        void queueLowPriorityNotificationForDigest() {
            UUID player = UUID.randomUUID();

            boolean queued = DigestManager.INSTANCE.queueNotification(
                player,
                NotificationType.NEW_MESSAGE,
                "New message from Admin",
                "You have a new message",
                3,
                Map.of("messageId", UUID.randomUUID().toString())
            );

            assertTrue(queued);

            DigestStats stats = DigestManager.INSTANCE.getStats();
            assertTrue(stats.totalPending() >= 1);
        }

        @Test
        @DisplayName("High priority notification is delivered immediately")
        void highPriorityNotificationDeliveredImmediately() {
            UUID player = UUID.randomUUID();

            DigestManager.INSTANCE.setPreferences(player, new DigestPreferences(
                true,
                Duration.ofHours(4),
                5,
                8,
                LocalTime.of(22, 0),
                LocalTime.of(8, 0),
                ZoneId.systemDefault(),
                null
            ));

            boolean queued = DigestManager.INSTANCE.queueNotification(
                player,
                NotificationType.SYSTEM_ALERT,
                "Critical Alert",
                "Server is shutting down!",
                9,
                Map.of()
            );

            assertFalse(queued, "High priority notifications should bypass the digest queue");
            DigestStats stats = DigestManager.INSTANCE.getStats();
            assertTrue(stats.immediateDeliveries() >= 1, "Immediate deliveries should be tracked");
        }

        @Test
        @DisplayName("Queue multiple notifications for same player")
        void queueMultipleNotificationsForSamePlayer() {
            UUID player = UUID.randomUUID();

            for (int i = 0; i < 5; i++) {
                DigestManager.INSTANCE.queueNotification(
                    player,
                    NotificationType.NEW_MESSAGE,
                    "Message " + i,
                    "Summary " + i,
                    3,
                    Map.of()
                );
            }

            DigestStats stats = DigestManager.INSTANCE.getStats();
            assertTrue(stats.totalPending() >= 5);
        }

        @Test
        @DisplayName("Queue notifications with different types")
        void queueNotificationsWithDifferentTypes() {
            UUID player = UUID.randomUUID();

            DigestManager.INSTANCE.queueNotification(
                player,
                NotificationType.NEW_MESSAGE,
                "New Message",
                "You got mail",
                3,
                Map.of()
            );

            DigestManager.INSTANCE.queueNotification(
                player,
                NotificationType.ATTACHMENT_RECEIVED,
                "Attachment Ready",
                "Claim your reward",
                4,
                Map.of()
            );

            DigestManager.INSTANCE.queueNotification(
                player,
                NotificationType.NEWS_PUBLISHED,
                "News Update",
                "Check out the latest news",
                2,
                Map.of()
            );

            DigestStats stats = DigestManager.INSTANCE.getStats();
            assertTrue(stats.totalPending() >= 3);
        }
    }

    @Nested
    @DisplayName("Player Preferences")
    class PlayerPreferencesTests {

        @Test
        @DisplayName("Set and get player preferences")
        void setAndGetPlayerPreferences() {
            UUID player = UUID.randomUUID();

            DigestPreferences prefs = new DigestPreferences(
                true,
                Duration.ofHours(2),
                3,
                7,
                LocalTime.of(23, 0),
                LocalTime.of(7, 0),
                ZoneId.of("Europe/Rome"),
                null
            );

            DigestManager.INSTANCE.setPreferences(player, prefs);

            DigestPreferences retrieved = DigestManager.INSTANCE.getPreferences(player);
            assertNotNull(retrieved);
            assertEquals(Duration.ofHours(2), retrieved.digestInterval());
            assertEquals(3, retrieved.batchThreshold());
            assertEquals(7, retrieved.immediatePriorityThreshold());
        }

        @Test
        @DisplayName("Default preferences are created for new player")
        void defaultPreferencesCreatedForNewPlayer() {
            UUID player = UUID.randomUUID();

            DigestManager.INSTANCE.queueNotification(
                player,
                NotificationType.NEW_MESSAGE,
                "Test",
                "Summary",
                3,
                Map.of()
            );

            DigestPreferences prefs = DigestManager.INSTANCE.getPreferences(player);
            assertNotNull(prefs);
            assertTrue(prefs.digestEnabled());
        }

        @Test
        @DisplayName("Disable digest for player")
        void disableDigestForPlayer() {
            UUID player = UUID.randomUUID();

            DigestPreferences prefs = new DigestPreferences(
                false,
                Duration.ofHours(4),
                5,
                8,
                LocalTime.of(22, 0),
                LocalTime.of(8, 0),
                ZoneId.systemDefault(),
                null
            );

            DigestManager.INSTANCE.setPreferences(player, prefs);

            DigestPreferences retrieved = DigestManager.INSTANCE.getPreferences(player);
            assertFalse(retrieved.digestEnabled());
        }
    }

    @Nested
    @DisplayName("Quiet Hours")
    class QuietHoursTests {

        @Test
        @DisplayName("Set quiet hours for player")
        void setQuietHoursForPlayer() {
            UUID player = UUID.randomUUID();

            DigestManager.INSTANCE.setQuietHours(
                player,
                LocalTime.of(22, 0),
                LocalTime.of(8, 0),
                ZoneId.of("America/New_York")
            );

            DigestPreferences prefs = DigestManager.INSTANCE.getPreferences(player);
            assertNotNull(prefs);
            assertEquals(LocalTime.of(22, 0), prefs.quietHoursStart());
            assertEquals(LocalTime.of(8, 0), prefs.quietHoursEnd());
        }
    }

    @Nested
    @DisplayName("Digest Flushing")
    class DigestFlushingTests {

        @Test
        @DisplayName("Flush digest for player")
        void flushDigestForPlayer() {
            UUID player = UUID.randomUUID();

            for (int i = 0; i < 3; i++) {
                DigestManager.INSTANCE.queueNotification(
                    player,
                    NotificationType.NEW_MESSAGE,
                    "Message " + i,
                    "Summary " + i,
                    3,
                    Map.of()
                );
            }

            DigestManager.INSTANCE.flushDigest(player);
            // Should not throw
        }

        @Test
        @DisplayName("Clear pending for player")
        void clearPendingForPlayer() {
            UUID player = UUID.randomUUID();

            for (int i = 0; i < 3; i++) {
                DigestManager.INSTANCE.queueNotification(
                    player,
                    NotificationType.NEW_MESSAGE,
                    "Message " + i,
                    "Summary " + i,
                    3,
                    Map.of()
                );
            }

            DigestManager.INSTANCE.clearPending(player);
            // Should clear pending for this player
        }
    }

    @Nested
    @DisplayName("Statistics")
    class StatisticsTests {

        @Test
        @DisplayName("Get digest stats")
        void getDigestStats() {
            DigestStats stats = DigestManager.INSTANCE.getStats();

            assertNotNull(stats);
            assertTrue(stats.running());
            assertTrue(stats.totalPending() >= 0);
            assertTrue(stats.playersWithPreferences() >= 0);
            assertTrue(stats.digestsSent() >= 0);
            assertTrue(stats.immediateDeliveries() >= 0);
        }

        @Test
        @DisplayName("Stats update after queueing notifications")
        void statsUpdateAfterQueueingNotifications() {
            UUID player = UUID.randomUUID();

            DigestStats beforeStats = DigestManager.INSTANCE.getStats();
            int pendingBefore = beforeStats.totalPending();

            DigestManager.INSTANCE.queueNotification(
                player,
                NotificationType.NEW_MESSAGE,
                "Test",
                "Summary",
                3,
                Map.of()
            );

            DigestStats afterStats = DigestManager.INSTANCE.getStats();
            assertTrue(afterStats.totalPending() > pendingBefore);
        }
    }

    @Nested
    @DisplayName("Lifecycle")
    class LifecycleTests {

        @Test
        @DisplayName("Start and stop manager")
        void startAndStopManager() {
            DigestManager.INSTANCE.stop();

            DigestStats stats = DigestManager.INSTANCE.getStats();
            assertFalse(stats.running());

            DigestManager.INSTANCE.start();
            stats = DigestManager.INSTANCE.getStats();
            assertTrue(stats.running());
        }

        @Test
        @DisplayName("Double start is safe")
        void doubleStartIsSafe() {
            DigestManager.INSTANCE.start();
            DigestStats stats = DigestManager.INSTANCE.getStats();
            assertTrue(stats.running());
        }

        @Test
        @DisplayName("Double stop is safe")
        void doubleStopIsSafe() {
            DigestManager.INSTANCE.stop();
            DigestManager.INSTANCE.stop();
            // Should not throw
        }
    }

    @Nested
    @DisplayName("Notification Types")
    class NotificationTypesTests {

        @Test
        @DisplayName("All notification types are valid")
        void allNotificationTypesAreValid() {
            UUID player = UUID.randomUUID();

            for (NotificationType type : NotificationType.values()) {
                boolean queued = DigestManager.INSTANCE.queueNotification(
                    player,
                    type,
                    "Title for " + type.name(),
                    "Summary",
                    3,
                    Map.of()
                );
                assertTrue(queued, "Failed to queue notification of type: " + type);
            }
        }
    }
}
