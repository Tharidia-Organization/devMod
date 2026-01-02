# Rename Map

## Overview
This document tracks all naming changes during the project standardization to NeoForge conventions.

**Last Updated:** 2024-12-24

---

## Namespace Migration (COMPLETE)

| Old | New | Status | Notes |
|-----|-----|--------|-------|
| `com.frenkvs.devmod.*` | `com.devmod.*` | **DONE** | All Java sources migrated |
| `mod_group_id=com.frenkvs.devmod` | `mod_group_id=com.devmod` | **DONE** | gradle.properties |

---

## Package Moves (NeoForge Standard)

### Client-Side Consolidation

| Old Location | New Location | Files | Status |
|--------------|--------------|-------|--------|
| `com.devmod.overlay` | `com.devmod.client.overlay` | 29 | **DONE** |
| `com.devmod.rendering` | `com.devmod.client.rendering` | 20 | **DONE** |
| `com.devmod.rendering.shader` | `com.devmod.client.rendering.shader` | 4 | **DONE** |
| `com.devmod.rendering.shield` | `com.devmod.client.rendering.shield` | 3 | **DONE** |
| `com.devmod.panels` | `com.devmod.client.panels` | 14 | **DONE** |

### Network Package (Corrected)

| Old | New | Status | Notes |
|-----|-----|--------|-------|
| `com.devmod.transport` | `com.devmod.network` | **DONE** | NeoForge standard is `network` |

---

## Class Renames (COMPLETE)

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

## Test File Renames

| Old | New | Status |
|-----|-----|--------|
| `PacketSecurityServiceTest.java` | `PacketValidatorTest.java` | DONE |
| `L0SmokeBootTest.transportFilesExist()` | `networkFilesExist()` | DONE |

---

## Packages NOT Renamed (Domain-Specific, Valid)

These packages follow domain-driven design and are intentionally kept:

| Package | Reason |
|---------|--------|
| `arena/hud` | Arena-specific HUD (domain term) |
| `arena/instance` | Arena instance management (domain term) |
| `arena/network` | Arena-specific networking |
| `actions/client` | Client-side actions (feature-local) |
| `debug/client` | Client-side debug rendering (feature-local) |

---

## Documentation Cleanup (COMPLETE)

All documentation has been updated to use `com.devmod`:

| Category | Status |
|----------|--------|
| README.md | DONE |
| docs/ENTRYPOINTS.md | DONE |
| docs/PROJECT_TOPOLOGY.md | DONE |
| docs/ARCHITECTURE.md | DONE |
| Excalidraw diagrams | DONE |
| docs/areas/* | DONE |
| docs/subsystems/prismatic-shield-integration/* | DONE |
| docs/subsystems/editor-design-system/* | DONE |

---

## Guardrails Active

The following automated checks prevent regression:

1. No `com.frenkvs` in Java sources
2. No legacy `transport` package
3. Root package file limit (max 5)
4. No `TransportHandler` class names
5. Correct `mod_id=devmod` in gradle.properties
6. No `DebugTransportHandler` references
7. Client packages in correct location (`client/overlay`, `client/rendering`, `client/panels`)

Run: `./tools/check-naming.sh`

---

## Summary

| Area | Status |
|------|--------|
| Java Sources | 100% clean |
| Package Structure | NeoForge compliant |
| Documentation | 100% updated |
| Tests | All passing (2783) |
| Guardrails | 7/7 active |
