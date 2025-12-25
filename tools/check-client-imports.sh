#!/bin/bash
# check-client-imports.sh
# Guardrail script to detect client-only imports outside /client/ packages
# Part of the client/server separation enforcement

set -e

SRC_DIR="${1:-src/main/java/com/devmod}"
FAIL_COUNT=0

echo "=== Client Import Check ==="
echo "Scanning for net.minecraft.client imports outside /client/ packages..."
echo ""

# Find all Java files with client imports that are NOT in /client/ directories
BAD_FILES=$(grep -rl "import net\.minecraft\.client\." "$SRC_DIR" --include="*.java" 2>/dev/null | \
    grep -v "/client/" || true)

if [ -n "$BAD_FILES" ]; then
    echo "ERROR: Found client imports outside /client/ packages:"
    echo ""
    for file in $BAD_FILES; do
        echo "  - $file"
        FAIL_COUNT=$((FAIL_COUNT + 1))
    done
    echo ""
    echo "These files import net.minecraft.client.* classes but are not in a /client/ package."
    echo "This will cause crashes on dedicated servers!"
    echo ""
    echo "Fix options:"
    echo "  1. Move the file to a /client/ subpackage (e.g., com.devmod.foo -> com.devmod.client.foo)"
    echo "  2. Use DistExecutor pattern to safely call client code"
    echo "  3. Create a client delegate class for client-only operations"
    echo ""
    exit 1
else
    echo "OK: No client imports found outside /client/ packages"
fi

# Also check for Minecraft.getInstance() which is client-only
echo ""
echo "Scanning for Minecraft.getInstance() outside /client/ packages..."

BAD_MINECRAFT=$(grep -rl "Minecraft\.getInstance()" "$SRC_DIR" --include="*.java" 2>/dev/null | \
    grep -v "/client/" || true)

if [ -n "$BAD_MINECRAFT" ]; then
    echo "ERROR: Found Minecraft.getInstance() outside /client/ packages:"
    echo ""
    for file in $BAD_MINECRAFT; do
        echo "  - $file"
        FAIL_COUNT=$((FAIL_COUNT + 1))
    done
    echo ""
    echo "Minecraft.getInstance() is client-only and will crash on dedicated servers!"
    echo ""
    exit 1
else
    echo "OK: No Minecraft.getInstance() found outside /client/ packages"
fi

echo ""
echo "=== Check Complete ==="
echo "All files follow client/server separation conventions."
exit 0
