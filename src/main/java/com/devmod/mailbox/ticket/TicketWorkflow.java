package com.devmod.mailbox.ticket;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiPredicate;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * P1-005: Ticket workflow state machine.
 *
 * Implements a formal state machine for ticket lifecycle management with:
 * - Role-based transition guards
 * - Entry/exit actions for each state
 * - SLA tracking and escalation
 * - Audit trail generation
 *
 * <p>State Diagram:
 * <pre>
 *                  ┌─────────────────────────────────────────────┐
 *                  │                                             ▼
 *  [OPEN] ───────► [ASSIGNED] ───────► [IN_PROGRESS] ───────► [RESOLVED] ───► [CLOSED]
 *    │                 │                      │                    │              │
 *    │                 │                      │                    │              │
 *    ▼                 ▼                      ▼                    ▼              │
 *  [CLOSED]◄─────────────────────────────────────────────────[REOPEN]◄──────────┘
 * </pre>
 */
public final class TicketWorkflow {

    private static final Logger LOGGER = LoggerFactory.getLogger(TicketWorkflow.class);

    // ============================================================================
    // ACTOR ROLES
    // ============================================================================

    /**
     * Role of the actor attempting a transition.
     */
    public enum ActorRole {
        /** The ticket reporter (original submitter) */
        REPORTER,
        /** A moderator/staff member */
        MODERATOR,
        /** An admin with elevated privileges */
        ADMIN,
        /** System-triggered action (e.g., SLA escalation) */
        SYSTEM
    }

    // ============================================================================
    // TRANSITION DEFINITION
    // ============================================================================

    /**
     * Represents a valid state transition with guards and actions.
     */
    public record Transition(
        TicketStatus from,
        TicketStatus to,
        Set<ActorRole> allowedRoles,
        @Nullable BiPredicate<Ticket, TransitionContext> guard,
        String description
    ) {
        public Transition {
            Objects.requireNonNull(from, "from");
            Objects.requireNonNull(to, "to");
            allowedRoles = allowedRoles != null ? EnumSet.copyOf(allowedRoles) : EnumSet.allOf(ActorRole.class);
        }

        /**
         * Check if this transition is allowed for the given context.
         */
        public boolean isAllowed(Ticket ticket, TransitionContext ctx) {
            if (!allowedRoles.contains(ctx.actorRole())) {
                return false;
            }
            if (guard != null && !guard.test(ticket, ctx)) {
                return false;
            }
            return true;
        }
    }

    /**
     * Context for a transition attempt.
     */
    public record TransitionContext(
        UUID actorUuid,
        String actorName,
        ActorRole actorRole,
        @Nullable String reason,
        Instant timestamp
    ) {
        public TransitionContext {
            Objects.requireNonNull(actorUuid, "actorUuid");
            Objects.requireNonNull(actorName, "actorName");
            Objects.requireNonNull(actorRole, "actorRole");
            timestamp = timestamp != null ? timestamp : Instant.now();
        }

        public static TransitionContext system(String reason) {
            return new TransitionContext(
                UUID.fromString("00000000-0000-0000-0000-000000000000"),
                "System",
                ActorRole.SYSTEM,
                reason,
                Instant.now()
            );
        }
    }

    /**
     * Result of a transition attempt.
     */
    public record TransitionResult(
        boolean success,
        @Nullable Ticket updatedTicket,
        @Nullable String errorMessage,
        @Nullable WorkflowEvent event
    ) {
        public static TransitionResult success(Ticket ticket, WorkflowEvent event) {
            return new TransitionResult(true, ticket, null, event);
        }

        public static TransitionResult failure(String message) {
            return new TransitionResult(false, null, message, null);
        }
    }

    /**
     * Workflow event for audit logging.
     */
    public record WorkflowEvent(
        UUID ticketId,
        TicketStatus fromStatus,
        TicketStatus toStatus,
        UUID actorUuid,
        String actorName,
        ActorRole actorRole,
        @Nullable String reason,
        Instant timestamp,
        Duration timeInPreviousState
    ) {}

    // ============================================================================
    // TRANSITION TABLE
    // ============================================================================

    private static final Map<TicketStatus, Set<Transition>> TRANSITION_TABLE;

