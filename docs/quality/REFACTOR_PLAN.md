# Refactor Plan

> Last updated: 2025-12-26
> Status: PLANNING

## Goals
- Reduce method length and file complexity without behavior changes.
- Preserve logging order, side effects, and client/server boundaries.
- Keep refactors local and incremental with small, reviewable diffs.

## Targeted Refactors (Method Length)

### EnduranceEventHandler.onWaveComplete (P1)
Plan:
- Extract wave stat collection into a private helper that returns a small data carrier (styleRank, maxCombo, waveKills, waveDamage, waveDamageTaken).
- Extract tide/telemetry/logging emissions into private helpers to keep side effects grouped and ordering intact.
- Extract directive/chain handling into dedicated helpers to isolate narrative flows.
Guardrails:
- Preserve existing log messages, sequencing, and wave progression.
- Avoid changing conditional logic; only extract, do not reorder.

### EnduranceEventHandler.onQuestEnd (P2)
Plan:
- Extract analytics/telemetry recording into a helper to reduce method length.
- Extract quest result assembly into a helper adjacent to buildQuestResult to keep data flow clear.
Guardrails:
- Keep quest outcome/tide handling order unchanged.

### NetworkHandler.register (P1)
Plan:
- Split registration into private helpers: registerPayloadTypes, registerServerHandlers, registerClientHandlers.
- Replace repeated payload registrations with a small static table where possible (type, handler, dist).
Guardrails:
- Maintain current registration order and side effects.
- Keep dist guards and client hook routing intact.

## Targeted Refactors (File Length >600 LOC)

### EnduranceQuestManager (P2)
Plan:
- Extract quest lifecycle steps (start, advance, end, abandon) into private helpers grouped by phase.
- Move quest state mutations into a nested helper to keep invariants centralized.
Guardrails:
- Preserve threading expectations and tick timing.

### ArenaBuilder (P2)
Plan:
- Extract validation and data normalization into helpers.
- Separate arena assembly from serialization/deserialization helpers.
Guardrails:
- Keep builder output byte-for-byte identical for the same inputs.

### ItemEditorScreen (P2)
Plan:
- Extract UI sections (layout, widgets, event hooks) into private helpers.
- Isolate data binding vs. rendering logic to reduce interleaving.
Guardrails:
- No UI behavior changes or widget ordering changes.

## Additional Long Methods (Telemetry)

### DuckDBSchemaManager.migrateSchema (P2)
Plan:
- Extract per-version migration steps into helpers or a map of version -> runnable.
Guardrails:
- Preserve exact schema operations and logging.

### DuckDBBatchWriter.flushTableUnlocked (P2)
Plan:
- Extract batch assembly and statement execution into helpers.
Guardrails:
- Preserve ordering and error handling semantics.
