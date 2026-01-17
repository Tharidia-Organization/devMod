# Spell Power Integration

> Last updated: 2025-12-26
> Status: CURRENT (verified against code)

## Overview

- Mod ID: `spell_power`
- Module: `com.devmod.compat.mods.spellpower.SpellPowerCompat`
- Registration: `ModIntegrationManager`
- Gating: reflection only; no hard dependency on Spell Power

## Implementation Notes

- Loads `SpellPower`, `SpellSchools`, and `SpellSchool` via reflection.
- Provides helpers for spell power values and haste per school.

## School Constants

`SCHOOL_ARCANE`, `SCHOOL_FIRE`, `SCHOOL_FROST`, `SCHOOL_HEALING`, `SCHOOL_LIGHTNING`, `SCHOOL_SOUL`.

## Exposed Helpers

- `isAvailable()` / `isApiFullyFunctional()`
- `getSpellSchool(String schoolName)`
- `getSpellPowerResult(LivingEntity, String schoolName)`
- `getSpellPower(LivingEntity, String schoolName)`
- `getCriticalSpellPower(LivingEntity, String schoolName)`
- `getHaste(LivingEntity, String schoolName)`
- `getTotalSpellPower(LivingEntity)`
- `getStrongestSchool(LivingEntity)`
- `getSpellPowerSummary(LivingEntity)`
- `hasSpellPower(LivingEntity)`

## Usage Pattern

- Call `SpellPowerCompat.isAvailable()` before using helpers.
- When unavailable, helpers return default values (-1 or empty strings).
