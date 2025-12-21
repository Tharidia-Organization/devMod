# TODO Agent 03 - Budget & Async (DD 11-12)

> DEPRECATED: task list archived; see `docs/arena-template-rework/TODO_AGENT_03_COMPLETE.md`.


## Parallel Agent Coordination
- **Agent ID**: 03
- **Role**: Budget Management & Async Build
- **Dependencies**: Agent 02 (Builder) for build integration
- **Outputs consumed by**: Agent 04 (Metriche), Agent 07 (Cleanup)
- **Shared resources**: `AsyncArenaBuilder.java`, `BuildBudget.java`

## Design Decisions Reference
- DD11: Budget Soft/Hard - WARN 80%, ERROR 100%
- DD12: Async Build - 500 blocks/tick, backpressure MSPT>40ms

## Tasks

### Core Implementation
- [ ] Implementare `BuildBudget` con soglie WARN 80% / ERROR 100%
- [ ] Implementare `BuildTimeoutException` e `BuildLimitExceededException`
- [ ] Implementare `AsyncArenaBuilder` con tick distribution
- [ ] Implementare backpressure basata su MSPT (threshold 40ms)
- [ ] Implementare gradual recovery rate dopo backpressure
- [ ] Registrare async builder in server tick event

### Files to Create/Modify
- `src/main/java/com/devmod/arena/builder/AsyncArenaBuilder.java`
- `src/main/java/com/devmod/arena/budget/BuildBudget.java`
- `src/main/java/com/devmod/arena/budget/BuildTimeoutException.java`
- `src/main/java/com/devmod/arena/budget/BackpressureManager.java`

### Unit Tests (Agent 12 will verify)
- [ ] Unit test budget WARN a 80%, ERROR a 100%
- [ ] Unit test async backpressure (MSPT > 40ms)

### Completion Signal
When done, create file: `TODO_AGENT_03_COMPLETE.md` with summary of changes.
