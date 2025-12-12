#!/bin/bash
#
# P1 Spatial Gate Verification Script
# ====================================
# Verifies that spatial events are correctly written to DuckDB.
#
# Usage:
#   ./scripts/p1_spatial_gate.sh [--minutes N] [--report PATH]
#
# Prerequisites:
#   1. Server running (./gradlew runClient or runServer)
#   2. DuckDB enabled, NDJSON_FALLBACK=false
#   3. Perform test actions in-game (see PLAYTEST CHECKLIST below)
#
# PLAYTEST CHECKLIST (do these in order):
#   [ ] 1. Walk around and enter different areas (triggers room_transitions)
#   [ ] 2. Fall from 4+ blocks height (triggers parkour_fall alert)
#   [ ] 3. Walk into a barrier block if available (triggers invisible_collision)
#   [ ] 4. Walk back to a room you visited earlier (triggers backtrack alert)
#   [ ] 5. Find/spawn a mob, run away to drop aggro (triggers aggro_drop alert)
#   [ ] 6. Attack a mob repeatedly from same position (triggers camping alert)
#   [ ] 7. Exit world (flushes DuckDB buffers, triggers choke_point)
#
# Note: stuck, spin, kiting_path, reset alerts require specific conditions
#       and may not be reproducible in a short playtest.
#
# Exit codes:
#   0 = PASS (required tables have data)
#   1 = FAIL (one or more required tables empty)
#   2 = ERROR (script/setup error)
#

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
DB_PATH="$PROJECT_DIR/run/telemetry/devmod_telemetry.duckdb"
MINUTES=10
REPORT_PATH="/tmp/p1_spatial_report.txt"

# Parse arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        --minutes)
            MINUTES="$2"
            shift 2
            ;;
        --report)
            REPORT_PATH="$2"
            shift 2
            ;;
        --help)
            head -40 "$0" | tail -35
            exit 0
            ;;
        *)
            shift
            ;;
    esac
done

# Start report
exec > >(tee "$REPORT_PATH") 2>&1

echo "============================================"
echo "  P1 SPATIAL GATE VERIFICATION"
echo "============================================"
echo ""
echo "Timestamp: $(date -u +"%Y-%m-%dT%H:%M:%SZ")"
echo "Database: $DB_PATH"
echo "Time window: last $MINUTES minutes"
echo "Report: $REPORT_PATH"
echo ""

# Check database exists
if [[ ! -f "$DB_PATH" ]]; then
    echo "ERROR: Database not found at $DB_PATH"
    echo "Run the game first to create the database."
    exit 2
fi

# Find DuckDB jar
DUCKDB_JAR=$(find ~/.gradle/caches/modules-2/files-2.1/org.duckdb/duckdb_jdbc -name "duckdb_jdbc-*.jar" 2>/dev/null | head -1)
if [[ -z "$DUCKDB_JAR" ]]; then
    echo "ERROR: DuckDB JDBC jar not found in Gradle cache"
    echo "Run './gradlew build' first."
    exit 2
fi

echo "DuckDB JAR: $(basename "$DUCKDB_JAR")"
echo ""

# Check config from DuckDBConfig.java
echo "============================================"
echo "  CONFIG CHECK"
echo "============================================"
echo ""
CONFIG_FILE="$PROJECT_DIR/src/main/java/com/frenkvs/devmod/telemetry/duckdb/DuckDBConfig.java"
if [[ -f "$CONFIG_FILE" ]]; then
    echo "DuckDBConfig.java:"
    grep -E "ENABLED|NDJSON_FALLBACK|FALLBACK_ON_ERROR" "$CONFIG_FILE" | head -5 || echo "  (could not parse)"
else
    echo "  Config file not found"
fi
echo ""

# Check for recent DuckDB errors in logs
echo "Recent DuckDB log entries:"
LOG_FILE="$PROJECT_DIR/run/logs/latest.log"
if [[ -f "$LOG_FILE" ]]; then
    grep -i "\[DuckDB\]" "$LOG_FILE" 2>/dev/null | tail -5 || echo "  (no DuckDB log entries)"
    echo ""
    echo "DuckDB errors (if any):"
    grep -i "\[DuckDB\].*error\|circuit.*breaker\|failed" "$LOG_FILE" 2>/dev/null | tail -3 || echo "  (no errors found)"
