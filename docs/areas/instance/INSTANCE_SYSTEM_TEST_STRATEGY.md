# Instance System - Test Strategy

> Last updated: 2025-12-26
> Status: CURRENT (verified against code)

## Automated Coverage

### Direct Tests (State + Persistence)

- `InstanceStateDirectTest`
- `PlayerInstanceStateDirectTest`
- `InstanceDataDirectTest`
- `InstanceRegistryDirectTest`
- `PlayerInstanceSnapshotDirectTest`
- `SnapshotDataValidationTest`
- `DataSerializationTest`

### Scenario + Validation Tests

- `InstanceFlowValidationTest`
- `RecoverySystemValidationTest`
- `InstanceValidationTest`
- `InstanceSystemLogicTest`
- `QuestLifecycleSimulationTest`
- `PartyCoordinationTest`
- `MultiplayerConcurrencyTest`
- `MultiplayerIsolationValidationTest`
- `ServerRestartSimulationTest`
- `ErrorHandlingValidationTest`
- `ErrorRecoveryScenarioTest`
- `EdgeCaseStressTest`
- `RealUserJourneyTest`

### Game Tests

- `com.devmod.gametest.InstanceSystemGameTests`

## Cross-References

- `docs/areas/instance/README.md`
- `docs/areas/instance/INSTANCE_DIMENSION_SYSTEM.md`
