package com.frenkvs.devmod.telemetry.dungeon;

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
import org.slf4j.Logger;

import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * P2-B Debug command for dungeon run testing.
 *
 * Usage:
 *   /devmod dungeon start <dungeon_id>
 *   /devmod dungeon end <SUCCESS|DEATH|ABANDONED|TIMEOUT> [kills] [deaths] [reward_count]
 *   /devmod dungeon status
 *
 * This uses the real DungeonRunService pipeline (not direct SQL insert).
 */
public class DungeonCommand {
    private static final Logger LOGGER = LogUtils.getLogger();

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("devmod")
                .then(Commands.literal("dungeon")
                    .requires(source -> source.hasPermission(2)) // Requires op level 2
                    .then(Commands.literal("start")
                        .then(Commands.argument("dungeon_id", Objects.requireNonNull(StringArgumentType.word()))
                            .executes(DungeonCommand::startRun)))
                    .then(Commands.literal("end")
                        .then(Commands.argument("outcome", Objects.requireNonNull(StringArgumentType.word()))
                            .suggests(DungeonCommand::suggestOutcomes)
                            .executes(ctx -> endRun(ctx, 0, 0, 0))
                            .then(Commands.argument("kills", Objects.requireNonNull(IntegerArgumentType.integer(0)))
                                .executes(ctx -> endRun(ctx, IntegerArgumentType.getInteger(ctx, "kills"), 0, 0))
                                .then(Commands.argument("deaths", Objects.requireNonNull(IntegerArgumentType.integer(0)))
                                    .executes(ctx -> endRun(ctx,
                                        IntegerArgumentType.getInteger(ctx, "kills"),
                                        IntegerArgumentType.getInteger(ctx, "deaths"), 0))
                                    .then(Commands.argument("reward_count", Objects.requireNonNull(IntegerArgumentType.integer(0)))
                                        .executes(ctx -> endRun(ctx,
                                            IntegerArgumentType.getInteger(ctx, "kills"),
                                            IntegerArgumentType.getInteger(ctx, "deaths"),
                                            IntegerArgumentType.getInteger(ctx, "reward_count"))))))))
                    .then(Commands.literal("status")
                        .executes(DungeonCommand::showStatus))
                    .executes(DungeonCommand::showHelp))
        );
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
