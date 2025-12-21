# DevMod Bug Log

> DEPRECATED: consolidated into `docs/BUG_LOG.md`.


**Last Updated:** 2025-12-10
**Format Version:** 1.0

---

## Active Bugs

*No active bugs at this time.*

---

## Resolved Bugs

### BUG-001: Thread Safety in DynamicDimensionManager

**Severity:** HIGH
**Status:** RESOLVED
**Found in:** L0 Code Review
**Resolved:** 2025-12-10

**Symptom:**
`DynamicDimensionManager.createDimensionAsync()` used `CompletableFuture.supplyAsync()` with server executor, but the actual dimension creation modifies `MinecraftServer.levels` which is not thread-safe.

**Reproduction Steps:**
1. Start quest while server is under load
2. Multiple dimension creations could race
3. ConcurrentModificationException possible

**Expected:**
Dimension creation should be thread-safe.

**Actual:**
Race condition possible when modifying `MinecraftServer.levels` map.

**Root Cause:**
The `supplyAsync()` call was executing on a thread pool, not guaranteeing server thread execution for the map modification.

**Fix:**
Changed `createDimensionAsync()` to use `server.execute()` which schedules the work on the server thread, then completes the future:

```java
public CompletableFuture<ResourceKey<Level>> createDimensionAsync(UUID instanceId, String arenaId) {
    CompletableFuture<ResourceKey<Level>> future = new CompletableFuture<>();

    // Schedule creation on server thread to ensure thread safety
    server.execute(() -> {
        try {
            ResourceKey<Level> result = createDimensionSync(instanceId, arenaId);
            future.complete(result);
        } catch (Exception e) {
            LOGGER.error("[DynamicDim] Failed to create dimension", e);
            future.complete(null);
        }
    });

    return future;
}
```

**Files Changed:**
- `DynamicDimensionManager.java:105-127`: Refactored async creation
- `DynamicDimensionManager.java:336-355`: Same pattern for destruction

**Regression Test:**
- `InstanceFlowValidationTest.RaceConditionTests`
- `MultiplayerConcurrencyTest.ThreadSafeRegistryOperations`

**Impact Assessment:**
- DynamicDimensionManager: Direct fix
- InstanceManager: Uses createDimensionAsync, benefits from fix
- No breaking API changes

---

### BUG-002: State Transition Validation Missing

**Severity:** MEDIUM
**Status:** RESOLVED
**Found in:** L0 Code Review
**Resolved:** 2025-12-10

**Symptom:**
`InstanceData.setState()` and `PlayerInstanceSnapshot.setState()` allowed any state transition without validation, potentially leading to invalid state machines.

**Reproduction Steps:**
1. Create instance in CREATING state
2. Call `setState(DESTROYED)` directly
3. No warning or validation

**Expected:**
Invalid state transitions should be logged/warned.

**Actual:**
Any transition was silently accepted.

**Root Cause:**
Original `setState()` was a simple assignment without validation.

**Fix:**
Added state transition validation with logging:

```java
public boolean setState(InstanceState newState) {
    InstanceState oldState = this.state;

    if (oldState == newState) {
        return true;
    }

    boolean valid = oldState.canTransitionTo(newState);
    if (!valid) {
        LOGGER.warn("[Instance] {} INVALID state transition: {} -> {}",
            instanceId, oldState, newState);
    }

    this.state = newState;
    return valid;
}
```

**Files Changed:**
- `InstanceData.java:93-121`: Added validation to setState()
- `InstanceData.java:127-131`: Added forceState() for recovery
- `PlayerInstanceSnapshot.java:98-126`: Added validation to setState()

**Regression Test:**
- `InstanceSystemLogicTest.InstanceStateTests`
- `InstanceSystemLogicTest.PlayerInstanceStateTests`

**Impact Assessment:**
- Better debugging of state machine issues
- No breaking changes (invalid transitions still allowed but logged)

---

### BUG-003: Player Disconnect During Dimension Creation

**Severity:** HIGH
**Status:** RESOLVED
**Found in:** L0 Code Review
**Resolved:** 2025-12-10

**Symptom:**
If a player disconnected during the async dimension creation phase, the code would try to recover a null player, causing potential NPE.

**Reproduction Steps:**
1. Player starts quest
2. Player disconnects during dimension creation (before teleport)
3. Dimension creation completes
4. Code tries to teleport disconnected player

**Expected:**
System should detect disconnect and skip teleport, preserving snapshot for login recovery.

**Actual:**
Potential NPE when iterating over players who disconnected.

**Root Cause:**
`InstanceManager.startInstanceQuestInternal()` captured player references in `allPlayers` list but didn't re-validate them after async dimension creation.

**Fix:**
Added player online check after dimension creation:

```java
for (ServerPlayer originalPlayer : allPlayers) {
    ServerPlayer onlinePlayer = server.getPlayerList().getPlayer(originalPlayer.getUUID());
    if (onlinePlayer == null) {
        LOGGER.warn("[InstanceManager] Player {} disconnected before teleport",
            originalPlayer.getUUID());
        instance.removePlayer(originalPlayer.getUUID());
        InstanceRegistry.INSTANCE.unmapPlayer(originalPlayer.getUUID());
        continue;
    }
    // Proceed with teleport for online player
}
```

