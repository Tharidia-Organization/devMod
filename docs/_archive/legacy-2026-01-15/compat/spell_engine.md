# Spell Engine Integration

> Last updated: 2025-12-26
> Status: CURRENT (verified against code)

## Overview

- Mod ID: `spell_engine`
- Module: `com.devmod.compat.mods.spellengine.SpellEngineCompat`
- Registration: `ModIntegrationManager`
- Gating: reflection only; no hard dependency on Spell Engine

## Implementation Notes

- Uses reflection to access `SpellContainer` and optional `SpellHelper` APIs.
- Reads spell container data via item data components when available.

## Targeting Mode Constants

`TARGET_AIM`, `TARGET_BEAM`, `TARGET_AREA`, `TARGET_CASTER`, `TARGET_NONE`.

## Exposed Helpers

- `isAvailable()` / `isApiFullyFunctional()`
- `getSpellContainer(ItemStack)`
- `getSpellIds(Object container)`
- `getMaxSpells(Object container)`
- `isCastingSpell(LivingEntity)`
- `getCurrentSpell(LivingEntity)`
- `getSpellId(Object spell)`
- `hasSpells(ItemStack)`
- `getCastingStatusString(LivingEntity)`

## Usage Pattern

- Call `SpellEngineCompat.isAvailable()` before using helpers.
- When unavailable, helpers return default values (0, false, empty list/string).
