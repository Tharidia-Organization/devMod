# Documentation Behavior Matrix

> Last updated: 2025-12-26
> Scope: active docs only (see filters below).
> Validation rule: every behavior must map to automated tests; manual checklists are not accepted.

---

## Scope Filters
- Excluded by path: `docs/_deprecated/`, legacy duplicate dirs, legacy subsystem stubs, and `docs/testing/L*_REPORT.md`.
- Excluded by status keywords: planning, historical, archive, deprecated (English/Italian variants).
- Note: any manual checklist docs will be converted to automated tests or archived.

---

## Coverage Summary
- Active docs in scope: 146
- Excluded by status: 11
- Behaviors mapped: 44 (END-01..END-08, ARENA-01..ARENA-09, INST-01..INST-05, TEL-01..TEL-04, CS-01..CS-03, RAD-01..RAD-04, TOOL-01..TOOL-04, CFG-01..CFG-04, TEST-01..TEST-03)
- Behaviors with automated tests: 44 (44 direct, 0 proxy)

---

## Verification Evidence
- 2025-12-26: `./gradlew test --tests 'com.devmod.endurance.*DirectTest'` — PASS
- 2025-12-26: `./gradlew test --tests 'com.devmod.arena.autosmoke.*Test'` — FAIL (Error Prone crash; resolved by disabling `ParameterName`)
- 2025-12-26: `./gradlew test --tests 'com.devmod.arena.autosmoke.*Test'` — PASS
- 2025-12-26: `./gradlew test --tests 'com.devmod.arena.*'` — PASS
- 2025-12-26: `./gradlew test --tests 'com.devmod.runtime.*DirectTest'` — PASS
- 2025-12-26: `./gradlew test --tests 'com.devmod.telemetry.*DirectTest' --tests 'com.devmod.telemetry.duckdb.packets.*DirectTest' --tests 'com.devmod.network.*DirectTest' --tests 'com.devmod.client.ui.radial.*DirectTest' --tests 'com.devmod.client.ui.radial.model.*DirectTest' --tests 'com.devmod.client.ui.radial.input.*DirectTest'` — PASS
- 2025-12-26: `./gradlew test --tests 'com.devmod.arena.autosmoke.*DirectTest'` — PASS
- 2025-12-26: `./gradlew test --tests 'com.devmod.config.gamedesign.*DirectTest' --tests 'com.devmod.ModConfigDirectTest'` — PASS
- 2025-12-26: `./gradlew test --tests 'com.devmod.testing.*DirectTest'` — PASS

---

