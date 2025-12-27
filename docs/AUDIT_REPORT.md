# Audit Report

> Last updated: 2025-12-26
> Status: HISTORICAL

> **Audit Date**: 2024-12-23
> **Auditor**: Claude Code
> **Scope**: Full codebase audit of DevMod

---

## Executive Summary

DevMod is a sophisticated NeoForge mod with **862 Java classes** across multiple systems. The audit identified **28 gaps** across 8 areas, with **5 critical issues** requiring immediate attention.

| Metric | Value |
|--------|-------|
| Total Classes | 862 |
| Test Classes | 114 |
| Lines of Code | ~50,000+ |
| Areas Audited | 8 |
| Critical Gaps | 5 |
| High Gaps | 12 |
| Medium Gaps | 11 |

---

## Area Status Overview

| Area | Status | Risk | Critical Issues |
|------|--------|------|-----------------|
| [[areas/arena/README|Arena System]] | PARTIAL | HIGH | TOCTOU race, missing PolicyEngine |
| [[areas/endurance/README|Endurance System]] | PARTIAL | MEDIUM | Session orphan, wave sync race |
| [[areas/instance/README|Instance System]] | PARTIAL | MEDIUM | Async exception handling |
| [[areas/telemetry/README|Telemetry System]] | PARTIAL | MEDIUM | Missing event hooks |
| [[areas/radial/README|Radial/UX System]] | DONE | HIGH | RadialAction API mismatch |
| [[areas/client_server/README|Client/Server]] | PARTIAL | HIGH | Missing @OnlyIn annotations |
| [[areas/config/README|Config System]] | PARTIAL | MEDIUM | No JSON schema validation |
| [[areas/tools/README|Tools/QA]] | DONE | LOW | Good coverage |

---

## Critical Issues (P0)

### 1. RadialAction API Mismatch

**Location**: `ui/radial/RadialMenuItem.java:36-75`

**Problem**: RadialMenuItem calls methods that don't exist in RadialAction (actions package).

**Impact**: Runtime failure when menu items are executed.

**Fix**: Unify the two RadialAction classes or update method signatures.

---

### 2. Client/Server Annotation Missing

**Location**: `network/ClientConfigFeedback.java`

**Problem**: Class uses `Minecraft.getInstance()` without `@OnlyIn(Dist.CLIENT)`.

**Impact**: `ClassNotFoundException` crash on dedicated server.

**Fix**: Add `@OnlyIn(Dist.CLIENT)` annotation.

---

### 3. TOCTOU Race in Lock Cleanup

**Location**: `PolicyResolver.java:464-466`

**Problem**: Check-then-remove race condition in `cleanupStaleLocks()`.

```java
// Between these two operations, another thread could queue
if (!lockEntry.lock.isLocked() && !lockEntry.lock.hasQueuedThreads()) {
    playerLocks.remove(playerId);
}
```

**Impact**: Premature lock removal, concurrent access issues.

**Fix**: Use `ConcurrentHashMap.computeIfAbsent()` with atomic operation.

---

### 4. Session Orphan in Endurance

**Location**: `EnduranceQuestManager.java:1831-1836`

**Problem**: If async arena build fails, placeholder session remains orphaned.

**Impact**: Memory leak, stuck sessions.

**Fix**: Add timeout cleanup for pending sessions.

---

### 5. Missing Telemetry Event Hooks

**Location**: `PlayerAttributeTelemetryService.java`

**Problem**: `logAbility()` method exists but is never called.

**Impact**: Zero data on ability usage (dash, dodge, stamina).

**Fix**: Add event hooks for ability events.

---

## High Priority Issues (P1)

### Arena System

| Issue | Location | Impact |
|-------|----------|--------|
| PolicyEngine not found | Docs reference | Documentation mismatch |
| No BuildHistoryStore | `ArenaBuilder.java:77` | No metrics learning |
| 3 lock mechanisms | Various | Deadlock potential |
| Hardcoded timeouts | 5s, 30s values | Inflexible |

### Endurance System

