# DevMod Migration Guide

> Last updated: 2025-12-26
> Status: HISTORICAL

This document tracks breaking changes and migration paths for the DevMod codebase.

---

## December 2024 - Package Reorganization

### Network Package Rename

The `transport` package was renamed to `network` to align with NeoForge conventions.

| Old | New |
|-----|-----|
| `com.devmod.transport.*` | `com.devmod.network.*` |
| `TransportHandler` | `NetworkHandler` |
| `TransportHandlerBase` | `NetworkHandlerBase` |
| `AbilityTransportHandler` | `AbilityNetworkHandler` |
| `ConfigTransportHandler` | `ConfigNetworkHandler` |
| `EnduranceTransportHandler` | `EnduranceNetworkHandler` |
| `MobItemTransportHandler` | `MobItemNetworkHandler` |
| `PartyTransportHandler` | `PartyNetworkHandler` |
| `ShieldTransportHandler` | `ShieldNetworkHandler` |
| `DebugTransportHandler` | `DebugNetworkHandler` |

**Impact:** All imports referencing `com.devmod.transport` need updating.

**Migration:**
```java
// Before
import com.devmod.transport.NetworkHandler;
import com.devmod.transport.handlers.*;

// After
import com.devmod.network.NetworkHandler;
import com.devmod.network.handlers.*;
```

### Test File Rename

| Old | New |
|-----|-----|
| `PacketSecurityServiceTest.java` | `PacketValidatorTest.java` |

---

## Historical Changes

### Namespace Consolidation (Pre-December 2024)

The original namespace `com.devmod` was migrated to `com.devmod`.

| Old | New |
|-----|-----|
| `com.devmod.*` | `com.devmod.*` |

**Note:** This migration is complete. No `com.frenkvs` references should exist in Java sources.

---

## Verification

Run the naming guardrails to verify compliance:

```bash
./tools/check-naming.sh
```

All checks should pass:
- No `com.frenkvs` in Java sources
- No `transport` package
- No `TransportHandler` class names
- Correct `mod_id` in gradle.properties

---

## Future Migrations

(This section will be updated as new migrations are planned)

---

## Support

For migration issues:
1. Check this document for the specific change
2. Run `./tools/check-naming.sh` to identify problems
3. Review `docs/reorg/RENAME_MAP.md` for complete rename history
