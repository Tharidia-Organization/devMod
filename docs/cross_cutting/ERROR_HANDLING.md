# Error Handling

> **Audit Date**: 2024-12-23

---

## Error Taxonomy

### Exception Types

| Exception | Package | Usage |
|-----------|---------|-------|
| `TemplateLoadException` | arena.registry | Template parse failure |
| `InheritanceCycleException` | arena.registry | Circular template extends |
| `ParentTemplateNotFoundException` | arena.registry | Missing parent |
| `InheritanceDepthExceededException` | arena.registry | Too deep inheritance |
| `ValidationException` | arena.validation | Invalid template |

### Error Codes (Proposed)

| Code | Category | Example |
|------|----------|---------|
| `E1xxx` | Arena | E1001: Template not found |
| `E2xxx` | Endurance | E2001: Quest already active |
| `E3xxx` | Network | E3001: Rate limit exceeded |
| `E4xxx` | Telemetry | E4001: DuckDB write failed |
| `E5xxx` | Config | E5001: Invalid config value |

---

## Recovery Patterns

### Circuit Breaker (DuckDB)

```java
private static final int THRESHOLD = 5;
private volatile int consecutiveErrors = 0;

public void write(Event event) {
    if (circuitOpen) {
        fallbackToNdjson(event);
        return;
    }

    try {
        duckdb.insert(event);
        consecutiveErrors = 0;
    } catch (Exception e) {
        consecutiveErrors++;
        if (consecutiveErrors >= THRESHOLD) {
            openCircuit();
        }
    }
}
```

### Fallback Chain (Arena)

```java
public ArenaTemplate resolve(String id) {
    // 1. Try requested template
    ArenaTemplate template = registry.get(id);
    if (template != null) return template;

    // 2. Try fallback template
    template = registry.get("default_flat_64");
    if (template != null) return template;

    // 3. Generate minimal template
    return TemplateGenerator.minimal();
}
```

### Graceful Degradation (Instance)

```java
public void onDimensionCreateFailed(UUID instanceId, Exception e) {
    LOGGER.error("Dimension creation failed", e);

    // Recover all affected players
    for (UUID playerId : getPlayersForInstance(instanceId)) {
        recoverySystem.performRecovery(playerId);
    }

    // Mark instance for cleanup
    registry.markFailed(instanceId);
}
```

---

## Alert Routing

### AlertRouter

```java
public interface AlertChannel {
    CompletableFuture<Boolean> send(ErrorContext context);
}

// Channels
- DiscordAlertChannel (webhook)
- TelemetryAlertChannel (DuckDB)
- LogAlertChannel (Log4j)
```

### Error Context

```java
public record ErrorContext(
    String errorCode,
    String message,
    Severity severity,
    Map<String, Object> metadata,
    Throwable cause
) {}
```

---

## Logging Guidelines

### Log Levels

| Level | Usage |
|-------|-------|
| ERROR | Unrecoverable, needs attention |
| WARN | Recoverable, may need attention |
| INFO | Important state changes |
| DEBUG | Detailed flow information |
| TRACE | Very detailed (disabled in prod) |

### Structured Logging

```java
LOGGER.error("[{}] Template load failed: {} (source: {})",
    "E1001",
    template.id(),
    source
);
```

---

## Cross-References

- [[areas/arena/README]] - Arena error handling
- [[areas/telemetry/README]] - Circuit breaker
- [[cross_cutting/CONCURRENCY]] - Thread error handling
