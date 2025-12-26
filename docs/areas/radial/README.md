# Radial Menu / UX System

> **Audit Date**: 2025-12-26
> **Status**: CURRENT (code-aligned)
> **Risk Level**: MEDIUM (client-only UI + input routing)

---

## 1. Purpose

The radial menu is the primary in-game UI for DevMod actions:

- **Macro categories**: 6 top-level buckets (Analyze, Telemetry, Combat, Arena, Play, Tools)
- **Action registry**: menu items are backed by `ActionRegistry`
- **Search**: prefix/substring/fuzzy matching
- **Favorites ring**: session-only favorites for quick access

---

## 2. Components

### UI Core
- `com.devmod.client.ui.radial.RadialMenuScreen`
- `com.devmod.client.ui.radial.RadialMenuRegistry`
- `com.devmod.client.ui.radial.RadialCategory`
- `com.devmod.client.ui.radial.RadialMenuItem`
- `com.devmod.client.ui.radial.RadialAction`

### Model + Input
- `com.devmod.client.ui.radial.model.MacroCategory`
- `com.devmod.client.ui.radial.model.RadialMenuState`
- `com.devmod.client.ui.radial.input.RadialSearchHandler`
- `com.devmod.client.ui.radial.RadialMenuConfig`

---

## 3. Input Defaults

### Open Radial Menu
- Default key: `G` (`KeyInputHandler.OPEN_RADIAL_MENU_KEY`)

### In-Menu Shortcuts (RadialMenuConfig)
- Macro keys: `1-6`
- Category keys: `7,8,9,0,-,=`
- Item keys: `Q,W,E,R,Y,U,I,O,P`
- Search toggle: `/` or `F`

---

## 4. Behavioral Rules (Implemented)

- **Subcategories** are created via `RadialCategory.addSubcategory()` and injected as navigation items.
- **Visibility gating** uses `RadialMenuItem.isVisible()` which combines item visibility and action visibility.
- **Search** uses `RadialSearchHandler` with prefix/substring/fuzzy scoring.
- **Favorites** exist in-session; persistence is not implemented.

---

## 5. Automated Validation

| Behavior | Test |
|----------|------|
| Macro category indexing + adjacency | `MacroCategoryDirectTest` |
| Subcategory links + visibility gating | `RadialCategoryDirectTest` |
| Toggle item execution | `RadialCategoryDirectTest` |
| Search scoring + best match | `RadialSearchHandlerDirectTest` |

---

## Cross-References

- `docs/areas/radial/RADIAL_BUTTON_CONTRACT.md`
- `docs/areas/radial/RADIAL_NAV_MAP.md`
- `docs/areas/radial/RADIAL_CENSUS.md`

