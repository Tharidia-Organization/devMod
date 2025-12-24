# Telemetry Conventions

> **Audit Date**: 2024-12-23

---

## Event Naming

### Format

```
<domain>.<entity>.<action>
```

### Examples

| Event | Domain | Entity | Action |
|-------|--------|--------|--------|
| `arena.template.loaded` | arena | template | loaded |
| `arena.build.complete` | arena | build | complete |
| `combat.hit.recorded` | combat | hit | recorded |
| `endurance.wave.complete` | endurance | wave | complete |

---

## Domain Prefixes

| Prefix | System |
|--------|--------|
| `arena.*` | Arena template system |
| `combat.*` | Combat tracking |
| `endurance.*` | Quest system |
| `player.*` | Player events |
| `spatial.*` | Heatmaps, alerts |
| `progression.*` | XP, advancements |
| `economy.*` | Loot, trades |
| `dungeon.*` | Dungeon runs |

---

## Required Fields

### All Events

| Field | Type | Description |
|-------|------|-------------|
| `ts` | TIMESTAMP | Event timestamp |
| `event_type` | VARCHAR | Event name |

### Combat Events

| Field | Type | Required |
|-------|------|----------|
| `room` | VARCHAR | Yes |
| `attacker_name` | VARCHAR | For hits |
| `target_name` | VARCHAR | Yes |
| `damage` | DOUBLE | For hits |

### Endurance Events

| Field | Type | Required |
|-------|------|----------|
| `session_id` | UUID | Yes |
| `player_id` | UUID | Yes |
| `wave_number` | INTEGER | For wave events |

---

## Correlation IDs

### Session Tracking

```java
// Generate at quest start
UUID sessionId = UUID.randomUUID();

// Include in all related events
event.put("session_id", sessionId.toString());
```

### Arena Tracking

```java
// Track arena through lifecycle
event.put("arena_id", arenaId.toString());
event.put("template_id", template.id());
event.put("template_version", template.version());
```

---

## Batch Writing

### Flush Conditions

| Condition | Default |
|-----------|---------|
| Rows in queue | 100 |
| Time elapsed | 5 seconds |
| Shutdown signal | Immediate |

### Priority Levels

| Priority | Events | Drop Policy |
|----------|--------|-------------|
| CRITICAL | hit, death, wave_end | Never drop |
| HIGH | spawn, heal, perk | Drop at 80% queue |
| NORMAL | ability, alert | Drop at 50% queue |
| LOW | movement, snapshot | Drop first |

---

## NDJSON Format

### File Location

```
logs/telemetry/<date>/<type>.ndjson
```

### Example Line

```json
{"ts":"2024-12-23T10:30:45Z","event":"combat.hit","room":"boss_arena","attacker":"player1","target":"zombie","damage":15.5}
```

---

## Cross-References

- [[areas/telemetry/README]] - Full telemetry system
- [[TRACEABILITY_MATRIX]] - Feature → telemetry mapping
