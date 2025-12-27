# Radial Menu / UX System

> Last updated: 2025-12-26
> Status: CURRENT (aggiornato dopo orphanage cleanup)

The radial menu is the primary in-game UI for DevMod actions and tools.

## Scope

- Macro categories (Analyze, Telemetry, Combat, Arena, Play, Tools)
- ActionRegistry-backed menu items and actions
- Search (prefix/substring/description/fuzzy scoring)
- Favorites ring (session-only)
- Input bindings and layout/animation settings

## Components

### UI Core

- `com.devmod.client.ui.radial.RadialMenuScreen`
- `com.devmod.client.ui.radial.RadialMenuRegistry`
- `com.devmod.client.ui.radial.RadialCategory`
- `com.devmod.client.ui.radial.RadialMenuItem`
- `com.devmod.client.ui.radial.RadialAction`

### Model + Input

- `com.devmod.client.ui.radial.model.MacroCategory`
- `com.devmod.client.ui.radial.input.RadialSearchHandler`
- `com.devmod.client.ui.radial.RadialMenuConfig`
- `com.devmod.client.ui.radial.config.RadialMenuConstants`

### Rendering + Animation

- `com.devmod.client.ui.radial.animation.RadialAnimator`
- `com.devmod.client.ui.radial.render.RadialTooltipRenderer`

## Input Defaults

- Open radial menu: `G` (`KeyInputHandler.OPEN_RADIAL_MENU_KEY`).
- In-menu shortcuts (from `RadialMenuConfig.InputBindings`):
  - Macro keys: `1-6`
  - Category keys: `7,8,9,0,-,=`
  - Item keys: `Q,W,E,R,Y,U,I,O,P`
  - Search toggle: `/` or `F`

## Behavioral Rules

- Menu items can be backed by the central `ActionRegistry` via `RadialMenuItem.registry(...)` and `RadialAction.registry(...)`.
- Visibility gating uses `RadialMenuItem.isVisible()` and action visibility.
- Search uses prefix/substring/description matching with a fuzzy fallback (`RadialSearchHandler`).
- Favorites are stored in-session only (no persistence in config).

## Automated Validation

- `MacroCategoryDirectTest`
- `RadialCategoryDirectTest`
- `RadialSearchHandlerDirectTest`
- `RadialMenuMacroCategoryTest`

## Cross-References

- `docs/areas/radial/RADIAL_BUTTON_CONTRACT.md`
- `docs/areas/radial/RADIAL_NAV_MAP.md`
- `docs/areas/radial/RADIAL_CENSUS.md`
