# Rename Map

## Overview
This document tracks all naming changes during the project standardization.

---

## Namespace Migration

| Old | New | Status | Notes |
|-----|-----|--------|-------|
| `com.frenkvs.devmod.*` | `com.devmod.*` | **DONE** | Java sources clean |
| `com.devmod.transport` | `com.devmod.network` | **DONE** | Package + classes renamed |

---

## Class Renames (Completed)

| Old Class | New Class | Location | Status |
|-----------|-----------|----------|--------|
| `TransportHandler` | `NetworkHandler` | `network/` | DONE |
| `TransportHandlerBase` | `NetworkHandlerBase` | `network/handlers/` | DONE |
| `AbilityTransportHandler` | `AbilityNetworkHandler` | `network/handlers/` | DONE |
| `ConfigTransportHandler` | `ConfigNetworkHandler` | `network/handlers/` | DONE |
| `EnduranceTransportHandler` | `EnduranceNetworkHandler` | `network/handlers/` | DONE |
| `MobItemTransportHandler` | `MobItemNetworkHandler` | `network/handlers/` | DONE |
| `PartyTransportHandler` | `PartyNetworkHandler` | `network/handlers/` | DONE |
| `ShieldTransportHandler` | `ShieldNetworkHandler` | `network/handlers/` | DONE |
| `DebugTransportHandler` | `DebugNetworkHandler` | `debug/` | DONE |
| `PacketSecurityService` | `PacketValidator` | `network/` | DONE |

---

## Pending Terminology Standardization

| Current | Target | Scope | Status | Notes |
|---------|--------|-------|--------|-------|
| `arena/hud` | `arena/overlay` | subpackage | TODO | Standard term |
| `arena/instance` | `arena/runtime` | subpackage | TODO | Standard term |

---

## Documentation Cleanup Required

The following docs still contain `com.frenkvs` references:

| File | Occurrences | Priority |
|------|-------------|----------|
| `docs/ENTRYPOINTS.md` | 1 | HIGH |
| `docs/PROJECT_TOPOLOGY.md` | 2 | HIGH |
| `docs/ARCHITECTURE.md` | 2 | HIGH |
| `docs/ARCHITECTURE.excalidraw.md` | 20+ | MEDIUM |
| `docs/areas/*` | 50+ | MEDIUM |
| `docs/cross_cutting/*` | 10+ | MEDIUM |
| `docs/telemetry/*` | 40+ | MEDIUM |
| `docs/testing/*` | 5+ | LOW |
| `docs/_deprecated/*` | 100+ | LOW (deprecated) |
| `docs/prismatic-shield-integration/*` | 20+ | LOW |
| `docs/editor-design-system/*` | 5+ | LOW |
| `docs/arena-template-rework/*` | 2 | LOW |
| `docs/reorg/*` | 10+ | LOW (meta docs) |

---

## Test File Renames

| Old | New | Status |
|-----|-----|--------|
| `PacketSecurityServiceTest.java` | `PacketValidatorTest.java` | DONE |

---

## Residual References in Tests

| File | Issue | Status |
|------|-------|--------|
| `L0SmokeBootTest.java:260` | Method named `transportFilesExist()` | TODO - cosmetic |

---

## Summary

- **Java Sources**: 100% clean (no `com.frenkvs`, no `transport`)
- **Documentation**: ~200+ references to old namespace need updating
- **Tests**: 1 cosmetic rename pending