else
    echo "  Log file not found"
fi
echo ""

# Create temp Java file for querying
TEMP_DIR=$(mktemp -d)
JAVA_FILE="$TEMP_DIR/P1SpatialGate.java"

cat > "$JAVA_FILE" << 'JAVAEOF'
import java.sql.*;

public class P1SpatialGate {
    static String[] TABLES = {"spatial_alerts", "spatial_room_transitions"};
    static int[] REQUIRED = {3, 2}; // Minimum required rows for PASS

    public static void main(String[] args) throws Exception {
        String dbPath = args[0];
        int minutes = Integer.parseInt(args[1]);
        String projectDir = args[2];

        Class.forName("org.duckdb.DuckDBDriver");
        try (Connection conn = DriverManager.getConnection("jdbc:duckdb:" + dbPath)) {
            Statement stmt = conn.createStatement();

            String interval = "NOW() - INTERVAL '" + minutes + " MINUTES'";

            // Get counts
            int[] counts = new int[TABLES.length];
            String[] maxTs = new String[TABLES.length];
            int[] totalCounts = new int[TABLES.length];

            for (int i = 0; i < TABLES.length; i++) {
                counts[i] = getCount(stmt, TABLES[i], interval);
                totalCounts[i] = getTotalCount(stmt, TABLES[i]);
                maxTs[i] = getMaxTs(stmt, TABLES[i]);
            }

            // Print config/status
            System.out.println("============================================");
            System.out.println("  DATABASE STATUS");
            System.out.println("============================================");
            System.out.println("");
            System.out.println("DB file size: " + new java.io.File(dbPath).length() / 1024 + " KB");
            System.out.println("Query time window: last " + minutes + " minutes");
            System.out.println("");

            // Print counts table
            System.out.println("============================================");
            System.out.println("  TABLE COUNTS");
            System.out.println("============================================");
            System.out.println("");
            System.out.println(String.format("%-30s %8s %8s %6s %s", "TABLE", "LAST_N", "TOTAL", "REQ", "STATUS"));
            System.out.println("-".repeat(75));

            boolean allPass = true;
            for (int i = 0; i < TABLES.length; i++) {
                String status;
                if (counts[i] >= REQUIRED[i]) {
                    status = "PASS (>=" + REQUIRED[i] + ")";
                } else {
                    status = "FAIL (<" + REQUIRED[i] + ")";
                    allPass = false;
                }
                System.out.println(String.format("%-30s %8d %8d %6d %s",
                    TABLES[i], counts[i], totalCounts[i], REQUIRED[i], status));
            }
            System.out.println("");

            // Alert types breakdown
            System.out.println("============================================");
            System.out.println("  ALERT TYPE BREAKDOWN");
            System.out.println("============================================");
            System.out.println("");
            showAlertTypeBreakdown(stmt, interval);
            System.out.println("");

            // Print MAX(ts) for each table
            System.out.println("============================================");
            System.out.println("  MAX TIMESTAMP PER TABLE");
            System.out.println("============================================");
            System.out.println("");
            for (int i = 0; i < TABLES.length; i++) {
                System.out.println(TABLES[i] + ": " + (maxTs[i] != null ? maxTs[i] : "(no data)"));
            }
            System.out.println("");

            // Show sample data (LIMIT 5 per table)
            System.out.println("============================================");
            System.out.println("  SAMPLE ROWS (LIMIT 5 per table)");
            System.out.println("============================================");
            System.out.println("");

            showSample(stmt, "spatial_alerts",
                "SELECT id, ts, alert_type, player_name, entity_name, room, x, y, z FROM spatial_alerts ORDER BY ts DESC LIMIT 5");
            showSample(stmt, "spatial_room_transitions",
                "SELECT id, ts, player_name, room FROM spatial_room_transitions ORDER BY ts DESC LIMIT 5");

            // Anti-spam check
            System.out.println("============================================");
            System.out.println("  ANTI-SPAM SANITY CHECKS");
            System.out.println("============================================");
            System.out.println("");
            double alertsPerMin = (double) counts[0] / Math.max(1, minutes);
            double transitionsPerMin = (double) counts[1] / Math.max(1, minutes);
            System.out.println("spatial_alerts events/min: " + String.format("%.2f", alertsPerMin));
            System.out.println("  (expect <30/min - alerts are deduplicated with TTL)");
            System.out.println("");
            System.out.println("spatial_room_transitions events/min: " + String.format("%.2f", transitionsPerMin));
            System.out.println("  (expect <10/min unless actively moving between rooms)");
            System.out.println("");

            // If FAIL, show diagnostics
            if (!allPass) {
                System.out.println("============================================");
                System.out.println("  FAIL DIAGNOSTICS");
                System.out.println("============================================");
                System.out.println("");

                for (int i = 0; i < TABLES.length; i++) {
                    if (counts[i] < REQUIRED[i]) {
                        System.out.println("--- " + TABLES[i] + " ---");
                        System.out.println("Required: >= " + REQUIRED[i] + ", Got: " + counts[i]);
                        System.out.println("");

                        // Show call-site grep
                        String eventFile = projectDir + "/src/main/java/com/frenkvs/devmod/telemetry/TelemetryService.java";
                        String methodName = getMethodName(TABLES[i]);
                        System.out.println("Call-site check (grep for " + methodName + "):");
                        try {
                            ProcessBuilder pb = new ProcessBuilder("grep", "-n", methodName, eventFile);
                            pb.redirectErrorStream(true);
                            Process p = pb.start();
                            java.io.BufferedReader br = new java.io.BufferedReader(new java.io.InputStreamReader(p.getInputStream()));
                            String line;
                            int lineCount = 0;
                            while ((line = br.readLine()) != null && lineCount < 5) {
                                System.out.println("  " + line);
                                lineCount++;
                            }
                            if (lineCount == 0) {
                                System.out.println("  WARNING: No call-site found! Missing wiring.");
                            }
                        } catch (Exception e) {
                            System.out.println("  (grep failed: " + e.getMessage() + ")");
                        }
                        System.out.println("");
                    }
                }
            }

            // ============================================
            // NOISE INVARIANT CHECKS (aggro_drop fix validation)
            // ============================================
            System.out.println("============================================");
            System.out.println("  NOISE INVARIANT CHECKS");
            System.out.println("============================================");
            System.out.println("");

            // Check 1: aggro_drop rate (should be <= 30 in 10min window)
            int aggroCount = getAggroDropCount(stmt, minutes);
            boolean aggroPass = aggroCount <= 30;
            System.out.println("aggro_drop count (last " + minutes + "min): " + aggroCount);
            System.out.println("  threshold: <= 30");
            System.out.println("  status: " + (aggroPass ? "PASS" : "FAIL (too many - possible tick-based bug)"));
            System.out.println("");

            // Check 2: rapid-fire detection (same entity <5s apart should be 0)
            int rapidFire = getRapidFireCount(stmt, minutes);
            boolean rapidPass = rapidFire == 0;
            System.out.println("rapid-fire events (<5s same entity): " + rapidFire);
            System.out.println("  threshold: = 0");
            System.out.println("  status: " + (rapidPass ? "PASS" : "FAIL (edge-detection broken)"));
            System.out.println("");

            // Include noise checks in overall pass/fail
            boolean noisePass = aggroPass && rapidPass;
            if (!noisePass) {
                System.out.println("WARNING: Noise invariants failed!");
                System.out.println("  This indicates aggro_drop is not properly edge-based.");
                System.out.println("  Check EntityTrackingService.checkAggroDrop() implementation.");
                System.out.println("");
            }

            // Final verdict
            System.out.println("============================================");
            System.out.println("  FINAL VERDICT");
            System.out.println("============================================");
            System.out.println("");

            boolean finalPass = allPass && noisePass;
            if (finalPass) {
                System.out.println("=== P1 SPATIAL GATE: PASS ===");
                System.out.println("");
                System.out.println("All required tables have data.");
                System.out.println("All noise invariants passed.");
                System.exit(0);
            } else {
                System.out.println("=== P1 SPATIAL GATE: FAIL ===");
                System.out.println("");
                if (!allPass) System.out.println("- Missing data in required tables.");
                if (!noisePass) System.out.println("- Noise invariants failed (aggro_drop spam detected).");
                System.out.println("See diagnostics above.");
                System.exit(1);
            }
        }
    }

