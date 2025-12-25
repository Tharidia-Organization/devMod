# Baseline

## Commands
- ./gradlew build (FAILED: test result XML write errors)
- ./gradlew test (FAILED: test result XML write errors)

## Test Failures
- Gradle test reporting failed to write XML results under `build/test-results/test` for multiple test classes.

## Recurring Warnings
- [ArmorComponents] Using fallback armor_stats component (test-mode only)
- StatusConsoleListener: Advanced terminal features are not available in this environment

## Problem Areas (Initial)
- Test result XML write failures block reliable test reporting; investigate filesystem permissions or concurrent runner issues under `build/test-results/test`.
