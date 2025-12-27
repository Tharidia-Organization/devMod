package com.devmod.mailbox.ticket;

/**
 * Priority level of a support ticket.
 */
public enum TicketPriority {
    LOW("Low", 0, "#808080"),
    NORMAL("Normal", 1, "#3498db"),
    HIGH("High", 2, "#f39c12"),
    URGENT("Urgent", 3, "#e74c3c");

    private final String displayName;
    private final int level;
    private final String colorHex;

    TicketPriority(String displayName, int level, String colorHex) {
        this.displayName = displayName;
        this.level = level;
        this.colorHex = colorHex;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getLevel() {
        return level;
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
}
