#!/bin/bash
#
# Client Boundary Regression Guard
#
# This script checks that server-safe code doesn't import client-only classes.
# Run this as part of CI to prevent regressions.
#
# Usage: ./scripts/check-client-boundary.sh

set -e

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
SRC_MAIN="$PROJECT_ROOT/src/main/java"

# Directories that MUST be server-safe (no client-only imports)
SERVER_SAFE_DIRS=(
    "com/devmod"  # Root package - but will exclude client/
    "com/devmod/arena"     # Arena system - server logic
)

# Directories that are ALLOWED to have client imports
CLIENT_ALLOWED_DIRS=(
    "com/devmod/client"
    "com/devmod/mixin"
)

# Client-only import patterns that should NOT appear in server-safe code
CLIENT_PATTERNS=(
    "import net.minecraft.client."
    "import com.mojang.blaze3d."
    "import net.neoforged.neoforge.client."
    "import org.lwjgl."
)

# Exceptions - files in server-safe dirs that ARE allowed client imports
# (because they're marked @OnlyIn(Dist.CLIENT) or have proper guards)
EXCEPTION_FILES=(
    "DevModClient.java"
    "ClientModEvents.java"
    "EditorClientConfig.java"
)

ERRORS_FOUND=0
WARNINGS=0

echo -e "${YELLOW}Client Boundary Regression Guard${NC}"
echo "=================================="
echo ""

# Function to check if file is an exception
is_exception() {
    local filename=$(basename "$1")
    for exception in "${EXCEPTION_FILES[@]}"; do
        if [[ "$filename" == "$exception" ]]; then
            return 0
        fi
    done
    return 1
}

# Function to check if path is in client-allowed directory
is_client_allowed() {
    local filepath="$1"
    for allowed in "${CLIENT_ALLOWED_DIRS[@]}"; do
        if [[ "$filepath" == *"$allowed"* ]]; then
            return 0
        fi
    done
    return 1
}

# Check for client imports in DevMod.java (the main entry point)
echo "Checking DevMod.java for client-only imports..."
DEVMOD_FILE="$SRC_MAIN/com/devmod/DevMod.java"
if [[ -f "$DEVMOD_FILE" ]]; then
    for pattern in "${CLIENT_PATTERNS[@]}"; do
        if grep -q "$pattern" "$DEVMOD_FILE"; then
            echo -e "${RED}ERROR: DevMod.java contains client-only import: $pattern${NC}"
            ERRORS_FOUND=$((ERRORS_FOUND + 1))
        fi
    done

    # Specifically check for KeyMapping/KeyInputHandler reference
    if grep -q "import.*KeyMapping" "$DEVMOD_FILE" || grep -q "KeyInputHandler" "$DEVMOD_FILE"; then
        echo -e "${RED}ERROR: DevMod.java references KeyMapping or KeyInputHandler${NC}"
        ERRORS_FOUND=$((ERRORS_FOUND + 1))
    fi
fi

# Check that KeyInputHandler is in client package
echo "Checking KeyInputHandler location..."
OLD_LOCATION="$SRC_MAIN/com/devmod/KeyInputHandler.java"
NEW_LOCATION="$SRC_MAIN/com/devmod/client/input/KeyInputHandler.java"

if [[ -f "$OLD_LOCATION" ]]; then
    echo -e "${RED}ERROR: KeyInputHandler.java is in wrong location (should be in client/input/)${NC}"
    ERRORS_FOUND=$((ERRORS_FOUND + 1))
fi

if [[ ! -f "$NEW_LOCATION" ]]; then
    echo -e "${RED}ERROR: KeyInputHandler.java not found in client/input/${NC}"
    ERRORS_FOUND=$((ERRORS_FOUND + 1))
else
    # Check it has @OnlyIn annotation
    if ! grep -q "@OnlyIn(Dist.CLIENT)" "$NEW_LOCATION"; then
        echo -e "${YELLOW}WARNING: KeyInputHandler.java missing @OnlyIn(Dist.CLIENT) annotation${NC}"
        WARNINGS=$((WARNINGS + 1))
    fi
fi

# Check ClientUiBridge exists
echo "Checking ClientUiBridge pattern..."
BRIDGE_FILE="$SRC_MAIN/com/devmod/bridge/ClientUiBridge.java"
if [[ ! -f "$BRIDGE_FILE" ]]; then
    echo -e "${RED}ERROR: ClientUiBridge.java not found${NC}"
    ERRORS_FOUND=$((ERRORS_FOUND + 1))
else
    # Check it has no client imports
    for pattern in "${CLIENT_PATTERNS[@]}"; do
        if grep -q "$pattern" "$BRIDGE_FILE"; then
            echo -e "${RED}ERROR: ClientUiBridge.java contains client-only import: $pattern${NC}"
            ERRORS_FOUND=$((ERRORS_FOUND + 1))
        fi
    done
fi

# Summary
echo ""
echo "=================================="
if [[ $ERRORS_FOUND -gt 0 ]]; then
    echo -e "${RED}FAILED: $ERRORS_FOUND error(s) found${NC}"
    if [[ $WARNINGS -gt 0 ]]; then
        echo -e "${YELLOW}Also found $WARNINGS warning(s)${NC}"
    fi
    exit 1
else
    echo -e "${GREEN}PASSED: No client boundary violations found${NC}"
    if [[ $WARNINGS -gt 0 ]]; then
        echo -e "${YELLOW}Found $WARNINGS warning(s)${NC}"
    fi
    exit 0
fi
