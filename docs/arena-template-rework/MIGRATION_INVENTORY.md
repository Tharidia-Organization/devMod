# Arena API Migration Inventory

## DD42: Migration Inventory - 12 Call-Sites, 6 PR Plan

This document tracks all legacy arena API call-sites and the migration plan.

## Call-Site Inventory

### Legacy `ArenaManager.createArena()` Call-Sites

| # | File | Line | Context | Priority | Status |
|---|------|------|---------|----------|--------|
| 1 | `commands/ArenaCommand.java` | ~45 | `/arena create` command | HIGH | Pending |
| 2 | `commands/ArenaCommand.java` | ~78 | `/arena quickstart` subcommand | HIGH | Pending |
| 3 | `events/MatchmakingHandler.java` | ~120 | Auto-create on queue pop | HIGH | Pending |
| 4 | `events/MatchmakingHandler.java` | ~156 | Ranked match creation | HIGH | Pending |
| 5 | `gui/ArenaCreatorScreen.java` | ~234 | GUI "Create" button | MEDIUM | Pending |
| 6 | `gui/ArenaCreatorScreen.java` | ~267 | GUI "Clone" button | MEDIUM | Pending |
| 7 | `integration/DungeonPlugin.java` | ~89 | Dungeon arena spawn | MEDIUM | Pending |
| 8 | `integration/EventPlugin.java` | ~45 | Event arena creation | MEDIUM | Pending |
| 9 | `test/ArenaIntegrationTest.java` | ~56 | Test fixture setup | LOW | Pending |
| 10 | `test/ArenaIntegrationTest.java` | ~89 | Multi-arena test | LOW | Pending |
| 11 | `test/MatchmakingTest.java` | ~123 | Matchmaking test | LOW | Pending |
| 12 | `debug/ArenaDebugCommand.java` | ~34 | Debug arena creation | LOW | Pending |

### Legacy `new Arena()` Direct Instantiation

| # | File | Line | Context | Priority | Status |
|---|------|------|---------|----------|--------|
| 1 | `ArenaManager.java` | ~88 | Internal creation | N/A | Adapter |

## Migration Plan - 6 PRs

### PR 1: Core Template API (Week 1)
**Branch:** `feature/arena-template-core`
**Scope:**
- ArenaTemplate base class
- ArenaConfig new format
- ArenaInstance with lifecycle
- Unit tests for core

**Files to Create:**
- `arena/template/ArenaTemplate.java`
- `arena/template/ArenaConfig.java`
- `arena/template/ArenaInstance.java`

### PR 2: Command Migration (Week 2)
**Branch:** `feature/arena-commands-v2`
**Scope:**
- Migrate `/arena create` command
- Migrate `/arena quickstart` command
- Add `/arena template` subcommand
- Update help text

**Call-Sites:** #1, #2

### PR 3: Matchmaking Migration (Week 2-3)
**Branch:** `feature/matchmaking-template`
**Scope:**
- Migrate MatchmakingHandler
- Add template selection
- Ranked match template support

**Call-Sites:** #3, #4

### PR 4: GUI Migration (Week 3)
**Branch:** `feature/arena-gui-v2`
**Scope:**
- Update ArenaCreatorScreen
- Template browser
- Clone from template

**Call-Sites:** #5, #6

### PR 5: Integration Migration (Week 4)
**Branch:** `feature/integrations-template`
**Scope:**
- DungeonPlugin adapter
- EventPlugin adapter
- API compatibility layer

**Call-Sites:** #7, #8

### PR 6: Test & Cleanup (Week 4-5)
**Branch:** `feature/arena-legacy-cleanup`
**Scope:**
- Migrate all test files
- Remove deprecated APIs
- Update documentation

**Call-Sites:** #9, #10, #11, #12

## Migration Code Examples

### Before (Legacy)
```java
// Creating an arena with legacy API
ArenaManager manager = ArenaManager.getInstance();
LegacyArenaConfig config = new LegacyArenaConfig("MyArena", 100, 50, 100);
int arenaId = manager.createArena(config);

if (arenaId == -1) {
    // Handle failure
}
```

### After (New Template API)
```java
// Creating an arena with new Template API
ArenaTemplate template = ArenaTemplate.load("templates/pvp_arena.json");
ArenaConfig config = ArenaConfig.builder()
    .name("MyArena")
    .size(100, 50, 100)
    .spawnPoints(/* ... */)
    .build();

ArenaInstance instance = template.createInstance(config);
instance.initialize()
    .thenAccept(success -> {
        if (success) {
            // Arena ready
        }
    });
```

## Deprecation Timeline

| Milestone | Date | Action |
|-----------|------|--------|
| v1.5.0 | 2024-Q1 | Add @Deprecated annotations |
| v1.6.0 | 2024-Q2 | Add runtime warnings |
| v1.7.0 | 2024-Q3 | Remove from public API |
| v2.0.0 | 2024-Q4 | Complete removal |

## Telemetry

Legacy API usage is tracked via `ArenaManager.getLegacyCallCount()`.

Dashboard query:
```sql
SELECT
    date_trunc('day', timestamp) as day,
    COUNT(*) as legacy_calls,
    COUNT(DISTINCT server_id) as affected_servers
FROM arena_telemetry
WHERE method = 'createArena' AND deprecated = true
GROUP BY 1
ORDER BY 1 DESC
```

## Rollback Plan

If issues arise during migration:

1. **Immediate:** Revert specific PR
2. **Short-term:** Enable legacy compatibility layer
3. **Long-term:** Extend deprecation timeline

## Success Criteria

- [ ] All 12 call-sites migrated
- [ ] Zero legacy API calls in production
- [ ] No regression in arena creation time
- [ ] All tests passing with new API
- [ ] Documentation updated
