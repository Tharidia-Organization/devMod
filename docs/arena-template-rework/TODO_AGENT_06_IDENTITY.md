# TODO Agent 06 - Identity & Recovery (DD 22-28)

## Parallel Agent Coordination
- **Agent ID**: 06
- **Role**: UUID Generation, Retention, Recovery
- **Dependencies**: Agent 05 (Observability) for NDJSON/DuckDB
- **Outputs consumed by**: Agent 09 (Telemetry), Agent 11 (Pool)
- **Shared resources**: `ArenaIdempotencyCache.java`, `RetentionJob.java`

## Design Decisions Reference
- DD22: UUID Generation - randomUUID + idempotency cache 5 min
- DD23: Retention Job - 04:00 daily, log separato
- DD24: Source of Truth - NDJSON append-only, DuckDB ricostruibile
- DD25: Snapshot Versioning - schemaVersion + migration chain
- DD26: Instance Naming - max 32 chars, [a-z0-9_], sanitization
- DD27: Recovery Template Missing - fallback default, no rebuild
- DD28: Tag Dictionary - enum predefiniti + autocomplete + typo detection

## Tasks

### Core Implementation
- [ ] Implementare `ArenaIdempotencyCache` con Caffeine (5 min TTL, 1000 max)
- [ ] Integrare idempotency cache in `ArenaService.build()`
- [ ] Implementare `RetentionJob` con scheduling 04:00 daily
- [ ] Implementare `NdjsonArchiver.archiveOlderThan()`
- [ ] Implementare `DuckDBCleaner.deleteOlderThan()`
- [ ] Configurare logger separato `arena.retention` per audit
- [ ] Implementare `DuckDBRecovery.rebuildFromNdjson()`
- [ ] Aggiungere `schemaVersion` a `ArenaSessionSnapshot`
- [ ] Implementare migration chain v1→v2 in snapshot
- [ ] Implementare `InstanceName` record con validazione
- [ ] Implementare `InstanceName.sanitize()` e `generate()`
- [ ] Implementare `ArenaRecoveryResult` sealed interface
- [ ] Implementare graceful fallback per template mancante in recovery
- [ ] Implementare `PredefinedTag` enum con 16 tag predefiniti
- [ ] Implementare `PredefinedTag.autocomplete()` e `findSimilar()`
- [ ] Implementare `TagValidationResult` con suggerimenti typo
- [ ] Aggiungere warning log per tag sconosciuti

### Files to Create/Modify
- `src/main/java/com/devmod/arena/identity/ArenaIdempotencyCache.java`
- `src/main/java/com/devmod/arena/retention/RetentionJob.java`
- `src/main/java/com/devmod/arena/retention/NdjsonArchiver.java`
- `src/main/java/com/devmod/arena/recovery/ArenaRecoveryResult.java`
- `src/main/java/com/devmod/arena/naming/InstanceName.java`
- `src/main/java/com/devmod/arena/tags/PredefinedTag.java`

### Unit Tests (Agent 12 will verify)
- [ ] Unit test idempotency cache (stesso requestId → stesso UUID)
- [ ] Unit test idempotency cache expiry (dopo 5 min → nuovo UUID)
- [ ] Unit test RetentionJob (archive + prune)
- [ ] Unit test DuckDB recovery from NDJSON
- [ ] Unit test snapshot migration chain (v1→v2)
- [ ] Unit test InstanceName sanitization
- [ ] Unit test InstanceName validation (reject invalid chars)
- [ ] Unit test recovery template missing → degraded result
- [ ] Unit test recovery version mismatch → warning + proceed
- [ ] Unit test PredefinedTag autocomplete
- [ ] Unit test PredefinedTag typo detection (Levenshtein ≤ 2)
- [ ] Unit test TagValidationResult suggestions

### Completion Signal
When done, create file: `TODO_AGENT_06_COMPLETE.md` with summary of changes.
