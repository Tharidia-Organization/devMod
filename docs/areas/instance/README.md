# Instance System

> Last updated: 2025-12-26
> Status: CURRENT (verified against code)

The Instance system creates temporary quest dimensions, tracks player state, and guarantees recovery and cleanup.

## Scope

- Dynamic instance dimension creation and teardown
- Player snapshot/recovery and state tracking
- Registry of instances and participants
- Server lifecycle and tick hooks

## Core Runtime Components (`com.devmod.runtime`)

- `InstanceManager` (lifecycle, quest start/stop, tick)
- `InstanceRegistry` (mappings + persistence)
- `InstanceData` (instance model)
- `InstanceState` / `PlayerInstanceState` (lifecycle enums)
- `DynamicDimensionManager` (dimension create/teleport/destroy)
- `RecoverySystem` (snapshot IO + recovery)
- `InstanceEventHandler` (server + player event hooks)

## Entry Points

- `InstanceManager`: `startInstanceQuestImmediate`, `startInstanceQuest`, `endInstanceQuest`, `tick`, `shutdown`.
- `InstanceEventHandler`: hooks server start/stop, tick, and player events.

## Persistence + Paths

- Snapshots: `config/devmod/snapshots/<playerUuid>.dat` (RecoverySystem).
- Registry: `config/devmod/instances.json` (InstanceRegistry).
- Dimensions: `devmod:instance_<uuidWithoutDashes>` (DynamicDimensionManager), data under `world/dimensions/devmod/instance_<uuidWithoutDashes>`.

## Integration

- `com.devmod.endurance.InstanceArenaManager` bridges endurance quests to instances.
- `com.devmod.arena.registry.InstanceSettingsValidator` validates arena instance settings.

## Automated Validation

- Direct tests: `InstanceStateDirectTest`, `PlayerInstanceStateDirectTest`, `InstanceDataDirectTest`, `InstanceRegistryDirectTest`, `PlayerInstanceSnapshotDirectTest`.
- Scenario/integration: `InstanceFlowValidationTest`, `RecoverySystemValidationTest`, `MultiplayerConcurrencyTest`, `PartyCoordinationTest`, `QuestLifecycleSimulationTest`, `ServerRestartSimulationTest`.
