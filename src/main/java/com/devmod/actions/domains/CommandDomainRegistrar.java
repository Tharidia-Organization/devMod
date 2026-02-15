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

        // --- Nexus: Testing Lab Zones (perm 0) ---
        specs.add(cmd(ActionIds.COMMAND_NEXUS_TP_HUB, ActionCategory.TOOLS, 0,
            "devmod.action.command.nexus_tp_hub", "devmod.action.command.nexus_tp_hub.desc",
            "minecraft:beacon", "Root/Nexus/Zones/Hub Center", "devmod nexus tp spawn", false));
        specs.add(cmd(ActionIds.COMMAND_NEXUS_TP_COMBAT_LAB, ActionCategory.TOOLS, 0,
            "devmod.action.command.nexus_tp_combat_lab", "devmod.action.command.nexus_tp_combat_lab.desc",
            "minecraft:iron_sword", "Root/Nexus/Zones/Combat/Combat Lab", "devmod nexus tp combat_lab", false));
        specs.add(cmd(ActionIds.COMMAND_NEXUS_TP_ABILITIES_LAB, ActionCategory.TOOLS, 0,
            "devmod.action.command.nexus_tp_abilities_lab", "devmod.action.command.nexus_tp_abilities_lab.desc",
            "minecraft:feather", "Root/Nexus/Zones/Combat/Abilities Lab", "devmod nexus tp abilities_lab", false));
        specs.add(cmd(ActionIds.COMMAND_NEXUS_TP_BOSS_ARENA, ActionCategory.TOOLS, 0,
            "devmod.action.command.nexus_tp_boss_arena", "devmod.action.command.nexus_tp_boss_arena.desc",
            "minecraft:wither_skeleton_skull", "Root/Nexus/Zones/Combat/Boss Arena", "devmod nexus tp boss_arena", false));
        specs.add(cmd(ActionIds.COMMAND_NEXUS_TP_COLLISION_LAB, ActionCategory.TOOLS, 0,
            "devmod.action.command.nexus_tp_collision_lab", "devmod.action.command.nexus_tp_collision_lab.desc",
            "minecraft:glass", "Root/Nexus/Zones/Combat/Collision Lab", "devmod nexus tp collision_lab", false));
        specs.add(cmd(ActionIds.COMMAND_NEXUS_TP_PORTAL_LAB, ActionCategory.TOOLS, 0,
            "devmod.action.command.nexus_tp_portal_lab", "devmod.action.command.nexus_tp_portal_lab.desc",
            "minecraft:ender_pearl", "Root/Nexus/Zones/Systems/Portal Lab", "devmod nexus tp portal_lab", false));
        specs.add(cmd(ActionIds.COMMAND_NEXUS_TP_NPC_LAB, ActionCategory.TOOLS, 0,
            "devmod.action.command.nexus_tp_npc_lab", "devmod.action.command.nexus_tp_npc_lab.desc",
            "minecraft:villager_spawn_egg", "Root/Nexus/Zones/Systems/NPC Lab", "devmod nexus tp npc_lab", false));
        specs.add(cmd(ActionIds.COMMAND_NEXUS_TP_QUEST_TESTING, ActionCategory.TOOLS, 0,
            "devmod.action.command.nexus_tp_quest_testing", "devmod.action.command.nexus_tp_quest_testing.desc",
            "minecraft:writable_book", "Root/Nexus/Zones/Systems/Quest Testing", "devmod nexus tp quest_testing", false));
        specs.add(cmd(ActionIds.COMMAND_NEXUS_TP_ARENA_BUILDER, ActionCategory.TOOLS, 0,
            "devmod.action.command.nexus_tp_arena_builder", "devmod.action.command.nexus_tp_arena_builder.desc",
            "minecraft:structure_block", "Root/Nexus/Zones/Systems/Arena Builder", "devmod nexus tp arena_builder", false));
        specs.add(cmd(ActionIds.COMMAND_NEXUS_TP_VFX_STUDIO, ActionCategory.TOOLS, 0,
            "devmod.action.command.nexus_tp_vfx_studio", "devmod.action.command.nexus_tp_vfx_studio.desc",
            "minecraft:firework_star", "Root/Nexus/Zones/Tools/VFX Studio", "devmod nexus tp vfx_studio", false));
        specs.add(cmd(ActionIds.COMMAND_NEXUS_TP_ITEM_WORKSHOP, ActionCategory.TOOLS, 0,
            "devmod.action.command.nexus_tp_item_workshop", "devmod.action.command.nexus_tp_item_workshop.desc",
            "minecraft:anvil", "Root/Nexus/Zones/Tools/Item Workshop", "devmod nexus tp item_workshop", false));
        specs.add(cmd(ActionIds.COMMAND_NEXUS_TP_CONFIG_ROOM, ActionCategory.TOOLS, 0,
            "devmod.action.command.nexus_tp_config_room", "devmod.action.command.nexus_tp_config_room.desc",
            "minecraft:comparator", "Root/Nexus/Zones/Tools/Config Room", "devmod nexus tp config_room", false));
        specs.add(cmd(ActionIds.COMMAND_NEXUS_TP_HUD_TESTING, ActionCategory.TOOLS, 0,
            "devmod.action.command.nexus_tp_hud_testing", "devmod.action.command.nexus_tp_hud_testing.desc",
            "minecraft:painting", "Root/Nexus/Zones/Tools/HUD Testing", "devmod nexus tp hud_testing", false));
        specs.add(cmd(ActionIds.COMMAND_NEXUS_TP_SANDBOX, ActionCategory.TOOLS, 0,
            "devmod.action.command.nexus_tp_sandbox", "devmod.action.command.nexus_tp_sandbox.desc",
            "minecraft:sand", "Root/Nexus/Zones/Misc/Sandbox", "devmod nexus tp sandbox", false));
        specs.add(cmd(ActionIds.COMMAND_NEXUS_TP_ADMIN_TOOLS, ActionCategory.TOOLS, 0,
            "devmod.action.command.nexus_tp_admin_tools", "devmod.action.command.nexus_tp_admin_tools.desc",
            "minecraft:command_block", "Root/Nexus/Zones/Misc/Admin Tools", "devmod nexus tp admin_tools", false));

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
        // --- Nexus Testing Lab Zone Teleports ---
        registry.register(ActionIds.COMMAND_NEXUS_TP_HUB, ctx -> ctx.executeCommand("devmod nexus tp spawn"));
        registry.register(ActionIds.COMMAND_NEXUS_TP_COMBAT_LAB, ctx -> ctx.executeCommand("devmod nexus tp combat_lab"));
        registry.register(ActionIds.COMMAND_NEXUS_TP_ABILITIES_LAB, ctx -> ctx.executeCommand("devmod nexus tp abilities_lab"));
        registry.register(ActionIds.COMMAND_NEXUS_TP_BOSS_ARENA, ctx -> ctx.executeCommand("devmod nexus tp boss_arena"));
        registry.register(ActionIds.COMMAND_NEXUS_TP_COLLISION_LAB, ctx -> ctx.executeCommand("devmod nexus tp collision_lab"));
        registry.register(ActionIds.COMMAND_NEXUS_TP_PORTAL_LAB, ctx -> ctx.executeCommand("devmod nexus tp portal_lab"));
        registry.register(ActionIds.COMMAND_NEXUS_TP_NPC_LAB, ctx -> ctx.executeCommand("devmod nexus tp npc_lab"));
        registry.register(ActionIds.COMMAND_NEXUS_TP_QUEST_TESTING, ctx -> ctx.executeCommand("devmod nexus tp quest_testing"));
        registry.register(ActionIds.COMMAND_NEXUS_TP_ARENA_BUILDER, ctx -> ctx.executeCommand("devmod nexus tp arena_builder"));
        registry.register(ActionIds.COMMAND_NEXUS_TP_VFX_STUDIO, ctx -> ctx.executeCommand("devmod nexus tp vfx_studio"));
        registry.register(ActionIds.COMMAND_NEXUS_TP_ITEM_WORKSHOP, ctx -> ctx.executeCommand("devmod nexus tp item_workshop"));
        registry.register(ActionIds.COMMAND_NEXUS_TP_CONFIG_ROOM, ctx -> ctx.executeCommand("devmod nexus tp config_room"));
        registry.register(ActionIds.COMMAND_NEXUS_TP_HUD_TESTING, ctx -> ctx.executeCommand("devmod nexus tp hud_testing"));
        registry.register(ActionIds.COMMAND_NEXUS_TP_SANDBOX, ctx -> ctx.executeCommand("devmod nexus tp sandbox"));
        registry.register(ActionIds.COMMAND_NEXUS_TP_ADMIN_TOOLS, ctx -> ctx.executeCommand("devmod nexus tp admin_tools"));
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
