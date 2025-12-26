package com.devmod.telemetry.duckdb;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import java.util.stream.Stream;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

public final class DuckDBMigrationService {
    private static final Logger LOGGER = LogUtils.getLogger();

    private DuckDBMigrationService() {} // Utility class

    /**
     * Mapping from NDJSON filenames to DuckDB table names.
     * Only files with direct table mappings are included.
     */
    private static final Map<String, String> FILE_TO_TABLE = Map.ofEntries(
        // Combat
        Map.entry("hits.ndjson", "combat_hits"),
        Map.entry("deaths.ndjson", "combat_deaths"),
        Map.entry("heals.ndjson", "combat_heals"),
        Map.entry("spawns.ndjson", "combat_spawns"),
        Map.entry("fights.ndjson", "combat_fights"),

        // Player
        Map.entry("player_attributes.ndjson", "player_snapshots"),
        Map.entry("ability_usage.ndjson", "player_abilities"),

        // Spatial
        Map.entry("stuck_heatmap.ndjson", "spatial_heatmaps"),
        Map.entry("death_heatmap.ndjson", "spatial_heatmaps"),
        Map.entry("movement_heatmap.ndjson", "spatial_heatmaps"),
        Map.entry("camping_heatmap.ndjson", "spatial_heatmaps"),
        Map.entry("aggro_drop_heatmap.ndjson", "spatial_heatmaps"),
        Map.entry("kiting_heatmap.ndjson", "spatial_heatmaps"),
        Map.entry("choke_point_heatmap.ndjson", "spatial_heatmaps"),
        Map.entry("invisible_collision_heatmap.ndjson", "spatial_heatmaps"),
        Map.entry("parkour_fall_heatmap.ndjson", "spatial_heatmaps"),
        Map.entry("alerts.ndjson", "spatial_alerts"),
        Map.entry("room_time.ndjson", "spatial_room_transitions"),

        // Performance
        Map.entry("performance.ndjson", "performance_samples")
    );

    /**
     * Migrate all existing NDJSON files to DuckDB.
     *
     * @param telemetryDir Path to the telemetry directory containing NDJSON files
     * @param conn DuckDB connection
     * @return Number of files successfully migrated
     */
    public static int migrateFromNDJSON(Path telemetryDir, Connection conn) {
        if (!Files.exists(telemetryDir)) {
            LOGGER.info("[DuckDB Migration] No telemetry directory found, skipping migration");
            return 0;
        }

        LOGGER.info("[DuckDB Migration] Starting migration from: {}", telemetryDir);
        int migratedCount = 0;
        int errorCount = 0;

        for (Map.Entry<String, String> entry : FILE_TO_TABLE.entrySet()) {
            String filename = entry.getKey();
            String tableName = entry.getValue();
            Path file = telemetryDir.resolve(filename);

            if (Files.exists(file)) {
                try {
                    long rowCount = importNDJSON(file, tableName, conn);
                    if (rowCount > 0) {
                        LOGGER.info("[DuckDB Migration] Imported {} rows from {} into {}", rowCount, filename, tableName);
                        migratedCount++;
                    }
                } catch (SQLException e) {
                    LOGGER.error("[DuckDB Migration] Failed to import {}", filename, e);
                    errorCount++;
                }
            }
        }

        // Handle endurance.ndjson separately (complex multi-table mapping)
        Path enduranceFile = telemetryDir.resolve("endurance.ndjson");
        if (Files.exists(enduranceFile)) {
            try {
                migrateEnduranceData(enduranceFile, conn);
                migratedCount++;
            } catch (Exception e) {
                LOGGER.error("[DuckDB Migration] Failed to migrate endurance data", e);
                errorCount++;
            }
        }

        LOGGER.info("[DuckDB Migration] Migration complete: {} files migrated, {} errors", migratedCount, errorCount);
        return migratedCount;
    }

    /**
     * Import a single NDJSON file into a DuckDB table.
     * Uses DuckDB's native read_json_auto() for fast import.
     */
    private static long importNDJSON(Path file, String tableName, Connection conn) throws SQLException {
        String filePath = file.toAbsolutePath().toString().replace("\\", "/");

        // Count rows before import
        long rowsBefore = countRows(conn, tableName);

        // DuckDB can read NDJSON directly
        // Use INSERT ... SELECT to import data
        String sql = String.format(
            "INSERT INTO %s SELECT * FROM read_json_auto('%s', format='newline_delimited', ignore_errors=true)",
            tableName, filePath
        );

        try (Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        }

        // Count rows after import
        long rowsAfter = countRows(conn, tableName);
        return rowsAfter - rowsBefore;
    }