## Active Docs (In Scope)
- `docs/ARCHITECTURE.excalidraw.md` — status: NO_STATUS
- `docs/ARCHITECTURE.md` — status: NO_STATUS
- `docs/ASSETS_CREDITS.md` — status: NO_STATUS
- `docs/AUDIT_REPORT.md` — status: NO_STATUS
- `docs/DOCS_GUIDE.md` — status: NO_STATUS
- `docs/DOCS_BEHAVIOR_MATRIX.md` — status: NO_STATUS
- `docs/DOCS_INVENTORY.md` — status: NO_STATUS
- `docs/DOCS_VERIFICATION_REPORT.md` — status: NO_STATUS
- `docs/DOCUMENTATION_STATUS.md` — status: NO_STATUS
- `docs/ENTRYPOINTS.md` — status: NO_STATUS
- `docs/FEATURES.md` — status: : ✅ CURRENT - Aligned with codebase
- `docs/GAME_DESIGN_ANALYSIS.md` — status: NO_STATUS
- `docs/GLOSSARY.md` — status: NO_STATUS
- `docs/MIGRATION.md` — status: NO_STATUS
- `docs/MOC.md` — status: NO_STATUS
- `docs/PROJECT_TOPOLOGY.md` — status: NO_STATUS
- `docs/README.md` — status: NO_STATUS
- `docs/TRACEABILITY_MATRIX.md` — status: NO_STATUS
- `docs/UX_PLAYER_JOURNEY.md` — status: NO_STATUS
- `docs/adr/ADR-001-endurance-quest-manager.md` — status: : Accepted
- `docs/adr/ADR-002-devmod-client-actions.md` — status: : Accepted
- `docs/adr/ADR-003-item-editor-screen.md` — status: : Accepted
- `docs/areas/arena/README.md` — status: : ✅ CURRENT - Arena Template v2.23 implementato
- `docs/areas/client_server/README.md` — status: : ✅ CURRENT - client/server boundary aligned
- `docs/areas/config/README.md` — status: : ✅ CURRENT - code-aligned
- `docs/areas/endurance/README.md` — status: : PARTIAL
- `docs/areas/instance/INSTANCE_DIMENSION_SYSTEM.md` — status: : ✅ CURRENT - implementation aligned
- `docs/areas/instance/INSTANCE_SYSTEM_TEST_STRATEGY.md` — status: : ✅ CURRENT - automated coverage + gaps tracked
- `docs/areas/instance/README.md` — status: : ✅ CURRENT - code-aligned
- `docs/areas/radial/README.md` — status: : ✅ CURRENT - code-aligned
- `docs/areas/telemetry/README.md` — status: : ✅ CURRENT - code-aligned
- `docs/areas/tools/README.md` — status: : ✅ CURRENT - code-aligned
- `docs/compat/MOD_INVENTORY.md` — status: NO_STATUS
- `docs/compat/README.md` — status: NO_STATUS
- `docs/compat/clothconfig.md` — status: NO_STATUS
- `docs/compat/curios.md` — status: NO_STATUS
- `docs/compat/irons_spellbooks.md` — status: NO_STATUS
- `docs/compat/ranged_weapon_api.md` — status: NO_STATUS
- `docs/compat/spark.md` — status: NO_STATUS
- `docs/compat/spell_engine.md` — status: NO_STATUS
- `docs/compat/spell_power.md` — status: NO_STATUS
- `docs/cross_cutting/CLIENT_SERVER.md` — status: NO_STATUS
- `docs/cross_cutting/CONCURRENCY.md` — status: NO_STATUS
- `docs/cross_cutting/ERROR_HANDLING.md` — status: NO_STATUS
- `docs/cross_cutting/TELEMETRY_CONVENTIONS.md` — status: NO_STATUS
- `docs/design/GAME_DESIGN_EVOLUTION.md` — status: ✅ COMPLETE - All Systems Implemented
- `docs/design/GAME_DESIGN_ROADMAP.md` — status: NO_STATUS
- `docs/gamedesign/ENDURANCE_IMPROVEMENTS.md` — status: 12/12 ALL TASKS COMPLETED** (P0 + P1 + P2 + P3 ALL Complete!)
- `docs/network/PACKET_REGISTRY.md` — status: NO_STATUS
- `docs/network/SECURITY_HARDENING.md` — status: Active
- `docs/project/BUG_LOG.md` — status: RESOLVED
- `docs/project/IMPLEMENTATION_STATUS.md` — status: NO_STATUS
- `docs/project/NEXT_STEPS.md` — status: NO_STATUS
- `docs/project/TODO_WARNINGS.md` — status: NO_STATUS
- `docs/project/pr-drafts/PR_DRAFT_Debug_MultiEdit.md` — status: NO_STATUS
- `docs/remediation/VERIFY.md` — status: : ✅ CURRENT - server readiness runbook
- `docs/runbook/arena-alerts.md` — status: NO_STATUS
- `docs/subsystems/arena-template-rework/ARENA_TEMPLATE_AUDIT.md` — status: : ✅ CURRENT - Audit allineato con codebase
- `docs/subsystems/arena-template-rework/DOCUMENTATION_AUDIT_REPORT.md` — status: : ✅ CURRENT - audit refreshed 2025-12-26
- `docs/subsystems/arena-template-rework/MIGRATION_INVENTORY.md` — status: : ✅ CURRENT - inventory tracked
- `docs/subsystems/arena-template-rework/PRODUCTION_MARKER_README.md` — status: NO_STATUS
- `docs/subsystems/arena-template-rework/README.md` — status: : ✅ CURRENT - Sistema Arena Template v2.23 documentato
- `docs/subsystems/arena-template-rework/TODO_AGENT_01_COMPLETE.md` — status: NO_STATUS
- `docs/subsystems/arena-template-rework/TODO_AGENT_02_COMPLETE.md` — status: NO_STATUS
- `docs/subsystems/arena-template-rework/TODO_AGENT_03_COMPLETE.md` — status: NO_STATUS
- `docs/subsystems/arena-template-rework/TODO_AGENT_04_COMPLETE.md` — status: : ✅ CURRENT - API/metrics doc allineato
- `docs/subsystems/arena-template-rework/TODO_AGENT_05_COMPLETE.md` — status: : ✅ CURRENT - DuckDbRepository implemented
- `docs/subsystems/arena-template-rework/TODO_AGENT_06_COMPLETE.md` — status: NO_STATUS
- `docs/subsystems/arena-template-rework/TODO_AGENT_07_COMPLETE.md` — status: : ✅ CURRENT - autosmoke thresholds allineati
- `docs/subsystems/arena-template-rework/TODO_AGENT_08_COMPLETE.md` — status: NO_STATUS
- `docs/subsystems/arena-template-rework/TODO_AGENT_09_COMPLETE.md` — status: NO_STATUS
- `docs/subsystems/arena-template-rework/TODO_AGENT_10_COMPLETE.md` — status: NO_STATUS
- `docs/subsystems/arena-template-rework/TODO_AGENT_11_COMPLETE.md` — status: NO_STATUS
- `docs/subsystems/arena-template-rework/TODO_AGENT_12_COMPLETE.md` — status: : ✅ CURRENT - pool/rollout doc allineato
- `docs/subsystems/arena-template-rework/TODO_ARENA_TEMPLATE.md` — status: : ✅ DEFINITIVE
- `docs/subsystems/arena-template-rework/TODO_GAPS.md` — status: : ✅ CURRENT - gap tracking
- `docs/subsystems/editor-design-system/00-overview.md` — status: NO_STATUS
- `docs/subsystems/editor-design-system/01-layout-specifications.md` — status: NO_STATUS
- `docs/subsystems/editor-design-system/02-shared-components.md` — status: NO_STATUS
- `docs/subsystems/editor-design-system/03-architecture.md` — status: NO_STATUS
- `docs/subsystems/editor-design-system/03-crafting-analysis.md` — status: NO_STATUS
- `docs/subsystems/editor-design-system/04-debug-system.md` — status: NO_STATUS
- `docs/subsystems/editor-design-system/05-dual-mode-system.md` — status: NO_STATUS
- `docs/subsystems/editor-design-system/06-persistence.md` — status: NO_STATUS
- `docs/subsystems/editor-design-system/07-ui-scaling.md` — status: NO_STATUS
- `docs/subsystems/editor-design-system/08-unified-architecture.md` — status: NO_STATUS
- `docs/subsystems/editor-design-system/09-radial-menu-integration.md` — status: NO_STATUS
- `docs/subsystems/editor-design-system/10-weapon-types.md` — status: NO_STATUS
- `docs/subsystems/editor-design-system/11-multiedit-system.md` — status: NO_STATUS
- `docs/subsystems/editor-design-system/12-grid-spacing.md` — status: NO_STATUS
- `docs/subsystems/editor-design-system/13-scroll-system.md` — status: NO_STATUS
- `docs/subsystems/editor-design-system/14-debug-overlay.md` — status: NO_STATUS
- `docs/subsystems/editor-design-system/15-weapon-properties.md` — status: NO_STATUS
- `docs/subsystems/editor-design-system/16-armor-properties.md` — status: NO_STATUS
- `docs/subsystems/editor-design-system/16-ranged-weapons.md` — status: NO_STATUS
- `docs/subsystems/editor-design-system/17-implementation-guide.md` — status: NO_STATUS
- `docs/subsystems/editor-design-system/18-testing-strategy.md` — status: NO_STATUS
- `docs/subsystems/editor-design-system/19-performance-considerations.md` — status: NO_STATUS
- `docs/subsystems/editor-design-system/20-crafting-info-panel.md` — status: ✅ **Implementato** in `ui/editor/systems/CraftingInfoPanel.java`. Estende `BaseOverlay` per comportamento modale consistente.
- `docs/subsystems/editor-design-system/21-template-preset-architecture.md` — status: NO_STATUS
- `docs/subsystems/editor-design-system/23-architecture-comparison.md` — status: NO_STATUS
- `docs/subsystems/editor-design-system/24-component-library.md` — status: NO_STATUS
- `docs/subsystems/editor-design-system/25-panel-system.md` — status: NO_STATUS
- `docs/subsystems/editor-design-system/26-module-evolution-guide.md` — status: NO_STATUS
- `docs/subsystems/editor-design-system/27-general-module-hub.md` — status: NO_STATUS
- `docs/subsystems/editor-design-system/A0_VERIFICATION.md` — status: NO_STATUS
- `docs/subsystems/editor-design-system/COMPLETION_STRATEGY.md` — status: NO_STATUS
- `docs/subsystems/editor-design-system/DEBUG_PANEL_VERIFICATION.md` — status: NO_STATUS
- `docs/subsystems/editor-design-system/EDITOR_BUTTON_AUDIT_COMPLETE.md` — status: NO_STATUS
- `docs/subsystems/editor-design-system/EDITOR_IMPLEMENTATION_LOG.md` — status: NO_STATUS
- `docs/subsystems/editor-design-system/MISSING_MECHANICS_ANALYSIS.md` — status: NO_STATUS
- `docs/subsystems/editor-design-system/README.md` — status: : ✅ CURRENT - Sistema design implementato e documentato
- `docs/subsystems/editor-design-system/RENDERING_AUDIT.md` — status: : ✅ CURRENT - Audit architettura rendering
- `docs/subsystems/editor-design-system/SHADER_SYSTEM.md` — status: NO_STATUS
- `docs/subsystems/editor-design-system/TODO.md` — status: NO_STATUS
- `docs/subsystems/editor-design-system/TODO_EDITOR_MISSING_FEATURES.md` — status: NO_STATUS
- `docs/subsystems/impact-hud-audit/00-executive-summary.md` — status: NO_STATUS
- `docs/subsystems/impact-hud-audit/01-architecture.md` — status: NO_STATUS
- `docs/subsystems/impact-hud-audit/02-damage-calculation.md` — status: NO_STATUS
- `docs/subsystems/impact-hud-audit/03-rendering-system.md` — status: NO_STATUS
- `docs/subsystems/impact-hud-audit/04-issues-and-bugs.md` — status: : AGGIORNATO - Bug risolti marcati come FIXED
- `docs/subsystems/impact-hud-audit/05-upgrade-roadmap.md` — status: ✅ DONE
- `docs/subsystems/impact-hud-audit/06-code-snippets.md` — status: NO_STATUS
- `docs/subsystems/impact-hud-audit/README.md` — status: : PARTIALLY OUTDATED - See notes below
- `docs/subsystems/prismatic-shield-integration/00-overview.md` — status: NO_STATUS
- `docs/subsystems/prismatic-shield-integration/01-shader-integration.md` — status: NO_STATUS
- `docs/subsystems/prismatic-shield-integration/02-mesh-generation.md` — status: NO_STATUS
- `docs/subsystems/prismatic-shield-integration/03-impact-effects.md` — status: NO_STATUS
- `docs/subsystems/prismatic-shield-integration/04-deflection-system.md` — status: NO_STATUS
- `docs/subsystems/prismatic-shield-integration/05-editor-integration.md` — status: NO_STATUS
- `docs/subsystems/prismatic-shield-integration/06-network-sync.md` — status: NO_STATUS
- `docs/subsystems/prismatic-shield-integration/07-complete-file-inventory.md` — status: NO_STATUS
- `docs/telemetry/MISSING_TELEMETRY_HOOKS.md` — status: NO_STATUS
- `docs/telemetry/TELEMETRY_DOCUMENTATION.md` — status: NO_STATUS
- `docs/telemetry/dashboard/DASHBOARD_UPGRADE_PLAN.md` — status: NO_STATUS
- `docs/telemetry/duckdb/DUCKDB_MIGRATION_AUDIT.md` — status: P0 FIXED, P1 COMPLETE, P2-A COMPLETE - P2-B Pending
- `docs/testing/PROGRESSIVE_TEST_PLAN.md` — status: Initial Analysis Complete
- `docs/testing/QA_WEAPON_PROPERTIES.md` — status: NO_STATUS
- `docs/testing/TESTING.md` — status: : ✅ CURRENT - code-aligned
- `docs/testing/TEST_HARNESS.md` — status: : ✅ CURRENT - code-aligned
- `docs/testing/TEST_WORLD_SETUP.md` — status: NO_STATUS
- `docs/tools/mcp/codex-mcp-server/README.md` — status: NO_STATUS
- `docs/ui/UI_AUDIT_FINDINGS.md` — status: NO_STATUS
- `docs/ui/UI_INVENTORY.md` — status: NO_STATUS
- `docs/ui/UI_LOCALIZATION_TODO.md` — status: NO_STATUS
- `docs/ui/UI_NAVIGATION_MAP.md` — status: NO_STATUS

