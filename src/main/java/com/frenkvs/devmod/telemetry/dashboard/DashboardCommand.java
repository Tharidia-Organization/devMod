package com.frenkvs.devmod.telemetry.dashboard;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import java.util.Objects;

/**
 * Command to control the telemetry dashboard.
 *
 * Usage:
 *   /devmod dashboard        - Open dashboard in browser (starts server if needed)
 *   /devmod dashboard start  - Start the dashboard server
 *   /devmod dashboard stop   - Stop the dashboard server
 *   /devmod dashboard status - Show server status
 */
public class DashboardCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("devmod")
                .then(Commands.literal("dashboard")
                    .requires(source -> source.hasPermission(2)) // Requires op level 2
                    .executes(DashboardCommand::openDashboard)
                    .then(Commands.literal("start")
                        .executes(DashboardCommand::startServer))
                    .then(Commands.literal("stop")
                        .executes(DashboardCommand::stopServer))
                    .then(Commands.literal("status")
                        .executes(DashboardCommand::showStatus)))
        );
    }

    private static int openDashboard(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        TelemetryDashboardServer server = TelemetryDashboardServer.INSTANCE;

        if (!server.isRunning()) {
            source.sendSuccess(() -> Component.literal("Starting dashboard server..."), false);
            server.start();
        }

        if (server.isRunning()) {
            server.openInBrowser();
            source.sendSuccess(() -> Component.literal(
                "Dashboard opened: " + server.getDashboardUrl()
            ), false);
            return 1;
        } else {
            source.sendFailure(Objects.requireNonNull(Component.literal(
                "Failed to start dashboard server. Check logs for details."
            )));
            return 0;
        }
    }

    private static int startServer(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        TelemetryDashboardServer server = TelemetryDashboardServer.INSTANCE;

        if (server.isRunning()) {
            source.sendSuccess(() -> Component.literal(
                "Dashboard server already running at " + server.getDashboardUrl()
            ), false);
            return 1;
        }

        server.start();

        if (server.isRunning()) {
            source.sendSuccess(() -> Component.literal(
                "Dashboard server started at " + server.getDashboardUrl()
            ), false);
            return 1;
        } else {
            source.sendFailure(Objects.requireNonNull(Component.literal(
                "Failed to start dashboard server. Check logs for details."
            )));
            return 0;
        }
    }

    private static int stopServer(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        TelemetryDashboardServer server = TelemetryDashboardServer.INSTANCE;

        if (!server.isRunning()) {
            source.sendSuccess(() -> Component.literal("Dashboard server is not running."), false);
            return 1;
        }

        server.stop();
        source.sendSuccess(() -> Component.literal("Dashboard server stopped."), false);
        return 1;
    }

    private static int showStatus(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        TelemetryDashboardServer server = TelemetryDashboardServer.INSTANCE;

        if (server.isRunning()) {
            source.sendSuccess(() -> Component.literal(
                "Dashboard server is running at " + server.getDashboardUrl()
            ), false);
        } else {
            source.sendSuccess(() -> Component.literal("Dashboard server is not running."), false);
        }

        // Show DuckDB status
        boolean duckdbEnabled = com.frenkvs.devmod.telemetry.duckdb.DuckDBTelemetryService.INSTANCE.isEnabled();
        source.sendSuccess(() -> Component.literal(
            "DuckDB: " + (duckdbEnabled ? "enabled" : "disabled")
        ), false);

        return 1;
    }
}