    static {
        TRANSITION_TABLE = new EnumMap<>(TicketStatus.class);

        // From OPEN
        TRANSITION_TABLE.put(TicketStatus.OPEN, Set.of(
            new Transition(
                TicketStatus.OPEN, TicketStatus.ASSIGNED,
                EnumSet.of(ActorRole.MODERATOR, ActorRole.ADMIN, ActorRole.SYSTEM),
                null,
                "Assign ticket to moderator"
            ),
            new Transition(
                TicketStatus.OPEN, TicketStatus.IN_PROGRESS,
                EnumSet.of(ActorRole.MODERATOR, ActorRole.ADMIN),
                null,
                "Start working on ticket directly"
            ),
            new Transition(
                TicketStatus.OPEN, TicketStatus.RESOLVED,
                EnumSet.of(ActorRole.ADMIN),
                null,
                "Admin quick-resolve"
            ),
            new Transition(
                TicketStatus.OPEN, TicketStatus.CLOSED,
                EnumSet.of(ActorRole.REPORTER, ActorRole.ADMIN),
                (ticket, ctx) -> ctx.actorRole() == ActorRole.ADMIN ||
                    ticket.reporterUuid().equals(ctx.actorUuid()),
                "Reporter/admin closes without resolution"
            )
        ));

        // From ASSIGNED
        TRANSITION_TABLE.put(TicketStatus.ASSIGNED, Set.of(
            new Transition(
                TicketStatus.ASSIGNED, TicketStatus.IN_PROGRESS,
                EnumSet.of(ActorRole.MODERATOR, ActorRole.ADMIN),
                (ticket, ctx) -> {
                    UUID assignee = ticket.assignedTo();
                    return assignee == null ||
                        assignee.equals(ctx.actorUuid()) ||
                        ctx.actorRole() == ActorRole.ADMIN;
                },
                "Start working (assignee or admin)"
            ),
            new Transition(
                TicketStatus.ASSIGNED, TicketStatus.RESOLVED,
                EnumSet.of(ActorRole.MODERATOR, ActorRole.ADMIN),
                null,
                "Direct resolution"
            ),
            new Transition(
                TicketStatus.ASSIGNED, TicketStatus.OPEN,
                EnumSet.of(ActorRole.MODERATOR, ActorRole.ADMIN),
                null,
                "Unassign ticket"
            ),
            new Transition(
                TicketStatus.ASSIGNED, TicketStatus.CLOSED,
                EnumSet.of(ActorRole.ADMIN),
                null,
                "Admin closes without resolution"
            )
        ));

        // From IN_PROGRESS
        TRANSITION_TABLE.put(TicketStatus.IN_PROGRESS, Set.of(
            new Transition(
                TicketStatus.IN_PROGRESS, TicketStatus.RESOLVED,
                EnumSet.of(ActorRole.MODERATOR, ActorRole.ADMIN),
                null,
                "Mark as resolved"
            ),
            new Transition(
                TicketStatus.IN_PROGRESS, TicketStatus.ASSIGNED,
                EnumSet.of(ActorRole.MODERATOR, ActorRole.ADMIN),
                null,
                "Pause work / reassign"
            ),
            new Transition(
                TicketStatus.IN_PROGRESS, TicketStatus.CLOSED,
                EnumSet.of(ActorRole.ADMIN),
                null,
                "Admin force close"
            )
        ));

        // From RESOLVED
        TRANSITION_TABLE.put(TicketStatus.RESOLVED, Set.of(
            new Transition(
                TicketStatus.RESOLVED, TicketStatus.CLOSED,
                EnumSet.of(ActorRole.REPORTER, ActorRole.MODERATOR, ActorRole.ADMIN, ActorRole.SYSTEM),
                null,
                "Confirm resolution and close"
            ),
            new Transition(
                TicketStatus.RESOLVED, TicketStatus.OPEN,
                EnumSet.of(ActorRole.REPORTER, ActorRole.ADMIN),
                (ticket, ctx) -> ctx.actorRole() == ActorRole.ADMIN ||
                    ticket.reporterUuid().equals(ctx.actorUuid()),
                "Reopen (reporter dissatisfied or admin override)"
            )
        ));

        // From CLOSED
        TRANSITION_TABLE.put(TicketStatus.CLOSED, Set.of(
            new Transition(
                TicketStatus.CLOSED, TicketStatus.OPEN,
                EnumSet.of(ActorRole.ADMIN),
                null,
                "Admin reopen closed ticket"
            )
        ));
    }

    // ============================================================================
    // SLA CONFIGURATION
    // ============================================================================

