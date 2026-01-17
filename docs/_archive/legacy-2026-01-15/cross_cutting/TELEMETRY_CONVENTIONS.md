# Telemetry Conventions

> Last updated: 2025-12-26
> Status: CURRENT (verified against code)

This document summarizes the concrete telemetry building blocks used in DevMod.

## Data Model and Schema

- Schema creation and migrations are handled by `DuckDBSchemaManager` and `DuckDBMigrationService`.
- Runtime storage is managed by `DuckDBTelemetryService` and `DuckDBBatchWriter`.

## Encoding and Utilities

- JSON escaping and safe string encoding: `TelemetryJson`.
- Compact flag encoding: `BitPackedFlags`.
- Room metadata defaults and parsing: `RoomDefinition`.

## Ingestion and Batching

- Client batches are defined by `TelemetryBatchPayload` and handled by `TelemetryPacketHandler`.
- Server-side batches are queued and flushed by `DuckDBBatchWriter`.
- `AsyncTelemetryWriter` is used for fallback asynchronous writes.

## Exports and Dashboards

- Exporters: `CsvExporter`, `JsonReportExporter`, `HeatmapExporter`.
- Dashboard server: `TelemetryDashboardServer` with `TelemetryAnalyticsHandlers`.
