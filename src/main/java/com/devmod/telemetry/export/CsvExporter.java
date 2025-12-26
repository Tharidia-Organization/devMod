package com.devmod.telemetry.export;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

import org.slf4j.Logger;

import net.minecraft.core.BlockPos;

import com.devmod.telemetry.dungeon.DungeonRunService;
import com.devmod.telemetry.room.RoomEntityCounter;
import com.devmod.telemetry.spatial.BacktrackingService;
import com.devmod.telemetry.spatial.DesireLinesService;
import com.devmod.telemetry.spatial.HeatmapService;
import com.devmod.util.ConfigPaths;

import com.mojang.logging.LogUtils;

/**
 * VOXEL-LAB FASE 6: CSV Export
 *
 * Exports telemetry data to CSV format for analysis in spreadsheet tools
 * or data analysis software (Excel, Google Sheets, Python/Pandas, R).
 *
 * Exports include:
 * - Heatmap data (position, count, type)
 * - Room statistics
 * - Dungeon run results
 * - Backtracking statistics
 * - Desire lines data
 * - Entity counts
 */
public class CsvExporter {
    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * Exports all available telemetry data to CSV files.
     * @return Number of files exported
     */
    public static int exportAll() {
        int count = 0;
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
        Path exportDir = getExportDir();

        try {
            Files.createDirectories(exportDir);
        } catch (IOException e) {
            LOGGER.error("[DevMod] Failed to create CSV export directory: {}", e.getMessage());
            return 0;
        }

        count += exportHeatmaps(exportDir, timestamp);
        count += exportRoomStats(exportDir, timestamp);
        count += exportDungeonRuns(exportDir, timestamp);
        count += exportBacktracking(exportDir, timestamp);
        count += exportDesireLines(exportDir, timestamp);
        count += exportEntityCounts(exportDir, timestamp);

        LOGGER.info("[DevMod] Exported {} CSV files to {}", count, exportDir);
        return count;
    }

    /**
     * Exports all heatmap data to CSV.
     */
    public static int exportHeatmaps(Path exportDir, String timestamp) {
        int count = 0;

        count += exportHeatmap(exportDir, timestamp, "death", HeatmapService.INSTANCE.getDeathHeatmap());
        count += exportHeatmap(exportDir, timestamp, "movement", HeatmapService.INSTANCE.getMovementHeatmap());
        count += exportHeatmap(exportDir, timestamp, "stuck", HeatmapService.INSTANCE.getStuckHeatmap());
        count += exportHeatmap(exportDir, timestamp, "camping", HeatmapService.INSTANCE.getCampingHeatmap());
        count += exportHeatmap(exportDir, timestamp, "aggro_drop", HeatmapService.INSTANCE.getAggroDropHeatmap());
        count += exportHeatmap(exportDir, timestamp, "kiting", HeatmapService.INSTANCE.getKitingHeatmap());
        count += exportHeatmap(exportDir, timestamp, "choke_point", HeatmapService.INSTANCE.getChokePointHeatmap());
        count += exportHeatmap(exportDir, timestamp, "invisible_collision", HeatmapService.INSTANCE.getInvisibleCollisionHeatmap());
        count += exportHeatmap(exportDir, timestamp, "parkour_fall", HeatmapService.INSTANCE.getParkourFallHeatmap());

        return count;
    }

    /**
     * Exports a single heatmap type to CSV.
     */
    private static int exportHeatmap(Path exportDir, String timestamp, String type, Map<String, Map<BlockPos, Integer>> heatmap) {
        if (heatmap.isEmpty()) return 0;

        String filename = String.format("heatmap_%s_%s.csv", type, timestamp);
        Path file = exportDir.resolve(filename);

        try (BufferedWriter writer = Files.newBufferedWriter(file)) {
            // Header
            writer.write("room,x,y,z,count,type");
            writer.newLine();

            // Data
            for (Map.Entry<String, Map<BlockPos, Integer>> roomEntry : heatmap.entrySet()) {
                String room = escapeCsv(roomEntry.getKey());
                for (Map.Entry<BlockPos, Integer> posEntry : roomEntry.getValue().entrySet()) {
                    BlockPos pos = posEntry.getKey();
                    writer.write(String.format("%s,%d,%d,%d,%d,%s",
                        room, pos.getX(), pos.getY(), pos.getZ(), posEntry.getValue(), type));
                    writer.newLine();
                }
            }

            LOGGER.debug("[DevMod] Exported heatmap CSV: {}", filename);
            return 1;
        } catch (IOException e) {
            LOGGER.error("[DevMod] Failed to export heatmap CSV: {}", e.getMessage());
            return 0;
        }
    }

    /**
     * Exports room statistics to CSV.
     */
    public static int exportRoomStats(Path exportDir, String timestamp) {
        var roomStats = RoomEntityCounter.INSTANCE.getAllRoomStats();
        if (roomStats.isEmpty()) return 0;

        String filename = String.format("room_stats_%s.csv", timestamp);
        Path file = exportDir.resolve(filename);

        try (BufferedWriter writer = Files.newBufferedWriter(file)) {
            // Header
            writer.write("room,total_entities,hostile_mobs,passive_mobs,npcs,players,bosses,last_update_ms");
            writer.newLine();

            // Data
            for (Map.Entry<String, RoomEntityCounter.RoomEntityStats> entry : roomStats.entrySet()) {
                var stats = entry.getValue();
                writer.write(String.format("%s,%d,%d,%d,%d,%d,%d,%d",
                    escapeCsv(entry.getKey()),
                    stats.totalEntities,
                    stats.hostileMobs,
                    stats.passiveMobs,
                    stats.npcs,
                    stats.players,
                    stats.bosses,
                    stats.lastUpdateMs));
                writer.newLine();
            }

            LOGGER.debug("[DevMod] Exported room stats CSV: {}", filename);
            return 1;
        } catch (IOException e) {
            LOGGER.error("[DevMod] Failed to export room stats CSV: {}", e.getMessage());
            return 0;
        }
    }

