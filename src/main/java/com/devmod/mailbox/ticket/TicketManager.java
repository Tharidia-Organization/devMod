package com.devmod.mailbox.ticket;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.devmod.mailbox.template.MessageTemplateRegistry;
import com.devmod.mailbox.webhook.WebhookManager;

/**
 * Manager for the support ticket system.
 *
 * Provides high-level operations for creating, managing, and resolving tickets.
 */
public final class TicketManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(TicketManager.class);

    public static final TicketManager INSTANCE = new TicketManager();

    private volatile boolean initialized = false;

    private TicketManager() {}

    // ============================================================================
    // LIFECYCLE
    // ============================================================================

    public CompletableFuture<Void> initialize() {
        if (initialized) {
            return CompletableFuture.completedFuture(null);
        }

        return TicketRepository.INSTANCE.initialize().thenRun(() -> {
            initialized = true;
            LOGGER.info("[TicketManager] Initialized");
        });
    }

    public CompletableFuture<Void> shutdown() {
        if (!initialized) {
            return CompletableFuture.completedFuture(null);
        }

        initialized = false;
        return TicketRepository.INSTANCE.shutdown().thenRun(() -> {
            LOGGER.info("[TicketManager] Shutdown complete");
        });
    }

    // ============================================================================
    // TICKET CREATION
    // ============================================================================

    /**
     * Create a new ticket from a player report.
     *
     * @param reporterUuid UUID of the reporting player
     * @param reporterName Name of the reporting player
     * @param category Ticket category
     * @param subject Short subject/title
     * @param description Detailed description
     * @return Created ticket
     */
    public CompletableFuture<Ticket> createTicket(
            UUID reporterUuid,
            @Nullable String reporterName,
            TicketCategory category,
            String subject,
            @Nullable String description) {

        String safeReporterName = reporterName != null ? reporterName : "Unknown";
        Ticket ticket = Ticket.builder(reporterUuid, category, subject)
            .reporterName(safeReporterName)
            .description(description)
            .build();

        return TicketRepository.INSTANCE.saveTicket(ticket).thenApply(saved -> {
            LOGGER.info("[TicketManager] Ticket created: {} by {} ({})",
                saved.id(), safeReporterName, category);

            // Send confirmation mailbox to reporter
            sendTicketConfirmation(saved);

            // Dispatch webhook for urgent tickets
            if (saved.priority() == TicketPriority.URGENT || saved.priority() == TicketPriority.HIGH) {
                WebhookManager.INSTANCE.dispatchAnomalyDetected(
                    "new_ticket_" + saved.priority().name().toLowerCase(Locale.ROOT),
                    reporterUuid,
                    "New " + saved.priority().getDisplayName() + " ticket: " + subject
                );
            }

            return saved;
        });
    }

    /**
     * Create a player abuse report ticket.
     *
     * @param reporterUuid UUID of the reporting player
     * @param reporterName Name of the reporting player
     * @param reportedUuid UUID of the reported player
     * @param reportedName Name of the reported player
     * @param reason Reason for the report
     * @param description Detailed description
     * @return Created ticket
     */
    public CompletableFuture<Ticket> createPlayerReport(
            UUID reporterUuid,
            @Nullable String reporterName,
            UUID reportedUuid,
            @Nullable String reportedName,
            String reason,
            @Nullable String description) {

        String safeReporterName = reporterName != null ? reporterName : "Unknown";
        String safeReportedName = reportedName != null ? reportedName : "Unknown";
        String subject = "Player Report: " + safeReportedName + " - " + reason;

        Ticket ticket = Ticket.builder(reporterUuid, TicketCategory.ABUSE, subject)
            .reporterName(safeReporterName)
            .reportedPlayer(reportedUuid, safeReportedName)
            .description(description)
            .priority(TicketPriority.HIGH) // Abuse reports are high priority
            .build();

        return TicketRepository.INSTANCE.saveTicket(ticket).thenApply(saved -> {
            LOGGER.info("[TicketManager] Player report created: {} reported {} by {}",
                saved.id(), safeReportedName, safeReporterName);

            // Send confirmation mailbox to reporter
            sendTicketConfirmation(saved);

            // Dispatch webhook for abuse reports
            WebhookManager.INSTANCE.dispatchAnomalyDetected(
                "player_report",
                reporterUuid,
                reporterName + " reported " + reportedName + ": " + reason
            );

            return saved;
        });
    }

    // ============================================================================
    // TICKET MANAGEMENT
    // ============================================================================

    /**
     * Get a ticket by ID.
     */
    public CompletableFuture<Optional<Ticket>> getTicket(UUID ticketId) {
        return TicketRepository.INSTANCE.getTicket(ticketId);
    }

    /**
     * Find tickets with filters.
     */
    public CompletableFuture<List<Ticket>> findTickets(TicketRepository.TicketQuery query) {
        return TicketRepository.INSTANCE.findTickets(query);
    }

    /**
     * Get all open tickets.
     */
    public CompletableFuture<List<Ticket>> getOpenTickets() {
        return TicketRepository.INSTANCE.findTickets(TicketRepository.TicketQuery.open());
    }

    /**
     * Get tickets for a specific player.
     */
    public CompletableFuture<List<Ticket>> getPlayerTickets(UUID playerUuid) {
        return TicketRepository.INSTANCE.findTickets(TicketRepository.TicketQuery.forReporter(playerUuid));
    }

    /**
     * Assign a ticket to a moderator.
     *
     * @param ticketId Ticket UUID
     * @param assigneeUuid Moderator UUID
     * @param assigneeName Moderator name
     * @return Updated ticket, or empty if not found
     */
    public CompletableFuture<Optional<Ticket>> assignTicket(UUID ticketId, UUID assigneeUuid, String assigneeName) {
        return TicketRepository.INSTANCE.getTicket(ticketId).thenCompose(opt -> {
            if (opt.isEmpty()) {
                return CompletableFuture.completedFuture(Optional.empty());
            }

            Ticket ticket = opt.get();
            Ticket updated = ticket.withAssignment(assigneeUuid, assigneeName);

            return TicketRepository.INSTANCE.saveTicket(updated).thenApply(saved -> {
                LOGGER.info("[TicketManager] Ticket {} assigned to {}", ticketId, assigneeName);

                // Add system comment
                TicketComment comment = TicketComment.systemComment(
                    ticketId,
                    "Ticket assigned to " + assigneeName
                );
                TicketRepository.INSTANCE.saveComment(comment);

                return Optional.of(saved);
            });
        });
    }

    /**
     * Update ticket status.
     *
     * @param ticketId Ticket UUID
     * @param newStatus New status
     * @param actorName Name of the person changing status
     * @return Updated ticket, or empty if not found or invalid transition
     */
    public CompletableFuture<Optional<Ticket>> updateStatus(UUID ticketId, TicketStatus newStatus, String actorName) {
        return TicketRepository.INSTANCE.getTicket(ticketId).thenCompose(opt -> {
            if (opt.isEmpty()) {
                return CompletableFuture.completedFuture(Optional.empty());
            }

            Ticket ticket = opt.get();
            if (!ticket.status().canTransitionTo(newStatus)) {
                LOGGER.warn("[TicketManager] Invalid status transition: {} -> {}", ticket.status(), newStatus);
                return CompletableFuture.completedFuture(Optional.empty());
            }

            Ticket updated = ticket.withStatus(newStatus);

            return TicketRepository.INSTANCE.saveTicket(updated).thenApply(saved -> {
                LOGGER.info("[TicketManager] Ticket {} status changed to {} by {}",
                    ticketId, newStatus, actorName);

                // Add system comment
                TicketComment comment = TicketComment.systemComment(
                    ticketId,
                    "Status changed to " + newStatus.getDisplayName() + " by " + actorName
                );
                TicketRepository.INSTANCE.saveComment(comment);

                return Optional.of(saved);
            });
        });
    }

    /**
     * Resolve a ticket with resolution notes.
     *
     * @param ticketId Ticket UUID
     * @param resolutionNotes Notes about the resolution
     * @param resolverName Name of the person resolving
     * @return Updated ticket, or empty if not found
     */
    public CompletableFuture<Optional<Ticket>> resolveTicket(UUID ticketId, String resolutionNotes, String resolverName) {
        return TicketRepository.INSTANCE.getTicket(ticketId).thenCompose(opt -> {
            if (opt.isEmpty()) {
                return CompletableFuture.completedFuture(Optional.empty());
            }

            Ticket ticket = opt.get();
            Ticket updated = ticket.withResolution(resolutionNotes);

            return TicketRepository.INSTANCE.saveTicket(updated).thenApply(saved -> {
                LOGGER.info("[TicketManager] Ticket {} resolved by {}", ticketId, resolverName);

                // Add system comment
                TicketComment comment = TicketComment.systemComment(
                    ticketId,
                    "Ticket resolved by " + resolverName + ": " + resolutionNotes
                );
                TicketRepository.INSTANCE.saveComment(comment);

                // Send resolution mailbox to reporter
                sendTicketResolution(saved);

                return Optional.of(saved);
            });
        });
    }

    /**
     * Update ticket priority.
     */
    public CompletableFuture<Optional<Ticket>> updatePriority(UUID ticketId, TicketPriority newPriority, String actorName) {
        return TicketRepository.INSTANCE.getTicket(ticketId).thenCompose(opt -> {
            if (opt.isEmpty()) {
                return CompletableFuture.completedFuture(Optional.empty());
            }

            Ticket ticket = opt.get();
            Ticket updated = ticket.withPriority(newPriority);

            return TicketRepository.INSTANCE.saveTicket(updated).thenApply(saved -> {
                LOGGER.info("[TicketManager] Ticket {} priority changed to {} by {}",
                    ticketId, newPriority, actorName);

                // Add system comment
                TicketComment comment = TicketComment.systemComment(
                    ticketId,
                    "Priority changed to " + newPriority.getDisplayName() + " by " + actorName
                );
                TicketRepository.INSTANCE.saveComment(comment);

                return Optional.of(saved);
            });
        });
    }

    // ============================================================================
    // COMMENTS
    // ============================================================================

    /**
     * Add a comment to a ticket.
     */
    public CompletableFuture<TicketComment> addComment(
            UUID ticketId,
            @Nullable UUID authorUuid,
            @Nullable String authorName,
            String content,
            boolean isInternal) {

        String safeAuthorName = authorName != null ? authorName : "Admin";
        TicketComment comment = isInternal
            ? TicketComment.internalNote(ticketId, authorUuid, safeAuthorName, content)
            : TicketComment.staffComment(ticketId, authorUuid, safeAuthorName, content);

        return TicketRepository.INSTANCE.saveComment(comment).thenApply(saved -> {
            LOGGER.debug("[TicketManager] Comment added to ticket {} by {}",
                ticketId, safeAuthorName);
            return saved;
        });
    }

    /**
     * Get comments for a ticket.
     *
     * @param ticketId Ticket UUID
     * @param includeInternal Whether to include internal staff notes
     * @return List of comments
     */
    public CompletableFuture<List<TicketComment>> getComments(UUID ticketId, boolean includeInternal) {
        return TicketRepository.INSTANCE.getComments(ticketId, includeInternal);
    }

    // ============================================================================
    // STATISTICS
    // ============================================================================

    /**
     * Get ticket statistics.
     */
    public CompletableFuture<TicketRepository.TicketStats> getStats() {
        return TicketRepository.INSTANCE.getStats();
    }

    // ============================================================================
    // MAILBOX NOTIFICATIONS
    // ============================================================================

    private void sendTicketConfirmation(Ticket ticket) {
        try {
            MessageTemplateRegistry.INSTANCE.sendFromTemplate(
                "moderation.report_received",
                ticket.reporterUuid(),
                Map.of(
                    "player_name", ticket.reporterName() != null ? ticket.reporterName() : "Player",
                    "ticket_id", ticket.id().toString().substring(0, 8),
                    "category", ticket.category().getDisplayName(),
                    "subject", ticket.subject()
                ),
                null
            );
        } catch (Exception e) {
            LOGGER.error("[TicketManager] Failed to send ticket confirmation mailbox", e);
        }
    }

    private void sendTicketResolution(Ticket ticket) {
        try {
            MessageTemplateRegistry.INSTANCE.sendFromTemplate(
                "moderation.report_resolved",
                ticket.reporterUuid(),
                Map.of(
                    "player_name", ticket.reporterName() != null ? ticket.reporterName() : "Player",
                    "ticket_id", ticket.id().toString().substring(0, 8),
                    "resolution", ticket.resolutionNotes() != null ? ticket.resolutionNotes() : "Resolved"
                ),
                null
            );
        } catch (Exception e) {
            LOGGER.error("[TicketManager] Failed to send ticket resolution mailbox", e);
        }
    }
}
