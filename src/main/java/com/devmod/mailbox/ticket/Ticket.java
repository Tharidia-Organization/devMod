package com.devmod.mailbox.ticket;

import java.time.Instant;
import java.util.UUID;

import javax.annotation.Nullable;

/**
 * Represents a support ticket submitted by a player.
 */
public record Ticket(
    UUID id,
    UUID reporterUuid,
    @Nullable String reporterName,
    @Nullable UUID reportedUuid,
    @Nullable String reportedName,
    TicketCategory category,
    TicketPriority priority,
    TicketStatus status,
    String subject,
    @Nullable String description,
    @Nullable UUID assignedTo,
    @Nullable String assignedToName,
    Instant createdAt,
    @Nullable Instant updatedAt,
    @Nullable Instant resolvedAt,
    @Nullable String resolutionNotes
) {
    /**
     * Create a new builder for a ticket.
     */
    public static Builder builder(UUID reporterUuid, TicketCategory category, String subject) {
        return new Builder(reporterUuid, category, subject);
    }

    /**
     * Check if this ticket is about a player report.
     */
    public boolean isPlayerReport() {
        return reportedUuid != null;
    }

    /**
     * Check if this ticket is assigned to someone.
     */
    public boolean isAssigned() {
        return assignedTo != null;
    }

    /**
     * Check if this ticket is resolved.
     */
    public boolean isResolved() {
        return status == TicketStatus.RESOLVED || status == TicketStatus.CLOSED;
    }

    /**
     * Get the time since creation in milliseconds.
     */
    public long getAge() {
        return Instant.now().toEpochMilli() - createdAt.toEpochMilli();
    }

    /**
     * Builder for Ticket.
     */
    public static final class Builder {
        private final UUID reporterUuid;
        private final TicketCategory category;
        private final String subject;

        private UUID id = UUID.randomUUID();
        @Nullable private String reporterName;
        @Nullable private UUID reportedUuid;
        @Nullable private String reportedName;
        private TicketPriority priority;
        private TicketStatus status = TicketStatus.OPEN;
        @Nullable private String description;
        @Nullable private UUID assignedTo;
        @Nullable private String assignedToName;
        private Instant createdAt = Instant.now();
        @Nullable private Instant updatedAt;
        @Nullable private Instant resolvedAt;
        @Nullable private String resolutionNotes;

        Builder(UUID reporterUuid, TicketCategory category, String subject) {
            this.reporterUuid = reporterUuid;
            this.category = category;
            this.subject = subject;
            this.priority = category.getDefaultPriority();
        }

        public Builder id(UUID id) {
            this.id = id;
            return this;
        }

        public Builder reporterName(String reporterName) {
            this.reporterName = reporterName;
            return this;
        }

        public Builder reportedPlayer(UUID uuid, String name) {
            this.reportedUuid = uuid;
            this.reportedName = name;
            return this;
        }

        public Builder priority(TicketPriority priority) {
            this.priority = priority;
            return this;
        }

        public Builder status(TicketStatus status) {
            this.status = status;
            return this;
        }

        public Builder description(@Nullable String description) {
            this.description = description;
            return this;
        }

        public Builder assignTo(UUID uuid, String name) {
            this.assignedTo = uuid;
            this.assignedToName = name;
            return this;
        }

        public Builder createdAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public Builder updatedAt(Instant updatedAt) {
            this.updatedAt = updatedAt;
            return this;
        }

        public Builder resolvedAt(Instant resolvedAt) {
            this.resolvedAt = resolvedAt;
            return this;
        }

        public Builder resolutionNotes(@Nullable String resolutionNotes) {
            this.resolutionNotes = resolutionNotes;
            return this;
        }

        public Ticket build() {
            return new Ticket(
                id,
                reporterUuid,
                reporterName,
                reportedUuid,
                reportedName,
                category,
                priority,
                status,
                subject,
                description,
                assignedTo,
                assignedToName,
                createdAt,
                updatedAt,
                resolvedAt,
                resolutionNotes
            );
        }
    }

    /**
     * Create a copy of this ticket with updated fields.
     */
    public Ticket withStatus(TicketStatus newStatus) {
        return new Ticket(
            id, reporterUuid, reporterName, reportedUuid, reportedName,
            category, priority, newStatus, subject, description,
            assignedTo, assignedToName, createdAt, Instant.now(),
            newStatus == TicketStatus.RESOLVED ? Instant.now() : resolvedAt,
            resolutionNotes
        );
    }

    public Ticket withAssignment(UUID assigneeUuid, String assigneeName) {
        return new Ticket(
            id, reporterUuid, reporterName, reportedUuid, reportedName,
            category, priority, TicketStatus.ASSIGNED, subject, description,
            assigneeUuid, assigneeName, createdAt, Instant.now(),
            resolvedAt, resolutionNotes
        );
    }

    public Ticket withPriority(TicketPriority newPriority) {
        return new Ticket(
            id, reporterUuid, reporterName, reportedUuid, reportedName,
            category, newPriority, status, subject, description,
            assignedTo, assignedToName, createdAt, Instant.now(),
            resolvedAt, resolutionNotes
        );
    }

    public Ticket withResolution(String notes) {
        return new Ticket(
            id, reporterUuid, reporterName, reportedUuid, reportedName,
            category, priority, TicketStatus.RESOLVED, subject, description,
            assignedTo, assignedToName, createdAt, Instant.now(),
            Instant.now(), notes
        );
    }
}
