package com.devmod.mailbox.ticket;

import com.devmod.mailbox.ticket.TicketWorkflow.ActorRole;
import com.devmod.mailbox.ticket.TicketWorkflow.TransitionContext;
import com.devmod.mailbox.ticket.TicketWorkflow.TransitionResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TicketWorkflowTest {

    private final UUID reporterUuid = UUID.randomUUID();
    private final UUID modUuid = UUID.randomUUID();

    private Ticket createTicket(TicketStatus status) {
        return Ticket.builder(reporterUuid, TicketCategory.BUG, "Test Bug")
            .status(status)
            .createdAt(Instant.now().minusSeconds(60))
            .build();
    }

    private TransitionContext modContext() {
        return new TransitionContext(modUuid, "Moderator", ActorRole.MODERATOR, null, Instant.now());
    }

    private TransitionContext adminContext() {
        return new TransitionContext(UUID.randomUUID(), "Admin", ActorRole.ADMIN, null, Instant.now());
    }

    private TransitionContext reporterContext() {
        return new TransitionContext(reporterUuid, "Reporter", ActorRole.REPORTER, null, Instant.now());
    }

    @Test
    @DisplayName("OPEN -> ASSIGNED succeeds for moderator")
    void openToAssigned() {
        Ticket ticket = createTicket(TicketStatus.OPEN);
        TransitionResult result = TicketWorkflow.transition(ticket, TicketStatus.ASSIGNED, modContext());

        assertTrue(result.success());
        assertNotNull(result.updatedTicket());
        assertEquals(TicketStatus.ASSIGNED, result.updatedTicket().status());
        assertNotNull(result.event());
    }

    @Test
    @DisplayName("OPEN -> RESOLVED fails for reporter")
    void openToResolvedFailsForReporter() {
        Ticket ticket = createTicket(TicketStatus.OPEN);
        TransitionResult result = TicketWorkflow.transition(ticket, TicketStatus.RESOLVED, reporterContext());

        assertFalse(result.success());
        assertNotNull(result.errorMessage());
    }

    @Test
    @DisplayName("OPEN -> RESOLVED succeeds for admin")
    void openToResolvedAdmin() {
        Ticket ticket = createTicket(TicketStatus.OPEN);
        TransitionResult result = TicketWorkflow.transition(ticket, TicketStatus.RESOLVED, adminContext());

        assertTrue(result.success());
    }

    @Test
    @DisplayName("OPEN -> CLOSED succeeds for reporter (own ticket)")
    void openToClosedReporter() {
        Ticket ticket = createTicket(TicketStatus.OPEN);
        TransitionResult result = TicketWorkflow.transition(ticket, TicketStatus.CLOSED, reporterContext());

        assertTrue(result.success());
    }

    @Test
    @DisplayName("transition to same status is no-op failure")
    void sameStatusNoOp() {
        Ticket ticket = createTicket(TicketStatus.OPEN);
        TransitionResult result = TicketWorkflow.transition(ticket, TicketStatus.OPEN, modContext());

        assertFalse(result.success());
        assertTrue(result.errorMessage().contains("already in status"));
    }

    @Test
    @DisplayName("RESOLVED -> OPEN reporter can reopen own ticket")
    void resolvedToOpenReporter() {
        Ticket ticket = createTicket(TicketStatus.RESOLVED);
        TransitionResult result = TicketWorkflow.transition(ticket, TicketStatus.OPEN, reporterContext());

        assertTrue(result.success());
    }

    @Test
    @DisplayName("CLOSED -> OPEN only admin can reopen")
    void closedToOpenAdminOnly() {
        Ticket ticket = createTicket(TicketStatus.CLOSED);

        TransitionResult modResult = TicketWorkflow.transition(ticket, TicketStatus.OPEN, modContext());
        assertFalse(modResult.success());

        TransitionResult adminResult = TicketWorkflow.transition(ticket, TicketStatus.OPEN, adminContext());
        assertTrue(adminResult.success());
    }

    @Test
    @DisplayName("getValidTransitions returns correct set for role")
    void getValidTransitions() {
        Ticket openTicket = createTicket(TicketStatus.OPEN);

        Set<TicketStatus> modTransitions = TicketWorkflow.getValidTransitions(openTicket, ActorRole.MODERATOR);
        assertTrue(modTransitions.contains(TicketStatus.ASSIGNED));
        assertTrue(modTransitions.contains(TicketStatus.IN_PROGRESS));
        assertFalse(modTransitions.contains(TicketStatus.CLOSED)); // only reporter/admin

        Set<TicketStatus> reporterTransitions = TicketWorkflow.getValidTransitions(openTicket, ActorRole.REPORTER);
        assertTrue(reporterTransitions.contains(TicketStatus.CLOSED));
    }

    @Test
    @DisplayName("TransitionContext.system creates system context")
    void systemContext() {
        TransitionContext ctx = TransitionContext.system("Auto-escalation");
        assertEquals(ActorRole.SYSTEM, ctx.actorRole());
        assertEquals("System", ctx.actorName());
        assertEquals("Auto-escalation", ctx.reason());
    }

    @Test
    @DisplayName("IN_PROGRESS -> RESOLVED succeeds for moderator")
    void inProgressToResolved() {
        Ticket ticket = createTicket(TicketStatus.IN_PROGRESS);
        TransitionResult result = TicketWorkflow.transition(ticket, TicketStatus.RESOLVED, modContext());

        assertTrue(result.success());
        assertEquals(TicketStatus.RESOLVED, result.updatedTicket().status());
    }

    @Test
    @DisplayName("WorkflowEvent captures time in previous state")
    void workflowEventTimeInState() {
        Ticket ticket = createTicket(TicketStatus.OPEN);
        TransitionResult result = TicketWorkflow.transition(ticket, TicketStatus.ASSIGNED, modContext());

        assertTrue(result.success());
        assertNotNull(result.event());
        assertFalse(result.event().timeInPreviousState().isNegative());
    }

    @Test
    @DisplayName("checkAutoEscalation returns empty for URGENT tickets")
    void autoEscalationUrgent() {
        Ticket urgentTicket = Ticket.builder(reporterUuid, TicketCategory.BUG, "Urgent Bug")
            .priority(TicketPriority.URGENT)
            .createdAt(Instant.now().minusSeconds(3600 * 48))
            .build();

        assertTrue(TicketWorkflow.checkAutoEscalation(urgentTicket).isEmpty());
    }
}
