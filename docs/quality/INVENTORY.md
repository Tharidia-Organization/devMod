# Quality Inventory

**Date**: 2025-12-25
**Total Java Files**: 982
**Total LOC**: ~150,000 (estimated)

## Summary Statistics

| Category | Count | Priority |
|----------|-------|----------|
| Wildcard imports | 191 | P1 |
| Compiler warnings | 15 | P1 |
| Methods >80 lines | 22+ | P2 |
| Files >600 LOC | 30+ | P2 (report only) |
| TODO/FIXME | 15 | P2 |
| Minecraft.getInstance outside client | 0 | ✅ |

---

## P0 - Critical Issues

None identified.

---

## P1 - High Priority Issues

### 1. Wildcard Imports (191 occurrences)

| File | Import Pattern |
|------|---------------|
| `HitHelper.java` | `import com.devmod.*` (duplicate!) |
| `FuelConfigManager.java` | `import java.nio.file.*` |
| `FoodConfigManager.java` | `import java.nio.file.*` |
| `InstanceSystemGameTests.java` | `import com.devmod.runtime.*` |
| `L0BootVerificationTests.java` | `import com.devmod.runtime.*` |
| `RecipeInjector.java` | `import net.minecraft.world.item.crafting.*` |
| `RecipeManagerMixin.java` | `import net.minecraft.world.item.crafting.*` |
| `HazardTypeRegistry.java` | `import java.io.*` |
| `ArenaSnapshotManager.java` | `import java.io.*` |
| `AutosmokeScheduler.java` | `import java.util.concurrent.*` |
| `ArenaDashboardEndpoint.java` | `import java.util.concurrent.*` |
| `LogAggregationPipeline.java` | `import java.util.concurrent.*` |
| `PolicyResolver.java` | `import java.util.concurrent.*` |
| `AnalyticsService.java` | `import java.util.concurrent.*` |
| `EnduranceTelemetryService.java` | `import com.devmod.endurance.*` |
| Panel classes (9) | `import static ...PanelConstants.*` |
| `VoxelLabUiTestScreen.java` | `import ...panel.*` |
| `DebugOverlaysPage.java` | `import com.devmod.client.rendering.*` |

**Fix**: Replace with explicit imports.

### 2. Compiler Warnings (15 total)

| File:Line | Warning | Fix |
|-----------|---------|-----|
| `SmartBrainLibCompat.java:177` | deprecation (getRunningBehaviors) | Add @SuppressWarnings or update API |
| `SmartBrainLibCompat.java:190` | deprecation (getMemories) | Add @SuppressWarnings or update API |
| `SoulImprintManager.java:313-331` | 10x deprecation (getCombinedEffect) | Migrate to new API |
| `ItemEditorScreen.java:271` | this-escape | Defer callback registration |
| `ContractHudOverlay.java:92` | lossy-conversions (double→float) | Explicit cast |
| `Guild.java:56` | this-escape | Defer member addition |

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

### Files >600 LOC (30+ files, top 10)

| File | LOC |
|------|-----|
| `EnduranceQuestManager.java` | 3027 |
| `DevModClientActions.java` | 2725 |
| `ItemEditorScreen.java` | 2462 |
| `ArenaBuilder.java` | 1474 |
| `DuckDBBatchWriter.java` | 1473 |
| `DuckDBQueryAPI.java` | 1392 |
| `EnduranceTelemetryService.java` | 1364 |
| `ArenaDashboardEndpoint.java` | 1338 |
| `DuckDBTelemetryService.java` | 1337 |
| `RadialMenuScreen.java` | 1329 |

**Note**: Large files are informational only. No refactor in this pass.

### TODO/FIXME (15)

Most are documentation references to `TODO_ARENA_TEMPLATE.md`. 
5 actual TODOs for future implementation:
- `SpellEngineCompat.java:157` - Component access pending
- `ClientUiBridgeImpl.java:95` - Debug overlay integration
- `ComebackSystem.java:189` - Particle effects
- `SeasonPassSystem.java:383` - Player notification
- `GuildSystem.java:287,333` - Member notifications

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
