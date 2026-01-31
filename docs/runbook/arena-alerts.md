# Arena Alerts Runbook (DD68)

> Ultimo aggiornamento: 2026-01-31
> Stato: CURRENT (verificato su codice)

## Overview

Questo runbook copre la pipeline degli alert arena basata su `AlertRouter` e telemetria.
Gli alert sono emessi dai sottosistemi arena e instradati a log, NDJSON, DuckDB e mailbox admin
(con canali opzionali verso webhook esterni).

## Alert Pipeline (Current Behavior)

`ArenaCommandEvents.ensureAlertRouter()` inizializza il router durante la registrazione dei comandi `/arena`.

Canali default:
- **Console**: `ConsoleAlertChannel`
- **Log**: `LogAlertChannel` (logger `arena.alerts`)
- **Telemetry**: `TelemetryAlertChannel` (evento `arena.alert`)
- **Mailbox**: `MailboxAlertChannel` (template `system.admin_alert`)
- **DuckDB**: `DuckDbAlertRecorder` (storico delivery)

Canali opzionali (env):
- `DEVMOD_ARENA_ALERT_WEBHOOK_URL` (+ `DEVMOD_ARENA_ALERT_WEBHOOK_AUTH`)
- `DEVMOD_ARENA_ALERT_DISCORD_WEBHOOK_URL`

Nota: `AutosmokeScheduler` usa l'`AlertRouter` per notificare fallimenti.

## Where Alerts Appear

- Logs: logger `arena.alerts` + console server.
- NDJSON: `run/arena-telemetry_YYYY-MM-DD.ndjson` (event `arena.alert`).
- DuckDB: record delivery in `DuckDbAlertRecorder`.
- Mailbox: alert persistenti via mailbox admin (template `system.admin_alert`).
- Webhook/Discord: se configurati via env.

## Alert Types and Thresholds

### Build outcome rates (24h window)
Source: `BuildOutcomeMonitor` (invocato da `ArenaBuilder`).

- **Failure rate** defaults: warn `>= 5%`, error `>= 15%`
- **Rollback rate** defaults: warn `>= 2%`, error `>= 10%`
- **Cooldown**: 10 minuti tra alert

Override config (env o system properties in `ArenaTemplateConfig`):
- `DEVMOD_ARENA_WARN_FAILURE_RATE` / `devmod.arena.warnFailureRate`
- `DEVMOD_ARENA_ERROR_FAILURE_RATE` / `devmod.arena.errorFailureRate`
- `DEVMOD_ARENA_WARN_ROLLBACK_RATE` / `devmod.arena.warnRollbackRate`
- `DEVMOD_ARENA_ERROR_ROLLBACK_RATE` / `devmod.arena.errorRollbackRate`

### Autosmoke failures
Source: `AutosmokeScheduler` -> `routeFailureAlert()`.

- Severity: ERROR (o CRITICAL quando `failedCount > 5`)
- Metadata: template falliti (fino a 10), mod version, git commit, durata

### Cleanup residuals
Source: rimosso (legacy `CleanupResidualChecker`).

- Nessun alert attivo

### Prebuild pool miss rate
Source: `PrebuildPoolManager` (log + telemetry).

- Warn: miss rate > 20% (`arena.pool.metrics` + INFO log)
- Critical: miss rate > 30% (`arena.pool.critical_miss_rate` + WARN log)
- Auto-disable: miss rate > 50% per 3 check consecutivi (`arena.pool.auto_disabled`)

## Response Checklist (General)

1. Identifica `errorType` e `component` in `arena.alert`.
2. Usa metadata (`templateId`, `arenaId`, `window_hours`) per delimitare l'impatto.
3. Correlare con telemetria build (`arena.build.*`) e cleanup.
4. Mitigare (disabilita template, riduci limiti, pausa autosmoke, controlla pool).
5. Verificare recovery osservando la telemetria o assenza di nuovi alert.