---

## Excluded by Status
- `docs/subsystems/editor-design-system/22-recipe-editor-future.md` — status: PIANIFICATO - Feature complessa che richiede integrazione profonda con Minecraft
- `docs/subsystems/editor-design-system/AUDIT.md` — status: : 📊 HISTORICAL - Audit snapshot pre-refactoring
- `docs/areas/instance/INSTANCE_SYSTEM_MANUAL_TEST_CHECKLIST.md` — status: ARCHIVED - replaced by automated validations
- `docs/areas/client_server/CLIENT_BOUNDARY_AUDIT.md` — status: ARCHIVED - superseded by boundary README
- `docs/areas/radial/RADIAL_AUDIT.md` — status: HISTORICAL - legacy snapshot
- `docs/areas/radial/RADIAL_BUTTON_CONTRACT.md` — status: HISTORICAL - legacy spec
- `docs/areas/radial/RADIAL_CENSUS.md` — status: HISTORICAL - legacy inventory
- `docs/areas/radial/RADIAL_NAV_MAP.md` — status: PLANNING - vision map
- `docs/areas/radial/RADIAL_QA_SCENARIOS.md` — status: ARCHIVED - manual checklist
- `docs/areas/tools/null-suppression-audit.md` — status: PLANNING - not active; excluded from validation
- `docs/infrastructure/INFRASTRUCTURE_PLAN.md` — status: PLANNING - roadmap only

