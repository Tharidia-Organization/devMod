# Core Critical Files Review

**Date**: 2025-12-26
**Reviewer**: Quality Pass Automation

## Summary

| Severity | Count | Status |
|----------|-------|--------|
| P0 | 0 | N/A |
| P1 | 0 | N/A |
| P2 | 2 | 1 fixed, 1 open |

---

## Fixes Applied

### 1. Nullable field access in template resolution
**File**: `EnduranceQuestManager.java`

**Issue**: `forceTemplateCapability` is annotated @Nullable and was accessed directly in `resolveArenaTemplate`.
**Fix**: Copy to a local variable before the null check to avoid racy reads and satisfy null-safety guidance.

---

## Open TODOs / Deferred

1. **ClientUiBridgeImpl.openEnduranceQuestScreen ignores templateId (P2)**
   - The interface documents a template ID parameter, but the implementation does not use it.
   - Recommendation: either pass the ID to the screen (if supported) or document that the ID is unused.

2. **ClientUiBridgeImpl.toggleDebugOverlay is a TODO (P2)**
   - Debug overlay integration is stubbed out and should be implemented when the overlay is available.

---

## Files Reviewed

| Area | Files |
|------|-------|
| Endurance core flow | `EnduranceQuestManager.java`, `WaveManager.java` |
| Arena registry/builder/policy | `ArenaTemplateRegistry.java`, `TemplateRegistryBootstrap.java`, `ArenaPolicyRegistry.java`, `ArenaBuilder.java` |
| Network handlers + validators | `NetworkHandler.java`, `EnduranceNetworkHandler.java`, `PacketValidator.java` |
| Telemetry persistence | `DuckDBBatchWriter.java`, `DuckDBTelemetryService.java`, `DuckDbDestination.java` |
| Radial action registry | `ActionRegistry.java` |
| Client/server boundary | `ClientUiBridge.java`, `ClientUiBridgeImpl.java`, `ClientVFXProxy.java` |

---

## Notes

- `DuckDBBatchWriter` now uses `AtomicInteger` for `pressureLevel`; no atomicity issues found in the current update path.
- No client-only imports observed in common/server packages for the reviewed files.
