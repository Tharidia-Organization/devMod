package com.devmod.notification;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.devmod.notification.NotificationRouter.NotificationPreferences;
import com.devmod.notification.NotificationRouter.NotificationPreferences.CategoryPreference;
import com.devmod.notification.NotificationRouter.RoutingDecision;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for NotificationRouter routing logic and NotificationPreferences.
 *
 * NOTE: The route() method calls isPlayerOnline() which depends on MinecraftServer.
 * Since that always returns false in test, we test routing through the offline path
 * and test the preferences system thoroughly as a pure unit.
 */
class NotificationRouterTest {

    private NotificationRouter router;

    @BeforeEach
    void setUp() {
        router = new NotificationRouter();
    }

    private Notification buildNotification(NotificationCategory cat, NotificationPriority priority,
                                            boolean showOverlay, boolean persistToMailbox) {
        return new Notification(
            UUID.randomUUID(), cat, priority, "test.title", "test.msg",
            Map.of(), null, null, null, null, Instant.now(), null,
            persistToMailbox, showOverlay, priority.getDefaultDurationMs()
        );
    }

    // ========================================================================
    // ROUTING DECISION
    // ========================================================================

    @Nested
    @DisplayName("RoutingDecision")
    class RoutingDecisionTests {

        @Test
        @DisplayName("hasAnyDelivery returns true when overlay is set")
        void hasAnyDeliveryOverlay() {
            assertTrue(new RoutingDecision(true, false, false).hasAnyDelivery());
        }

        @Test
        @DisplayName("hasAnyDelivery returns true when mailbox is set")
        void hasAnyDeliveryMailbox() {
            assertTrue(new RoutingDecision(false, true, false).hasAnyDelivery());
        }

        @Test
        @DisplayName("hasAnyDelivery returns true when chat is set")
        void hasAnyDeliveryChat() {
            assertTrue(new RoutingDecision(false, false, true).hasAnyDelivery());
        }

        @Test
        @DisplayName("hasAnyDelivery returns false when all are false")
        void hasAnyDeliveryAllFalse() {
            assertFalse(new RoutingDecision(false, false, false).hasAnyDelivery());
        }
    }

    // ========================================================================
    // ROUTING LOGIC (offline player path)
    // ========================================================================

    @Nested
    @DisplayName("Routing Logic (offline player)")
    class RoutingLogicOffline {

        @Test
        @DisplayName("Global mute blocks overlay and chat but allows mailbox")
        void globalMuteBlocksOverlayAndChat() {
            UUID player = UUID.randomUUID();
            NotificationPreferences prefs = new NotificationPreferences(player);
            prefs.setGlobalMute(true);
            router.updatePreferences(player, prefs);

            Notification n = buildNotification(NotificationCategory.PARTY,
                NotificationPriority.NORMAL, true, true);
            RoutingDecision decision = router.route(player, n);

            assertFalse(decision.sendOverlay());
            assertFalse(decision.sendChat());
            // Mailbox should still be allowed if notification requests it
            assertTrue(decision.sendMailbox());
        }

        @Test
        @DisplayName("Muted category blocks overlay, preserves mailbox if allowed")
        void mutedCategoryBlocksOverlay() {
            UUID player = UUID.randomUUID();
            NotificationPreferences prefs = new NotificationPreferences(player);
            prefs.setCategoryOverlayEnabled(NotificationCategory.PARTY, false);
            router.updatePreferences(player, prefs);

            // PARTY has defaultMailboxEnabled=true
            Notification n = buildNotification(NotificationCategory.PARTY,
                NotificationPriority.NORMAL, true, true);
            RoutingDecision decision = router.route(player, n);

            assertFalse(decision.sendOverlay());
            assertTrue(decision.sendMailbox());
        }

        @Test
        @DisplayName("Muted category with mailbox-disabled category blocks mailbox too")
        void mutedCategoryWithMailboxDisabledBlocksMailbox() {
            UUID player = UUID.randomUUID();
            NotificationPreferences prefs = new NotificationPreferences(player);
            // COMBAT: defaultMailboxEnabled=false
            prefs.setCategoryOverlayEnabled(NotificationCategory.COMBAT, false);
            router.updatePreferences(player, prefs);

            Notification n = buildNotification(NotificationCategory.COMBAT,
                NotificationPriority.NORMAL, true, true);
            RoutingDecision decision = router.route(player, n);

            assertFalse(decision.sendOverlay());
            assertFalse(decision.sendMailbox());
        }

        @Test
        @DisplayName("Offline player with HIGH+ priority forces mailbox delivery")
        void offlineHighPriorityForcesMailbox() {
            UUID player = UUID.randomUUID();

            Notification n = buildNotification(NotificationCategory.PARTY,
                NotificationPriority.HIGH, true, false);
            RoutingDecision decision = router.route(player, n);

            // Player is offline, HIGH priority, so mailbox should be forced
            assertTrue(decision.sendMailbox());
        }

