# Iron's Spells 'n Spellbooks Integration

> Last updated: 2025-12-26
> Status: CURRENT (verified against code)

## Overview

- Mod ID: `irons_spellbooks`
- Module: `com.devmod.compat.mods.ironsspellbooks.IronsSpellbooksCompat`
- Registration: `ModIntegrationManager`
- Gating: reflection only; no hard dependency on Iron's Spellbooks

## Implementation Notes

- Uses reflection to access `MagicHelper` and `MagicData` APIs.
- Provides read-only helpers for mana and casting state.
- All helpers return safe defaults when the API is unavailable.

## School Constants

`SCHOOL_FIRE`, `SCHOOL_ICE`, `SCHOOL_LIGHTNING`, `SCHOOL_HOLY`, `SCHOOL_ENDER`,
`SCHOOL_BLOOD`, `SCHOOL_EVOCATION`, `SCHOOL_NATURE`, `SCHOOL_ELDRITCH`.

## Exposed Helpers

- `isAvailable()` / `isApiFullyFunctional()`
- `getMagicData(Player)`
- `getMana(Player)` / `getMaxMana(Player)` / `getManaPercentage(Player)`
- `isCasting(Player)`
- `getCastingSpell(Player)` / `getCastingSpellName(Player)`
- `hasMagicData(LivingEntity)`
- `getMagicStatusString(Player)`
- `getManaRegenRate(Player)`

## Usage Pattern

- Call `IronsSpellbooksCompat.isAvailable()` before using helpers.
- When unavailable, helpers return default values (-1 or empty strings).
