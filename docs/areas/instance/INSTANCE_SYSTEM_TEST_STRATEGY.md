# Instance System - Test Strategy

> **Status**: CURRENT (automated coverage; integration gaps tracked)
> **Last Verified**: 2025-12-26

---

## 1. Automated Coverage (Direct Tests)

| Area | Test | Coverage |
|------|------|----------|
| Instance state machine | `InstanceStateDirectTest` | Allowed transitions + lifecycle flags |
| Player recovery state | `PlayerInstanceStateDirectTest` | Transition rules + recovery flags |
| InstanceData rules | `InstanceDataDirectTest` | Capacity, destruction scheduling, invalid transitions |
| Snapshot persistence | `PlayerInstanceSnapshotDirectTest` | NBT + file roundtrip |
| Registry mappings | `InstanceRegistryDirectTest` | Player + dimension lookup |

These tests exercise the **actual runtime classes** in `com.devmod.runtime` (no proxies).

---

## 2. Remaining Validation Gaps

The following behaviors require server/integration harnesses and are not covered by unit tests:

- Dynamic dimension creation + teardown on a live server thread.
- Teleport sequencing with real ServerPlayer objects.
- Endurance-specific overrides in `InstanceEventHandler`.
- Full recovery flow with real inventory/effects.

---

## 3. Execution

Run direct instance validations:

```
./gradlew test --tests 'com.devmod.runtime.*DirectTest'
```

---

## Cross-References

- `docs/DOCS_BEHAVIOR_MATRIX.md`
- `docs/areas/instance/README.md`

