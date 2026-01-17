# Arena System

> Last updated: 2025-12-26
> Status: CURRENT (aggiornato dopo orphanage cleanup)

The Arena system manages template-driven arenas: registry/validation, policy resolution, build execution, and operational tooling.

## Scope

- Template registry, schema validation, and hot reload
- Policy resolution and overrides
- Build pipeline (sync/async builders, dry-run, rollback, cleanup)
- Operational tooling (autosmoke, monitoring, alerts, telemetry)
- Integration with quest/instance systems

## Architecture

### Registry + Policy

- `com.devmod.arena.registry` (ArenaTemplateRegistry, TemplateRegistryBootstrap, TemplateLoader, TemplateValidator, TemplateDirectoryWatcher, StructureManifest*)
- `com.devmod.arena.policy` (PolicyResolver, ArenaPolicyRegistry, ResolveContext, ResolvedArena, PolicySchemaValidator, VersionCompatibilityChecker)

### Build + Runtime

- `com.devmod.arena.builder` (ArenaBuilder, TemplateArenaBuilder, AsyncArenaBuilder, BuildTransaction, BuildDryRun, BuildDryRunCalculator, ChunkLoadingManager)
- `com.devmod.arena.concurrency` (TemplateLockManager, ArenaBuildRateLimiter, BuildPermit)
- `com.devmod.arena.validation` (SecurityLimitsEnforcer)
- `com.devmod.arena.cleanup` (ArenaCleanupExecutor, CleanupVerification)
- `com.devmod.arena.fallback` (CircuitBreaker, FallbackMetrics)
- `com.devmod.arena.pool` (PrebuildPoolManager, PooledArena)

### Ops + Telemetry

- `com.devmod.arena.autosmoke` (AutosmokeRunner, AutosmokeScheduler, AutosmokeReportWriter, AutosmokeThresholds)
- `com.devmod.arena.logging` (LogAggregationPipeline, NdjsonWriter, DuckDbDestination)
- `com.devmod.arena.telemetry` / `com.devmod.arena.metrics` (ArenaTelemetry, ArenaMetricsContext, MetricsCompatibilityLayer)
- `com.devmod.arena.alert` (AlertRouter + channels)
- `com.devmod.arena.monitoring` (BuildOutcomeMonitor)

### APIs + Integration

- `com.devmod.arena.command` (ArenaCommands, ArenaActionRegistry) registered by `ArenaCommandEvents`
- `com.devmod.arena.api` (ArenaHandle)
- `com.devmod.arena.integration` (MinecraftBlockPlacer, MinecraftEntitySpawner)
- `com.devmod.arena.override` (TemplateOverrideManager, ForceTemplateCapability)
- `com.devmod.arena.snapshot` / `com.devmod.arena.recovery` / `com.devmod.arena.identity`

## Entry Points

- `/arena` commands via `ArenaCommandEvents` -> `ArenaCommands`.
- Programmatic access via `ArenaActionRegistry`/`ArenaActionBridge` (no `ArenaService`).
- Client build progress via `BuildProgressPayload`.

## Data + Config

- Templates directory is resolved by `ArenaTemplateConfig` (default `config/devmod/arena_templates/`).
- Structure manifest default: `config/devmod/structures_manifest.json`.
- JSON schemas shipped in `src/main/resources/schemas/arena_template.schema.json` and `src/main/resources/schemas/arena_policy.schema.json`.
- Telemetry/logging persists via NDJSON/DuckDB pipelines in `com.devmod.arena.logging`.

## Automated Validation

- Tests live under `src/test/java/com/devmod/arena/**` and cover registry, policy resolution, builder flows, autosmoke, fallback/cleanup, and monitoring logic.
