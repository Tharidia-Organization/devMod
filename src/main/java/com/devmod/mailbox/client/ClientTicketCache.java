package com.devmod.mailbox.client;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import com.devmod.mailbox.ticket.TicketCategory;
import com.devmod.mailbox.ticket.TicketPriority;
import com.devmod.mailbox.ticket.TicketStatus;
/**
 * Client-side cache for ticket data.
 * Stores ticket information received from the server.
 */
@OnlyIn(Dist.CLIENT)
public final class ClientTicketCache {
    private static final List<TicketData> tickets = new ArrayList<>();
    private static long lastSyncTime = 0;

    private ClientTicketCache() {}

    /**
     * Sync tickets from server.
     */
    public static void sync(List<TicketData> serverTickets) {
        tickets.clear();
        tickets.addAll(serverTickets);
        tickets.sort(Comparator.comparing(TicketData::createdAt).reversed());
        lastSyncTime = System.currentTimeMillis();
    }

    /**
     * Add a single ticket (optimistic update).
     */
    public static void addTicket(TicketData ticket) {
        tickets.add(0, ticket); // Add to front (newest first)
    }

    /**
     * Update ticket status.
     */
    public static void updateStatus(UUID ticketId, TicketStatus newStatus) {
        for (int i = 0; i < tickets.size(); i++) {
            TicketData t = tickets.get(i);
            if (t.id().equals(ticketId)) {
                tickets.set(i, new TicketData(
                    t.id(), t.category(), t.priority(), newStatus,
                    t.subject(), t.description(), t.createdAt(), Instant.now(),
                    t.commentCount()
                ));
                break;
            }
        }
    }

    /**
     * Get all cached tickets.
     */
    public static List<TicketData> getTickets() {
        return Collections.unmodifiableList(tickets);
    }

    /**
     * Get tickets by status.
     */
    public static List<TicketData> getTicketsByStatus(TicketStatus status) {
        return tickets.stream()
            .filter(t -> t.status() == status)
            .toList();
    }

    /**
     * Get open ticket count (open, assigned, or in progress).
     */
    public static int getOpenCount() {
        return (int) tickets.stream()
            .filter(TicketData::isActive)
            .count();
    }

    /**
     * Get total ticket count.
     */
    public static int getTotalCount() {
        return tickets.size();
    }

    /**
     * Find ticket by ID.
     */
    @Nullable
    public static TicketData getTicket(UUID id) {
        return tickets.stream()
            .filter(t -> t.id().equals(id))
            .findFirst()
            .orElse(null);
    }

    /**
     * Clear the cache.
     */
    public static void clear() {
        tickets.clear();
        lastSyncTime = 0;
    }

    /**
     * Check if cache needs refresh (older than 60 seconds).
     */
    public static boolean needsRefresh() {
        return System.currentTimeMillis() - lastSyncTime > 60_000;
    }

    /**
     * Lightweight ticket data for client display.
     */
    public record TicketData(
        UUID id,
        TicketCategory category,
        TicketPriority priority,
        TicketStatus status,
        String subject,
        @Nullable String description,
        Instant createdAt,
        @Nullable Instant updatedAt,
        int commentCount
    ) {
        /**
         * Check if ticket is open, assigned, or in progress.
         */
        public boolean isActive() {
            return status == TicketStatus.OPEN || status == TicketStatus.ASSIGNED || status == TicketStatus.IN_PROGRESS;
        }
    }
}
