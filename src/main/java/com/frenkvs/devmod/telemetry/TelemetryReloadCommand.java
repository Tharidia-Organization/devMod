package com.frenkvs.devmod.telemetry;

import com.frenkvs.devmod.util.I18n;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
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
                                        .executes(TelemetryReloadCommand::reload))
                                .then(Commands.literal("dump")
                                        .then(Commands.literal("weapons")
                                                .executes(TelemetryReloadCommand::dumpWeapons))
                                        .then(Commands.literal("rooms")
                                                .executes(TelemetryReloadCommand::dumpRooms))
                                        .then(Commands.literal("fights")
                                                .executes(TelemetryReloadCommand::dumpFights))
                                        .then(Commands.literal("minions")
                                                .executes(TelemetryReloadCommand::dumpMinions)))
                                .then(Commands.literal("export")
                                        .then(Commands.literal("heatmaps")
                                                .executes(TelemetryReloadCommand::exportHeatmaps))
                                        .then(Commands.literal("png")
                                                .executes(TelemetryReloadCommand::exportHeatmapsPng))
                                        .then(Commands.literal("csv")
                                                .executes(TelemetryReloadCommand::exportCsv))
                                        .then(Commands.literal("json")
                                                .executes(TelemetryReloadCommand::exportJsonReport))
                                        .then(Commands.literal("all")
                                                .executes(TelemetryReloadCommand::exportAll)))
                                .then(Commands.literal("scan")
                                        .then(Commands.literal("light")
                                                .executes(TelemetryReloadCommand::scanLightAll)
                                                .then(Commands.argument("roomId", StringArgumentType.string())
                                                        .executes(TelemetryReloadCommand::scanLightRoom))))
                                .then(Commands.literal("spawnability")
                                        .then(Commands.argument("roomId", StringArgumentType.string())
                                                .executes(TelemetryReloadCommand::checkSpawnability)))
                                .then(Commands.literal("desirelines")
                                        .executes(TelemetryReloadCommand::dumpDesireLines)
                                        .then(Commands.argument("roomId", StringArgumentType.string())
                                                .executes(TelemetryReloadCommand::analyzeDesireLines)))
                                .then(Commands.literal("dungeons")
                                        .executes(TelemetryReloadCommand::dumpDungeonRuns)
                                        .then(Commands.argument("dungeonId", StringArgumentType.string())
                                                .executes(TelemetryReloadCommand::getDungeonStats)))
                                .then(Commands.literal("backtracking")
                                        .executes(TelemetryReloadCommand::dumpBacktracking)
                                        .then(Commands.literal("confusing")
                                                .executes(TelemetryReloadCommand::getMostConfusingRooms))))
        );
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
        String roomId = StringArgumentType.getString(ctx, "roomId");
        ctx.getSource().sendSuccess(() -> I18n.translate("devmod.telemetry.scanning_room", roomId), true);
        TelemetryService.INSTANCE.scanRoomLightLevels(level, roomId);
        ctx.getSource().sendSuccess(() -> I18n.translate("devmod.telemetry.scan_room_complete", roomId), true);
        return 1;
    }

    private static int checkSpawnability(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerLevel level = ctx.getSource().getLevel();
        String roomId = StringArgumentType.getString(ctx, "roomId");
        var report = TelemetryService.INSTANCE.getSpawnabilityReport(level, roomId);
        ctx.getSource().sendSuccess(() -> Component.literal(report.toString()), false);
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
        ctx.getSource().sendSuccess(() -> I18n.translate("devmod.telemetry.total_segments").append(Component.literal(": " + data.totalSegments())), false);

        ctx.getSource().sendSuccess(() -> I18n.translate("devmod.telemetry.top_paths").append(Component.literal(":")), false);
        int count = 0;
        for (var path : data.topPaths()) {
            if (count++ >= 5) break;
            String pathStr = String.format("  [%d,%d,%d] -> [%d,%d,%d] (%dx)",
                path.from().getX(), path.from().getY(), path.from().getZ(),
                path.to().getX(), path.to().getY(), path.to().getZ(),
                path.count());
            ctx.getSource().sendSuccess(() -> Component.literal(pathStr), false);
        }

        ctx.getSource().sendSuccess(() -> I18n.translate("devmod.telemetry.hotspots").append(Component.literal(":")), false);
        count = 0;
        for (var hotspot : data.hotspots()) {
            if (count++ >= 5) break;
            String hotspotStr = String.format("  [%d,%d,%d] traffic: %d",
                hotspot.position().getX(), hotspot.position().getY(), hotspot.position().getZ(),
                hotspot.traffic());
            ctx.getSource().sendSuccess(() -> Component.literal(hotspotStr), false);
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
                ctx.getSource().sendSuccess(() -> Component.literal(summary), false);
            }
        }
        return 1;
    }

    private static int getDungeonStats(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        String dungeonId = StringArgumentType.getString(ctx, "dungeonId");
        var stats = com.frenkvs.devmod.telemetry.dungeon.DungeonRunService.INSTANCE.getDungeonStats(dungeonId);

        if (stats.totalRuns() == 0) {
            ctx.getSource().sendSuccess(() -> I18n.translate("devmod.telemetry.no_runs_dungeon", dungeonId), false);
        } else {
            ctx.getSource().sendSuccess(() -> Component.literal(stats.toString()), false);
            ctx.getSource().sendSuccess(() -> I18n.translate("devmod.telemetry.deaths")
                .append(Component.literal(": " + stats.deaths() + " | "))
                .append(I18n.translate("devmod.telemetry.successes"))
                .append(Component.literal(": " + stats.successes())), false);
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
                ctx.getSource().sendSuccess(() -> Component.literal(summary), false);
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
