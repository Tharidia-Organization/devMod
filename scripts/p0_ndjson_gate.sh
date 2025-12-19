#!/bin/bash
#
# P0 NDJSON Gate Verification Script
# ===================================
# Verifies that NO NDJSON files are written when DuckDB is PRIMARY mode.
#
# Requirements:
# - DuckDB ENABLED=true (default)
# - NDJSON_FALLBACK=false (default)
#
# Usage:
#   ./scripts/p0_ndjson_gate.sh [--duration SECONDS] [--telemetry-dir PATH]
#
# Exit codes:
#   0 = PASS (no NDJSON modifications)
#   1 = FAIL (NDJSON files were modified)
#   2 = ERROR (script/setup error)
#

set -e

# Configuration
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
DEFAULT_TELEMETRY_DIR="$PROJECT_DIR/run/telemetry"
DEFAULT_DURATION=30

# Parse arguments
DURATION=$DEFAULT_DURATION
TELEMETRY_DIR=""

while [[ $# -gt 0 ]]; do
    case $1 in
        --duration)
            DURATION="$2"
            shift 2
            ;;
        --telemetry-dir)
            TELEMETRY_DIR="$2"
            shift 2
            ;;
        --help)
            echo "Usage: $0 [--duration SECONDS] [--telemetry-dir PATH]"
            echo ""
            echo "Options:"
            echo "  --duration      Test duration in seconds (default: 30)"
            echo "  --telemetry-dir Path to telemetry directory (default: run/telemetry)"
            exit 0
            ;;
        *)
            echo "Unknown option: $1"
            exit 2
            ;;
    esac
done

# Use default telemetry dir if not specified
if [[ -z "$TELEMETRY_DIR" ]]; then
    TELEMETRY_DIR="$DEFAULT_TELEMETRY_DIR"
fi

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

echo -e "${CYAN}============================================${NC}"
echo -e "${CYAN}  P0 NDJSON Gate Verification${NC}"
echo -e "${CYAN}============================================${NC}"
echo ""
echo -e "Project dir:    ${PROJECT_DIR}"
echo -e "Telemetry dir:  ${TELEMETRY_DIR}"
echo -e "Test duration:  ${DURATION}s"
echo ""

# Verify project structure
if [[ ! -f "$PROJECT_DIR/build.gradle" ]]; then
    echo -e "${RED}ERROR: Not a valid project directory (no build.gradle)${NC}"
    exit 2
fi

# Create telemetry dir if it doesn't exist
mkdir -p "$TELEMETRY_DIR"

# Temp files for snapshots
SNAPSHOT_BEFORE=$(mktemp)
SNAPSHOT_AFTER=$(mktemp)
DIFF_OUTPUT=$(mktemp)

cleanup() {
    rm -f "$SNAPSHOT_BEFORE" "$SNAPSHOT_AFTER" "$DIFF_OUTPUT"
}
trap cleanup EXIT

# Function to snapshot NDJSON files
snapshot_ndjson() {
    local output_file="$1"
    echo "# NDJSON Snapshot - $(date -Iseconds)" > "$output_file"

    if [[ -d "$TELEMETRY_DIR" ]]; then
        # Find all .ndjson files and record their size/mtime
        find "$TELEMETRY_DIR" -name "*.ndjson" -type f 2>/dev/null | sort | while read -r file; do
            if [[ -f "$file" ]]; then
                local size=$(stat -f%z "$file" 2>/dev/null || stat -c%s "$file" 2>/dev/null || echo "0")
                local lines=$(wc -l < "$file" 2>/dev/null | tr -d ' ' || echo "0")
                local mtime=$(stat -f%m "$file" 2>/dev/null || stat -c%Y "$file" 2>/dev/null || echo "0")
                local relpath="${file#$TELEMETRY_DIR/}"
                echo "$relpath|$size|$lines|$mtime" >> "$output_file"
            fi
        done
    fi
}

