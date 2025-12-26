# Telemetry System

> **Audit Date**: 2025-12-26
> **Status**: CURRENT (code-aligned)
> **Risk Level**: MEDIUM (DuckDB IO + async batching)

---

## 1. Purpose

The Telemetry System captures gameplay analytics and exports them for analysis:

- **Primary storage**: DuckDB (server-side)
- **Optional fallback**: NDJSON (configurable)
- **Exports**: CSV, JSON, heatmaps
- **Dashboard**: HTTP server for analytics queries

---

## 2. Key Components

### Core
- `com.devmod.telemetry.TelemetryService`
- `com.devmod.telemetry.TelemetryEvents`
- `com.devmod.telemetry.TelemetryLogHandlers`

### DuckDB
- `com.devmod.telemetry.duckdb.DuckDBTelemetryService`
- `com.devmod.telemetry.duckdb.DuckDBSchemaManager`
- `com.devmod.telemetry.duckdb.DuckDBBatchWriter`
- `com.devmod.telemetry.duckdb.DuckDBQueryAPI`
- `com.devmod.telemetry.duckdb.DuckDBConfig`

### Network Batch
- `com.devmod.telemetry.duckdb.packets.TelemetryBatchPayload`
- `com.devmod.telemetry.duckdb.packets.TelemetryPacketHandler`

### Export
- `com.devmod.telemetry.export.CsvExporter`
- `com.devmod.telemetry.export.JsonReportExporter`
- `com.devmod.telemetry.export.HeatmapExporter`

### Dashboard
- `com.devmod.telemetry.dashboard.TelemetryDashboardServer`
- `com.devmod.telemetry.dashboard.TelemetryAnalyticsHandlers`

---

## 3. Entrypoints

### Commands
- `devmod telemetry <subcommand>` (reload, dump, export, scan)
- `devmod dashboard <start|stop|status|open>`

### Action Registry
Telemetry commands are also registered as actions via `ActionRegistry` (see `ActionIds.TELEMETRY_*`).

---

## 4. Data & Encoding

- DuckDB schema and migrations are defined in `DuckDBSchemaManager`.
- NDJSON fallback is controlled by `DuckDBConfig`.
- Compact flags use `BitPackedFlags`.
- JSON escaping uses `TelemetryJson`.

---

## 5. Automated Validation

| Behavior | Test |
|----------|------|
| JSON escaping | `TelemetryJsonDirectTest` |
| Bit-packed flags | `BitPackedFlagsDirectTest` |
| Room definition defaults | `RoomDefinitionDirectTest` |
| Telemetry batch limits + decoding | `TelemetryBatchPayloadDirectTest` |

---

## Cross-References

- `docs/telemetry/TELEMETRY_DOCUMENTATION.md`
- `docs/telemetry/MISSING_TELEMETRY_HOOKS.md`
- `docs/telemetry/dashboard/DASHBOARD_UPGRADE_PLAN.md`

