# Instance Dimension System - Implementation Reference

> Last updated: 2025-12-26
> Status: NEEDS_VERIFICATION (last verified 2025-12-26)

---

## 1. Scope

This document captures the **current behavior** of the Instance Dimension System as implemented in
`com.devmod.runtime`. It focuses on what the code does today, not future plans.

---

## 2. Core Flow (Current)

1. **Quest start**
   - `InstanceManager.startInstanceQuest()` / `startInstanceQuestImmediate()`
   - Creates `InstanceData`, writes `PlayerInstanceSnapshot`, maps players in `InstanceRegistry`.

2. **Dimension creation**
   - `DynamicDimensionManager.createDimensionAsync()` schedules creation on server thread.
   - Dimension name: `devmod:instance_<uuidWithoutDashes>`.
   - Void world generator uses a flat bedrock layer as platform.

3. **Teleport**
   - Immediate mode: teleport as soon as dimension exists.
   - Countdown mode: 10 seconds (200 ticks), messages at 5s/3s/1s.
   - Stale teleports are cleared after 30s with recovery.

4. **Quest end**
   - `InstanceManager.endInstanceQuest()` sets `COMPLETING`, runs recovery for each player.
   - Instance is marked for destruction and queued.

5. **Destroy**
   - `InstanceRegistry.processPendingDestructions()` runs every 100 ticks.
   - `DynamicDimensionManager.destroyDimensionAsync()` removes dimension and data.

---

## 3. State Machines

### InstanceState
```
CREATING -> READY -> ACTIVE -> COMPLETING -> DESTROYING -> DESTROYED
```
- Invalid transitions are logged but **allowed** to prevent deadlocks.

### PlayerInstanceState
```
NORMAL -> PREPARING -> IN_TRANSIT -> IN_INSTANCE -> RETURNING -> NORMAL
```
- Any state can transition to `NORMAL` (recovery path).

---

## 4. Recovery Rules (Implemented)

- **PREPARING / IN_TRANSIT**: teleport failed or interrupted -> restore snapshot.
- **IN_INSTANCE**: quest failed -> restore snapshot.
- **RETURNING**: return teleport interrupted -> restore snapshot.

Recovery deletes the snapshot and unmaps the player from the registry.

---

## 5. Persistence Paths

- Snapshots: `config/devmod/snapshots/<playerUuid>.dat`
- Registry: `config/devmod/instances.json`
- Dimension data: `world/dimensions/devmod/instance_<uuidWithoutDashes>`

---

## 6. Known Gaps (Code-Verified)

- No timeout/backoff around `createDimensionAsync()`.
- Forced state transitions are allowed (logged only).
- Snapshot pruning is per-player only (no periodic cleanup job).

---

## 7. Automated Validation

| Behavior | Test |
|----------|------|
| Instance lifecycle + scheduling | `InstanceDataDirectTest` |
| State machines | `InstanceStateDirectTest`, `PlayerInstanceStateDirectTest` |
| Snapshot NBT + file IO | `PlayerInstanceSnapshotDirectTest` |
| Registry mappings | `InstanceRegistryDirectTest` |

---

## Cross-References

- `docs/areas/instance/README.md`
- `docs/areas/endurance/README.md`
- `docs/areas/arena/README.md`
