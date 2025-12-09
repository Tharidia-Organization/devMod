package com.frenkvs.devmod.telemetry.export;

import com.frenkvs.devmod.telemetry.spatial.BacktrackingService;
import com.frenkvs.devmod.telemetry.spatial.DesireLinesService;
import com.frenkvs.devmod.telemetry.spatial.HeatmapService;
import com.frenkvs.devmod.telemetry.dungeon.DungeonRunService;
import com.frenkvs.devmod.telemetry.room.RoomEntityCounter;
import com.frenkvs.devmod.util.ConfigPaths;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import org.slf4j.Logger;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * VOXEL-LAB FASE 6: JSON Report Export
 *
 * Generates a comprehensive JSON report containing all telemetry data
 * for analysis or import into external tools.
 *
 * Report structure:
 * - metadata: timestamp, version, session info
 * - heatmaps: all heatmap data organized by type
 * - rooms: room statistics and entity counts
 * - dungeons: dungeon run results and statistics
 * - backtracking: confusion metrics per room
 * - desire_lines: movement pattern analysis
 */
public class JsonReportExporter {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String VERSION = "1.0.0";

    /**
     * Exports a complete JSON report.
     * @return Path to the exported file, or null on failure
     */
    public static String exportReport() {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
        Path exportDir = getExportDir();

        try {
            Files.createDirectories(exportDir);
        } catch (IOException e) {
            LOGGER.error("[DevMod] Failed to create JSON export directory: {}", e.getMessage());
            return null;
        }

        String filename = String.format("telemetry_report_%s.json", timestamp);
        Path file = exportDir.resolve(filename);

        try (BufferedWriter writer = Files.newBufferedWriter(file)) {
            writer.write("{\n");

            // Metadata
            writeMetadata(writer, timestamp);
            writer.write(",\n");

            // Heatmaps
            writeHeatmaps(writer);
            writer.write(",\n");

            // Room statistics
            writeRoomStats(writer);
            writer.write(",\n");

            // Dungeon runs
            writeDungeonRuns(writer);
            writer.write(",\n");

            // Backtracking
            writeBacktracking(writer);
            writer.write(",\n");

            // Desire lines
            writeDesireLines(writer);
            writer.write(",\n");

            // Global stats
            writeGlobalStats(writer);

            writer.write("\n}");

            LOGGER.info("[DevMod] Exported JSON report: {}", file);
            return file.toString();
        } catch (IOException e) {
            LOGGER.error("[DevMod] Failed to export JSON report: {}", e.getMessage());
            return null;
        }
    }

    private static void writeMetadata(BufferedWriter writer, String timestamp) throws IOException {
        writer.write("  \"metadata\": {\n");
        writer.write("    \"version\": \"" + VERSION + "\",\n");
        writer.write("    \"timestamp\": \"" + timestamp + "\",\n");
        writer.write("    \"generator\": \"DevMod VOXEL-LAB\",\n");
        writer.write("    \"format\": \"telemetry_report_v1\"\n");
        writer.write("  }");
    }

    private static void writeHeatmaps(BufferedWriter writer) throws IOException {
        writer.write("  \"heatmaps\": {\n");

        boolean first = true;
        first = writeHeatmapType(writer, "death", HeatmapService.INSTANCE.getDeathHeatmap(), first);
        first = writeHeatmapType(writer, "movement", HeatmapService.INSTANCE.getMovementHeatmap(), first);
        first = writeHeatmapType(writer, "stuck", HeatmapService.INSTANCE.getStuckHeatmap(), first);
        first = writeHeatmapType(writer, "camping", HeatmapService.INSTANCE.getCampingHeatmap(), first);
        first = writeHeatmapType(writer, "aggro_drop", HeatmapService.INSTANCE.getAggroDropHeatmap(), first);
        first = writeHeatmapType(writer, "kiting", HeatmapService.INSTANCE.getKitingHeatmap(), first);
        first = writeHeatmapType(writer, "choke_point", HeatmapService.INSTANCE.getChokePointHeatmap(), first);
        first = writeHeatmapType(writer, "invisible_collision", HeatmapService.INSTANCE.getInvisibleCollisionHeatmap(), first);
        writeHeatmapType(writer, "parkour_fall", HeatmapService.INSTANCE.getParkourFallHeatmap(), first);

        writer.write("\n  }");
    }

    private static boolean writeHeatmapType(BufferedWriter writer, String type,
            Map<String, Map<BlockPos, Integer>> heatmap, boolean first) throws IOException {
        if (heatmap.isEmpty()) return first;

        if (!first) writer.write(",\n");
        writer.write("    \"" + type + "\": {\n");

        boolean firstRoom = true;
        for (Map.Entry<String, Map<BlockPos, Integer>> roomEntry : heatmap.entrySet()) {
            if (!firstRoom) writer.write(",\n");
            firstRoom = false;

            writer.write("      \"" + escapeJson(roomEntry.getKey()) + "\": [\n");

            boolean firstPos = true;
            for (Map.Entry<BlockPos, Integer> posEntry : roomEntry.getValue().entrySet()) {
                if (!firstPos) writer.write(",\n");
                firstPos = false;

                BlockPos pos = posEntry.getKey();
                writer.write(String.format("        {\"x\": %d, \"y\": %d, \"z\": %d, \"count\": %d}",
                    pos.getX(), pos.getY(), pos.getZ(), posEntry.getValue()));
            }

            writer.write("\n      ]");
        }

        writer.write("\n    }");
        return false;
    }

