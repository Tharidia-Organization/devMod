package com.devmod.mailbox.network;

import java.util.List;
import java.util.Objects;

import net.minecraft.server.level.ServerPlayer;

import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import com.devmod.mailbox.network.payload.TicketCreatePayload;
import com.devmod.mailbox.network.payload.TicketSyncPayload;
import com.devmod.mailbox.network.payload.TicketSyncRequestPayload;
import com.devmod.mailbox.ticket.Ticket;
import com.devmod.mailbox.ticket.TicketManager;
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
