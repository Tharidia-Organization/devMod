# Glossary

> **Audit Date**: 2024-12-23

---

## Arena System

| Term | Definition | File Reference |
|------|------------|----------------|
| **ArenaTemplate** | JSON/YAML definition of an arena including structures, spawns, and hazards | `registry/ArenaTemplate.java:22` |
| **ArenaPolicy** | Rules for selecting templates based on context (mob type, difficulty, etc.) | `policy/ArenaPolicy.java:12` |
| **ResolvedArena** | Final combination of template + policy after resolution | `policy/ResolvedArena.java` |
| **ResolveContext** | Input context for policy resolution (mobType, questType, difficulty, tags, playerCount) | `policy/ResolveContext.java:12` |
| **PolicyResolver** | Engine that matches context to policies and selects best template | `policy/PolicyResolver.java:31` |
| **TemplateRegistryBootstrap** | Initialization and hot-reload of template registry | `registry/TemplateRegistryBootstrap.java:26` |
| **BuildTransaction** | Tracked arena build with rollback capability | `builder/BuildTransaction.java` |
| **Autosmoke** | Automated smoke testing of arena templates | `autosmoke/AutosmokeScheduler.java` |

---

## Endurance System

| Term | Definition | File Reference |
|------|------------|----------------|
| **EnduranceQuest** | A wave-based roguelike quest session | `EnduranceQuest.java` |
| **WaveManager** | Orchestrates mob spawning and wave progression | `WaveManager.java:1-1290` |
| **WaveDirective** | Preset wave behavior (steady, blitz, brute_force, etc.) | `WaveDirective.java:17` |
| **SpawnAffix** | Modifier applied to spawned mobs (RUSH, BRUTE, ELITE, etc.) | `SpawnAffix.java:26` |
| **ComboSystem** | DMC-style scoring from D rank to SSS | `ComboSystem.java` |
| **StyleRank** | Combo ranking: D, C, B, A, S, SS, SSS | `ComboSystem.java` |
| **PerkSystem** | Roguelike upgrade system between waves | `PerkSystem.java` |
| **PerkTier** | Perk rarity: COMMON (60%), UNCOMMON (25%), RARE (10%), EPIC (4%), LEGENDARY (1%) | `PerkSystem.java` |
| **ActiveQuestSession** | Runtime state of an ongoing quest | `EnduranceQuestManager.java` |

---

## Instance System

| Term | Definition | File Reference |
|------|------------|----------------|
| **InstanceData** | Data model for a dimension instance | `InstanceData.java:19-521` |
| **InstanceState** | State machine: CREATING→READY→ACTIVE→COMPLETING→DESTROYING→DESTROYED | `InstanceState.java:16-92` |
| **PlayerInstanceState** | Player flow: NORMAL→PREPARING→IN_TRANSIT→IN_INSTANCE→RETURNING | `PlayerInstanceState.java:19-97` |
| **PlayerInstanceSnapshot** | NBT serialization of player state for recovery | `PlayerInstanceSnapshot.java:27-607` |
| **DynamicDimensionManager** | Creates and destroys runtime void dimensions | `DynamicDimensionManager.java:56-758` |
| **RecoverySystem** | Restores players from snapshots on login/crash | `RecoverySystem.java:35-583` |

---

## Telemetry System

| Term | Definition | File Reference |
|------|------------|----------------|
| **TelemetryService** | Central telemetry orchestrator | `TelemetryService.java:987` |
| **DuckDBBatchWriter** | Async batch inserter for DuckDB | `duckdb/DuckDBBatchWriter.java:1473` |
| **DuckDBQueryAPI** | Query interface for analytics | `duckdb/DuckDBQueryAPI.java:1392` |
| **Circuit Breaker** | Failure protection: after 5 errors, switch to NDJSON fallback | `DuckDBBatchWriter.java:76` |
| **FightSession** | Room-based grouping of combat events | `FightSessionService.java` |
| **HeatmapService** | Spatial aggregation for death, movement, stuck locations | `HeatmapService.java:441` |
| **TTK** | Time-to-kill metric (from first hit or spawn) | Telemetry tables |

