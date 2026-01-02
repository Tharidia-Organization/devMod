# DevMod Item Editors — Fase 0 Audit (current HEAD)

> Last updated: 2025-12-26

> Status: HISTORICAL (design system snapshot)
> Note: Alcuni issue elencati potrebbero essere stati risolti in commit successivi

Baseline commands run: `./gradlew tasks --all`, `./gradlew --version`, `./gradlew test` (all green).

## TODO / FIXME / XXX scan
| File | Line | Snippet | Severity | Proposed action |
|------|------|---------|----------|-----------------|
| EDITOR_IMPLEMENTATION_LOG.md | 248 | `buildPayload() returns null (network payloads TODO)` | Low / stale doc | Update log once code/doc converge |
| EDITOR_IMPLEMENTATION_LOG.md | 256 | `buildPayload() returns null (network payloads TODO)` | Low / stale doc | Same as above |
| docs/_deprecated/DEBUG_OVERLAY_SYSTEM.md | 11+ | Multiple `REQ-A* ... (TODO)` | Low / deprecated | Leave ignored (out of scope) |
| docs/_deprecated/PIANO_BODY_PART_HUD.md | 1+ | `// TODO: BC integration` | Low / deprecated | Leave ignored (out of scope) |
| docs/INSTANCE_SYSTEM_TEST_STRATEGY.md | 1+ | `BUG-XXX` placeholders | Low / template | None; template only |

## Feature status vs DoD

### Already OK
- **Baseline build**: Gradle tasks and `./gradlew test` pass; configuration cache works.
- **Dual-mode guardrails**: Default PREVIEW, F5 toggles; Ctrl+Enter quick-apply respects preview/dirty; dirty tracking disabled in PREVIEW; Apply short-circuits in PREVIEW or when clean.
- **Debug tab shell**: Both modules expose Debug tab with item identification, comparison list, session history entries, NBT dump of CustomData, and “Copy Debug” clipboard export.
- **MultiEdit UI**: Panel toggled with `M`, auto-fills inventory stacks of the same item type, preset dropdown is scrollable (8 visible) with failure summary/copy.
- **Preset/history overlays**: Preset search/sort/rename/delete, favorites list, and history overlay are mutually exclusive and consume input.

### Missing / Incomplete
- **Batch apply real effect**: `MultiEditManager` stores copies; `applyPresetToAll` mutates those copies via `ItemEditorPresetManager` and never updates player inventory or sends payloads—visible “success” does not change real items.
- **Preset scope/filtering**: Multi-edit dropdown shows all presets; `DataPreset.scope()` matches by substring/null-any, so scopes are overly permissive and can apply wrong presets once batch is fixed.
- **Debug “expected vs actual”**: `serverValue` is just the current CustomData; no vanilla/server/config baseline and `hasMismatch` simply compares against slider state. `hasCustomData` only checks CustomData presence, not `WeaponModStats`/`ArmorModStats` keys.
- **Session log coverage**: Only `markDirty`/`logEvent` entries appear; no entries for apply sent/ack/fail, slot switches, import/export, or undo/redo. Debug clipboard mirrors these gaps.
- **Data surface**: Debug NBT viewer shows only CustomData, not full stack tag count; copy/paste exports still only carry stat float lists (no enchants/components) despite design.
- **Spec gaps**: Item value analysis, crafting recipe view, and templates picker/preview are absent; MultiEdit failure list lacks virtualization but is short.

### Risky / Divergence
- **Preview mutates CustomData**: `applyPreview` writes stats into the item copy, so mismatches disappear after toggling modes even before a server roundtrip (though no packets are sent).
- **Batch scope ambiguity**: Substring scope matching can hit unrelated items (“sword” vs “crossbow”).
- **False success reporting**: MultiEdit “success” counts can mislead testers because underlying inventory is untouched.

## Tests observed
- Numerous integration/unit tests present; relevant editor tests limited to `MultiEditManagerTest` (copy-based logic) and `ItemEditorPresetManagerTest` (stat mapping to config managers). No tests for debug tab, dual-mode, or batch apply wiring.

## Plan (aligned to PR1–PR6)
- **PR1 – Doc/code alignment**: Fix EDITOR_DESIGN_SYSTEM.md contradictions (storage naming, MultiEdit reality, future features flagged).
- **PR2 – Debug tab hardening**: Real expected vs actual, session log coverage, readable NBT/custom data, clipboard usefulness, server/config placeholders clearly marked.
- **PR3 – MultiEdit preset selector UX**: Dropdown clarity/scroll, no overlap, filter by active type; keep within 550×420.
- **PR4 – Failure reporting**: Batch apply shows counts + expandable failure reasons + copy failures.
- **PR5 – Dual-mode hardening**: Enforce PREVIEW=no dirty/no packets, APPLY dirty-only apply, confirm dialogs, button state/labels, shortcuts (F5/Ctrl+Enter).
- **PR6 – Tests**: Unit tests for `ItemEditorPresetManager` mappings and `MultiEditManager.applyPresetToAll` success/failure paths (isolated from MC runtime).

DoD Fase 0: audit only (no code changes); this file fulfils the requirement.

## Suggested next steps
- Rework MultiEdit to operate on live inventory stacks (or emit network payloads) and filter presets by active module/item type; tighten `DataPreset.scope()` to explicit item IDs/tags.
- Introduce real expected/server comparison in debug: load vanilla baseline + server config to populate `serverValue`/`hasMismatch`; track `hasCustomData` on the specific stats keys.
- Extend session logging to include apply sent/ack/error, undo/redo, slot switches, import/export, and batch outcomes.
- Implement or stub value analysis/recipe view/templates per DoD, and expand presets/export to carry enchants/components, not just stat floats.
