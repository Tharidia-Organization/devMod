package com.devmod.actions.domains;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.devmod.actions.ActionCategory;
import com.devmod.actions.ActionIds;
import com.devmod.actions.ActionOrigin;
import com.devmod.actions.ActionType;
import com.devmod.actions.catalog.ActionSpec;
import com.devmod.actions.catalog.ActionSpec.ActionChannel;
import com.devmod.actions.catalog.ActionSpec.BindingMeta;

/**
 * V2 domain registrar for server command actions (gamemode, heal, time, weather,
 * and all Nexus commands from DevModActions).
 *
 * <p>Parallel to the existing {@link com.devmod.actions.DevModActions#registerCommon()}
 * command registration. Does NOT replace it yet.
 */
public final class CommandDomainRegistrar implements DomainRegistrar {

    @Override
    public String domainName() {
        return "commands";
    }

    @Override
    public List<ActionSpec> getActionSpecs() {
        List<ActionSpec> specs = new ArrayList<>();

        // --- Vanilla commands (perm 2) ---
        specs.add(cmd(ActionIds.COMMAND_GAMEMODE_CREATIVE, ActionCategory.TOOLS, 2,
            "devmod.action.command.gamemode_creative", "devmod.action.command.gamemode_creative.desc",
            "minecraft:grass_block", "Root/Tools/Commands/Gamemode Creative", "gamemode creative", false));
        specs.add(cmd(ActionIds.COMMAND_GAMEMODE_SURVIVAL, ActionCategory.TOOLS, 2,
            "devmod.action.command.gamemode_survival", "devmod.action.command.gamemode_survival.desc",
            "minecraft:iron_sword", "Root/Tools/Commands/Gamemode Survival", "gamemode survival", false));
        specs.add(cmd(ActionIds.COMMAND_HEAL, ActionCategory.TOOLS, 2,
            "devmod.action.command.heal", "devmod.action.command.heal.desc",
            "minecraft:golden_apple", "Root/Tools/Commands/Heal", "heal", false));
        specs.add(cmd(ActionIds.COMMAND_TIME_DAY, ActionCategory.TOOLS, 2,
            "devmod.action.command.time_day", "devmod.action.command.time_day.desc",
            "minecraft:sunflower", "Root/Tools/Commands/Time Day", "time set day", false));
        specs.add(cmd(ActionIds.COMMAND_TIME_NIGHT, ActionCategory.TOOLS, 2,
            "devmod.action.command.time_night", "devmod.action.command.time_night.desc",
            "minecraft:clock", "Root/Tools/Commands/Time Night", "time set night", false));
        specs.add(cmd(ActionIds.COMMAND_WEATHER_CLEAR, ActionCategory.TOOLS, 2,
            "devmod.action.command.weather_clear", "devmod.action.command.weather_clear.desc",
            "minecraft:feather", "Root/Tools/Commands/Weather Clear", "weather clear", false));

        // --- Nexus: Riftstamp (perm 2) ---
        specs.add(cmd(ActionIds.COMMAND_NEXUS_RIFTSTAMP, ActionCategory.ADMIN, 2,
            "devmod.action.command.nexus_riftstamp", "devmod.action.command.nexus_riftstamp.desc",
            "minecraft:ender_eye", "Root/Nexus/Portals/RiftStamp", "devmod nexus riftstamp", false));

        // --- Nexus: Info / Access (perm 0) ---
        specs.add(cmd(ActionIds.COMMAND_NEXUS_HELP, ActionCategory.TOOLS, 0,
            "devmod.action.command.nexus_help", "devmod.action.command.nexus_help.desc",
            "minecraft:paper", "Root/Nexus/Info/Help", "devmod nexus help", false));
        specs.add(cmd(ActionIds.COMMAND_NEXUS_ZONES, ActionCategory.TOOLS, 0,
            "devmod.action.command.nexus_zones", "devmod.action.command.nexus_zones.desc",
            "minecraft:book", "Root/Nexus/Info/Zones", "devmod nexus zones", false));
        specs.add(cmd(ActionIds.COMMAND_NEXUS_ENTER, ActionCategory.TOOLS, 0,
            "devmod.action.command.nexus_enter", "devmod.action.command.nexus_enter.desc",
            "minecraft:ender_pearl", "Root/Nexus/Access/Enter Hub", "devmod nexus enter", false));
        specs.add(cmd(ActionIds.COMMAND_NEXUS_RETURN, ActionCategory.TOOLS, 0,
            "devmod.action.command.nexus_return", "devmod.action.command.nexus_return.desc",
            "minecraft:compass", "Root/Nexus/Access/Return", "devmod nexus return", false));

        // --- Nexus: Zones (perm 0) ---
        specs.add(cmd(ActionIds.COMMAND_NEXUS_TP_HUB, ActionCategory.TOOLS, 0,
            "devmod.action.command.nexus_tp_hub", "devmod.action.command.nexus_tp_hub.desc",
            "minecraft:beacon", "Root/Nexus/Zones/Core/Spawn", "devmod nexus tp spawn", false));
        specs.add(cmd(ActionIds.COMMAND_NEXUS_TP_TUTORIAL, ActionCategory.TOOLS, 0,
            "devmod.action.command.nexus_tp_tutorial", "devmod.action.command.nexus_tp_tutorial.desc",
            "minecraft:book", "Root/Nexus/Zones/Core/Tutorial", "devmod nexus tp tutorial", false));
        specs.add(cmd(ActionIds.COMMAND_NEXUS_TP_GATE_PROGRESSION, ActionCategory.TOOLS, 0,
            "devmod.action.command.nexus_tp_gate_progression", "devmod.action.command.nexus_tp_gate_progression.desc",
            "minecraft:end_portal_frame", "Root/Nexus/Zones/Core/Gate Progression", "devmod nexus tp gate_progression", false));
        specs.add(cmd(ActionIds.COMMAND_NEXUS_TP_CLASSES, ActionCategory.TOOLS, 0,
            "devmod.action.command.nexus_tp_classes", "devmod.action.command.nexus_tp_classes.desc",
            "minecraft:enchanting_table", "Root/Nexus/Zones/Core/Classes System", "devmod nexus tp classes", false));
        specs.add(cmd(ActionIds.COMMAND_NEXUS_TP_OVERVIEW, ActionCategory.TOOLS, 0,
            "devmod.action.command.nexus_tp_overview", "devmod.action.command.nexus_tp_overview.desc",
            "minecraft:spyglass", "Root/Nexus/Zones/Labs/Overview Deck", "devmod nexus tp overview", false));
        specs.add(cmd(ActionIds.COMMAND_NEXUS_TP_COMBAT, ActionCategory.TOOLS, 0,
            "devmod.action.command.nexus_tp_combat", "devmod.action.command.nexus_tp_combat.desc",
            "minecraft:iron_sword", "Root/Nexus/Zones/Labs/Combat", "devmod nexus tp combat", false));
        specs.add(cmd(ActionIds.COMMAND_NEXUS_TP_ARENA, ActionCategory.TOOLS, 0,
            "devmod.action.command.nexus_tp_arena", "devmod.action.command.nexus_tp_arena.desc",
            "minecraft:shield", "Root/Nexus/Zones/Labs/Arena", "devmod nexus tp arena", false));
        specs.add(cmd(ActionIds.COMMAND_NEXUS_TP_UI, ActionCategory.TOOLS, 0,
            "devmod.action.command.nexus_tp_ui", "devmod.action.command.nexus_tp_ui.desc",
            "minecraft:comparator", "Root/Nexus/Zones/Labs/UI Lab", "devmod nexus tp ui", false));
        specs.add(cmd(ActionIds.COMMAND_NEXUS_TP_TELEMETRY, ActionCategory.TOOLS, 0,
            "devmod.action.command.nexus_tp_telemetry", "devmod.action.command.nexus_tp_telemetry.desc",
            "minecraft:redstone_lamp", "Root/Nexus/Zones/Labs/Telemetry Lab", "devmod nexus tp telemetry", false));
        specs.add(cmd(ActionIds.COMMAND_NEXUS_TP_SHOWCASE, ActionCategory.TOOLS, 0,
            "devmod.action.command.nexus_tp_showcase", "devmod.action.command.nexus_tp_showcase.desc",
            "minecraft:item_frame", "Root/Nexus/Zones/Labs/Showcase", "devmod nexus tp showcase", false));
        specs.add(cmd(ActionIds.COMMAND_NEXUS_TP_INTEGRATION, ActionCategory.TOOLS, 0,
            "devmod.action.command.nexus_tp_integration", "devmod.action.command.nexus_tp_integration.desc",
            "minecraft:anvil", "Root/Nexus/Zones/Labs/Integration Bay", "devmod nexus tp integration", false));
        specs.add(cmd(ActionIds.COMMAND_NEXUS_TP_SANDBOX, ActionCategory.TOOLS, 0,
            "devmod.action.command.nexus_tp_sandbox", "devmod.action.command.nexus_tp_sandbox.desc",
            "minecraft:sand", "Root/Nexus/Zones/Labs/Sandbox", "devmod nexus tp sandbox", false));
        specs.add(cmd(ActionIds.COMMAND_NEXUS_TP_MECHANICS, ActionCategory.TOOLS, 0,
            "devmod.action.command.nexus_tp_mechanics", "devmod.action.command.nexus_tp_mechanics.desc",
            "minecraft:redstone_block", "Root/Nexus/Zones/Labs/Mechanics", "devmod nexus tp mechanics", false));
        specs.add(cmd(ActionIds.COMMAND_NEXUS_TP_BUILDING_WEST, ActionCategory.TOOLS, 0,
            "devmod.action.command.nexus_tp_building_west", "devmod.action.command.nexus_tp_building_west.desc",
            "minecraft:oak_planks", "Root/Nexus/Zones/Ring 1/Building West", "devmod nexus tp building_west", false));
        specs.add(cmd(ActionIds.COMMAND_NEXUS_TP_BUILDING_EAST, ActionCategory.TOOLS, 0,
            "devmod.action.command.nexus_tp_building_east", "devmod.action.command.nexus_tp_building_east.desc",
            "minecraft:stone_bricks", "Root/Nexus/Zones/Ring 2/Building East", "devmod nexus tp building_east", false));
        specs.add(cmd(ActionIds.COMMAND_NEXUS_TP_QUEST_NORTHEAST, ActionCategory.TOOLS, 0,
            "devmod.action.command.nexus_tp_quest_northeast", "devmod.action.command.nexus_tp_quest_northeast.desc",
            "minecraft:writable_book", "Root/Nexus/Zones/Ring 1/Quest Hub NE", "devmod nexus tp quest_northeast", false));
        specs.add(cmd(ActionIds.COMMAND_NEXUS_TP_QUEST_NORTHWEST, ActionCategory.TOOLS, 0,
            "devmod.action.command.nexus_tp_quest_northwest", "devmod.action.command.nexus_tp_quest_northwest.desc",
            "minecraft:book", "Root/Nexus/Zones/Ring 1/Quest Hub NW", "devmod nexus tp quest_northwest", false));
        specs.add(cmd(ActionIds.COMMAND_NEXUS_TP_WAR_HUB_EAST, ActionCategory.TOOLS, 0,
            "devmod.action.command.nexus_tp_war_hub_east", "devmod.action.command.nexus_tp_war_hub_east.desc",
            "minecraft:iron_sword", "Root/Nexus/Zones/Ring 1/War Hub East", "devmod nexus tp war_hub_east", false));
        specs.add(cmd(ActionIds.COMMAND_NEXUS_TP_WAR_HUB_WEST, ActionCategory.TOOLS, 0,
            "devmod.action.command.nexus_tp_war_hub_west", "devmod.action.command.nexus_tp_war_hub_west.desc",
            "minecraft:shield", "Root/Nexus/Zones/Ring 1/War Hub West", "devmod nexus tp war_hub_west", false));
        specs.add(cmd(ActionIds.COMMAND_NEXUS_TP_ECONOMIA_EAST, ActionCategory.TOOLS, 0,
            "devmod.action.command.nexus_tp_economia_east", "devmod.action.command.nexus_tp_economia_east.desc",
            "minecraft:emerald", "Root/Nexus/Zones/Ring 2/Economia East", "devmod nexus tp economia_east", false));
        specs.add(cmd(ActionIds.COMMAND_NEXUS_TP_ECONOMIA_WEST, ActionCategory.TOOLS, 0,
            "devmod.action.command.nexus_tp_economia_west", "devmod.action.command.nexus_tp_economia_west.desc",
            "minecraft:gold_ingot", "Root/Nexus/Zones/Ring 2/Economia West", "devmod nexus tp economia_west", false));
        specs.add(cmd(ActionIds.COMMAND_NEXUS_TP_TOWN_MANAGEMENT, ActionCategory.TOOLS, 0,
            "devmod.action.command.nexus_tp_town_management", "devmod.action.command.nexus_tp_town_management.desc",
            "minecraft:bell", "Root/Nexus/Zones/Ring 2/Town & Politics", "devmod nexus tp town_management", false));
        specs.add(cmd(ActionIds.COMMAND_NEXUS_TP_EVENTI, ActionCategory.TOOLS, 0,
            "devmod.action.command.nexus_tp_eventi", "devmod.action.command.nexus_tp_eventi.desc",
            "minecraft:firework_rocket", "Root/Nexus/Zones/Ring 2/Eventi", "devmod nexus tp eventi", false));
        specs.add(cmd(ActionIds.COMMAND_NEXUS_TP_DM_MOD, ActionCategory.TOOLS, 0,
            "devmod.action.command.nexus_tp_dm_mod", "devmod.action.command.nexus_tp_dm_mod.desc",
            "minecraft:command_block", "Root/Nexus/Zones/Ring 2/DM Mod", "devmod nexus tp dm_mod", false));

        // --- Nexus Admin (perm 2-4) ---
        specs.add(cmd(ActionIds.COMMAND_NEXUS_STATUS, ActionCategory.ADMIN, 2,
            "devmod.action.command.nexus_status", "devmod.action.command.nexus_status.desc",
            "minecraft:book", "Root/Nexus/Admin/Status", "devmod nexus status", false));
        specs.add(cmd(ActionIds.COMMAND_NEXUS_REBUILD, ActionCategory.ADMIN, 4,
            "devmod.action.command.nexus_rebuild", "devmod.action.command.nexus_rebuild.desc",
            "minecraft:diamond_pickaxe", "Root/Nexus/Admin/Rebuild", "devmod nexus rebuild", true));
        specs.add(cmd(ActionIds.COMMAND_NEXUS_LOCK, ActionCategory.ADMIN, 4,
            "devmod.action.command.nexus_lock", "devmod.action.command.nexus_lock.desc",
            "minecraft:iron_bars", "Root/Nexus/Admin/Lock", "devmod nexus lock", true));
        specs.add(cmd(ActionIds.COMMAND_NEXUS_UNLOCK, ActionCategory.ADMIN, 4,
            "devmod.action.command.nexus_unlock", "devmod.action.command.nexus_unlock.desc",
            "minecraft:iron_door", "Root/Nexus/Admin/Unlock", "devmod nexus unlock", true));
        specs.add(cmd(ActionIds.COMMAND_NEXUS_AVATAR_STATUS, ActionCategory.ADMIN, 0,
            "devmod.action.command.nexus_avatar_status", "devmod.action.command.nexus_avatar_status.desc",
            "minecraft:player_head", "Root/Nexus/Admin/Avatar Status", "devmod nexus avatar status", false));
        specs.add(cmd(ActionIds.COMMAND_NEXUS_AVATAR_SPAWN, ActionCategory.ADMIN, 2,
            "devmod.action.command.nexus_avatar_spawn", "devmod.action.command.nexus_avatar_spawn.desc",
            "minecraft:totem_of_undying", "Root/Nexus/Admin/Avatar Spawn", "devmod nexus avatar spawn", false));
        specs.add(cmd(ActionIds.COMMAND_NEXUS_AVATAR_REMOVE, ActionCategory.ADMIN, 2,
            "devmod.action.command.nexus_avatar_remove", "devmod.action.command.nexus_avatar_remove.desc",
            "minecraft:bone", "Root/Nexus/Admin/Avatar Remove", "devmod nexus avatar remove", false));

        return specs;
    }

