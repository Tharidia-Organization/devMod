package com.devmod.actions.domains;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.devmod.actions.ActionCategory;
import com.devmod.actions.ActionIds;
import com.devmod.actions.ActionRegistry;
import com.devmod.actions.ActionType;
import com.devmod.actions.catalog.ActionSpec;
import com.devmod.actions.catalog.ActionSpec.ActionChannel;

/**
 * Domain registrar for telemetry actions from TelemetryReloadCommand,
 * DashboardCommand, and DungeonCommand.
 *
 * <p>Covers: reload, dump (weapons/rooms/fights/minions), export
 * (heatmaps/png/csv/json/all), scan (light), analysis (spawnability,
 * desire lines, dungeons, backtracking), dashboard server, dungeon runs.
 *
 * <p>All actions are server-side and require permission level 2.
 */
public final class TelemetryDomainRegistrar implements DomainRegistrar {

    @Override
    public String domainName() {
        return "telemetry";
    }

    @Override
    public List<ActionSpec> getActionSpecs() {
        List<ActionSpec> specs = new ArrayList<>();

        // ── Core telemetry ──
        specs.add(telemetrySpec(ActionIds.TELEMETRY_RELOAD,
            "devmod.action.telemetry.reload", "devmod.action.telemetry.reload.desc",
            "minecraft:repeater", "Root/Telemetry/Admin/Reload", "devmod telemetry reload"));

        // ── Dumps ──
        specs.add(telemetrySpec(ActionIds.TELEMETRY_DUMP_WEAPONS,
            "devmod.action.telemetry.dump.weapons", "devmod.action.telemetry.dump.weapons.desc",
            "minecraft:diamond_sword", "Root/Telemetry/Dump/Weapons", "devmod telemetry dump weapons"));
        specs.add(telemetrySpec(ActionIds.TELEMETRY_DUMP_ROOMS,
            "devmod.action.telemetry.dump.rooms", "devmod.action.telemetry.dump.rooms.desc",
            "minecraft:map", "Root/Telemetry/Dump/Rooms", "devmod telemetry dump rooms"));
        specs.add(telemetrySpec(ActionIds.TELEMETRY_DUMP_FIGHTS,
            "devmod.action.telemetry.dump.fights", "devmod.action.telemetry.dump.fights.desc",
            "minecraft:shield", "Root/Telemetry/Dump/Fights", "devmod telemetry dump fights"));
        specs.add(telemetrySpec(ActionIds.TELEMETRY_DUMP_MINIONS,
            "devmod.action.telemetry.dump.minions", "devmod.action.telemetry.dump.minions.desc",
            "minecraft:zombie_head", "Root/Telemetry/Dump/Minions", "devmod telemetry dump minions"));

        // ── Exports ──
        specs.add(telemetrySpec(ActionIds.TELEMETRY_EXPORT_HEATMAPS,
            "devmod.action.telemetry.export.heatmaps", "devmod.action.telemetry.export.heatmaps.desc",
            "minecraft:blaze_powder", "Root/Telemetry/Export/Heatmaps", "devmod telemetry export heatmaps"));
        specs.add(telemetrySpec(ActionIds.TELEMETRY_EXPORT_PNG,
            "devmod.action.telemetry.export.png", "devmod.action.telemetry.export.png.desc",
            "minecraft:painting", "Root/Telemetry/Export/PNG", "devmod telemetry export png"));
        specs.add(telemetrySpec(ActionIds.TELEMETRY_EXPORT_CSV,
            "devmod.action.telemetry.export.csv", "devmod.action.telemetry.export.csv.desc",
            "minecraft:paper", "Root/Telemetry/Export/CSV", "devmod telemetry export csv"));
        specs.add(telemetrySpec(ActionIds.TELEMETRY_EXPORT_JSON,
            "devmod.action.telemetry.export.json", "devmod.action.telemetry.export.json.desc",
            "minecraft:writable_book", "Root/Telemetry/Export/JSON", "devmod telemetry export json"));
        specs.add(telemetrySpec(ActionIds.TELEMETRY_EXPORT_ALL,
            "devmod.action.telemetry.export.all", "devmod.action.telemetry.export.all.desc",
            "minecraft:chest", "Root/Telemetry/Export/All", "devmod telemetry export all"));

        // ── Light scan ──
        specs.add(telemetrySpec(ActionIds.TELEMETRY_SCAN_LIGHT_ALL,
            "devmod.action.telemetry.scan.light_all", "devmod.action.telemetry.scan.light_all.desc",
            "minecraft:lantern", "Root/Telemetry/Scan/Light All", "devmod telemetry scan light"));
        specs.add(telemetryPromptSpec(ActionIds.TELEMETRY_SCAN_LIGHT_ROOM,
            "devmod.action.telemetry.scan.light_room", "devmod.action.telemetry.scan.light_room.desc",
            "minecraft:soul_lantern", "Root/Telemetry/Scan/Light Room", "devmod telemetry scan light <roomId>"));

        // ── Analysis ──
        specs.add(telemetryPromptSpec(ActionIds.TELEMETRY_SPAWNABILITY,
            "devmod.action.telemetry.spawnability", "devmod.action.telemetry.spawnability.desc",
            "minecraft:skeleton_skull", "Root/Telemetry/Analysis/Spawnability", "devmod telemetry spawnability <roomId>"));
        specs.add(telemetrySpec(ActionIds.TELEMETRY_DESIRELINES_DUMP,
            "devmod.action.telemetry.desirelines.dump", "devmod.action.telemetry.desirelines.dump.desc",
            "minecraft:string", "Root/Telemetry/Analysis/Desire Lines", "devmod telemetry desirelines"));
        specs.add(telemetryPromptSpec(ActionIds.TELEMETRY_DESIRELINES_ANALYZE,
            "devmod.action.telemetry.desirelines.analyze", "devmod.action.telemetry.desirelines.analyze.desc",
            "minecraft:spyglass", "Root/Telemetry/Analysis/Desire Lines Room", "devmod telemetry desirelines <roomId>"));
        specs.add(telemetrySpec(ActionIds.TELEMETRY_DUNGEONS_DUMP,
            "devmod.action.telemetry.dungeons.dump", "devmod.action.telemetry.dungeons.dump.desc",
            "minecraft:diamond_pickaxe", "Root/Telemetry/Analysis/Dungeons", "devmod telemetry dungeons"));
        specs.add(telemetryPromptSpec(ActionIds.TELEMETRY_DUNGEONS_STATS,
            "devmod.action.telemetry.dungeons.stats", "devmod.action.telemetry.dungeons.stats.desc",
            "minecraft:diamond_ore", "Root/Telemetry/Analysis/Dungeon Stats", "devmod telemetry dungeons <id>"));
        specs.add(telemetrySpec(ActionIds.TELEMETRY_BACKTRACKING_DUMP,
            "devmod.action.telemetry.backtracking.dump", "devmod.action.telemetry.backtracking.dump.desc",
            "minecraft:compass", "Root/Telemetry/Analysis/Backtracking", "devmod telemetry backtracking"));
        specs.add(telemetrySpec(ActionIds.TELEMETRY_BACKTRACKING_CONFUSING,
            "devmod.action.telemetry.backtracking.confusing", "devmod.action.telemetry.backtracking.confusing.desc",
            "minecraft:redstone_torch", "Root/Telemetry/Analysis/Backtracking Confusing", "devmod telemetry backtracking confusing"));

        // ── Dashboard server ──
        specs.add(ActionSpec.builder(ActionIds.TELEMETRY_DASHBOARD_SERVER_OPEN)
            .channel(ActionChannel.BOTH)
            .category(ActionCategory.TELEMETRY)
            .actionType(ActionType.OPEN_EXTERNAL)
            .permissionLevel(2)
            .ui("devmod.action.telemetry.dashboard.open",
                "devmod.action.telemetry.dashboard.open.desc",
                "minecraft:map", "Root/Telemetry/Dashboard/Open", false)
            .binding(null, "devmod dashboard", null)
            .policy(false, 0, "requiresPermissionOrClient_2")
            .telemetryTags("telemetry", "dashboard")
            .build());
        specs.add(telemetrySpec(ActionIds.TELEMETRY_DASHBOARD_SERVER_START,
            "devmod.action.telemetry.dashboard.start", "devmod.action.telemetry.dashboard.start.desc",
            "minecraft:redstone", "Root/Telemetry/Dashboard/Start", "devmod dashboard start"));
        specs.add(telemetrySpec(ActionIds.TELEMETRY_DASHBOARD_SERVER_STOP,
            "devmod.action.telemetry.dashboard.stop", "devmod.action.telemetry.dashboard.stop.desc",
            "minecraft:barrier", "Root/Telemetry/Dashboard/Stop", "devmod dashboard stop"));
        specs.add(telemetrySpec(ActionIds.TELEMETRY_DASHBOARD_SERVER_STATUS,
            "devmod.action.telemetry.dashboard.status", "devmod.action.telemetry.dashboard.status.desc",
            "minecraft:clock", "Root/Telemetry/Dashboard/Status", "devmod dashboard status"));

        // ── Dungeon telemetry ──
        specs.add(telemetrySpec(ActionIds.DUNGEON_HELP,
            "devmod.action.dungeon.help", "devmod.action.dungeon.help.desc",
            "minecraft:book", "Root/Telemetry/Dungeon/Help", "devmod dungeon"));
        specs.add(telemetryPromptSpec(ActionIds.DUNGEON_START,
            "devmod.action.dungeon.start", "devmod.action.dungeon.start.desc",
            "minecraft:diamond_pickaxe", "Root/Telemetry/Dungeon/Start", "devmod dungeon start <id>"));
        specs.add(telemetryPromptSpec(ActionIds.DUNGEON_END,
            "devmod.action.dungeon.end", "devmod.action.dungeon.end.desc",
            "minecraft:diamond_sword", "Root/Telemetry/Dungeon/End", "devmod dungeon end <outcome>"));
        specs.add(telemetrySpec(ActionIds.DUNGEON_STATUS,
            "devmod.action.dungeon.status", "devmod.action.dungeon.status.desc",
            "minecraft:clock", "Root/Telemetry/Dungeon/Status", "devmod dungeon status"));

        return List.copyOf(specs);
    }

