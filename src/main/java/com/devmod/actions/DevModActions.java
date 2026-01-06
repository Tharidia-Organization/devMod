package com.devmod.actions;

import java.util.concurrent.atomic.AtomicBoolean;

import net.minecraft.world.item.Items;

import com.devmod.arena.command.ArenaActionRegistry;
import com.devmod.debug.DebugCommand;
import com.devmod.endurance.LeaderboardCommandEvents;
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

        ActionRegistry.register(RadialAction.builder(ActionIds.COMMAND_NEXUS_RIFTSTAMP)
            .labelKey("devmod.action.command.nexus_riftstamp")
            .descriptionKey("devmod.action.command.nexus_riftstamp.desc")
            .category(ActionCategory.ADMIN)
            .actionType(ActionType.RUN_SERVER_COMMAND)
            .menuPath("Root/Nexus/Portals/RiftStamp")
            .icon(Items.ENDER_EYE)
            .visibilityPredicate(ctx -> hasPermission(ctx, 2))
            .precondition(ActionPreconditions.requiresPermissionOrClient(2))
            .permissionLevel(2)
            .uiFeedback(RadialAction.UIFeedback.CHAT)
            .commandHint("devmod nexus riftstamp")
            .handler(context -> context.executeCommand("devmod nexus riftstamp"))
            .build());

        // =====================================================================
        // NEXUS COMMANDS (Access + Admin)
        // =====================================================================

        ActionRegistry.register(RadialAction.builder(ActionIds.COMMAND_NEXUS_HELP)
            .labelKey("devmod.action.command.nexus_help")
            .descriptionKey("devmod.action.command.nexus_help.desc")
            .category(ActionCategory.TOOLS)
            .actionType(ActionType.RUN_SERVER_COMMAND)
            .menuPath("Root/Nexus/Info/Help")
            .icon(Items.PAPER)
            .uiFeedback(RadialAction.UIFeedback.CHAT)
            .commandHint("devmod nexus help")
            .handler(context -> context.executeCommand("devmod nexus help"))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.COMMAND_NEXUS_ZONES)
            .labelKey("devmod.action.command.nexus_zones")
            .descriptionKey("devmod.action.command.nexus_zones.desc")
            .category(ActionCategory.TOOLS)
            .actionType(ActionType.RUN_SERVER_COMMAND)
            .menuPath("Root/Nexus/Info/Zones")
            .icon(Items.BOOK)
            .uiFeedback(RadialAction.UIFeedback.CHAT)
            .commandHint("devmod nexus zones")
            .handler(context -> context.executeCommand("devmod nexus zones"))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.COMMAND_NEXUS_ENTER)
            .labelKey("devmod.action.command.nexus_enter")
            .descriptionKey("devmod.action.command.nexus_enter.desc")
            .category(ActionCategory.TOOLS)
            .actionType(ActionType.RUN_SERVER_COMMAND)
            .menuPath("Root/Nexus/Access/Enter Hub")
            .icon(Items.ENDER_PEARL)
            .uiFeedback(RadialAction.UIFeedback.CHAT)
            .commandHint("devmod nexus enter")
            .handler(context -> context.executeCommand("devmod nexus enter"))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.COMMAND_NEXUS_RETURN)
            .labelKey("devmod.action.command.nexus_return")
            .descriptionKey("devmod.action.command.nexus_return.desc")
            .category(ActionCategory.TOOLS)
            .actionType(ActionType.RUN_SERVER_COMMAND)
            .menuPath("Root/Nexus/Access/Return")
            .icon(Items.COMPASS)
            .uiFeedback(RadialAction.UIFeedback.CHAT)
            .commandHint("devmod nexus return")
            .handler(context -> context.executeCommand("devmod nexus return"))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.COMMAND_NEXUS_TP_HUB)
            .labelKey("devmod.action.command.nexus_tp_hub")
            .descriptionKey("devmod.action.command.nexus_tp_hub.desc")
            .category(ActionCategory.TOOLS)
            .actionType(ActionType.RUN_SERVER_COMMAND)
            .menuPath("Root/Nexus/Zones/Hub")
            .icon(Items.BEACON)
            .uiFeedback(RadialAction.UIFeedback.CHAT)
            .commandHint("devmod nexus tp hub")
            .handler(context -> context.executeCommand("devmod nexus tp hub"))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.COMMAND_NEXUS_TP_OVERVIEW)
            .labelKey("devmod.action.command.nexus_tp_overview")
            .descriptionKey("devmod.action.command.nexus_tp_overview.desc")
            .category(ActionCategory.TOOLS)
            .actionType(ActionType.RUN_SERVER_COMMAND)
            .menuPath("Root/Nexus/Zones/Overview Deck")
            .icon(Items.MAP)
            .uiFeedback(RadialAction.UIFeedback.CHAT)
            .commandHint("devmod nexus tp overview")
            .handler(context -> context.executeCommand("devmod nexus tp overview"))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.COMMAND_NEXUS_TP_COMBAT)
            .labelKey("devmod.action.command.nexus_tp_combat")
            .descriptionKey("devmod.action.command.nexus_tp_combat.desc")
            .category(ActionCategory.TOOLS)
            .actionType(ActionType.RUN_SERVER_COMMAND)
            .menuPath("Root/Nexus/Zones/Combat")
            .icon(Items.IRON_SWORD)
            .uiFeedback(RadialAction.UIFeedback.CHAT)
            .commandHint("devmod nexus tp combat")
            .handler(context -> context.executeCommand("devmod nexus tp combat"))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.COMMAND_NEXUS_TP_ARENA)
            .labelKey("devmod.action.command.nexus_tp_arena")
            .descriptionKey("devmod.action.command.nexus_tp_arena.desc")
            .category(ActionCategory.TOOLS)
            .actionType(ActionType.RUN_SERVER_COMMAND)
            .menuPath("Root/Nexus/Zones/Arena")
            .icon(Items.SHIELD)
            .uiFeedback(RadialAction.UIFeedback.CHAT)
            .commandHint("devmod nexus tp arena")
            .handler(context -> context.executeCommand("devmod nexus tp arena"))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.COMMAND_NEXUS_TP_UI)
            .labelKey("devmod.action.command.nexus_tp_ui")
            .descriptionKey("devmod.action.command.nexus_tp_ui.desc")
            .category(ActionCategory.TOOLS)
            .actionType(ActionType.RUN_SERVER_COMMAND)
            .menuPath("Root/Nexus/Zones/UI Lab")
            .icon(Items.COMPARATOR)
            .uiFeedback(RadialAction.UIFeedback.CHAT)
            .commandHint("devmod nexus tp ui")
            .handler(context -> context.executeCommand("devmod nexus tp ui"))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.COMMAND_NEXUS_TP_TELEMETRY)
            .labelKey("devmod.action.command.nexus_tp_telemetry")
            .descriptionKey("devmod.action.command.nexus_tp_telemetry.desc")
            .category(ActionCategory.TOOLS)
            .actionType(ActionType.RUN_SERVER_COMMAND)
            .menuPath("Root/Nexus/Zones/Telemetry Lab")
            .icon(Items.WRITABLE_BOOK)
            .uiFeedback(RadialAction.UIFeedback.CHAT)
            .commandHint("devmod nexus tp telemetry")
            .handler(context -> context.executeCommand("devmod nexus tp telemetry"))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.COMMAND_NEXUS_TP_SHOWCASE)
            .labelKey("devmod.action.command.nexus_tp_showcase")
            .descriptionKey("devmod.action.command.nexus_tp_showcase.desc")
            .category(ActionCategory.TOOLS)
            .actionType(ActionType.RUN_SERVER_COMMAND)
            .menuPath("Root/Nexus/Zones/Showcase")
            .icon(Items.ITEM_FRAME)
            .uiFeedback(RadialAction.UIFeedback.CHAT)
            .commandHint("devmod nexus tp showcase")
            .handler(context -> context.executeCommand("devmod nexus tp showcase"))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.COMMAND_NEXUS_TP_INTEGRATION)
            .labelKey("devmod.action.command.nexus_tp_integration")
            .descriptionKey("devmod.action.command.nexus_tp_integration.desc")
            .category(ActionCategory.TOOLS)
            .actionType(ActionType.RUN_SERVER_COMMAND)
            .menuPath("Root/Nexus/Zones/Integration Bay")
            .icon(Items.ANVIL)
            .uiFeedback(RadialAction.UIFeedback.CHAT)
            .commandHint("devmod nexus tp integration")
            .handler(context -> context.executeCommand("devmod nexus tp integration"))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.COMMAND_NEXUS_TP_SANDBOX)
            .labelKey("devmod.action.command.nexus_tp_sandbox")
            .descriptionKey("devmod.action.command.nexus_tp_sandbox.desc")
            .category(ActionCategory.TOOLS)
            .actionType(ActionType.RUN_SERVER_COMMAND)
            .menuPath("Root/Nexus/Zones/Sandbox")
            .icon(Items.SAND)
            .uiFeedback(RadialAction.UIFeedback.CHAT)
            .commandHint("devmod nexus tp sandbox")
            .handler(context -> context.executeCommand("devmod nexus tp sandbox"))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.COMMAND_NEXUS_TP_MECHANICS)
            .labelKey("devmod.action.command.nexus_tp_mechanics")
            .descriptionKey("devmod.action.command.nexus_tp_mechanics.desc")
            .category(ActionCategory.TOOLS)
            .actionType(ActionType.RUN_SERVER_COMMAND)
            .menuPath("Root/Nexus/Zones/Mechanics")
            .icon(Items.PISTON)
            .uiFeedback(RadialAction.UIFeedback.CHAT)
            .commandHint("devmod nexus tp mechanics")
            .handler(context -> context.executeCommand("devmod nexus tp mechanics"))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.COMMAND_NEXUS_STATUS)
            .labelKey("devmod.action.command.nexus_status")
            .descriptionKey("devmod.action.command.nexus_status.desc")
            .category(ActionCategory.ADMIN)
            .actionType(ActionType.RUN_SERVER_COMMAND)
            .menuPath("Root/Nexus/Admin/Status")
            .icon(Items.BOOK)
            .visibilityPredicate(ctx -> hasPermission(ctx, 2))
            .precondition(ActionPreconditions.requiresPermissionOrClient(2))
            .permissionLevel(2)
            .uiFeedback(RadialAction.UIFeedback.CHAT)
            .commandHint("devmod nexus status")
            .handler(context -> context.executeCommand("devmod nexus status"))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.COMMAND_NEXUS_REBUILD)
            .labelKey("devmod.action.command.nexus_rebuild")
            .descriptionKey("devmod.action.command.nexus_rebuild.desc")
            .category(ActionCategory.ADMIN)
            .actionType(ActionType.RUN_SERVER_COMMAND)
            .menuPath("Root/Nexus/Admin/Rebuild")
            .icon(Items.DIAMOND_PICKAXE)
            .visibilityPredicate(ctx -> hasPermission(ctx, 4))
            .precondition(ActionPreconditions.requiresPermissionOrClient(4))
            .permissionLevel(4)
            .requiresConfirm(true)
            .uiFeedback(RadialAction.UIFeedback.CHAT)
            .commandHint("devmod nexus rebuild")
            .handler(context -> context.executeCommand("devmod nexus rebuild"))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.COMMAND_NEXUS_LOCK)
            .labelKey("devmod.action.command.nexus_lock")
            .descriptionKey("devmod.action.command.nexus_lock.desc")
            .category(ActionCategory.ADMIN)
            .actionType(ActionType.RUN_SERVER_COMMAND)
            .menuPath("Root/Nexus/Admin/Lock")
            .icon(Items.IRON_BARS)
            .visibilityPredicate(ctx -> hasPermission(ctx, 4))
            .precondition(ActionPreconditions.requiresPermissionOrClient(4))
            .permissionLevel(4)
            .requiresConfirm(true)
            .uiFeedback(RadialAction.UIFeedback.CHAT)
            .commandHint("devmod nexus lock")
            .handler(context -> context.executeCommand("devmod nexus lock"))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.COMMAND_NEXUS_UNLOCK)
            .labelKey("devmod.action.command.nexus_unlock")
            .descriptionKey("devmod.action.command.nexus_unlock.desc")
            .category(ActionCategory.ADMIN)
            .actionType(ActionType.RUN_SERVER_COMMAND)
            .menuPath("Root/Nexus/Admin/Unlock")
            .icon(Items.IRON_DOOR)
            .visibilityPredicate(ctx -> hasPermission(ctx, 4))
            .precondition(ActionPreconditions.requiresPermissionOrClient(4))
            .permissionLevel(4)
            .requiresConfirm(true)
            .uiFeedback(RadialAction.UIFeedback.CHAT)
            .commandHint("devmod nexus unlock")
            .handler(context -> context.executeCommand("devmod nexus unlock"))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.COMMAND_NEXUS_AVATAR_STATUS)
            .labelKey("devmod.action.command.nexus_avatar_status")
            .descriptionKey("devmod.action.command.nexus_avatar_status.desc")
            .category(ActionCategory.ADMIN)
            .actionType(ActionType.RUN_SERVER_COMMAND)
            .menuPath("Root/Nexus/Admin/Avatar Status")
            .icon(Items.PLAYER_HEAD)
            .uiFeedback(RadialAction.UIFeedback.CHAT)
            .commandHint("devmod nexus avatar status")
            .handler(context -> context.executeCommand("devmod nexus avatar status"))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.COMMAND_NEXUS_AVATAR_SPAWN)
            .labelKey("devmod.action.command.nexus_avatar_spawn")
            .descriptionKey("devmod.action.command.nexus_avatar_spawn.desc")
            .category(ActionCategory.ADMIN)
            .actionType(ActionType.RUN_SERVER_COMMAND)
            .menuPath("Root/Nexus/Admin/Avatar Spawn")
            .icon(Items.TOTEM_OF_UNDYING)
            .visibilityPredicate(ctx -> hasPermission(ctx, 2))
            .precondition(ActionPreconditions.requiresPermissionOrClient(2))
            .permissionLevel(2)
            .uiFeedback(RadialAction.UIFeedback.CHAT)
            .commandHint("devmod nexus avatar spawn")
            .handler(context -> context.executeCommand("devmod nexus avatar spawn"))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.COMMAND_NEXUS_AVATAR_REMOVE)
            .labelKey("devmod.action.command.nexus_avatar_remove")
            .descriptionKey("devmod.action.command.nexus_avatar_remove.desc")
            .category(ActionCategory.ADMIN)
            .actionType(ActionType.RUN_SERVER_COMMAND)
            .menuPath("Root/Nexus/Admin/Avatar Remove")
            .icon(Items.BONE)
            .visibilityPredicate(ctx -> hasPermission(ctx, 2))
            .precondition(ActionPreconditions.requiresPermissionOrClient(2))
            .permissionLevel(2)
            .uiFeedback(RadialAction.UIFeedback.CHAT)
            .commandHint("devmod nexus avatar remove")
            .handler(context -> context.executeCommand("devmod nexus avatar remove"))
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
        LeaderboardCommandEvents.registerActions();
    }
}
