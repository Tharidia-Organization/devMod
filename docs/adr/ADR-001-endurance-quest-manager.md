# ADR-001: EnduranceQuestManager Architecture

**Status**: Accepted
**Date**: 2025-12-26
**Context**: Quality pass documentation for large classes

---

## Summary

`EnduranceQuestManager` (3027 LOC) is the central coordinator for all Endurance Quest operations. This ADR documents its current architecture, responsibilities, and rationale for its size.

---

## Context

Endurance Quests are a core gameplay feature requiring coordination of:
- Arena creation and destruction
- Player session lifecycle
- Wave progression and mob spawning
- Persistence and statistics
- Party synchronization
- Telemetry and analytics

A central manager pattern was chosen to provide a single point of coordination for these cross-cutting concerns.

---

## Architecture

### Responsibilities

| Area | Methods | Description |
|------|---------|-------------|
| **Initialization** | `initialize()`, `shutdown()` | Lifecycle management, data directory setup |
| **Arena Preparation** | `prepareArenaForParty()`, `prepareArenaForPartyAsync()` | Creates/claims arena instances |
| **Player Teleportation** | `teleportPlayersToArena()` | Moves players to arena with proper state |
| **Quest Lifecycle** | `startQuest()`, `startPreparedQuest()`, `abandonQuest()` | Quest state transitions |
| **Wave Management** | `completeWave()`, `continueToNextWave()`, `exitAtCheckpoint()` | Wave progression |
| **Combat Tracking** | `recordKill()`, `recordDamageDealt()`, `recordDamageTaken()` | Combat statistics |
| **Session Queries** | `getActiveSession()`, `isPlayerInQuest()`, `getActiveSessions()` | State queries |
| **Persistence** | `getPlayerStats()`, `clearAllPlayerStats()` | Stats persistence |

### Delegation Pattern

The manager delegates to specialized classes:

```
EnduranceQuestManager
├── EnduranceQuestPersistence     - Player stats loading/saving
├── EndurancePlayerStateManager   - Player state during quests
├── EnduranceSessionHandler       - Session lifecycle events
├── WaveManager                   - Wave spawning and progression
├── PerkSystem                    - Perk selection and effects
├── RewardSystem                  - Quest rewards distribution
└── PrebuildPoolManager           - Arena pre-building pool
```

### Thread Safety

- `activeSessions`: `ConcurrentHashMap<UUID, ActiveQuestSession>`
- `questTemplates`: `ConcurrentHashMap<ResourceLocation, EnduranceQuest>`
- Async arena building via `CompletableFuture`

---

## Decision

### Why a Single Manager?

1. **Transaction Boundaries**: Quest operations require atomic state changes across multiple subsystems
2. **Event Ordering**: Player death, wave completion, and quest end must be processed in order
3. **Session Isolation**: Each quest session must be isolated from others
4. **Recovery**: Crash recovery requires centralized state knowledge

### Why 3000+ LOC?

The size is justified by:
1. **50+ public methods** covering the full quest lifecycle
2. **Complex arena preparation** with async building, fallbacks, and circuit breakers
3. **Party coordination** requiring synchronized multi-player state
4. **Comprehensive error handling** with user-friendly messages

---

## Alternatives Considered

### Option A: Split by Lifecycle Phase
- `QuestPreparationService` - Arena setup
- `QuestExecutionService` - Active quest management
- `QuestCompletionService` - End-of-quest handling

**Rejected**: Would require passing session context between services, increasing coupling.

### Option B: Event-Driven Architecture
- Publish events for each state change
- Subsystems subscribe and react independently

**Rejected**: Makes transaction boundaries unclear; harder to ensure ordering.

### Option C: Command Pattern
- Each operation as a command object
- Command handler processes sequentially

**Partially Adopted**: Used for async arena building (`CompletableFuture`).

---

## Consequences

### Positive
- Single source of truth for quest state
- Clear transaction boundaries
- Easy to add telemetry and logging
- Predictable operation ordering

### Negative
- Large class size (3027 LOC)
- Testing requires mocking many dependencies
- Changes risk affecting multiple features

### Mitigations
- Extensive delegation to specialized classes
- Clear method naming by responsibility area
- Comprehensive Javadoc on public methods

---

## Future Considerations

1. **Extract ArenaPreparationService**: The 500+ LOC arena preparation logic could be extracted
2. **Extract QuestSessionFacade**: Session query methods could move to a read-only facade
3. **Add Integration Tests**: End-to-end tests for complete quest flows

---

## Extraction Analysis (2025-12-26)

A detailed analysis was performed to evaluate extracting components from EnduranceQuestManager.

### ArenaPreparationService Analysis

**Scope**: Lines 536-1330 (~800 LOC)

**Methods identified for extraction**:

- `prepareArenaForParty()`, `prepareArenaForPartyAsync()`
- `prepareTemplateArenaForParty()`, `prepareTemplateArenaForPartyAsync()`
- `finalizePreparedArena()`, `teleportPlayersToArena()`
- 15+ private helpers (fallback logic, origin resolution, spawn validation)

**Dependencies required** (8+ injections):

- `questTemplates` (ConcurrentHashMap)
- `arenaTemplateRegistry`, `arenaPolicyRegistry`, `policyResolver`
- `asyncBuildCoordinator`, `arenaTelemetry`, `arenaConfigSnapshot`
- `overrideManager`, `forceTemplateCapability`

**Risk assessment**:

- Complex error handling with async CompletableFuture chains
- Fallback logic with circuit breaker state management
- Instance cleanup on multiple failure paths
- Telemetry emission at various decision points

**Conclusion**: Extraction would require significant refactoring with high bug risk. The arena preparation flow is intentionally co-located for transactional integrity - a failure at any step must properly clean up instances and emit telemetry.

### QuestSessionQueries Analysis

**Scope**: Lines 2229-2449 (~50 LOC)

**Methods identified**:

- `getActiveSession(UUID)`, `getActiveSession(Player)`
- `isPlayerInQuest(UUID)`, `getActiveSessions()`
- `getAllQuestTemplates()`, `getQuestTemplate(ResourceLocation)`
- `getPlayerStats(UUID)`, `getPolicyForSession()`

**Assessment**: These methods are simple pass-throughs that directly access `activeSessions` and `questTemplates`. Extracting to a facade would add indirection without meaningful benefit.

**Conclusion**: Not worth extracting. The methods are trivial and tightly coupled to manager state.

### Recommendation

**Status**: Deferred

The current architecture with heavy delegation is effective:

- Core logic delegated to EnduranceSessionHandler, WaveManager, PerkSystem
- Arena building delegated to TemplateArenaBuilder, AsyncArenaBuilder
- Persistence delegated to EnduranceQuestPersistence

The remaining code in EnduranceQuestManager serves as coordination logic that benefits from co-location. Further extraction would increase coupling through dependency injection without improving cohesion.

---

## References

- [EnduranceQuestManager.java](../../src/main/java/com/devmod/endurance/EnduranceQuestManager.java)
- [EnduranceSessionHandler.java](../../src/main/java/com/devmod/endurance/EnduranceSessionHandler.java)
- [WaveManager.java](../../src/main/java/com/devmod/endurance/WaveManager.java)
