package com.devmod.actions;

import java.util.concurrent.atomic.AtomicBoolean;

import net.minecraft.world.item.Items;

import com.devmod.arena.command.ArenaActionRegistry;
import com.devmod.debug.DebugCommand;
import com.devmod.gametest.TestHarnessCommands;
import com.devmod.mailbox.admin.MailboxCommands;
import com.devmod.telemetry.TelemetryReloadCommand;
import com.devmod.telemetry.dashboard.DashboardCommand;
import com.devmod.telemetry.dungeon.DungeonCommand;

public final class DevModActions {
    private static final AtomicBoolean REGISTERED = new AtomicBoolean(false);

    private DevModActions() {}

    public static void registerCommon() {
        if (!REGISTERED.compareAndSet(false, true)) {
            return;
        }
        registerCommandActions();
        registerServerActions();
    }

    private static void registerCommandActions() {
        // Admin command actions with proper gating:
        // - visibilityPredicate: hides from non-admins (Scenario 9)
        // - precondition: blocks execution with error feedback (Scenario 2)
        // - permissionLevel: explicit for contract documentation
        // - uiFeedback: CHAT since server sends feedback
        // - actionType: RUN_SERVER_COMMAND for telemetry

        ActionRegistry.register(RadialAction.builder(ActionIds.COMMAND_GAMEMODE_CREATIVE)
            .labelKey("devmod.action.command.gamemode_creative")
            .descriptionKey("devmod.action.command.gamemode_creative.desc")
            .category(ActionCategory.TOOLS)
            .actionType(ActionType.RUN_SERVER_COMMAND)
            .menuPath("Root/Tools/Commands/Gamemode Creative")
            .icon(Items.GRASS_BLOCK)
            .visibilityPredicate(ctx -> hasPermission(ctx, 2))
            .precondition(ActionPreconditions.requiresPermissionOrClient(2))
            .permissionLevel(2)
            .uiFeedback(RadialAction.UIFeedback.CHAT)
            .commandHint("gamemode creative")
            .handler(context -> context.executeCommand("gamemode creative"))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.COMMAND_GAMEMODE_SURVIVAL)
            .labelKey("devmod.action.command.gamemode_survival")
            .descriptionKey("devmod.action.command.gamemode_survival.desc")
            .category(ActionCategory.TOOLS)
            .actionType(ActionType.RUN_SERVER_COMMAND)
            .menuPath("Root/Tools/Commands/Gamemode Survival")
            .icon(Items.IRON_SWORD)
            .visibilityPredicate(ctx -> hasPermission(ctx, 2))
            .precondition(ActionPreconditions.requiresPermissionOrClient(2))
            .permissionLevel(2)
            .uiFeedback(RadialAction.UIFeedback.CHAT)
            .commandHint("gamemode survival")
            .handler(context -> context.executeCommand("gamemode survival"))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.COMMAND_HEAL)
            .labelKey("devmod.action.command.heal")
            .descriptionKey("devmod.action.command.heal.desc")
            .category(ActionCategory.TOOLS)
            .actionType(ActionType.RUN_SERVER_COMMAND)
            .menuPath("Root/Tools/Commands/Heal")
            .icon(Items.GOLDEN_APPLE)
            .visibilityPredicate(ctx -> hasPermission(ctx, 2))
            .precondition(ActionPreconditions.requiresPermissionOrClient(2))
            .permissionLevel(2)
            .uiFeedback(RadialAction.UIFeedback.CHAT)
            .commandHint("heal")
            .handler(context -> context.executeCommand("heal"))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.COMMAND_TIME_DAY)
            .labelKey("devmod.action.command.time_day")
            .descriptionKey("devmod.action.command.time_day.desc")
            .category(ActionCategory.TOOLS)
            .actionType(ActionType.RUN_SERVER_COMMAND)
            .menuPath("Root/Tools/Commands/Time Day")
            .icon(Items.SUNFLOWER)
            .visibilityPredicate(ctx -> hasPermission(ctx, 2))
            .precondition(ActionPreconditions.requiresPermissionOrClient(2))
            .permissionLevel(2)
            .uiFeedback(RadialAction.UIFeedback.CHAT)
            .commandHint("time set day")
            .handler(context -> context.executeCommand("time set day"))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.COMMAND_TIME_NIGHT)
            .labelKey("devmod.action.command.time_night")
            .descriptionKey("devmod.action.command.time_night.desc")
            .category(ActionCategory.TOOLS)
            .actionType(ActionType.RUN_SERVER_COMMAND)
            .menuPath("Root/Tools/Commands/Time Night")
            .icon(Items.CLOCK)
            .visibilityPredicate(ctx -> hasPermission(ctx, 2))
            .precondition(ActionPreconditions.requiresPermissionOrClient(2))
            .permissionLevel(2)
            .uiFeedback(RadialAction.UIFeedback.CHAT)
            .commandHint("time set night")
            .handler(context -> context.executeCommand("time set night"))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.COMMAND_WEATHER_CLEAR)
            .labelKey("devmod.action.command.weather_clear")
            .descriptionKey("devmod.action.command.weather_clear.desc")
            .category(ActionCategory.TOOLS)
            .actionType(ActionType.RUN_SERVER_COMMAND)
            .menuPath("Root/Tools/Commands/Weather Clear")
            .icon(Items.FEATHER)
            .visibilityPredicate(ctx -> hasPermission(ctx, 2))
            .precondition(ActionPreconditions.requiresPermissionOrClient(2))
            .permissionLevel(2)
            .uiFeedback(RadialAction.UIFeedback.CHAT)
            .commandHint("weather clear")
            .handler(context -> context.executeCommand("weather clear"))
            .build());
    }

    /**
     * Helper to check permission for visibility gating.
     */
    private static boolean hasPermission(ActionContext ctx, int level) {
        var player = ctx.getPlayer();
        return player != null && player.hasPermissions(level);
    }

    private static void registerServerActions() {
        ArenaActionRegistry.registerCommonActions();
        DebugCommand.registerActions();
        TelemetryReloadCommand.registerActions();
        DashboardCommand.registerActions();
        DungeonCommand.registerActions();
        TestHarnessCommands.registerActions();
        MailboxCommands.registerActions();
    }
}
