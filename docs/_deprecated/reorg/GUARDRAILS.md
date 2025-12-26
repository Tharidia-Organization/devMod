# Naming Convention Guardrails

## Overview

This document describes the automated checks that prevent naming convention regressions.

## Script Location

```
tools/check-naming.sh
```

## Usage

```bash
# From project root
./tools/check-naming.sh
```

**Exit codes:**
- `0` - All checks passed (or passed with warnings)
- `1` - One or more checks failed

## Checks Performed

| # | Check | Description | Severity |
|---|-------|-------------|----------|
| 1 | No `com.frenkvs` in Java | Searches for legacy namespace | ERROR |
| 2 | No `transport` package | Legacy package name | ERROR |
| 3 | Root package file count | Max 5 files in `com.devmod/` | WARNING |
| 4 | No `TransportHandler` classes | Legacy class naming | ERROR |
| 5 | Correct `mod_id` | Must be `devmod` in gradle.properties | ERROR |
| 6 | No `DebugTransportHandler` | Specific legacy class check | ERROR |

## CI Integration

Add to your CI workflow:

```yaml
# GitHub Actions example
- name: Check naming conventions
  run: ./tools/check-naming.sh
```

```groovy
// Gradle task example
task checkNaming(type: Exec) {
    commandLine 'bash', './tools/check-naming.sh'
}

check.dependsOn checkNaming
```

## Adding New Checks

To add a new check to `tools/check-naming.sh`:

1. Add a numbered check block following the pattern:
```bash
# Check N: Description
echo -n "Checking for X... "
if [condition]; then
    echo -e "${GREEN}PASS${NC}"
else
    echo -e "${RED}FAIL${NC}"
    echo "  Error details..."
    ERRORS=$((ERRORS + 1))
fi
```

2. Update this documentation

## Current Compliance Status

Run the script to verify current status:

```
================================================
DevMod Naming Convention Check
================================================

Checking for com.frenkvs in Java sources... PASS
Checking for legacy 'transport' package... PASS
Checking root package file count... PASS (3 files)
Checking for legacy TransportHandler class names... PASS
Checking gradle.properties mod_id... PASS
Checking for DebugTransportHandler... PASS

================================================
Summary
================================================
Errors:   0
Warnings: 0

ALL CHECKS PASSED
```

## Related Documentation

- [RENAME_MAP.md](RENAME_MAP.md) - Complete rename history
- [ARCHITECTURE.md](../ARCHITECTURE.md) - Package structure reference