    /**
     * Exports dungeon run results to CSV.
     */
    public static int exportDungeonRuns(Path exportDir, String timestamp) {
        var summaries = DungeonRunService.INSTANCE.getRunSummaries();
        if (summaries.isEmpty()) return 0;

        String filename = String.format("dungeon_runs_%s.csv", timestamp);
        Path file = exportDir.resolve(filename);

        try (BufferedWriter writer = Files.newBufferedWriter(file)) {
            // Header
            writer.write("summary");
            writer.newLine();

            // Data (summaries are already formatted strings)
            for (String summary : summaries) {
                writer.write(escapeCsv(summary));
                writer.newLine();
            }

            LOGGER.debug("[DevMod] Exported dungeon runs CSV: {}", filename);
            return 1;
        } catch (IOException e) {
            LOGGER.error("[DevMod] Failed to export dungeon runs CSV: {}", e.getMessage());
            return 0;
        }
    }

    /**
     * Exports backtracking statistics to CSV.
     */
    public static int exportBacktracking(Path exportDir, String timestamp) {
        var allStats = BacktrackingService.INSTANCE.getAllStats();
        if (allStats.isEmpty()) return 0;

        String filename = String.format("backtracking_%s.csv", timestamp);
        Path file = exportDir.resolve(filename);

        try (BufferedWriter writer = Files.newBufferedWriter(file)) {
            // Header
            writer.write("room,backtrack_count,total_movements,unique_visitors,total_steps_back,backtrack_rate,confusion_index");
            writer.newLine();

            // Data
            for (Map.Entry<String, BacktrackingService.RoomBacktrackStats> entry : allStats.entrySet()) {
                var stats = entry.getValue();
                double confusionIndex = BacktrackingService.INSTANCE.getConfusionIndex(entry.getKey());
                writer.write(String.format("%s,%d,%d,%d,%d,%.4f,%.4f",
                    escapeCsv(entry.getKey()),
                    stats.backtrackCount,
                    stats.totalMovements,
                    stats.uniqueVisitors,
                    stats.totalStepsBack,
                    stats.getBacktrackRate(),
                    confusionIndex));
                writer.newLine();
            }

            LOGGER.debug("[DevMod] Exported backtracking CSV: {}", filename);
            return 1;
        } catch (IOException e) {
            LOGGER.error("[DevMod] Failed to export backtracking CSV: {}", e.getMessage());
            return 0;
        }
    }

    /**
     * Exports desire lines data to CSV.
     */
    public static int exportDesireLines(Path exportDir, String timestamp) {
        var allSegments = DesireLinesService.INSTANCE.getAllSegments();
        if (allSegments.isEmpty()) return 0;

        String filename = String.format("desire_lines_%s.csv", timestamp);
        Path file = exportDir.resolve(filename);

        try (BufferedWriter writer = Files.newBufferedWriter(file)) {
            // Header
            writer.write("room_from_key,to_x,to_y,to_z,count");
            writer.newLine();

            // Data
            for (Map.Entry<String, Map<BlockPos, Integer>> entry : allSegments.entrySet()) {
                String fromKey = escapeCsv(entry.getKey());
                for (Map.Entry<BlockPos, Integer> toEntry : entry.getValue().entrySet()) {
                    BlockPos to = toEntry.getKey();
                    writer.write(String.format("%s,%d,%d,%d,%d",
                        fromKey, to.getX(), to.getY(), to.getZ(), toEntry.getValue()));
                    writer.newLine();
                }
            }

            LOGGER.debug("[DevMod] Exported desire lines CSV: {}", filename);
            return 1;
        } catch (IOException e) {
            LOGGER.error("[DevMod] Failed to export desire lines CSV: {}", e.getMessage());
            return 0;
        }
    }

    /**
     * Exports entity counts to CSV.
     */
    public static int exportEntityCounts(Path exportDir, String timestamp) {
        var stats = RoomEntityCounter.INSTANCE.getGlobalStats();

        String filename = String.format("entity_counts_%s.csv", timestamp);
        Path file = exportDir.resolve(filename);

        try (BufferedWriter writer = Files.newBufferedWriter(file)) {
            // Header
            writer.write("metric,value");
            writer.newLine();

            // Data
            writer.write(String.format("total_entities,%d", stats.totalEntities));
            writer.newLine();
            writer.write(String.format("total_hostile,%d", stats.totalHostile));
            writer.newLine();
            writer.write(String.format("total_passive,%d", stats.totalPassive));
            writer.newLine();
            writer.write(String.format("total_players,%d", stats.totalPlayers));
            writer.newLine();
            writer.write(String.format("total_npcs,%d", stats.totalNpcs));
            writer.newLine();
            writer.write(String.format("rooms_with_entities,%d", stats.roomsWithEntities));
            writer.newLine();
            writer.write(String.format("busiest_room,%s", escapeCsv(stats.busiestRoom)));
            writer.newLine();
            writer.write(String.format("busiest_room_count,%d", stats.busiestRoomCount));
            writer.newLine();

            LOGGER.debug("[DevMod] Exported entity counts CSV: {}", filename);
            return 1;
        } catch (IOException e) {
            LOGGER.error("[DevMod] Failed to export entity counts CSV: {}", e.getMessage());
            return 0;
        }
    }

    /**
     * Escapes a string for CSV format.
     */
    private static String escapeCsv(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    /**
     * Gets the export directory path.
     */
    public static Path getExportDir() {
        return ConfigPaths.getTelemetryExportDir().resolve("csv");
    }
}