---

## Radial Menu / UX

| Term | Definition | File Reference |
|------|------------|----------------|
| **RadialMenuScreenV3** | Main radial menu UI | `ui/radial/RadialMenuScreenV3.java:1-1330` |
| **RadialAction** | Action definition with handler and metadata | `actions/RadialAction.java:1-309` |
| **ActionRegistry** | Global registry of all actions | `actions/ActionRegistry.java:1-178` |
| **MacroCategory** | Top-level grouping: ANALYZE, TELEMETRY, COMBAT, ARENA, PLAY, TOOLS | `model/MacroCategory.java:1-140` |
| **RadialCategory** | Category container for menu items | `RadialCategory.java:1-231` |
| **ActionType** | Action execution type: NAVIGATE_SCREEN, RUN_SERVER_COMMAND, TOGGLE_SETTING, etc. | `ActionType.java` |

---

## Network / Communication

| Term | Definition | File Reference |
|------|------------|----------------|
| **Payload** | Serializable network packet data | `network/*.java` |
| **playToServer** | Client→Server packet direction | `NetworkHandler.java` |
| **playToClient** | Server→Client packet direction | `NetworkHandler.java` |
| **PacketSecurityService** | Validation layer for network packets | `PacketSecurityService.java` |
| **StreamCodec** | NeoForge packet serialization | Network handlers |

---

## Config System

| Term | Definition | File Reference |
|------|------------|----------------|
| **ModConfigSpec** | NeoForge typed config builder | `Config.java` |
| **ConfigChangeListener** | Callback for hot-reload notifications | `EditorConfig.java:79` |
| **Feature Flag** | Runtime toggle for features | Various config classes |
| **Circuit Breaker** | Automatic failure recovery pattern | `DuckDBBatchWriter.java` |

---

## Combat System

| Term | Definition | File Reference |
|------|------------|----------------|
| **OBB** | Oriented Bounding Box (rotation-aware hitbox) | `collision/obb/` |
| **AABB** | Axis-Aligned Bounding Box (standard Minecraft) | Vanilla |
| **BodyPart** | Hit detection zones: HEAD, BODY, ARMS, LEGS | `collision/bodypart/` |
| **ArmorPen** | Armor penetration mechanic | `DamageHandler.java` |
| **HitContext** | Temporary hit data storage | `HitContext.java` |

---

## Testing

| Term | Definition | File Reference |
|------|------------|----------------|
| **GameTest** | NeoForge in-game test framework | `gametest/` |
| **L0 Boot** | Critical startup verification tests | `L0BootVerificationTests.java` |
| **QAEventTracker** | Event-driven test auto-completion | `QAEventTracker.java` |
| **TesterProfile** | Gamification: XP, badges, streaks | `TesterProfile.java` |

---

## Abbreviations

| Abbrev | Full Form |
|--------|-----------|
| **DD** | Design Decision (numbered) |
| **P0** | Priority 0 (Critical) |
| **P1** | Priority 1 (High) |
| **P2** | Priority 2 (Medium) |
| **TTK** | Time-to-Kill |
| **DPS** | Damage Per Second |
| **KPS** | Kills Per Second |
| **DTPS** | Damage Taken Per Second |
| **OBB** | Oriented Bounding Box |
| **AABB** | Axis-Aligned Bounding Box |
| **NBT** | Named Binary Tag (Minecraft serialization) |
| **NDJSON** | Newline-Delimited JSON |
| **HUD** | Heads-Up Display |
| **VFX** | Visual Effects |
| **CI** | Continuous Integration |

---

## Cross-References

- [[MOC]] - Master index
- [[PROJECT_TOPOLOGY]] - Code structure
- [[AUDIT_REPORT]] - Findings
- [[TRACEABILITY_MATRIX]] - Feature mapping

---

*Generated from codebase analysis - 2024-12-23*
