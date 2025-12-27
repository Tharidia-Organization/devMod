package com.devmod.mailbox.ticket;

/**
 * Category of a support ticket.
 */
public enum TicketCategory {
    BUG("Bug Report", "Report a bug or technical issue"),
    ABUSE("Player Report", "Report abusive behavior or rule violations"),
    SUGGESTION("Suggestion", "Suggest a new feature or improvement"),
    QUESTION("Question", "Ask a question about the game"),
    OTHER("Other", "Other issues not covered by other categories");

    private final String displayName;
    private final String description;

    TicketCategory(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    /**
     * Get the default priority for this category.
     */
    public TicketPriority getDefaultPriority() {
        return switch (this) {
            case ABUSE -> TicketPriority.HIGH;
            case BUG -> TicketPriority.NORMAL;
            case SUGGESTION -> TicketPriority.LOW;
            case QUESTION -> TicketPriority.LOW;
            case OTHER -> TicketPriority.NORMAL;
        };
    }
}
