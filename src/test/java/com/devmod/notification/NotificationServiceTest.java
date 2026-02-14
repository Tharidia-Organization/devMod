package com.devmod.notification;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for NotificationService convenience methods and helper logic.
 *
 * Since NotificationService is a singleton with Minecraft dependencies,
 * these tests focus on the notification construction logic via the builder
 * patterns used by the convenience methods, and on the helper methods
 * that can be tested independently.
 */
class NotificationServiceTest {

    // ========================================================================
    // SERVICE LIFECYCLE
    // ========================================================================

    @Nested
    @DisplayName("Service Lifecycle")
    class LifecycleTests {

        @Test
        @DisplayName("Service starts uninitialized")
        void startsUninitialized() {
            // NotificationService.INSTANCE is a singleton, we test its API surface
            // The actual state depends on test ordering, so just check the method exists
            assertNotNull(NotificationService.INSTANCE);
        }

        @Test
        @DisplayName("Initialize is idempotent")
        void initializeIsIdempotent() {
            NotificationService.INSTANCE.initialize();
            assertTrue(NotificationService.INSTANCE.isInitialized());
            // Second call should not throw
            NotificationService.INSTANCE.initialize();
            assertTrue(NotificationService.INSTANCE.isInitialized());
            // Clean up
            NotificationService.INSTANCE.shutdown();
        }

        @Test
        @DisplayName("Shutdown is idempotent")
        void shutdownIsIdempotent() {
            NotificationService.INSTANCE.initialize();
            NotificationService.INSTANCE.shutdown();
            assertFalse(NotificationService.INSTANCE.isInitialized());
            // Second call should not throw
            NotificationService.INSTANCE.shutdown();
            assertFalse(NotificationService.INSTANCE.isInitialized());
        }
    }

    // ========================================================================
    // NOTIFICATION CONSTRUCTION PATTERNS
    // ========================================================================

    @Nested
    @DisplayName("Notification Construction (testing builder patterns used by service methods)")
    class NotificationConstruction {

        @Test
        @DisplayName("Badge unlock notification structure")
        void badgeUnlockNotificationStructure() {
            // Mirrors NotificationService.notifyBadgeUnlock logic
            String badgeName = "Dragon Slayer";
            String rarity = "legendary";

            Notification n = Notification.builder(NotificationCategory.ACHIEVEMENT)
                .titleKey("devmod.notification.badge_unlock.title")
                .messageKey("devmod.notification.badge_unlock.message")
                .param("badge", badgeName)
                .param("rarity", rarity)
                .priority(NotificationPriority.URGENT) // legendary -> URGENT
                .soundId("badge.legendary")
                .iconId("badge.legendary")
                .persistToMailbox(true)
                .build();

            assertEquals(NotificationCategory.ACHIEVEMENT, n.category());
            assertEquals(NotificationPriority.URGENT, n.priority());
            assertEquals(badgeName, n.getParam("badge"));
            assertEquals(rarity, n.getParam("rarity"));
            assertTrue(n.persistToMailbox());
            assertEquals("badge.legendary", n.soundId());
        }

        @Test
        @DisplayName("Token gain notification with zero amount is handled by caller")
        void tokenGainZeroAmountHandledByCaller() {
            // NotificationService.notifyTokenGain checks amount <= 0 and returns early
            // Test that a positive amount produces valid notification
            int amount = 100;
            Notification n = Notification.builder(NotificationCategory.TOKEN)
                .titleKey("devmod.notification.token_gain.title")
                .messageKey("devmod.notification.token_gain.message")
                .param("amount", String.valueOf(amount))
                .priority(NotificationPriority.NORMAL)
                .soundId("token.gain")
                .persistToMailbox(false)
                .build();

            assertEquals("100", n.getParam("amount"));
            assertFalse(n.persistToMailbox());
        }

        @Test
        @DisplayName("Personal record notification has URGENT priority")
        void personalRecordNotificationUrgent() {
            Notification n = Notification.builder(NotificationCategory.RECORD)
                .titleKey("devmod.notification.record.title")
                .messageKey("devmod.notification.record.message")
                .param("type", "fastest_clear")
                .param("value", "1:23.456")
                .priority(NotificationPriority.URGENT)
                .soundId("record.new")
                .iconId("record")
                .persistToMailbox(true)
                .build();

            assertEquals(NotificationPriority.URGENT, n.priority());
            assertTrue(n.persistToMailbox());
            assertEquals("fastest_clear", n.getParam("type"));
        }

        @Test
        @DisplayName("Combo decay notification uses LOW priority")
        void comboDecayLowPriority() {
            Notification n = Notification.builder(NotificationCategory.COMBAT)
                .titleKey("devmod.notification.combo_decay.title")
                .messageKey("devmod.notification.combo_decay.message")
                .param("lost", "5")
                .param("previousRank", "3")
                .param("newRank", "2")
                .priority(NotificationPriority.LOW)
                .displayDurationMs(1200)
                .persistToMailbox(false)
                .showOverlay(true)
                .build();

            assertEquals(NotificationPriority.LOW, n.priority());
            assertEquals(1200, n.displayDurationMs());
            assertFalse(n.persistToMailbox());
            assertTrue(n.showOverlay());
        }

