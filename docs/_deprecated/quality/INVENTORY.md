# Quality Inventory

**Date**: 2025-12-26  
**Total Java Files**: 1111 (main + test)  
**Total LOC**: ~351,623 (main + test)

## Summary Statistics

| Category | Count | Priority |
|----------|-------|----------|
| Wildcard imports | 229 (tests only) | P1 |
| Compiler warnings | 100+ (NullAway + Error Prone) | P1 |
| Methods >80 lines | 147 (heuristic scan) | P2 |
| Files >600 LOC | 104 (main) | P2 (report only) |
| TODO/FIXME | 15 | P2 |
| Minecraft.getInstance outside client | 0 | ✅ |

---

## P0 - Critical Issues

None identified.

---

## P1 - High Priority Issues

### Inventory Table (Representative)

| File | Issue Category | Severity | Proposed Fix Type |
|------|----------------|----------|-------------------|
| `src/main/java/com/devmod/arena/registry/ArenaTemplateRegistry.java` | NullAway nullable return/arg warnings | P1 | Copy @Nullable fields to locals + guard/annotate; consider @Nullable return contract |
| `src/main/java/com/devmod/arena/config/ArenaTemplateConfig.java` | Error Prone (StringCaseLocaleUsage, EmptyCatch, StringSplitter) | P1 | Use Locale.ROOT, add fallback handling, replace split with Splitter |
| `src/main/java/com/devmod/arena/registry/TemplateValidator.java` | NullAway init warnings + locale casing | P1 | Initialize @NonNull fields in ctor or annotate @Nullable; use Locale.ROOT |
| `src/main/java/com/devmod/compat/mods/easynpc/EasyNpcCompat.java` | NullAway uninitialized statics + EmptyCatch | P1 | Lazy init guards or @Nullable + comment; handle exceptions |
| `src/test/java/com/devmod/stress/PerformancePatternTest.java` | Wildcard imports (java/junit) | P1 | Replace with explicit imports |
| `src/test/java/com/devmod/stress/L5MemoryAndSoakTest.java` | Wildcard imports (java/junit) | P1 | Replace with explicit imports |

### Wildcard Imports (229 occurrences, tests only)

**Fix**: Replace wildcard imports with explicit ones in test sources (JUnit + java.* + concurrent.*).

---

## P2 - Medium Priority Issues

### Methods >80 Lines (heuristic scan, top 12)

| File | Start Line | Approx. Lines |
|------|------------|---------------|
| `src/main/java/com/devmod/actions/client/DevModClientActions.java` | 742 | 738 |
| `src/main/java/com/devmod/actions/client/DevModClientActions.java` | 185 | 556 |
| `src/main/java/com/devmod/network/NetworkHandler.java` | 223 | 425 |
| `src/main/java/com/devmod/actions/client/DevModClientActions.java` | 1481 | 355 |
| `src/main/java/com/devmod/client/ui/editor/systems/MultiEditPanel.java` | 238 | 325 |
| `src/main/java/com/devmod/endurance/EnduranceEventHandler.java` | 422 | 295 |
| `src/main/java/com/devmod/endurance/PerkSystem.java` | 391 | 270 |
| `src/main/java/com/devmod/client/testing/TestingSession.java` | 206 | 269 |
| `src/main/java/com/devmod/gametest/TestHarnessCommands.java` | 146 | 254 |
| `src/main/java/com/devmod/endurance/DirectiveChainManager.java` | 41 | 253 |
| `src/main/java/com/devmod/client/endurance/QuestCompletionScreen.java` | 167 | 229 |
| `src/main/java/com/devmod/telemetry/duckdb/DuckDBBatchWriter.java` | 1176 | 226 |

**Note**: Heuristic scan (brace-based) may overcount; use for prioritization only.

### Files >600 LOC (main, top 10)

| File | LOC |
|------|-----|
| `src/main/java/com/devmod/endurance/EnduranceQuestManager.java` | 3041 |
| `src/main/java/com/devmod/actions/client/DevModClientActions.java` | 2755 |
| `src/main/java/com/devmod/client/ui/editor/ItemEditorScreen.java` | 2472 |
| `src/main/java/com/devmod/telemetry/duckdb/DuckDBBatchWriter.java` | 1496 |
| `src/main/java/com/devmod/arena/builder/ArenaBuilder.java` | 1484 |
| `src/main/java/com/devmod/client/endurance/KitSelectionScreen.java` | 1446 |
| `src/main/java/com/devmod/telemetry/duckdb/DuckDBQueryAPI.java` | 1393 |
| `src/main/java/com/devmod/telemetry/endurance/EnduranceTelemetryService.java` | 1378 |
| `src/main/java/com/devmod/client/ui/radial/RadialMenuScreen.java` | 1364 |
| `src/main/java/com/devmod/arena/dashboard/ArenaDashboardEndpoint.java` | 1347 |

**Note**: Large files are informational only. No large refactor in this pass.

### TODO/FIXME (15)

Most are documentation references to `TODO_ARENA_TEMPLATE.md`.  
Actionable TODOs:
- `src/main/java/com/devmod/compat/mods/spellengine/SpellEngineCompat.java:160` - Component access pending
- `src/main/java/com/devmod/client/ClientUiBridgeImpl.java:97` - Debug overlay integration
- `src/main/java/com/devmod/endurance/ComebackSystem.java:190` - Particle effects
- `src/main/java/com/devmod/endurance/season/SeasonPassSystem.java:390` - Player notification
- `src/main/java/com/devmod/endurance/guild/GuildSystem.java:294,340` - Member notifications

### Duplicazioni Evidenti (examples)

- Repeated UI label composition in endurance/party screens → consolidate via `I18n` keys  
- Item category checks in kit selection → already partially extracted; continue to normalize

---

## Fix Plan

### Batch 1: Imports
- Replace wildcard imports in tests
- Enforce import order (java → javax → external → net.minecraft → net.neoforged → com.devmod)
- Remove unused imports

### Batch 2: Null-Safety
- Address high-signal NullAway warnings in arena registry and compat modules
- Copy @Nullable fields to locals before checks
- Add/clarify @Nullable/@Nonnull contracts

### Batch 3: Logging Standardization
- Verify hot-path logging and adjust levels
- Add context IDs to action-oriented logs

### Batch 4: Comment Cleanup
- Remove trivial comments, add invariants/edge cases where needed

### Batch 5: Micro-refactors
- Extract helpers for clarity in long methods (behavior-preserving)