---

## Manual Checklists to Replace
- None

---

## Behavior Mapping (Initial)

### Endurance System (docs/areas/endurance/README.md)

| Behavior ID | Statement | Code refs | Tests | Validation |
|---|---|---|---|---|
| END-01 | Wave management uses progressive difficulty scaling | `com/devmod/endurance/WaveManager`, `DifficultyScaler` | `DifficultyScalerDirectTest` (direct) | DIRECT |
| END-02 | Combo system tracks D→SSS style ranks | `com/devmod/endurance/ComboSystem`, `StyleRank` | `ComboSystemDirectTest` (direct) | DIRECT |
| END-03 | Perk system offers upgrades between waves | `com/devmod/endurance/PerkSystem` | `PerkSystemDirectTest` (direct) | DIRECT |
| END-04 | Reward system grants tokens/loot/achievements | `com/devmod/endurance/RewardSystem` | `RewardSystemDirectTest` (direct) | DIRECT |
| END-05 | Boss waves occur every 5 waves | `com/devmod/endurance/BossWaveSystem` | `BossWaveSystemDirectTest` (direct) | DIRECT |
| END-06 | Endurance gate blocks when arena templates disabled | `com/devmod/endurance/EnduranceQuestManager` | `EnduranceSmokeTests#gateBlocksWhenTemplateDisabled` (direct) | DIRECT |
| END-07 | Quest keybinds use F10/F11/F12 and \ | `com/devmod/client/input/Keybinds` | `KeybindSystemValidationTest#questSystemUsesContiguousKeys` (direct) | DIRECT |
| END-08 | Critical kill markers expire after window | `com/devmod/endurance/EnduranceEventCombat` | `EnduranceEventCombatTest` (direct) | DIRECT |

