package com.devmod.notification;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NotificationCategoryTest {

    @Nested
    @DisplayName("Enum Values")
    class EnumValues {

        @Test
        @DisplayName("All categories have non-null IDs")
        void allCategoriesHaveIds() {
            for (NotificationCategory cat : NotificationCategory.values()) {
                assertNotNull(cat.getId(), "Category " + cat.name() + " has null ID");
                assertFalse(cat.getId().isBlank(), "Category " + cat.name() + " has blank ID");
            }
        }

        @Test
        @DisplayName("All categories have non-null translation keys")
        void allCategoriesHaveTranslationKeys() {
            for (NotificationCategory cat : NotificationCategory.values()) {
                assertNotNull(cat.getTranslationKey(), "Category " + cat.name() + " has null translation key");
                assertTrue(cat.getTranslationKey().startsWith("devmod.notification.category."),
                    "Category " + cat.name() + " translation key does not follow convention");
            }
        }

        @Test
        @DisplayName("Category IDs are unique")
        void categoryIdsAreUnique() {
            NotificationCategory[] values = NotificationCategory.values();
            for (int i = 0; i < values.length; i++) {
                for (int j = i + 1; j < values.length; j++) {
                    assertNotEquals(values[i].getId(), values[j].getId(),
                        "Categories " + values[i].name() + " and " + values[j].name() + " share ID");
                }
            }
        }

        @Test
        @DisplayName("Expected category count")
        void expectedCategoryCount() {
            // PARTY, ACHIEVEMENT, RECORD, SEASON, TOKEN, REWARD, COMBAT, RESONANCE,
            // QUEST, MAILBOX, NEWS, ADMIN, SYSTEM = 13
            assertEquals(13, NotificationCategory.values().length);
        }
    }

    @Nested
    @DisplayName("Category Defaults")
    class CategoryDefaults {

        @Test
        @DisplayName("COMBAT has overlay disabled and mailbox disabled")
        void combatDefaults() {
            assertFalse(NotificationCategory.COMBAT.isDefaultOverlayEnabled());
            assertFalse(NotificationCategory.COMBAT.isDefaultMailboxEnabled());
        }

        @Test
        @DisplayName("PARTY has both overlay and mailbox enabled")
        void partyDefaults() {
            assertTrue(NotificationCategory.PARTY.isDefaultOverlayEnabled());
            assertTrue(NotificationCategory.PARTY.isDefaultMailboxEnabled());
        }

        @Test
        @DisplayName("TOKEN has overlay enabled but mailbox disabled")
        void tokenDefaults() {
            assertTrue(NotificationCategory.TOKEN.isDefaultOverlayEnabled());
            assertFalse(NotificationCategory.TOKEN.isDefaultMailboxEnabled());
        }

        @Test
        @DisplayName("SYSTEM has overlay enabled but mailbox disabled")
        void systemDefaults() {
            assertTrue(NotificationCategory.SYSTEM.isDefaultOverlayEnabled());
            assertFalse(NotificationCategory.SYSTEM.isDefaultMailboxEnabled());
        }
    }

    @Nested
    @DisplayName("fromId lookup")
    class FromIdLookup {

        @Test
        @DisplayName("Finds category by valid ID")
        void findsCategoryByValidId() {
            assertEquals(NotificationCategory.PARTY, NotificationCategory.fromId("party"));
            assertEquals(NotificationCategory.ACHIEVEMENT, NotificationCategory.fromId("achievement"));
            assertEquals(NotificationCategory.COMBAT, NotificationCategory.fromId("combat"));
            assertEquals(NotificationCategory.SYSTEM, NotificationCategory.fromId("system"));
        }

        @Test
        @DisplayName("Unknown ID returns SYSTEM")
        void unknownIdReturnsSystem() {
            assertEquals(NotificationCategory.SYSTEM, NotificationCategory.fromId("nonexistent"));
            assertEquals(NotificationCategory.SYSTEM, NotificationCategory.fromId(""));
        }

        @Test
        @DisplayName("All categories can be looked up by ID")
        void allCategoriesCanBeLookedUpById() {
            for (NotificationCategory cat : NotificationCategory.values()) {
                assertEquals(cat, NotificationCategory.fromId(cat.getId()),
                    "Category " + cat.name() + " not found by ID " + cat.getId());
            }
        }
    }

    @Nested
    @DisplayName("fromOrdinal lookup")
    class FromOrdinalLookup {

        @Test
        @DisplayName("Valid ordinals return correct category")
        void validOrdinalsReturnCorrectCategory() {
            for (NotificationCategory cat : NotificationCategory.values()) {
                assertEquals(cat, NotificationCategory.fromOrdinal(cat.ordinal()));
            }
        }

        @Test
        @DisplayName("Negative ordinal returns SYSTEM")
        void negativeOrdinalReturnsSystem() {
            assertEquals(NotificationCategory.SYSTEM, NotificationCategory.fromOrdinal(-1));
        }

        @Test
        @DisplayName("Ordinal out of bounds returns SYSTEM")
        void ordinalOutOfBoundsReturnsSystem() {
            assertEquals(NotificationCategory.SYSTEM, NotificationCategory.fromOrdinal(999));
        }
    }
}