    /**
     * SLA thresholds by priority.
     */
    public static final Map<TicketPriority, Duration> RESPONSE_SLA = Map.of(
        TicketPriority.LOW, Duration.ofHours(72),
        TicketPriority.NORMAL, Duration.ofHours(24),
        TicketPriority.HIGH, Duration.ofHours(4),
        TicketPriority.URGENT, Duration.ofHours(1)
    );

    public static final Map<TicketPriority, Duration> RESOLUTION_SLA = Map.of(
        TicketPriority.LOW, Duration.ofDays(14),
        TicketPriority.NORMAL, Duration.ofDays(7),
        TicketPriority.HIGH, Duration.ofDays(2),
        TicketPriority.URGENT, Duration.ofHours(24)
    );

    // ============================================================================
    // WORKFLOW OPERATIONS
    // ============================================================================

    private TicketWorkflow() {} // Static utility class

    /**
     * Check if a transition is valid for the given ticket and context.
     *
     * @param ticket The ticket to transition
     * @param targetStatus The target status
     * @param ctx The transition context
     * @return Optional containing the valid transition, or empty if not allowed
     */
    public static Optional<Transition> findValidTransition(
            Ticket ticket,
            TicketStatus targetStatus,
            TransitionContext ctx) {

        Set<Transition> transitions = TRANSITION_TABLE.get(ticket.status());
        if (transitions == null) {
            return Optional.empty();
        }

        return transitions.stream()
            .filter(t -> t.to() == targetStatus)
            .filter(t -> t.isAllowed(ticket, ctx))
            .findFirst();
    }

    /**
     * Attempt to transition a ticket to a new status.
     *
     * @param ticket The ticket to transition
     * @param targetStatus The target status
     * @param ctx The transition context
     * @return TransitionResult indicating success or failure
     */
    public static TransitionResult transition(
            Ticket ticket,
            TicketStatus targetStatus,
            TransitionContext ctx) {

        // Same status is no-op
        if (ticket.status() == targetStatus) {
            return TransitionResult.failure("Ticket is already in status: " + targetStatus);
        }

        // Find valid transition
        Optional<Transition> transition = findValidTransition(ticket, targetStatus, ctx);
        if (transition.isEmpty()) {
            LOGGER.warn("[TicketWorkflow] Invalid transition: {} -> {} by {} ({})",
                ticket.status(), targetStatus, ctx.actorName(), ctx.actorRole());
            return TransitionResult.failure(String.format(
                "Transition from %s to %s not allowed for role %s",
                ticket.status(), targetStatus, ctx.actorRole()
            ));
        }

        // Calculate time in previous state (use updatedAt if available, else createdAt)
        Duration timeInState = Duration.between(
            ticket.updatedAt() != null ? ticket.updatedAt() : ticket.createdAt(),
            ctx.timestamp()
        );

        // Create workflow event
        WorkflowEvent event = new WorkflowEvent(
            ticket.id(),
            ticket.status(),
            targetStatus,
            ctx.actorUuid(),
            ctx.actorName(),
            ctx.actorRole(),
            ctx.reason(),
            ctx.timestamp(),
            timeInState
        );

        // Apply transition
        Ticket updated = ticket.withStatus(targetStatus);

        LOGGER.info("[TicketWorkflow] Ticket {} transitioned: {} -> {} by {} ({}) - {}",
            ticket.id(), ticket.status(), targetStatus,
            ctx.actorName(), ctx.actorRole(),
            transition.get().description());

        return TransitionResult.success(updated, event);
    }

    // ============================================================================
    // SLA OPERATIONS
    // ============================================================================

    /**
     * Check if a ticket has breached its response SLA.
     *
     * @param ticket The ticket to check
     * @return true if SLA is breached
     */
    public static boolean isResponseSlaBreached(Ticket ticket) {
        if (ticket.status() != TicketStatus.OPEN) {
            return false; // Already responded
        }

        Duration sla = RESPONSE_SLA.get(ticket.priority());
        if (sla == null) {
            return false;
        }

        Duration elapsed = Duration.between(ticket.createdAt(), Instant.now());
        return elapsed.compareTo(sla) > 0;
    }

