package com.devmod.compat.mods.dummmmmmy;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import javax.annotation.Nonnull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;

/**
 * Debug commands for Dummmmmmy mod integration.
 *
 * Usage:
 *   /dummy spawn [id]    - Spawn a training dummy nearby
 *   /dummy remove <id>   - Remove a specific dummy
 *   /dummy clear         - Remove all DevMod-spawned dummies
 *   /dummy list          - List active dummies
 *   /dummy stats         - Show stats for nearest dummy
 *   /dummy reset         - Reset damage counter on nearest dummy
 *   /dummy status        - Show integration status
 */
public final class DummmmmmyCommands {

    private static final Logger LOGGER = LoggerFactory.getLogger(DummmmmmyCommands.class);

    private DummmmmmyCommands() {}

    @Nonnull
    private static Component msg(String text, ChatFormatting... styles) {
        return Objects.requireNonNull(Component.literal(Objects.requireNonNull(text))
            .withStyle(Objects.requireNonNull(styles)));
    }

    /**
     * Register the /dummy command tree.
     */
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("dummy")
                .requires(source -> source.hasPermission(2))
                // /dummy spawn [id]
                .then(Commands.literal("spawn")
                    .executes(ctx -> spawnDummy(ctx, "default"))
                    .then(Commands.argument("id", StringArgumentType.word())
                        .executes(ctx -> spawnDummy(ctx, StringArgumentType.getString(ctx, "id")))))
                // /dummy remove <id>
                .then(Commands.literal("remove")
                    .then(Commands.argument("id", StringArgumentType.word())
                        .executes(ctx -> removeDummy(ctx, StringArgumentType.getString(ctx, "id")))))
                // /dummy clear
                .then(Commands.literal("clear")
                    .executes(DummmmmmyCommands::clearAllDummies))
                // /dummy list
                .then(Commands.literal("list")
                    .executes(DummmmmmyCommands::listDummies))
                // /dummy stats
                .then(Commands.literal("stats")
                    .executes(DummmmmmyCommands::showStats))
                // /dummy reset
                .then(Commands.literal("reset")
                    .executes(DummmmmmyCommands::resetDamage))
                // /dummy status
                .then(Commands.literal("status")
                    .executes(DummmmmmyCommands::showStatus))
        );
    }

    /**
     * Spawn a training dummy 3 blocks in front of the player.
     */
    private static int spawnDummy(CommandContext<CommandSourceStack> ctx, String id) throws CommandSyntaxException {
        if (!DummmmmmyCompat.isAvailable()) {
            ctx.getSource().sendFailure(msg("Dummmmmmy mod not installed", ChatFormatting.RED));
            return 0;
        }

        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ServerLevel level = player.serverLevel();
        BlockPos pos = player.blockPosition().relative(player.getDirection(), 3);

        UUID uuid = DummmmmmyCompat.spawnDummy(level, pos, id);
        if (uuid != null) {
            ctx.getSource().sendSuccess(() ->
                msg("Spawned dummy '" + id + "' at " + formatPos(pos), ChatFormatting.GREEN), true);
            LOGGER.info("[Compat:dummmmmmy] Player {} spawned dummy '{}' at {}",
                player.getName().getString(), id, pos);
            return 1;
        }

        ctx.getSource().sendFailure(msg("Failed to spawn dummy", ChatFormatting.RED));
        return 0;
    }

    /**
     * Remove a specific dummy by ID.
     */
    private static int removeDummy(CommandContext<CommandSourceStack> ctx, String id) throws CommandSyntaxException {
        if (!DummmmmmyCompat.isAvailable()) {
            ctx.getSource().sendFailure(msg("Dummmmmmy mod not installed", ChatFormatting.RED));
            return 0;
        }

        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ServerLevel level = player.serverLevel();

        if (DummmmmmyCompat.removeDummy(level, id)) {
            ctx.getSource().sendSuccess(() ->
                msg("Removed dummy '" + id + "'", ChatFormatting.YELLOW), true);
            return 1;
        }

        ctx.getSource().sendFailure(msg("Dummy '" + id + "' not found", ChatFormatting.RED));
        return 0;
    }

    /**
     * Clear all DevMod-spawned dummies.
     */
    private static int clearAllDummies(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        if (!DummmmmmyCompat.isAvailable()) {
            ctx.getSource().sendFailure(msg("Dummmmmmy mod not installed", ChatFormatting.RED));
            return 0;
        }

        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ServerLevel level = player.serverLevel();
        int count = DummmmmmyCompat.getSpawnedDummyCount();

        DummmmmmyCompat.removeAllDummies(level);

        ctx.getSource().sendSuccess(() ->
            msg("Cleared " + count + " dummies", ChatFormatting.YELLOW), true);
        return count;
    }

    /**
     * List active dummy count.
     */
    private static int listDummies(CommandContext<CommandSourceStack> ctx) {
        if (!DummmmmmyCompat.isAvailable()) {
            ctx.getSource().sendFailure(msg("Dummmmmmy mod not installed", ChatFormatting.RED));
            return 0;
        }

        int count = DummmmmmyCompat.getSpawnedDummyCount();
        ctx.getSource().sendSuccess(() ->
            msg("Active dummies: " + count, ChatFormatting.AQUA), false);
        return count;
    }

    /**
     * Show damage stats for the nearest dummy.
     */
    private static int showStats(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        if (!DummmmmmyCompat.isAvailable()) {
            ctx.getSource().sendFailure(msg("Dummmmmmy mod not installed", ChatFormatting.RED));
            return 0;
        }

        if (!DummmmmmyCompat.isApiAvailable()) {
            ctx.getSource().sendFailure(msg("Dummmmmmy API not accessible", ChatFormatting.RED));
            return 0;
        }

        ServerPlayer player = ctx.getSource().getPlayerOrException();
        Entity dummy = findNearestDummy(player);

        if (dummy == null) {
            ctx.getSource().sendFailure(msg("No dummy nearby (within 10 blocks)", ChatFormatting.RED));
            return 0;
        }

        Map<String, Object> stats = DummmmmmyCompat.getDamageStats(dummy);
        float totalDmg = ((Number) stats.getOrDefault("totalDamage", -1f)).floatValue();
        float dps = ((Number) stats.getOrDefault("dps", -1f)).floatValue();
        float health = ((Number) stats.getOrDefault("health", 0f)).floatValue();
        float maxHealth = ((Number) stats.getOrDefault("maxHealth", 0f)).floatValue();

        ctx.getSource().sendSuccess(() -> msg("=== Dummy Stats ===", ChatFormatting.GOLD, ChatFormatting.BOLD), false);

        if (totalDmg >= 0) {
            ctx.getSource().sendSuccess(() -> msg(String.format("Total Damage: %.1f", totalDmg), ChatFormatting.WHITE), false);
        }
        if (dps >= 0) {
            ctx.getSource().sendSuccess(() -> msg(String.format("DPS: %.2f", dps), ChatFormatting.WHITE), false);
        }
        if (maxHealth > 0) {
            ctx.getSource().sendSuccess(() -> msg(String.format("Health: %.0f / %.0f", health, maxHealth), ChatFormatting.WHITE), false);
        }

        return 1;
    }

    /**
     * Reset damage counter on the nearest dummy.
     */
    private static int resetDamage(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        if (!DummmmmmyCompat.isAvailable()) {
            ctx.getSource().sendFailure(msg("Dummmmmmy mod not installed", ChatFormatting.RED));
            return 0;
        }

        ServerPlayer player = ctx.getSource().getPlayerOrException();
        Entity dummy = findNearestDummy(player);

        if (dummy == null) {
            ctx.getSource().sendFailure(msg("No dummy nearby (within 10 blocks)", ChatFormatting.RED));
            return 0;
        }

        if (DummmmmmyCompat.resetDamage(dummy)) {
            ctx.getSource().sendSuccess(() -> msg("Damage counter reset", ChatFormatting.GREEN), false);
            return 1;
        }

        ctx.getSource().sendFailure(msg("Failed to reset damage (API unavailable)", ChatFormatting.YELLOW));
        return 0;
    }

    /**
     * Show integration status.
     */
    private static int showStatus(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().sendSuccess(() -> msg("=== Dummmmmmy Integration ===", ChatFormatting.GOLD, ChatFormatting.BOLD), false);
        ctx.getSource().sendSuccess(() -> msg("Mod Available: " + DummmmmmyCompat.isAvailable(),
            DummmmmmyCompat.isAvailable() ? ChatFormatting.GREEN : ChatFormatting.RED), false);
        ctx.getSource().sendSuccess(() -> msg("API Available: " + DummmmmmyCompat.isApiAvailable(),
            DummmmmmyCompat.isApiAvailable() ? ChatFormatting.GREEN : ChatFormatting.YELLOW), false);
        ctx.getSource().sendSuccess(() -> msg("Tracked Dummies: " + DummmmmmyCompat.getSpawnedDummyCount(), ChatFormatting.WHITE), false);

        return 1;
    }

    /**
     * Find the nearest dummy entity within 10 blocks.
     */
    private static Entity findNearestDummy(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        AABB searchBox = player.getBoundingBox().inflate(10);

        Entity nearest = null;
        double nearestDist = Double.MAX_VALUE;

        for (Entity entity : level.getEntities(player, searchBox)) {
            if (DummmmmmyCompat.isDummy(entity)) {
                double dist = player.distanceToSqr(entity);
                if (dist < nearestDist) {
                    nearest = entity;
                    nearestDist = dist;
                }
            }
        }

        return nearest;
    }

    private static String formatPos(BlockPos pos) {
        return String.format("(%d, %d, %d)", pos.getX(), pos.getY(), pos.getZ());
    }
}
