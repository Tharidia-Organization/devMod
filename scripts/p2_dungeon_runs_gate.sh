#!/bin/bash
#
# P2-B Dungeon Runs Gate Verification Script
# ===========================================
# Verifies that dungeon run events are correctly written to DuckDB.
#
# Usage:
#   ./scripts/p2_dungeon_runs_gate.sh [--minutes N] [--report PATH]
#
# Prerequisites:
#   1. Server running (./gradlew runClient or runServer)
#   2. DuckDB enabled, NDJSON_FALLBACK=false
#   3. Perform test actions in-game (see PLAYTEST CHECKLIST below)
#
# PLAYTEST CHECKLIST (do these in order, ~5-10 min):
#   [ ] 1. Enter a dungeon area (room named "dungeon_*" or "*_dungeon*")
#   [ ] 2. Kill some mobs in the dungeon
#   [ ] 3. Pick up loot in the dungeon
#   [ ] 4. Die in the dungeon (triggers run end with DEATH outcome)
#   [ ] 5. Enter dungeon again and exit cleanly (triggers ABANDONED outcome)
#   [ ] 6. Exit world cleanly (flushes data to DuckDB)
#
# Note: SUCCESS outcome requires completing a dungeon (game-specific logic).
#       TIMEOUT outcome occurs after 30 minutes of inactivity.
#
# Exit codes:
#   0 = PASS (dungeon run data present + invariants pass)
#   1 = FAIL (missing data or invariant violation)
#   2 = ERROR (script/setup error)
#

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
DB_PATH="$PROJECT_DIR/run/telemetry/devmod_telemetry.duckdb"
NDJSON_PATH="$PROJECT_DIR/run/telemetry/dungeon_runs.ndjson"
MINUTES=15
REPORT_PATH="/tmp/p2_dungeon_runs_report.txt"

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
            head -35 "$0" | tail -30
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
echo "  P2-B DUNGEON RUNS GATE VERIFICATION"
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

# Check for recent log entries
echo "============================================"
echo "  LOG CHECK"
echo "============================================"
echo ""
LOG_FILE="$PROJECT_DIR/run/logs/latest.log"
if [[ -f "$LOG_FILE" ]]; then
    echo "DungeonRunService log entries:"
    grep -i "\[DungeonRun\]" "$LOG_FILE" 2>/dev/null | tail -5 || echo "  (no DungeonRun log entries)"
else
    echo "  Log file not found"
fi
echo ""

# NDJSON Strict Mode Check
echo "============================================"
echo "  NDJSON STRICT MODE CHECK"
echo "============================================"
echo ""
if [[ -f "$NDJSON_PATH" ]]; then
    NDJSON_LINES=$(wc -l < "$NDJSON_PATH" 2>/dev/null || echo "0")
    NDJSON_LINES=$(echo "$NDJSON_LINES" | tr -d ' ')
    echo "NDJSON file exists: $NDJSON_PATH"
    echo "NDJSON line count: $NDJSON_LINES"
    if [[ "$NDJSON_LINES" -gt 0 ]]; then
        echo "WARNING: NDJSON file has data. If NDJSON_FALLBACK=false, this indicates"
        echo "         either old data or the fallback was triggered."
    fi
else
    echo "NDJSON file does not exist (expected when NDJSON_FALLBACK=false)"
fi
echo ""

# Create temp Java file for querying
TEMP_DIR=$(mktemp -d)
JAVA_FILE="$TEMP_DIR/P2DungeonRunsGate.java"

cat > "$JAVA_FILE" << 'JAVAEOF'
import java.sql.*;

public class P2DungeonRunsGate {
    // Minimum required rows for PASS
    static int REQUIRED_TOTAL = 1;  // At least 1 dungeon run

    public static void main(String[] args) {
        try {
            String dbPath = args[0];
            int minutes = Integer.parseInt(args[1]);
            runGate(dbPath, minutes);
        } catch (Exception e) {
            System.err.println("FATAL ERROR: " + e.getMessage());
            e.printStackTrace();
            System.exit(2);
        }
    }