    /**
     * Check if a ticket has breached its resolution SLA.
     *
     * @param ticket The ticket to check
     * @return true if SLA is breached
     */
    public static boolean isResolutionSlaBreached(Ticket ticket) {
        if (ticket.status() == TicketStatus.RESOLVED || ticket.status() == TicketStatus.CLOSED) {
            return false;
        }

        Duration sla = RESOLUTION_SLA.get(ticket.priority());
        if (sla == null) {
            return false;
        }

        Duration elapsed = Duration.between(ticket.createdAt(), Instant.now());
        return elapsed.compareTo(sla) > 0;
    }

    /**
     * Check if a ticket should be auto-escalated based on SLA.
     *
     * @param ticket The ticket
     * @return The new priority if escalation is needed, or empty
     */
    public static Optional<TicketPriority> checkAutoEscalation(Ticket ticket) {
        if (ticket.priority() == TicketPriority.URGENT) {
            return Optional.empty(); // Already at max
        }

        if (isResolutionSlaBreached(ticket)) {
            TicketPriority escalated = switch (ticket.priority()) {
                case LOW -> TicketPriority.NORMAL;
                case NORMAL -> TicketPriority.HIGH;
                case HIGH -> TicketPriority.URGENT;
                case URGENT -> TicketPriority.URGENT;
            };
            return Optional.of(escalated);
        }

        return Optional.empty();
    }

    /**
     * Get all valid transitions from the current status for the given role.
     *
     * @param ticket The ticket
     * @param actorRole The actor's role
     * @return Set of valid target statuses
     */
    public static Set<TicketStatus> getValidTransitions(Ticket ticket, ActorRole actorRole) {
        Set<Transition> transitions = TRANSITION_TABLE.get(ticket.status());
        if (transitions == null) {
            return Set.of();
        }

        Set<TicketStatus> valid = EnumSet.noneOf(TicketStatus.class);

        for (Transition t : transitions) {
            if (t.allowedRoles().contains(actorRole)) {
                // Don't check guard for listing - just role check
                valid.add(t.to());
            }
        }

        return valid;
    }

    /**
     * Get remaining time until response SLA breach.
     *
     * @param ticket The ticket
     * @return Remaining duration, or Duration.ZERO if already breached
     */
    public static Duration getTimeUntilResponseSlaBreach(Ticket ticket) {
        if (ticket.status() != TicketStatus.OPEN) {
            return Duration.ofDays(999); // Not applicable
        }

        Duration sla = RESPONSE_SLA.get(ticket.priority());
        if (sla == null) {
            return Duration.ofDays(999);
        }

        Duration elapsed = Duration.between(ticket.createdAt(), Instant.now());
        Duration remaining = sla.minus(elapsed);
        return remaining.isNegative() ? Duration.ZERO : remaining;
    }

    /**
     * Get remaining time until resolution SLA breach.
     *
     * @param ticket The ticket
     * @return Remaining duration, or Duration.ZERO if already breached
     */
    public static Duration getTimeUntilResolutionSlaBreach(Ticket ticket) {
        if (ticket.status() == TicketStatus.RESOLVED || ticket.status() == TicketStatus.CLOSED) {
            return Duration.ofDays(999); // Not applicable
        }

        Duration sla = RESOLUTION_SLA.get(ticket.priority());
        if (sla == null) {
            return Duration.ofDays(999);
        }

        Duration elapsed = Duration.between(ticket.createdAt(), Instant.now());
        Duration remaining = sla.minus(elapsed);
        return remaining.isNegative() ? Duration.ZERO : remaining;
    }

    // ============================================================================
    // BATCH OPERATIONS
    // ============================================================================

    /**
     * Find all tickets that need SLA escalation.
     *
     * @param tickets List of tickets to check
     * @return Map of ticket ID to new priority
     */
    public static Map<UUID, TicketPriority> findTicketsNeedingEscalation(Iterable<Ticket> tickets) {
        Map<UUID, TicketPriority> escalations = new java.util.HashMap<>();

        for (Ticket ticket : tickets) {
            checkAutoEscalation(ticket).ifPresent(newPriority ->
                escalations.put(ticket.id(), newPriority)
            );
        }

        return escalations;
    }

    /**
     * Find all tickets breaching response SLA.
     *
     * @param tickets List of tickets to check
     * @return Set of ticket IDs breaching SLA
     */
    public static Set<UUID> findTicketsBreachingResponseSla(Iterable<Ticket> tickets) {
        Set<UUID> breaching = new java.util.HashSet<>();

        for (Ticket ticket : tickets) {
            if (isResponseSlaBreached(ticket)) {
                breaching.add(ticket.id());
            }
        }

        return breaching;
    }
}
