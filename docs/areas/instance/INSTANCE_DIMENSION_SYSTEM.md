# Instance Dimension System - Implementation Reference

> Last updated: 2025-12-26
> Status: CURRENT (verified against code)

## Responsibilities

- Create, teleport to, and destroy instance dimensions via `DynamicDimensionManager`.
- Track instance mappings and cleanup via `InstanceRegistry`.
- Persist player snapshots and perform recovery via `RecoverySystem`.
- Orchestrate lifecycle and teleport flow via `InstanceManager`.

## Dimension Creation + Teleport

- `createDimensionAsync()` schedules `createDimensionSync()` on the server thread for thread safety.
- Dimension IDs use `devmod:instance_<uuidWithoutDashes>`.
- Dimensions are created as void worlds with a flat bedrock platform (see `createVoidDimension`).
- `InstanceManager` supports immediate teleport or a 10-second countdown (`TELEPORT_COUNTDOWN_TICKS = 200`).
- Teleport requests are considered stale after 30 seconds (`TeleportRequest.MAX_AGE_MS`).

## State Models

- `InstanceState`: CREATING, READY, ACTIVE, COMPLETING, DESTROYING, DESTROYED (transitions validated by `canTransitionTo`).
- `PlayerInstanceState`: NORMAL, PREPARING, IN_TRANSIT, IN_INSTANCE, RETURNING (NORMAL is always a recovery target).

## Scheduling + Cleanup

- `InstanceEventHandler` ticks `InstanceManager` every server tick.
- Pending destructions are processed every 100 ticks (`DESTRUCTION_CHECK_INTERVAL`).
- `DynamicDimensionManager` handles teardown and dimension registry cleanup.

## Persistence Paths

- Snapshots: `config/devmod/snapshots/<playerUuid>.dat`.
- Registry: `config/devmod/instances.json`.
- Dimension data: `world/dimensions/devmod/instance_<uuidWithoutDashes>`.

## Automated Validation

- See `docs/areas/instance/INSTANCE_SYSTEM_TEST_STRATEGY.md`.