| Issue | Location | Impact |
|-------|----------|--------|
| Wave sync race | `WaveManager.java:151-169` | Wave stuck |
| Objective replacement | `WaveManager.java:212-222` | Uncompletable objectives |
| Combo carryover | `ComboSystem.java` | Artificial scores |

### Telemetry System

| Issue | Location | Impact |
|-------|----------|--------|
| Circuit breaker silent | `DuckDBBatchWriter.java` | Undetected failures |
| Memory leak risk | 12+ services | Entity cleanup issues |
| No migration tests | V1→V8 path | Data corruption risk |

### Client/Server

| Issue | Location | Impact |
|-------|----------|--------|
| ConfigNetworkHandler | Direct Minecraft import | Server crash |
| 5 client mixins | No @OnlyIn | Best practice violation |
| Static client imports | EnduranceNetworkHandler | ClassNotFoundException |

---

## Medium Priority Issues (P2)

### Config System

| Issue | Description |
|-------|-------------|
| No JSON schema | Config corruption not detected |
| No atomic writes | Data loss on crash |
| Silent failures | Catch-all exception handling |

### Instance System

| Issue | Description |
|-------|-------------|
| No dimension rollback | Orphaned files on partial creation |
| setState no validation | Invalid transitions allowed |
| Snapshot orphans | Files never auto-deleted |

### Radial/UX

| Issue | Description |
|-------|-------------|
| Favorites not persisted | Lost on restart |
| Usage stats not saved | No frequency ranking |
| Search no fuzzy | Simple match only |

---

## Documentation vs Code Mismatches

| Documentation | Code Reality |
|---------------|--------------|
| PolicyEngine class | NOT FOUND - logic in PolicyResolver |
| BuilderState class | Actually BuildTransaction |
| ArenaActionRegistry | Inline in ArenaCommands |
| Persistence layer | Only BuildHistoryStore interface |

---

## Test Coverage

| System | Unit Tests | GameTests | Coverage |
|--------|------------|-----------|----------|
| Instance | 114 | 12 | Good |
| Arena | ~230 | 5 | Good |
| Endurance | ~50 | 0 | Medium |
| Telemetry | ~30 | 0 | Medium |
| UI/Radial | ~20 | 0 | Low |

**Overall**: 655 unit tests, 51 GameTests, 7 CI gates

---

## Recommendations by Priority

### Immediate (This Week)

1. Fix RadialAction API unification
2. Add @OnlyIn to ClientConfigFeedback
3. Fix TOCTOU race in PolicyResolver
4. Add session timeout cleanup
5. Annotate all client mixins

### Short-term (This Month)

6. Implement ability event hooks
7. Add JSON schema validation
8. Consolidate lock mechanisms
9. Add atomic config writes
10. Implement favorites persistence

### Medium-term (Next Quarter)

11. Implement BuildHistoryStore
12. Add migration path tests
13. Improve telemetry coverage
14. Add performance baselines
15. Implement DuckDB repair

---

## Risk Matrix

```
         │ Low Impact │ Medium Impact │ High Impact │
─────────┼────────────┼───────────────┼─────────────┤
Critical │            │               │ P0-1,2,3,4,5│
─────────┼────────────┼───────────────┼─────────────┤
High     │            │ P1-Arena      │ P1-Client   │
         │            │ P1-Endurance  │             │
─────────┼────────────┼───────────────┼─────────────┤
Medium   │ P2-Radial  │ P2-Config     │             │
         │            │ P2-Instance   │             │
─────────┼────────────┼───────────────┼─────────────┤
Low      │ Tools/QA   │               │             │
```

---

## Conclusion

DevMod has a **solid foundation** with good test coverage and CI infrastructure. The critical issues are concentrated in:

1. **Client/Server boundary** - Missing annotations
2. **Radial menu** - API inconsistency
3. **Concurrency** - Race conditions in locks

Addressing the 5 P0 issues should be prioritized before any feature work.

---

## Cross-References

- [[MOC]] - Master index
- [[PROJECT_TOPOLOGY]] - Code structure
- [[ENTRYPOINTS]] - Entry points
- [[TRACEABILITY_MATRIX]] - Feature tracing
- [[GLOSSARY]] - Terms

---

*Generated from codebase analysis - 2024-12-23*
