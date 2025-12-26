package com.devmod.telemetry.dungeon;

import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.logging.LogUtils;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;

import com.devmod.actions.ActionCategory;
import com.devmod.actions.ActionCommandInvoker;
import com.devmod.actions.ActionContext;
import com.devmod.actions.ActionIds;
import com.devmod.actions.ActionOrigin;
import com.devmod.actions.ActionPreconditions;
import com.devmod.actions.ActionRegistry;
import com.devmod.actions.RadialAction;
public class DungeonCommand {
    private static final Logger LOGGER = LogUtils.getLogger();

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("devmod")
                .then(Commands.literal("dungeon")
                    .requires(source -> source.hasPermission(2)) // Requires op level 2
                    .then(Commands.literal("start")
                        .then(Commands.argument("dungeon_id", Objects.requireNonNull(StringArgumentType.word()))
                            .executes(ctx -> ActionCommandInvoker.invoke(ActionIds.DUNGEON_START, ctx))))
                    .then(Commands.literal("end")
                        .then(Commands.argument("outcome", Objects.requireNonNull(StringArgumentType.word()))
                            .suggests(DungeonCommand::suggestOutcomes)
                            .executes(ctx -> ActionCommandInvoker.invoke(ActionIds.DUNGEON_END, ctx))
                            .then(Commands.argument("kills", Objects.requireNonNull(IntegerArgumentType.integer(0)))
                                .executes(ctx -> ActionCommandInvoker.invoke(ActionIds.DUNGEON_END, ctx))
                                .then(Commands.argument("deaths", Objects.requireNonNull(IntegerArgumentType.integer(0)))
                                    .executes(ctx -> ActionCommandInvoker.invoke(ActionIds.DUNGEON_END, ctx))
                                    .then(Commands.argument("reward_count", Objects.requireNonNull(IntegerArgumentType.integer(0)))
                                        .executes(ctx -> ActionCommandInvoker.invoke(ActionIds.DUNGEON_END, ctx)))))))
                    .then(Commands.literal("status")
                        .executes(ctx -> ActionCommandInvoker.invoke(ActionIds.DUNGEON_STATUS, ctx)))
                    .executes(ctx -> ActionCommandInvoker.invoke(ActionIds.DUNGEON_HELP, ctx)))
        );
    }

    public static void registerActions() {
        ActionRegistry.register(RadialAction.builder(ActionIds.DUNGEON_HELP)
            .labelKey("devmod.action.dungeon.help")
            .descriptionKey("devmod.action.dungeon.help.desc")
            .category(ActionCategory.TELEMETRY)
            .menuPath("Root/Telemetry/Dungeon/Help")
            .icon(Items.BOOK)
            .precondition(ActionPreconditions.requiresPermissionOrClient(2))
            .commandHint("devmod dungeon")
            .handler(context -> handleCommand(context, "devmod dungeon", DungeonCommand::showHelp))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.DUNGEON_START)
            .labelKey("devmod.action.dungeon.start")
            .descriptionKey("devmod.action.dungeon.start.desc")
            .category(ActionCategory.TELEMETRY)
            .menuPath("Root/Telemetry/Dungeon/Start")
            .icon(Items.DIAMOND_PICKAXE)
            .precondition(ActionPreconditions.requiresPermissionOrClient(2))
            .commandHint("devmod dungeon start <id>")
            .handler(context -> handleCommandPrompt(context, "devmod dungeon start ", DungeonCommand::startRun))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.DUNGEON_END)
            .labelKey("devmod.action.dungeon.end")
            .descriptionKey("devmod.action.dungeon.end.desc")
            .category(ActionCategory.TELEMETRY)
            .menuPath("Root/Telemetry/Dungeon/End")
            .icon(Items.DIAMOND_SWORD)
            .precondition(ActionPreconditions.requiresPermissionOrClient(2))
            .commandHint("devmod dungeon end <outcome>")
            .handler(context -> handleCommandPrompt(context, "devmod dungeon end ", DungeonCommand::endRunFromContext))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.DUNGEON_STATUS)
            .labelKey("devmod.action.dungeon.status")
            .descriptionKey("devmod.action.dungeon.status.desc")
            .category(ActionCategory.TELEMETRY)
            .menuPath("Root/Telemetry/Dungeon/Status")
            .icon(Items.CLOCK)
            .precondition(ActionPreconditions.requiresPermissionOrClient(2))
            .commandHint("devmod dungeon status")
            .handler(context -> handleCommand(context, "devmod dungeon status", DungeonCommand::showStatus))
            .build());
    }

    private static void handleCommand(ActionContext context, String command,
                                      CommandHandler handler) {
        CommandContext<CommandSourceStack> cmd = context.getCommandContext();
        if (context.getOrigin() == ActionOrigin.COMMAND && cmd != null) {
            handler.run(cmd);
            return;
        }
        context.executeCommand(command);
    }

    private static void handleCommandPrompt(ActionContext context, String command,
                                            CommandHandler handler) {
        CommandContext<CommandSourceStack> cmd = context.getCommandContext();
        if (context.getOrigin() == ActionOrigin.COMMAND && cmd != null) {
            handler.run(cmd);
            return;
        }
        if (!context.openCommandPrompt(command)) {
            context.executeCommand(command);
        }
    }

    @FunctionalInterface
    private interface CommandHandler {
        void run(CommandContext<CommandSourceStack> context);
    }

    private static int showHelp(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        source.sendSuccess(() -> Component.literal("§e=== Dungeon Run Debug Commands ==="), false);
        source.sendSuccess(() -> Component.literal("§7/devmod dungeon start <dungeon_id> §f- Start a debug dungeon run"), false);
        source.sendSuccess(() -> Component.literal("§7/devmod dungeon end <outcome> [kills] [deaths] [rewards] §f- End run"), false);
        source.sendSuccess(() -> Component.literal("§7/devmod dungeon status §f- Show active run status"), false);
        source.sendSuccess(() -> Component.literal(""), false);
        source.sendSuccess(() -> Component.literal("§eOutcomes: SUCCESS, DEATH, ABANDONED, TIMEOUT"), false);
        return 1;
    }

    private static int startRun(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Objects.requireNonNull(Component.literal("This command must be run by a player")));
            return 0;
        }

        String dungeonId = StringArgumentType.getString(context, "dungeon_id");

        // Ensure dungeon_id has proper format for detection
        if (!dungeonId.startsWith("dungeon_")) {
            dungeonId = "dungeon_" + dungeonId;
        }

        String roomId = dungeonId + "_room1";

        // Use debug method to start the run (prevents auto-end by trackPlayer)
        DungeonRunService.INSTANCE.debugStartRun(player, dungeonId, roomId);

        final String finalDungeonId = dungeonId;
        source.sendSuccess(() -> Component.literal(
            "§a[DungeonRun] Started debug run in dungeon '" + finalDungeonId + "'"
        ), false);

        LOGGER.info("[DungeonCommand] DEBUG RUN START: player='{}' dungeonId='{}'",
            player.getName().getString(), finalDungeonId);

        return 1;
    }

    private static int endRun(CommandContext<CommandSourceStack> context, int kills, int deaths, int rewardCount) {
        CommandSourceStack source = context.getSource();

        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Objects.requireNonNull(Component.literal("This command must be run by a player")));
            return 0;
        }

        String outcomeStr = StringArgumentType.getString(context, "outcome").toUpperCase(Locale.ROOT);

        DungeonRunService.RunOutcome outcome;
        try {
            outcome = DungeonRunService.RunOutcome.valueOf(outcomeStr);
        } catch (IllegalArgumentException e) {
            source.sendFailure(Objects.requireNonNull(Component.literal(
                "Invalid outcome: " + outcomeStr + ". Use SUCCESS, DEATH, ABANDONED, or TIMEOUT"
            )));
            return 0;
        }

        // Simulate combat stats before ending (all outcomes use debugEndRun)
        for (int i = 0; i < kills; i++) {
            DungeonRunService.INSTANCE.onEnemyKill(player, "debug_enemy");
        }
        // Simulate damage dealt/taken
        DungeonRunService.INSTANCE.onDamageDealt(player, kills * 10.0f);
        DungeonRunService.INSTANCE.onDamageTaken(player, deaths * 20.0f);

        // For DEATH outcome, we need to set deaths count manually via debugSetDeaths
        if (outcome == DungeonRunService.RunOutcome.DEATH && deaths > 0) {
            DungeonRunService.INSTANCE.debugSetDeaths(player.getUUID(), deaths);
        }

        // End the run with specified outcome (all outcomes go through same path)
        DungeonRunService.INSTANCE.debugEndRun(player.getUUID(), outcome, "debug command");

        source.sendSuccess(() -> Component.literal(
            "§a[DungeonRun] Ended debug run with outcome=" + outcome +
            " kills=" + kills + " deaths=" + deaths + " rewards=" + rewardCount
        ), false);

        LOGGER.info("[DungeonCommand] DEBUG RUN END: player='{}' outcome={} kills={} deaths={} rewards={}",
            player.getName().getString(), outcome, kills, deaths, rewardCount);

        return 1;
    }

    private static int endRunFromContext(CommandContext<CommandSourceStack> context) {
        int kills = 0;
        int deaths = 0;
        int rewardCount = 0;
        try {
            kills = IntegerArgumentType.getInteger(context, "kills");
        } catch (IllegalArgumentException ignored) {
            // Optional argument not supplied.
        }
        try {
            deaths = IntegerArgumentType.getInteger(context, "deaths");
        } catch (IllegalArgumentException ignored) {
            // Optional argument not supplied.
        }
        try {
            rewardCount = IntegerArgumentType.getInteger(context, "reward_count");
        } catch (IllegalArgumentException ignored) {
            // Optional argument not supplied.
        }
        return endRun(context, kills, deaths, rewardCount);
    }

    private static int showStatus(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        if (!(source.getEntity() instanceof ServerPlayer)) {
            source.sendFailure(Objects.requireNonNull(Component.literal("This command must be run by a player")));
            return 0;
        }

        int activeRuns = DungeonRunService.INSTANCE.getActiveRunsCount();

        source.sendSuccess(() -> Component.literal("§e=== Dungeon Run Status ==="), false);
        source.sendSuccess(() -> Component.literal("§7Active runs: §f" + activeRuns), false);

        // Show recent completed runs
        var summaries = DungeonRunService.INSTANCE.getRunSummaries();
        if (summaries.isEmpty()) {
            source.sendSuccess(() -> Component.literal("§7No completed runs yet."), false);
        } else {
            source.sendSuccess(() -> Component.literal("§7Recent runs:"), false);
            for (String summary : summaries) {
                source.sendSuccess(() -> Component.literal("§8- " + summary), false);
            }
        }

        return 1;
    }

    private static CompletableFuture<Suggestions> suggestOutcomes(
            CommandContext<CommandSourceStack> context,
            SuggestionsBuilder builder) {

        String input = builder.getRemaining().toLowerCase(Locale.ROOT);

        Arrays.stream(DungeonRunService.RunOutcome.values())
            .map(Enum::name)
            .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(input))
            .forEach(builder::suggest);

        return builder.buildFuture();
    }
}
