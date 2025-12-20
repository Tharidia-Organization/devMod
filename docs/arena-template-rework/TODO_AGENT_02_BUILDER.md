# TODO Agent 02 - Builder Transazionale (DD 7-10)

## Parallel Agent Coordination
- **Agent ID**: 02
- **Role**: Transactional Builder Implementation
- **Dependencies**: Agent 01 (Registry) for template resolution
- **Outputs consumed by**: Agent 03 (Budget), Agent 04 (Metriche)
- **Shared resources**: `ArenaBuilder.java`, `BuildTransaction.java`

## Design Decisions Reference
- DD7: Transactional Build - BlockChange tracking, rollback reverse
- DD8: Memory Safety - Hard cap 150k, CompactBlockTracker, NBT streaming
- DD9: Chunk Loading - Polling FULL status, failure sequence
- DD10: Dry-Run Estimation - Euristica + DuckDB P75

## Tasks

### Core Implementation
- [ ] Implementare `BuildTransaction` con tracking blocchi/entità/chunks
- [ ] Implementare `CompactBlockTracker` con `LongArrayList`
- [ ] Implementare hard cap 150k blocchi con `BuildLimitExceededException`
- [ ] Implementare NBT streaming con callback per tracking
- [ ] Implementare `ensureChunksLoaded()` con polling FULL status
- [ ] Implementare rollback completo su failure (blocchi reverse + entità + chunks)
- [ ] Implementare chiusura istanza su build failure
- [ ] Implementare `estimateBuildTimeMs()` euristica
- [ ] Implementare `estimateFromHistory()` con DuckDB P75
- [ ] Implementare feedback loop accuratezza stima

### Files to Create/Modify
- `src/main/java/com/devmod/arena/builder/ArenaBuilder.java`
- `src/main/java/com/devmod/arena/builder/BuildTransaction.java`
- `src/main/java/com/devmod/arena/builder/CompactBlockTracker.java`
- `src/main/java/com/devmod/arena/builder/BuildLimitExceededException.java`
- `src/main/java/com/devmod/arena/builder/ChunkLoadingManager.java`

### Unit Tests (Agent 12 will verify)
- [ ] Unit test rollback completo (100 blocchi, failure a metà)
- [ ] Unit test memory limit (150k blocchi)
- [ ] Unit test chunk timeout → rollback + cleanup

### Completion Signal
When done, create file: `TODO_AGENT_02_COMPLETE.md` with summary of changes.
