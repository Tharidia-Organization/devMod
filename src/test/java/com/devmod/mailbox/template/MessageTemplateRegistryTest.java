package com.devmod.mailbox.template;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.devmod.mailbox.MessageType;
import com.devmod.mailbox.template.MessageTemplateRegistry.MessageSound;
import com.devmod.mailbox.template.MessageTemplateRegistry.TemplateCategory;
import com.devmod.mailbox.template.MessageTemplateRegistry.TemplatePreview;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for MessageTemplateRegistry.
 */
@DisplayName("MessageTemplateRegistry Tests")
class MessageTemplateRegistryTest {

    @Nested
    @DisplayName("Standard Templates")
    class StandardTemplatesTests {

        @Test
        @DisplayName("Welcome templates exist")
        void welcomeTemplatesExist() {
            Optional<MessageTemplate> newPlayer = MessageTemplateRegistry.INSTANCE.get("welcome.new_player");
            assertTrue(newPlayer.isPresent());
            assertEquals(TemplateCategory.WELCOME, newPlayer.get().category());

            Optional<MessageTemplate> returningPlayer = MessageTemplateRegistry.INSTANCE.get("welcome.returning_player");
            assertTrue(returningPlayer.isPresent());
        }

        @Test
        @DisplayName("Reward templates exist")
        void rewardTemplatesExist() {
            Optional<MessageTemplate> dailyLogin = MessageTemplateRegistry.INSTANCE.get("reward.daily_login");
            assertTrue(dailyLogin.isPresent());
            assertEquals(TemplateCategory.REWARD, dailyLogin.get().category());

            Optional<MessageTemplate> achievement = MessageTemplateRegistry.INSTANCE.get("reward.achievement");
            assertTrue(achievement.isPresent());

            Optional<MessageTemplate> eventParticipation = MessageTemplateRegistry.INSTANCE.get("reward.event_participation");
            assertTrue(eventParticipation.isPresent());
        }

        @Test
        @DisplayName("Event templates exist")
        void eventTemplatesExist() {
            Optional<MessageTemplate> announcement = MessageTemplateRegistry.INSTANCE.get("event.announcement");
            assertTrue(announcement.isPresent());
            assertEquals(TemplateCategory.EVENT, announcement.get().category());

            Optional<MessageTemplate> reminder = MessageTemplateRegistry.INSTANCE.get("event.reminder");
            assertTrue(reminder.isPresent());

            Optional<MessageTemplate> ended = MessageTemplateRegistry.INSTANCE.get("event.ended");
            assertTrue(ended.isPresent());
        }

        @Test
        @DisplayName("Admin templates exist")
        void adminTemplatesExist() {
            Optional<MessageTemplate> warning = MessageTemplateRegistry.INSTANCE.get("admin.warning");
            assertTrue(warning.isPresent());
            assertEquals(TemplateCategory.ADMIN, warning.get().category());

            Optional<MessageTemplate> maintenance = MessageTemplateRegistry.INSTANCE.get("admin.maintenance");
            assertTrue(maintenance.isPresent());

            Optional<MessageTemplate> broadcast = MessageTemplateRegistry.INSTANCE.get("admin.broadcast");
            assertTrue(broadcast.isPresent());
        }

        @Test
        @DisplayName("Social templates exist")
        void socialTemplatesExist() {
            Optional<MessageTemplate> friendRequest = MessageTemplateRegistry.INSTANCE.get("social.friend_request");
            assertTrue(friendRequest.isPresent());
            assertEquals(TemplateCategory.SOCIAL, friendRequest.get().category());

            Optional<MessageTemplate> partyInvite = MessageTemplateRegistry.INSTANCE.get("social.party_invite");
            assertTrue(partyInvite.isPresent());

            Optional<MessageTemplate> guildInvite = MessageTemplateRegistry.INSTANCE.get("social.guild_invite");
            assertTrue(guildInvite.isPresent());
        }

        @Test
        @DisplayName("Transaction templates exist")
        void transactionTemplatesExist() {
            Optional<MessageTemplate> purchaseConfirm = MessageTemplateRegistry.INSTANCE.get("transaction.purchase_confirm");
            assertTrue(purchaseConfirm.isPresent());
            assertEquals(TemplateCategory.TRANSACTION, purchaseConfirm.get().category());

            Optional<MessageTemplate> receivedPayment = MessageTemplateRegistry.INSTANCE.get("transaction.received_payment");
            assertTrue(receivedPayment.isPresent());
        }

