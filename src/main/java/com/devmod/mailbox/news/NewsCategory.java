package com.devmod.mailbox.news;

/**
 * Enum representing news article categories.
 */
public enum NewsCategory {
    /** Patch notes and updates */
    PATCH("patch", "Patch Notes"),

    /** In-game events */
    EVENT("event", "Events"),

    /** General announcements */
    ANNOUNCEMENT("announcement", "Announcements"),

    /** Server maintenance notices */
    MAINTENANCE("maintenance", "Maintenance"),

    /** Developer communications */
    DEV_BLOG("dev_blog", "Dev Blog"),

    /** Community highlights */
    COMMUNITY("community", "Community");

    private final String id;
    private final String displayName;

    NewsCategory(String id, String displayName) {
        this.id = id;
        this.displayName = displayName;
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    /**
     * Get NewsCategory from string ID.
     *
     * @param id the string identifier
     * @return the matching NewsCategory, or ANNOUNCEMENT if not found
     */
    public static NewsCategory fromId(String id) {
        if (id == null) {
            return ANNOUNCEMENT;
        }
        for (NewsCategory category : values()) {
            if (category.id.equalsIgnoreCase(id)) {
                return category;
            }
        }
        return ANNOUNCEMENT;
    }
}
