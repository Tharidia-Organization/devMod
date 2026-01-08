# DevMod Evolution Research Notes

## Research Methodology

- **Approach**: Systematic analysis of codebase structure, git history, and architectural patterns
- **Goal**: Understand evolution to identify improvement opportunities inspired by CraftTweaker

---

## Hypothesis Tree

### H1: DevMod Architecture Evolution

| Hypothesis | Confidence | Evidence | Status |
|------------|------------|----------|--------|
| H1.1: Started as simple utility mod, grew organically | 15% | Namespace migration suggests planned growth | Rejected |
| H1.2: Designed with modular architecture from start | 25% | Clean package separation exists | Partial |
| H1.3: Multiple major rewrites/pivots occurred | **70%** | "enorme cambiamento strutturale" commit, namespace migration, multiple refactor batches | **Confirmed** |

### H2: Current Architectural Maturity

| Hypothesis | Confidence | Evidence | Status |
|------------|------------|----------|--------|
| H2.1: Ready for plugin system like CraftTweaker | 35% | Has ServiceRegistry, TemplateEventDispatcher | Possible |
| H2.2: Needs refactoring before adding extensibility | 25% | Some areas need work, others are mature | Partial |
| H2.3: Some subsystems mature, others need work | **60%** | Arena/Core mature, Config managers procedural | **Confirmed** |

### H3: Primary Improvement Vectors

| Hypothesis | Confidence | Evidence | Status |
|------------|------------|----------|--------|
| H3.1: Event bus would benefit most | 15% | Arena already has TemplateEventDispatcher, Core has Services | Lower priority |
| H3.2: Action/logging system priority | 25% | No structured action system found | Medium priority |
| H3.3: Config handler system priority | **45%** | ConfigManagers are 700-1000 LOC, procedural | **Leading** |
| H3.4: Service abstraction layer priority | 15% | Already has ServiceRegistry pattern! | Lower priority |

---

## Data Collection Log

### Phase 1: Git History Analysis [COMPLETE]

- [x] Commit frequency: 205 commits total, 171 in Dec 2025 alone (rapid development)
- [x] Timeline: 2025-11 to 2026-01 (2-3 months of active development)
- [x] Major events:
  - "enorme cambiamento strutturale" (major structural change)
  - com.frenkvs -> com.devmod namespace migration
  - 14+ micro-refactor batches
  - Arena system rework
  - Endurance Quest System refactor

### Phase 2: Architecture Mapping [COMPLETE]

- [x] Package structure: 40+ top-level packages, 1264 Java files
- [x] Size breakdown:
  - client: 449 files (35%) - UI heavy
  - arena: 154 files (12%) - Complex subsystem
  - endurance: 120 files (9.5%)
  - mailbox: 93 files (7%)
  - telemetry: 71 files (5.6%)
  - runtime: 49 files (4%)
- [x] Architectural patterns found:
  - Interfaces (I prefix): 59 files
  - Services: 31 files
  - Handlers: 38 files
  - Managers: 76 files (dominant pattern!)
- [x] Core infrastructure found:
  - ServiceRegistry + Services (DI-like)
  - Network handlers with domain separation
  - TemplateEventDispatcher (custom event bus)

### Phase 3: Subsystem Deep Dives [COMPLETE]

- [x] Arena system: **Mature** - Has TemplateEventDispatcher, sealed events, async support
- [x] Endurance Mode: **Mature** - Many subsystems with session patterns
- [x] Mailbox system: Functional, uses persistence layer
- [x] Config system: **Needs work** - Procedural managers, no decompose pattern
- [x] Network system: **Well organized** - Domain-specific handlers
- [x] Core: **Has Services/ServiceRegistry** - Already supports DI pattern!

---

## Key Findings

### 1. Event System (Arena)

**Location**: `com.devmod.arena.event`

DevMod already has a sophisticated event system:

```
TemplateEventDispatcher
├── Type-specific listeners (generics)
├── Global listeners
├── Async emission (virtual threads)
├── WeakReference for GC safety
├── ListenerRegistration tokens
└── DispatcherStats
```

**Missing vs CraftTweaker**:

- No Phase system (EARLIEST, NORMAL, LATEST)
- No cancellation support
- No platform wire abstraction
- Only used for Arena templates, not mod-wide

### 2. Service Registry (Core)

**Location**: `com.devmod.core`

DevMod ALREADY has a service layer similar to CraftTweaker!

```java
// Services.java - Type-safe accessors
Services.party().createParty(...)
Services.endurance().startQuest(...)
Services.mailbox().send(...)

// ServiceRegistry.java - DI support
ServiceRegistry.register(PartyManager.class, () -> PartyManager.INSTANCE);
ServiceRegistry.override(PartyManager.class, mockManager); // Testing!
```

**Comparison with CraftTweaker**:

| Feature | CraftTweaker | DevMod |
| --- | --- | --- |
| Service locator | Services.java | Services.java |
| Registration | Java SPI (ServiceLoader) | Explicit register() |
| Override for testing | No | Yes! |
| Lazy init | Yes | Yes |

