# TODO Agent 05 - Observability & Persistence (DD 16-21)

> DEPRECATED: task list archived; see `docs/arena-template-rework/TODO_AGENT_05_COMPLETE.md`.


## Parallel Agent Coordination
- **Agent ID**: 05
- **Role**: Logging, Persistence, DuckDB
- **Dependencies**: Agent 04 (Metriche) for context format
- **Outputs consumed by**: Agent 06 (Identity), Agent 09 (Telemetry)
- **Shared resources**: `NdjsonWriter.java`, DuckDB tables

## Design Decisions Reference
- DD16: Hot-Reload Session - Snapshot immutabile, version drift detection
- DD17: Log Rotation - 14 giorni, 500MB cap, .gz compression
- DD18: Stacktrace JSON - Array max 20 frames
- DD19: Alert Routing - Tutti i canali, retry per critici
- DD20: NDJSON Non-blocking - Buffer 10k, flush 100 righe/1s
- DD21: DuckDB Indici - 5 indici, query <200ms

## Tasks

### Core Implementation
- [ ] Implementare `ArenaTemplateSnapshot` record per session
- [ ] Implementare version drift detection a fine session
- [ ] Configurare log rotation (14 giorni, 500MB cap, .gz)
- [ ] Implementare `ErrorContext` record con stacktrace array
- [ ] Implementare `AlertRouter` con delivery su tutti i canali
- [ ] Implementare retry queue per canali critici (log, telemetry)
- [ ] Implementare `NdjsonWriter` async con buffer 10k
- [ ] Implementare flush policy (100 righe o 1 secondo)
- [ ] Creare tabelle DuckDB `arena_template_builds` e `arena_template_usage`
- [ ] Creare 5 indici per query dashboard
- [ ] Verificare performance query <200ms

### Files to Create/Modify
- `src/main/java/com/devmod/arena/snapshot/ArenaTemplateSnapshot.java`
- `src/main/java/com/devmod/arena/logging/NdjsonWriter.java`
- `src/main/java/com/devmod/arena/alert/AlertRouter.java`
- `src/main/java/com/devmod/arena/alert/ErrorContext.java`
- `resources/db/duckdb_schema.sql`

### Unit Tests (Agent 12 will verify)
- [ ] Unit test snapshot immutabile durante hot-reload
- [ ] Unit test AlertRouter delivery su tutti i canali
- [ ] Unit test NdjsonWriter non-blocking (buffer full → drop)
- [ ] Benchmark query DuckDB <200ms

### Completion Signal
When done, create file: `TODO_AGENT_05_COMPLETE.md` with summary of changes.