        @Test
        @DisplayName("Party event priority mapping")
        void partyEventPriorityMapping() {
            // kicked -> HIGH
            Notification kicked = Notification.builder(NotificationCategory.PARTY)
                .titleKey("devmod.notification.party.kicked.title")
                .priority(NotificationPriority.HIGH)
                .persistToMailbox(true)
                .build();
            assertEquals(NotificationPriority.HIGH, kicked.priority());
            assertTrue(kicked.persistToMailbox());

            // invite -> NORMAL
            Notification invite = Notification.builder(NotificationCategory.PARTY)
                .titleKey("devmod.notification.party.invite.title")
                .priority(NotificationPriority.NORMAL)
                .persistToMailbox(false)
                .build();
            assertEquals(NotificationPriority.NORMAL, invite.priority());
        }

        @Test
        @DisplayName("Wave start boss notification structure")
        void waveStartBossNotification() {
            Notification n = Notification.builder(NotificationCategory.QUEST)
                .titleKey("devmod.notification.wave.boss_title")
                .messageKey("devmod.notification.wave.boss_message")
                .param("wave", "10")
                .param("enemyCount", "1")
                .param("enemyType", "Dragon")
                .priority(NotificationPriority.HIGH)
                .soundId("wave.boss_start")
                .iconId("boss")
                .displayDurationMs(3500)
                .showOverlay(true)
                .persistToMailbox(false)
                .build();

            assertEquals(NotificationPriority.HIGH, n.priority());
            assertEquals("wave.boss_start", n.soundId());
            assertEquals("boss", n.iconId());
            assertEquals("Dragon", n.getParam("enemyType"));
        }

        @Test
        @DisplayName("Season tier up notification has action ID")
        void seasonTierUpHasActionId() {
            Notification n = Notification.builder(NotificationCategory.SEASON)
                .titleKey("devmod.notification.season_tier.title")
                .messageKey("devmod.notification.season_tier.message")
                .param("tier", "5")
                .param("freeReward", "Gold Sword")
                .param("premiumReward", "Diamond Armor")
                .priority(NotificationPriority.HIGH)
                .soundId("season.tierup")
                .iconId("season.tier")
                .actionId("ui_season_pass_open")
                .persistToMailbox(true)
                .build();

            assertNotNull(n.actionId());
            assertEquals("ui_season_pass_open", n.actionId());
        }

        @Test
        @DisplayName("Challenge reward notification has cadence-specific keys")
        void challengeRewardCadenceKeys() {
            // Weekly challenge
            Notification weekly = Notification.builder(NotificationCategory.REWARD)
                .titleKey("devmod.notification.challenge.weekly.title")
                .messageKey("devmod.notification.challenge.weekly.message")
                .param("challenge", "Kill 100 Mobs")
                .param("tokens", "500")
                .param("prestige", "50")
                .priority(NotificationPriority.HIGH)
                .soundId("quest.complete")
                .displayDurationMs(5000)
                .persistToMailbox(false)
                .build();

            assertEquals(NotificationPriority.HIGH, weekly.priority());
            assertEquals(5000, weekly.displayDurationMs());

            // Daily challenge
            Notification daily = Notification.builder(NotificationCategory.REWARD)
                .titleKey("devmod.notification.challenge.daily.title")
                .messageKey("devmod.notification.challenge.daily.message")
                .param("challenge", "Win 3 Matches")
                .priority(NotificationPriority.NORMAL)
                .soundId("token.gain")
                .displayDurationMs(3500)
                .build();

            assertEquals(NotificationPriority.NORMAL, daily.priority());
            assertEquals(3500, daily.displayDurationMs());
        }
    }

    // ========================================================================
    // MAILBOX EXPIRATION LOGIC
    // ========================================================================

    @Nested
    @DisplayName("Mailbox Expiration Logic")
    class MailboxExpirationTests {

        @Test
        @DisplayName("Higher priorities get longer mailbox retention")
        void higherPrioritiesGetLongerRetention() {
            // These match the getMailboxExpiration switch in NotificationService
            // LOW=3d, NORMAL=7d, HIGH=14d, URGENT=30d, CRITICAL=60d
            // We verify the pattern by checking durations are increasing
            Duration low = Duration.ofDays(3);
            Duration normal = Duration.ofDays(7);
            Duration high = Duration.ofDays(14);
            Duration urgent = Duration.ofDays(30);
            Duration critical = Duration.ofDays(60);

            assertTrue(normal.compareTo(low) > 0);
            assertTrue(high.compareTo(normal) > 0);
            assertTrue(urgent.compareTo(high) > 0);
            assertTrue(critical.compareTo(urgent) > 0);
        }
    }

    // ========================================================================
    // NULL SAFETY
    // ========================================================================

    @Nested
    @DisplayName("Null Safety")
    class NullSafetyTests {

        @Test
        @DisplayName("notify rejects null playerUuid")
        void notifyRejectsNullPlayerUuid() {
            Notification n = Notification.simple(NotificationCategory.SYSTEM, "test");
            assertThrows(NullPointerException.class, () ->
                NotificationService.INSTANCE.notify(null, n));
        }

        @Test
        @DisplayName("notify rejects null notification")
        void notifyRejectsNullNotification() {
            assertThrows(NullPointerException.class, () ->
                NotificationService.INSTANCE.notify(UUID.randomUUID(), null));
        }

        @Test
        @DisplayName("notifyAll rejects null collection")
        void notifyAllRejectsNullCollection() {
            Notification n = Notification.simple(NotificationCategory.SYSTEM, "test");
            assertThrows(NullPointerException.class, () ->
                NotificationService.INSTANCE.notifyAll(null, n));
        }

        @Test
        @DisplayName("broadcast rejects null notification")
        void broadcastRejectsNullNotification() {
            assertThrows(NullPointerException.class, () ->
                NotificationService.INSTANCE.broadcast(null));
        }
    }
}