    private static void writeRoomStats(BufferedWriter writer) throws IOException {
        writer.write("  \"rooms\": {\n");

        var roomStats = RoomEntityCounter.INSTANCE.getAllRoomStats();
        boolean first = true;
        for (Map.Entry<String, RoomEntityCounter.RoomEntityStats> entry : roomStats.entrySet()) {
            if (!first) writer.write(",\n");
            first = false;

            var stats = entry.getValue();
            writer.write("    \"" + escapeJson(entry.getKey()) + "\": {\n");
            writer.write("      \"total_entities\": " + stats.totalEntities + ",\n");
            writer.write("      \"hostile_mobs\": " + stats.hostileMobs + ",\n");
            writer.write("      \"passive_mobs\": " + stats.passiveMobs + ",\n");
            writer.write("      \"npcs\": " + stats.npcs + ",\n");
            writer.write("      \"players\": " + stats.players + ",\n");
            writer.write("      \"bosses\": " + stats.bosses + "\n");
            writer.write("    }");
        }

        writer.write("\n  }");
    }

    private static void writeDungeonRuns(BufferedWriter writer) throws IOException {
        writer.write("  \"dungeon_runs\": {\n");
        writer.write("    \"active_runs\": " + DungeonRunService.INSTANCE.getActiveRunsCount() + ",\n");
        writer.write("    \"summaries\": [\n");

        var summaries = DungeonRunService.INSTANCE.getRunSummaries();
        boolean first = true;
        for (String summary : summaries) {
            if (!first) writer.write(",\n");
            first = false;
            writer.write("      \"" + escapeJson(summary) + "\"");
        }

        writer.write("\n    ]\n");
        writer.write("  }");
    }

    private static void writeBacktracking(BufferedWriter writer) throws IOException {
        writer.write("  \"backtracking\": {\n");

        var allStats = BacktrackingService.INSTANCE.getAllStats();
        boolean first = true;
        for (Map.Entry<String, BacktrackingService.RoomBacktrackStats> entry : allStats.entrySet()) {
            if (!first) writer.write(",\n");
            first = false;

            var stats = entry.getValue();
            double confusionIndex = BacktrackingService.INSTANCE.getConfusionIndex(entry.getKey());

            writer.write("    \"" + escapeJson(entry.getKey()) + "\": {\n");
            writer.write("      \"backtrack_count\": " + stats.backtrackCount + ",\n");
            writer.write("      \"total_movements\": " + stats.totalMovements + ",\n");
            writer.write("      \"unique_visitors\": " + stats.uniqueVisitors + ",\n");
            writer.write("      \"total_steps_back\": " + stats.totalStepsBack + ",\n");
            writer.write("      \"backtrack_rate\": " + String.format("%.4f", stats.getBacktrackRate()) + ",\n");
            writer.write("      \"confusion_index\": " + String.format("%.4f", confusionIndex) + "\n");
            writer.write("    }");
        }

        writer.write("\n  }");
    }

    private static void writeDesireLines(BufferedWriter writer) throws IOException {
        writer.write("  \"desire_lines\": {\n");

        var allSegments = DesireLinesService.INSTANCE.getAllSegments();
        boolean first = true;
        for (Map.Entry<String, Map<BlockPos, Integer>> entry : allSegments.entrySet()) {
            if (!first) writer.write(",\n");
            first = false;

            writer.write("    \"" + escapeJson(entry.getKey()) + "\": [\n");

            boolean firstPos = true;
            for (Map.Entry<BlockPos, Integer> toEntry : entry.getValue().entrySet()) {
                if (!firstPos) writer.write(",\n");
                firstPos = false;

                BlockPos to = toEntry.getKey();
                writer.write(String.format("      {\"to_x\": %d, \"to_y\": %d, \"to_z\": %d, \"count\": %d}",
                    to.getX(), to.getY(), to.getZ(), toEntry.getValue()));
            }

            writer.write("\n    ]");
        }

        writer.write("\n  }");
    }

    private static void writeGlobalStats(BufferedWriter writer) throws IOException {
        var stats = RoomEntityCounter.INSTANCE.getGlobalStats();

        writer.write("  \"global_stats\": {\n");
        writer.write("    \"total_entities\": " + stats.totalEntities + ",\n");
        writer.write("    \"total_hostile\": " + stats.totalHostile + ",\n");
        writer.write("    \"total_passive\": " + stats.totalPassive + ",\n");
        writer.write("    \"total_players\": " + stats.totalPlayers + ",\n");
        writer.write("    \"total_npcs\": " + stats.totalNpcs + ",\n");
        writer.write("    \"rooms_with_entities\": " + stats.roomsWithEntities + ",\n");
        writer.write("    \"busiest_room\": \"" + escapeJson(stats.busiestRoom) + "\",\n");
        writer.write("    \"busiest_room_count\": " + stats.busiestRoomCount + "\n");
        writer.write("  }");
    }

    /**
     * Escapes a string for JSON.
     */
    private static String escapeJson(String value) {
        if (value == null) return "";
        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t");
    }

    /**
     * Gets the export directory path.
     */
    public static Path getExportDir() {
        return ConfigPaths.getTelemetryExportDir().resolve("reports");
    }
}