### 3. Config Managers Pattern

**Example**: WeaponConfigManager.java (982 lines!)

Current approach:

- Static methods everywhere
- Mixed concerns (persistence, validation, component application)
- No decompose/recompose pattern
- Manual field-by-field handling

**Improvement opportunity**: Apply IRecipeHandler pattern

```java
// Current
WeaponStats stats = WeaponConfigManager.getStats(stack);
stats.setAttackDamage(10);
WeaponConfigManager.setSpecificStats(stack, stats);

// Could become
IDecomposedConfig decomposed = handler.decompose(stack);
decomposed.set(WeaponComponents.ATTACK_DAMAGE, 10);
handler.recompose(stack, decomposed);
```

### 4. Endurance Session Pattern

**Location**: `com.devmod.endurance`

Uses a consistent "session" pattern across subsystems:

```java
ComboSystem.INSTANCE.startSession(playerId, questId);
MomentumTracker.INSTANCE.startSession(playerId);
MutatorSystem.INSTANCE.createSession(questId, ...);
PerkSystem.INSTANCE.startSession(playerId, questId, policy);
TensionSystem.INSTANCE.startSession(questId);
```

This is a good pattern that could be formalized with an ISession interface.

---

## Architecture Comparison

| Aspect | CraftTweaker | DevMod Current | Gap |
| --- | --- | --- | --- |
| Event Bus | PhasedEventBus with Wire | TemplateEventDispatcher (Arena only) | Generalize to mod-wide |
| Config/Recipe Handling | IRecipeHandler + decompose | Procedural ConfigManagers | Major refactor needed |
| Plugin System | ICraftTweakerPlugin | None | Not present |
| Service Abstraction | Services.java + SPI | Services.java + Registry | **Already good!** |
| Actions | IAction (traceable, validatable) | None | Not present |
| Platform Independence | Common/Fabric/NeoForge split | Single codebase (NeoForge) | N/A if single platform |

---

## Final Recommendations

### Priority 1: Config Handler System (High Impact, Medium Effort)

Transform ConfigManagers from procedural to handler-based:

```java
public interface IConfigHandler<T, S> {
    Optional<IDecomposedConfig> decompose(T source, S stats);
    Optional<S> recompose(IDecomposedConfig config);
    String dumpToString(S stats);
    boolean validate(S stats, Logger logger);
}

@ConfigHandler.For(WeaponStats.class)
public class WeaponConfigHandler implements IConfigHandler<ItemStack, WeaponStats> {
    // ...
}
```

**Benefits**:

- Reduces 1000-line managers to ~200-line handlers
- Enables generic UI for all config types
- Makes validation centralized and testable

### Priority 2: Generalize Event System (Medium Impact, Low Effort)

Promote TemplateEventDispatcher to a mod-wide pattern:

```java
public final class DevModEvents {
    public static final IEventBus<QuestStartEvent> QUEST_START = ...;
    public static final IEventBus<WaveCompleteEvent> WAVE_COMPLETE = ...;
    public static final IEventBus<ArenaBuiltEvent> ARENA_BUILT = ...;
}
```

Add Phase support for ordering:

```java
DevModEvents.QUEST_START.registerHandler(Phase.EARLIEST, event -> {
    // Setup
});
DevModEvents.QUEST_START.registerHandler(Phase.NORMAL, event -> {
    // Main logic
});
```

### Priority 3: Action System (Medium Impact, Medium Effort)

Add IAction for trackable modifications:

```java
public interface IAction {
    void apply();
    void undo();  // Optional
    String describe();
    boolean validate(Logger logger);
}

// Usage
IAction action = new ActionModifyWeaponDamage(stack, oldDamage, newDamage);
if (action.validate(logger)) {
    action.apply();
    ActionHistory.record(action);  // For debugging
}
```

### Priority 4: Session Interface (Low Impact, Low Effort)

Formalize the session pattern already in use:

```java
public interface ISession {
    UUID getId();
    Instant getStartTime();
    boolean isActive();
    void end();
}

public interface ISessionManager<T extends ISession> {
    T startSession(UUID entityId, Object... params);
    Optional<T> getSession(UUID entityId);
    void endSession(UUID entityId);
}
```

---

## Progress Notes

### Session 1 Analysis

- Git history reveals rapid, intense development (170+ commits in 1 month)
- Multiple structural changes indicate evolving architecture
- Arena system shows most architectural maturity
- Config system is functional but procedural
- "Manager" pattern dominates codebase
- **Surprise finding**: ServiceRegistry already exists and is well-designed!

### Confidence Calibration

- Initial H1.3 (rewrites) was 40%, now 70% - evidence confirmed
- H2.3 (mixed maturity) confirmed at 60%
- H3.3 (config handlers) confirmed as priority at 45%
- H3.4 (service layer) dropped from 25% to 15% - already exists!

### Key Insight

DevMod is more architecturally mature than initially expected. The core infrastructure (Services, Events, Network) is solid. The main improvement vector is the Config system, which would benefit most from CraftTweaker's decompose/recompose pattern.