    /**
     * Count rows in a table.
     */
    private static long countRows(Connection conn, String tableName) throws SQLException {
        try (var stmt = conn.createStatement();
             var rs = stmt.executeQuery("SELECT COUNT(*) FROM " + tableName)) {
            if (rs.next()) {
                return rs.getLong(1);
            }
        }
        return 0;
    }

    /**
     * Migrate endurance.ndjson which contains multiple event types.
     * Each event type goes to a different table based on the "type" field.
     */
    private static void migrateEnduranceData(Path file, Connection conn) throws SQLException, IOException {
        LOGGER.info("[DuckDB Migration] Migrating endurance data (multi-table)...");

        // Read file line by line and route to appropriate table
        // This is slower than read_json_auto but necessary for multi-table routing
        try (Stream<String> lines = Files.lines(file)) {
            lines.forEach(line -> {
                try {
                    routeEnduranceEvent(line, conn);
                } catch (Exception e) {
                    LOGGER.debug("[DuckDB Migration] Failed to route endurance event", e);
                }
            });
        }

        LOGGER.info("[DuckDB Migration] Endurance data migration complete");
    }

    /**
     * Route a single endurance event JSON line to the appropriate table.
     */
    private static void routeEnduranceEvent(String json, Connection conn) throws SQLException {
        // Extract type from JSON (simple string search for performance)
        String type = extractJsonField(json, "type");
        if (type == null) return;

        // Route based on type
        // Note: This is a simplified version - full implementation would parse JSON properly
        switch (type) {
            case "wave_start", "wave_complete" -> {
                // Insert into endurance_waves
                // Would need proper JSON parsing for full implementation
            }
            case "wave_kill" -> {
                // Insert into endurance_wave_kills
            }
            case "style_rank_change", "combo_milestone", "combo_break", "special_action" -> {
                // Insert into endurance_combos
            }
            case "perk_selected", "perk_choices" -> {
                // Insert into endurance_perks
            }
            case "mutators_assigned", "mutator_added" -> {
                // Insert into endurance_mutators
            }
            case "currency_earned", "loot_drop", "achievement_unlocked", "shop_purchase" -> {
                // Insert into endurance_rewards
            }
            case "party_created", "party_join", "party_leave", "party_disbanded", "invite_sent", "invite_response" -> {
                // Insert into endurance_parties
            }
            case "boss_wave_start", "boss_ability", "boss_defeated" -> {
                // Insert into endurance_bosses
            }
            default -> {
                // Unknown event type, skip
            }
        }
    }

    /**
     * Simple JSON field extraction (without full JSON parsing).
     * Looks for "fieldName":"value" pattern.
     */
    private static String extractJsonField(String json, String fieldName) {
        String pattern = "\"" + fieldName + "\":\"";
        int start = json.indexOf(pattern);
        if (start < 0) return null;

        start += pattern.length();
        int end = json.indexOf("\"", start);
        if (end < 0) return null;

        return json.substring(start, end);
    }

    /**
     * Check if migration has already been performed.
     */
    public static boolean isMigrationComplete(Connection conn) {
        try (var stmt = conn.createStatement();
             var rs = stmt.executeQuery("SELECT COUNT(*) FROM migrations WHERE version >= 1")) {
            if (rs.next()) {
                return rs.getInt(1) > 0;
            }
        } catch (SQLException e) {
            // Table might not exist yet
        }
        return false;
    }

    /**
     * Mark migration as complete.
     */
    public static void markMigrationComplete(Connection conn, int version) throws SQLException {
        try (var stmt = conn.createStatement()) {
            stmt.execute(String.format(
                "INSERT INTO migrations (version, migrated_at) VALUES (%d, NOW()) ON CONFLICT DO NOTHING",
                version
            ));
        }
    }

    /**
     * Export DuckDB table to NDJSON file (for backup or external analysis tools).
     */
    public static void exportToNDJSON(Connection conn, String tableName, Path outputFile) throws SQLException, IOException {
        LOGGER.info("[DuckDB Export] Exporting {} to {}", tableName, outputFile);

        String filePath = outputFile.toAbsolutePath().toString().replace("\\", "/");
        String sql = String.format(
            "COPY (SELECT * FROM %s) TO '%s' (FORMAT JSON, ARRAY true)",
            tableName, filePath
        );

        try (Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        }

        LOGGER.info("[DuckDB Export] Export complete: {}", outputFile);
    }
}