        @Test
        @DisplayName("Moderation templates exist")
        void moderationTemplatesExist() {
            Optional<MessageTemplate> reportReceived = MessageTemplateRegistry.INSTANCE.get("moderation.report_received");
            assertTrue(reportReceived.isPresent());
            assertEquals(TemplateCategory.MODERATION, reportReceived.get().category());

            Optional<MessageTemplate> reportResolved = MessageTemplateRegistry.INSTANCE.get("moderation.report_resolved");
            assertTrue(reportResolved.isPresent());
        }
    }

    @Nested
    @DisplayName("Template Retrieval")
    class TemplateRetrievalTests {

        @Test
        @DisplayName("Get all templates")
        void getAllTemplates() {
            List<MessageTemplate> templates = MessageTemplateRegistry.INSTANCE.getAll();
            assertFalse(templates.isEmpty());
            assertTrue(templates.size() >= 17); // At least 17 standard templates
        }

        @Test
        @DisplayName("Get templates by category")
        void getTemplatesByCategory() {
            List<MessageTemplate> welcomeTemplates = MessageTemplateRegistry.INSTANCE
                .getByCategory(TemplateCategory.WELCOME);
            assertFalse(welcomeTemplates.isEmpty());
            for (MessageTemplate t : welcomeTemplates) {
                assertEquals(TemplateCategory.WELCOME, t.category());
            }
        }

        @Test
        @DisplayName("Check template exists")
        void checkTemplateExists() {
            assertTrue(MessageTemplateRegistry.INSTANCE.exists("welcome.new_player"));
            assertFalse(MessageTemplateRegistry.INSTANCE.exists("nonexistent.template"));
        }

        @Test
        @DisplayName("Get non-existent template returns empty")
        void getNonExistentTemplateReturnsEmpty() {
            Optional<MessageTemplate> template = MessageTemplateRegistry.INSTANCE.get("does.not.exist");
            assertTrue(template.isEmpty());
        }
    }

    @Nested
    @DisplayName("Placeholder Substitution")
    class PlaceholderSubstitutionTests {

        @Test
        @DisplayName("Substitute single placeholder")
        void substituteSinglePlaceholder() {
            String result = MessageTemplateRegistry.INSTANCE.substitutePlaceholders(
                "Hello {name}!",
                Map.of("name", "Steve")
            );
            assertEquals("Hello Steve!", result);
        }

        @Test
        @DisplayName("Substitute multiple placeholders")
        void substituteMultiplePlaceholders() {
            String result = MessageTemplateRegistry.INSTANCE.substitutePlaceholders(
                "Welcome {player_name} to {server_name}!",
                Map.of(
                    "player_name", "Alex",
                    "server_name", "MyServer"
                )
            );
            assertEquals("Welcome Alex to MyServer!", result);
        }

        @Test
        @DisplayName("Missing placeholder is preserved")
        void missingPlaceholderIsPreserved() {
            String result = MessageTemplateRegistry.INSTANCE.substitutePlaceholders(
                "Hello {name}, your balance is {balance}",
                Map.of("name", "Steve")
            );
            assertEquals("Hello Steve, your balance is {balance}", result);
        }

        @Test
        @DisplayName("Empty placeholders map")
        void emptyPlaceholdersMap() {
            String text = "Hello {name}!";
            String result = MessageTemplateRegistry.INSTANCE.substitutePlaceholders(text, Map.of());
            assertEquals(text, result);
        }

        @Test
        @DisplayName("Null text returns null")
        void nullTextReturnsNull() {
            String result = MessageTemplateRegistry.INSTANCE.substitutePlaceholders(null, Map.of("key", "value"));
            assertNull(result);
        }
    }

    @Nested
    @DisplayName("Extract Placeholders")
    class ExtractPlaceholdersTests {

        @Test
        @DisplayName("Extract single placeholder")
        void extractSinglePlaceholder() {
            List<String> placeholders = MessageTemplateRegistry.extractPlaceholders("Hello {name}!");
            assertEquals(1, placeholders.size());
            assertTrue(placeholders.contains("name"));
        }

