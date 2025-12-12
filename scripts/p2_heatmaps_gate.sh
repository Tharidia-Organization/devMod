#!/bin/bash
#
# P2-A Heatmaps Gate Verification Script
# =======================================
# Verifies that heatmap events are correctly written to DuckDB.
#
# Usage:
#   ./scripts/p2_heatmaps_gate.sh [--minutes N] [--report PATH]
#
# Prerequisites:
#   1. Server running (./gradlew runClient or runServer)
#   2. DuckDB enabled, NDJSON_FALLBACK=false
#   3. Perform test actions in-game (see PLAYTEST CHECKLIST below)
#
# PLAYTEST CHECKLIST (do these in order, ~5-10 min):
#   [ ] 1. Walk around for 2+ minutes (triggers movement heatmap)
#   [ ] 2. Die once (triggers death heatmap)
#   [ ] 3. Attack a mob from same position 5+ times (triggers camping heatmap)
#   [ ] 4. Fall from 4+ blocks (triggers parkour_fall heatmap)
#   [ ] 5. Exit world cleanly (flushes heatmap to DuckDB)
#
# Note: stuck, aggro_drop, kiting heatmaps require specific mob AI conditions
#       and may not be reproducible in a short playtest.
#
# Exit codes:
#   0 = PASS (heatmap data present + invariants pass)
#   1 = FAIL (missing data or invariant violation)
#   2 = ERROR (script/setup error)
#

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
DB_PATH="$PROJECT_DIR/run/telemetry/devmod_telemetry.duckdb"
MINUTES=15
REPORT_PATH="/tmp/p2_heatmaps_report.txt"

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
echo "  P2-A HEATMAPS GATE VERIFICATION"
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
    echo "HeatmapService log entries:"
    grep -i "\[HeatmapService\]" "$LOG_FILE" 2>/dev/null | tail -5 || echo "  (no HeatmapService log entries)"
else
    echo "  Log file not found"
fi
echo ""

# Create temp Java file for querying
TEMP_DIR=$(mktemp -d)
JAVA_FILE="$TEMP_DIR/P2HeatmapsGate.java"

cat > "$JAVA_FILE" << 'JAVAEOF'
import java.sql.*;

public class P2HeatmapsGate {
    // Minimum required rows for PASS (aggregated buckets, not raw events)
    static int REQUIRED_TOTAL = 5;  // At least 5 buckets across all types

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

            // Get total count
            int totalCount = getCount(stmt, "spatial_heatmaps", interval);
            int allTimeCount = getTotalCount(stmt, "spatial_heatmaps");

            System.out.println("============================================");
            System.out.println("  TABLE COUNTS");
            System.out.println("============================================");
            System.out.println("");
            System.out.println(String.format("%-25s %8s %8s %6s %s", "TABLE", "LAST_N", "TOTAL", "REQ", "STATUS"));
            System.out.println("-".repeat(70));

            boolean tablePass = totalCount >= REQUIRED_TOTAL;
            String status = tablePass ? "PASS (>=" + REQUIRED_TOTAL + ")" : "FAIL (<" + REQUIRED_TOTAL + ")";
            System.out.println(String.format("%-25s %8d %8d %6d %s",
                "spatial_heatmaps", totalCount, allTimeCount, REQUIRED_TOTAL, status));
            System.out.println("");

            // Heatmap type breakdown
            System.out.println("============================================");
            System.out.println("  HEATMAP TYPE BREAKDOWN");
            System.out.println("============================================");
            System.out.println("");
            showTypeBreakdown(stmt, interval);
            System.out.println("");

            // Sample rows
            System.out.println("============================================");
            System.out.println("  SAMPLE ROWS (LIMIT 10)");
            System.out.println("============================================");
            System.out.println("");
            showSample(stmt, "spatial_heatmaps",
                "SELECT id, ts, heatmap_type, room, x, y, z, count FROM spatial_heatmaps ORDER BY ts DESC LIMIT 10");

            // ============================================
            // INVARIANT CHECKS
            // ============================================
            System.out.println("============================================");
            System.out.println("  INVARIANT CHECKS");
            System.out.println("============================================");
            System.out.println("");

            // Check 1: Heatmap type whitelist
            boolean typePass = checkTypeWhitelist(stmt, interval);
            System.out.println("heatmap_type whitelist: " + (typePass ? "PASS" : "FAIL (invalid types found)"));

            // Check 2: Movement rate limit
            // NOTE: With aggregated flush strategy, all buckets are written at flush time,
            // so we can't detect rapid-fire at DB level. The throttle is enforced in HeatmapService.
            // Instead, we check that no bucket has count > max_reasonable (e.g., 100 per bucket)
            int highCountBuckets = getHighCountBuckets(stmt, interval, 100);
            boolean movementPass = highCountBuckets == 0;
            System.out.println("high-count buckets (>100): " + highCountBuckets);
            System.out.println("  status: " + (movementPass ? "PASS" : "FAIL (potential spam)"));