    @Override
    public void registerHandlers(HandlerRegistry registry) {
        for (String id : List.of(
                ActionIds.TELEMETRY_RELOAD,
                ActionIds.TELEMETRY_DUMP_WEAPONS, ActionIds.TELEMETRY_DUMP_ROOMS,
                ActionIds.TELEMETRY_DUMP_FIGHTS, ActionIds.TELEMETRY_DUMP_MINIONS,
                ActionIds.TELEMETRY_EXPORT_HEATMAPS, ActionIds.TELEMETRY_EXPORT_PNG,
                ActionIds.TELEMETRY_EXPORT_CSV, ActionIds.TELEMETRY_EXPORT_JSON,
                ActionIds.TELEMETRY_EXPORT_ALL,
                ActionIds.TELEMETRY_SCAN_LIGHT_ALL, ActionIds.TELEMETRY_SCAN_LIGHT_ROOM,
                ActionIds.TELEMETRY_SPAWNABILITY,
                ActionIds.TELEMETRY_DESIRELINES_DUMP, ActionIds.TELEMETRY_DESIRELINES_ANALYZE,
                ActionIds.TELEMETRY_DUNGEONS_DUMP, ActionIds.TELEMETRY_DUNGEONS_STATS,
                ActionIds.TELEMETRY_BACKTRACKING_DUMP, ActionIds.TELEMETRY_BACKTRACKING_CONFUSING,
                ActionIds.TELEMETRY_DASHBOARD_SERVER_OPEN, ActionIds.TELEMETRY_DASHBOARD_SERVER_START,
                ActionIds.TELEMETRY_DASHBOARD_SERVER_STOP, ActionIds.TELEMETRY_DASHBOARD_SERVER_STATUS,
                ActionIds.DUNGEON_HELP, ActionIds.DUNGEON_START,
                ActionIds.DUNGEON_END, ActionIds.DUNGEON_STATUS)) {
            registry.register(id, context -> ActionRegistry.invoke(id, context));
        }
    }

