# Telemetry System

> Ultimo aggiornamento: 2025-12-30

Sistema di analytics gameplay con persistenza DuckDB, export e dashboard.

---

## Come Funziona (in breve)

DevMod raccoglie dati di gioco (combattimenti, morti, movimenti, etc.) e li salva in un database locale **DuckDB**. Questi dati servono per:

- **Dashboard** — Visualizzare statistiche in tempo reale
- **Heatmap** — Vedere dove i giocatori muoiono o si muovono
- **Balancing** — Capire cosa è troppo forte o debole
- **Debug** — Investigare bug con dati concreti

---

## DuckDB Bootstrap (Download Automatico)

### Il Problema

DuckDB è un database embedded che richiede una libreria nativa (~15MB) specifica per ogni sistema operativo. Non possiamo includerla nel mod perché:

1. Aumenterebbe troppo le dimensioni del JAR
2. Dovremmo includere versioni per Windows, Linux, macOS

### La Soluzione

Al primo avvio, DevMod:

1. **Controlla** se DuckDB è già disponibile
2. **Rileva** il sistema operativo (Windows/Linux/macOS) e architettura (amd64/arm64)
3. **Scarica** il JAR corretto da Maven Central
4. **Ripacchetta** il JAR con attributi NeoForge (FMLModType: GAMELIBRARY)
5. **Salva** in `mods/duckdb-jdbc-X.X.X.jar`
6. **Richiede restart** del server

### Cosa Vede l'Utente

```
[DevMod] Driver not found in classpath
[DevMod] Downloading duckdb_jdbc-1.4.3.0-windows_amd64.jar from Maven Central...
[DevMod] Successfully downloaded duckdb-jdbc-1.4.3.0.jar (15 MB)

========================================================
  DUCKDB JDBC DRIVER DOWNLOADED SUCCESSFULLY!

  Please RESTART THE SERVER to complete installation.

  Mailbox and Telemetry features will be available
  after the restart.
========================================================
```

### Cosa Succede Se Non Si Riavvia

Le feature che richiedono DuckDB sono **disabilitate** ma il mod funziona:

- Mailbox → disabilitato
- Notification persistence → disabilitato
- Telemetry → disabilitato
- Dashboard → disabilitato
- Tutto il resto → funziona normalmente

### File Coinvolti

```
com.devmod.telemetry.duckdb/
└── DuckDBBootstrap.java    # Download automatico

mods/
└── duckdb-jdbc-1.4.3.0.jar # JAR scaricato (dopo download)
```

### API per Sviluppatori

```java
// Controlla se DuckDB è disponibile (senza download)
boolean available = DuckDBBootstrap.isAvailable();

// Assicura disponibilità, scarica se necessario
// Ritorna true se pronto, false se serve restart
boolean ready = DuckDBBootstrap.ensureAvailable(gameDir);

// Controlla se scaricato ma non ancora caricato
boolean needsRestart = DuckDBBootstrap.isDownloadedButNotLoaded(gameDir);
```

---

## Scope

- Event capture e scrittura asincrona (`TelemetryService`, `AsyncTelemetryWriter`, `TelemetryEvents`)
- Domain tracker (player, combat, damage, progression, spatial, boss, dungeon, economy)
- Pipeline DuckDB (connection, schema, migrations, batch writer, query API, aggregations)
- Network batching (`TelemetryBatchPayload`, `TelemetryPacketHandler`, `LVCSyncPayload`)
- Export (CSV/JSON/heatmap) e dashboard server
- Settings e formati dati (`TelemetrySettings`, `TelemetryConfig`, `TelemetryJson`, `BitPackedFlags`)

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
