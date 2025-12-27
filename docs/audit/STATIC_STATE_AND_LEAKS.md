# Static State and Memory Leak Remediation

> Last updated: 2025-12-26
> Status: CURRENT (verified against code)

**Priority:** P0 (Crash) + P2 (Leak)

## P0 - Client-Only Singletons (Crash Prevention)

| Singleton | Package | Status |
|-----------|---------|--------|
| `ShakeManager.INSTANCE` | `com.devmod.client.effects` | Client-only |
| `TrailManager.INSTANCE` | `com.devmod.client.effects` | Client-only |
| `DebugClientRenderer.INSTANCE` | `com.devmod.debug.client` | Client-only |
| `IntegratedTestSession.INSTANCE` | `com.devmod.client.testing` | Client-only |
| `QANotificationSystem.INSTANCE` | `com.devmod.client.testing` | Client-only |
| `ClientTelemetryBuffer.INSTANCE` | `com.devmod.client.telemetry` | Client-only |

**Gating patterns in use:**
- Client-only compat modules are registered only when `FMLEnvironment.dist == Dist.CLIENT`
  (`ModIntegrationManager` -> `ClientCompatRegistrar`).
- `TransformProviderRegistry` uses `FMLEnvironment.dist.isClient()` and reflection to load
  `ClientTransformProvider` without server classloading.

## P2 - Cache Leak Fixes

### ModelPartTransformCapture (client)

- TTL: 100ms, max 256 entities, periodic cleanup every 5s (evicts stale and oldest entries).
- Per-entity cleanup: `ClientModEvents.onEntityLeave()` -> `removeEntity(int)`.
- Periodic cleanup: `RenderEvents.onClientTick()` -> `clientTick()`.
- World unload: `ClientModEvents.onPlayerLogout()` -> `clearAll()`.

### ModelPartTransformExtractor (client)

- Max 256 entries, periodic cleanup every 100 ticks via `cleanupCache`.
- Per-entity cleanup: `ClientModEvents.onEntityLeave()` -> `clearCache(int)`.
- World unload: `ClientModEvents.onPlayerLogout()` -> `clearAllCaches()`.

### TransformProviderRegistry (common + client)

- Server shutdown: `TelemetryEvents.onServerStopped()` -> `clearAllCaches()`.
- Per-entity cleanup (server): `TelemetryEvents.onEntityLeave()` -> `clearCache(int)`.
- Per-entity cleanup (client): `ClientModEvents.onEntityLeave()` -> `clearCache(int)`.

## Status

- Client-only singletons are confined to client packages.
- Cache cleanup hooks run on entity removal and world unload.
- Common code avoids direct client singleton access (enforced by tests/scripts).
