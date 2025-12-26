package com.devmod.telemetry.dashboard;

import java.util.Objects;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Items;

import com.devmod.actions.ActionCategory;
import com.devmod.actions.ActionCommandInvoker;
import com.devmod.actions.ActionContext;
import com.devmod.actions.ActionIds;
import com.devmod.actions.ActionOrigin;
import com.devmod.actions.ActionPreconditions;
import com.devmod.actions.ActionRegistry;
import com.devmod.actions.RadialAction;

public class DashboardCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("devmod")
                .then(Commands.literal("dashboard")
                    .requires(source -> source.hasPermission(2)) // Requires op level 2
                    .executes(ctx -> ActionCommandInvoker.invoke(ActionIds.TELEMETRY_DASHBOARD_SERVER_OPEN, ctx))
                    .then(Commands.literal("start")
                        .executes(ctx -> ActionCommandInvoker.invoke(ActionIds.TELEMETRY_DASHBOARD_SERVER_START, ctx)))
                    .then(Commands.literal("stop")
                        .executes(ctx -> ActionCommandInvoker.invoke(ActionIds.TELEMETRY_DASHBOARD_SERVER_STOP, ctx)))
                    .then(Commands.literal("status")
                        .executes(ctx -> ActionCommandInvoker.invoke(ActionIds.TELEMETRY_DASHBOARD_SERVER_STATUS, ctx))))
        );
    }

    public static void registerActions() {
        ActionRegistry.register(RadialAction.builder(ActionIds.TELEMETRY_DASHBOARD_SERVER_OPEN)
            .labelKey("devmod.action.telemetry.dashboard.open")
            .descriptionKey("devmod.action.telemetry.dashboard.open.desc")
            .category(ActionCategory.TELEMETRY)
            .actionType(com.devmod.actions.ActionType.OPEN_EXTERNAL)
            .menuPath("Root/Telemetry/Dashboard/Open")
            .icon(Items.MAP)
            .precondition(ActionPreconditions.requiresPermissionOrClient(2))
            .commandHint("devmod dashboard")
            .handler(DashboardCommand::handleOpenDashboard)
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.TELEMETRY_DASHBOARD_SERVER_START)
            .labelKey("devmod.action.telemetry.dashboard.start")
            .descriptionKey("devmod.action.telemetry.dashboard.start.desc")
            .category(ActionCategory.TELEMETRY)
            .menuPath("Root/Telemetry/Dashboard/Start")
            .icon(Items.REDSTONE)
            .precondition(ActionPreconditions.requiresPermissionOrClient(2))
            .commandHint("devmod dashboard start")
            .handler(context -> handleCommand(context, "devmod dashboard start", DashboardCommand::startServer))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.TELEMETRY_DASHBOARD_SERVER_STOP)
            .labelKey("devmod.action.telemetry.dashboard.stop")
            .descriptionKey("devmod.action.telemetry.dashboard.stop.desc")
            .category(ActionCategory.TELEMETRY)
            .menuPath("Root/Telemetry/Dashboard/Stop")
            .icon(Items.BARRIER)
            .precondition(ActionPreconditions.requiresPermissionOrClient(2))
            .commandHint("devmod dashboard stop")
            .handler(context -> handleCommand(context, "devmod dashboard stop", DashboardCommand::stopServer))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.TELEMETRY_DASHBOARD_SERVER_STATUS)
            .labelKey("devmod.action.telemetry.dashboard.status")
            .descriptionKey("devmod.action.telemetry.dashboard.status.desc")
            .category(ActionCategory.TELEMETRY)
            .menuPath("Root/Telemetry/Dashboard/Status")
            .icon(Items.CLOCK)
            .precondition(ActionPreconditions.requiresPermissionOrClient(2))
            .commandHint("devmod dashboard status")
            .handler(context -> handleCommand(context, "devmod dashboard status", DashboardCommand::showStatus))
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

    /**
     * Handler for OPEN_EXTERNAL action from radial menu.
     * Shows confirmation dialog with copy fallback on client, executes command on server.
     */
    private static void handleOpenDashboard(ActionContext context) {
        // If called from server command, use the original behavior
        if (context.getOrigin() == ActionOrigin.COMMAND && context.getCommandContext() != null) {
            openDashboard(context.getCommandContext());
            return;
        }

        // Client-side: use confirmation dialog
        if (context.isClientSide()) {
            TelemetryDashboardServer server = TelemetryDashboardServer.INSTANCE;

            // Start server if not running
            if (!server.isRunning()) {
                server.start();
            }

            if (server.isRunning()) {
                String url = server.getDashboardUrl();
                openDashboardConfirmationClientSafe(url);
            } else {
                // Server failed to start - show error
                context.sendFailure(Component.translatable("devmod.dashboard.start_failed"));
            }
        } else {
            // Server-side: execute command
            context.executeCommand("devmod dashboard");
        }
    }

    @FunctionalInterface
    private interface CommandHandler {
        void run(CommandContext<CommandSourceStack> context);
    }

    private static void openDashboardConfirmationClientSafe(String url) {
        try {
            Class<?> delegateClass = Class.forName("com.devmod.client.telemetry.TelemetryClientDelegate");
            java.lang.reflect.Method method = delegateClass.getMethod("openDashboardConfirmation", String.class);
            method.invoke(null, url);
        } catch (ClassNotFoundException e) {
            // Client delegate not available on dedicated servers.
        } catch (Exception e) {
            // Avoid spamming logs for client-only UI failures.
        }
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
        boolean duckdbEnabled = com.devmod.telemetry.duckdb.DuckDBTelemetryService.INSTANCE.isEnabled();
        source.sendSuccess(() -> Component.literal(
            "DuckDB: " + (duckdbEnabled ? "enabled" : "disabled")
        ), false);

        return 1;
    }
}
