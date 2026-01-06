# Radial Menu / UX System

> Last updated: 2025-12-26
> Status: CURRENT (aggiornato dopo orphanage cleanup)

The radial menu is the primary in-game UI for DevMod actions and tools.

## Scope

- Macro categories (Analyze, Telemetry, Combat, Arena, Play, Tools)
- ActionRegistry-backed menu items and actions
- Search (prefix/substring/description/fuzzy scoring)
- Favorites ring (persisted in radial config)
- Quick actions (pinned items via Ctrl+Click)
- Profiles/safe mode filters + usage-based ordering
- Action details + risk badges
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
  - Profile cycle: `M`
  - Safe mode toggle: `N`
  - Theme cycle: `T`
  - Edit mode toggle: `Shift`
  - Quick actions: `Alt+1-6` (pinned)
  - Details: right-click (if enabled) or long-press

## Behavioral Rules

- Menu items can be backed by the central `ActionRegistry` via `RadialMenuItem.registry(...)` and `RadialAction.registry(...)`.
- Visibility gating uses `RadialMenuItem.isVisible()` and action visibility.
- Search uses prefix/substring/description matching with a fuzzy fallback (`RadialSearchHandler`).
- Favorites are persisted in radial config (`favoriteActionIds`).
- Safe mode hides risky actions (based on ActionType, permissions, confirmation flags).
- Usage ordering sorts items by usage count when enabled.
- Quick actions are pinned with Ctrl+Click and executed with Alt+macro keys.
- Action details open on long-press/right-click; dangerous actions open details for confirmation.

## Automated Validation

- `MacroCategoryDirectTest`
- `RadialCategoryDirectTest`
- `RadialSearchHandlerDirectTest`
- `RadialMenuMacroCategoryTest`

## Cross-References

- `docs/areas/radial/RADIAL_BUTTON_CONTRACT.md`
- `docs/areas/radial/RADIAL_NAV_MAP.md`
- `docs/areas/radial/RADIAL_CENSUS.md`
