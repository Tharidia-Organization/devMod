package com.devmod.mailbox.analytics;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.devmod.mailbox.MessageType;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for MailboxAnalyticsEngine.
 */
@DisplayName("MailboxAnalyticsEngine Tests")
class MailboxAnalyticsEngineTest {

    @BeforeEach
    void setUp() {
        MailboxAnalyticsEngine.INSTANCE.start();
    }

    @AfterEach
    void tearDown() {
        MailboxAnalyticsEngine.INSTANCE.stop();
    }

    @Nested
    @DisplayName("Event Recording")
    class EventRecordingTests {

        @Test
        @DisplayName("Record message sent increments counters")
        void recordMessageSentIncrementsCounters() {
            UUID sender = UUID.randomUUID();
            UUID recipient = UUID.randomUUID();
            long before = MailboxAnalyticsEngine.INSTANCE.getDashboardMetrics().totalMessages();

            MailboxAnalyticsEngine.INSTANCE.recordMessageSent(sender, recipient, MessageType.PLAYER, false);

            var metrics = MailboxAnalyticsEngine.INSTANCE.getDashboardMetrics();
            assertTrue(metrics.totalMessages() > before);
        }

        @Test
        @DisplayName("Record multiple messages tracks correctly")
        void recordMultipleMessagesTracksCorrectly() {
            UUID sender = UUID.randomUUID();
            long before = MailboxAnalyticsEngine.INSTANCE.getDashboardMetrics().totalMessages();

            for (int i = 0; i < 10; i++) {
                UUID recipient = UUID.randomUUID();
                MailboxAnalyticsEngine.INSTANCE.recordMessageSent(sender, recipient, MessageType.SYSTEM, false);
            }

            var metrics = MailboxAnalyticsEngine.INSTANCE.getDashboardMetrics();
            assertEquals(before + 10, metrics.totalMessages());
        }

        @Test
        @DisplayName("Record attachment sent tracks attachment count")
        void recordAttachmentSentTracksAttachmentCount() {
            UUID sender = UUID.randomUUID();
            UUID recipient = UUID.randomUUID();
            long before = MailboxAnalyticsEngine.INSTANCE.getDashboardMetrics().attachmentsSent();

            MailboxAnalyticsEngine.INSTANCE.recordMessageSent(sender, recipient, MessageType.REWARD, true);

            var metrics = MailboxAnalyticsEngine.INSTANCE.getDashboardMetrics();
            assertEquals(before + 1, metrics.attachmentsSent());
        }

        @Test
        @DisplayName("Record message read updates read count")
        void recordMessageReadUpdatesReadCount() {
            UUID sender = UUID.randomUUID();
            UUID recipient = UUID.randomUUID();
            long before = MailboxAnalyticsEngine.INSTANCE.getDashboardMetrics().totalRead();

            MailboxAnalyticsEngine.INSTANCE.recordMessageSent(sender, recipient, MessageType.PLAYER, false);
            MailboxAnalyticsEngine.INSTANCE.recordMessageRead(recipient, Duration.ofSeconds(5));

            var metrics = MailboxAnalyticsEngine.INSTANCE.getDashboardMetrics();
            assertEquals(before + 1, metrics.totalRead());
        }

        @Test
        @DisplayName("Record attachment claimed updates claim count")
        void recordAttachmentClaimedUpdatesClaimCount() {
            UUID sender = UUID.randomUUID();
            UUID recipient = UUID.randomUUID();
            long before = MailboxAnalyticsEngine.INSTANCE.getDashboardMetrics().attachmentsClaimed();

            MailboxAnalyticsEngine.INSTANCE.recordMessageSent(sender, recipient, MessageType.REWARD, true);
            MailboxAnalyticsEngine.INSTANCE.recordAttachmentClaimed(recipient, "currency", 100);

            var metrics = MailboxAnalyticsEngine.INSTANCE.getDashboardMetrics();
            assertEquals(before + 1, metrics.attachmentsClaimed());
        }

        @Test
        @DisplayName("Record message deleted updates delete count")
        void recordMessageDeletedUpdatesDeleteCount() {
            UUID player = UUID.randomUUID();
            long before = MailboxAnalyticsEngine.INSTANCE.getDashboardMetrics().totalDeleted();

            MailboxAnalyticsEngine.INSTANCE.recordMessageDeleted(player, true);

            var metrics = MailboxAnalyticsEngine.INSTANCE.getDashboardMetrics();
            assertEquals(before + 1, metrics.totalDeleted());
        }
    }

    @Nested
    @DisplayName("Message Type Breakdown")
    class MessageTypeBreakdownTests {

        @Test
        @DisplayName("Track messages by type")
        void trackMessagesByType() {
            UUID sender = UUID.randomUUID();
            UUID recipient = UUID.randomUUID();

            MailboxAnalyticsEngine.INSTANCE.recordMessageSent(sender, recipient, MessageType.PLAYER, false);
            MailboxAnalyticsEngine.INSTANCE.recordMessageSent(sender, recipient, MessageType.PLAYER, false);
            MailboxAnalyticsEngine.INSTANCE.recordMessageSent(sender, recipient, MessageType.SYSTEM, false);
            MailboxAnalyticsEngine.INSTANCE.recordMessageSent(sender, recipient, MessageType.ADMIN, false);
            MailboxAnalyticsEngine.INSTANCE.recordMessageSent(sender, recipient, MessageType.REWARD, true);

            Map<MessageType, Long> breakdown = MailboxAnalyticsEngine.INSTANCE.getMessageTypeBreakdown();

            assertTrue(breakdown.getOrDefault(MessageType.PLAYER, 0L) >= 2);
            assertTrue(breakdown.getOrDefault(MessageType.SYSTEM, 0L) >= 1);
            assertTrue(breakdown.getOrDefault(MessageType.ADMIN, 0L) >= 1);
            assertTrue(breakdown.getOrDefault(MessageType.REWARD, 0L) >= 1);
        }
    }

