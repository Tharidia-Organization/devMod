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
            .menuPath("Root/Nexus/Zones/Core/Spawn")
            .icon(Items.BEACON)
            .uiFeedback(RadialAction.UIFeedback.CHAT)
            .commandHint("devmod nexus tp spawn")
            .handler(context -> context.executeCommand("devmod nexus tp spawn"))
            .build());

        // --- Combat & Physics ---
        ActionRegistry.register(RadialAction.builder(ActionIds.COMMAND_NEXUS_TP_COMBAT_LAB)
            .labelKey("devmod.action.command.nexus_tp_combat_lab")
            .descriptionKey("devmod.action.command.nexus_tp_combat_lab.desc")
            .category(ActionCategory.TOOLS)
            .actionType(ActionType.RUN_SERVER_COMMAND)
            .menuPath("Root/Nexus/Zones/Combat/Combat Lab")
            .icon(Items.IRON_SWORD)
            .uiFeedback(RadialAction.UIFeedback.CHAT)
            .commandHint("devmod nexus tp combat_lab")
            .handler(context -> context.executeCommand("devmod nexus tp combat_lab"))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.COMMAND_NEXUS_TP_ABILITIES_LAB)
            .labelKey("devmod.action.command.nexus_tp_abilities_lab")
            .descriptionKey("devmod.action.command.nexus_tp_abilities_lab.desc")
            .category(ActionCategory.TOOLS)
            .actionType(ActionType.RUN_SERVER_COMMAND)
            .menuPath("Root/Nexus/Zones/Combat/Abilities Lab")
            .icon(Items.FEATHER)
            .uiFeedback(RadialAction.UIFeedback.CHAT)
            .commandHint("devmod nexus tp abilities_lab")
            .handler(context -> context.executeCommand("devmod nexus tp abilities_lab"))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.COMMAND_NEXUS_TP_BOSS_ARENA)
            .labelKey("devmod.action.command.nexus_tp_boss_arena")
            .descriptionKey("devmod.action.command.nexus_tp_boss_arena.desc")
            .category(ActionCategory.TOOLS)
            .actionType(ActionType.RUN_SERVER_COMMAND)
            .menuPath("Root/Nexus/Zones/Combat/Boss Arena")
            .icon(Items.DRAGON_HEAD)
            .uiFeedback(RadialAction.UIFeedback.CHAT)
            .commandHint("devmod nexus tp boss_arena")
            .handler(context -> context.executeCommand("devmod nexus tp boss_arena"))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.COMMAND_NEXUS_TP_COLLISION_LAB)
            .labelKey("devmod.action.command.nexus_tp_collision_lab")
            .descriptionKey("devmod.action.command.nexus_tp_collision_lab.desc")
            .category(ActionCategory.TOOLS)
            .actionType(ActionType.RUN_SERVER_COMMAND)
            .menuPath("Root/Nexus/Zones/Combat/Collision Lab")
            .icon(Items.ARMOR_STAND)
            .uiFeedback(RadialAction.UIFeedback.CHAT)
            .commandHint("devmod nexus tp collision_lab")
            .handler(context -> context.executeCommand("devmod nexus tp collision_lab"))
            .build());

        // --- Systems ---
        ActionRegistry.register(RadialAction.builder(ActionIds.COMMAND_NEXUS_TP_PORTAL_LAB)
            .labelKey("devmod.action.command.nexus_tp_portal_lab")
            .descriptionKey("devmod.action.command.nexus_tp_portal_lab.desc")
            .category(ActionCategory.TOOLS)
            .actionType(ActionType.RUN_SERVER_COMMAND)
            .menuPath("Root/Nexus/Zones/Systems/Portal Lab")
            .icon(Items.ENDER_PEARL)
            .uiFeedback(RadialAction.UIFeedback.CHAT)
            .commandHint("devmod nexus tp portal_lab")
            .handler(context -> context.executeCommand("devmod nexus tp portal_lab"))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.COMMAND_NEXUS_TP_NPC_LAB)
            .labelKey("devmod.action.command.nexus_tp_npc_lab")
            .descriptionKey("devmod.action.command.nexus_tp_npc_lab.desc")
            .category(ActionCategory.TOOLS)
            .actionType(ActionType.RUN_SERVER_COMMAND)
            .menuPath("Root/Nexus/Zones/Systems/NPC Lab")
            .icon(Items.VILLAGER_SPAWN_EGG)
            .uiFeedback(RadialAction.UIFeedback.CHAT)
            .commandHint("devmod nexus tp npc_lab")
            .handler(context -> context.executeCommand("devmod nexus tp npc_lab"))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.COMMAND_NEXUS_TP_QUEST_TESTING)
            .labelKey("devmod.action.command.nexus_tp_quest_testing")
            .descriptionKey("devmod.action.command.nexus_tp_quest_testing.desc")
            .category(ActionCategory.TOOLS)
            .actionType(ActionType.RUN_SERVER_COMMAND)
            .menuPath("Root/Nexus/Zones/Systems/Quest Testing")
            .icon(Items.WRITABLE_BOOK)
            .uiFeedback(RadialAction.UIFeedback.CHAT)
            .commandHint("devmod nexus tp quest_testing")
            .handler(context -> context.executeCommand("devmod nexus tp quest_testing"))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.COMMAND_NEXUS_TP_ARENA_BUILDER)
            .labelKey("devmod.action.command.nexus_tp_arena_builder")
            .descriptionKey("devmod.action.command.nexus_tp_arena_builder.desc")
            .category(ActionCategory.TOOLS)
            .actionType(ActionType.RUN_SERVER_COMMAND)
            .menuPath("Root/Nexus/Zones/Systems/Arena Builder")
            .icon(Items.STRUCTURE_BLOCK)
            .uiFeedback(RadialAction.UIFeedback.CHAT)
            .commandHint("devmod nexus tp arena_builder")
            .handler(context -> context.executeCommand("devmod nexus tp arena_builder"))
            .build());

        // --- Tools ---
        ActionRegistry.register(RadialAction.builder(ActionIds.COMMAND_NEXUS_TP_VFX_STUDIO)
            .labelKey("devmod.action.command.nexus_tp_vfx_studio")
            .descriptionKey("devmod.action.command.nexus_tp_vfx_studio.desc")
            .category(ActionCategory.TOOLS)
            .actionType(ActionType.RUN_SERVER_COMMAND)
            .menuPath("Root/Nexus/Zones/Tools/VFX Studio")
            .icon(Items.BLAZE_POWDER)
            .uiFeedback(RadialAction.UIFeedback.CHAT)
            .commandHint("devmod nexus tp vfx_studio")
            .handler(context -> context.executeCommand("devmod nexus tp vfx_studio"))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.COMMAND_NEXUS_TP_ITEM_WORKSHOP)
            .labelKey("devmod.action.command.nexus_tp_item_workshop")
            .descriptionKey("devmod.action.command.nexus_tp_item_workshop.desc")
            .category(ActionCategory.TOOLS)
            .actionType(ActionType.RUN_SERVER_COMMAND)
            .menuPath("Root/Nexus/Zones/Tools/Item Workshop")
            .icon(Items.ANVIL)
            .uiFeedback(RadialAction.UIFeedback.CHAT)
            .commandHint("devmod nexus tp item_workshop")
            .handler(context -> context.executeCommand("devmod nexus tp item_workshop"))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.COMMAND_NEXUS_TP_CONFIG_ROOM)
            .labelKey("devmod.action.command.nexus_tp_config_room")
            .descriptionKey("devmod.action.command.nexus_tp_config_room.desc")
            .category(ActionCategory.TOOLS)
            .actionType(ActionType.RUN_SERVER_COMMAND)
            .menuPath("Root/Nexus/Zones/Tools/Config Room")
            .icon(Items.COMPARATOR)
            .uiFeedback(RadialAction.UIFeedback.CHAT)
            .commandHint("devmod nexus tp config_room")
            .handler(context -> context.executeCommand("devmod nexus tp config_room"))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.COMMAND_NEXUS_TP_HUD_TESTING)
            .labelKey("devmod.action.command.nexus_tp_hud_testing")
            .descriptionKey("devmod.action.command.nexus_tp_hud_testing.desc")
            .category(ActionCategory.TOOLS)
            .actionType(ActionType.RUN_SERVER_COMMAND)
            .menuPath("Root/Nexus/Zones/Tools/HUD Testing")
            .icon(Items.REDSTONE_LAMP)
            .uiFeedback(RadialAction.UIFeedback.CHAT)
            .commandHint("devmod nexus tp hud_testing")
            .handler(context -> context.executeCommand("devmod nexus tp hud_testing"))
            .build());

        // --- Misc ---
        ActionRegistry.register(RadialAction.builder(ActionIds.COMMAND_NEXUS_TP_SANDBOX)
            .labelKey("devmod.action.command.nexus_tp_sandbox")
            .descriptionKey("devmod.action.command.nexus_tp_sandbox.desc")
            .category(ActionCategory.TOOLS)
            .actionType(ActionType.RUN_SERVER_COMMAND)
            .menuPath("Root/Nexus/Zones/Misc/Sandbox")
            .icon(Items.SAND)
            .uiFeedback(RadialAction.UIFeedback.CHAT)
            .commandHint("devmod nexus tp sandbox")
            .handler(context -> context.executeCommand("devmod nexus tp sandbox"))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.COMMAND_NEXUS_TP_ADMIN_TOOLS)
            .labelKey("devmod.action.command.nexus_tp_admin_tools")
            .descriptionKey("devmod.action.command.nexus_tp_admin_tools.desc")
            .category(ActionCategory.TOOLS)
            .actionType(ActionType.RUN_SERVER_COMMAND)
            .menuPath("Root/Nexus/Zones/Misc/Admin Tools")
            .icon(Items.COMMAND_BLOCK)
            .uiFeedback(RadialAction.UIFeedback.CHAT)
            .commandHint("devmod nexus tp admin_tools")
            .handler(context -> context.executeCommand("devmod nexus tp admin_tools"))
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
