package com.devmod.mailbox.ticket;

/**
 * Status of a support ticket.
 */
public enum TicketStatus {
    OPEN("Open", "Ticket is open and awaiting review"),
    ASSIGNED("Assigned", "Ticket has been assigned to a moderator"),
    IN_PROGRESS("In Progress", "Ticket is being actively worked on"),
    RESOLVED("Resolved", "Ticket has been resolved"),
    CLOSED("Closed", "Ticket is closed");

    private final String displayName;
    private final String description;

    TicketStatus(String displayName, String description) {
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
     * Check if the ticket can be transitioned to the target status.
     */
    public boolean canTransitionTo(TicketStatus target) {
        return switch (this) {
            case OPEN -> target == ASSIGNED || target == IN_PROGRESS || target == RESOLVED || target == CLOSED;
            case ASSIGNED -> target == IN_PROGRESS || target == RESOLVED || target == CLOSED || target == OPEN;
            case IN_PROGRESS -> target == RESOLVED || target == CLOSED || target == ASSIGNED;
            case RESOLVED -> target == CLOSED || target == OPEN; // Can reopen
            case CLOSED -> target == OPEN; // Can reopen
        };
    }

    /**
     * Check if the ticket is in a terminal state.
     */
    public boolean isTerminal() {
        return this == CLOSED;
    }

    /**
     * Check if the ticket is open/active.
     */
    public boolean isActive() {
        return this == OPEN || this == ASSIGNED || this == IN_PROGRESS;
    }
}
