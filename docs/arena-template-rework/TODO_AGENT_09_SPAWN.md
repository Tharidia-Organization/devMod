# TODO Agent 09 - Rollback & Spawn (DD 44-50)

## Parallel Agent Coordination
- **Agent ID**: 09
- **Role**: Rollback Strategy, Spawn Slots, Heatmap
- **Dependencies**: Agent 02 (Builder) for rollback integration
- **Outputs consumed by**: Agent 10 (Gamification)
- **Shared resources**: `FallbackBuildStrategy.java`, `SpawnSlotResolver.java`

## Design Decisions Reference
- DD44: Rollback Staging Test - scenario obbligatorio pre-deploy
- DD45: Fallback Chain Limits - max 1 retry, circuit breaker 3/5min
- DD46: Default Fail Message - user-friendly, no tech details
- DD47: SpawnSlots Distance - melee 3-15, ranged 12-30, LOS+ground+forbidden
- DD48: SpawnSlotValidator Performance - O(n²) at load, O(1) runtime
- DD49: Heatmap Privacy - 5x5 cell, hourly bucket, no player ID
- DD50: Mutator Binding - SUGGESTED soft, EXCLUDED/REQUIRED hard

## Tasks

### Rollback & Fallback
- [ ] Creare `RollbackTestScenario` test class con 3 scenari
- [ ] Implementare staging checklist pre-deploy
- [ ] Implementare `FallbackBuildStrategy` con circuit breaker
- [ ] Implementare `CircuitBreaker` (threshold 3, window 5min, cooldown 30s)
- [ ] Implementare metriche dedicate fallback (PRIMARY_SUCCESS, FALLBACK_USED, ALL_FAILED)

### Failure Handling
- [ ] Implementare `ArenaFailureHandler` con player messages localizzabili
- [ ] Definire `PLAYER_MESSAGES` map per tutti i FailureType
- [ ] Implementare alert per failure critici

### Spawn Slots
- [ ] Implementare `SpawnSlotConstraints` record con distanze default
- [ ] Implementare `SpawnSlotResolver` con LOS check
- [ ] Implementare `hasLineOfSight()` con ClipContext
- [ ] Implementare `SpawnSlotValidator` con cache al load
- [ ] Implementare `ValidationCache` per O(1) lookup runtime
- [ ] Implementare `isPositionOccupied()` check leggero

### Heatmap
- [ ] Implementare `HeatmapCollector` con aggregazione 5x5
- [ ] Implementare flush batch ogni 5 minuti
- [ ] Implementare retention 30 giorni + aggregazione settimanale

### Mutator Binding
- [ ] Implementare `MutatorBinding` record con BindingType enum
- [ ] Implementare `PolicyMutatorResolver` con REQUIRED/EXCLUDED logic
- [ ] Implementare UI sorting per SUGGESTED

### Files to Create/Modify
- `src/main/java/com/devmod/arena/fallback/FallbackBuildStrategy.java`
- `src/main/java/com/devmod/arena/fallback/CircuitBreaker.java`
- `src/main/java/com/devmod/arena/failure/ArenaFailureHandler.java`
- `src/main/java/com/devmod/arena/spawn/SpawnSlotResolver.java`
- `src/main/java/com/devmod/arena/spawn/SpawnSlotValidator.java`
- `src/main/java/com/devmod/arena/analytics/HeatmapCollector.java`
- `src/main/java/com/devmod/arena/policy/MutatorBinding.java`

### Unit Tests (Agent 12 will verify)
- [ ] Staging test RollbackTestScenario 3 scenari pass
- [ ] Unit test FallbackBuildStrategy max 1 retry
- [ ] Unit test CircuitBreaker open dopo 3 failures
- [ ] Unit test CircuitBreaker cooldown 30 sec
- [ ] Unit test ArenaFailureHandler player message no tech details
- [ ] Unit test ArenaFailureHandler stack trace in log
- [ ] Unit test SpawnSlotConstraints distance validation
- [ ] Unit test SpawnSlotResolver LOS check
- [ ] Unit test SpawnSlotResolver forbidden zone rejection
- [ ] Unit test SpawnSlotValidator O(1) runtime lookup
- [ ] Unit test SpawnSlotValidator position occupied check
- [ ] Unit test HeatmapCollector 5x5 cell aggregation
- [ ] Unit test HeatmapCollector hourly bucket
- [ ] Unit test HeatmapCollector no player ID
- [ ] Unit test MutatorBinding SUGGESTED (soft, selectable)
- [ ] Unit test MutatorBinding EXCLUDED (hard block)
- [ ] Unit test MutatorBinding REQUIRED (always on)
- [ ] Unit test PolicyMutatorResolver REQUIRED auto-add
- [ ] Integration test fallback chain end-to-end
- [ ] Integration test staging rollback scenario

### Completion Signal
When done, create file: `TODO_AGENT_09_COMPLETE.md` with summary of changes.
