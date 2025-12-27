# Spark Profiler Integration

> Last updated: 2025-12-26
> Status: CURRENT (verified against code)

## Overview

- Mod ID: `spark`
- Module: `com.devmod.compat.mods.spark.SparkCompat`
- Registration: `ModIntegrationManager`
- Gating: reflection only; API access is optional

## Implementation Notes

- Uses `SparkProvider.get()` to obtain the API instance via reflection.
- Provides helpers to read TPS/MSPT windows when the API is available.

## Exposed Helpers

- `isAvailable()` / `isApiAvailable()`
- TPS windows: `getTps10Seconds()`, `getTps1Minute()`, `getTps5Minutes()`, `getTps15Minutes()`
- MSPT windows: `getMspt10Seconds()`, `getMspt1Minute()`
- Health helpers: `isTpsHealthy()`, `isTpsCritical()`, `getTpsStatusString()`
- Summary helpers: `getPerformanceSummary()`, `getAllTpsValues()`

## Usage Pattern

- Call `SparkCompat.isApiAvailable()` before reading TPS/MSPT values.
- When unavailable, helpers return default values (-1 or empty strings).