        @Test
        @DisplayName("Extract multiple placeholders")
        void extractMultiplePlaceholders() {
            List<String> placeholders = MessageTemplateRegistry.extractPlaceholders(
                "{greeting} {name}, welcome to {server_name}!"
            );
            assertEquals(3, placeholders.size());
            assertTrue(placeholders.contains("greeting"));
            assertTrue(placeholders.contains("name"));
            assertTrue(placeholders.contains("server_name"));
        }

        @Test
        @DisplayName("No duplicates in extracted placeholders")
        void noDuplicatesInExtractedPlaceholders() {
            List<String> placeholders = MessageTemplateRegistry.extractPlaceholders(
                "Hello {name}, your name is {name}"
            );
            assertEquals(1, placeholders.size());
        }

        @Test
        @DisplayName("Empty text returns empty list")
        void emptyTextReturnsEmptyList() {
            List<String> placeholders = MessageTemplateRegistry.extractPlaceholders("");
            assertTrue(placeholders.isEmpty());
        }

        @Test
        @DisplayName("Null text returns empty list")
        void nullTextReturnsEmptyList() {
            List<String> placeholders = MessageTemplateRegistry.extractPlaceholders(null);
            assertTrue(placeholders.isEmpty());
        }
    }

    @Nested
    @DisplayName("Template Preview")
    class TemplatePreviewTests {

        @Test
        @DisplayName("Preview template with placeholders")
        void previewTemplateWithPlaceholders() {
            Optional<TemplatePreview> preview = MessageTemplateRegistry.INSTANCE.preview(
                "welcome.new_player",
                Map.of(
                    "server_name", "TestServer",
                    "player_name", "Alex",
                    "discord_link", "discord.gg/test"
                )
            );

            assertTrue(preview.isPresent());
            assertTrue(preview.get().subject().contains("TestServer"));
            assertTrue(preview.get().body().contains("Alex"));
        }

        @Test
        @DisplayName("Preview non-existent template returns empty")
        void previewNonExistentTemplateReturnsEmpty() {
            Optional<TemplatePreview> preview = MessageTemplateRegistry.INSTANCE.preview(
                "nonexistent",
                Map.of()
            );
            assertTrue(preview.isEmpty());
        }
    }

    @Nested
    @DisplayName("Custom Template Registration")
    class CustomTemplateRegistrationTests {

        @Test
        @DisplayName("Register custom template")
        void registerCustomTemplate() {
            String customId = "custom.test_" + System.currentTimeMillis();

            MessageTemplate custom = MessageTemplate.builder(customId)
                .category(TemplateCategory.CUSTOM)
                .subject("Custom Subject")
                .body("Custom body with {placeholder}")
                .type(MessageType.SYSTEM)
                .build();

            boolean registered = MessageTemplateRegistry.INSTANCE.register(custom);
            assertTrue(registered);
            assertTrue(MessageTemplateRegistry.INSTANCE.exists(customId));

            // Cleanup
            MessageTemplateRegistry.INSTANCE.unregister(customId);
        }

        @Test
        @DisplayName("Cannot register duplicate ID")
        void cannotRegisterDuplicateId() {
            // Try to register existing template
            MessageTemplate duplicate = MessageTemplate.builder("welcome.new_player")
                .category(TemplateCategory.CUSTOM)
                .subject("Duplicate")
                .body("Body")
                .build();

            boolean registered = MessageTemplateRegistry.INSTANCE.register(duplicate);
            assertFalse(registered);
        }

        @Test
        @DisplayName("Unregister custom template")
        void unregisterCustomTemplate() {
            String customId = "custom.to_remove_" + System.currentTimeMillis();

            MessageTemplate custom = MessageTemplate.builder(customId)
                .category(TemplateCategory.CUSTOM)
                .subject("To Remove")
                .body("Body")
                .build();

            MessageTemplateRegistry.INSTANCE.register(custom);
            assertTrue(MessageTemplateRegistry.INSTANCE.exists(customId));

            boolean unregistered = MessageTemplateRegistry.INSTANCE.unregister(customId);
            assertTrue(unregistered);
            assertFalse(MessageTemplateRegistry.INSTANCE.exists(customId));
        }

        @Test
        @DisplayName("Unregister non-existent returns false")
        void unregisterNonExistentReturnsFalse() {
            boolean unregistered = MessageTemplateRegistry.INSTANCE.unregister("does.not.exist");
            assertFalse(unregistered);
        }
    }

    @Nested
    @DisplayName("MessageTemplate Record")
    class MessageTemplateRecordTests {

