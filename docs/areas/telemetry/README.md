# Telemetry System

> Last updated: 2025-12-26
> Status: CURRENT (verified against code)

The Telemetry system captures gameplay analytics, persists them to DuckDB, and exposes exports and dashboards.

## Scope

- Event capture and async writing (`TelemetryService`, `AsyncTelemetryWriter`, `TelemetryEvents`)
- Domain trackers (player, combat, damage, progression, spatial, boss, dungeon, economy)
- DuckDB pipeline (connection, schema, migrations, batch writer, query API, aggregations)
- Network batching (`TelemetryBatchPayload`, `TelemetryPacketHandler`, `LVCSyncPayload`)
- Exports (CSV/JSON/heatmaps) and dashboard server
- Settings and data formats (`TelemetrySettings`, `TelemetryConfig`, `TelemetryJson`, `BitPackedFlags`)

## Key Components

### Core

- `com.devmod.telemetry.TelemetryService`
- `com.devmod.telemetry.TelemetryEvents`
- `com.devmod.telemetry.TelemetryLogHandlers`
- `com.devmod.telemetry.TelemetrySettings`
- `com.devmod.telemetry.TelemetryConfig`

### Domain Trackers

- Player + progression: `player.*`, `progression.*`, `skills.*`
- Combat + damage: `combat.*`, `damage.*`, `boss.*`, `economy.*`
- Spatial + rooms: `spatial.*`, `room.*`
- Dungeons: `dungeon.*`

### DuckDB

- `com.devmod.telemetry.duckdb.DuckDBTelemetryService`
- `com.devmod.telemetry.duckdb.DuckDBSchemaManager`
- `com.devmod.telemetry.duckdb.DuckDBMigrationService`
- `com.devmod.telemetry.duckdb.DuckDBBatchWriter`
- `com.devmod.telemetry.duckdb.DuckDBQueryAPI`
- `com.devmod.telemetry.duckdb.aggregation.*`
- `com.devmod.telemetry.duckdb.lvc.*`

### Export + Dashboard

- `com.devmod.telemetry.export.CsvExporter`
- `com.devmod.telemetry.export.JsonReportExporter`
- `com.devmod.telemetry.export.HeatmapExporter`
- `com.devmod.telemetry.dashboard.TelemetryDashboardServer`
- `com.devmod.telemetry.dashboard.TelemetryAnalyticsHandlers`

## Entry Points

- Commands: `devmod telemetry ...` (`TelemetryReloadCommand`), `devmod dashboard ...` (`DashboardCommand`), `devmod dungeon ...` (`DungeonCommand`).
- Actions are registered in `ActionRegistry` (telemetry and dungeon action IDs).
- Network batching is handled by `TelemetryPacketHandler`.

## Data + Encoding

- DuckDB schema and migrations live in `DuckDBSchemaManager` and `DuckDBMigrationService`.
- Compact flags: `BitPackedFlags`.
- JSON encoding helpers: `TelemetryJson`.
- Room metadata: `RoomDefinition`.

## Automated Validation

- `TelemetryJsonDirectTest`
- `BitPackedFlagsDirectTest`
- `RoomDefinitionDirectTest`
- `TelemetryBatchPayloadDirectTest`
- `DuckDBMigrationValidationTest`
- `DuckDBTelemetryIntegrationTest`
- `TelemetryLVCTest`
- `TelemetryAggregatorTest`
