# Telemetry Dashboard API

> Ultimo aggiornamento: 2026-01-31

Documento di riferimento per gli endpoint HTTP della dashboard telemetry locale.

## Base URL e avvio

- Server locale: `http://127.0.0.1:8642`
- Dashboard UI: `http://127.0.0.1:8642/dashboard`
- Comandi:
  - `devmod dashboard start`
  - `devmod dashboard stop`
  - `devmod dashboard status`

## Autenticazione

- La dashboard e' locale (bind su `127.0.0.1`).
- Gli endpoint arena analytics richiedono token.
  - Ottieni token: `GET /api/arena/token?user=local&full=true|false`
  - Passa token con header `Authorization: Bearer <token>`
    oppure query `?token=<token>`.
- Rate limit token: 60 richieste/minuto.

## Parametri comuni

- `range`: finestra analytics (default `24h`). Valori: `1h`, `6h`, `24h`, `7d`, `all`.
- `from`, `to`: timestamp ISO-8601 o epoch ms (arena analytics). Se assenti usa `range`.
- `limit`: limite record (varia per endpoint).
- `templateId`, `templateVersion`, `policyId`, `policyVersion`, `arenaId`: filtri arena.
- `player`: filtro player per analytics avanzate.

## Endpoint base

- `GET /api/health`
  - Stato DuckDB, writer stats, latency, sampling, connection metrics.

- `GET /api/summary`
  - Conteggi per tabella, attivita recente, size DB.

- `GET /api/tables`
  - Elenco tabelle DuckDB con size stimata.

- `POST /api/query`
  - Body JSON: `{ "sql": "SELECT ..." }`.
  - Solo query `SELECT`.

## Raw data (tabelle)

- `GET /api/combat/hits`
  - Params: `from`, `to`, `limit` (default 1000), filtri arena.

- `GET /api/combat/deaths`
  - Params: `from`, `to`, `limit` (default 500), filtri arena.

- `GET /api/combat/fights`
  - Params: `from`, `to`, `limit` (default 100), filtri arena.

- `GET /api/combat/weapons`
  - Params: `from`, `to`, filtri arena.

- `GET /api/endurance/sessions`
  - Params: `from`, `to`, `limit` (default 100), filtri arena.

- `GET /api/endurance/waves`
  - Params: `limit` (default 500), `session_id` opzionale, filtri arena.

- `GET /api/endurance/perks`
  - Se `templateId` presente: dati raw con filtri (limit 500).
  - Altrimenti: statistiche aggregate perk.

- `GET /api/endurance/performance`
  - Params: `from`, `to`, `limit` (default 200), filtri arena.

- `GET /api/endurance/leaderboard`
  - Params: `category`, `scope` (`global`, `weekly`, `arena`), `arenaId`, `limit`.

- `GET /api/endurance/leaderboard/categories`

- `GET /api/player/snapshots`
  - Params: `player_id` opzionale, `limit` (default 500).

- `GET /api/player/abilities`
  - Params: `player_id` opzionale, `limit` (default 500).

- `GET /api/spatial/heatmaps`
  - Params: `type`, `room`, `limit` (default 5000).

- `GET /api/spatial/transitions`
  - Params: `limit` (default 1000).

- `GET /api/economy/drops`
  - Params: `limit` (default 500).

- `GET /api/economy/kills`
  - Params: `limit` (default 50). Risposta aggregata per mob.

- `GET /api/dungeons/runs`
  - Params: `from`, `to`, `limit` (default 100).

- `GET /api/performance`
  - Params: `from`, `to`, `limit` (default 1000).

## Analytics (range-based)

Parametri comuni: `range`, filtri arena (`templateId`, `templateVersion`, `policyId`, `policyVersion`).

- `GET /api/analytics/overview`
- `GET /api/analytics/hits-timeline`
- `GET /api/analytics/damage-by-bodypart`
- `GET /api/analytics/damage-by-type`
- `GET /api/analytics/weapon-stats`
- `GET /api/analytics/mob-kills`
- `GET /api/analytics/ttk`
- `GET /api/analytics/accuracy-timeline`
- `GET /api/analytics/endurance-stats`
- `GET /api/analytics/dungeon-stats`
- `GET /api/analytics/room-stats`
- `GET /api/analytics/loot-rates`
- `GET /api/analytics/dps-timeline` (param opzionale `player`)
- `GET /api/analytics/player-stats` (param richiesto `player`)
- `GET /api/analytics/player-comparison`
- `GET /api/analytics/trends`
- `GET /api/analytics/performance`
- `GET /api/analytics/fight-analysis`
- `GET /api/analytics/damage-taken` (param opzionale `player`)
- `GET /api/analytics/players-list`

## Arena analytics (token required)

Parametri comuni:
- `token` (o header `Authorization`)
- `templateId` obbligatorio per build/perf/heatmap/wave
- `templateVersion` opzionale
- `from`/`to` o `range` (default 7d)
- `page`, `pageSize` (default 0/100, max 1000)

Endpoint:
- `GET /api/analytics/arena/templates`
- `GET /api/analytics/arena/build-metrics`
- `GET /api/analytics/arena/performance`
- `GET /api/analytics/arena/spawn-heatmap`
- `GET /api/analytics/arena/death-heatmap`
- `GET /api/analytics/arena/wave-correlation`
- `GET /api/analytics/arena/templates-failure-rate`

Export (richiede token con `canExport`):
- `GET /api/export/arena/build-metrics?format=csv|json`
- `GET /api/export/arena/performance?format=csv|json`
- `GET /api/export/arena/wave-correlation?format=csv|json`

## Esempi rapidi

```bash
curl http://127.0.0.1:8642/api/health
```

```bash
curl "http://127.0.0.1:8642/api/combat/hits?limit=200&templateId=default_flat_64"
```

```bash
curl "http://127.0.0.1:8642/api/analytics/player-stats?player=Erik&range=7d"
```

```bash
curl -X POST http://127.0.0.1:8642/api/query \
  -H "Content-Type: application/json" \
  -d '{"sql":"SELECT COUNT(*) AS hits FROM combat_hits"}'
```

```bash
TOKEN=$(curl -s "http://127.0.0.1:8642/api/arena/token?user=local&full=true" | jq -r .token)
curl "http://127.0.0.1:8642/api/analytics/arena/build-metrics?templateId=default_flat_64&token=$TOKEN"
```
