# TODO Agent 04 - Metriche & API (DD 13-15)

> DEPRECATED: task list archived; see `docs/arena-template-rework/TODO_AGENT_04_COMPLETE.md`.


## Parallel Agent Coordination
- **Agent ID**: 04
- **Role**: Metrics Context & API Migration
- **Dependencies**: Agent 02 (Builder), Agent 03 (Budget)
- **Outputs consumed by**: Agent 05 (Observability), Agent 09 (Telemetry)
- **Shared resources**: `ArenaMetricsContext.java`, `ArenaHandle.java`

## Design Decisions Reference
- DD13: Metriche Obbligatorie - ArenaMetricsContext in tutti gli eventi
- DD14: API Compatibility - Legacy deprecato + prepareArenaForPartyV2
- DD15: ArenaHandle Audit - 7 call-site da migrare

## Tasks

### Core Implementation
- [ ] Implementare `ArenaMetricsContext` record
- [ ] Implementare `BuildTelemetry` wrapper con context obbligatorio
- [ ] Aggiornare tutti gli eventi arena.build.* con context completo
- [ ] Implementare `ResolveOptions` record
- [ ] Implementare `prepareArenaForPartyV2()` con nuovo return type
- [ ] Deprecare `prepareArenaForParty()` legacy
- [ ] Implementare `ArenaHandle` record completo

### Migration Tasks (7 call-site)
- [ ] Migrare `QuestStartSequence.prepareArena()` a ArenaHandle
- [ ] Migrare `EnduranceQuestManager.startPreparedQuest()` a ArenaHandle
- [ ] Migrare `InstanceArenaManager.startInstanceQuestForParty()` a ArenaHandle
- [ ] Migrare `WaveManager.spawnWave()` a handle.mobSpawnPositions()
- [ ] Migrare `EndurancePlayerStateManager.teleportToArena()` a handle.primaryPlayerSpawn()
- [ ] Creare `ArenaCleanupTask` con ArenaHandle
- [ ] Aggiornare `EnduranceTelemetryService` per estrarre context da handle

### Files to Create/Modify
- `src/main/java/com/devmod/arena/metrics/ArenaMetricsContext.java`
- `src/main/java/com/devmod/arena/metrics/BuildTelemetry.java`
- `src/main/java/com/devmod/arena/api/ArenaHandle.java`
- `src/main/java/com/devmod/arena/api/ResolveOptions.java`

### Unit Tests (Agent 12 will verify)
- [ ] Unit test ArenaMetricsContext in tutti gli eventi
- [ ] Integration test legacy API backward compat

### Completion Signal
When done, create file: `TODO_AGENT_04_COMPLETE.md` with summary of changes.
