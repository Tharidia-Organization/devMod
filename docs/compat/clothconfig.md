# Cloth Config API Integration

> Last updated: 2025-12-26
> Status: CURRENT (verified against code)

## Overview

- Mod ID: `cloth_config`
- Module: `com.devmod.compat.mods.clothconfig.ClothConfigCompat`
- Registration: `ModIntegrationManager` (common; becomes available only on client)
- Gating: reflection only; no hard dependency on Cloth Config

## Implementation Notes

- `initCommon()` checks client dist and attempts to load `ConfigBuilder`, `ConfigCategory`, and `ConfigEntryBuilder` via reflection.
- If those classes are found, `ClothConfigCompat.isAvailable()` returns true and helper methods can be used.

## Exposed Helpers

- `isAvailable()` / `hasConfigBuilder()`
- `createConfigBuilder()`
- `getEntryBuilder(Object configBuilder)`
- `setTitle(Object configBuilder, String titleKey)`
- `getOrCreateCategory(Object configBuilder, String categoryKey)`
- `addBooleanToggle(...)`
- `addIntSlider(...)`
- `buildScreen(Object configBuilder)`
- `setParentScreen(Object configBuilder, Object parentScreen)`
- `setSavingRunnable(Object configBuilder, Runnable saveRunnable)`

## Usage Pattern

- Use `ClothConfigCompat.isAvailable()` before calling any helper.
- When unavailable, fall back to NeoForge's default config screen.
