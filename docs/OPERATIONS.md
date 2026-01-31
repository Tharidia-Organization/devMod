# Operations e Tooling

> Ultimo aggiornamento: 2026-01-31

## Telemetry

- NDJSON: `run/telemetry/`
- DuckDB: schema in `src/main/resources/db/duckdb_schema.sql`
- Export: `run/telemetry/exports/` (PNG heatmap), `run/telemetry/csv/`, `run/telemetry/reports/` (se abilitato)

## Dashboard Telemetry

Comandi:

- `devmod dashboard` (apre dashboard)
- `devmod dashboard start`
- `devmod dashboard stop`
- `devmod dashboard status`

Endpoint:

- Base URL: `http://127.0.0.1:8642/dashboard`
- API: vedere `docs/TELEMETRY_DASHBOARD_API.md`

## Admin Panel (Mailbox)

- Frontend: `admin-panel/` (React + Vite)
- Start rapido: `./gradlew startAdminPanel`
- Manuale: `cd admin-panel && npm install && npm run dev`
- API base URL: `http://127.0.0.1:8765`
- Spec completa: `docs/MAILBOX_ADMIN_API.md`

## Log

- Log runtime e output: `run/` e `logs/` (se attivi).
