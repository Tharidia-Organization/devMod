#!/bin/bash
#
# P1 Progression Gate Verification Script
# =======================================
# Verifies that progression events are correctly written to DuckDB.
#
# Usage:
#   ./scripts/p1_progression_gate.sh [--minutes N] [--report PATH]
#
# Prerequisites:
#   1. Server running (./gradlew runClient or runServer)
#   2. DuckDB enabled, NDJSON_FALLBACK=false
#   3. Perform test actions in-game (see PLAYTEST CHECKLIST below)
#
# PLAYTEST CHECKLIST (do these in order):
#   [ ] 1. Break 5+ blocks (stone, dirt, etc.)
#   [ ] 2. Place 5+ blocks
#   [ ] 3. Kill a mob to get XP orbs
#   [ ] 4. Build a nether portal, go to Nether, come back
#   [ ] 5. Find/spawn a villager, trade with it
#   [ ] 6. Craft fishing rod, catch a fish
#   [ ] 7. (Optional) Earn an advancement - not required for PASS
#   [ ] 8. Exit world (flushes DuckDB buffers)
#
# Exit codes:
#   0 = PASS (all required tables have data)
#   1 = FAIL (one or more required tables empty)
#   2 = ERROR (script/setup error)
#

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
DB_PATH="$PROJECT_DIR/run/telemetry/devmod_telemetry.duckdb"
MINUTES=10
REPORT_PATH="/tmp/p1_progression_report.txt"

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
echo "  P1 PROGRESSION GATE VERIFICATION"
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
JAVA_FILE="$TEMP_DIR/P1ProgressionGate.java"

cat > "$JAVA_FILE" << 'JAVAEOF'
import java.sql.*;

public class P1ProgressionGate {
    static String[] TABLES = {
        "progression_blocks", "progression_xp", "progression_advancements",
        "progression_dimensions", "progression_trades", "progression_fishing"
    };
    static int[] REQUIRED = {5, 1, 0, 1, 1, 1}; // 0 = optional (SKIPPED if missing)

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
                if (REQUIRED[i] == 0) {
                    status = counts[i] > 0 ? "PASS" : "SKIPPED: not reproducible";
                } else if (counts[i] >= REQUIRED[i]) {
                    status = "PASS (>=" + REQUIRED[i] + ")";
                } else {
                    status = "FAIL (<" + REQUIRED[i] + ")";
                    allPass = false;
                }
                System.out.println(String.format("%-30s %8d %8d %6d %s",
                    TABLES[i], counts[i], totalCounts[i], REQUIRED[i], status));
            }
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

            showSample(stmt, "progression_blocks",
                "SELECT id, ts, player_name, event_type, block_id, x, y, z FROM progression_blocks ORDER BY ts DESC LIMIT 5");
            showSample(stmt, "progression_xp",
                "SELECT id, ts, player_name, event_type, xp_amount, old_level, new_level FROM progression_xp ORDER BY ts DESC LIMIT 5");
            showSample(stmt, "progression_advancements",
                "SELECT id, ts, player_name, advancement_id, title FROM progression_advancements ORDER BY ts DESC LIMIT 5");
            showSample(stmt, "progression_dimensions",
                "SELECT id, ts, player_name, from_dimension, to_dimension FROM progression_dimensions ORDER BY ts DESC LIMIT 5");
            showSample(stmt, "progression_trades",
                "SELECT id, ts, player_name, profession, item_bought, item_sold FROM progression_trades ORDER BY ts DESC LIMIT 5");
            showSample(stmt, "progression_fishing",
                "SELECT id, ts, player_name, item_id, item_count, x, y, z FROM progression_fishing ORDER BY ts DESC LIMIT 5");

            // Anti-spam check
            System.out.println("============================================");
            System.out.println("  ANTI-SPAM SANITY CHECKS");
            System.out.println("============================================");
            System.out.println("");
            double xpPerMin = (double) counts[1] / Math.max(1, minutes);
            double blocksPerMin = (double) counts[0] / Math.max(1, minutes);
            System.out.println("progression_xp events/min: " + String.format("%.2f", xpPerMin));
            System.out.println("  (with 1s batching, expect <60/min even with heavy XP farming)");
            System.out.println("");
            System.out.println("progression_blocks events/min: " + String.format("%.2f", blocksPerMin));
            System.out.println("  (LOW priority - will be dropped first under backpressure)");
            System.out.println("");

            // If FAIL, show diagnostics
            if (!allPass) {
                System.out.println("============================================");
                System.out.println("  FAIL DIAGNOSTICS");
                System.out.println("============================================");
                System.out.println("");

                for (int i = 0; i < TABLES.length; i++) {
                    if (REQUIRED[i] > 0 && counts[i] < REQUIRED[i]) {
                        System.out.println("--- " + TABLES[i] + " ---");
                        System.out.println("Required: >= " + REQUIRED[i] + ", Got: " + counts[i]);
                        System.out.println("");

                        // Show call-site grep
                        String eventFile = projectDir + "/src/main/java/com/frenkvs/devmod/telemetry/progression/ProgressionTrackingEvents.java";
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

            // Final verdict
            System.out.println("============================================");
            System.out.println("  FINAL VERDICT");
            System.out.println("============================================");
            System.out.println("");

            if (allPass) {
                System.out.println("=== P1 PROGRESSION GATE: PASS ===");
                System.out.println("");
                System.out.println("All required tables have data.");
                if (counts[2] == 0) {
                    System.out.println("Note: advancements=0 (SKIPPED: not reproducible)");
                }
                System.exit(0);
            } else {
                System.out.println("=== P1 PROGRESSION GATE: FAIL ===");
                System.out.println("");
                System.out.println("Missing data in required tables. See diagnostics above.");
                System.exit(1);
            }
        }
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
            case "progression_blocks": return "logBlock";
            case "progression_xp": return "logXp";
            case "progression_advancements": return "logAdvancement";
            case "progression_dimensions": return "logDimensionChange";
            case "progression_trades": return "logTrade";
            case "progression_fishing": return "logFishing";
            default: return table;
        }
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
javac -cp "$DUCKDB_JAR" P1ProgressionGate.java 2>/dev/null
java -cp ".:$DUCKDB_JAR" P1ProgressionGate "$DB_PATH" "$MINUTES" "$PROJECT_DIR"
EXIT_CODE=$?

# Cleanup
rm -rf "$TEMP_DIR"

echo ""
echo "============================================"
echo "Report saved to: $REPORT_PATH"
echo "============================================"

exit $EXIT_CODE
