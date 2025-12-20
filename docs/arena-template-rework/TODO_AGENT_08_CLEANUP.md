# TODO Agent 08 - Cleanup & Migration (DD 37-43)

## Parallel Agent Coordination
- **Agent ID**: 08
- **Role**: Cleanup Executor, Legacy Migration, CI Checks
- **Dependencies**: Agent 02 (Builder) for build context
- **Outputs consumed by**: Agent 10 (Operational Readiness)
- **Shared resources**: `ArenaCleanupExecutor.java`, `MsptMonitor.java`

## Design Decisions Reference
- DD37: Cleanup Robusto - 4 fasi: entità→blockEntities→scheduledTicks→blocchi
- DD38: Monitor MSPT - baseline pre-build, sliding window, confidence score
- DD39: Progress Overlay - rate limit 4Hz, delta min 1%
- DD40: Edge Cases Test - failure mid-build, chunk timeout, malformed template
- DD41: Coverage Policy - 80% core, 60% MC-dependent, 50% network/UI
- DD42: Migration Inventory - 12 call-site, 6 PR plan
- DD43: Zero Legacy Gate - CI grep + runtime deprecation warning

## Tasks

### Cleanup Implementation
- [ ] Implementare `ArenaCleanupExecutor` con 4 fasi
- [ ] Implementare `CleanupResult` record con contatori e warnings
- [ ] Implementare cleanup scheduled ticks (LevelTicks access)
- [ ] Implementare `CleanupVerification` post-cleanup

### MSPT Monitor
- [ ] Implementare `MsptMonitor` con baseline capture
- [ ] Implementare sliding window (100 samples, 5 sec)
- [ ] Implementare `MsptSample.shouldBackpressure()` con confidence

### Progress Overlay
- [ ] Implementare `BuildProgressOverlay` con rate limit 4 Hz
- [ ] Implementare `BuildProgressPacket` (28 bytes)
- [ ] Implementare client-side `BuildProgressHud`

### Edge Cases & Testing
- [ ] Creare test suite `ArenaEdgeCaseTests` con seed fisso
- [ ] Implementare test failure mid-build + rollback verify
- [ ] Implementare test chunk timeout + instance close
- [ ] Implementare test malformed template parameterized
- [ ] Implementare test 2 party concurrent
- [ ] Configurare JaCoCo coverage rules (80%/60%/50%)
- [ ] Creare `MinecraftMockExtension` per unit test

### Migration
- [ ] Documentare 12 call-site inventory
- [ ] Creare branch per 6 PR migration plan
- [ ] Aggiungere CI workflow `legacy-check.yml`
- [ ] Aggiungere `@Deprecated` a `ArenaManager.createArena()`
- [ ] Implementare runtime telemetry per legacy calls

### Files to Create/Modify
- `src/main/java/com/devmod/arena/cleanup/ArenaCleanupExecutor.java`
- `src/main/java/com/devmod/arena/cleanup/CleanupResult.java`
- `src/main/java/com/devmod/arena/monitor/MsptMonitor.java`
- `src/main/java/com/devmod/arena/ui/BuildProgressOverlay.java`
- `.github/workflows/legacy-check.yml`

### Unit Tests (Agent 12 will verify)
- [ ] Unit test CleanupExecutor 4 fasi ordine corretto
- [ ] Unit test CleanupResult.isComplete() (warnings empty)
- [ ] Unit test cleanup Container.clearContent() pre-remove
- [ ] Unit test MsptMonitor baseline capture (mediana)
- [ ] Unit test MsptMonitor confidence score
- [ ] Unit test MsptSample.shouldBackpressure() threshold
- [ ] Unit test BuildProgressOverlay rate limit (skip < 250ms)
- [ ] Unit test BuildProgressOverlay delta skip (< 1%)
- [ ] Unit test edge case failure mid-build rollback
- [ ] Unit test edge case chunk timeout → instance closed
- [ ] Unit test edge case malformed template rejection
- [ ] Unit test edge case 2 party no interference
- [ ] Unit test edge case same player blocked
- [ ] Integration test cleanup completo (entity+blockEntity+ticks+blocks)
- [ ] Benchmark cleanup 10k blocks duration

### Completion Signal
When done, create file: `TODO_AGENT_08_COMPLETE.md` with summary of changes.