**Files Changed:**
- `InstanceManager.java:230-285`: Added online check after dimension creation

**Regression Test:**
- `ErrorRecoveryScenarioTest.RecoveryTriggerTests`
- `InstanceFlowValidationTest.ErrorHandlingTests`

**Impact Assessment:**
- InstanceManager: Direct fix
- RecoverySystem: Properly invoked for disconnected players
- Snapshots preserved for login recovery

---

### BUG-004: Stale Teleport Requests

**Severity:** MEDIUM
**Status:** RESOLVED
**Found in:** L0 Code Review
**Resolved:** 2025-12-10

**Symptom:**
`TeleportRequest` objects in `pendingTeleports` map could become stale if something went wrong, never being cleaned up.

**Reproduction Steps:**
1. Player starts quest with countdown
2. Some edge case prevents teleport execution
3. TeleportRequest stays in map indefinitely

**Expected:**
Stale requests should be cleaned up automatically.

**Actual:**
No timeout mechanism existed.

**Root Cause:**
`TeleportRequest` had no creation timestamp or staleness check.

**Fix:**
Added staleness detection to TeleportRequest:

```java
private static class TeleportRequest {
    final long createdAt;
    static final long MAX_AGE_MS = 30_000; // 30 seconds

    boolean isStale() {
        return System.currentTimeMillis() - createdAt > MAX_AGE_MS;
    }
}
```

And cleanup in tick():

```java
if (request.isStale()) {
    LOGGER.warn("[InstanceManager] Removing stale teleport request");
    iterator.remove();
    // Trigger recovery
}
```

**Files Changed:**
- `InstanceManager.java:582-606`: Added createdAt and isStale() to TeleportRequest
- `InstanceManager.java:340-354`: Added stale check in tick()

**Regression Test:**
- `EdgeCaseStressTest.TimingEdgeCases`

**Impact Assessment:**
- InstanceManager: Direct fix
- Better cleanup of edge case scenarios

---

### BUG-005: Empty Instance in READY State Not Destroyed

**Severity:** LOW
**Status:** RESOLVED
**Found in:** L0 Code Review
**Resolved:** 2025-12-10

**Symptom:**
If all players disconnected during dimension creation (before ACTIVE state), the instance would be left in READY state with no players, not scheduled for destruction.

**Reproduction Steps:**
1. Start party quest
2. All players disconnect during dimension creation
3. Dimension created, instance in READY state
4. Instance has 0 players but not destroyed

**Expected:**
Empty instances should be destroyed regardless of state.

**Actual:**
`removePlayer()` only scheduled destruction for ACTIVE instances.

**Root Cause:**
Condition in `InstanceData.removePlayer()` was too restrictive.

**Fix:**
Extended destruction check to include READY state:

```java
if (currentPlayers.isEmpty() &&
    (state == InstanceState.ACTIVE || state == InstanceState.READY)) {
    scheduleDestruction();
}
```

Also added check in InstanceManager:

```java
if (instance.isEmpty()) {
    LOGGER.warn("[InstanceManager] All players disconnected, destroying instance");
    InstanceRegistry.INSTANCE.scheduleDestruction(instanceId);
}
```

**Files Changed:**
- `InstanceData.java:125-137`: Extended destruction condition
- `InstanceManager.java:277-282`: Added empty check after teleport phase

**Regression Test:**
- `EdgeCaseStressTest.BoundaryConditionTests.zeroPlayersTriggersDestruction`

**Impact Assessment:**
- InstanceData: Direct fix
- InstanceManager: Additional safety check
- Prevents orphaned instances

---

## Bug Statistics

| Severity | Open | Resolved | Total |
|----------|------|----------|-------|
| CRITICAL | 0 | 0 | 0 |
| HIGH | 0 | 3 | 3 |
| MEDIUM | 0 | 2 | 2 |
| LOW | 0 | 1 | 1 |
| **Total** | **0** | **5** | **5** |

---

## Template for New Bugs

```markdown
### BUG-XXX: [Short Title]

**Severity:** CRITICAL | HIGH | MEDIUM | LOW
**Status:** OPEN | IN_PROGRESS | RESOLVED
**Found in:** [Test Level/Phase]
**Resolved:** [Date or N/A]

**Symptom:**
[Observable behavior]

**Reproduction Steps:**
1. [Step 1]
2. [Step 2]
3. [Step 3]

**Expected:**
[What should happen]

**Actual:**
[What actually happens]

**Root Cause:**
[Technical explanation]

**Fix:**
[Code changes made]

**Files Changed:**
- [file1.java:lines]: [reason]
- [file2.java:lines]: [reason]

**Regression Test:**
[Test ID that validates fix]

**Impact Assessment:**
[What systems might be affected]
```
