# Quality Inventory

| File | Issue Category | Severity | Proposed Fix Type |
| --- | --- | --- | --- |
| src/main/java/com/devmod/combat/DamageHandler.java | Client-only dependency in common package (direct `com.devmod.client.*` usage) | P1 | Route through client hooks or reflection with Dist guard to avoid classloading on dedicated servers |
| src/main/java/com/devmod/telemetry/dashboard/DashboardCommand.java | Client-only dependency in common command | P1 | Guard with Dist.CLIENT or route through client delegate hooks |
| src/main/java/com/devmod/debug/DebugNetworkHandler.java | Client-only handler referenced in non-client package | P1 | Use client hooks or reflection with Dist guard |
| src/main/java/com/devmod/party/PartyData.java | @Nullable field access pattern (selectedMobId used without local copy) | P1 | Copy nullable field to local before check/use |
| src/main/java/com/devmod/runtime/InstanceRegistry.java | @Nullable access pattern (dimensionKey read multiple times) | P1 | Cache nullable read in local before check/use |
| src/main/java/com/devmod/network/NetworkHandler.java | Method >80 lines (register) | P1 | Extract registration helpers, keep behavior same |
| src/main/java/com/devmod/endurance/EnduranceEventHandler.java | Method >80 lines (onWaveComplete) | P1 | Extract private helpers for clarity |
| src/main/java/com/devmod/actions/client/DevModClientActions.java | Methods >80 lines (multiple register blocks) | P2 | Defer; candidate helper extraction without behavior change |
| src/main/java/com/devmod/endurance/EnduranceQuestManager.java | File >600 LOC (3027) | P2 | Report only; avoid large refactor in this pass |
| src/main/java/com/devmod/arena/builder/ArenaBuilder.java | File >600 LOC (1474) | P2 | Report only; avoid large refactor in this pass |
| src/main/java/com/devmod/client/ui/editor/ItemEditorScreen.java | File >600 LOC (2462) | P2 | Report only; avoid large refactor in this pass |
| src/main/java/com/devmod/recipe/RecipeInjector.java | Wildcard imports (java.util.*, net.minecraft.world.item.crafting.*) | P2 | Replace with explicit imports |
| src/test/java/com/devmod/** (many files) | Wildcard static imports (Assertions.*) | P2 | Limit to explicit static imports where reasonable |
| src/main/java/com/devmod/endurance/guild/GuildSystem.java | TODO/FIXME present | P2 | Resolve or downgrade with context |
| src/main/java/com/devmod/compat/mods/spellengine/SpellEngineCompat.java | TODO/FIXME present | P2 | Resolve or downgrade with context |
| src/main/java/com/devmod/runtime/InstanceRegistry.java | Micro-duplication (createInstance/createPartyInstance setup) | P2 | Extract helper for shared setup |

Notes:
- No occurrences found for `Minecraft.getInstance()` outside client-related package paths.
- Wildcard import list remains large; inventory calls out representative hotspots in main and tests.
