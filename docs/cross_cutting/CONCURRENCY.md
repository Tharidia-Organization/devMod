# Concurrency Patterns

> Last updated: 2025-12-26
> Status: CURRENT (verified against code)

This doc summarizes the concurrency patterns used in DevMod.

## Server Thread Rule

- World and dimension changes are scheduled onto the server thread.
- Example: `DynamicDimensionManager.createDimensionAsync()` uses `MinecraftServer.execute(...)` for thread safety.
- Network payloads enqueue work via `context.enqueueWork(...)` to run on the correct thread.

## Background Workers

- `DuckDBBatchWriter` uses a single-thread `ScheduledExecutorService` for periodic flushes.
- `AsyncTelemetryWriter` runs a dedicated daemon thread for fallback writes.
- `AutosmokeScheduler` uses a scheduled executor for timed runs.
- `AsyncArenaBuilder` / `AsyncArenaBuildCoordinator` use async execution for build workflows.

## Thread-safe Collections

- `InstanceRegistry` uses `ConcurrentHashMap` and concurrent sets for instance mappings.
- `ArenaTemplateRegistry` uses `ConcurrentHashMap` for template storage.
- `CompatRegistry` uses `ConcurrentHashMap` for module tracking.

## Shared State Patterns

- `ArenaCommandEvents` keeps the arena config snapshot in an `AtomicReference`.
- Registries and caches are updated via atomic swaps or guarded by thread-safe collections.