    static void runGate(String dbPath, int minutes) throws Exception {
        Class.forName("org.duckdb.DuckDBDriver");
        try (Connection conn = DriverManager.getConnection("jdbc:duckdb:" + dbPath)) {
            Statement stmt = conn.createStatement();

            String interval = "NOW() - INTERVAL '" + minutes + " MINUTES'";

            // Database status
            System.out.println("============================================");
            System.out.println("  DATABASE STATUS");
            System.out.println("============================================");
            System.out.println("");
            System.out.println("DB file size: " + new java.io.File(dbPath).length() / 1024 + " KB");
            System.out.println("Query time window: last " + minutes + " minutes");
            System.out.println("");

            // Check if table exists
            boolean tableExists = checkTableExists(stmt, "dungeon_runs");
            if (!tableExists) {
                System.out.println("ERROR: Table 'dungeon_runs' does not exist.");
                System.out.println("Schema may not be migrated. Re-run with new schema.");
                System.exit(1);
            }

            // Get total count
            int totalCount = getCount(stmt, "dungeon_runs", interval);
            int allTimeCount = getTotalCount(stmt, "dungeon_runs");

            System.out.println("============================================");
            System.out.println("  TABLE COUNTS");
            System.out.println("============================================");
            System.out.println("");
            System.out.println(String.format("%-25s %8s %8s %6s %s", "TABLE", "LAST_N", "TOTAL", "REQ", "STATUS"));
            System.out.println("-".repeat(70));

            boolean tablePass = totalCount >= REQUIRED_TOTAL;
            String status = tablePass ? "PASS (>=" + REQUIRED_TOTAL + ")" : "FAIL (<" + REQUIRED_TOTAL + ")";
            System.out.println(String.format("%-25s %8d %8d %6d %s",
                "dungeon_runs", totalCount, allTimeCount, REQUIRED_TOTAL, status));
            System.out.println("");

            // Outcome breakdown
            System.out.println("============================================");
            System.out.println("  OUTCOME BREAKDOWN");
            System.out.println("============================================");
            System.out.println("");
            showOutcomeBreakdown(stmt, interval);
            System.out.println("");

            // Sample rows
            System.out.println("============================================");
            System.out.println("  SAMPLE ROWS (LIMIT 10)");
            System.out.println("============================================");
            System.out.println("");
            showSample(stmt, "dungeon_runs",
                "SELECT id, start_ts, duration_ms, player_name, dungeon_id, outcome, rooms_visited, deaths, kills, reward_count " +
                "FROM dungeon_runs ORDER BY start_ts DESC LIMIT 10");

            // ============================================
            // INVARIANT CHECKS
            // ============================================
            System.out.println("============================================");
            System.out.println("  INVARIANT CHECKS");
            System.out.println("============================================");
            System.out.println("");

            // Check 1: Outcome whitelist
            boolean outcomePass = checkOutcomeWhitelist(stmt, interval);
            System.out.println("outcome whitelist: " + (outcomePass ? "PASS" : "FAIL (invalid outcomes found)"));

            // Check 2: duration_ms > 0
            int invalidDuration = getInvalidDuration(stmt, interval);
            boolean durationPass = invalidDuration == 0;
            System.out.println("duration_ms > 0: " + invalidDuration + " violations");
            System.out.println("  status: " + (durationPass ? "PASS" : "FAIL"));

            // Check 3: start_ts <= end_ts
            int invalidTimestamps = getInvalidTimestamps(stmt, interval);
            boolean timestampPass = invalidTimestamps == 0;
            System.out.println("start_ts <= end_ts: " + invalidTimestamps + " violations");
            System.out.println("  status: " + (timestampPass ? "PASS" : "FAIL"));

            // Check 4: reward_count >= 0
            int negativeRewards = getNegativeRewards(stmt, interval);
            boolean rewardPass = negativeRewards == 0;
            System.out.println("reward_count >= 0: " + negativeRewards + " violations");
            System.out.println("  status: " + (rewardPass ? "PASS" : "FAIL"));

            // Check 5: rooms_visited >= 1
            int invalidRooms = getInvalidRooms(stmt, interval);
            boolean roomsPass = invalidRooms == 0;
            System.out.println("rooms_visited >= 1: " + invalidRooms + " violations");
            System.out.println("  status: " + (roomsPass ? "PASS" : "FAIL"));

            // Check 6: deaths >= 0, kills >= 0
            int negativeCounts = getNegativeCounts(stmt, interval);
            boolean countsPass = negativeCounts == 0;
            System.out.println("deaths/kills >= 0: " + negativeCounts + " violations");
            System.out.println("  status: " + (countsPass ? "PASS" : "FAIL"));
            System.out.println("");

            // Final verdict
            boolean allInvariantsPass = outcomePass && durationPass && timestampPass && rewardPass && roomsPass && countsPass;
            boolean finalPass = tablePass && allInvariantsPass;

            System.out.println("============================================");
            System.out.println("  FINAL VERDICT");
            System.out.println("============================================");
            System.out.println("");

            if (finalPass) {
                System.out.println("=== P2-B DUNGEON RUNS GATE: PASS ===");
                System.out.println("");
                System.out.println("Dungeon run data present in DuckDB.");
                System.out.println("All invariants passed.");
                System.exit(0);
            } else {
                System.out.println("=== P2-B DUNGEON RUNS GATE: FAIL ===");
                System.out.println("");
                if (!tablePass) System.out.println("- Missing data: need >= " + REQUIRED_TOTAL + " rows, got " + totalCount);
                if (!outcomePass) System.out.println("- Invalid outcome values detected");
                if (!durationPass) System.out.println("- Invalid duration_ms (<= 0) detected");
                if (!timestampPass) System.out.println("- Invalid timestamps (start_ts > end_ts) detected");
                if (!rewardPass) System.out.println("- Negative reward_count detected");
                if (!roomsPass) System.out.println("- Invalid rooms_visited (< 1) detected");
                if (!countsPass) System.out.println("- Negative deaths/kills detected");
                System.out.println("");
                System.out.println("See diagnostics above.");
                System.exit(1);
            }
        }
    }

