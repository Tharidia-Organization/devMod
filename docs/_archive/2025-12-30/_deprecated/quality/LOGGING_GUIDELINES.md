# Logging Guidelines

**Purpose**: Prevent debug-level spam in hot paths that can impact performance.

---

## Log Level Usage

| Level | When to Use | Performance Impact |
|-------|-------------|-------------------|
| `ERROR` | Unrecoverable failures, exceptions that need attention | Minimal (rare) |
| `WARN` | Recoverable issues, deprecation notices, config problems | Minimal (occasional) |
| `INFO` | Significant lifecycle events (startup, shutdown, major operations) | Low (infrequent) |
| `DEBUG` | Detailed flow for troubleshooting | **High if in hot paths** |
| `TRACE` | Very detailed, step-by-step execution | **Very high** |

---

## Hot Path Rules

**Hot paths** are code executed frequently during gameplay:
- Per-tick updates (20 tps = 20 calls/second)
- Per-frame rendering (60+ fps = 60+ calls/second)
- Per-damage events (combat can trigger 10+ times/second)
- Per-entity loops (scales with entity count)

### DO NOT log at DEBUG/TRACE level in:

1. **Combat handlers** (called on every hit)
   - `DamageHandler.onDamage()`
   - `EvasionHandler.recordAttack()`
   - `ShieldBlockHandler.applyBlock()`

2. **Tick handlers** (called 20x/second)
   - `onServerTick()`, `onClientTick()`
   - Entity AI updates
   - Stamina/cooldown updates

3. **Render methods** (called 60+x/second)
   - `render()`, `renderDebug()`
   - HUD overlays
   - VFX particle systems

4. **Loops over entities/players**
   - Any `for (Entity e : level.getEntities())`
   - Any `for (Player p : server.getPlayerList())`

### Acceptable alternatives:

```java
// BAD: Logs every damage event
LOGGER.debug("Damage calc: base={}, final={}", baseDamage, finalDamage);

// GOOD: Rate-limited logging (once per second max)
if (System.currentTimeMillis() - lastLogTime > 1000) {
    LOGGER.debug("Recent damage events: {}", recentDamageCount);
    lastLogTime = System.currentTimeMillis();
}

// GOOD: Conditional on debug flag
if (DevModConfig.verboseCombatLogging.get()) {
    LOGGER.debug("Damage calc: base={}, final={}", baseDamage, finalDamage);
}

// GOOD: Aggregate logging (log summary, not individual events)
damageEventCount++;
// ... later in cleanup/summary method:
LOGGER.debug("Processed {} damage events this tick", damageEventCount);
```

---

## Current Violations (To Fix)

Found in combat hot paths:

| File | Line | Issue |
|------|------|-------|
| EvasionHandler.java | 55 | Logs every attack |
| DamageHandler.java | 146 | Logs every damage calc |
| ShieldBlockHandler.java | 45, 49 | Logs every block attempt |
| ExecutionSystem.java | 283 | Logs every execution start |

Found in rendering/tick paths:

| File | Line | Issue |
|------|------|-------|
| ServerTransformProvider.java | 178 | Logs cache cleanup (called periodically) |
| WeaponTrailVFX.java | (multiple) | May log in render path |

---

## Logging Patterns

### Lifecycle events (INFO is appropriate)
```java
LOGGER.info("DevMod initialized successfully");
LOGGER.info("Arena '{}' created in {}ms", arenaId, duration);
LOGGER.info("Endurance quest started for {} players", playerCount);
```

### Config/setup issues (WARN is appropriate)
```java
LOGGER.warn("Config file missing, using defaults: {}", configPath);
LOGGER.warn("GeckoLib not found - using fallback transforms");
LOGGER.warn("Deprecated API used: {}", methodName);
```

### Exceptions (ERROR with context)
```java
try {
    // ...
} catch (IOException e) {
    LOGGER.error("Failed to save arena template '{}': {}", templateId, e.getMessage());
    // Include stack trace only for unexpected errors
    LOGGER.debug("Stack trace:", e);
}
```

### Debug logging (guarded or rate-limited)
```java
// Guard with config flag
if (TelemetryConfig.debugSpawning.get()) {
    LOGGER.debug("Spawning {} mobs at {}", count, position);
}

// Or use lazy evaluation
LOGGER.debug("Entity state: {}", () -> buildExpensiveDebugString(entity));
```

---

## Telemetry vs Logging

For high-frequency data, use telemetry instead of logging:

```java
// BAD: Logging every hit
LOGGER.debug("Player hit for {} damage", damage);

// GOOD: Record to telemetry
TelemetryService.recordDamageEvent(player, damage);
// Telemetry buffers and batch-writes, logging doesn't
```

---

## Verification

Run the following to find potential hot path logging:

```bash
# Find debug logging in combat package
grep -rn "LOGGER.debug" src/main/java/com/devmod/combat/

# Find debug logging in tick handlers
grep -rn "LOGGER.debug" src/main/java/com/devmod/ | grep -i "tick"

# Count total debug statements
grep -rn "LOGGER.debug" src/main/java/com/devmod/ | wc -l
```

---

## Future: Static Analysis

Consider adding a custom Checkstyle or Error Prone rule to:
1. Flag `LOGGER.debug()` calls inside methods named `tick*`, `render*`, `onDamage*`
2. Require debug logging to be guarded by config flags in specified packages

---

*Last updated: 2025-12-26*
