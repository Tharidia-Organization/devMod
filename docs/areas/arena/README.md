# Arena System

> Last updated: 2025-12-26
> Status: CURRENT (verified against code)

The Arena system manages template-driven arenas: registry/validation, policy resolution, build execution, and operational tooling.

## Scope

- Template registry, schema validation, and hot reload
- Policy resolution and overrides
- Build pipeline (sync/async builders, dry-run, rollback, cleanup)
- Operational tooling (autosmoke, monitoring, alerts, telemetry)
- Integration with quest/instance systems

## Architecture

### Registry + Policy

- `com.devmod.arena.registry` (ArenaTemplateRegistry, TemplateRegistryBootstrap, TemplateLoader, TemplateValidator, HotReloadManager, StructureManifest*)
- `com.devmod.arena.policy` (PolicyResolver, ArenaPolicyRegistry, ResolveContext, ResolvedArena, PolicySchemaValidator, PolicyMutatorResolver, VersionCompatibilityChecker)

### Build + Runtime

- `com.devmod.arena.builder` (ArenaBuilder, TemplateArenaBuilder, AsyncArenaBuilder, BuildTransaction, BuildDryRun, BuildDryRunCalculator, ChunkLoadingManager)
- `com.devmod.arena.concurrency` (TemplateLockManager, ArenaBuildRateLimiter, BuildPermit)
- `com.devmod.arena.validation` (RuntimePreflightCheck, AdvancedArenaTemplateValidator, SecurityLimitsEnforcer)
- `com.devmod.arena.cleanup` (ArenaCleanupExecutor, CleanupVerification, CleanupResidualChecker)
- `com.devmod.arena.fallback` (FallbackBuildStrategy, CircuitBreaker, GracefulDegradationManager)
- `com.devmod.arena.pool` (PrebuildPoolManager, PooledArena)

### Ops + Telemetry

- `com.devmod.arena.autosmoke` (AutosmokeRunner, AutosmokeScheduler, AutosmokeReportWriter, AutosmokeThresholds)
- `com.devmod.arena.logging` (LogAggregationPipeline, NdjsonWriter, DuckDbDestination)
- `com.devmod.arena.telemetry` / `com.devmod.arena.metrics` (ArenaTelemetry, ArenaBuildTelemetry, BuildTelemetry)
- `com.devmod.arena.alert` (AlertRouter + channels)
- `com.devmod.arena.monitoring` (BuildOutcomeMonitor, DashboardValidationJob, AnomalyThresholds)

### APIs + Integration

- `com.devmod.arena.command` (ArenaCommands, ArenaActionRegistry) registered by `ArenaCommandEvents`
- `com.devmod.arena.api` (ArenaService, ArenaHandle, ResolveOptions)
- `com.devmod.arena.integration` (ArenaQuestIntegration, MinecraftBlockPlacer, MinecraftEntitySpawner)
- `com.devmod.arena.override` (TemplateOverrideManager, ForceTemplateCapability)
- `com.devmod.arena.snapshot` / `com.devmod.arena.recovery` / `com.devmod.arena.identity`

## Entry Points

- `/arena` commands via `ArenaCommandEvents` -> `ArenaCommands`.
- Programmatic access via `ArenaService` (prepare/resolve/release).
- Client build progress via `BuildProgressPayload`.

## Data + Config

- Templates directory is resolved by `ArenaTemplateConfig` (default `config/devmod/arena_templates/`).
- Structure manifest default: `config/devmod/structures_manifest.json`.
- JSON schemas shipped in `src/main/resources/schemas/arena_template.schema.json` and `src/main/resources/schemas/arena_policy.schema.json`.
- Telemetry/logging persists via NDJSON/DuckDB pipelines in `com.devmod.arena.logging`.

## Automated Validation

- Tests live under `src/test/java/com/devmod/arena/**` and cover registry, policy resolution, builder flows, autosmoke, fallback/cleanup, and monitoring logic.
