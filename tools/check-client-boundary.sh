#!/bin/bash
# ============================================================================
# Client/Server Boundary Checker
# ============================================================================
# This script verifies that client code is properly isolated from server code.
# Run this as part of CI to prevent regressions.
#
# Usage: ./tools/check-client-boundary.sh
# Exit codes: 0 = pass, 1 = violations found
# ============================================================================

set -e

SRC_DIR="src/main/java/com/devmod"
CLIENT_PKG="$SRC_DIR/client"
ERRORS=0

echo "========================================"
echo " Client/Server Boundary Verification"
echo "========================================"
echo ""

# ============================================================================
# Check 1: Client imports in non-client packages
# ============================================================================
echo "[Check 1] Searching for client imports outside *.client.* packages..."

CLIENT_IMPORTS=$(grep -r "import net\.minecraft\.client\." "$SRC_DIR" --include="*.java" 2>/dev/null | grep -v "/client/" || true)

if [ -n "$CLIENT_IMPORTS" ]; then
    echo ""
    echo "ERROR: Found client imports in non-client packages:"
    echo "$CLIENT_IMPORTS" | head -20
    echo ""
    ERRORS=$((ERRORS + 1))
else
    echo "   PASS: No client imports found outside client packages"
fi

# ============================================================================
# Check 2: Minecraft.getInstance() outside client packages
# ============================================================================
echo ""
echo "[Check 2] Searching for Minecraft.getInstance() outside client packages..."

MC_INSTANCE=$(grep -r "Minecraft\.getInstance()" "$SRC_DIR" --include="*.java" 2>/dev/null | grep -v "/client/" || true)

if [ -n "$MC_INSTANCE" ]; then
    echo ""
    echo "ERROR: Found Minecraft.getInstance() in non-client packages:"
    echo "$MC_INSTANCE" | head -20
    echo ""
    ERRORS=$((ERRORS + 1))
else
    echo "   PASS: No Minecraft.getInstance() found outside client packages"
fi

# ============================================================================
# Check 3: Screen imports outside client packages
# ============================================================================
echo ""
echo "[Check 3] Searching for Screen imports outside client packages..."

SCREEN_IMPORTS=$(grep -r "import net\.minecraft\.client\.gui\.screens\.Screen" "$SRC_DIR" --include="*.java" 2>/dev/null | grep -v "/client/" || true)
SCREEN_EXTENDS=$(grep -rE "extends\s+Screen\b" "$SRC_DIR" --include="*.java" 2>/dev/null | grep -v "/client/" || true)

if [ -n "$SCREEN_IMPORTS" ] || [ -n "$SCREEN_EXTENDS" ]; then
    echo ""
    echo "ERROR: Found Screen usage in non-client packages:"
    [ -n "$SCREEN_IMPORTS" ] && echo "$SCREEN_IMPORTS" | head -10
    [ -n "$SCREEN_EXTENDS" ] && echo "$SCREEN_EXTENDS" | head -10
    echo ""
    ERRORS=$((ERRORS + 1))
else
    echo "   PASS: No Screen usage found outside client packages"
fi

# ============================================================================
# Check 4: KeyMapping/InputConstants outside client packages
# ============================================================================
echo ""
echo "[Check 4] Searching for KeyMapping/InputConstants outside client packages..."

KEY_IMPORTS=$(grep -rE "import.*(KeyMapping|InputConstants)" "$SRC_DIR" --include="*.java" 2>/dev/null | grep -v "/client/" || true)

if [ -n "$KEY_IMPORTS" ]; then
    echo ""
    echo "ERROR: Found KeyMapping/InputConstants in non-client packages:"
    echo "$KEY_IMPORTS" | head -20
    echo ""
    ERRORS=$((ERRORS + 1))
else
    echo "   PASS: No KeyMapping/InputConstants found outside client packages"
fi

# ============================================================================
# Check 5: RenderSystem/GuiGraphics/PoseStack outside client packages
# ============================================================================
echo ""
echo "[Check 5] Searching for rendering imports outside client packages..."

RENDER_IMPORTS=$(grep -rE "import.*(RenderSystem|GuiGraphics|PoseStack)" "$SRC_DIR" --include="*.java" 2>/dev/null | grep -v "/client/" | grep -v "/mixin/" || true)

if [ -n "$RENDER_IMPORTS" ]; then
    echo ""
    echo "ERROR: Found rendering imports in non-client packages:"
    echo "$RENDER_IMPORTS" | head -20
    echo ""
    ERRORS=$((ERRORS + 1))
else
    echo "   PASS: No rendering imports found outside client packages"
fi

# ============================================================================
# Check 6: Missing @OnlyIn annotations in client packages
# ============================================================================
echo ""
echo "[Check 6] Checking for missing @OnlyIn annotations in client packages..."

if [ -d "$CLIENT_PKG" ]; then
    MISSING_ONLYIN=$(find "$CLIENT_PKG" -name "*.java" -type f ! -name "package-info.java" -exec grep -L "@OnlyIn(Dist.CLIENT)" {} \; 2>/dev/null || true)

    if [ -n "$MISSING_ONLYIN" ]; then
        COUNT=$(echo "$MISSING_ONLYIN" | wc -l | tr -d ' ')
        echo ""
        echo "ERROR: Found $COUNT files missing @OnlyIn(Dist.CLIENT) annotation:"
        echo "$MISSING_ONLYIN" | head -20
        echo ""
        ERRORS=$((ERRORS + 1))
    else
        echo "   PASS: All client classes have @OnlyIn annotation"
    fi
else
    echo "   SKIP: Client package not found at $CLIENT_PKG"
fi

# ============================================================================
# Summary
# ============================================================================
echo ""
echo "========================================"
echo " Summary"
echo "========================================"

if [ $ERRORS -eq 0 ]; then
    echo ""
    echo " ALL CHECKS PASSED"
    echo ""
    echo " The client/server boundary is properly maintained."
    echo ""
    exit 0
else
    echo ""
    echo " FAILED: $ERRORS check(s) failed"
    echo ""
    echo " Please fix the violations before committing."
    echo " See docs/CLIENT_BOUNDARY_AUDIT.md for guidance."
    echo ""
    exit 1
fi
