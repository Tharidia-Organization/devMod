package com.devmod.mailbox.webhook;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.devmod.mailbox.webhook.WebhookManager.WebhookConfig;
import com.devmod.mailbox.webhook.WebhookManager.WebhookEvent;
import com.devmod.mailbox.webhook.WebhookManager.WebhookFormat;
import com.devmod.mailbox.webhook.WebhookManager.WebhookPayload;
import com.devmod.mailbox.webhook.WebhookManager.WebhookStats;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for WebhookManager.
 */
@DisplayName("WebhookManager Tests")
class WebhookManagerTest {

    @BeforeEach
    void setUp() {
        WebhookManager.INSTANCE.start();
    }

    @AfterEach
    void tearDown() {
        // Unregister all webhooks
        for (WebhookConfig config : WebhookManager.INSTANCE.getWebhooks()) {
            WebhookManager.INSTANCE.unregisterWebhook(config.id());
        }
        WebhookManager.INSTANCE.stop();
    }

    @Nested
    @DisplayName("Webhook Registration")
    class WebhookRegistrationTests {

        @Test
        @DisplayName("Register webhook with basic params")
        void registerWebhookWithBasicParams() {
            Set<WebhookEvent> events = EnumSet.of(
                WebhookEvent.MESSAGE_SENT,
                WebhookEvent.BROADCAST_SENT
            );

            UUID id = WebhookManager.INSTANCE.registerWebhook(
                "Test Webhook",
                "https://example.com/webhook",
                events
            );

            assertNotNull(id);
            WebhookConfig config = WebhookManager.INSTANCE.getWebhook(id);
            assertNotNull(config);
            assertEquals("Test Webhook", config.name());
        }

        @Test
        @DisplayName("Register Discord webhook")
        void registerDiscordWebhook() {
            Set<WebhookEvent> events = EnumSet.allOf(WebhookEvent.class);

            UUID id = WebhookManager.INSTANCE.registerDiscordWebhook(
                "Discord Notifications",
                "https://discord.com/api/webhooks/123/abc",
                events
            );

            assertNotNull(id);
            WebhookConfig config = WebhookManager.INSTANCE.getWebhook(id);
            assertNotNull(config);
            assertEquals(WebhookFormat.DISCORD, config.format());
        }

        @Test
        @DisplayName("Register Slack webhook")
        void registerSlackWebhook() {
            Set<WebhookEvent> events = Set.of(WebhookEvent.NEWS_PUBLISHED);

            UUID id = WebhookManager.INSTANCE.registerSlackWebhook(
                "Slack News",
                "https://hooks.slack.com/services/T00/B00/XXX",
                events
            );

            assertNotNull(id);
            WebhookConfig config = WebhookManager.INSTANCE.getWebhook(id);
            assertNotNull(config);
            assertEquals(WebhookFormat.SLACK, config.format());
        }

        @Test
        @DisplayName("Register full WebhookConfig")
        void registerFullWebhookConfig() {
            WebhookConfig config = new WebhookConfig(
                UUID.randomUUID(),
                "Full Config Webhook",
                "https://api.example.com/hooks",
                EnumSet.allOf(WebhookEvent.class),
                WebhookFormat.JSON,
                "secret123",
                true,
                5,
                Instant.now()
            );

            UUID id = WebhookManager.INSTANCE.registerWebhook(config);
            assertEquals(config.id(), id);
        }
    }

    @Nested
    @DisplayName("Webhook Management")
    class WebhookManagementTests {

        @Test
        @DisplayName("Unregister webhook")
        void unregisterWebhook() {
            UUID id = WebhookManager.INSTANCE.registerWebhook(
                "To Remove",
                "https://example.com",
                Set.of(WebhookEvent.MESSAGE_SENT)
            );

            boolean removed = WebhookManager.INSTANCE.unregisterWebhook(id);
            assertTrue(removed);
            assertNull(WebhookManager.INSTANCE.getWebhook(id));
        }

        @Test
        @DisplayName("Unregister non-existent webhook returns false")
        void unregisterNonExistentReturnsFalse() {
            boolean removed = WebhookManager.INSTANCE.unregisterWebhook(UUID.randomUUID());
            assertFalse(removed);
        }

        @Test
        @DisplayName("Enable and disable webhook")
        void enableAndDisableWebhook() {
            UUID id = WebhookManager.INSTANCE.registerWebhook(
                "Toggle Test",
                "https://example.com",
                Set.of(WebhookEvent.MESSAGE_SENT)
            );

            assertTrue(requireWebhook(id).enabled());

            boolean disabled = WebhookManager.INSTANCE.setWebhookEnabled(id, false);
            assertTrue(disabled);
            assertFalse(requireWebhook(id).enabled());

            boolean enabled = WebhookManager.INSTANCE.setWebhookEnabled(id, true);
            assertTrue(enabled);
            assertTrue(requireWebhook(id).enabled());
        }

        @Test
        @DisplayName("Update webhook configuration")
        void updateWebhookConfiguration() {
            UUID id = WebhookManager.INSTANCE.registerWebhook(
                "Original Name",
                "https://example.com",
                Set.of(WebhookEvent.MESSAGE_SENT)
            );

            WebhookConfig original = requireWebhook(id);
            WebhookConfig updated = new WebhookConfig(
                id,
                "Updated Name",
                original.url(),
                EnumSet.allOf(WebhookEvent.class),
                original.format(),
                original.secret(),
                original.enabled(),
                original.maxRetries(),
                original.createdAt()
            );

            boolean success = WebhookManager.INSTANCE.updateWebhook(updated);
            assertTrue(success);
            assertEquals("Updated Name", requireWebhook(id).name());
        }

