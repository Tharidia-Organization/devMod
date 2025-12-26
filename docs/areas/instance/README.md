# Instance System

> **Audit Date**: 2025-12-26
> **Status**: CURRENT (code-aligned)
> **Risk Level**: MEDIUM (async dimension creation, recovery IO)

---

## 1. Purpose

The Instance System creates temporary quest dimensions and guarantees recovery when teleports or sessions fail:

- **On-demand dimensions**: creates `devmod:instance_<uuid>` worlds for quest sessions
- **Safe teleport**: immediate or 10-second countdown with stale-teleport recovery
- **Snapshot recovery**: disk-backed player snapshots for disconnect/crash handling
- **Cleanup**: registry + destruction queue for dimension teardown

---

## 2. Key Concepts

| Concept | Description | File Reference |
|---------|-------------|----------------|
| **InstanceData** | Instance model + player list + destruction schedule | `src/main/java/com/devmod/runtime/InstanceData.java` |
| **InstanceState** | Instance lifecycle state machine | `src/main/java/com/devmod/runtime/InstanceState.java` |
| **PlayerInstanceState** | Player recovery state machine | `src/main/java/com/devmod/runtime/PlayerInstanceState.java` |
| **PlayerInstanceSnapshot** | NBT snapshot for recovery | `src/main/java/com/devmod/runtime/PlayerInstanceSnapshot.java` |
| **InstanceRegistry** | Instance/payer mappings + persistence | `src/main/java/com/devmod/runtime/InstanceRegistry.java` |
| **DynamicDimensionManager** | Dimension create/teleport/destroy | `src/main/java/com/devmod/runtime/DynamicDimensionManager.java` |
| **RecoverySystem** | Snapshot IO + recovery routines | `src/main/java/com/devmod/runtime/RecoverySystem.java` |
| **InstanceEventHandler** | Server/player event hooks | `src/main/java/com/devmod/runtime/InstanceEventHandler.java` |

---

## 3. Components (Runtime Package)

```
com.devmod.runtime
├── InstanceManager.java         # Orchestrator
├── InstanceRegistry.java        # Persistence + mappings
├── InstanceData.java            # Instance model
├── InstanceState.java           # Instance lifecycle enum
├── PlayerInstanceSnapshot.java  # Recovery snapshot
├── PlayerInstanceState.java     # Player lifecycle enum
├── RecoverySystem.java          # Snapshot save/load + recovery
├── DynamicDimensionManager.java # Dimension create/teleport/destroy
└── InstanceEventHandler.java    # Server/player events
```

Integration points:
- `com.devmod.endurance.InstanceArenaManager` bridges Endurance sessions to InstanceManager.
- `com.devmod.arena.registry.InstanceSettingsValidator` validates arena instance settings.

---

## 4. Entrypoints

### Event Hooks
| Event | Handler | Purpose |
|-------|---------|---------|
| `ServerStartedEvent` | `InstanceEventHandler.onServerStarted()` | Initializes instance subsystem |
| `ServerStoppingEvent` | `InstanceEventHandler.onServerStopping()` | Shutdown + cleanup |
| `ServerTickEvent.Post` | `InstanceEventHandler.onServerTick()` | Teleport ticks + destruction queue |
| `PlayerLoggedInEvent` | `InstanceEventHandler.onPlayerLoggedIn()` | Snapshot recovery check |
| `PlayerLoggedOutEvent` | `InstanceEventHandler.onPlayerLoggedOut()` | Force-end instance if needed |
| `LivingDeathEvent` | `InstanceEventHandler.onPlayerDeath()` | End instance on death (unless Endurance overrides) |
| `PlayerChangedDimensionEvent` | `InstanceEventHandler.onPlayerChangeDimension()` | Detect unexpected exits |

### Core API
| Method | File | Purpose |
|--------|------|---------|
| `startInstanceQuestImmediate()` | `InstanceManager.java` | Immediate teleport flow |
| `startInstanceQuest()` | `InstanceManager.java` | Countdown teleport flow |
| `endInstanceQuest()` | `InstanceManager.java` | End quest + recovery + destruction |
| `forceEndPlayerInstances()` | `InstanceManager.java` | Disconnect cleanup |
| `tick()` | `InstanceManager.java` | Countdown processing + stale teleports |

---

## 5. Runtime Flow (Current Behavior)

1. **Start quest**: `InstanceManager.startInstanceQuest*()` creates `InstanceData`, writes snapshots, maps players, saves registry.
2. **Create dimension**: `DynamicDimensionManager.createDimensionAsync()` creates `devmod:instance_<uuid>`.
3. **Success path**: instance state -> `READY`; teleport immediate or start 10s countdown.
4. **Teleport**: `teleportToInstance()` updates snapshot to `IN_INSTANCE` and instance to `ACTIVE`.
5. **End quest**: `endInstanceQuest()` sets state `COMPLETING`, runs recovery, schedules destruction.
6. **Destroy**: `InstanceRegistry.processPendingDestructions()` runs every 100 ticks; `DynamicDimensionManager` deletes dimension and unregisters.

---

## 6. Persistence & Paths

- **Snapshots**: `config/devmod/snapshots/<playerUuid>.dat`
- **Registry**: `config/devmod/instances.json`
- **Dimensions**: `world/dimensions/devmod/instance_<uuidWithoutDashes>`

---

## 7. Failure Handling (Implemented)

- **Dimension creation returns null**: recover online players and remove instance.
- **Stale countdown (>30s)**: recovery triggered for that player.
- **Logout in instance**: `forceEndPlayerInstances()` cleans membership and schedules destruction.
- **Unexpected dimension exit**: handled in `InstanceEventHandler.onPlayerChangeDimension()`.

---

## 8. Known Gaps (Code-Verified)

- `createDimensionAsync()` has no timeout/backoff guard.
- Invalid state transitions are allowed (logged, not blocked).
- Snapshot cleanup is per-player; no periodic pruning of orphaned snapshots.

---

## 9. Automated Validation

| Behavior | Test |
|----------|------|
| Instance state transitions | `InstanceStateDirectTest` |
| Player state transitions | `PlayerInstanceStateDirectTest` |
| InstanceData lifecycle rules | `InstanceDataDirectTest` |
| Snapshot NBT + file roundtrip | `PlayerInstanceSnapshotDirectTest` |
| Registry mapping/dimension lookup | `InstanceRegistryDirectTest` |

---

## Cross-References

- `docs/areas/arena/README.md`
- `docs/areas/endurance/README.md`
- `docs/cross_cutting/CONCURRENCY.md`

