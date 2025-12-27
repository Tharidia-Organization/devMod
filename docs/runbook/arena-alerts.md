# Arena Alerts Runbook (DD68)

> Last updated: 2025-12-26
> Status: CURRENT (verified against code)

## Overview
This runbook covers the arena alert pipeline built around `AlertRouter` and telemetry events.
Alerts are emitted by arena subsystems and routed to logs, telemetry, and optional webhooks.

## Alert Pipeline (Current Behavior)

`ArenaCommandEvents.ensureAlertRouter()` initializes the router during `/arena` command registration.
Default channels are:
- **Console**: `ConsoleAlertChannel`
- **Log**: `LogAlertChannel` (logger name: `arena.alerts`)
- **Telemetry**: `TelemetryAlertChannel` (event name: `arena.alert`)
- **DuckDB**: `DuckDbAlertRecorder` (records delivery history)

Optional channels:
- `DEVMOD_ARENA_ALERT_WEBHOOK_URL` (+ optional `DEVMOD_ARENA_ALERT_WEBHOOK_AUTH`)
- `DEVMOD_ARENA_ALERT_DISCORD_WEBHOOK_URL`

## Where Alerts Appear

- Logs: `arena.alerts` logger and server console output.
- NDJSON: `run/arena-telemetry_YYYY-MM-DD.ndjson` (event `arena.alert`).
- DuckDB: alert delivery records persisted by `DuckDbAlertRecorder`.

## Alert Types and Thresholds

### Build outcome rates (24h window)
Source: `BuildOutcomeMonitor` (invoked by `ArenaBuilder`).

- **Failure rate** defaults: warn `>= 5%`, error `>= 15%`
- **Rollback rate** defaults: warn `>= 2%`, error `>= 10%`
- **Cooldown**: 10 minutes between alerts

Config overrides (env or system properties via `ArenaTemplateConfig`):
- `DEVMOD_ARENA_WARN_FAILURE_RATE` / `devmod.arena.warnFailureRate`
- `DEVMOD_ARENA_ERROR_FAILURE_RATE` / `devmod.arena.errorFailureRate`
- `DEVMOD_ARENA_WARN_ROLLBACK_RATE` / `devmod.arena.warnRollbackRate`
- `DEVMOD_ARENA_ERROR_ROLLBACK_RATE` / `devmod.arena.errorRollbackRate`

### Autosmoke failures
Source: `AutosmokeScheduler` -> `routeFailureAlert()`.

- Severity: ERROR (or CRITICAL when failed templates > 5)
- Metadata includes failed template IDs (up to 10), mod version, git commit, duration

### Cleanup residuals
Source: `CleanupResidualChecker` (telemetry only).

- Emits `arena.cleanup.residual_alert` when residuals exceed thresholds
- Defaults in `ArenaTemplateConfig.AlertThresholds`: entities > 0 warn, > 5 error; blocks > 0 warn, > 10 error

### Prebuild pool miss rate
Source: `PrebuildPoolManager` (logs + telemetry).

- Warn: miss rate > 20% (`arena.pool.metrics` + INFO log)
- Critical: miss rate > 30% (`arena.pool.critical_miss_rate` + WARN log)
- Auto-disable: miss rate > 50% for 3 consecutive checks (`arena.pool.auto_disabled`)

## Response Checklist (General)

1. Identify alert source from `errorType` and `component` in `arena.alert`.
2. Use metadata (`templateId`, `arenaId`, `window_hours`) to scope impact.
3. Correlate with build telemetry (`arena.build.*`) and cleanup events as needed.
4. Apply mitigations (disable template, adjust limits, pause autosmoke, inspect pool).
5. Confirm recovery by watching follow-up telemetry or the absence of new alerts.