    @Override
    public Set<String> getDependencies() {
        return Set.of();
    }

    // ── Factory helpers ──

    private static ActionSpec telemetrySpec(String id, String labelKey, String descKey,
                                            String iconItemId, String menuPath, String commandHint) {
        return ActionSpec.builder(id)
            .channel(ActionChannel.SERVER)
            .category(ActionCategory.TELEMETRY)
            .actionType(ActionType.RUN_SERVER_COMMAND)
            .permissionLevel(2)
            .ui(labelKey, descKey, iconItemId, menuPath, false)
            .binding(null, commandHint, null)
            .policy(false, 0, "requiresPermissionOrClient_2")
            .telemetryTags("telemetry")
            .build();
    }

    private static ActionSpec telemetryPromptSpec(String id, String labelKey, String descKey,
                                                  String iconItemId, String menuPath, String commandHint) {
        return ActionSpec.builder(id)
            .channel(ActionChannel.BOTH)
            .category(ActionCategory.TELEMETRY)
            .actionType(ActionType.RUN_SERVER_COMMAND)
            .permissionLevel(2)
            .ui(labelKey, descKey, iconItemId, menuPath, false)
            .binding(null, commandHint, null)
            .policy(false, 0, "requiresPermissionOrClient_2")
            .telemetryTags("telemetry")
            .build();
    }
}
