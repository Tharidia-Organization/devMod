package com.devmod.mailbox.network;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import net.minecraft.server.level.ServerPlayer;

import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import com.devmod.mailbox.MailboxPermissions;
import com.devmod.mailbox.network.payload.TicketActionPayload;
import com.devmod.mailbox.network.payload.TicketCreatePayload;
import com.devmod.mailbox.network.payload.TicketSyncPayload;
import com.devmod.mailbox.network.payload.TicketSyncRequestPayload;
import com.devmod.mailbox.ticket.Ticket;
import com.devmod.mailbox.ticket.TicketManager;
import com.devmod.mailbox.ticket.TicketStatus;
import com.devmod.network.handlers.NetworkHandlerBase;

/**
 * Server-side handlers for ticket sync and creation.
 */
public final class TicketNetworkHandler extends NetworkHandlerBase {

    private TicketNetworkHandler() {}

    public static void handleTicketCreate(TicketCreatePayload payload, IPayloadContext context) {
        observeFuture(context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }

            String subject = payload.subject() != null ? payload.subject().trim() : "";
            String description = payload.description() != null ? payload.description().trim() : "";
            if (subject.isBlank()) {
                subject = "Ticket";
            }

            observeFuture(TicketManager.INSTANCE.createTicket(
                player.getUUID(),
                player.getName().getString(),
                payload.category(),
                subject,
                description.isBlank() ? null : description
            ).thenAccept(ticket -> sendTicketSync(player)), "ticket create");
        }), "ticket create enqueue");
    }

    public static void handleTicketAction(TicketActionPayload payload, IPayloadContext context) {
        observeFuture(context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }

            UUID ticketId = payload.ticketId();
            if (ticketId == null) {
                return;
            }

            boolean isAdmin = MailboxPermissions.INSTANCE.isAdmin(player.getUUID(), player);
            observeFuture(TicketManager.INSTANCE.getTicket(ticketId).thenCompose(opt -> {
                if (opt.isEmpty()) {
                    return java.util.concurrent.CompletableFuture.completedFuture(null);
                }

                Ticket ticket = opt.get();
                if (!isAdmin && !ticket.reporterUuid().equals(player.getUUID())) {
                    LOGGER.warn("[TicketNetwork] Player {} attempted to modify ticket {}",
                        player.getName().getString(), ticketId);
                    return java.util.concurrent.CompletableFuture.completedFuture(null);
                }

                return switch (payload.action()) {
                    case ADD_COMMENT -> handleAddComment(payload, player, ticket);
                    case UPDATE_STATUS -> handleUpdateStatus(payload, player, ticket, isAdmin);
                };
            }), "ticket action");
        }), "ticket action enqueue");
    }

    public static void handleTicketSyncRequest(TicketSyncRequestPayload payload, IPayloadContext context) {
        observeFuture(context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                sendTicketSync(player);
            }
        }), "ticket sync request");
    }

    public static void sendTicketSync(ServerPlayer player) {
        if (player == null) {
            return;
        }

        observeFuture(TicketManager.INSTANCE.getPlayerTickets(player.getUUID()).thenAccept(tickets -> {
            List<TicketSyncPayload.TicketData> data = tickets.stream()
                .map(TicketNetworkHandler::toTicketData)
                .toList();
            PacketDistributor.sendToPlayer(Objects.requireNonNull(player), new TicketSyncPayload(data));
        }), "ticket sync");
    }

    private static java.util.concurrent.CompletableFuture<Void> handleAddComment(
            TicketActionPayload payload,
            ServerPlayer player,
            Ticket ticket) {
        String comment = payload.comment();
        if (comment == null || comment.isBlank()) {
            return java.util.concurrent.CompletableFuture.completedFuture(null);
        }

        String trimmed = comment.trim();
        return TicketManager.INSTANCE.addComment(
            ticket.id(),
            player.getUUID(),
            player.getName().getString(),
            trimmed,
            false
        ).thenAccept(saved -> sendTicketSync(player));
    }

    private static java.util.concurrent.CompletableFuture<Void> handleUpdateStatus(
            TicketActionPayload payload,
            ServerPlayer player,
            Ticket ticket,
            boolean isAdmin) {
        TicketStatus newStatus = parseStatus(payload.statusId());
        if (newStatus == null) {
            return java.util.concurrent.CompletableFuture.completedFuture(null);
        }

        if (!isAdmin && !isPlayerStatusAllowed(ticket.status(), newStatus)) {
            return java.util.concurrent.CompletableFuture.completedFuture(null);
        }

        return TicketManager.INSTANCE.updateStatus(
            ticket.id(),
            newStatus,
            player.getName().getString()
        ).thenAccept(updated -> sendTicketSync(player));
    }

    private static TicketStatus parseStatus(String statusId) {
        if (statusId == null || statusId.isBlank()) {
            return null;
        }
        try {
            return TicketStatus.valueOf(statusId.toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static boolean isPlayerStatusAllowed(TicketStatus current, TicketStatus target) {
        if (target == TicketStatus.CLOSED) {
            return current != TicketStatus.CLOSED;
        }
        if (target == TicketStatus.OPEN) {
            return current == TicketStatus.CLOSED || current == TicketStatus.RESOLVED;
        }
        return false;
    }

    private static TicketSyncPayload.TicketData toTicketData(Ticket ticket) {
        return new TicketSyncPayload.TicketData(
            ticket.id(),
            ticket.category(),
            ticket.priority(),
            ticket.status(),
            ticket.subject(),
            ticket.description(),
            ticket.createdAt(),
            ticket.updatedAt(),
            0
        );
    }
}
