#!/bin/bash
# Architecture Guardrails Check
# Run this before pushing to verify architectural constraints

set -e

echo "=== DevMod Architecture Guardrails ==="
echo ""

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

FAILED=0

# Check 1: No frenkvs namespace
echo "1. Checking for com.frenkvs references..."
if grep -r "com\.frenkvs" src/ --include="*.java" 2>/dev/null; then
    echo -e "${RED}FAIL: Found com.frenkvs references${NC}"
    FAILED=1
else
    echo -e "${GREEN}PASS: No com.frenkvs references found${NC}"
fi
echo ""

# Check 2: Root package check (max 3 files)
echo "2. Checking root package size..."
count=$(find src/main/java/com/devmod -maxdepth 1 -name "*.java" 2>/dev/null | wc -l | xargs)
if [ "$count" -gt 3 ]; then
    echo -e "${RED}FAIL: Root package has $count files (max 3 allowed)${NC}"
    echo "Files in root:"
    find src/main/java/com/devmod -maxdepth 1 -name "*.java"
    FAILED=1
else
    echo -e "${GREEN}PASS: Root package has $count files (max 3)${NC}"
fi
echo ""

# Check 3: Duplicate class names (warning only)
echo "3. Checking for duplicate class names..."
duplicates=$(find src/main/java -name "*.java" -exec basename {} \; 2>/dev/null | sort | uniq -d)
if [ -n "$duplicates" ]; then
    echo -e "${YELLOW}WARNING: Duplicate class names found:${NC}"
    echo "$duplicates"
else
    echo -e "${GREEN}PASS: No duplicate class names${NC}"
fi
echo ""

# Check 4: Namespace consistency
echo "4. Verifying namespace consistency..."
bad_packages=$(grep -rh "^package " src/main/java --include="*.java" 2>/dev/null | grep -v "package com\.devmod" | sort -u || true)
if [ -n "$bad_packages" ]; then
    echo -e "${RED}FAIL: Found packages outside com.devmod namespace:${NC}"
    echo "$bad_packages"
    FAILED=1
else
    echo -e "${GREEN}PASS: All packages under com.devmod namespace${NC}"
fi
echo ""

# Summary
echo "=== Summary ==="
if [ $FAILED -eq 0 ]; then
    echo -e "${GREEN}All architecture checks passed!${NC}"
    exit 0
else
    echo -e "${RED}Some architecture checks failed!${NC}"
    exit 1
fi