# Function to compare snapshots
compare_snapshots() {
    local before="$1"
    local after="$2"
    local diff_file="$3"

    # Create associative arrays from snapshots
    declare -A before_data
    declare -A after_data

    # Parse before snapshot
    while IFS='|' read -r file size lines mtime; do
        [[ "$file" == \#* ]] && continue  # Skip comments
        [[ -z "$file" ]] && continue
        before_data["$file"]="$size|$lines|$mtime"
    done < "$before"

    # Parse after snapshot
    while IFS='|' read -r file size lines mtime; do
        [[ "$file" == \#* ]] && continue
        [[ -z "$file" ]] && continue
        after_data["$file"]="$size|$lines|$mtime"
    done < "$after"

    local changes=0

    echo "# NDJSON Changes Report" > "$diff_file"
    echo "" >> "$diff_file"

    # Check for new or modified files
    for file in "${!after_data[@]}"; do
        local after_val="${after_data[$file]}"
        local before_val="${before_data[$file]:-}"

        if [[ -z "$before_val" ]]; then
            # New file
            IFS='|' read -r size lines mtime <<< "$after_val"
            echo "NEW: $file (size=$size, lines=$lines)" >> "$diff_file"
            ((changes++))
        elif [[ "$before_val" != "$after_val" ]]; then
            # Modified file
            IFS='|' read -r b_size b_lines b_mtime <<< "$before_val"
            IFS='|' read -r a_size a_lines a_mtime <<< "$after_val"
            local size_diff=$((a_size - b_size))
            local lines_diff=$((a_lines - b_lines))
            echo "MODIFIED: $file (size: $b_size -> $a_size [$size_diff], lines: $b_lines -> $a_lines [$lines_diff])" >> "$diff_file"
            ((changes++))
        fi
    done

    echo "$changes"
}

# Step 1: Take BEFORE snapshot
echo -e "${YELLOW}[1/4] Taking BEFORE snapshot...${NC}"
snapshot_ndjson "$SNAPSHOT_BEFORE"
before_count=$(grep -v '^#' "$SNAPSHOT_BEFORE" | grep -v '^$' | wc -l | tr -d ' ')
echo -e "      Found $before_count existing NDJSON files"
echo ""

# Step 2: Run the game test server
echo -e "${YELLOW}[2/4] Running gameTestServer for ${DURATION}s...${NC}"
echo -e "      (This will test DuckDB writes without NDJSON fallback)"
echo ""

cd "$PROJECT_DIR"

# Run gameTestServer in background with timeout
# Use JVM args to ensure DuckDB PRIMARY mode
timeout "${DURATION}s" ./gradlew runGameTestServer \
    -Ddevmod.duckdb.enabled=true \
    -Ddevmod.duckdb.ndjson_fallback=false \
    2>&1 | head -100 || true

# Give a moment for async writes to flush
sleep 2

echo ""
echo -e "${YELLOW}[3/4] Taking AFTER snapshot...${NC}"
snapshot_ndjson "$SNAPSHOT_AFTER"
after_count=$(grep -v '^#' "$SNAPSHOT_AFTER" | grep -v '^$' | wc -l | tr -d ' ')
echo -e "      Found $after_count NDJSON files after test"
echo ""

# Step 4: Compare and report
echo -e "${YELLOW}[4/4] Comparing snapshots...${NC}"
echo ""

# Simple diff comparison
changes=0

# Compare line by line (excluding timestamp comment)
diff_result=$(diff <(grep -v '^#' "$SNAPSHOT_BEFORE" | sort) <(grep -v '^#' "$SNAPSHOT_AFTER" | sort) 2>/dev/null || true)

if [[ -n "$diff_result" ]]; then
    echo -e "${RED}============================================${NC}"
    echo -e "${RED}  CHANGES DETECTED IN NDJSON FILES${NC}"
    echo -e "${RED}============================================${NC}"
    echo ""

    # Parse and display changes
    echo "Diff output:"
    echo "$diff_result" | head -50
    echo ""

    # Count modified files
    new_files=$(echo "$diff_result" | grep '^>' | wc -l | tr -d ' ')
    removed_files=$(echo "$diff_result" | grep '^<' | wc -l | tr -d ' ')

    echo -e "New/modified entries: $new_files"
    echo -e "Removed entries: $removed_files"
    echo ""

    # Show top 20 modified files
    echo -e "${YELLOW}Top 20 NDJSON files with changes:${NC}"
    echo ""

    # List files that are different
    for line in $(echo "$diff_result" | grep '^>' | head -20 | sed 's/^> //'); do
        file=$(echo "$line" | cut -d'|' -f1)
        size=$(echo "$line" | cut -d'|' -f2)
        lines=$(echo "$line" | cut -d'|' -f3)
        echo "  - $file (size=$size bytes, lines=$lines)"
    done

    echo ""
    echo -e "${RED}============================================${NC}"
    echo -e "${RED}  P0 VERIFICATION: FAIL${NC}"
    echo -e "${RED}============================================${NC}"
    echo ""
    echo -e "NDJSON files were written in DuckDB PRIMARY mode!"
    echo -e "This violates P0 requirement."
    echo ""
    echo -e "Possible causes:"
    echo -e "  1. TelemetryService.appendLine() guard not working"
    echo -e "  2. Direct filesystem writes bypassing TelemetryService"
    echo -e "  3. DuckDBTelemetryService.isEnabled() returning false"
    echo -e "  4. Test ran before DuckDB initialization completed"
    echo ""
    exit 1
else
    echo -e "${GREEN}============================================${NC}"
    echo -e "${GREEN}  P0 VERIFICATION: PASS${NC}"
    echo -e "${GREEN}============================================${NC}"
    echo ""
    echo -e "No NDJSON files were modified during the test."
    echo -e "DuckDB PRIMARY mode is working correctly."
    echo ""
    exit 0
fi