### Arena System (docs/areas/arena/README.md)

| Behavior ID | Statement | Code refs | Tests | Validation |
|---|---|---|---|---|
| ARENA-01 | Template registry loads and validates templates (schema rules) | `com/devmod/arena/registry/ArenaTemplateRegistry`, `TemplateLoader`, `SchemaValidator` | `TemplateLoaderTest`, `SchemaValidatorTest` (direct) | DIRECT |
| ARENA-02 | Hot reload rejects invalid inheritance depth/diamond | `com/devmod/arena/registry/ArenaTemplateRegistry` | `InheritanceResolutionTest` (direct) | DIRECT |
| ARENA-03 | Policy resolution picks highest weight and deterministic tie-breaks | `com/devmod/arena/policy/PolicyResolver` | `PolicyResolverWeightTest` (direct) | DIRECT |
| ARENA-04 | Template validation rejects bounds gaps (walls/ceiling) | `com/devmod/arena/registry/TemplateValidator` | `TemplateValidatorBoundsGapTest` (direct) | DIRECT |
| ARENA-05 | Spawn slots are validated for runtime constraints | `com/devmod/arena/registry/TemplateSpawnValidator` | `SpawnSlotRuntimeValidationTest`, `SpawnSlotConstraintsTest`, `RuntimeSpawnValidatorTest` (direct) | DIRECT |
| ARENA-06 | Build dry-run estimates block counts for templates | `com/devmod/arena/builder/BuildDryRunCalculator` | `BuildDryRunCalculatorTest` (direct) | DIRECT |
| ARENA-07 | Async builder pauses when MSPT is too high | `com/devmod/arena/builder/AsyncArenaBuilder` | `AsyncArenaBuilderPerformanceTest` (direct) | DIRECT |
| ARENA-08 | Fallback/rollback strategy handles failures with circuit breaker | `com/devmod/arena/fallback/FallbackBuildStrategy` | `RollbackTestScenario`, `FallbackBuildStrategyTest` (direct) | DIRECT |
| ARENA-09 | Autosmoke guard/runner/scheduler gate and report runs | `com/devmod/arena/autosmoke/*` | `AutosmokeGuardTest`, `AutosmokeRunnerTest`, `AutosmokeSchedulerTest` (direct) | DIRECT |

