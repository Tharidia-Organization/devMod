package com.devmod.notification;

/**
 * Priority levels for the Unified Notification Center.
 * Higher priority notifications get banner treatment and longer display times.
 */
public enum NotificationPriority {
    /**
     * Low priority - subtle feedback, short duration.
     * Examples: Combo decay, minor stat updates
     */
    LOW(0, 1000, false),

    /**
     * Normal priority - standard notifications.
     * Examples: Token gains, party member updates
     */
    NORMAL(1, 2000, false),

    /**
     * High priority - important notifications with banner display.
     * Examples: Badge unlocks, wave completions
     */
    HIGH(2, 4000, true),

    /**
     * Urgent priority - significant events.
     * Examples: Quest completion, personal records
     */
    URGENT(3, 6000, true),

    /**
     * Critical priority - must-see notifications.
     * Examples: Admin alerts, party kicks, bans
     */
    CRITICAL(4, 8000, true);

    private final int level;
    private final int defaultDurationMs;
    private final boolean useBannerDisplay;

    NotificationPriority(int level, int defaultDurationMs, boolean useBannerDisplay) {
        this.level = level;
        this.defaultDurationMs = defaultDurationMs;
        this.useBannerDisplay = useBannerDisplay;
    }

    /**
     * Numeric priority level (0-4).
     */
    public int getLevel() {
        return level;
    }

    /**
     * Default display duration in milliseconds.
     */
    public int getDefaultDurationMs() {
        return defaultDurationMs;
    }

    /**
     * Whether this priority should use banner display (top-center) instead of toast (corner).
     */
    public boolean shouldUseBannerDisplay() {
        return useBannerDisplay;
    }

    /**
     * Check if this priority is at least as high as another.
     * @param other The priority to compare against
     * @return true if this priority level is >= other's level
     */
    public boolean isAtLeast(NotificationPriority other) {
        return this.level >= other.level;
    }

    /**
     * Get priority by ordinal with bounds checking.
     * @param ordinal The ordinal value
     * @return The priority, or NORMAL if out of bounds
     */
    public static NotificationPriority fromOrdinal(int ordinal) {
        NotificationPriority[] values = values();
        if (ordinal >= 0 && ordinal < values.length) {
            return values[ordinal];
        }
        return NORMAL;
    }

    /**
     * Get priority by level value.
     * @param level The level (0-4)
     * @return The matching priority, or NORMAL if not found
     */
    public static NotificationPriority fromLevel(int level) {
        for (NotificationPriority priority : values()) {
            if (priority.level == level) {
                return priority;
            }
        }
        return NORMAL;
    }
}
