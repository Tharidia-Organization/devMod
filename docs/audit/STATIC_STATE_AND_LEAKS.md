# Static State and Memory Leak Remediation

**Last Updated:** 2025-12-24  
**Priority:** P0 (Crash) + P2 (Leak)

## P0 - Client-Only Singletons (Crash Prevention)

| Singleton | Package | Status | Action |
|-----------|---------|--------|--------|
| `ShakeManager.INSTANCE` | `com.devmod.client.effects` | Safe | Already in client package |
| `TrailManager.INSTANCE` | `com.devmod.client.effects` | Safe | Already in client package |
| `DebugClientRenderer.INSTANCE` | `com.devmod.debug.client` | Safe | Module client package |
| `IntegratedTestSession.INSTANCE` | `com.devmod.client.testing` | Safe | Already in client package |
| `QANotificationSystem.INSTANCE` | `com.devmod.client.testing` | Safe | Already in client package |
| `ClientTelemetryBuffer.INSTANCE` | `com.devmod.client.telemetry` | Fixed | Moved from common package |

**Additional gating:**
- Client-only compat modules now registered via `DistExecutor.safeRunWhenOn(Dist.CLIENT, ...)`.
- Client singleton access in common code removed (no `com.devmod.client.*.INSTANCE` references).
- `TransformProviderRegistry` now uses reflection to obtain the client provider.

## P2 - Cache Leak Fixes

### ModelPartTransformCapture (client)

**Issues:**
- Unbounded `ConcurrentHashMap` growth on long sessions.
  
**Fixes:**
- TTL and max-size eviction already present.
- Added `removeEntity(int entityId)` and `clientTick()` cleanup hook.
- Added per-entity cleanup on `EntityLeaveLevelEvent`.

**Call Sites:**
- `ClientModEvents.onEntityLeave()` removes per-entity cache.
- `RenderEvents.onClientTick()` calls `ModelPartTransformCapture.clientTick()`.

### ModelPartTransformExtractor (client)

- Already capped + periodic cleanup.
- Per-entity cleanup called on `EntityLeaveLevelEvent`.

### TransformProviderRegistry (common)

- `clearCache(entityId)` invoked on:
  - Server: `TelemetryEvents.onEntityLeave()`
  - Client: `ClientModEvents.onEntityLeave()`

## Before / After

- **Before:** Client-only singleton (`ClientTelemetryBuffer`) lived in common package; mixins and entrypoint outside `/client/`.  
- **After:** All client-only singletons and client-only code are under `/client/` or `*.client.*` packages; common code no longer references client `.INSTANCE` fields.
