# TODO Agent 01 - Registry & Resolver (DD 1-6)

## Parallel Agent Coordination
- **Agent ID**: 01
- **Role**: Registry & Resolver Implementation
- **Dependencies**: None (can start immediately)
- **Outputs consumed by**: Agent 02 (Builder), Agent 03 (Budget)
- **Shared resources**: `ArenaTemplateRegistry.java`, `PolicyResolver.java`

## Design Decisions Reference
- DD1: Version Handling - Last Wins
- DD2: Inheritance Resolution - On Load with Caching
- DD3: Tie-Break Rule - Deterministic (score→version→id)
- DD4: Weight Taratura - Telemetry Plan
- DD5: Override Scope - Session-based with Cleanup
- DD6: Concurrency - Lock per Player with Timeout

## Tasks

### Core Implementation
- [ ] Implementare version handling in `ArenaTemplateRegistry.load()`
- [ ] Implementare inheritance resolution on-load con caching
- [ ] Implementare tie-break rule in `PolicyResolver`
- [ ] Implementare telemetria per weight taratura
- [ ] Implementare `TemplateOverride` record e `OverrideManager`
- [ ] Implementare session cleanup hooks
- [ ] Implementare lock per player con timeout in `PolicyResolver`
- [ ] Implementare lock cleanup scheduled task

### Files to Create/Modify
- `src/main/java/com/devmod/arena/registry/ArenaTemplateRegistry.java`
- `src/main/java/com/devmod/arena/policy/PolicyResolver.java`
- `src/main/java/com/devmod/arena/override/TemplateOverride.java`
- `src/main/java/com/devmod/arena/override/OverrideManager.java`

### Unit Tests (Agent 12 will verify)
- [ ] Unit test version handling (last-wins)
- [ ] Unit test inheritance caching
- [ ] Unit test tie-break deterministico

### Completion Signal
When done, create file: `TODO_AGENT_01_COMPLETE.md` with summary of changes.
