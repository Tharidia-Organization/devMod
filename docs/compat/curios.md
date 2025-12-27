# Curios API Integration

> Last updated: 2025-12-26
> Status: CURRENT (verified against code)

## Overview

- Mod ID: `curios`
- Module: `com.devmod.compat.mods.curios.CuriosCompat`
- Registration: `ModIntegrationManager`
- Gating: reflection only; no hard dependency on Curios

## Implementation Notes

- Uses reflection to access `CuriosApi` and `ICuriosHelper`.
- Provides slot constants for common Curios slots (head, necklace, ring, belt, etc.).
- All helpers are no-ops when the mod is not present.

## Slot Constants

- `SLOT_HEAD`, `SLOT_NECKLACE`, `SLOT_BACK`, `SLOT_BODY`, `SLOT_HANDS`
- `SLOT_RING`, `SLOT_BELT`, `SLOT_CHARM`, `SLOT_CURIO`

## Exposed Helpers

- `isAvailable()`
- `findCurios(LivingEntity, String slotType)`
- `findFirstCurio(LivingEntity, String slotType)`
- `getStackFromResult(Object slotResult)`
- `getAllEquippedCurios(LivingEntity)`
- `hasCurioEquipped(LivingEntity, String slotType)`
- `getCurioCount(LivingEntity, String slotType)`
- `hasRingEquipped(Player)` / `hasNecklaceEquipped(Player)`
- `getEquippedRings(Player)` / `getEquippedNecklace(Player)`
- `getTotalCurioCount(LivingEntity)`

## Usage Pattern

- Call `CuriosCompat.isAvailable()` before using slot helpers.
- Slot queries return empty results when Curios is not present.
