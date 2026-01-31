# Quickstart

> Ultimo aggiornamento: 2026-01-31

## Requisiti

- Java 21
- Minecraft 1.21.1
- NeoForge 21.1.x (repo usa 21.1.216)
- Node.js (solo per admin panel)

## Build

```bash
./gradlew build
```

## Run (dev)

```bash
# Client
./gradlew runClient

# Server
./gradlew runServer

# GameTest
./gradlew runGameTestServer
```

## Admin panel

```bash
./gradlew startAdminPanel
# oppure
cd admin-panel && npm install && npm run dev
```

## Config e runtime

- Config TOML runtime: `run/config/devmod-common.toml`, `run/config/devmod-mechanics.toml`, `run/config/devmod-portals.toml`, `run/config/devmod-client.toml`
- JSON config aggiuntivi: `config/devmod/`
- Telemetry output: `run/telemetry/`

## Data generation

```bash
./gradlew runData
```
