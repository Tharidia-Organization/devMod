# ADR-003: ItemEditorScreen Architecture

**Status**: Accepted
**Date**: 2025-12-26
**Context**: Quality pass documentation for large classes

---

## Summary

`ItemEditorScreen` (2462 LOC) is the unified item editor for weapons, armor, food, and usables. This ADR documents its component-based architecture and rendering pipeline.

---

## Context

The Item Editor provides:
- Real-time item stat editing
- Multi-item batch editing
- Preset management (save/load configurations)
- Template system for quick setup
- Undo/redo with history
- Export to datapacks

A unified screen approach was chosen over separate editors per item type to:
- Share UI infrastructure (layout, theming, input handling)
- Enable cross-item-type presets
- Reduce code duplication

---

## Architecture

### Section Organization

The class is organized into logical sections:

| Section | Lines | Description |
|---------|-------|-------------|
| **LAYOUT** | ~100 | Dimensions, responsive calculations |
| **STATE** | ~70 | Current item, mode, module selection |
| **CONSTRUCTOR** | ~20 | Initialization entry point |
| **INITIALIZATION** | ~450 | Component setup, module loading |
| **RENDERING** | ~430 | Draw loop, components, overlays |
| **INPUT HANDLING** | ~380 | Mouse, keyboard, scrolling |
| **ACTIONS** | ~110 | Apply, reset, close handlers |
| **DATA OPS** | ~550 | Export, import, presets |
| **UTILITY** | ~350 | Helpers, accessors |

### Component Delegation

```
ItemEditorScreen
├── HeaderComponent          - Title, mode badge, tabs
├── LeftColumnComponent      - Item picker, slot selector
├── ScrollableContentArea    - Main edit area with modules
├── FooterComponent          - Apply/Reset/Close buttons
└── Overlays
    ├── HelpOverlay          - Keyboard shortcuts
    ├── PresetSelectorOverlay - Load/save presets
    ├── TemplateOverlay      - Quick templates
    ├── MultiEditPanel       - Batch editing
    └── DebugOverlay         - Development info
```

### Module System

Item-type-specific editing is delegated to modules:

| Module | Item Types | Stats |
|--------|------------|-------|
| `WeaponModule` | Swords, axes, tools | Damage, speed, crit |
| `ArmorModule` | Helmets, chestplates, etc. | Armor, toughness |
| `RangedModule` | Bows, crossbows | Velocity, accuracy |
| `FoodModule` | Food items | Nutrition, saturation |
| `UsableModule` | Potions, tools | Duration, cooldown |
| `FuelModule` | Fuel items | Burn time |

### Controller Pattern

Input and state management use controllers:

```java
InputRouter     - Routes keyboard/mouse to appropriate handler
ModeController  - Manages edit/preview/compare modes
OverlayController - Manages overlay visibility and stacking
```

---

## Decision

### Why a Single Screen?

1. **Shared Infrastructure**: Layout, theming, keybinds, input handling
2. **Cross-Type Operations**: Compare weapon vs armor stats
3. **Unified Presets**: One preset can contain multiple item types
4. **Consistent UX**: Same workflow regardless of item type

### Why 2400+ LOC?

The size reflects:
1. **Complex UI layout** with responsive design
2. **Multiple overlays** (help, presets, templates, debug)
3. **Comprehensive input handling** (mouse, keyboard, scroll, drag)
4. **Data operations** (export, import, preset management)

---

## Alternatives Considered

### Option A: Separate Screens Per Item Type
- `WeaponEditorScreen`, `ArmorEditorScreen`, etc.

**Rejected**: Would duplicate 60%+ of UI code; harder to maintain consistency.

### Option B: Entity-Component-System
- Pure ECS with no inheritance

**Rejected**: Over-engineering for a UI; harder to reason about.

### Option C: ImGui-Style Immediate Mode
- No retained state, rebuild UI each frame

**Partially Adopted**: Some overlays use immediate-mode patterns.

---

## Consequences

### Positive
- Single source for all item editing
- Consistent UX across item types
- Easy to add new modules
- Shared undo/redo infrastructure

### Negative
- Large class file (2462 LOC)
- Complex initialization
- Module coordination requires careful state management

### Mitigations
- Clear section markers with visual separators
- Heavy delegation to components and modules
- Controllers for cross-cutting concerns
- Comprehensive Javadoc linking to design system docs

---

## Related Documents

- [Editor design system](../subsystems/editor-design-system/README.md)
- [Module interfaces](../../src/main/java/com/devmod/client/ui/editor/EditorModule.java)

---

## References

- [ItemEditorScreen.java](../../src/main/java/com/devmod/client/ui/editor/ItemEditorScreen.java)
- [InputRouter.java](../../src/main/java/com/devmod/client/ui/editor/controller/InputRouter.java)
- [WeaponModule.java](../../src/main/java/com/devmod/client/ui/editor/modules/WeaponModule.java)
