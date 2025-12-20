# TODO Agent 11 - Telemetry & Concurrency (DD 57-62)

## Parallel Agent Coordination
- **Agent ID**: 11
- **Role**: Telemetry Audit, Rate Limiting, Lock Management
- **Dependencies**: Agent 05 (Observability) for AlertRouter
- **Outputs consumed by**: Agent 12 (Pool & Readiness)
- **Shared resources**: `TelemetryAuditJob.java`, `ArenaBuildRateLimiter.java`

## Design Decisions Reference
- DD57: Telemetry Propagation Audit - 12 sub-services, CI check eventi orfani
- DD58: Room ID Uniqueness - arenaId immutabile, sessionId per reconnect
- DD59: Balance Report Job - settimanale Dom 06:00, <30s, JSON+Slack
- DD60: Lock Map Cleanup - scheduled cleanup 5min, no leak
- DD61: Rate Limit 4th Build - queue max 10, timeout 60s, reject retry-after
- DD62: Telemetry Contention - waitTimeMs + templateId per bottleneck

## Tasks

### Telemetry Audit
- [ ] Implementare `TelemetryAuditJob` con scheduled daily 05:00
- [ ] Implementare query orphan events (campi mancanti)
- [ ] Implementare sub-service coverage check (12 services)
- [ ] Aggiungere CI grep per emit() senza context

### Session Identity
- [ ] Implementare `ArenaIdentity` record con roomId()
- [ ] Implementare `SessionReconnectHandler` con sessionId separato
- [ ] Implementare `ReconnectState` tracking

### Balance Report
- [ ] Implementare `BalanceReportJob` scheduled Dom 06:00
- [ ] Implementare query templateStats, perkStats, outliers
- [ ] Implementare Slack notifier per balance report

### Lock Management
- [ ] Implementare `TemplateLockManager` con cleanup scheduled 5min
- [ ] Implementare `TemplateLock` record con isExpired()
- [ ] Implementare shutdown hook per cleanup executor

### Rate Limiting
- [ ] Implementare `ArenaBuildRateLimiter` con Semaphore(3)
- [ ] Implementare queue max 10 con timeout 60s
- [ ] Implementare `BuildPermit` sealed interface (Granted/Rejected)
- [ ] Implementare retry-after header per rejected builds

### Contention Telemetry
- [ ] Implementare `ArenaBuildTelemetry` con waitTimeMs
- [ ] Implementare emitContention() per high wait detection
- [ ] Creare dashboard query bottleneck (avg_wait, p95_wait)

### Files to Create/Modify
- `src/main/java/com/devmod/arena/telemetry/TelemetryAuditJob.java`
- `src/main/java/com/devmod/arena/identity/ArenaIdentity.java`
- `src/main/java/com/devmod/arena/identity/SessionReconnectHandler.java`
- `src/main/java/com/devmod/arena/report/BalanceReportJob.java`
- `src/main/java/com/devmod/arena/concurrency/TemplateLockManager.java`
- `src/main/java/com/devmod/arena/concurrency/ArenaBuildRateLimiter.java`
- `src/main/java/com/devmod/arena/concurrency/BuildPermit.java`
- `src/main/java/com/devmod/arena/telemetry/ArenaBuildTelemetry.java`

### Unit Tests (Agent 12 will verify)
- [ ] Unit test TelemetryAuditJob orphan detection
- [ ] Unit test TelemetryAuditJob sub-service coverage (12 services)
- [ ] Unit test TelemetryAuditJob alert on missing fields
- [ ] Unit test ArenaIdentity roomId() immutability
- [ ] Unit test SessionReconnectHandler sessionId uniqueness
- [ ] Unit test SessionReconnectHandler reconnectCount increment
- [ ] Unit test BalanceReportJob query timeout (30s)
- [ ] Unit test BalanceReportJob outlier detection (<30%, >70%)
- [ ] Unit test BalanceReportJob JSON persistence
- [ ] Unit test TemplateLockManager acquire/release
- [ ] Unit test TemplateLockManager expiry (30s)
- [ ] Unit test TemplateLockManager cleanup job (5min)
- [ ] Unit test TemplateLockManager no leak (dynamic templates)
- [ ] Unit test ArenaBuildRateLimiter concurrent limit (3)
- [ ] Unit test ArenaBuildRateLimiter queue limit (10)
- [ ] Unit test ArenaBuildRateLimiter timeout (60s)
- [ ] Unit test ArenaBuildRateLimiter reject retry-after
- [ ] Unit test BuildPermit sealed interface
- [ ] Unit test ArenaBuildTelemetry waitTimeMs included
- [ ] Unit test ArenaBuildTelemetry contention detection (>2 waiting)
- [ ] Unit test ArenaBuildTelemetry severity levels
- [ ] Integration test telemetry audit end-to-end
- [ ] Integration test rate limiter under load (13 concurrent requests)
- [ ] Integration test lock cleanup after server restart

### Completion Signal
When done, create file: `TODO_AGENT_11_COMPLETE.md` with summary of changes.
