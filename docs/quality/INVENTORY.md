# Quality Inventory

| File | Issue Category | Severity | Proposed Fix Type |
| --- | --- | --- | --- |
| src/test/java/com/devmod/boot/L0SmokeBootTest.java | Test structural drift (expects ui/ package + UIConstants path) | P0 | Align test paths to current client/ui layout or add compatibility shim |
| src/test/java/com/devmod/client/ui/radial/RadialMenuMacroCategoryTest.java | Test structural drift (expects ui/radial/RadialMenuScreen.java) | P0 | Update expected path or relocate file reference |
| src/main/java/com/devmod/compat/mods/clothconfig/ClothConfigCompat.java | Client-only import in non-client package | P1 | Isolate client-only code behind dist guard or move to client package |
| src/main/java/com/devmod/runtime/InstanceData.java | @Nullable field access patterns | P1 | Copy nullable fields to locals before checks; add requireNonNull where safe |
| src/main/java/com/devmod/party/PartyManager.java | @Nullable field access patterns | P1 | Copy nullable fields to locals before checks; clarify @Nullable contracts |
| src/main/java/com/devmod/runtime/PlayerInstanceSnapshot.java | @Nullable field access patterns | P1 | Copy nullable fields to locals before checks; add requireNonNull where safe |
| src/main/java/com/devmod/network/NetworkHandler.java | Method >80 lines (register) | P1 | Extract registration helpers, keep behavior same |
| src/main/java/com/devmod/endurance/EnduranceEventHandler.java | Method >80 lines (onWaveComplete) | P1 | Extract private helpers for clarity |
| src/main/java/com/devmod/arena/command/ArenaCommands.java | Method >80 lines (register/createArena) | P2 | Extract private helpers for clarity |
| src/main/java/com/devmod/telemetry/duckdb/DuckDBSchemaManager.java | Method >80 lines (migrateSchema) | P2 | Extract small helpers, keep behavior same |
| src/main/java/com/devmod/endurance/EnduranceQuestManager.java | File >600 LOC (2939) | P2 | Report only; avoid large refactor in this pass |
| src/main/java/com/devmod/arena/builder/ArenaBuilder.java | File >600 LOC (1474) | P2 | Report only; avoid large refactor in this pass |
| src/main/java/com/devmod/client/ui/editor/ItemEditorScreen.java | File >600 LOC (2400) | P2 | Report only; avoid large refactor in this pass |
| src/main/java/com/devmod/debug/DebugManager.java | Wildcard import (java.util.*) | P2 | Replace with explicit imports |
| src/main/java/com/devmod/endurance/LeaderboardSystem.java | Wildcard imports (java.io.*, java.util.*) | P2 | Replace with explicit imports |
| src/main/java/com/devmod/network/NetworkHandler.java | Wildcard imports (com.devmod.endurance.*, com.devmod.party.*) | P2 | Replace with explicit imports |
| src/test/java/com/devmod/** (many files) | Wildcard static imports (Assertions.*) | P2 | Limit to explicit static imports where reasonable |
| src/main/java/com/devmod/client/ClientUiBridgeImpl.java | TODO/FIXME present | P2 | Resolve or downgrade with context |
| src/main/java/com/devmod/compat/mods/spellengine/SpellEngineCompat.java | TODO/FIXME present | P2 | Resolve or downgrade with context |
| src/main/java/com/devmod/endurance/ComebackSystem.java | TODO/FIXME present | P2 | Resolve or downgrade with context |
| src/main/java/com/devmod/actions/client/DevModClientActions.java | Duplication in large register methods | P2 | Extract shared helper for action registration |

Notes:
- No occurrences found for Minecraft.getInstance() outside client packages.
- Wildcard import list is large; inventory calls out representative hotspots in main and tests.
