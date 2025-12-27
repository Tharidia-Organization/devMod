package com.devmod.notification;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Categories for the Unified Notification Center.
 * Each category can have different routing defaults and user preferences.
 */
public enum NotificationCategory {
    // Social
    PARTY("party", "devmod.notification.category.party", true, true),

    // Progression
    ACHIEVEMENT("achievement", "devmod.notification.category.achievement", true, true),
    RECORD("record", "devmod.notification.category.record", true, true),
    SEASON("season", "devmod.notification.category.season", true, true),

    // Economy
    TOKEN("token", "devmod.notification.category.token", true, false),
    REWARD("reward", "devmod.notification.category.reward", true, true),

    // Combat
    COMBAT("combat", "devmod.notification.category.combat", false, false),
    RESONANCE("resonance", "devmod.notification.category.resonance", true, false),

    // System
    QUEST("quest", "devmod.notification.category.quest", true, true),
    MAILBOX("mailbox", "devmod.notification.category.mailbox", true, false),
    NEWS("news", "devmod.notification.category.news", true, false),
    ADMIN("admin", "devmod.notification.category.admin", true, true),
    SYSTEM("system", "devmod.notification.category.system", true, false);

    private static final Map<String, NotificationCategory> BY_ID = Arrays.stream(values())
            .collect(Collectors.toMap(NotificationCategory::getId, Function.identity()));

    private final String id;
    private final String translationKey;
    private final boolean defaultOverlayEnabled;
    private final boolean defaultMailboxEnabled;

    NotificationCategory(String id, String translationKey,
                         boolean defaultOverlayEnabled, boolean defaultMailboxEnabled) {
        this.id = id;
        this.translationKey = translationKey;
        this.defaultOverlayEnabled = defaultOverlayEnabled;
        this.defaultMailboxEnabled = defaultMailboxEnabled;
    }

    public String getId() {
        return id;
    }

    public String getTranslationKey() {
        return translationKey;
    }

    public boolean isDefaultOverlayEnabled() {
        return defaultOverlayEnabled;
    }

    public boolean isDefaultMailboxEnabled() {
        return defaultMailboxEnabled;
    }

    /**
     * Get category by its string ID.
     * @param id The category ID
     * @return The category, or SYSTEM if not found
     */
    public static NotificationCategory fromId(String id) {
        return BY_ID.getOrDefault(id, SYSTEM);
    }

    /**
     * Get category by ordinal with bounds checking.
     * @param ordinal The ordinal value
     * @return The category, or SYSTEM if out of bounds
     */
    public static NotificationCategory fromOrdinal(int ordinal) {
        NotificationCategory[] values = values();
        if (ordinal >= 0 && ordinal < values.length) {
            return values[ordinal];
        }
        return SYSTEM;
    }
}