        @Test
        @DisplayName("Offline player with NORMAL priority does not force mailbox")
        void offlineNormalPriorityDoesNotForceMailbox() {
            UUID player = UUID.randomUUID();

            // COMBAT category has mailbox disabled by default
            Notification n = buildNotification(NotificationCategory.COMBAT,
                NotificationPriority.NORMAL, true, false);
            RoutingDecision decision = router.route(player, n);

            // No forced mailbox for offline NORMAL priority with persistToMailbox=false
            assertFalse(decision.sendMailbox());
        }

        @Test
        @DisplayName("Offline player never gets overlay")
        void offlinePlayerNeverGetsOverlay() {
            UUID player = UUID.randomUUID();

            Notification n = buildNotification(NotificationCategory.ADMIN,
                NotificationPriority.CRITICAL, true, true);
            RoutingDecision decision = router.route(player, n);

            assertFalse(decision.sendOverlay());
        }

        @Test
        @DisplayName("Offline player never gets chat")
        void offlinePlayerNeverGetsChat() {
            UUID player = UUID.randomUUID();
            NotificationPreferences prefs = new NotificationPreferences(player);
            prefs.setPreferChatOverOverlay(true);
            router.updatePreferences(player, prefs);

            Notification n = buildNotification(NotificationCategory.SYSTEM,
                NotificationPriority.CRITICAL, true, false);
            RoutingDecision decision = router.route(player, n);

            assertFalse(decision.sendChat());
        }
    }

    // ========================================================================
    // PREFERENCES CACHE
    // ========================================================================

    @Nested
    @DisplayName("Preferences Cache")
    class PreferencesCacheTests {

        @Test
        @DisplayName("getPreferences returns default for unknown player")
        void getPreferencesReturnsDefaultForUnknown() {
            UUID player = UUID.randomUUID();
            NotificationPreferences prefs = router.getPreferences(player);

            assertNotNull(prefs);
            assertEquals(player, prefs.getPlayerUuid());
            assertFalse(prefs.isGlobalMute());
        }

        @Test
        @DisplayName("updatePreferences caches new preferences")
        void updatePreferencesCaches() {
            UUID player = UUID.randomUUID();
            NotificationPreferences prefs = new NotificationPreferences(player);
            prefs.setGlobalMute(true);
            router.updatePreferences(player, prefs);

            NotificationPreferences retrieved = router.getPreferences(player);
            assertTrue(retrieved.isGlobalMute());
        }

        @Test
        @DisplayName("clearPreferences removes cached preferences")
        void clearPreferencesRemovesCached() {
            UUID player = UUID.randomUUID();
            NotificationPreferences prefs = new NotificationPreferences(player);
            prefs.setGlobalMute(true);
            router.updatePreferences(player, prefs);
            router.clearPreferences(player);

            NotificationPreferences retrieved = router.getPreferences(player);
            // Should get fresh defaults since cache was cleared
            assertFalse(retrieved.isGlobalMute());
        }
    }

    // ========================================================================
    // NOTIFICATION PREFERENCES
    // ========================================================================

    @Nested
    @DisplayName("NotificationPreferences")
    class PreferencesTests {

        private UUID playerUuid;
        private NotificationPreferences prefs;

        @BeforeEach
        void setUp() {
            playerUuid = UUID.randomUUID();
            prefs = new NotificationPreferences(playerUuid);
        }

        @Test
        @DisplayName("Default preferences are sensible")
        void defaultPreferencesAreSensible() {
            assertEquals(playerUuid, prefs.getPlayerUuid());
            assertFalse(prefs.isGlobalMute());
            assertEquals(NotificationPriority.LOW, prefs.getMinOverlayPriority());
            assertFalse(prefs.prefersChatOverOverlay());
            assertEquals(1.0f, prefs.getMasterVolume(), 0.001f);
        }

        @Test
        @DisplayName("Category preferences are initialized from category defaults")
        void categoryPrefsInitializedFromDefaults() {
            // PARTY has overlay enabled
            assertFalse(prefs.isCategoryMuted(NotificationCategory.PARTY));
            // COMBAT has overlay disabled
            assertTrue(prefs.isCategoryMuted(NotificationCategory.COMBAT));
        }

        @Test
        @DisplayName("setGlobalMute toggles mute state")
        void setGlobalMuteTogglesMuteState() {
            prefs.setGlobalMute(true);
            assertTrue(prefs.isGlobalMute());
            prefs.setGlobalMute(false);
            assertFalse(prefs.isGlobalMute());
        }

        @Test
        @DisplayName("setMinOverlayPriority changes threshold")
        void setMinOverlayPriorityChangesThreshold() {
            prefs.setMinOverlayPriority(NotificationPriority.HIGH);
            assertEquals(NotificationPriority.HIGH, prefs.getMinOverlayPriority());
        }