    @Override
    public void registerHandlers(HandlerRegistry registry) {
        // Vanilla commands
        registry.register(ActionIds.COMMAND_GAMEMODE_CREATIVE, ctx -> ctx.executeCommand("gamemode creative"));
        registry.register(ActionIds.COMMAND_GAMEMODE_SURVIVAL, ctx -> ctx.executeCommand("gamemode survival"));
        registry.register(ActionIds.COMMAND_HEAL, ctx -> ctx.executeCommand("heal"));
        registry.register(ActionIds.COMMAND_TIME_DAY, ctx -> ctx.executeCommand("time set day"));
        registry.register(ActionIds.COMMAND_TIME_NIGHT, ctx -> ctx.executeCommand("time set night"));
        registry.register(ActionIds.COMMAND_WEATHER_CLEAR, ctx -> ctx.executeCommand("weather clear"));

        // Nexus commands
        registry.register(ActionIds.COMMAND_NEXUS_RIFTSTAMP, ctx -> ctx.executeCommand("devmod nexus riftstamp"));
        registry.register(ActionIds.COMMAND_NEXUS_HELP, ctx -> ctx.executeCommand("devmod nexus help"));
        registry.register(ActionIds.COMMAND_NEXUS_ZONES, ctx -> ctx.executeCommand("devmod nexus zones"));
        registry.register(ActionIds.COMMAND_NEXUS_ENTER, ctx -> ctx.executeCommand("devmod nexus enter"));
        registry.register(ActionIds.COMMAND_NEXUS_RETURN, ctx -> ctx.executeCommand("devmod nexus return"));
        registry.register(ActionIds.COMMAND_NEXUS_TP_HUB, ctx -> ctx.executeCommand("devmod nexus tp spawn"));
        registry.register(ActionIds.COMMAND_NEXUS_TP_TUTORIAL, ctx -> ctx.executeCommand("devmod nexus tp tutorial"));
        registry.register(ActionIds.COMMAND_NEXUS_TP_GATE_PROGRESSION, ctx -> ctx.executeCommand("devmod nexus tp gate_progression"));
        registry.register(ActionIds.COMMAND_NEXUS_TP_CLASSES, ctx -> ctx.executeCommand("devmod nexus tp classes"));
        registry.register(ActionIds.COMMAND_NEXUS_TP_OVERVIEW, ctx -> ctx.executeCommand("devmod nexus tp overview"));
        registry.register(ActionIds.COMMAND_NEXUS_TP_COMBAT, ctx -> ctx.executeCommand("devmod nexus tp combat"));
        registry.register(ActionIds.COMMAND_NEXUS_TP_ARENA, ctx -> ctx.executeCommand("devmod nexus tp arena"));
        registry.register(ActionIds.COMMAND_NEXUS_TP_UI, ctx -> ctx.executeCommand("devmod nexus tp ui"));
        registry.register(ActionIds.COMMAND_NEXUS_TP_TELEMETRY, ctx -> ctx.executeCommand("devmod nexus tp telemetry"));
        registry.register(ActionIds.COMMAND_NEXUS_TP_SHOWCASE, ctx -> ctx.executeCommand("devmod nexus tp showcase"));
        registry.register(ActionIds.COMMAND_NEXUS_TP_INTEGRATION, ctx -> ctx.executeCommand("devmod nexus tp integration"));
        registry.register(ActionIds.COMMAND_NEXUS_TP_SANDBOX, ctx -> ctx.executeCommand("devmod nexus tp sandbox"));
        registry.register(ActionIds.COMMAND_NEXUS_TP_MECHANICS, ctx -> ctx.executeCommand("devmod nexus tp mechanics"));
        registry.register(ActionIds.COMMAND_NEXUS_TP_BUILDING_WEST, ctx -> ctx.executeCommand("devmod nexus tp building_west"));
        registry.register(ActionIds.COMMAND_NEXUS_TP_BUILDING_EAST, ctx -> ctx.executeCommand("devmod nexus tp building_east"));
        registry.register(ActionIds.COMMAND_NEXUS_TP_QUEST_NORTHEAST, ctx -> ctx.executeCommand("devmod nexus tp quest_northeast"));
        registry.register(ActionIds.COMMAND_NEXUS_TP_QUEST_NORTHWEST, ctx -> ctx.executeCommand("devmod nexus tp quest_northwest"));
        registry.register(ActionIds.COMMAND_NEXUS_TP_WAR_HUB_EAST, ctx -> ctx.executeCommand("devmod nexus tp war_hub_east"));
        registry.register(ActionIds.COMMAND_NEXUS_TP_WAR_HUB_WEST, ctx -> ctx.executeCommand("devmod nexus tp war_hub_west"));
        registry.register(ActionIds.COMMAND_NEXUS_TP_ECONOMIA_EAST, ctx -> ctx.executeCommand("devmod nexus tp economia_east"));
        registry.register(ActionIds.COMMAND_NEXUS_TP_ECONOMIA_WEST, ctx -> ctx.executeCommand("devmod nexus tp economia_west"));
        registry.register(ActionIds.COMMAND_NEXUS_TP_TOWN_MANAGEMENT, ctx -> ctx.executeCommand("devmod nexus tp town_management"));
        registry.register(ActionIds.COMMAND_NEXUS_TP_EVENTI, ctx -> ctx.executeCommand("devmod nexus tp eventi"));
        registry.register(ActionIds.COMMAND_NEXUS_TP_DM_MOD, ctx -> ctx.executeCommand("devmod nexus tp dm_mod"));
        registry.register(ActionIds.COMMAND_NEXUS_STATUS, ctx -> ctx.executeCommand("devmod nexus status"));
        registry.register(ActionIds.COMMAND_NEXUS_REBUILD, ctx -> ctx.executeCommand("devmod nexus rebuild"));
        registry.register(ActionIds.COMMAND_NEXUS_LOCK, ctx -> ctx.executeCommand("devmod nexus lock"));
        registry.register(ActionIds.COMMAND_NEXUS_UNLOCK, ctx -> ctx.executeCommand("devmod nexus unlock"));
        registry.register(ActionIds.COMMAND_NEXUS_AVATAR_STATUS, ctx -> ctx.executeCommand("devmod nexus avatar status"));
        registry.register(ActionIds.COMMAND_NEXUS_AVATAR_SPAWN, ctx -> ctx.executeCommand("devmod nexus avatar spawn"));
        registry.register(ActionIds.COMMAND_NEXUS_AVATAR_REMOVE, ctx -> ctx.executeCommand("devmod nexus avatar remove"));
    }

    // ── Spec helper ──

    private static ActionSpec cmd(String id, ActionCategory category, int permLevel,
                                   String labelKey, String descKey, String icon,
                                   String menuPath, String commandHint, boolean requiresConfirm) {
        return ActionSpec.builder(id)
            .channel(ActionChannel.SERVER)
            .allowedOrigins(Set.of(
                ActionOrigin.RADIAL, ActionOrigin.KEYBIND, ActionOrigin.COMMAND,
                ActionOrigin.UI, ActionOrigin.EVENT, ActionOrigin.NETWORK))
            .category(category)
            .actionType(ActionType.RUN_SERVER_COMMAND)
            .permissionLevel(permLevel)
            .ui(labelKey, descKey, icon, menuPath, false)
            .policy(requiresConfirm, 0, permLevel > 0 ? "requiresPermission" + permLevel : null)
            .binding(null, commandHint, null)
            .handlerRef(id)
            .build();
    }
}