    static boolean checkTableExists(Statement stmt, String tableName) throws SQLException {
        ResultSet rs = stmt.executeQuery(
            "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = '" + tableName + "'");
        rs.next();
        return rs.getInt(1) > 0;
    }

    static int getCount(Statement stmt, String table, String interval) throws SQLException {
        ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM " + table + " WHERE start_ts >= " + interval);
        rs.next();
        return rs.getInt(1);
    }

    static int getTotalCount(Statement stmt, String table) throws SQLException {
        ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM " + table);
        rs.next();
        return rs.getInt(1);
    }

    static void showOutcomeBreakdown(Statement stmt, String interval) throws SQLException {
        String sql = "SELECT outcome, COUNT(*) as cnt, AVG(duration_ms)/1000 as avg_duration_sec, SUM(kills) as total_kills " +
                     "FROM dungeon_runs WHERE start_ts >= " + interval +
                     " GROUP BY outcome ORDER BY cnt DESC";
        ResultSet rs = stmt.executeQuery(sql);
        System.out.println(String.format("%-15s %8s %12s %12s", "OUTCOME", "COUNT", "AVG_DUR_SEC", "TOTAL_KILLS"));
        System.out.println("-".repeat(50));
        int rows = 0;
        while (rs.next()) {
            System.out.println(String.format("%-15s %8d %12.1f %12d",
                rs.getString(1), rs.getInt(2), rs.getDouble(3), rs.getInt(4)));
            rows++;
        }
        if (rows == 0) System.out.println("(no dungeon run data in time window)");
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
        while (rs.next() && rows < 10) {
            StringBuilder row = new StringBuilder();
            for (int i = 1; i <= cols; i++) {
                if (i > 1) row.append(" | ");
                String val = rs.getString(i);
                if (val != null && val.length() > 25) val = val.substring(0, 22) + "...";
                row.append(val != null ? val : "NULL");
            }
            System.out.println(row);
            rows++;
        }
        if (rows == 0) System.out.println("(no data)");
        System.out.println("");
    }

    static boolean checkOutcomeWhitelist(Statement stmt, String interval) throws SQLException {
        String sql = "SELECT DISTINCT outcome FROM dungeon_runs WHERE start_ts >= " + interval +
                     " AND outcome NOT IN ('SUCCESS', 'DEATH', 'ABANDONED', 'TIMEOUT')";
        ResultSet rs = stmt.executeQuery(sql);
        return !rs.next(); // Pass if no invalid outcomes found
    }

    static int getInvalidDuration(Statement stmt, String interval) throws SQLException {
        String sql = "SELECT COUNT(*) FROM dungeon_runs WHERE start_ts >= " + interval +
                     " AND duration_ms <= 0";
        ResultSet rs = stmt.executeQuery(sql);
        rs.next();
        return rs.getInt(1);
    }

    static int getInvalidTimestamps(Statement stmt, String interval) throws SQLException {
        String sql = "SELECT COUNT(*) FROM dungeon_runs WHERE start_ts >= " + interval +
                     " AND start_ts > end_ts";
        ResultSet rs = stmt.executeQuery(sql);
        rs.next();
        return rs.getInt(1);
    }

    static int getNegativeRewards(Statement stmt, String interval) throws SQLException {
        String sql = "SELECT COUNT(*) FROM dungeon_runs WHERE start_ts >= " + interval +
                     " AND reward_count < 0";
        ResultSet rs = stmt.executeQuery(sql);
        rs.next();
        return rs.getInt(1);
    }

    static int getInvalidRooms(Statement stmt, String interval) throws SQLException {
        String sql = "SELECT COUNT(*) FROM dungeon_runs WHERE start_ts >= " + interval +
                     " AND rooms_visited < 1";
        ResultSet rs = stmt.executeQuery(sql);
        rs.next();
        return rs.getInt(1);
    }

    static int getNegativeCounts(Statement stmt, String interval) throws SQLException {
        String sql = "SELECT COUNT(*) FROM dungeon_runs WHERE start_ts >= " + interval +
                     " AND (deaths < 0 OR kills < 0)";
        ResultSet rs = stmt.executeQuery(sql);
        rs.next();
        return rs.getInt(1);
    }
}
JAVAEOF

# Compile and run
cd "$TEMP_DIR"
if ! javac -cp "$DUCKDB_JAR" P2DungeonRunsGate.java; then
    echo "ERROR: Java compilation failed"
    exit 2
fi
java -cp ".:$DUCKDB_JAR" P2DungeonRunsGate "$DB_PATH" "$MINUTES"
EXIT_CODE=$?

# Cleanup
rm -rf "$TEMP_DIR"

echo ""
echo "============================================"
echo "Report saved to: $REPORT_PATH"
echo "============================================"

exit $EXIT_CODE
