package com.devmod.mailbox.ticket;

import com.devmod.shared.SharedColorTokens;

/**
 * Priority level of a support ticket.
 */
public enum TicketPriority {
    LOW("Low", 0, SharedColorTokens.Palette.TEXT_SECONDARY),
    NORMAL("Normal", 1, SharedColorTokens.Palette.ACCENT_BLUE),
    HIGH("High", 2, SharedColorTokens.Palette.ACCENT_AMBER),
    URGENT("Urgent", 3, SharedColorTokens.Palette.ERROR);

    private final String displayName;
    private final int level;
    private final int color;
    private final String colorHex;

    TicketPriority(String displayName, int level, int color) {
        this.displayName = displayName;
        this.level = level;
        this.color = color;
        this.colorHex = toHex(color);
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getLevel() {
        return level;
    }

    public int getColor() {
        return color;
    }

    public String getColorHex() {
        return colorHex;
    }

    /**
     * Check if this priority is higher than another.
     */
    public boolean isHigherThan(TicketPriority other) {
        return this.level > other.level;
    }

    private static String toHex(int color) {
        return String.format("#%06X", color & 0xFFFFFFL);
    }
}
