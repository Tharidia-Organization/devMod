package com.devmod.mailbox.ticket;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.devmod.mailbox.MailboxPermissions;

/**
 * P1: Auto-transition service for ticket workflow.
 *
 * Periodically evaluates tickets and applies automatic transitions based on rules:
 * - Auto-close resolved tickets after N days of inactivity
 * - Auto-escalate tickets breaching SLA
 * - Auto-assign to available moderators (round-robin)
 *
 * All auto-transitions are logged with SYSTEM actor role for audit trail.
 */
public final class AutoTransitionService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AutoTransitionService.class);

    public static final AutoTransitionService INSTANCE = new AutoTransitionService();

    // ========================================================================
    // CONFIGURATION
    // ========================================================================

    /** Auto-close resolved tickets after this duration. */
    private volatile Duration autoCloseDuration = Duration.ofDays(7);

    /** Whether auto-close is enabled. */
    private volatile boolean autoCloseEnabled = true;

    /** Whether SLA auto-escalation is enabled. */
    private volatile boolean slaEscalationEnabled = true;

    /** Whether auto-assign on open is enabled. */
    private volatile boolean autoAssignEnabled = false;

    /** Run interval for the scheduler. */
    private volatile Duration runInterval = Duration.ofMinutes(15);

    // ========================================================================
    // STATE
    // ========================================================================

    private volatile boolean running = false;
    @Nullable
    private ScheduledExecutorService scheduler;
    @Nullable
    private ScheduledFuture<?> scheduledTask;

    /** Metrics */
    private final AtomicLong autoClosedCount = new AtomicLong(0);
    private final AtomicLong escalatedCount = new AtomicLong(0);
    private final AtomicLong autoAssignedCount = new AtomicLong(0);
    private final AtomicLong totalRunCount = new AtomicLong(0);
    @Nullable
    private volatile Instant lastRunTime;

    /** Round-robin assignment state */
    private final AtomicInteger roundRobinIndex = new AtomicInteger(0);

    /** Tracks open ticket count per moderator for load-balancing */
    private final Map<UUID, Integer> moderatorTicketCounts = new ConcurrentHashMap<>();

    private AutoTransitionService() {}

    // ========================================================================
    // LIFECYCLE
    // ========================================================================

    /**
     * Start the auto-transition service.
     */
    public void start() {
        if (running) {
            return;
        }

        ScheduledExecutorService svc = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "TicketAutoTransition");
            t.setDaemon(true);
            return t;
        });
        scheduler = svc;

        scheduledTask = svc.scheduleAtFixedRate(
            this::runAutoTransitions,
            1,
            runInterval.toMinutes(),
            TimeUnit.MINUTES
        );

        running = true;
        LOGGER.info("[AutoTransition] Service started with {}min interval", runInterval.toMinutes());
    }

    /**
     * Stop the auto-transition service.
     */
    public void stop() {
        if (!running) {
            return;
        }

        if (scheduledTask != null) {
            scheduledTask.cancel(false);
            scheduledTask = null;
        }

        ScheduledExecutorService svc = scheduler;
        if (svc != null) {
            svc.shutdown();
            try {
                if (!svc.awaitTermination(5, TimeUnit.SECONDS)) {
                    svc.shutdownNow();
                }
            } catch (InterruptedException e) {
                svc.shutdownNow();
                Thread.currentThread().interrupt();
            }
            scheduler = null;
        }

        running = false;
        LOGGER.info("[AutoTransition] Service stopped");
    }

    /**
     * Check if the service is running.
     */
    public boolean isRunning() {
        return running;
    }

    // ========================================================================
    // AUTO-TRANSITION LOGIC
    // ========================================================================

    /**
     * Run all auto-transition checks.
     * This is called periodically by the scheduler.
     */
    public void runAutoTransitions() {
        if (!running) {
            return;
        }

        totalRunCount.incrementAndGet();
        lastRunTime = Instant.now();

        LOGGER.debug("[AutoTransition] Running auto-transition checks...");

        try {
            // Get all active tickets using the query API
            TicketRepository.INSTANCE.findTickets(TicketRepository.TicketQuery.all())
                .thenAccept(tickets -> {
                    // Refresh moderator ticket counts for load-balanced assignment
                    if (autoAssignEnabled) {
                        refreshModeratorTicketCounts(tickets);
                    }

                    List<AutoTransitionResult> results = new ArrayList<>();

                    // Use batch operations for SLA processing (more efficient)
                    if (slaEscalationEnabled) {
                        // Batch find all tickets needing escalation
                        Map<UUID, TicketPriority> escalations = TicketWorkflow.findTicketsNeedingEscalation(tickets);
                        for (Ticket ticket : tickets) {
                            TicketPriority newPriority = escalations.get(ticket.id());
                            if (newPriority != null) {
                                results.add(AutoTransitionResult.of(
                                    ticket,
                                    AutoTransitionType.SLA_ESCALATION,
                                    null,
                                    newPriority,
                                    String.format("SLA escalation: %s -> %s", ticket.priority(), newPriority)
                                ));
                            }
                        }

                        // Log response SLA breaches for monitoring/alerting
                        Set<UUID> breachingResponse = TicketWorkflow.findTicketsBreachingResponseSla(tickets);
                        if (!breachingResponse.isEmpty()) {
                            LOGGER.warn("[AutoTransition] {} tickets breaching response SLA: {}",
                                breachingResponse.size(), breachingResponse);
                        }
                    }

                    for (Ticket ticket : tickets) {
                        // Skip terminal tickets
                        if (ticket.status().isTerminal()) {
                            continue;
                        }

                        // Check auto-close for resolved tickets
                        if (autoCloseEnabled && ticket.status() == TicketStatus.RESOLVED) {
                            checkAutoClose(ticket).ifPresent(results::add);
                        }

                        // Check auto-assign for open tickets
                        if (autoAssignEnabled && ticket.status() == TicketStatus.OPEN) {
                            checkAutoAssign(ticket).ifPresent(results::add);
                        }
                    }

                    // Apply all transitions
                    for (AutoTransitionResult result : results) {
                        applyTransition(result);
                    }

                    if (!results.isEmpty()) {
                        LOGGER.info("[AutoTransition] Applied {} auto-transitions", results.size());
                    }
                })
                .exceptionally(e -> {
                    LOGGER.error("[AutoTransition] Failed to run auto-transitions", e);
                    return null;
                });

        } catch (Exception e) {
            LOGGER.error("[AutoTransition] Error during auto-transition run", e);
        }
    }

    /**
     * Check if a resolved ticket should be auto-closed.
     */
    private java.util.Optional<AutoTransitionResult> checkAutoClose(Ticket ticket) {
        if (ticket.status() != TicketStatus.RESOLVED) {
            return java.util.Optional.empty();
        }

        Instant updatedAt = ticket.updatedAt() != null ? ticket.updatedAt() : ticket.createdAt();
        Duration timeSinceResolved = Duration.between(updatedAt, Instant.now());

        if (timeSinceResolved.compareTo(autoCloseDuration) > 0) {
            return java.util.Optional.of(AutoTransitionResult.of(
                ticket,
                AutoTransitionType.AUTO_CLOSE,
                TicketStatus.CLOSED,
                null,
                String.format("Auto-closed after %d days of inactivity", autoCloseDuration.toDays())
            ));
        }

        return java.util.Optional.empty();
    }

    /**
     * Check if an open ticket should be auto-assigned.
     * Uses load-balanced assignment (assigns to moderator with fewest open tickets).
     * Falls back to round-robin if all moderators have equal load.
     */
    private java.util.Optional<AutoTransitionResult> checkAutoAssign(Ticket ticket) {
        // Skip if already assigned
        if (ticket.isAssigned()) {
            return java.util.Optional.empty();
        }

        // Get available moderators (admins from MailboxPermissions)
        Set<UUID> moderators = MailboxPermissions.INSTANCE.getAdmins();
        if (moderators.isEmpty()) {
            LOGGER.debug("[AutoAssign] No moderators available for assignment");
            return java.util.Optional.empty();
        }

        // Convert to list for indexed access
        List<UUID> moderatorList = new ArrayList<>(moderators);

        // Select assignee using load-balanced or round-robin strategy
        UUID selectedModerator = selectModerator(moderatorList);
        if (selectedModerator == null) {
            return java.util.Optional.empty();
        }

        // Use shortened UUID as moderator identifier
        // (Full player name resolution would require server access)
        String moderatorName = selectedModerator.toString().substring(0, 8);

        LOGGER.debug("[AutoAssign] Selected moderator {} for ticket {}", moderatorName, ticket.id());

        return java.util.Optional.of(AutoTransitionResult.forAssignment(
            ticket,
            selectedModerator,
            moderatorName,
            String.format("Auto-assigned to %s (load-balanced)", moderatorName)
        ));
    }

    /**
     * Selects a moderator using load-balanced strategy.
     * Prefers moderator with fewest assigned open tickets.
     * Falls back to round-robin if ticket counts are equal.
     */
    @Nullable
    private UUID selectModerator(List<UUID> moderators) {
        if (moderators.isEmpty()) {
            return null;
        }

        if (moderators.size() == 1) {
            return moderators.get(0);
        }

        // Find moderator with lowest ticket count (load-balanced)
        UUID lowestLoadModerator = null;
        int lowestCount = Integer.MAX_VALUE;
        int equalCountModerators = 0;

        for (UUID moderator : moderators) {
            int count = moderatorTicketCounts.getOrDefault(moderator, 0);
            if (count < lowestCount) {
                lowestCount = count;
                lowestLoadModerator = moderator;
                equalCountModerators = 1;
            } else if (count == lowestCount) {
                equalCountModerators++;
            }
        }

        // If all moderators have equal load, use round-robin
        if (equalCountModerators == moderators.size()) {
            int index = roundRobinIndex.getAndUpdate(i -> (i + 1) % moderators.size());
            lowestLoadModerator = moderators.get(index);
            LOGGER.trace("[AutoAssign] Using round-robin, selected index {}", index);
        }

        // Increment the selected moderator's ticket count
        if (lowestLoadModerator != null) {
            moderatorTicketCounts.merge(lowestLoadModerator, 1, (a, b) -> a + b);
        }

        return lowestLoadModerator;
    }

    /**
     * Refreshes moderator ticket counts from the repository.
     * Called at the start of each auto-transition run.
     */
    private void refreshModeratorTicketCounts(List<Ticket> tickets) {
        moderatorTicketCounts.clear();

        for (Ticket ticket : tickets) {
            if (ticket.isAssigned() && ticket.status().isActive()) {
                UUID assignee = ticket.assignedTo();
                if (assignee != null) {
                    moderatorTicketCounts.merge(assignee, 1, (a, b) -> a + b);
                }
            }
        }

        LOGGER.debug("[AutoAssign] Refreshed ticket counts: {}", moderatorTicketCounts);
    }

    /**
     * Apply an auto-transition result.
     */
    private void applyTransition(AutoTransitionResult result) {
        Ticket ticket = result.ticket();
        TicketWorkflow.TransitionContext ctx = TicketWorkflow.TransitionContext.system(result.reason());

        CompletableFuture<Void> future;

        switch (result.type()) {
            case AUTO_CLOSE -> {
                TicketWorkflow.TransitionResult transitionResult = TicketWorkflow.transition(
                    ticket, TicketStatus.CLOSED, ctx);

                Ticket updated = transitionResult.updatedTicket();
                if (transitionResult.success() && updated != null) {
                    future = TicketRepository.INSTANCE.saveTicket(updated)
                        .thenRun(() -> autoClosedCount.incrementAndGet());
                } else {
                    LOGGER.warn("[AutoTransition] Failed to auto-close ticket {}: {}",
                        ticket.id(), transitionResult.errorMessage());
                    return;
                }
            }

            case SLA_ESCALATION -> {
                if (result.newPriority() == null) {
                    return;
                }
                Ticket escalated = ticket.withPriority(result.newPriority());
                future = TicketRepository.INSTANCE.saveTicket(escalated)
                    .thenRun(() -> escalatedCount.incrementAndGet());
            }

            case AUTO_ASSIGN -> {
                UUID assigneeId = result.assigneeId();
                if (result.newStatus() == null || assigneeId == null) {
                    return;
                }

                // First transition the status
                TicketWorkflow.TransitionResult transitionResult = TicketWorkflow.transition(
                    ticket, result.newStatus(), ctx);

                Ticket updated = transitionResult.updatedTicket();
                if (!transitionResult.success() || updated == null) {
                    LOGGER.warn("[AutoTransition] Failed to transition ticket {} for auto-assign: {}",
                        ticket.id(), transitionResult.errorMessage());
                    return;
                }

                // Then apply the assignment
                String assigneeName = result.assigneeName() != null
                    ? result.assigneeName()
                    : assigneeId.toString().substring(0, 8);
                Ticket assigned = updated.withAssignment(assigneeId, assigneeName);
                future = TicketRepository.INSTANCE.saveTicket(assigned)
                    .thenRun(() -> {
                        autoAssignedCount.incrementAndGet();
                        LOGGER.info("[AutoTransition] Auto-assigned ticket {} to {}",
                            ticket.id(), assigneeName);
                    });
            }

            default -> {
                return;
            }
        }

        future.exceptionally(e -> {
            LOGGER.error("[AutoTransition] Failed to persist transition for ticket {}", ticket.id(), e);
            return null;
        });
    }

    // ========================================================================
    // CONFIGURATION API
    // ========================================================================

    public void setAutoCloseDuration(Duration duration) {
        this.autoCloseDuration = duration != null ? duration : Duration.ofDays(7);
    }

    public Duration getAutoCloseDuration() {
        return autoCloseDuration;
    }

    public void setAutoCloseEnabled(boolean enabled) {
        this.autoCloseEnabled = enabled;
    }

    public boolean isAutoCloseEnabled() {
        return autoCloseEnabled;
    }

    public void setSlaEscalationEnabled(boolean enabled) {
        this.slaEscalationEnabled = enabled;
    }

    public boolean isSlaEscalationEnabled() {
        return slaEscalationEnabled;
    }

    public void setAutoAssignEnabled(boolean enabled) {
        this.autoAssignEnabled = enabled;
    }

    public boolean isAutoAssignEnabled() {
        return autoAssignEnabled;
    }

    // ========================================================================
    // METRICS API
    // ========================================================================

    public AutoTransitionMetrics getMetrics() {
        return new AutoTransitionMetrics(
            autoClosedCount.get(),
            escalatedCount.get(),
            autoAssignedCount.get(),
            totalRunCount.get(),
            lastRunTime,
            running
        );
    }

    // ========================================================================
    // INNER CLASSES
    // ========================================================================

    /**
     * Type of auto-transition (package-private, used only internally).
     */
    enum AutoTransitionType {
        AUTO_CLOSE,
        SLA_ESCALATION,
        AUTO_ASSIGN
    }

    /**
     * Result of an auto-transition check.
     */
    private record AutoTransitionResult(
        Ticket ticket,
        AutoTransitionType type,
        @Nullable TicketStatus newStatus,
        @Nullable TicketPriority newPriority,
        @Nullable UUID assigneeId,
        @Nullable String assigneeName,
        String reason
    ) {
        /** Creates a result without assignment info. */
        static AutoTransitionResult of(Ticket ticket, AutoTransitionType type,
                @Nullable TicketStatus newStatus, @Nullable TicketPriority newPriority, String reason) {
            return new AutoTransitionResult(ticket, type, newStatus, newPriority, null, null, reason);
        }

        /** Creates an assignment result. */
        static AutoTransitionResult forAssignment(Ticket ticket, UUID assigneeId,
                String assigneeName, String reason) {
            return new AutoTransitionResult(ticket, AutoTransitionType.AUTO_ASSIGN,
                TicketStatus.IN_PROGRESS, null, assigneeId, assigneeName, reason);
        }
    }

    /**
     * Metrics snapshot for the auto-transition service.
     */
    public record AutoTransitionMetrics(
        long autoClosedCount,
        long escalatedCount,
        long autoAssignedCount,
        long totalRunCount,
        @Nullable Instant lastRunTime,
        boolean running
    ) {}
}
