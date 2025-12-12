#!/bin/bash
#
# P1 Economy Gate Verification Script
# ====================================
# Verifies that economy events are correctly written to DuckDB.
#
# Prerequisites:
# - Run client, enter world, perform test actions:
#   1. /summon zombie ~ ~ ~ (kill it for mob_kills + mob_drops)
#   2. Pick up dropped items (item_pickups)
#   3. Eat food or use potion (item_usage - consumed)
#   4. Press Q to toss item (item_usage - discarded)
# - Exit game
#
# Usage:
#   ./scripts/p1_economy_gate.sh [--minutes N]
#
# Exit codes:
#   0 = PASS (all 4 economy tables have data)
#   1 = FAIL (one or more tables empty)
#   2 = ERROR (script/setup error)
#

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
DB_PATH="$PROJECT_DIR/run/telemetry/devmod_telemetry.duckdb"
MINUTES=10

# Parse arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        --minutes)
            MINUTES="$2"
            shift 2
            ;;
        --help)
            echo "Usage: $0 [--minutes N]"
            exit 0
            ;;
        *)
            shift
            ;;
    esac
done

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m'

echo -e "${CYAN}============================================${NC}"
echo -e "${CYAN}  P1 Economy Gate Verification${NC}"
echo -e "${CYAN}============================================${NC}"
echo ""
echo "Database: $DB_PATH"
echo "Time window: last $MINUTES minutes"
echo ""

# Check database exists
if [[ ! -f "$DB_PATH" ]]; then
    echo -e "${RED}ERROR: Database not found at $DB_PATH${NC}"
    exit 2
fi

# Find DuckDB jar
DUCKDB_JAR=$(find ~/.gradle/caches/modules-2/files-2.1/org.duckdb/duckdb_jdbc -name "duckdb_jdbc-*.jar" 2>/dev/null | head -1)
if [[ -z "$DUCKDB_JAR" ]]; then
    echo -e "${RED}ERROR: DuckDB JDBC jar not found in Gradle cache${NC}"
    exit 2
fi

echo "DuckDB JAR: $DUCKDB_JAR"
echo ""

# Create temp Java file for querying
TEMP_DIR=$(mktemp -d)
JAVA_FILE="$TEMP_DIR/P1EconomyGate.java"

cat > "$JAVA_FILE" << 'JAVAEOF'
import java.sql.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

public class P1EconomyGate {
    public static void main(String[] args) throws Exception {
        String dbPath = args[0];
        int minutes = Integer.parseInt(args[1]);

        Class.forName("org.duckdb.DuckDBDriver");
        try (Connection conn = DriverManager.getConnection("jdbc:duckdb:" + dbPath)) {
            Statement stmt = conn.createStatement();

            String interval = "NOW() - INTERVAL '" + minutes + " MINUTES'";

            // Query counts
            int mobKills = getCount(stmt, "economy_mob_kills", interval);
            int mobDrops = getCount(stmt, "economy_mob_drops", interval);
            int itemPickups = getCount(stmt, "economy_item_pickups", interval);
            int itemUsage = getCount(stmt, "economy_item_usage", interval);

            System.out.println("=== ECONOMY TABLE COUNTS (last " + minutes + " min) ===");
            System.out.println("economy_mob_kills:    " + mobKills + (mobKills > 0 ? " ✓" : " ✗"));
            System.out.println("economy_mob_drops:    " + mobDrops + (mobDrops > 0 ? " ✓" : " ✗"));
            System.out.println("economy_item_pickups: " + itemPickups + (itemPickups > 0 ? " ✓" : " ✗"));
            System.out.println("economy_item_usage:   " + itemUsage + (itemUsage > 0 ? " ✓" : " ✗"));
            System.out.println("");

            // Show sample data
            System.out.println("=== SAMPLE DATA (LIMIT 5) ===");
            System.out.println("");

            System.out.println("-- economy_mob_kills --");
            showSample(stmt, "SELECT ts, mob_type, total_kills, had_loot FROM economy_mob_kills ORDER BY ts DESC LIMIT 5");

            System.out.println("-- economy_mob_drops --");
            showSample(stmt, "SELECT ts, mob_type, item_id, item_count FROM economy_mob_drops ORDER BY ts DESC LIMIT 5");

            System.out.println("-- economy_item_pickups --");
            showSample(stmt, "SELECT ts, player_name, item_id, item_count FROM economy_item_pickups ORDER BY ts DESC LIMIT 5");

            System.out.println("-- economy_item_usage --");
            showSample(stmt, "SELECT ts, player_name, event_type, item_id, use_type FROM economy_item_usage ORDER BY ts DESC LIMIT 5");

            // Final verdict
            System.out.println("");
            boolean pass = mobKills > 0 && mobDrops > 0 && itemPickups > 0 && itemUsage > 0;
            if (pass) {
                System.out.println("=== P1 ECONOMY GATE: PASS ===");
                System.exit(0);
            } else {
                System.out.println("=== P1 ECONOMY GATE: FAIL ===");
                System.out.println("Missing data in one or more tables.");
                System.out.println("Required actions:");
                if (mobKills == 0) System.out.println("  - Kill a mob (summon zombie, then kill it)");
                if (mobDrops == 0) System.out.println("  - Kill a mob that drops items");
                if (itemPickups == 0) System.out.println("  - Pick up an item from ground");
                if (itemUsage == 0) System.out.println("  - Eat food/drink potion OR toss item (Q key)");
                System.exit(1);
            }
        }
    }

    static int getCount(Statement stmt, String table, String interval) throws SQLException {
        ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM " + table + " WHERE ts >= " + interval);
        rs.next();
        return rs.getInt(1);
    }

    static void showSample(Statement stmt, String sql) throws SQLException {
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
                if (val != null && val.length() > 40) val = val.substring(0, 37) + "...";
                row.append(val);
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
javac -cp "$DUCKDB_JAR" P1EconomyGate.java 2>/dev/null
java -cp ".:$DUCKDB_JAR" P1EconomyGate "$DB_PATH" "$MINUTES"
EXIT_CODE=$?

# Cleanup
rm -rf "$TEMP_DIR"

exit $EXIT_CODE