### Instance System (docs/areas/instance/README.md)

| Behavior ID | Statement | Code refs | Tests | Validation |
|---|---|---|---|---|
| INST-01 | Instance state transitions follow the allowed lifecycle graph | `com/devmod/runtime/InstanceState` | `InstanceStateDirectTest` (direct) | DIRECT |
| INST-02 | Player recovery states allow NORMAL fallback and enforce flow | `com/devmod/runtime/PlayerInstanceState` | `PlayerInstanceStateDirectTest` (direct) | DIRECT |
| INST-03 | InstanceData enforces capacity and schedules destruction when empty | `com/devmod/runtime/InstanceData` | `InstanceDataDirectTest` (direct) | DIRECT |
| INST-04 | Player snapshots round-trip via NBT and file IO | `com/devmod/runtime/PlayerInstanceSnapshot` | `PlayerInstanceSnapshotDirectTest` (direct) | DIRECT |
| INST-05 | Registry maps players and dimensions to instances | `com/devmod/runtime/InstanceRegistry` | `InstanceRegistryDirectTest` (direct) | DIRECT |

---

### Config System (docs/areas/config/README.md)

| Behavior ID | Statement | Code refs | Tests | Validation |
|---|---|---|---|---|
| CFG-01 | GameDesignConfig copy produces independent primitive configs | `com/devmod/config/gamedesign/GameDesignConfig` | `GameDesignConfigDirectTest` (direct) | DIRECT |
| CFG-02 | Instance overrides and presets apply expected fields | `com/devmod/config/gamedesign/InstanceOverride` | `InstanceOverrideDirectTest` (direct) | DIRECT |
| CFG-03 | Effective config uses global defaults or overrides without mutating global | `com/devmod/config/gamedesign/GameDesignConfigManager` | `GameDesignConfigManagerDirectTest` (direct) | DIRECT |
| CFG-04 | ModConfig cycles overlay color palette deterministically | `com/devmod/ModConfig` | `ModConfigDirectTest` (direct) | DIRECT |

---

### Testing Harness (docs/testing/TESTING.md, docs/testing/TEST_HARNESS.md)