            // Check 3: Coordinate sanity (y range)
            int invalidY = getInvalidYCoords(stmt, interval);
            boolean coordPass = invalidY == 0;
            System.out.println("invalid Y coordinates: " + invalidY);
            System.out.println("  status: " + (coordPass ? "PASS" : "FAIL (y out of range -64..320)"));

            // Check 4: No NaN coordinates
            int nanCoords = getNaNCoords(stmt, interval);
            boolean nanPass = nanCoords == 0;
            System.out.println("NaN coordinates: " + nanCoords);
            System.out.println("  status: " + (nanPass ? "PASS" : "FAIL"));
            System.out.println("");

            // Final verdict
            boolean allInvariantsPass = typePass && movementPass && coordPass && nanPass;
            boolean finalPass = tablePass && allInvariantsPass;

            System.out.println("============================================");
            System.out.println("  FINAL VERDICT");
            System.out.println("============================================");
            System.out.println("");

            if (finalPass) {
                System.out.println("=== P2-A HEATMAPS GATE: PASS ===");
                System.out.println("");
                System.out.println("Heatmap data present in DuckDB.");
                System.out.println("All invariants passed.");
                System.exit(0);
            } else {
                System.out.println("=== P2-A HEATMAPS GATE: FAIL ===");
                System.out.println("");
                if (!tablePass) System.out.println("- Missing data: need >= " + REQUIRED_TOTAL + " rows, got " + totalCount);
                if (!typePass) System.out.println("- Invalid heatmap types detected");
                if (!movementPass) System.out.println("- Movement throttle broken (rapid-fire detected)");
                if (!coordPass) System.out.println("- Invalid Y coordinates (out of range)");
                if (!nanPass) System.out.println("- NaN coordinates detected");
                System.out.println("");
                System.out.println("See diagnostics above.");
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

    static void showTypeBreakdown(Statement stmt, String interval) throws SQLException {
        String sql = "SELECT heatmap_type, COUNT(*) as cnt, SUM(count) as total_events " +
                     "FROM spatial_heatmaps WHERE ts >= " + interval +
                     " GROUP BY heatmap_type ORDER BY cnt DESC";
        ResultSet rs = stmt.executeQuery(sql);
        System.out.println(String.format("%-25s %8s %12s", "HEATMAP_TYPE", "BUCKETS", "TOTAL_EVENTS"));
        System.out.println("-".repeat(50));
        int rows = 0;
        while (rs.next()) {
            System.out.println(String.format("%-25s %8d %12d",
                rs.getString(1), rs.getInt(2), rs.getInt(3)));
            rows++;
        }
        if (rows == 0) System.out.println("(no heatmap data in time window)");
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
                if (val != null && val.length() > 35) val = val.substring(0, 32) + "...";
                row.append(val != null ? val : "NULL");
            }
            System.out.println(row);
            rows++;
        }
        if (rows == 0) System.out.println("(no data)");
        System.out.println("");
    }

    static boolean checkTypeWhitelist(Statement stmt, String interval) throws SQLException {
        String sql = "SELECT DISTINCT heatmap_type FROM spatial_heatmaps WHERE ts >= " + interval +
                     " AND heatmap_type NOT IN ('stuck', 'aggro_drop', 'kiting', 'death', 'movement', " +
                     "'camping', 'choke_point', 'invisible_collision', 'parkour_fall')";
        ResultSet rs = stmt.executeQuery(sql);
        return !rs.next(); // Pass if no invalid types found
    }

    static int getHighCountBuckets(Statement stmt, String interval, int threshold) throws SQLException {
        // Check for buckets with abnormally high count (potential spam/throttle failure)
        String sql = "SELECT COUNT(*) FROM spatial_heatmaps WHERE ts >= " + interval +
                     " AND count > " + threshold;
        ResultSet rs = stmt.executeQuery(sql);
        rs.next();
        return rs.getInt(1);
    }

    static int getInvalidYCoords(Statement stmt, String interval) throws SQLException {
        String sql = "SELECT COUNT(*) FROM spatial_heatmaps WHERE ts >= " + interval +
                     " AND (y < -64 OR y > 320)";
        ResultSet rs = stmt.executeQuery(sql);
        rs.next();
        return rs.getInt(1);
    }

    static int getNaNCoords(Statement stmt, String interval) throws SQLException {
        String sql = "SELECT COUNT(*) FROM spatial_heatmaps WHERE ts >= " + interval +
                     " AND (x != x OR y != y OR z != z)"; // NaN check: NaN != NaN
        ResultSet rs = stmt.executeQuery(sql);
        rs.next();
        return rs.getInt(1);
    }
}
JAVAEOF

# Compile and run
cd "$TEMP_DIR"
if ! javac -cp "$DUCKDB_JAR" P2HeatmapsGate.java; then
    echo "ERROR: Java compilation failed"
    exit 2
fi
java -cp ".:$DUCKDB_JAR" P2HeatmapsGate "$DB_PATH" "$MINUTES"
EXIT_CODE=$?

# Cleanup
rm -rf "$TEMP_DIR"

echo ""
echo "============================================"
echo "Report saved to: $REPORT_PATH"
echo "============================================"

exit $EXIT_CODE
