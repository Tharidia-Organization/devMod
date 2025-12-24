#!/bin/bash
# check-naming.sh - Guardrails for naming conventions
# Fails CI if legacy or non-standard naming is detected

set -e

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

ERRORS=0
WARNINGS=0

echo "================================================"
echo "DevMod Naming Convention Check"
echo "================================================"
echo ""

# Check 1: No com.frenkvs in Java sources
echo -n "Checking for com.frenkvs in Java sources... "
FRENKVS_COUNT=$(grep -r "com\.frenkvs" src/ --include="*.java" 2>/dev/null | wc -l | tr -d ' ')
if [ "$FRENKVS_COUNT" -gt 0 ]; then
    echo -e "${RED}FAIL${NC}"
    echo "  Found $FRENKVS_COUNT references to com.frenkvs in Java sources"
    grep -r "com\.frenkvs" src/ --include="*.java" 2>/dev/null | head -10
    ERRORS=$((ERRORS + 1))
else
    echo -e "${GREEN}PASS${NC}"
fi

# Check 2: No transport package (legacy naming)
echo -n "Checking for legacy 'transport' package... "
if [ -d "src/main/java/com/devmod/transport" ]; then
    echo -e "${RED}FAIL${NC}"
    echo "  Legacy package 'transport' still exists. Should be 'network'."
    ERRORS=$((ERRORS + 1))
else
    echo -e "${GREEN}PASS${NC}"
fi

# Check 3: Root package should have minimal files (max 5)
echo -n "Checking root package file count... "
ROOT_FILES=$(find src/main/java/com/devmod -maxdepth 1 -name "*.java" | wc -l | tr -d ' ')
ALLOWED_ROOT=5
if [ "$ROOT_FILES" -gt "$ALLOWED_ROOT" ]; then
    echo -e "${YELLOW}WARN${NC}"
    echo "  Root package has $ROOT_FILES files (max allowed: $ALLOWED_ROOT)"
    echo "  Files:"
    find src/main/java/com/devmod -maxdepth 1 -name "*.java" -exec basename {} \;
    WARNINGS=$((WARNINGS + 1))
else
    echo -e "${GREEN}PASS${NC} ($ROOT_FILES files)"
fi

# Check 4: No TransportHandler class names
echo -n "Checking for legacy TransportHandler class names... "
TRANSPORT_HANDLERS=$(grep -r "class.*TransportHandler" src/ --include="*.java" 2>/dev/null | wc -l | tr -d ' ')
if [ "$TRANSPORT_HANDLERS" -gt 0 ]; then
    echo -e "${RED}FAIL${NC}"
    echo "  Found $TRANSPORT_HANDLERS classes with TransportHandler in name"
    grep -r "class.*TransportHandler" src/ --include="*.java" 2>/dev/null | head -5
    ERRORS=$((ERRORS + 1))
else
    echo -e "${GREEN}PASS${NC}"
fi

# Check 5: gradle.properties has correct mod id
echo -n "Checking gradle.properties mod_id... "
if grep -q 'mod_id=devmod' gradle.properties 2>/dev/null; then
    echo -e "${GREEN}PASS${NC}"
else
    echo -e "${RED}FAIL${NC}"
    echo "  gradle.properties should have mod_id=devmod"
    ERRORS=$((ERRORS + 1))
fi

# Check 6: No DebugTransportHandler
echo -n "Checking for DebugTransportHandler... "
DEBUG_TRANSPORT_COUNT=$(grep -r "DebugTransportHandler" src/ --include="*.java" 2>/dev/null | wc -l | tr -d ' ')
if [ "$DEBUG_TRANSPORT_COUNT" -gt 0 ]; then
    echo -e "${RED}FAIL${NC}"
    echo "  Found $DEBUG_TRANSPORT_COUNT references to DebugTransportHandler (should be DebugNetworkHandler)"
    grep -r "DebugTransportHandler" src/ --include="*.java" 2>/dev/null | head -5
    ERRORS=$((ERRORS + 1))
else
    echo -e "${GREEN}PASS${NC}"
fi

echo ""
echo "================================================"
echo "Summary"
echo "================================================"
echo -e "Errors:   ${RED}$ERRORS${NC}"
echo -e "Warnings: ${YELLOW}$WARNINGS${NC}"

if [ "$ERRORS" -gt 0 ]; then
    echo ""
    echo -e "${RED}FAILED - $ERRORS error(s) found${NC}"
    exit 1
fi

if [ "$WARNINGS" -gt 0 ]; then
    echo ""
    echo -e "${YELLOW}PASSED with warnings${NC}"
    exit 0
fi

echo ""
echo -e "${GREEN}ALL CHECKS PASSED${NC}"
exit 0