    static int getAggroDropCount(Statement stmt, int minutes) throws SQLException {
        String sql = "SELECT COUNT(*) FROM spatial_alerts WHERE alert_type='aggro_drop' AND ts >= NOW() - INTERVAL '" + minutes + " MINUTES'";
        ResultSet rs = stmt.executeQuery(sql);
        rs.next();
        return rs.getInt(1);
    }

    static int getRapidFireCount(Statement stmt, int minutes) throws SQLException {
        String sql = "WITH ordered AS (" +
            "  SELECT entity_name, ts, " +
            "         LAG(ts) OVER (PARTITION BY entity_name ORDER BY ts) AS prev_ts " +
            "  FROM spatial_alerts " +
            "  WHERE alert_type='aggro_drop' AND ts >= NOW() - INTERVAL '" + minutes + " MINUTES'" +
            ") " +
            "SELECT COUNT(*) FROM ordered " +
            "WHERE prev_ts IS NOT NULL AND ts - prev_ts < INTERVAL '5 seconds'";
        ResultSet rs = stmt.executeQuery(sql);
        rs.next();
        return rs.getInt(1);
    }

    static int getCount(Statement stmt, String table, String interval) throws SQLException {
        ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM " + table + " WHERE ts >= " + interval);
        rs.next();
        return rs.getInt(1);
    }

