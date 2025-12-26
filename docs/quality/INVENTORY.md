# Quality Inventory

**Date**: 2025-12-26
**Total Java Files**: 1159
**Total LOC**: ~355,000 (estimated)

## Summary Statistics

| Category | Count | Priority |
|----------|-------|----------|
| Wildcard imports | 108 (104 are `Assertions.*` in tests) | P1 |
| Compiler warnings | Not collected (build failed) | P1 |
| Methods >80 lines | 22+ | P2 |
| Files >600 LOC | 148 | P2 (report only) |
| TODO/FIXME | 2 | P2 |
| Minecraft.getInstance outside `com.devmod.client` | 0 (client-only packages) | ✅ |

---

## Inventory Table (Current Run)

| File | Issue Category | Severity | Proposed Fix Type |
|------|----------------|----------|-------------------|
| `src/main/java/com/devmod/telemetry/duckdb/packets/TelemetryPacketHandler.java` | Compile error: missing `dimension` local in `processPlayerSnapshot` | P0 | Add local `dimension` with fallback and reuse |
| `src/main/java/com/devmod/telemetry/duckdb/DuckDBBatchWriter.java` | Static init failure in tests (ExceptionInInitializerError at `TABLE_PRIORITY`) | P0 | Investigate initializer, add guard/test |
| `src/main/java/com/devmod/telemetry/duckdb/packets/TelemetryPacketHandler.java` | Unused import (`TelemetryLVC`) | P1 | Remove unused import, reorder |
| `build/test-results/test` | Test XML reports fail to write | P1 | Diagnose filesystem/permissions |
| `src/test/java/com/devmod/client/ui/radial/RadialMenuMacroCategoryTest.java` | Wildcard imports (`java.nio.file.*`, `java.util.*`, `java.util.regex.*`, `org.junit.jupiter.api.*`) | P2 | Replace with explicit imports |
| `src/main/java/com/devmod/actions/client/DevModClientActions.java` | `Minecraft.getInstance()` used outside `com.devmod.client` | P2 | Confirm client-only packaging |
| `src/main/java/com/devmod/debug/client/DebugClientRenderer.java` | `Minecraft.getInstance()` used outside `com.devmod.client` | P2 | Confirm client-only packaging |

---

## P0 - Critical Issues

1. **TelemetryPacketHandler compile error**: missing local `dimension` variable in player snapshot aggregation.
2. **DuckDBBatchWriter init failure**: `ExceptionInInitializerError` during DuckDB telemetry integration tests.

---

## P1 - High Priority Issues

### 1. Wildcard Imports (108 occurrences)

| File | Import Pattern |
|------|---------------|
| `RadialMenuMacroCategoryTest.java` | `import java.nio.file.*` |
| `RadialMenuMacroCategoryTest.java` | `import java.util.*` |
| `RadialMenuMacroCategoryTest.java` | `import java.util.regex.*` |
| `RadialMenuMacroCategoryTest.java` | `import org.junit.jupiter.api.*` |
| Tests (104) | `import static org.junit.jupiter.api.Assertions.*` |

**Fix**: Replace non-JUnit wildcard imports with explicit imports. Static JUnit wildcard imports are acceptable but optional to expand.

### 2. Compiler Warnings (current run)

| File:Line | Warning | Fix |
|-----------|---------|-----|
| `TelemetryPacketHandler.java:26` | unused import (`TelemetryLVC`) | Remove import |

### 3. Duplicate Import (Critical)

`HitHelper.java` has duplicate `import com.devmod.*;` on lines 4 and 5.

---

## P2 - Medium Priority Issues

### Methods >80 Lines (22 found)

| File | Line | Lines |
|------|------|-------|
| `ArenaCommands.java` | 188 | 106 |
| `AdvancedArenaTemplateValidator.java` | 43 | 85 |
| `DuckDBBatchWriter.java` | 1153 | 225 |
| `PlayerAttributeTelemetryService.java` | 99 | 141 |
| `VisualizersPage.java` | 84 | 87 |
| `DebugOverlaysPage.java (unified)` | 59 | 130 |
| `MobEquipmentScreen.java` | 119 | 91 |
| `DebugOverlaysPage.java (testing)` | 110 | 97 |
| `ItemEditorDataManager.java` | 366 | 90 |
| `HeaderComponent.java` | 158 | 84 |
| `WeaponModuleUI.java` | 174 | 88 |
| `PartyScreen.java` | 180 | 99 |
| `TestingSession.java` | 202 | 267 |
| `PerformanceProfiler.java` | 240 | 94 |
| `EnduranceSettingsScreen.java` | 53 | 196 |
| `DirectiveChainManager.java` | 35 | 252 |
| `BloodContractRegistry.java` | 149 | 119 |
| `PerkSynergyWeb.java` | 326 | 116 |
| `DailyChallengeManager.java` | 81 | 206 |
| `WeeklyChallengeManager.java` | 83 | 209 |
| `MutatorSystem.java` | 258 | 91 |
| `PerkSynergySystem.java` | 147 | 220 |

### Files >600 LOC (148 files, top 10)

| File | LOC |
|------|-----|
| `EnduranceQuestManager.java` | 3060 |
| `DevModClientActions.java` | 2736 |
| `ItemEditorScreen.java` | 2498 |
| `DuckDBBatchWriter.java` | 1698 |
| `L7CrossSystemIntegrationTest.java` | 1670 |
| `ComboSystemTest.java` | 1551 |
| `DuckDBTelemetryService.java` | 1527 |
| `DuckDBQueryAPI.java` | 1513 |
| `ArenaBuilder.java` | 1473 |
| `RewardSystemTest.java` | 1418 |

**Note**: Large files are informational only. No refactor in this pass.

### TODO/FIXME (2)

- `ClientUiBridgeImpl.java:106` - Debug overlay integration
- `ComebackSystem.java:176` - Particle effects

---

## Fix Plan

### Batch 1: Imports
- Remove duplicate imports
- Replace wildcard imports with explicit
- Order imports per standard

### Batch 2: Compiler Warnings
- Fix lossy-conversions
- Address this-escape warnings
- Handle deprecation with @SuppressWarnings where appropriate

### Batch 3: Logging Standardization
- Ensure consistent log levels
- Add context IDs where missing

### Batch 4: Comment Cleanup
- Remove trivial comments
- Add invariant documentation

### Batch 5: Micro-refactors
- Extract helpers from longest methods (careful, behavior-preserving only)
