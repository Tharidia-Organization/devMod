package com.devmod.endurance;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import com.devmod.actions.ActionCommandInvoker;
import com.devmod.actions.ActionIds;
import com.devmod.endurance.LeaderboardSystem.LeaderboardCategory;
import com.devmod.endurance.LeaderboardSystem.LeaderboardEntry;

/**
 * Commands for the leaderboard system.
 *
 * Provides commands for:
 * - Viewing top players by category
 * - Checking personal rankings
 * - Viewing weekly leaderboards
 * - Arena-specific leaderboards
 */
public final class LeaderboardCommands {

    private static final Logger LOGGER = LoggerFactory.getLogger(LeaderboardCommands.class);

    private LeaderboardCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("leaderboard")
                // /leaderboard - shows help
                .executes(ctx -> ActionCommandInvoker.invoke(ActionIds.LEADERBOARD_HELP, ctx))

                // /leaderboard help
                .then(Commands.literal("help")
                    .executes(ctx -> ActionCommandInvoker.invoke(ActionIds.LEADERBOARD_HELP, ctx)))

                // /leaderboard list - list all categories
                .then(Commands.literal("list")
                    .executes(ctx -> ActionCommandInvoker.invoke(ActionIds.LEADERBOARD_LIST, ctx)))

                // /leaderboard top <category> [limit]
                .then(Commands.literal("top")
                    .then(Commands.argument("category", Objects.requireNonNull(StringArgumentType.word()))
                        .suggests(LeaderboardCommands::suggestCategories)
                        .executes(ctx -> ActionCommandInvoker.invoke(ActionIds.LEADERBOARD_TOP, ctx))
                        .then(Commands.argument("limit", Objects.requireNonNull(IntegerArgumentType.integer(1, 100)))
                            .executes(ctx -> ActionCommandInvoker.invoke(ActionIds.LEADERBOARD_TOP, ctx)))))

                // /leaderboard me [category]
                .then(Commands.literal("me")
                    .executes(ctx -> ActionCommandInvoker.invoke(ActionIds.LEADERBOARD_ME, ctx))
                    .then(Commands.argument("category", Objects.requireNonNull(StringArgumentType.word()))
                        .suggests(LeaderboardCommands::suggestCategories)
                        .executes(ctx -> ActionCommandInvoker.invoke(ActionIds.LEADERBOARD_ME, ctx))))

                // /leaderboard player <player> [category]
                .then(Commands.literal("player")
                    .requires(src -> src.hasPermission(2))
                    .then(Commands.argument("player", Objects.requireNonNull(EntityArgument.player()))
                        .executes(ctx -> ActionCommandInvoker.invoke(ActionIds.LEADERBOARD_PLAYER, ctx))
                        .then(Commands.argument("category", Objects.requireNonNull(StringArgumentType.word()))
                            .suggests(LeaderboardCommands::suggestCategories)
                            .executes(ctx -> ActionCommandInvoker.invoke(ActionIds.LEADERBOARD_PLAYER, ctx)))))

                // /leaderboard weekly [category] [limit]
                .then(Commands.literal("weekly")
                    .executes(ctx -> ActionCommandInvoker.invoke(ActionIds.LEADERBOARD_WEEKLY, ctx))
                    .then(Commands.argument("category", Objects.requireNonNull(StringArgumentType.word()))
                        .suggests(LeaderboardCommands::suggestCategories)
                        .executes(ctx -> ActionCommandInvoker.invoke(ActionIds.LEADERBOARD_WEEKLY, ctx))
                        .then(Commands.argument("limit", Objects.requireNonNull(IntegerArgumentType.integer(1, 100)))
                            .executes(ctx -> ActionCommandInvoker.invoke(ActionIds.LEADERBOARD_WEEKLY, ctx)))))

