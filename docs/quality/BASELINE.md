# Baseline

## Commands
- ./gradlew build (FAILED)
- ./gradlew test (FAILED)

## Test Failures (4)
- Radial Menu Macro-Category System: initializationError
  - java.nio.file.NoSuchFileException: src/main/java/com/devmod/ui/radial/RadialMenuScreen.java
- L0-08: Directory Structure
  - Package should exist: ui (src/main/java/com/devmod/ui missing)
- L0-04: Critical Source Files
  - Missing: ui/editor/core/UIConstants.java
- L0-05: Compiled Classes
  - Missing compiled class: ui/editor/core/UIConstants.class

## Recurring Warnings
- [ArmorComponents] Using fallback armor_stats component (test-mode only)
- StatusConsoleListener: Advanced terminal features are not available in this environment

## Problem Areas (Initial)
- Client UI package structure appears out-of-sync with smoke tests (missing ui/ and UIConstants).
- Radial menu source path mismatch (RadialMenuScreen expected under ui/radial).
- L0 smoke tests failing indicates boot-time structural drift vs. tests.