| Behavior ID | Statement | Code refs | Tests | Validation |
|---|---|---|---|---|
| TEST-01 | Progress-based tests auto-start and auto-complete | `com/devmod/testing/TestCase` | `TestCaseDirectTest` (direct) | DIRECT |
| TEST-02 | Auto-validation failures capture error logs and mark failed | `com/devmod/testing/TestCase` | `TestCaseDirectTest` (direct) | DIRECT |
| TEST-03 | Auto-validation checks can mark validation without changing status | `com/devmod/testing/TestCase` | `TestCaseDirectTest` (direct) | DIRECT |

---

### Telemetry System (docs/areas/telemetry/README.md)

| Behavior ID | Statement | Code refs | Tests | Validation |
|---|---|---|---|---|
| TEL-01 | Telemetry JSON escaping handles quotes and control characters | `com/devmod/telemetry/TelemetryJson` | `TelemetryJsonDirectTest` (direct) | DIRECT |
| TEL-02 | Bit-packed flags pack/unpack movement state correctly | `com/devmod/telemetry/util/BitPackedFlags` | `BitPackedFlagsDirectTest` (direct) | DIRECT |
| TEL-03 | Room definitions default missing dimensions to overworld | `com/devmod/telemetry/RoomDefinition` | `RoomDefinitionDirectTest` (direct) | DIRECT |
| TEL-04 | Telemetry batch payload limits event count and decodes data | `com/devmod/telemetry/duckdb/packets/TelemetryBatchPayload` | `TelemetryBatchPayloadDirectTest` (direct) | DIRECT |

### Client/Server Boundary (docs/areas/client_server/README.md)

| Behavior ID | Statement | Code refs | Tests | Validation |
|---|---|---|---|---|
| CS-01 | Channel IDs have no collisions within a direction | `com/devmod/network/ChannelId` | `ChannelIdDirectTest` (direct) | DIRECT |
| CS-02 | Packet validation clamps values to configured bounds | `com/devmod/network/PacketValidator` | `PacketValidatorDirectTest` (direct) | DIRECT |
| CS-03 | Client hook dispatch is safe when hooks are absent | `com/devmod/network/NetworkHandler` | `NetworkHandlerDirectTest` (direct) | DIRECT |

### Radial Menu (docs/areas/radial/README.md)

| Behavior ID | Statement | Code refs | Tests | Validation |
|---|---|---|---|---|
| RAD-01 | Macro categories expose count/indexing and adjacency | `com/devmod/client/ui/radial/model/MacroCategory` | `MacroCategoryDirectTest` (direct) | DIRECT |
| RAD-02 | Categories create subcategory links and honor visibility gating | `com/devmod/client/ui/radial/RadialCategory`, `RadialMenuItem` | `RadialCategoryDirectTest` (direct) | DIRECT |
| RAD-03 | Toggle items flip state on execute | `com/devmod/client/ui/radial/RadialMenuItem`, `RadialAction` | `RadialCategoryDirectTest` (direct) | DIRECT |
| RAD-04 | Search ranks prefix/fuzzy matches and returns best result | `com/devmod/client/ui/radial/input/RadialSearchHandler` | `RadialSearchHandlerDirectTest` (direct) | DIRECT |

### Tools / QA / Autosmoke (docs/areas/tools/README.md)

| Behavior ID | Statement | Code refs | Tests | Validation |
|---|---|---|---|---|
| TOOL-01 | Thresholds resolve by override or naming conventions and validate limits | `com/devmod/arena/autosmoke/AutosmokeThresholds` | `AutosmokeThresholdsDirectTest` (direct) | DIRECT |
| TOOL-02 | Size thresholds categorize templates, enforce limits, and honor whitelists | `com/devmod/arena/autosmoke/AutosmokeSizeThresholds` | `AutosmokeSizeThresholdsDirectTest` (direct) | DIRECT |
| TOOL-03 | Report headers hash config content and render formatted output | `com/devmod/arena/autosmoke/AutosmokeReportHeader` | `AutosmokeReportHeaderDirectTest` (direct) | DIRECT |
| TOOL-04 | Autosmoke reports roll up metrics and export CSV/JSON | `com/devmod/arena/autosmoke/AutosmokeRunner` | `AutosmokeReportDirectTest` (direct) | DIRECT |

## Mapping Backlog
- Remaining docs in scope are not yet mapped; next passes will expand this matrix per subsystem and area.
