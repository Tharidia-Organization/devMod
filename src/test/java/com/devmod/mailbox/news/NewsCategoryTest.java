package com.devmod.mailbox.news;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NewsCategoryTest {

    @Test
    @DisplayName("fromId returns correct category for known ids")
    void fromIdKnown() {
        assertEquals(NewsCategory.PATCH, NewsCategory.fromId("patch"));
        assertEquals(NewsCategory.EVENT, NewsCategory.fromId("event"));
        assertEquals(NewsCategory.ANNOUNCEMENT, NewsCategory.fromId("announcement"));
        assertEquals(NewsCategory.MAINTENANCE, NewsCategory.fromId("maintenance"));
        assertEquals(NewsCategory.DEV_BLOG, NewsCategory.fromId("dev_blog"));
        assertEquals(NewsCategory.COMMUNITY, NewsCategory.fromId("community"));
    }

    @Test
    @DisplayName("fromId is case insensitive")
    void fromIdCaseInsensitive() {
        assertEquals(NewsCategory.PATCH, NewsCategory.fromId("PATCH"));
        assertEquals(NewsCategory.EVENT, NewsCategory.fromId("Event"));
    }

    @Test
    @DisplayName("fromId returns ANNOUNCEMENT for null or unknown")
    void fromIdDefault() {
        assertEquals(NewsCategory.ANNOUNCEMENT, NewsCategory.fromId(null));
        assertEquals(NewsCategory.ANNOUNCEMENT, NewsCategory.fromId("unknown"));
    }

    @Test
    @DisplayName("getId returns correct id strings")
    void getId() {
        assertEquals("patch", NewsCategory.PATCH.getId());
        assertEquals("event", NewsCategory.EVENT.getId());
        assertEquals("dev_blog", NewsCategory.DEV_BLOG.getId());
    }

    @Test
    @DisplayName("displayName is set for all values")
    void displayName() {
        for (NewsCategory cat : NewsCategory.values()) {
            assertNotNull(cat.getDisplayName());
            assertFalse(cat.getDisplayName().isEmpty());
        }
    }
}
