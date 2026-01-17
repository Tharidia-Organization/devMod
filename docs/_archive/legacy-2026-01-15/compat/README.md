# DevMod Compatibility Layer

> Last updated: 2025-12-30
> Status: CURRENT (verified against code)

This directory documents the compatibility modules that integrate DevMod with optional third-party mods.

## Architecture

- `Compat` provides mod detection and version helpers with caching.
- `CompatModule` defines the integration interface (priority, initCommon/initClient, optional actions).
- `CompatRegistry` registers modules and initializes them in priority order only when the mod is present.
- `ModIntegrationManager` registers common modules and a few legacy integrations.
- `ClientCompatRegistrar` registers client-only modules.

## Module Locations

- Common modules: `src/main/java/com/devmod/compat/mods/**`
- Client-only modules: `src/main/java/com/devmod/client/compat/mods/**`
- Legacy integrations (non-CompatModule): `com.devmod.integration.*`

## Quick Reference

- `docs/compat/MOD_INVENTORY.md` (authoritative list of modules in code)
- `docs/compat/apothic_attributes.md`
- `docs/compat/c2me.md`
- `docs/compat/clothconfig.md`
- `docs/compat/curios.md`
- `docs/compat/geckolib.md`
- `docs/compat/epicfight.md`
- `docs/compat/irons_spellbooks.md`
- `docs/compat/ranged_weapon_api.md`
- `docs/compat/spark.md`
- `docs/compat/spell_engine.md`
- `docs/compat/spell_power.md`

## Adding a New Integration

1. Implement `CompatModule` in `com.devmod.compat.mods.<modid>`.
2. Register the module in `ModIntegrationManager.initCompatModules()` (or `ClientCompatRegistrar` for client-only).
3. Update `docs/compat/MOD_INVENTORY.md` and add a module doc in `docs/compat/` if needed.

## Status Legend

- `IMPLEMENTED`: integration exists in code (CompatModule or legacy integration).
- `PLANNING`: no integration in code yet.
