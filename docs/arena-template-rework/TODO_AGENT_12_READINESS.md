# TODO Agent 12 - Pool & Operational Readiness (DD 63-72)

## Parallel Agent Coordination
- **Agent ID**: 12
- **Role**: Pool (deferred), Migration Audit, Release Gates, KPIs
- **Dependencies**: ALL other agents (this is the final integration agent)
- **Outputs consumed by**: Release pipeline
- **Shared resources**: `RolloutEvaluator.java`, `release-gate.yml`

## Design Decisions Reference
- DD63: Prebuild Pool Deferral - DEFERRED, evaluate after 2 weeks telemetry
- DD64: Pool Cleanup Unused - state machine READY→RESERVED→IN_USE
- DD65: Pool Metrics Operational - hit/miss linked to feature flag, auto-disable >50%
- DD66: Migration Wrapper Detection - grep+AST+runtime telemetry
- DD67: Deprecation CI+Runtime - warning M1, -Werror M2, removal M3
- DD68: Monitoring 48h Runbook - soglie + ownership + escalation
- DD69: Dashboard Validation - automated job daily + manual checklist
- DD70: Security Release Gate - CI bloccante, 7 checks
- DD71: Template Obsolescence - versioned extends, session-safe deprecation
- DD72: Success Criteria KPIs - build_p95<5s, rollback<1%, completion>75%

## Tasks

### Pool (DEFERRED - Prepare for future)
- [ ] Documentare criteri decisione pool (build_p95>5s, quest_rate>0.5/min)
- [ ] Raccogliere telemetria 2 settimane pre-pool decision
- [ ] Implementare `PoolState` enum (BUILDING, READY, RESERVED, IN_USE, CLEANUP)
- [ ] Implementare `PooledArena` record con isUnused() e canBeEvicted()
- [ ] Implementare reserve() con state change atomico
- [ ] Implementare `PoolMetrics` con hit/miss tracking
- [ ] Implementare auto-disable pool su miss_rate > 50%

### Migration Audit
- [ ] Implementare `WrapperAnalyzer` per AST analysis
- [ ] Implementare runtime callerChain capture per legacy calls
- [ ] Creare task Gradle `migrationAudit`
- [ ] Configurare -Xlint:deprecation (M1)
- [ ] Pianificare -Werror (M2 +4 weeks)

### Monitoring & Runbook
- [ ] Implementare `AnomalyThresholds` record
- [ ] Creare runbook markdown per alert handling

### Dashboard Validation
- [ ] Implementare `DashboardValidationJob` daily 02:00
- [ ] Creare manual validation checklist

### Release Gate
- [ ] Creare workflow `release-gate.yml` con 7 checks

### Template Obsolescence
- [ ] Implementare `TemplateObsolescenceHandler`
- [ ] Implementare successor mapping config
- [ ] Implementare session-safe template removal

### Success Criteria & Rollout
- [ ] Implementare `RolloutSuccessCriteria` record
- [ ] Implementare `RolloutEvaluator` con canAdvance()
- [ ] Configurare dashboard per KPI tracking
- [ ] Definire go/no-go gates per ogni phase

### Files to Create/Modify
- `src/main/java/com/devmod/arena/pool/PoolState.java`
- `src/main/java/com/devmod/arena/pool/PooledArena.java`
- `src/main/java/com/devmod/arena/pool/PoolMetrics.java`
- `src/main/java/com/devmod/arena/migration/WrapperAnalyzer.java`
- `src/main/java/com/devmod/arena/monitoring/AnomalyThresholds.java`
- `src/main/java/com/devmod/arena/validation/DashboardValidationJob.java`
- `src/main/java/com/devmod/arena/obsolescence/TemplateObsolescenceHandler.java`
- `src/main/java/com/devmod/arena/rollout/RolloutSuccessCriteria.java`
- `src/main/java/com/devmod/arena/rollout/RolloutEvaluator.java`
- `.github/workflows/release-gate.yml`
- `docs/runbook/arena-alerts.md`

### Integration & Verification (CRITICAL)
This agent verifies ALL other agents' work:
- [ ] Verify Agent 01 completion (Registry & Resolver)
- [ ] Verify Agent 02 completion (Builder)
- [ ] Verify Agent 03 completion (Budget)
- [ ] Verify Agent 04 completion (Metriche)
- [ ] Verify Agent 05 completion (Observability)
- [ ] Verify Agent 06 completion (Identity)
- [ ] Verify Agent 07 completion (Operations)
- [ ] Verify Agent 08 completion (Cleanup)
- [ ] Verify Agent 09 completion (Spawn)
- [ ] Verify Agent 10 completion (Gamification)
- [ ] Verify Agent 11 completion (Telemetry)

### Unit Tests
- [ ] Unit test PoolState transitions (BUILDING→READY→RESERVED→IN_USE)
- [ ] Unit test PooledArena isUnused() (10 min threshold)
- [ ] Unit test PooledArena canBeEvicted() (only READY/BUILDING)
- [ ] Unit test reserve() state change atomico
- [ ] Unit test PoolMetrics hit/miss ratio calculation
- [ ] Unit test PoolMetrics auto-disable threshold (50%)
- [ ] Unit test PoolMetrics alert thresholds (20%, 30%)
- [ ] Unit test WrapperAnalyzer detect hidden calls
- [ ] Unit test runtime callerChain capture
- [ ] Unit test deprecation log rate limiting
- [ ] Unit test deprecation telemetry always emitted
- [ ] Unit test AnomalyThresholds bounds
- [ ] Unit test DashboardValidationJob row count match
- [ ] Unit test DashboardValidationJob aggregate match
- [ ] Unit test DashboardValidationJob temporal consistency
- [ ] Unit test release-gate workflow 7 checks
- [ ] Unit test TemplateObsolescenceHandler successor lookup
- [ ] Unit test TemplateObsolescenceHandler extends fallback
- [ ] Unit test session-safe removal (wait for active)
- [ ] Unit test RolloutSuccessCriteria defaults
- [ ] Unit test RolloutEvaluator canAdvance() gates
- [ ] Unit test RolloutPhase transitions
- [ ] Integration test pool lifecycle end-to-end
- [ ] Integration test migration audit full pipeline
- [ ] Integration test deprecation M1→M2→M3 timeline
- [ ] Integration test 48h monitoring dashboard
- [ ] Integration test release gate blocking PR
- [ ] Integration test template obsolescence with active sessions
- [ ] Integration test rollout phase advancement

### Completion Signal
When done, create file: `TODO_AGENT_12_COMPLETE.md` with summary.
This agent should be the LAST to complete as it verifies all others.