        @Test
        @DisplayName("setMasterVolume clamps to [0, 1]")
        void setMasterVolumeClamps() {
            prefs.setMasterVolume(1.5f);
            assertEquals(1.0f, prefs.getMasterVolume(), 0.001f);

            prefs.setMasterVolume(-0.5f);
            assertEquals(0.0f, prefs.getMasterVolume(), 0.001f);

            prefs.setMasterVolume(0.75f);
            assertEquals(0.75f, prefs.getMasterVolume(), 0.001f);
        }

        @Test
        @DisplayName("setCategoryOverlayEnabled changes mute state")
        void setCategoryOverlayEnabledChangesMuteState() {
            prefs.setCategoryOverlayEnabled(NotificationCategory.PARTY, false);
            assertTrue(prefs.isCategoryMuted(NotificationCategory.PARTY));

            prefs.setCategoryOverlayEnabled(NotificationCategory.PARTY, true);
            assertFalse(prefs.isCategoryMuted(NotificationCategory.PARTY));
        }

        @Test
        @DisplayName("isSoundEnabled respects global mute")
        void isSoundEnabledRespectsGlobalMute() {
            assertTrue(prefs.isSoundEnabled(NotificationCategory.PARTY));

            prefs.setGlobalMute(true);
            assertFalse(prefs.isSoundEnabled(NotificationCategory.PARTY));
        }

        @Test
        @DisplayName("isSoundEnabled respects category setting")
        void isSoundEnabledRespectsCategorySetting() {
            assertTrue(prefs.isSoundEnabled(NotificationCategory.PARTY));

            prefs.setCategorySoundEnabled(NotificationCategory.PARTY, false);
            assertFalse(prefs.isSoundEnabled(NotificationCategory.PARTY));
        }

        @Test
        @DisplayName("getCategoryVolume combines master and category volume")
        void getCategoryVolumeCombinesMasterAndCategory() {
            prefs.setMasterVolume(0.5f);
            prefs.setCategorySoundVolume(NotificationCategory.PARTY, 0.8f);

            float volume = prefs.getCategoryVolume(NotificationCategory.PARTY);
            assertEquals(0.4f, volume, 0.001f);
        }

        @Test
        @DisplayName("getCategoryVolume returns 0 when globally muted")
        void getCategoryVolumeReturnsZeroWhenGloballyMuted() {
            prefs.setGlobalMute(true);
            assertEquals(0f, prefs.getCategoryVolume(NotificationCategory.PARTY), 0.001f);
        }

        @Test
        @DisplayName("setCategorySoundVolume clamps to [0, 1]")
        void setCategorySoundVolumeClamps() {
            prefs.setCategorySoundVolume(NotificationCategory.PARTY, 2.0f);
            CategoryPreference pref = prefs.getCategoryPreference(NotificationCategory.PARTY);
            assertNotNull(pref);
            assertTrue(pref.soundVolume() <= 1.0f);

            prefs.setCategorySoundVolume(NotificationCategory.PARTY, -1.0f);
            pref = prefs.getCategoryPreference(NotificationCategory.PARTY);
            assertNotNull(pref);
            assertTrue(pref.soundVolume() >= 0.0f);
        }

        @Test
        @DisplayName("getCategoryPreference returns null for unknown category")
        void getCategoryPreferenceInitializedForAllCategories() {
            // All categories should be initialized
            for (NotificationCategory cat : NotificationCategory.values()) {
                assertNotNull(prefs.getCategoryPreference(cat),
                    "Category " + cat.name() + " preference should be initialized");
            }
        }

        @Test
        @DisplayName("setCategoryOverlayEnabled preserves other category settings")
        void setCategoryOverlayPreservesOtherSettings() {
            prefs.setCategorySoundEnabled(NotificationCategory.PARTY, false);
            prefs.setCategorySoundVolume(NotificationCategory.PARTY, 0.3f);

            // Toggle overlay
            prefs.setCategoryOverlayEnabled(NotificationCategory.PARTY, false);

            CategoryPreference pref = prefs.getCategoryPreference(NotificationCategory.PARTY);
            assertNotNull(pref);
            assertFalse(pref.overlayEnabled());
            assertFalse(pref.soundEnabled());
            assertEquals(0.3f, pref.soundVolume(), 0.001f);
        }

        @Test
        @DisplayName("setCategorySoundEnabled preserves other category settings")
        void setCategorySoundPreservesOtherSettings() {
            prefs.setCategoryOverlayEnabled(NotificationCategory.PARTY, false);
            prefs.setCategorySoundVolume(NotificationCategory.PARTY, 0.7f);

            prefs.setCategorySoundEnabled(NotificationCategory.PARTY, false);

            CategoryPreference pref = prefs.getCategoryPreference(NotificationCategory.PARTY);
            assertNotNull(pref);
            assertFalse(pref.overlayEnabled());
            assertFalse(pref.soundEnabled());
            assertEquals(0.7f, pref.soundVolume(), 0.001f);
        }
    }
}