    static int getTotalCount(Statement stmt, String table) throws SQLException {
        ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM " + table);
        rs.next();
        return rs.getInt(1);
    }

    static String getMaxTs(Statement stmt, String table) throws SQLException {
        ResultSet rs = stmt.executeQuery("SELECT MAX(ts) FROM " + table);
        if (rs.next()) {
            Timestamp ts = rs.getTimestamp(1);
            return ts != null ? ts.toString() : null;
        }
        return null;
    }

    static String getMethodName(String table) {
        switch (table) {
            case "spatial_alerts": return "logAlert";
            case "spatial_room_transitions": return "logRoomTransition";
            default: return table;
        }
    }

    static void showAlertTypeBreakdown(Statement stmt, String interval) throws SQLException {
        String sql = "SELECT alert_type, COUNT(*) as cnt FROM spatial_alerts WHERE ts >= " + interval + " GROUP BY alert_type ORDER BY cnt DESC";
        ResultSet rs = stmt.executeQuery(sql);
        System.out.println(String.format("%-25s %8s", "ALERT_TYPE", "COUNT"));
        System.out.println("-".repeat(35));
        int rows = 0;
        while (rs.next()) {
            System.out.println(String.format("%-25s %8d", rs.getString(1), rs.getInt(2)));
            rows++;
        }
        if (rows == 0) System.out.println("(no alerts in time window)");
    }

    static void showSample(Statement stmt, String tableName, String sql) throws SQLException {
        System.out.println("-- " + tableName + " --");
        ResultSet rs = stmt.executeQuery(sql);
        ResultSetMetaData meta = rs.getMetaData();
        int cols = meta.getColumnCount();

        // Header
        StringBuilder header = new StringBuilder();
        for (int i = 1; i <= cols; i++) {
            if (i > 1) header.append(" | ");
            header.append(meta.getColumnName(i));
        }
        System.out.println(header);

        // Rows
        int rows = 0;
        while (rs.next() && rows < 5) {
            StringBuilder row = new StringBuilder();
            for (int i = 1; i <= cols; i++) {
                if (i > 1) row.append(" | ");
                String val = rs.getString(i);
                if (val != null && val.length() > 32) val = val.substring(0, 29) + "...";
                row.append(val != null ? val : "NULL");
            }
            System.out.println(row);
            rows++;
        }
        if (rows == 0) System.out.println("(no data)");
        System.out.println("");
    }
}
JAVAEOF

# Compile and run
cd "$TEMP_DIR"
javac -cp "$DUCKDB_JAR" P1SpatialGate.java 2>/dev/null
java -cp ".:$DUCKDB_JAR" P1SpatialGate "$DB_PATH" "$MINUTES" "$PROJECT_DIR"
EXIT_CODE=$?

# Cleanup
rm -rf "$TEMP_DIR"

echo ""
echo "============================================"
echo "Report saved to: $REPORT_PATH"
echo "============================================"

exit $EXIT_CODE
