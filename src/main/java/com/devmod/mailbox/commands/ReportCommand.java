package com.devmod.mailbox.commands;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

import javax.annotation.Nonnull;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import com.devmod.mailbox.ticket.Ticket;
import com.devmod.mailbox.ticket.TicketCategory;
import com.devmod.mailbox.ticket.TicketManager;

/**
 * In-game command for submitting support tickets and player reports.
 *
 * Usage:
 *   /report bug <message>           - Report a bug
 *   /report suggestion <message>    - Submit a suggestion
 *   /report question <message>      - Ask a question
 *   /report player <player> <reason> - Report a player
 */
public final class ReportCommand {

    private ReportCommand() {}

    /** Helper to create non-null Component for sendSystemMessage. */
    @Nonnull
    private static Component msg(String text, ChatFormatting... styles) {
        return Objects.requireNonNull(Component.literal(Objects.requireNonNull(text))
            .withStyle(Objects.requireNonNull(styles)));
    }

    /**
     * Register the /report command.
     */
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("report")
                .requires(source -> source.isPlayer())
                // /report bug <message>
                .then(Commands.literal("bug")
                    .then(Commands.argument("message", Objects.requireNonNull(StringArgumentType.greedyString()))
                        .executes(ctx -> submitTicket(ctx, TicketCategory.BUG))))
                // /report suggestion <message>
                .then(Commands.literal("suggestion")
                    .then(Commands.argument("message", Objects.requireNonNull(StringArgumentType.greedyString()))
                        .executes(ctx -> submitTicket(ctx, TicketCategory.SUGGESTION))))
                // /report question <message>
                .then(Commands.literal("question")
                    .then(Commands.argument("message", Objects.requireNonNull(StringArgumentType.greedyString()))
                        .executes(ctx -> submitTicket(ctx, TicketCategory.QUESTION))))
                // /report other <message>
                .then(Commands.literal("other")
                    .then(Commands.argument("message", Objects.requireNonNull(StringArgumentType.greedyString()))
                        .executes(ctx -> submitTicket(ctx, TicketCategory.OTHER))))
                // /report player <player> <reason>
                .then(Commands.literal("player")
                    .then(Commands.argument("target", Objects.requireNonNull(EntityArgument.player()))
                        .then(Commands.argument("reason", Objects.requireNonNull(StringArgumentType.greedyString()))
                            .suggests(REASON_SUGGESTIONS)
                            .executes(ReportCommand::reportPlayer))))
                // /report list - Show player's own tickets
                .then(Commands.literal("list")
                    .executes(ReportCommand::listOwnTickets))
        );
    }

    /**
     * Suggestion provider for common report reasons.
     */
    private static final SuggestionProvider<CommandSourceStack> REASON_SUGGESTIONS =
        (context, builder) -> {
            return SharedSuggestionProvider.suggest(new String[]{
                "cheating",
                "harassment",
                "spam",
                "griefing",
                "inappropriate_name",
                "exploiting",
                "other"
            }, Objects.requireNonNull(builder));
        };

    /**
     * Submit a general ticket (bug, suggestion, question, other).
     */
    private static int submitTicket(CommandContext<CommandSourceStack> ctx, TicketCategory category)
            throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        String message = StringArgumentType.getString(ctx, "message");

        if (message.length() < 10) {
            player.sendSystemMessage(msg("Please provide a more detailed message (at least 10 characters).",
                ChatFormatting.RED));
            return 0;
        }

        // Truncate subject for display, use full message as description
        String subject = message.length() > 50 ? message.substring(0, 47) + "..." : message;

        player.sendSystemMessage(msg("Submitting your " + category.getDisplayName().toLowerCase(Locale.ROOT) + "...",
            ChatFormatting.GRAY));

        TicketManager.INSTANCE.createTicket(
            player.getUUID(),
            player.getName().getString(),
            category,
            subject,
            message
        ).thenAccept(ticket -> {
            player.sendSystemMessage(msg("=================================", ChatFormatting.GREEN));
            player.sendSystemMessage(msg("Ticket submitted successfully!", ChatFormatting.GREEN, ChatFormatting.BOLD));
            player.sendSystemMessage(msg("Ticket ID: " + ticket.id().toString().substring(0, 8), ChatFormatting.GRAY));
            player.sendSystemMessage(msg("Category: " + category.getDisplayName(), ChatFormatting.GRAY));
            player.sendSystemMessage(msg("You will receive a mailbox notification when reviewed.", ChatFormatting.YELLOW));
            player.sendSystemMessage(msg("=================================", ChatFormatting.GREEN));
        }).exceptionally(e -> {
            player.sendSystemMessage(msg("Failed to submit ticket. Please try again later.", ChatFormatting.RED));
            return null;
        });

        return 1;
    }

    /**
     * Report another player.
     */
    private static int reportPlayer(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer reporter = ctx.getSource().getPlayerOrException();
        ServerPlayer target = EntityArgument.getPlayer(ctx, "target");
        String reason = StringArgumentType.getString(ctx, "reason");

        // Can't report yourself
        if (reporter.getUUID().equals(target.getUUID())) {
            reporter.sendSystemMessage(msg("You cannot report yourself.", ChatFormatting.RED));
            return 0;
        }

        if (reason.length() < 5) {
            reporter.sendSystemMessage(msg("Please provide a reason (at least 5 characters).", ChatFormatting.RED));
            return 0;
        }

        reporter.sendSystemMessage(msg("Submitting player report...", ChatFormatting.GRAY));

        TicketManager.INSTANCE.createPlayerReport(
            reporter.getUUID(),
            reporter.getName().getString(),
            target.getUUID(),
            target.getName().getString(),
            reason,
            "Reported player: " + target.getName().getString() + "\nReason: " + reason
        ).thenAccept(ticket -> {
            reporter.sendSystemMessage(msg("=================================", ChatFormatting.GOLD));
            reporter.sendSystemMessage(msg("Player report submitted!", ChatFormatting.GOLD, ChatFormatting.BOLD));
            reporter.sendSystemMessage(msg("Reported: " + target.getName().getString(), ChatFormatting.GRAY));
            reporter.sendSystemMessage(msg("Report ID: " + ticket.id().toString().substring(0, 8), ChatFormatting.GRAY));
            reporter.sendSystemMessage(msg("Staff will review this report shortly.", ChatFormatting.YELLOW));
            reporter.sendSystemMessage(msg("=================================", ChatFormatting.GOLD));
        }).exceptionally(e -> {
            reporter.sendSystemMessage(msg("Failed to submit report. Please try again later.", ChatFormatting.RED));
            return null;
        });

        return 1;
    }

    /* List the player's own tickets. */
    private static int listOwnTickets(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();

        player.sendSystemMessage(msg("Loading your tickets...", ChatFormatting.GRAY));

        TicketManager.INSTANCE.getPlayerTickets(player.getUUID())
            .thenAccept(tickets -> sendTicketList(player, tickets))
            .exceptionally(e -> {
                player.sendSystemMessage(msg("Failed to load tickets.", ChatFormatting.RED));
                return null;
            });

        return 1;
    }

    private static void sendTicketList(ServerPlayer player, List<Ticket> tickets) {
        if (tickets == null || tickets.isEmpty()) {
            player.sendSystemMessage(msg("You have no submitted tickets.", ChatFormatting.YELLOW));
            return;
        }

        player.sendSystemMessage(msg("=== Your Tickets ===", ChatFormatting.GOLD, ChatFormatting.BOLD));

        for (Ticket ticket : tickets) {
            player.sendSystemMessage(msg(formatTicketLine(ticket), statusColor(ticket)));
        }

        player.sendSystemMessage(msg("Total: " + tickets.size() + " tickets", ChatFormatting.GRAY));
    }

    private static String formatTicketLine(Ticket ticket) {
        String shortId = ticket.id().toString().substring(0, 8);
        String subject = ticket.subject();
        String preview = subject.length() > 30 ? subject.substring(0, 27) + "..." : subject;

        return "[" + shortId + "] " + ticket.status().getDisplayName() + " - " + preview;
    }

    private static ChatFormatting statusColor(Ticket ticket) {
        return switch (ticket.status()) {
            case OPEN -> ChatFormatting.WHITE;
            case ASSIGNED, IN_PROGRESS -> ChatFormatting.YELLOW;
            case RESOLVED -> ChatFormatting.GREEN;
            case CLOSED -> ChatFormatting.GRAY;
        };
    }
}