        @Test
        @DisplayName("Build template with all options")
        void buildTemplateWithAllOptions() {
            MessageTemplate template = MessageTemplate.builder("test.full")
                .category(TemplateCategory.REWARD)
                .subject("Full Template {var}")
                .body("Body with {another_var}")
                .type(MessageType.REWARD)
                .sendSound(MessageSound.REWARD_SENT)
                .receiveSound(MessageSound.REWARD_RECEIVED)
                .expiresAfter(Duration.ofDays(7))
                .requiredPlaceholders("var", "another_var")
                .description("A test template")
                .system(true)
                .build();

            assertEquals("test.full", template.id());
            assertEquals(TemplateCategory.REWARD, template.category());
            assertEquals(MessageType.REWARD, template.type());
            assertTrue(template.hasSendSound());
            assertTrue(template.hasReceiveSound());
            assertNotNull(template.getSendSoundId());
            assertNotNull(template.getReceiveSoundId());
        }

        @Test
        @DisplayName("Build template with custom sounds")
        void buildTemplateWithCustomSounds() {
            MessageTemplate template = MessageTemplate.builder("test.custom_sounds")
                .subject("Custom")
                .body("Body")
                .customSendSound("minecraft:custom.send")
                .customReceiveSound("minecraft:custom.receive")
                .build();

            assertEquals(MessageSound.CUSTOM, template.sendSound());
            assertEquals("minecraft:custom.send", template.getSendSoundId());
            assertEquals("minecraft:custom.receive", template.getReceiveSoundId());
        }

        @Test
        @DisplayName("Get all placeholders")
        void getAllPlaceholders() {
            MessageTemplate template = MessageTemplate.builder("test.placeholders")
                .subject("{subject_var}")
                .body("{body_var1} and {body_var2}")
                .build();

            List<String> all = template.getAllPlaceholders();
            assertTrue(all.contains("subject_var"));
            assertTrue(all.contains("body_var1"));
            assertTrue(all.contains("body_var2"));
        }
    }

    @Nested
    @DisplayName("MessageSound Enum")
    class MessageSoundEnumTests {

        @Test
        @DisplayName("All sounds have ID")
        void allSoundsHaveId() {
            for (MessageSound sound : MessageSound.values()) {
                assertNotNull(sound.getId());
            }
        }

        @Test
        @DisplayName("Find sound from ID")
        void findSoundFromId() {
            MessageSound found = MessageSound.fromId("welcome_chime");
            assertEquals(MessageSound.WELCOME_CHIME, found);
        }

        @Test
        @DisplayName("Unknown ID returns NONE")
        void unknownIdReturnsNone() {
            MessageSound found = MessageSound.fromId("unknown");
            assertEquals(MessageSound.NONE, found);
        }

        @Test
        @DisplayName("NONE sound has null minecraft sound")
        void noneSoundHasNullMinecraftSound() {
            assertNull(MessageSound.NONE.getMinecraftSound());
        }

        @Test
        @DisplayName("Regular sounds have minecraft sound")
        void regularSoundsHaveMinecraftSound() {
            assertNotNull(MessageSound.WELCOME_CHIME.getMinecraftSound());
            assertNotNull(MessageSound.ACHIEVEMENT.getMinecraftSound());
            assertNotNull(MessageSound.REWARD_RECEIVED.getMinecraftSound());
        }
    }

    @Nested
    @DisplayName("TemplateCategory Enum")
    class TemplateCategoryEnumTests {

        @Test
        @DisplayName("All categories have display name")
        void allCategoriesHaveDisplayName() {
            for (TemplateCategory category : TemplateCategory.values()) {
                assertNotNull(category.getDisplayName());
                assertFalse(category.getDisplayName().isEmpty());
            }
        }

        @Test
        @DisplayName("All categories")
        void allCategories() {
            assertEquals(8, TemplateCategory.values().length);
            assertNotNull(TemplateCategory.WELCOME);
            assertNotNull(TemplateCategory.REWARD);
            assertNotNull(TemplateCategory.EVENT);
            assertNotNull(TemplateCategory.ADMIN);
            assertNotNull(TemplateCategory.SOCIAL);
            assertNotNull(TemplateCategory.TRANSACTION);
            assertNotNull(TemplateCategory.MODERATION);
            assertNotNull(TemplateCategory.CUSTOM);
        }
    }
}
