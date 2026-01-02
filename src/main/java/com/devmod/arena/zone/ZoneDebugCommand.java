package com.devmod.arena.zone;

import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import com.devmod.arena.api.ArenaHandle;
import com.devmod.endurance.EnduranceQuestManager;
import com.devmod.endurance.WaveManager;
import com.devmod.network.NetworkHandler;
import com.devmod.network.ZoneDebugPayload;

/**
 * Command handler for zone debug visualization.
 * Allows operators to toggle zone boundary rendering for arena debugging.
 *
 * Commands:
 * - /devmod zone debug on   - Enable zone debug rendering for current arena
 * - /devmod zone debug off  - Disable zone debug rendering
 * - /devmod zone debug info - Show current zone layout info
 */
public class ZoneDebugCommand {
    private static final Logger LOGGER = LoggerFactory.getLogger(ZoneDebugCommand.class);

    private ZoneDebugCommand() {} // Utility class

    /**
     * Registers the command with the dispatcher.
     */
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("devmod")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("zone")
                    .then(Commands.literal("debug")
                        .then(Commands.literal("on")
                            .executes(ZoneDebugCommand::enableDebug))
                        .then(Commands.literal("off")
                            .executes(ZoneDebugCommand::disableDebug))
                        .then(Commands.literal("info")
                            .executes(ZoneDebugCommand::showInfo))
                        .executes(ZoneDebugCommand::toggleDebug)))
        );
    }

    /**
     * Toggles zone debug rendering.
     */
    private static int toggleDebug(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) {
            ctx.getSource().sendFailure(Objects.requireNonNull(Component.literal(
                "[ZoneDebug] This command must be run by a player.")));
            return 0;
        }

        // Check if player is in an active session
        var sessionOpt = EnduranceQuestManager.INSTANCE.getActiveSession(player);
        if (sessionOpt.isEmpty()) {
            ctx.getSource().sendFailure(Objects.requireNonNull(Component.literal(
                "[ZoneDebug] You must be in an active arena to use this command.")));
            return 0;
        }

        var session = sessionOpt.get();
        ArenaHandle handle = session.getArenaHandle();
        if (handle == null) {
            ctx.getSource().sendFailure(Objects.requireNonNull(Component.literal(
                "[ZoneDebug] Arena handle not available yet.")));
            return 0;
        }

        // Get wave state to access zone layout
        var waveStateOpt = WaveManager.INSTANCE.getWaveState(handle.arenaId());
        if (waveStateOpt.isEmpty()) {
            ctx.getSource().sendFailure(Objects.requireNonNull(Component.literal(
                "[ZoneDebug] Wave state not found for this arena.")));
            return 0;
        }

        var waveState = waveStateOpt.get();

        if (!waveState.hasZones()) {
            ctx.getSource().sendFailure(Objects.requireNonNull(Component.literal(
                "[ZoneDebug] This arena does not have zone definitions.")));
            return 0;
        }

        // Send enable payload with zone layout
        ZoneLayout layout = waveState.getZoneLayout();
        if (layout == null) {
            ctx.getSource().sendFailure(Objects.requireNonNull(Component.literal(
                "[ZoneDebug] Zone layout is not available.")));
            return 0;
        }
        int baseY = handle.originY();

        ZoneDebugPayload payload = ZoneDebugPayload.enable(layout, baseY);
        NetworkHandler.sendZoneDebug(player, payload);

        ctx.getSource().sendSuccess(() -> Objects.requireNonNull(Component.literal(
            Objects.requireNonNull(String.format("[ZoneDebug] Enabled - %d zones visible", layout.zoneCount())))), true);

        LOGGER.info("Zone debug enabled for player {} in arena {}", player.getName().getString(), handle.arenaId());
        return 1;
    }

    /**
     * Enables zone debug rendering.
     */
    private static int enableDebug(CommandContext<CommandSourceStack> ctx) {
        return toggleDebug(ctx);
    }

    /**
     * Disables zone debug rendering.
     */
    private static int disableDebug(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) {
            ctx.getSource().sendFailure(Objects.requireNonNull(Component.literal(
                "[ZoneDebug] This command must be run by a player.")));
            return 0;
        }

        ZoneDebugPayload payload = ZoneDebugPayload.disable();
        NetworkHandler.sendZoneDebug(player, payload);

        ctx.getSource().sendSuccess(() -> Objects.requireNonNull(Component.literal(
            "[ZoneDebug] Disabled - zone visualization hidden")), true);

        LOGGER.info("Zone debug disabled for player {}", player.getName().getString());
        return 1;
    }

    /**
     * Shows information about current zone layout.
     */
    private static int showInfo(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) {
            ctx.getSource().sendFailure(Objects.requireNonNull(Component.literal(
                "[ZoneDebug] This command must be run by a player.")));
            return 0;
        }

        // Check if player is in an active session
        var sessionOpt = EnduranceQuestManager.INSTANCE.getActiveSession(player);
        if (sessionOpt.isEmpty()) {
            ctx.getSource().sendFailure(Objects.requireNonNull(Component.literal(
                "[ZoneDebug] You must be in an active arena to use this command.")));
            return 0;
        }

        var session = sessionOpt.get();
        ArenaHandle handle = session.getArenaHandle();
        if (handle == null) {
            ctx.getSource().sendFailure(Objects.requireNonNull(Component.literal(
                "[ZoneDebug] Arena handle not available yet.")));
            return 0;
        }

        // Get wave state to access zone layout
        var waveStateOpt = WaveManager.INSTANCE.getWaveState(handle.arenaId());
        if (waveStateOpt.isEmpty()) {
            ctx.getSource().sendFailure(Objects.requireNonNull(Component.literal(
                "[ZoneDebug] Wave state not found for this arena.")));
            return 0;
        }

        var waveState = waveStateOpt.get();

        // Header
        ctx.getSource().sendSuccess(() -> Objects.requireNonNull(Component.literal(
            "=== Zone Layout Info ===")), false);
        ctx.getSource().sendSuccess(() -> Objects.requireNonNull(Component.literal(
            Objects.requireNonNull(String.format("Arena: %s", handle.templateId())))), false);
        ctx.getSource().sendSuccess(() -> Objects.requireNonNull(Component.literal(
            Objects.requireNonNull(String.format("Origin: %d, %d, %d", handle.originX(), handle.originY(), handle.originZ())))), false);

        if (!waveState.hasZones()) {
            ctx.getSource().sendSuccess(() -> Objects.requireNonNull(Component.literal(
                "Zones: None (single-zone arena)")), false);
            return 1;
        }

        ZoneLayout layout = waveState.getZoneLayout();
        if (layout == null) {
            ctx.getSource().sendSuccess(() -> Objects.requireNonNull(Component.literal(
                "Zones: None (layout unavailable)")), false);
            return 1;
        }
        ctx.getSource().sendSuccess(() -> Objects.requireNonNull(Component.literal(
            Objects.requireNonNull(String.format("Zones: %d | Strategy: %s", layout.zoneCount(), layout.strategy())))), false);

        // List each zone
        for (ArenaZone zone : layout.zones()) {
            String biome = zone.environment().biome()
                .map(b -> b.getPath())
                .orElse("default");
            String shape = zone.shape().name().toLowerCase(java.util.Locale.ROOT);

            ctx.getSource().sendSuccess(() -> Objects.requireNonNull(Component.literal(
                Objects.requireNonNull(String.format("  - %s [%s] biome=%s light=%d",
                    zone.name(), shape, biome, zone.environment().lighting().blockLight())))), false);
        }

        return 1;
    }

    /**
     * Sends zone debug payload to a player when they enter an arena with zones.
     * Called from WaveManager when wave starts.
     *
     * @param player The player to send to
     * @param layout The zone layout
     * @param baseY The floor Y level
     */
    public static void sendOnArenaEntry(ServerPlayer player, ZoneLayout layout, int baseY) {
        if (layout == null || !layout.isMultiZone()) {
            return;
        }

        ZoneDebugPayload payload = ZoneDebugPayload.enable(layout, baseY);
        NetworkHandler.sendZoneDebug(player, payload);

        LOGGER.debug("Auto-sent zone debug to {} ({} zones)",
            player.getName().getString(), layout.zoneCount());
    }

    /**
     * Sends disable payload when player leaves arena.
     *
     * @param player The player leaving
     */
    public static void sendOnArenaExit(ServerPlayer player) {
        ZoneDebugPayload payload = ZoneDebugPayload.disable();
        NetworkHandler.sendZoneDebug(player, payload);

        LOGGER.debug("Auto-disabled zone debug for {}", player.getName().getString());
    }
}
