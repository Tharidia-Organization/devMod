package com.frenkvs.devmod.telemetry;

import com.frenkvs.devmod.actions.ActionCategory;
import com.frenkvs.devmod.actions.ActionCommandInvoker;
import com.frenkvs.devmod.actions.ActionContext;
import com.frenkvs.devmod.actions.ActionIds;
import com.frenkvs.devmod.actions.ActionOrigin;
import com.frenkvs.devmod.actions.ActionPreconditions;
import com.frenkvs.devmod.actions.ActionRegistry;
import com.frenkvs.devmod.actions.RadialAction;
import com.frenkvs.devmod.util.I18n;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Items;
import java.util.Objects;

/**
 * Simple admin command to reload telemetry room definitions without server restart.
 * Usage: /devmod telemetry reload
 *        /devmod telemetry dump <weapons|rooms|fights|minions>
 *        /devmod telemetry export <heatmaps|all>
 *        /devmod telemetry scan light [roomId]
 */
public class TelemetryReloadCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("devmod")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("telemetry")
                                .then(Commands.literal("reload")
                                        .executes(ctx -> ActionCommandInvoker.invoke(ActionIds.TELEMETRY_RELOAD, ctx)))
                                .then(Commands.literal("dump")
                                        .then(Commands.literal("weapons")
                                                .executes(ctx -> ActionCommandInvoker.invoke(ActionIds.TELEMETRY_DUMP_WEAPONS, ctx)))
                                        .then(Commands.literal("rooms")
                                                .executes(ctx -> ActionCommandInvoker.invoke(ActionIds.TELEMETRY_DUMP_ROOMS, ctx)))
                                        .then(Commands.literal("fights")
                                                .executes(ctx -> ActionCommandInvoker.invoke(ActionIds.TELEMETRY_DUMP_FIGHTS, ctx)))
                                        .then(Commands.literal("minions")
                                                .executes(ctx -> ActionCommandInvoker.invoke(ActionIds.TELEMETRY_DUMP_MINIONS, ctx))))
                                .then(Commands.literal("export")
                                        .then(Commands.literal("heatmaps")
                                                .executes(ctx -> ActionCommandInvoker.invoke(ActionIds.TELEMETRY_EXPORT_HEATMAPS, ctx)))
                                        .then(Commands.literal("png")
                                                .executes(ctx -> ActionCommandInvoker.invoke(ActionIds.TELEMETRY_EXPORT_PNG, ctx)))
                                        .then(Commands.literal("csv")
                                                .executes(ctx -> ActionCommandInvoker.invoke(ActionIds.TELEMETRY_EXPORT_CSV, ctx)))
                                        .then(Commands.literal("json")
                                                .executes(ctx -> ActionCommandInvoker.invoke(ActionIds.TELEMETRY_EXPORT_JSON, ctx)))
                                        .then(Commands.literal("all")
                                                .executes(ctx -> ActionCommandInvoker.invoke(ActionIds.TELEMETRY_EXPORT_ALL, ctx))))
                                .then(Commands.literal("scan")
                                        .then(Commands.literal("light")
                                                .executes(ctx -> ActionCommandInvoker.invoke(ActionIds.TELEMETRY_SCAN_LIGHT_ALL, ctx))
                                                .then(Commands.argument("roomId", Objects.requireNonNull(StringArgumentType.string()))
                                                        .executes(ctx -> ActionCommandInvoker.invoke(ActionIds.TELEMETRY_SCAN_LIGHT_ROOM, ctx)))))
                                .then(Commands.literal("spawnability")
                                        .then(Commands.argument("roomId", Objects.requireNonNull(StringArgumentType.string()))
                                                .executes(ctx -> ActionCommandInvoker.invoke(ActionIds.TELEMETRY_SPAWNABILITY, ctx))))
                                .then(Commands.literal("desirelines")
                                        .executes(ctx -> ActionCommandInvoker.invoke(ActionIds.TELEMETRY_DESIRELINES_DUMP, ctx))
                                        .then(Commands.argument("roomId", Objects.requireNonNull(StringArgumentType.string()))
                                                .executes(ctx -> ActionCommandInvoker.invoke(ActionIds.TELEMETRY_DESIRELINES_ANALYZE, ctx))))
                                .then(Commands.literal("dungeons")
                                        .executes(ctx -> ActionCommandInvoker.invoke(ActionIds.TELEMETRY_DUNGEONS_DUMP, ctx))
                                        .then(Commands.argument("dungeonId", Objects.requireNonNull(StringArgumentType.string()))
                                                .executes(ctx -> ActionCommandInvoker.invoke(ActionIds.TELEMETRY_DUNGEONS_STATS, ctx))))
                                .then(Commands.literal("backtracking")
                                        .executes(ctx -> ActionCommandInvoker.invoke(ActionIds.TELEMETRY_BACKTRACKING_DUMP, ctx))
                                        .then(Commands.literal("confusing")
                                                .executes(ctx -> ActionCommandInvoker.invoke(ActionIds.TELEMETRY_BACKTRACKING_CONFUSING, ctx)))))
        );
    }

    public static void registerActions() {
        ActionRegistry.register(RadialAction.builder(ActionIds.TELEMETRY_RELOAD)
            .labelKey("devmod.action.telemetry.reload")
            .descriptionKey("devmod.action.telemetry.reload.desc")
            .category(ActionCategory.TELEMETRY)
            .menuPath("Root/Telemetry/Admin/Reload")
            .icon(Items.REPEATER)
            .precondition(ActionPreconditions.requiresPermissionOrClient(2))
            .commandHint("devmod telemetry reload")
            .handler(context -> handleCommand(context, "devmod telemetry reload", TelemetryReloadCommand::reload))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.TELEMETRY_DUMP_WEAPONS)
            .labelKey("devmod.action.telemetry.dump.weapons")
            .descriptionKey("devmod.action.telemetry.dump.weapons.desc")
            .category(ActionCategory.TELEMETRY)
            .menuPath("Root/Telemetry/Dump/Weapons")
            .icon(Items.DIAMOND_SWORD)
            .precondition(ActionPreconditions.requiresPermissionOrClient(2))
            .commandHint("devmod telemetry dump weapons")
            .handler(context -> handleCommand(context, "devmod telemetry dump weapons", TelemetryReloadCommand::dumpWeapons))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.TELEMETRY_DUMP_ROOMS)
            .labelKey("devmod.action.telemetry.dump.rooms")
            .descriptionKey("devmod.action.telemetry.dump.rooms.desc")
            .category(ActionCategory.TELEMETRY)
            .menuPath("Root/Telemetry/Dump/Rooms")
            .icon(Items.MAP)
            .precondition(ActionPreconditions.requiresPermissionOrClient(2))
            .commandHint("devmod telemetry dump rooms")
            .handler(context -> handleCommand(context, "devmod telemetry dump rooms", TelemetryReloadCommand::dumpRooms))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.TELEMETRY_DUMP_FIGHTS)
            .labelKey("devmod.action.telemetry.dump.fights")
            .descriptionKey("devmod.action.telemetry.dump.fights.desc")
            .category(ActionCategory.TELEMETRY)
            .menuPath("Root/Telemetry/Dump/Fights")
            .icon(Items.SHIELD)
            .precondition(ActionPreconditions.requiresPermissionOrClient(2))
            .commandHint("devmod telemetry dump fights")
            .handler(context -> handleCommand(context, "devmod telemetry dump fights", TelemetryReloadCommand::dumpFights))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.TELEMETRY_DUMP_MINIONS)
            .labelKey("devmod.action.telemetry.dump.minions")
            .descriptionKey("devmod.action.telemetry.dump.minions.desc")
            .category(ActionCategory.TELEMETRY)
            .menuPath("Root/Telemetry/Dump/Minions")
            .icon(Items.ZOMBIE_HEAD)
            .precondition(ActionPreconditions.requiresPermissionOrClient(2))
            .commandHint("devmod telemetry dump minions")
            .handler(context -> handleCommand(context, "devmod telemetry dump minions", TelemetryReloadCommand::dumpMinions))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.TELEMETRY_EXPORT_HEATMAPS)
            .labelKey("devmod.action.telemetry.export.heatmaps")
            .descriptionKey("devmod.action.telemetry.export.heatmaps.desc")
            .category(ActionCategory.TELEMETRY)
            .menuPath("Root/Telemetry/Export/Heatmaps")
            .icon(Items.BLAZE_POWDER)
            .precondition(ActionPreconditions.requiresPermissionOrClient(2))
            .commandHint("devmod telemetry export heatmaps")
            .handler(context -> handleCommand(context, "devmod telemetry export heatmaps", TelemetryReloadCommand::exportHeatmaps))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.TELEMETRY_EXPORT_PNG)
            .labelKey("devmod.action.telemetry.export.png")
            .descriptionKey("devmod.action.telemetry.export.png.desc")
            .category(ActionCategory.TELEMETRY)
            .menuPath("Root/Telemetry/Export/PNG")
            .icon(Items.PAINTING)
            .precondition(ActionPreconditions.requiresPermissionOrClient(2))
            .commandHint("devmod telemetry export png")
            .handler(context -> handleCommand(context, "devmod telemetry export png", TelemetryReloadCommand::exportHeatmapsPng))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.TELEMETRY_EXPORT_CSV)
            .labelKey("devmod.action.telemetry.export.csv")
            .descriptionKey("devmod.action.telemetry.export.csv.desc")
            .category(ActionCategory.TELEMETRY)
            .menuPath("Root/Telemetry/Export/CSV")
            .icon(Items.PAPER)
            .precondition(ActionPreconditions.requiresPermissionOrClient(2))
            .commandHint("devmod telemetry export csv")
            .handler(context -> handleCommand(context, "devmod telemetry export csv", TelemetryReloadCommand::exportCsv))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.TELEMETRY_EXPORT_JSON)
            .labelKey("devmod.action.telemetry.export.json")
            .descriptionKey("devmod.action.telemetry.export.json.desc")
            .category(ActionCategory.TELEMETRY)
            .menuPath("Root/Telemetry/Export/JSON")
            .icon(Items.WRITABLE_BOOK)
            .precondition(ActionPreconditions.requiresPermissionOrClient(2))
            .commandHint("devmod telemetry export json")
            .handler(context -> handleCommand(context, "devmod telemetry export json", TelemetryReloadCommand::exportJsonReport))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.TELEMETRY_EXPORT_ALL)
            .labelKey("devmod.action.telemetry.export.all")
            .descriptionKey("devmod.action.telemetry.export.all.desc")
            .category(ActionCategory.TELEMETRY)
            .menuPath("Root/Telemetry/Export/All")
            .icon(Items.CHEST)
            .precondition(ActionPreconditions.requiresPermissionOrClient(2))
            .commandHint("devmod telemetry export all")
            .handler(context -> handleCommand(context, "devmod telemetry export all", TelemetryReloadCommand::exportAll))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.TELEMETRY_SCAN_LIGHT_ALL)
            .labelKey("devmod.action.telemetry.scan.light_all")
            .descriptionKey("devmod.action.telemetry.scan.light_all.desc")
            .category(ActionCategory.TELEMETRY)
            .menuPath("Root/Telemetry/Scan/Light All")
            .icon(Items.LANTERN)
            .precondition(ActionPreconditions.requiresPermissionOrClient(2))
            .commandHint("devmod telemetry scan light")
            .handler(context -> handleCommand(context, "devmod telemetry scan light", TelemetryReloadCommand::scanLightAll))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.TELEMETRY_SCAN_LIGHT_ROOM)
            .labelKey("devmod.action.telemetry.scan.light_room")
            .descriptionKey("devmod.action.telemetry.scan.light_room.desc")
            .category(ActionCategory.TELEMETRY)
            .menuPath("Root/Telemetry/Scan/Light Room")
            .icon(Items.SOUL_LANTERN)
            .precondition(ActionPreconditions.requiresPermissionOrClient(2))
            .commandHint("devmod telemetry scan light <roomId>")
            .handler(context -> handleCommandPrompt(context, "devmod telemetry scan light ", TelemetryReloadCommand::scanLightRoom))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.TELEMETRY_SPAWNABILITY)
            .labelKey("devmod.action.telemetry.spawnability")
            .descriptionKey("devmod.action.telemetry.spawnability.desc")
            .category(ActionCategory.TELEMETRY)
            .menuPath("Root/Telemetry/Analysis/Spawnability")
            .icon(Items.SKELETON_SKULL)
            .precondition(ActionPreconditions.requiresPermissionOrClient(2))
            .commandHint("devmod telemetry spawnability <roomId>")
            .handler(context -> handleCommandPrompt(context, "devmod telemetry spawnability ", TelemetryReloadCommand::checkSpawnability))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.TELEMETRY_DESIRELINES_DUMP)
            .labelKey("devmod.action.telemetry.desirelines.dump")
            .descriptionKey("devmod.action.telemetry.desirelines.dump.desc")
            .category(ActionCategory.TELEMETRY)
            .menuPath("Root/Telemetry/Analysis/Desire Lines")
            .icon(Items.STRING)
            .precondition(ActionPreconditions.requiresPermissionOrClient(2))
            .commandHint("devmod telemetry desirelines")
            .handler(context -> handleCommand(context, "devmod telemetry desirelines", TelemetryReloadCommand::dumpDesireLines))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.TELEMETRY_DESIRELINES_ANALYZE)
            .labelKey("devmod.action.telemetry.desirelines.analyze")
            .descriptionKey("devmod.action.telemetry.desirelines.analyze.desc")
            .category(ActionCategory.TELEMETRY)
            .menuPath("Root/Telemetry/Analysis/Desire Lines Room")
            .icon(Items.SPYGLASS)
            .precondition(ActionPreconditions.requiresPermissionOrClient(2))
            .commandHint("devmod telemetry desirelines <roomId>")
            .handler(context -> handleCommandPrompt(context, "devmod telemetry desirelines ", TelemetryReloadCommand::analyzeDesireLines))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.TELEMETRY_DUNGEONS_DUMP)
            .labelKey("devmod.action.telemetry.dungeons.dump")
            .descriptionKey("devmod.action.telemetry.dungeons.dump.desc")
            .category(ActionCategory.TELEMETRY)
            .menuPath("Root/Telemetry/Analysis/Dungeons")
            .icon(Items.DIAMOND_PICKAXE)
            .precondition(ActionPreconditions.requiresPermissionOrClient(2))
            .commandHint("devmod telemetry dungeons")
            .handler(context -> handleCommand(context, "devmod telemetry dungeons", TelemetryReloadCommand::dumpDungeonRuns))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.TELEMETRY_DUNGEONS_STATS)
            .labelKey("devmod.action.telemetry.dungeons.stats")
            .descriptionKey("devmod.action.telemetry.dungeons.stats.desc")
            .category(ActionCategory.TELEMETRY)
            .menuPath("Root/Telemetry/Analysis/Dungeon Stats")
            .icon(Items.DIAMOND_ORE)
            .precondition(ActionPreconditions.requiresPermissionOrClient(2))
            .commandHint("devmod telemetry dungeons <id>")
            .handler(context -> handleCommandPrompt(context, "devmod telemetry dungeons ", TelemetryReloadCommand::getDungeonStats))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.TELEMETRY_BACKTRACKING_DUMP)
            .labelKey("devmod.action.telemetry.backtracking.dump")
            .descriptionKey("devmod.action.telemetry.backtracking.dump.desc")
            .category(ActionCategory.TELEMETRY)
            .menuPath("Root/Telemetry/Analysis/Backtracking")
            .icon(Items.COMPASS)
            .precondition(ActionPreconditions.requiresPermissionOrClient(2))
            .commandHint("devmod telemetry backtracking")
            .handler(context -> handleCommand(context, "devmod telemetry backtracking", TelemetryReloadCommand::dumpBacktracking))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.TELEMETRY_BACKTRACKING_CONFUSING)
            .labelKey("devmod.action.telemetry.backtracking.confusing")
            .descriptionKey("devmod.action.telemetry.backtracking.confusing.desc")
            .category(ActionCategory.TELEMETRY)
            .menuPath("Root/Telemetry/Analysis/Backtracking Confusing")
            .icon(Items.REDSTONE_TORCH)
            .precondition(ActionPreconditions.requiresPermissionOrClient(2))
            .commandHint("devmod telemetry backtracking confusing")
            .handler(context -> handleCommand(context, "devmod telemetry backtracking confusing", TelemetryReloadCommand::getMostConfusingRooms))
            .build());
    }

    private static void handleCommand(ActionContext context, String command,
                                      CommandHandler handler) {
        CommandContext<CommandSourceStack> cmd = context.getCommandContext();
        if (context.getOrigin() == ActionOrigin.COMMAND && cmd != null) {
            try {
                handler.run(cmd);
            } catch (CommandSyntaxException e) {
                String message = Objects.requireNonNullElse(e.getMessage(), "Unknown error");
                context.sendFailure(Component.literal(Objects.requireNonNull(message, "errorMessage")));
            }
            return;
        }
        context.executeCommand(command);
    }

    private static void handleCommandPrompt(ActionContext context, String command,
                                            CommandHandler handler) {
        CommandContext<CommandSourceStack> cmd = context.getCommandContext();
        if (context.getOrigin() == ActionOrigin.COMMAND && cmd != null) {
            try {
                handler.run(cmd);
            } catch (CommandSyntaxException e) {
                String message = Objects.requireNonNullElse(e.getMessage(), "Unknown error");
                context.sendFailure(Component.literal(Objects.requireNonNull(message, "errorMessage")));
            }
            return;
        }
        if (!context.openCommandPrompt(command)) {
            context.executeCommand(command);
        }
    }

    @FunctionalInterface
    private interface CommandHandler {
        void run(CommandContext<CommandSourceStack> context) throws CommandSyntaxException;
    }

    private static int reload(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        var server = ctx.getSource().getServer();
        TelemetryService.INSTANCE.reload(server);
        ctx.getSource().sendSuccess(() -> I18n.translate("devmod.telemetry.rooms_reloaded"), true);
        return 1;
    }

    private static int dumpWeapons(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        var lines = TelemetryService.INSTANCE.getWeaponSummaries();
        if (lines.isEmpty()) {
            ctx.getSource().sendSuccess(() -> I18n.translate("devmod.telemetry.no_weapon_aggregates"), false);
        } else {
            lines.forEach(line -> ctx.getSource().sendSuccess(() -> Component.literal(Objects.requireNonNull(line)), false));
        }
        return 1;
    }

    private static int dumpRooms(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        var lines = TelemetryService.INSTANCE.getRoomSummaries();
        if (lines.isEmpty()) {
            ctx.getSource().sendSuccess(() -> I18n.translate("devmod.telemetry.no_room_aggregates"), false);
        } else {
            lines.forEach(line -> ctx.getSource().sendSuccess(() -> Component.literal(Objects.requireNonNull(line)), false));
        }
        return 1;
    }

    private static int dumpFights(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        var lines = TelemetryService.INSTANCE.getFightSummaries();
        if (lines.isEmpty()) {
            ctx.getSource().sendSuccess(() -> I18n.translate("devmod.telemetry.no_active_fights"), false);
        } else {
            lines.forEach(line -> ctx.getSource().sendSuccess(() -> Component.literal(Objects.requireNonNull(line)), false));
        }
        return 1;
    }

    private static int dumpMinions(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        var lines = TelemetryService.INSTANCE.getAllMinionWaveStats();
        if (lines.isEmpty()) {
            ctx.getSource().sendSuccess(() -> I18n.translate("devmod.telemetry.no_minion_data"), false);
        } else {
            lines.forEach(line -> ctx.getSource().sendSuccess(() -> Component.literal(Objects.requireNonNull(line)), false));
        }
        return 1;
    }

    private static int exportHeatmaps(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        TelemetryService.INSTANCE.exportStuckHeatmap();
        TelemetryService.INSTANCE.exportAggroDropHeatmap();
        TelemetryService.INSTANCE.exportKitingHeatmap();
        TelemetryService.INSTANCE.exportDeathHeatmap();
        TelemetryService.INSTANCE.exportMovementHeatmap();
        TelemetryService.INSTANCE.exportCampingHeatmap();
        ctx.getSource().sendSuccess(() -> I18n.translate("devmod.telemetry.exported_heatmaps"), true);
        return 1;
    }

    private static int exportAll(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        exportHeatmaps(ctx);
        int pngCount = com.frenkvs.devmod.telemetry.export.HeatmapExporter.exportAll();
        int csvCount = com.frenkvs.devmod.telemetry.export.CsvExporter.exportAll();
        com.frenkvs.devmod.telemetry.export.JsonReportExporter.exportReport();
        ctx.getSource().sendSuccess(() -> I18n.translate("devmod.telemetry.exported_all", pngCount, csvCount), true);
        return 1;
    }

    private static int exportHeatmapsPng(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        int count = com.frenkvs.devmod.telemetry.export.HeatmapExporter.exportAll();
        if (count > 0) {
            ctx.getSource().sendSuccess(() -> I18n.translate("devmod.telemetry.exported_png", count), true);
        } else {
            ctx.getSource().sendSuccess(() -> I18n.translate("devmod.telemetry.no_heatmap_data"), false);
        }
        return 1;
    }

    private static int exportCsv(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        int count = com.frenkvs.devmod.telemetry.export.CsvExporter.exportAll();
        if (count > 0) {
            ctx.getSource().sendSuccess(() -> I18n.translate("devmod.telemetry.exported_csv", count), true);
        } else {
            ctx.getSource().sendSuccess(() -> I18n.translate("devmod.telemetry.no_telemetry_data"), false);
        }
        return 1;
    }

    private static int exportJsonReport(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        String path = com.frenkvs.devmod.telemetry.export.JsonReportExporter.exportReport();
        if (path != null) {
            ctx.getSource().sendSuccess(() -> I18n.translate("devmod.telemetry.exported_json", path), true);
        } else {
            ctx.getSource().sendSuccess(() -> I18n.translate("devmod.telemetry.failed_export_json"), false);
        }
        return 1;
    }

    private static int scanLightAll(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerLevel level = ctx.getSource().getLevel();
        ctx.getSource().sendSuccess(() -> I18n.translate("devmod.telemetry.scanning_rooms"), true);
        TelemetryService.INSTANCE.scanAllRoomsLightLevels(level);
        ctx.getSource().sendSuccess(() -> I18n.translate("devmod.telemetry.scan_complete"), true);
        return 1;
    }

    private static int scanLightRoom(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerLevel level = ctx.getSource().getLevel();
        String roomId = Objects.requireNonNull(StringArgumentType.getString(ctx, "roomId"));
        ctx.getSource().sendSuccess(() -> I18n.translate("devmod.telemetry.scanning_room", roomId), true);
        TelemetryService.INSTANCE.scanRoomLightLevels(level, roomId);
        ctx.getSource().sendSuccess(() -> I18n.translate("devmod.telemetry.scan_room_complete", roomId), true);
        return 1;
    }

    private static int checkSpawnability(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerLevel level = ctx.getSource().getLevel();
        String roomId = Objects.requireNonNull(StringArgumentType.getString(ctx, "roomId"));
        var report = TelemetryService.INSTANCE.getSpawnabilityReport(level, roomId);
        String reportStr = Objects.requireNonNull(report.toString());
        ctx.getSource().sendSuccess(() -> Objects.requireNonNull(Component.literal(reportStr)), false);
        return 1;
    }

    private static int dumpDesireLines(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        var lines = com.frenkvs.devmod.telemetry.spatial.DesireLinesService.INSTANCE.getSummaries();
        if (lines.isEmpty()) {
            ctx.getSource().sendSuccess(() -> I18n.translate("devmod.telemetry.no_desire_line_data"), false);
        } else {
            ctx.getSource().sendSuccess(() -> I18n.translate("devmod.telemetry.desire_lines_summary"), false);
            lines.forEach(line -> ctx.getSource().sendSuccess(() -> Component.literal(Objects.requireNonNull(line)), false));
        }
        return 1;
    }

    private static int analyzeDesireLines(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        String roomId = StringArgumentType.getString(ctx, "roomId");
        var service = com.frenkvs.devmod.telemetry.spatial.DesireLinesService.INSTANCE;

        // Trigger analysis for this room
        service.analyzeRoom(roomId);

        var analysis = service.getPathAnalysis(roomId);
        if (analysis.isEmpty()) {
            ctx.getSource().sendSuccess(() -> I18n.translate("devmod.telemetry.no_movement_data", roomId), false);
            return 1;
        }

        var data = analysis.get();
        ctx.getSource().sendSuccess(() -> I18n.translate("devmod.telemetry.desire_lines_room", roomId), false);
        ctx.getSource().sendSuccess(() -> Objects.requireNonNull(I18n.translate("devmod.telemetry.total_segments").append(Objects.requireNonNull(Component.literal(": " + data.totalSegments())))), false);

        ctx.getSource().sendSuccess(() -> Objects.requireNonNull(I18n.translate("devmod.telemetry.top_paths").append(Objects.requireNonNull(Component.literal(":")))), false);
        int count = 0;
        for (var path : data.topPaths()) {
            if (count++ >= 5) break;
            String pathStr = Objects.requireNonNull(String.format("  [%d,%d,%d] -> [%d,%d,%d] (%dx)",
                path.from().getX(), path.from().getY(), path.from().getZ(),
                path.to().getX(), path.to().getY(), path.to().getZ(),
                path.count()));
            ctx.getSource().sendSuccess(() -> Objects.requireNonNull(Component.literal(pathStr)), false);
        }

        ctx.getSource().sendSuccess(() -> Objects.requireNonNull(I18n.translate("devmod.telemetry.hotspots").append(Objects.requireNonNull(Component.literal(":")))), false);
        count = 0;
        for (var hotspot : data.hotspots()) {
            if (count++ >= 5) break;
            String hotspotStr = Objects.requireNonNull(String.format("  [%d,%d,%d] traffic: %d",
                hotspot.position().getX(), hotspot.position().getY(), hotspot.position().getZ(),
                hotspot.traffic()));
            ctx.getSource().sendSuccess(() -> Objects.requireNonNull(Component.literal(hotspotStr)), false);
        }

        return 1;
    }

    private static int dumpDungeonRuns(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        var service = com.frenkvs.devmod.telemetry.dungeon.DungeonRunService.INSTANCE;
        var summaries = service.getRunSummaries();

        ctx.getSource().sendSuccess(() -> I18n.translate("devmod.telemetry.dungeon_runs", service.getActiveRunsCount()), false);

        if (summaries.isEmpty()) {
            ctx.getSource().sendSuccess(() -> I18n.translate("devmod.telemetry.no_completed_runs"), false);
        } else {
            for (String summary : summaries) {
                String sum = Objects.requireNonNull(summary);
                ctx.getSource().sendSuccess(() -> Objects.requireNonNull(Component.literal(sum)), false);
            }
        }
        return 1;
    }

    private static int getDungeonStats(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        String dungeonId = Objects.requireNonNull(StringArgumentType.getString(ctx, "dungeonId"));
        var stats = com.frenkvs.devmod.telemetry.dungeon.DungeonRunService.INSTANCE.getDungeonStats(dungeonId);

        if (stats.totalRuns() == 0) {
            ctx.getSource().sendSuccess(() -> I18n.translate("devmod.telemetry.no_runs_dungeon", dungeonId), false);
        } else {
            String statsStr = Objects.requireNonNull(stats.toString());
            ctx.getSource().sendSuccess(() -> Objects.requireNonNull(Component.literal(statsStr)), false);
            ctx.getSource().sendSuccess(() -> Objects.requireNonNull(I18n.translate("devmod.telemetry.deaths")
                .append(Objects.requireNonNull(Component.literal(": " + stats.deaths() + " | ")))
                .append(I18n.translate("devmod.telemetry.successes"))
                .append(Objects.requireNonNull(Component.literal(": " + stats.successes())))), false);
        }
        return 1;
    }

    private static int dumpBacktracking(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        var summaries = com.frenkvs.devmod.telemetry.spatial.BacktrackingService.INSTANCE.getSummaries();

        ctx.getSource().sendSuccess(() -> I18n.translate("devmod.telemetry.backtracking_stats"), false);

        if (summaries.isEmpty()) {
            ctx.getSource().sendSuccess(() -> I18n.translate("devmod.telemetry.no_backtracking_data"), false);
        } else {
            for (String summary : summaries) {
                String sum = Objects.requireNonNull(summary);
                ctx.getSource().sendSuccess(() -> Objects.requireNonNull(Component.literal(sum)), false);
            }
        }
        return 1;
    }

    private static int getMostConfusingRooms(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        var confusing = com.frenkvs.devmod.telemetry.spatial.BacktrackingService.INSTANCE.getMostConfusingRooms(10);

        ctx.getSource().sendSuccess(() -> I18n.translate("devmod.telemetry.confusing_rooms"), false);

        if (confusing.isEmpty()) {
            ctx.getSource().sendSuccess(() -> I18n.translate("devmod.telemetry.no_backtracking_data"), false);
        } else {
            int rank = 1;
            for (String room : confusing) {
                int finalRank = rank++;
                ctx.getSource().sendSuccess(() -> Component.literal(finalRank + ". " + room), false);
            }
        }
        return 1;
    }
}