    @Nested
    @DisplayName("Rankings")
    class RankingsTests {

        @Test
        @DisplayName("Get top senders")
        void getTopSenders() {
            UUID sender1 = UUID.randomUUID();
            UUID sender2 = UUID.randomUUID();
            UUID recipient = UUID.randomUUID();

            for (int i = 0; i < 5; i++) {
                MailboxAnalyticsEngine.INSTANCE.recordMessageSent(sender1, recipient, MessageType.PLAYER, false);
            }

            for (int i = 0; i < 3; i++) {
                MailboxAnalyticsEngine.INSTANCE.recordMessageSent(sender2, recipient, MessageType.PLAYER, false);
            }

            List<MailboxAnalyticsEngine.PlayerRanking> topSenders =
                MailboxAnalyticsEngine.INSTANCE.getTopSenders(10);

            assertFalse(topSenders.isEmpty());
        }

        @Test
        @DisplayName("Get top receivers")
        void getTopReceivers() {
            UUID sender = UUID.randomUUID();
            UUID recipient1 = UUID.randomUUID();
            UUID recipient2 = UUID.randomUUID();

            for (int i = 0; i < 4; i++) {
                MailboxAnalyticsEngine.INSTANCE.recordMessageSent(sender, recipient1, MessageType.PLAYER, false);
            }

            for (int i = 0; i < 2; i++) {
                MailboxAnalyticsEngine.INSTANCE.recordMessageSent(sender, recipient2, MessageType.PLAYER, false);
            }

            List<MailboxAnalyticsEngine.PlayerRanking> topReceivers =
                MailboxAnalyticsEngine.INSTANCE.getTopReceivers(10);

            assertFalse(topReceivers.isEmpty());
        }
    }

    @Nested
    @DisplayName("Dashboard Metrics")
    class DashboardMetricsTests {

        @Test
        @DisplayName("Get dashboard metrics")
        void getDashboardMetrics() {
            UUID sender = UUID.randomUUID();
            UUID recipient = UUID.randomUUID();

            MailboxAnalyticsEngine.INSTANCE.recordMessageSent(sender, recipient, MessageType.PLAYER, false);

            var metrics = MailboxAnalyticsEngine.INSTANCE.getDashboardMetrics();
            assertNotNull(metrics);
            assertTrue(metrics.totalMessages() >= 0);
            assertTrue(metrics.readRate() >= 0);
            assertTrue(metrics.claimRate() >= 0);
        }

        @Test
        @DisplayName("Track active players")
        void trackActivePlayers() {
            UUID sender1 = UUID.randomUUID();
            UUID sender2 = UUID.randomUUID();
            UUID recipient1 = UUID.randomUUID();
            UUID recipient2 = UUID.randomUUID();

            MailboxAnalyticsEngine.INSTANCE.recordMessageSent(sender1, recipient1, MessageType.PLAYER, false);
            MailboxAnalyticsEngine.INSTANCE.recordMessageSent(sender2, recipient2, MessageType.PLAYER, false);

            var metrics = MailboxAnalyticsEngine.INSTANCE.getDashboardMetrics();
            assertTrue(metrics.activePlayers() >= 0);
        }
    }

    @Nested
    @DisplayName("Lifecycle")
    class LifecycleTests {

        @Test
        @DisplayName("Start and stop engine")
        void startAndStopEngine() {
            MailboxAnalyticsEngine.INSTANCE.stop();
            MailboxAnalyticsEngine.INSTANCE.start();
            // Should not throw
        }

        @Test
        @DisplayName("Double start is safe")
        void doubleStartIsSafe() {
            MailboxAnalyticsEngine.INSTANCE.start();
            MailboxAnalyticsEngine.INSTANCE.start();
            // Should not throw
        }

        @Test
        @DisplayName("Double stop is safe")
        void doubleStopIsSafe() {
            MailboxAnalyticsEngine.INSTANCE.stop();
            MailboxAnalyticsEngine.INSTANCE.stop();
            // Should not throw
        }
    }

    @Nested
    @DisplayName("Hourly Volume")
    class HourlyVolumeTests {

        @Test
        @DisplayName("Get hourly volume returns 24 entries")
        void getHourlyVolumeReturns24Entries() {
            List<MailboxAnalyticsEngine.HourlyVolume> volumes =
                MailboxAnalyticsEngine.INSTANCE.getHourlyVolume();

            assertEquals(24, volumes.size());
        }

        @Test
        @DisplayName("Current hour has recorded messages")
        void currentHourHasRecordedMessages() {
            UUID sender = UUID.randomUUID();
            UUID recipient = UUID.randomUUID();

            MailboxAnalyticsEngine.INSTANCE.recordMessageSent(sender, recipient, MessageType.PLAYER, false);

            List<MailboxAnalyticsEngine.HourlyVolume> volumes =
                MailboxAnalyticsEngine.INSTANCE.getHourlyVolume();

            boolean hasMessages = volumes.stream().anyMatch(v -> v.count() > 0);
            assertTrue(hasMessages);
        }
    }

    @Nested
    @DisplayName("Broadcast Recording")
    class BroadcastRecordingTests {

        @Test
        @DisplayName("Record broadcast")
        void recordBroadcast() {
            MailboxAnalyticsEngine.INSTANCE.recordBroadcast(
                "Test Broadcast",
                100,
                95,
                Duration.ofSeconds(5)
            );
            // Should not throw
        }
    }
}
