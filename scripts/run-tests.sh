#!/bin/bash
# DevMod Automated Test Runner
# Usage: ./scripts/run-tests.sh [--verbose] [--no-compile]
#
# Options:
#   --verbose    Show detailed test output
#   --no-compile Skip compilation (faster if already compiled)
#
# Exit codes:
#   0 - All tests passed
#   1 - Tests failed
#   2 - Compilation failed

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
LOG_FILE="${PROJECT_DIR}/run/logs/gametest.log"
TIMEOUT=300
GRADLE_FLAGS=(--no-build-cache)

# Parse arguments
VERBOSE=false
SKIP_COMPILE=false

for arg in "$@"; do
    case $arg in
        --verbose)
            VERBOSE=true
            ;;
        --no-compile)
            SKIP_COMPILE=true
            ;;
        --help)
            echo "DevMod Automated Test Runner"
            echo ""
            echo "Usage: $0 [OPTIONS]"
            echo ""
            echo "Options:"
            echo "  --verbose      Show detailed test output"
            echo "  --no-compile   Skip compilation (faster if already compiled)"
            echo "  --help         Show this help message"
            exit 0
            ;;
    esac
done

cd "$PROJECT_DIR"

echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}   DevMod GameTest Runner${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""

# Step 1: Compile (unless skipped)
if [ "$SKIP_COMPILE" = false ]; then
    echo -e "${YELLOW}[1/3] Compiling Java...${NC}"
    if ./gradlew "${GRADLE_FLAGS[@]}" compileJava --quiet 2>&1; then
        echo -e "${GREEN}      Compilation successful${NC}"
    else
        echo -e "${RED}      Compilation FAILED${NC}"
        exit 2
    fi
else
    echo -e "${YELLOW}[1/3] Skipping compilation (--no-compile)${NC}"
fi

echo ""

# Step 2: Run GameTests
echo -e "${YELLOW}[2/3] Running GameTests...${NC}"
echo ""

# Run tests and capture output
TEST_OUTPUT=$(./gradlew "${GRADLE_FLAGS[@]}" runGameTestServer 2>&1)
EXIT_CODE=$?

# Extract key information
TOTAL_TESTS=$(echo "$TEST_OUTPUT" | grep -oE "[0-9]+ GAME TESTS COMPLETE" | grep -oE "[0-9]+" || echo "0")
PASSED_MSG=$(echo "$TEST_OUTPUT" | grep -E "All [0-9]+ required tests passed" || echo "")
TIME_TAKEN=$(echo "$TEST_OUTPUT" | grep -oE "COMPLETE IN [0-9]+\.[0-9]+ ms" | grep -oE "[0-9]+\.[0-9]+" || echo "N/A")

# Check for failures
FAILED_TESTS=$(echo "$TEST_OUTPUT" | grep -E "FAILED!" || echo "")

if [ "$VERBOSE" = true ]; then
    echo "$TEST_OUTPUT" | grep -E "(Running test batch|GameTest|PASSED|FAILED|tests passed|Setting up|Cleaning up)"
    echo ""
fi

# Step 3: Report results
echo -e "${YELLOW}[3/3] Test Results${NC}"
echo ""

if [ -n "$PASSED_MSG" ] && [ -z "$FAILED_TESTS" ]; then
    echo -e "${GREEN}========================================${NC}"
    echo -e "${GREEN}   ALL ${TOTAL_TESTS} TESTS PASSED${NC}"
    echo -e "${GREEN}   Time: ${TIME_TAKEN}ms${NC}"
    echo -e "${GREEN}========================================${NC}"
    echo ""

    # Show batch breakdown
    echo "Batches executed:"
    echo "$TEST_OUTPUT" | grep "Running test batch" | while read line; do
        BATCH=$(echo "$line" | grep -oE "'[^']+'" | head -1)
        COUNT=$(echo "$line" | grep -oE "\([0-9]+ tests\)" || echo "(? tests)")
        echo -e "  ${GREEN}✓${NC} $BATCH $COUNT"
    done

    echo ""
    exit 0
else
    echo -e "${RED}========================================${NC}"
    echo -e "${RED}   TESTS FAILED${NC}"
    echo -e "${RED}========================================${NC}"
    echo ""

    if [ -n "$FAILED_TESTS" ]; then
        echo -e "${RED}Failed tests:${NC}"
        echo "$FAILED_TESTS" | while read line; do
            echo -e "  ${RED}✗${NC} $line"
        done
    fi

    echo ""
    echo "Full output saved to: $LOG_FILE"
    echo "$TEST_OUTPUT" > "$LOG_FILE"

    exit 1
fi
