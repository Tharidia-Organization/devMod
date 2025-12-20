# TODO Agent 07 - Operations & Security (DD 29-36)

## Parallel Agent Coordination
- **Agent ID**: 07
- **Role**: Security, Permissions, Dashboard Auth
- **Dependencies**: Agent 05 (Observability) for AlertRouter
- **Outputs consumed by**: Agent 10 (Operational Readiness)
- **Shared resources**: `ArenaCommandPermissions.java`, `ArenaDashboardEndpoint.java`

## Design Decisions Reference
- DD29: forceTemplateId Persistence - session state + capability per relog
- DD30: HUD Visibility - permission + toggle esplicito
- DD31: Command Permissions - modello granulare + audit log
- DD32: Autosmoke Production Guard - triple guard ENV+flag+file
- DD33: Autosmoke Assert Exceptions - soglie per size + whitelist
- DD34: Report Export Context - header con git commit, config hash
- DD35: Dashboard Auth - token + cache + background refresh
- DD36: Analytics Query Limits - 30 giorni max, pagination, timeout 10s

## Tasks

### Core Implementation
- [ ] Implementare `TemplateOverrideManager` con session state + capability
- [ ] Registrare `TEMPLATE_OVERRIDE_CAP` capability
- [ ] Implementare `ArenaDebugHud` con permission check
- [ ] Implementare `ArenaDebugState.isHudEnabled()` toggle
- [ ] Implementare `ArenaCommandPermissions` con 7 permission levels
- [ ] Implementare `ArenaCommandAudit.log()` per comandi mutanti
- [ ] Configurare logger separato `arena.audit`
- [ ] Implementare `AutosmokeGuard.canRun()` con triple check
- [ ] Creare `.production` marker file in prod deployment
- [ ] Implementare `AutosmokeThresholds` record con STRICT/LARGE/ASYNC
- [ ] Implementare `AutosmokeExceptions` whitelist
- [ ] Implementare `AutosmokeReportHeader.capture()`
- [ ] Aggiungere git commit/branch in build properties
- [ ] Implementare `ArenaDashboardEndpoint` con auth middleware
- [ ] Implementare rate limiter per dashboard (60 req/min)
- [ ] Implementare metrics cache con background refresh (5 min)
- [ ] Implementare `AnalyticsQueryParams` con validation
- [ ] Implementare query timeout (10 sec)
- [ ] Implementare export job asincrono per query > 30 giorni

### Files to Create/Modify
- `src/main/java/com/devmod/arena/security/ArenaCommandPermissions.java`
- `src/main/java/com/devmod/arena/security/ArenaCommandAudit.java`
- `src/main/java/com/devmod/arena/hud/ArenaDebugHud.java`
- `src/main/java/com/devmod/arena/autosmoke/AutosmokeGuard.java`
- `src/main/java/com/devmod/arena/dashboard/ArenaDashboardEndpoint.java`
- `src/main/java/com/devmod/arena/analytics/AnalyticsQueryParams.java`

### Unit Tests (Agent 12 will verify)
- [ ] Unit test TemplateOverrideManager session state
- [ ] Unit test TemplateOverrideManager capability persistence (relog)
- [ ] Unit test TemplateOverride expiry (TTL)
- [ ] Unit test ArenaDebugHud permission check (block senza permission)
- [ ] Unit test ArenaDebugHud toggle (default OFF)
- [ ] Unit test ArenaCommandAudit log format
- [ ] Unit test AutosmokeGuard ENV check
- [ ] Unit test AutosmokeGuard feature flag check
- [ ] Unit test AutosmokeGuard .production marker check
- [ ] Unit test AutosmokeThresholds.forTemplate() selection
- [ ] Unit test AutosmokeExceptions whitelist lookup
- [ ] Unit test AutosmokeReportHeader capture
- [ ] Unit test ArenaDashboardEndpoint auth (401 senza token)
- [ ] Unit test ArenaDashboardEndpoint rate limit (429)
- [ ] Unit test AnalyticsQueryParams validation
- [ ] Unit test AnalyticsService timeout (10 sec)

### Completion Signal
When done, create file: `TODO_AGENT_07_COMPLETE.md` with summary of changes.
