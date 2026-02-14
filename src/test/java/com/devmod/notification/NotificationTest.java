package com.devmod.notification;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NotificationTest {

    // ========================================================================
    // RECORD VALIDATION
    // ========================================================================

    @Nested
    @DisplayName("Record Validation")
    class RecordValidation {

        @Test
        @DisplayName("Null id throws NullPointerException")
        void nullIdThrows() {
            assertThrows(NullPointerException.class, () ->
                new Notification(null, NotificationCategory.SYSTEM, NotificationPriority.NORMAL,
                    "title", null, null, null, null, null, null, Instant.now(), null, false, true, 2000));
        }

        @Test
        @DisplayName("Null category throws NullPointerException")
        void nullCategoryThrows() {
            assertThrows(NullPointerException.class, () ->
                new Notification(UUID.randomUUID(), null, NotificationPriority.NORMAL,
                    "title", null, null, null, null, null, null, Instant.now(), null, false, true, 2000));
        }

        @Test
        @DisplayName("Null priority throws NullPointerException")
        void nullPriorityThrows() {
            assertThrows(NullPointerException.class, () ->
                new Notification(UUID.randomUUID(), NotificationCategory.SYSTEM, null,
                    "title", null, null, null, null, null, null, Instant.now(), null, false, true, 2000));
        }

        @Test
        @DisplayName("Null titleKey throws NullPointerException")
        void nullTitleKeyThrows() {
            assertThrows(NullPointerException.class, () ->
                new Notification(UUID.randomUUID(), NotificationCategory.SYSTEM, NotificationPriority.NORMAL,
                    null, null, null, null, null, null, null, Instant.now(), null, false, true, 2000));
        }

        @Test
        @DisplayName("Null createdAt throws NullPointerException")
        void nullCreatedAtThrows() {
            assertThrows(NullPointerException.class, () ->
                new Notification(UUID.randomUUID(), NotificationCategory.SYSTEM, NotificationPriority.NORMAL,
                    "title", null, null, null, null, null, null, null, null, false, true, 2000));
        }

        @Test
        @DisplayName("Null params becomes empty immutable map")
        void nullParamsBecomeEmptyMap() {
            Notification n = new Notification(UUID.randomUUID(), NotificationCategory.SYSTEM,
                NotificationPriority.NORMAL, "title", null, null, null, null, null, null,
                Instant.now(), null, false, true, 2000);
            assertNotNull(n.params());
            assertTrue(n.params().isEmpty());
        }

        @Test
        @DisplayName("Params are defensively copied")
        void paramsAreDefensivelyCopied() {
            Map<String, String> mutable = new LinkedHashMap<>();
            mutable.put("key", "value");

            Notification n = new Notification(UUID.randomUUID(), NotificationCategory.SYSTEM,
                NotificationPriority.NORMAL, "title", null, mutable, null, null, null, null,
                Instant.now(), null, false, true, 2000);

            // Mutating original should not affect notification
            mutable.put("other", "data");
            assertFalse(n.params().containsKey("other"));
            assertEquals(1, n.params().size());
        }

        @Test
        @DisplayName("Params map is unmodifiable")
        void paramsMapIsUnmodifiable() {
            Map<String, String> original = Map.of("key", "value");
            Notification n = new Notification(UUID.randomUUID(), NotificationCategory.SYSTEM,
                NotificationPriority.NORMAL, "title", null, original, null, null, null, null,
                Instant.now(), null, false, true, 2000);

            assertThrows(UnsupportedOperationException.class, () -> n.params().put("new", "val"));
        }

        @Test
        @DisplayName("Zero displayDurationMs uses priority default")
        void zeroDisplayDurationUsesPriorityDefault() {
            Notification n = new Notification(UUID.randomUUID(), NotificationCategory.SYSTEM,
                NotificationPriority.HIGH, "title", null, null, null, null, null, null,
                Instant.now(), null, false, true, 0);
            assertEquals(NotificationPriority.HIGH.getDefaultDurationMs(), n.displayDurationMs());
        }

        @Test
        @DisplayName("Negative displayDurationMs uses priority default")
        void negativeDisplayDurationUsesPriorityDefault() {
            Notification n = new Notification(UUID.randomUUID(), NotificationCategory.SYSTEM,
                NotificationPriority.URGENT, "title", null, null, null, null, null, null,
                Instant.now(), null, false, true, -100);
            assertEquals(NotificationPriority.URGENT.getDefaultDurationMs(), n.displayDurationMs());
        }

        @Test
        @DisplayName("Positive displayDurationMs is preserved")
        void positiveDisplayDurationIsPreserved() {
            Notification n = new Notification(UUID.randomUUID(), NotificationCategory.SYSTEM,
                NotificationPriority.NORMAL, "title", null, null, null, null, null, null,
                Instant.now(), null, false, true, 5000);
            assertEquals(5000, n.displayDurationMs());
        }
    }

    // ========================================================================
    // BUILDER
    // ========================================================================

    @Nested
    @DisplayName("Builder")
    class BuilderTests {

        @Test
        @DisplayName("Minimal build with just category and titleKey")
        void minimalBuild() {
            Notification n = Notification.builder(NotificationCategory.ACHIEVEMENT)
                .titleKey("test.title")
                .build();

            assertNotNull(n.id());
            assertEquals(NotificationCategory.ACHIEVEMENT, n.category());
            assertEquals(NotificationPriority.NORMAL, n.priority());
            assertEquals("test.title", n.titleKey());
            assertNull(n.messageKey());
            assertTrue(n.params().isEmpty());
            assertNotNull(n.createdAt());
        }

        @Test
        @DisplayName("Builder generates random UUID if not set")
        void builderGeneratesUuid() {
            Notification n1 = Notification.builder(NotificationCategory.SYSTEM).titleKey("t").build();
            Notification n2 = Notification.builder(NotificationCategory.SYSTEM).titleKey("t").build();
            assertNotEquals(n1.id(), n2.id());
        }

        @Test
        @DisplayName("Builder uses provided UUID")
        void builderUsesProvidedUuid() {
            UUID id = UUID.randomUUID();
            Notification n = Notification.builder(NotificationCategory.SYSTEM)
                .id(id).titleKey("t").build();
            assertEquals(id, n.id());
        }

        @Test
        @DisplayName("Builder generates current timestamp if not set")
        void builderGeneratesTimestamp() {
            Instant before = Instant.now();
            Notification n = Notification.builder(NotificationCategory.SYSTEM).titleKey("t").build();
            Instant after = Instant.now();

            assertFalse(n.createdAt().isBefore(before));
            assertFalse(n.createdAt().isAfter(after));
        }

        @Test
        @DisplayName("Builder uses category defaults for mailbox/overlay")
        void builderUsesCategoryDefaults() {
            // ACHIEVEMENT: overlay=true, mailbox=true
            Notification ach = Notification.builder(NotificationCategory.ACHIEVEMENT)
                .titleKey("t").build();
            assertTrue(ach.showOverlay());
            assertTrue(ach.persistToMailbox());

            // COMBAT: overlay=false, mailbox=false
            Notification combat = Notification.builder(NotificationCategory.COMBAT)
                .titleKey("t").build();
            assertFalse(combat.showOverlay());
            assertFalse(combat.persistToMailbox());
        }

        @Test
        @DisplayName("Builder explicit mailbox/overlay overrides category defaults")
        void builderExplicitOverridesCategoryDefaults() {
            // COMBAT defaults: overlay=false, mailbox=false
            Notification n = Notification.builder(NotificationCategory.COMBAT)
                .titleKey("t")
                .showOverlay(true)
                .persistToMailbox(true)
                .build();
            assertTrue(n.showOverlay());
            assertTrue(n.persistToMailbox());
        }

        @Test
        @DisplayName("Builder param accumulation works")
        void builderParamAccumulation() {
            Notification n = Notification.builder(NotificationCategory.SYSTEM)
                .titleKey("t")
                .param("key1", "val1")
                .param("key2", "val2")
                .param("num", 42)
                .param("longNum", 100L)
                .param("dbl", 3.14)
                .build();

            assertEquals("val1", n.getParam("key1"));
            assertEquals("val2", n.getParam("key2"));
            assertEquals("42", n.getParam("num"));
            assertEquals("100", n.getParam("longNum"));
            assertEquals("3.14", n.getParam("dbl"));
        }

        @Test
        @DisplayName("Builder params map replaces individual params")
        void builderParamsMapReplacesIndividual() {
            Map<String, String> paramsMap = Map.of("a", "1", "b", "2");
            Notification n = Notification.builder(NotificationCategory.SYSTEM)
                .titleKey("t")
                .param("shouldBeReplaced", "val")
                .params(paramsMap)
                .build();

            // params() call replaces the map entirely
            assertFalse(n.params().containsKey("shouldBeReplaced"));
            assertEquals("1", n.getParam("a"));
        }

        @Test
        @DisplayName("Builder with null category throws")
        void builderWithNullCategoryThrows() {
            assertThrows(NullPointerException.class, () -> Notification.builder(null));
        }

        @Test
        @DisplayName("Builder build without titleKey throws")
        void builderBuildWithoutTitleKeyThrows() {
            assertThrows(NullPointerException.class, () ->
                Notification.builder(NotificationCategory.SYSTEM).build());
        }

        @Test
        @DisplayName("All builder fields are wired correctly")
        void allBuilderFieldsWiredCorrectly() {
            UUID id = UUID.randomUUID();
            UUID entityId = UUID.randomUUID();
            Instant now = Instant.now();

            Notification n = Notification.builder(NotificationCategory.PARTY)
                .id(id)
                .priority(NotificationPriority.CRITICAL)
                .titleKey("title.key")
                .messageKey("msg.key")
                .param("k", "v")
                .iconId("icon1")
                .soundId("sound1")
                .actionId("action1")
                .actionDataJson("{}")
                .createdAt(now)
                .relatedEntityId(entityId)
                .persistToMailbox(true)
                .showOverlay(false)
                .displayDurationMs(9999)
                .build();

            assertEquals(id, n.id());
            assertEquals(NotificationCategory.PARTY, n.category());
            assertEquals(NotificationPriority.CRITICAL, n.priority());
            assertEquals("title.key", n.titleKey());
            assertEquals("msg.key", n.messageKey());
            assertEquals("v", n.getParam("k"));
            assertEquals("icon1", n.iconId());
            assertEquals("sound1", n.soundId());
            assertEquals("action1", n.actionId());
            assertEquals("{}", n.actionDataJson());
            assertEquals(now, n.createdAt());
            assertEquals(entityId, n.relatedEntityId());
            assertTrue(n.persistToMailbox());
            assertFalse(n.showOverlay());
            assertEquals(9999, n.displayDurationMs());
        }
    }

    // ========================================================================
    // FACTORY METHODS
    // ========================================================================

    @Nested
    @DisplayName("Factory Methods")
    class FactoryMethods {

        @Test
        @DisplayName("simple() creates minimal notification")
        void simpleCreatesMinimal() {
            Notification n = Notification.simple(NotificationCategory.SYSTEM, "sys.title");
            assertEquals(NotificationCategory.SYSTEM, n.category());
            assertEquals("sys.title", n.titleKey());
            assertNull(n.messageKey());
            assertEquals(NotificationPriority.NORMAL, n.priority());
        }

        @Test
        @DisplayName("withMessage() creates notification with title and message")
        void withMessageCreatesNotification() {
            Notification n = Notification.withMessage(NotificationCategory.REWARD, "r.title", "r.msg");
            assertEquals("r.title", n.titleKey());
            assertEquals("r.msg", n.messageKey());
            assertEquals(NotificationCategory.REWARD, n.category());
        }
    }

    // ========================================================================
    // UTILITY METHODS
    // ========================================================================

    @Nested
    @DisplayName("Utility Methods")
    class UtilityMethods {

        @Test
        @DisplayName("shouldUseBanner delegates to priority")
        void shouldUseBannerDelegatesToPriority() {
            Notification low = Notification.builder(NotificationCategory.SYSTEM)
                .titleKey("t").priority(NotificationPriority.LOW).build();
            Notification high = Notification.builder(NotificationCategory.SYSTEM)
                .titleKey("t").priority(NotificationPriority.HIGH).build();

            assertFalse(low.shouldUseBanner());
            assertTrue(high.shouldUseBanner());
        }

        @Test
        @DisplayName("getParam returns null for missing key")
        void getParamReturnsNullForMissing() {
            Notification n = Notification.simple(NotificationCategory.SYSTEM, "t");
            assertNull(n.getParam("nonexistent"));
        }

        @Test
        @DisplayName("getParam with default returns default for missing key")
        void getParamWithDefaultReturnsFallback() {
            Notification n = Notification.simple(NotificationCategory.SYSTEM, "t");
            assertEquals("fallback", n.getParam("nonexistent", "fallback"));
        }

        @Test
        @DisplayName("getParam returns actual value when present")
        void getParamReturnsActualValue() {
            Notification n = Notification.builder(NotificationCategory.SYSTEM)
                .titleKey("t").param("key", "actual").build();
            assertEquals("actual", n.getParam("key"));
            assertEquals("actual", n.getParam("key", "fallback"));
        }
    }
}
