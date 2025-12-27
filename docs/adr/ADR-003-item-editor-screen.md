# ADR-003: ItemEditorScreen Architecture

> Last updated: 2025-12-26
> Status: CURRENT (verified against code)

Decision status: Accepted  
Decision date: 2025-12-26  
Context: Quality pass for large classes

---

## Summary

`ItemEditorScreen` is the unified editor for weapons, armor, ranged items, food, fuel, and usable items. The design favors a single screen with modular sections and overlay systems.

## Context

A unified editor avoids duplicating layout, input, and preset handling across multiple screens while enabling shared workflows (multi-edit, presets, templates).

## Decision

Keep one screen for item editing and delegate item-specific behavior to modules and editor subsystems.

## Implementation Notes (Verified)

### Modules

- Item type modules live in `com.devmod.client.ui.editor.modules`:
  - `WeaponModule`, `ArmorModule`, `RangedModule`, `FoodModule`, `FuelModule`, `UsableModule`
  - Shared helpers such as `GeneralModule` and `RecipeModule`
  - Core/UI splits for several modules (e.g., `WeaponModuleCore`, `WeaponModuleUI`)

### Controllers

- Input and mode handling are centralized in:
  - `InputRouter`
  - `ModeController`
  - `OverlayController`

### Systems

- Presets, templates, and multi-edit support are implemented under `com.devmod.client.ui.editor.systems` (e.g., `PresetRegistry`, `PresetSelectorOverlay`, `TemplateOverlay`, `MultiEditManager`).

## Consequences

- Pros: shared layout/UX, consistent workflows, reusable presets.
- Cons: large screen class that requires careful organization.

## Notes

Modularization is preserved through modules and systems even while the primary screen remains centralized.
