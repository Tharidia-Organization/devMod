# Concurrency Patterns

> **Audit Date**: 2024-12-23

---

## Thread Model

### Game Threads

| Thread | Purpose | Key Classes |
|--------|---------|-------------|
| **Server Thread** | Game logic, ticks | `MinecraftServer.runServer()` |
| **Client Thread** | Rendering, input | `Minecraft.run()` |
| **Network Thread** | Packet I/O | Netty handlers |
| **Render Thread** | OpenGL calls | `RenderSystem` |

### DevMod Threads

| Thread | Purpose | Class |
|--------|---------|-------|
| **DuckDB Writer** | Batch telemetry inserts | `DuckDBBatchWriter` |
| **Async Telemetry** | NDJSON fallback writes | `AsyncTelemetryWriter` |
| **Dimension Builder** | Async arena creation | `DynamicDimensionManager` |
| **Autosmoke Scheduler** | Cron-style scheduling | `AutosmokeScheduler` |

---

## Concurrency Primitives

### ConcurrentHashMap Usage

| Class | Map | Purpose |
|-------|-----|---------|
| `InstanceRegistry` | `instances` | Player→Instance mapping |
| `InstanceRegistry` | `playerToInstance` | Bidirectional lookup |
| `PolicyResolver` | `policies` | Policy cache |
| `ArenaTemplateRegistry` | `registry` | Template storage |
| `ActionRegistry` | `actions` | Action definitions |

### AtomicReference

| Class | Field | Purpose |
|-------|-------|---------|
| `ArenaCommandEvents` | `CONFIG_SNAPSHOT` | Config hot-reload |
| `DuckDBBatchWriter` | `running` | Shutdown coordination |

### CopyOnWriteArrayList

| Class | Field | Purpose |
|-------|-------|---------|
| `EditorConfig` | `listeners` | Config change listeners |
| `TemplateEventDispatcher` | `listeners` | Template events |

---

## Lock Patterns

### PolicyResolver Locking

```java
// Per-player lock for template resolution
private final ConcurrentHashMap<UUID, ReentrantLock> playerLocks;

public ResolvedArena resolve(UUID playerId, ResolveContext context) {
    ReentrantLock lock = playerLocks.computeIfAbsent(
        playerId, k -> new ReentrantLock()
    );

    if (!lock.tryLock(5, TimeUnit.SECONDS)) {
        emitLockTimeout(playerId);
        return fallback();
    }

    try {
        return doResolve(context);
    } finally {
        lock.unlock();
    }
}
```

### Identified Issues

| Issue | Location | Risk |
|-------|----------|------|
| **TOCTOU Race** | `PolicyResolver.cleanupStaleLocks()` | Lock removed while queued |
| **No Lock Ordering** | Multiple lock types | Potential deadlock |
| **Hardcoded Timeouts** | 5s, 30s values | Not configurable |

---

## Rate Limiting

### DuckDB Batch Writer

```java
// Backpressure levels
PRESSURE_THRESHOLD_ELEVATED = QUEUE_CAPACITY * 0.5;
PRESSURE_THRESHOLD_CRITICAL = QUEUE_CAPACITY * 0.8;

// Priority-based dropping
CRITICAL: Never drop (hits, deaths)
HIGH: Drop at critical (spawns, heals)
NORMAL: Drop at elevated (abilities)
LOW: Drop first (movement)
```

### PacketSecurityService

```java
// Per-player rate limiting
MAX_PACKETS_PER_SECOND = 10;

// Per packet type tracking
Map<UUID, Map<String, RateLimitEntry>> rateLimits;
```

---

## Thread Safety Patterns

### Server Thread Execution

```java
// Safe server thread execution
server.execute(() -> {
    // Code runs on server thread
    createDimension();
});

// With future
CompletableFuture<Result> future = CompletableFuture.supplyAsync(
    () -> heavyComputation(),
    Util.backgroundExecutor()
).thenAcceptAsync(
    result -> applyToWorld(result),
    server
);
```

### Network Handler Pattern

```java
// Always enqueue work to correct thread
context.enqueueWork(() -> {
    // Runs on server or client thread depending on payload direction
    processPayload(payload);
});
```

---

## Known Race Conditions

### 1. Lock Cleanup Race

```java
// PolicyResolver.java:464-466
// PROBLEM: Check then remove race
if (!lockEntry.lock.isLocked() && !lockEntry.lock.hasQueuedThreads()) {
    playerLocks.remove(playerId);  // Another thread could queue here!
}
```

### 2. Config Reload Race

```java
// EditorConfig.java
// Game thread reads while reload thread writes
EditorUiScale newScale = EditorClientConfig.EDITOR_UI_SCALE.get();
```

### 3. Registry Iteration During Reload

```java
// ArenaTemplateRegistry.java
// Listeners may iterate during atomic swap
atomicReplaceRegistry(newRegistry);  // ConcurrentModificationException possible
```

---

## Recommendations

1. **Fix TOCTOU in lock cleanup**: Use atomic compute operations
2. **Document lock ordering**: PlayerLocks → TemplateLocks → RateLimiter
3. **Make timeouts configurable**: Extract to DuckDBConfig
4. **Add read-write locks for config**: Prevent reload races

---

## Cross-References

- [[areas/arena/README]] - Arena locking
- [[areas/telemetry/README]] - Batch writer
- [[areas/instance/README]] - Dimension creation