                // /leaderboard arena <arenaId> [category] [limit]
                .then(Commands.literal("arena")
                    .then(Commands.argument("arenaId", Objects.requireNonNull(StringArgumentType.word()))
                        .executes(ctx -> ActionCommandInvoker.invoke(ActionIds.LEADERBOARD_ARENA, ctx))
                        .then(Commands.argument("category", Objects.requireNonNull(StringArgumentType.word()))
                            .suggests(LeaderboardCommands::suggestCategories)
                            .executes(ctx -> ActionCommandInvoker.invoke(ActionIds.LEADERBOARD_ARENA, ctx))
                            .then(Commands.argument("limit", Objects.requireNonNull(IntegerArgumentType.integer(1, 100)))
                                .executes(ctx -> ActionCommandInvoker.invoke(ActionIds.LEADERBOARD_ARENA, ctx))))))
        );
    }

    private static CompletableFuture<Suggestions> suggestCategories(
            CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        String remaining = Objects.requireNonNull(builder.getRemainingLowerCase());
        for (LeaderboardCategory cat : LeaderboardCategory.values()) {
            String name = Objects.requireNonNull(cat.name().toLowerCase(Locale.ROOT));
            if (name.startsWith(remaining)) {
                builder.suggest(name, Objects.requireNonNull(Component.literal(Objects.requireNonNull(cat.displayName))));
            }
        }
        return builder.buildFuture();
    }

    // ========== Command Handlers ==========

    public static int showHelp(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        src.sendSuccess(() -> Objects.requireNonNull(Component.literal("§e=== Leaderboard Commands ===")), false);
        src.sendSuccess(() -> Objects.requireNonNull(Component.literal("§7/leaderboard list §8- §fList all categories")), false);
        src.sendSuccess(() -> Objects.requireNonNull(Component.literal("§7/leaderboard top <category> [limit] §8- §fView top players")), false);
        src.sendSuccess(() -> Objects.requireNonNull(Component.literal("§7/leaderboard me [category] §8- §fView your rankings")), false);
        src.sendSuccess(() -> Objects.requireNonNull(Component.literal("§7/leaderboard weekly [category] §8- §fView weekly leaderboard")), false);
        src.sendSuccess(() -> Objects.requireNonNull(Component.literal("§7/leaderboard arena <id> [category] §8- §fArena-specific rankings")), false);
        return 1;
    }

    public static int listCategories(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        src.sendSuccess(() -> Objects.requireNonNull(Component.literal("§e=== Leaderboard Categories ===")), false);
        for (LeaderboardCategory cat : LeaderboardCategory.values()) {
            String direction = cat.higherIsBetter ? "§a↑" : "§c↓";
            src.sendSuccess(() -> Objects.requireNonNull(Component.literal(Objects.requireNonNull(String.format(
                "§7%s %s §f%s §8- §7%s",
                direction, cat.name().toLowerCase(Locale.ROOT), cat.displayName, cat.description
            )))), false);
        }
        return 1;
    }

    public static int showTop(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();

        String categoryName = StringArgumentType.getString(ctx, "category");
        LeaderboardCategory category = parseCategory(categoryName);
        if (category == null) {
            src.sendFailure(Objects.requireNonNull(Component.literal("§cUnknown category: " + categoryName)));
            return 0;
        }

        int limit = 10;
        try {
            limit = IntegerArgumentType.getInteger(ctx, "limit");
        } catch (IllegalArgumentException e) {
            LOGGER.trace("Optional 'limit' argument not provided for showTop");
        }

        List<LeaderboardEntry> entries = LeaderboardSystem.INSTANCE.getTopEntries(category, limit);
        displayLeaderboard(src, category, entries, "Global");
        return 1;
    }

    public static int showMe(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();

        if (!(src.getEntity() instanceof ServerPlayer player)) {
            src.sendFailure(Objects.requireNonNull(Component.literal("§cThis command can only be run by players")));
            return 0;
        }

        UUID playerId = player.getUUID();

        // If category specified, show just that one
        LeaderboardCategory specificCategory = null;
        try {
            String categoryName = StringArgumentType.getString(ctx, "category");
            specificCategory = parseCategory(categoryName);
            if (specificCategory == null) {
                src.sendFailure(Objects.requireNonNull(Component.literal("§cUnknown category: " + categoryName)));
                return 0;
            }
        } catch (IllegalArgumentException e) {
            LOGGER.trace("Optional 'category' argument not provided for showMe");
        }

        src.sendSuccess(() -> Objects.requireNonNull(Component.literal("§e=== Your Rankings ===")), false);

        if (specificCategory != null) {
            showPlayerCategoryRank(src, playerId, specificCategory);
        } else {
            for (LeaderboardCategory cat : LeaderboardCategory.values()) {
                showPlayerCategoryRank(src, playerId, cat);
            }
        }

        return 1;
    }

    public static int showPlayer(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();

        ServerPlayer target;
        try {
            target = EntityArgument.getPlayer(ctx, "player");
        } catch (Exception e) {
            src.sendFailure(Objects.requireNonNull(Component.literal("§cPlayer not found")));
            return 0;
        }

        UUID playerId = target.getUUID();
        String playerName = target.getName().getString();

        LeaderboardCategory specificCategory = null;
        try {
            String categoryName = StringArgumentType.getString(ctx, "category");
            specificCategory = parseCategory(categoryName);
            if (specificCategory == null) {
                src.sendFailure(Objects.requireNonNull(Component.literal("§cUnknown category: " + categoryName)));
                return 0;
            }
        } catch (IllegalArgumentException e) {
            LOGGER.trace("Optional 'category' argument not provided for showPlayer");
        }

        src.sendSuccess(() -> Objects.requireNonNull(Component.literal("§e=== Rankings for " + playerName + " ===")), false);

        if (specificCategory != null) {
            showPlayerCategoryRank(src, playerId, specificCategory);
        } else {
            for (LeaderboardCategory cat : LeaderboardCategory.values()) {
                showPlayerCategoryRank(src, playerId, cat);
            }
        }

        return 1;
    }

    public static int showWeekly(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();

        LeaderboardCategory category = LeaderboardCategory.WAVES_COMPLETED;
        try {
            String categoryName = StringArgumentType.getString(ctx, "category");
            LeaderboardCategory parsed = parseCategory(categoryName);
            if (parsed == null) {
                src.sendFailure(Objects.requireNonNull(Component.literal("§cUnknown category: " + categoryName)));
                return 0;
            }
            category = parsed;
        } catch (IllegalArgumentException e) {
            LOGGER.trace("Optional 'category' argument not provided for showWeekly");
        }

        int limit = 10;
        try {
            limit = IntegerArgumentType.getInteger(ctx, "limit");
        } catch (IllegalArgumentException e) {
            LOGGER.trace("Optional 'limit' argument not provided for showWeekly");
        }

        List<LeaderboardEntry> entries = LeaderboardSystem.INSTANCE.getWeeklyTopEntries(category, limit);
        displayLeaderboard(src, category, entries, "Weekly");
        return 1;
    }

    public static int showArena(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();

        String arenaId = StringArgumentType.getString(ctx, "arenaId");

        LeaderboardCategory category = LeaderboardCategory.WAVES_COMPLETED;
        try {
            String categoryName = StringArgumentType.getString(ctx, "category");
            LeaderboardCategory parsed = parseCategory(categoryName);
            if (parsed == null) {
                src.sendFailure(Objects.requireNonNull(Component.literal("§cUnknown category: " + categoryName)));
                return 0;
            }
            category = parsed;
        } catch (IllegalArgumentException e) {
            LOGGER.trace("Optional 'category' argument not provided for showArena");
        }

        int limit = 10;
        try {
            limit = IntegerArgumentType.getInteger(ctx, "limit");
        } catch (IllegalArgumentException e) {
            LOGGER.trace("Optional 'limit' argument not provided for showArena");
        }

        List<LeaderboardEntry> entries = LeaderboardSystem.INSTANCE.getTopEntries(category, arenaId, limit);
        displayLeaderboard(src, category, entries, "Arena: " + arenaId);
        return 1;
    }

    // ========== Helpers ==========

    private static LeaderboardCategory parseCategory(String name) {
        try {
            return LeaderboardCategory.valueOf(name.toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            // Category not found - return null to signal error
            return null;
        }
    }

    private static void displayLeaderboard(CommandSourceStack src, LeaderboardCategory category,
                                            List<LeaderboardEntry> entries, String scope) {
        src.sendSuccess(() -> Objects.requireNonNull(Component.literal(Objects.requireNonNull(String.format(
            "§e=== %s - %s ===", category.displayName, scope
        )))), false);

        if (entries.isEmpty()) {
            src.sendSuccess(() -> Objects.requireNonNull(Component.literal("§7No entries yet")), false);
            return;
        }

        int rank = 1;
        for (LeaderboardEntry entry : entries) {
            String medal = getMedal(rank);
            String scoreStr = LeaderboardSystem.formatScore(category, entry.score());
            final int currentRank = rank;
            src.sendSuccess(() -> Objects.requireNonNull(Component.literal(Objects.requireNonNull(String.format(
                "%s §f#%d §7%s §8- §e%s",
                medal, currentRank, entry.playerName(), scoreStr
            )))), false);
            rank++;
        }
    }

    private static void showPlayerCategoryRank(CommandSourceStack src, UUID playerId, LeaderboardCategory category) {
        int rank = LeaderboardSystem.INSTANCE.getPlayerRank(category, playerId, null);
        long score = LeaderboardSystem.INSTANCE.getPlayerBestScore(category, playerId, null);

        String rankStr = rank > 0 ? "#" + rank : "Unranked";
        String scoreStr = score > 0 ? LeaderboardSystem.formatScore(category, score) : "-";

        src.sendSuccess(() -> Objects.requireNonNull(Component.literal(Objects.requireNonNull(String.format(
            "§7%s: §f%s §8(§e%s§8)",
            category.displayName, rankStr, scoreStr
        )))), false);
    }

    private static String getMedal(int rank) {
        return switch (rank) {
            case 1 -> "§6\u2B50"; // Gold star
            case 2 -> "§7\u2B50"; // Silver star
            case 3 -> "§c\u2B50"; // Bronze star
            default -> "§8\u25CF"; // Bullet
        };
    }
}