        @Test
        @DisplayName("Get all webhooks")
        void getAllWebhooks() {
            WebhookManager.INSTANCE.registerWebhook(
                "Webhook 1", "https://example1.com", Set.of(WebhookEvent.MESSAGE_SENT)
            );
            WebhookManager.INSTANCE.registerWebhook(
                "Webhook 2", "https://example2.com", Set.of(WebhookEvent.NEWS_PUBLISHED)
            );

            var webhooks = WebhookManager.INSTANCE.getWebhooks();
            assertEquals(2, webhooks.size());
        }
    }

    @Nested
    @DisplayName("WebhookConfig")
    class WebhookConfigTests {

        @Test
        @DisplayName("Create config for all events")
        void createConfigForAllEvents() {
            WebhookConfig config = WebhookConfig.forAllEvents(
                "All Events",
                "https://example.com",
                WebhookFormat.JSON
            );

            assertNotNull(config.id());
            assertEquals(WebhookEvent.values().length, config.events().size());
            assertTrue(config.enabled());
        }
    }

    @Nested
    @DisplayName("Webhook Events")
    class WebhookEventsTests {

        @Test
        @DisplayName("All webhook events")
        void allWebhookEvents() {
            assertEquals(6, WebhookEvent.values().length);
            assertNotNull(WebhookEvent.MESSAGE_SENT);
            assertNotNull(WebhookEvent.MESSAGE_READ);
            assertNotNull(WebhookEvent.BROADCAST_SENT);
            assertNotNull(WebhookEvent.ATTACHMENT_CLAIMED);
            assertNotNull(WebhookEvent.NEWS_PUBLISHED);
            assertNotNull(WebhookEvent.ANOMALY_DETECTED);
        }
    }

    @Nested
    @DisplayName("Webhook Formats")
    class WebhookFormatsTests {

        @Test
        @DisplayName("All webhook formats")
        void allWebhookFormats() {
            assertEquals(3, WebhookFormat.values().length);
            assertNotNull(WebhookFormat.JSON);
            assertNotNull(WebhookFormat.DISCORD);
            assertNotNull(WebhookFormat.SLACK);
        }
    }

    @Nested
    @DisplayName("WebhookPayload")
    class WebhookPayloadTests {

        @Test
        @DisplayName("Create payload")
        void createPayload() {
            WebhookPayload payload = new WebhookPayload(
                WebhookEvent.MESSAGE_SENT,
                Instant.now(),
                Map.of(
                    "message_id", UUID.randomUUID().toString(),
                    "subject", "Test Subject"
                )
            );

            assertEquals(WebhookEvent.MESSAGE_SENT, payload.event());
            assertNotNull(payload.timestamp());
            assertEquals(2, payload.data().size());
        }
    }

    @Nested
    @DisplayName("Statistics")
    class StatisticsTests {

        @Test
        @DisplayName("Get webhook stats")
        void getWebhookStats() {
            WebhookManager.INSTANCE.registerWebhook(
                "Stats Test", "https://example.com", Set.of(WebhookEvent.MESSAGE_SENT)
            );

            WebhookStats stats = WebhookManager.INSTANCE.getStats();

            assertNotNull(stats);
            assertEquals(1, stats.totalWebhooks());
            assertEquals(1, stats.enabledWebhooks());
            assertTrue(stats.running());
        }

        @Test
        @DisplayName("Stats reflect disabled webhooks")
        void statsReflectDisabledWebhooks() {
            WebhookManager.INSTANCE.registerWebhook(
                "Enabled", "https://example1.com", Set.of(WebhookEvent.MESSAGE_SENT)
            );
            UUID id2 = WebhookManager.INSTANCE.registerWebhook(
                "Disabled", "https://example2.com", Set.of(WebhookEvent.MESSAGE_SENT)
            );

            WebhookManager.INSTANCE.setWebhookEnabled(id2, false);

            WebhookStats stats = WebhookManager.INSTANCE.getStats();
            assertEquals(2, stats.totalWebhooks());
            assertEquals(1, stats.enabledWebhooks());
        }
    }

    @Nested
    @DisplayName("Lifecycle")
    class LifecycleTests {

        @Test
        @DisplayName("Start and stop manager")
        void startAndStopManager() {
            WebhookManager.INSTANCE.stop();

            WebhookStats stats = WebhookManager.INSTANCE.getStats();
            assertFalse(stats.running());

            WebhookManager.INSTANCE.start();
            stats = WebhookManager.INSTANCE.getStats();
            assertTrue(stats.running());
        }

        @Test
        @DisplayName("Double start is safe")
        void doubleStartIsSafe() {
            WebhookManager.INSTANCE.start();
            WebhookStats stats = WebhookManager.INSTANCE.getStats();
            assertTrue(stats.running());
        }

        @Test
        @DisplayName("Double stop is safe")
        void doubleStopIsSafe() {
            WebhookManager.INSTANCE.stop();
            WebhookManager.INSTANCE.stop();
            // Should not throw
        }
    }

    private static WebhookConfig requireWebhook(UUID id) {
        WebhookConfig config = WebhookManager.INSTANCE.getWebhook(id);
        assertNotNull(config, "Expected webhook to be registered for id " + id);
        return config;
    }
}
