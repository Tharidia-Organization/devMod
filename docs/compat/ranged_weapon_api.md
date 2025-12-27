# Ranged Weapon API Integration

> Last updated: 2025-12-26
> Status: CURRENT (verified against code)

## Overview

- Mod ID: `ranged_weapon_api`
- Module: `com.devmod.compat.mods.rangedweaponapi.RangedWeaponApiCompat`
- Registration: `ModIntegrationManager`
- Gating: reflection only; supports multiple package layouts for the API classes

## Implementation Notes

- Attempts to load `RangedWeaponItem` and `CrossbowItem` from common API packages.
- When class lookup fails, the integration still reports availability if the mod is loaded.

## Exposed Helpers

- `isAvailable()` / `hasPropertyAccess()`
- `isRangedWeapon(ItemStack)`
- `isCustomCrossbow(ItemStack)`
- `getDamage(ItemStack)`
- `getPullTime(ItemStack)`
- `getVelocity(ItemStack)`
- `getWeaponStatsSummary(ItemStack)`

## Usage Pattern

- Call `RangedWeaponApiCompat.isAvailable()` before using helpers.
- When properties are not accessible, helpers return default values (-1 or empty strings).
