# ADR-002: DevModClientActions Architecture

**Status**: Accepted
**Date**: 2025-12-26
**Context**: Quality pass documentation for large classes

---

## Summary

`DevModClientActions` (2725 LOC) is the central registry for all client-side actions. This ADR documents its architecture and the rationale for centralizing action registration.

---

## Context

DevMod provides 100+ client-side actions accessible via:
- Radial menu (mouse-driven)
- Keyboard shortcuts
- Console commands
- UI buttons

A centralized registration pattern ensures:
- All actions are discoverable
- Consistent precondition handling
- Unified keybind conflict detection
- Single-source action documentation

---

## Architecture

### Registration Pattern

```java
public static void register() {
    registerUiActions();
    registerDebugActions();
    registerConfigActions();
    registerTelemetryActions();
    registerQuestActions();
    registerEnduranceActions();
    registerAbilityActions();
    registerKeybindHints();
}
```

### Action Categories

| Category | Description | Example Actions |
|----------|-------------|-----------------|
| **UI** | Screen navigation | Open radial menu, settings, editor |
| **Debug** | Debug visualizers | Hitbox rendering, pathfinding debug |
| **Config** | Configuration | Mob config, weapon stats |
| **Telemetry** | Analytics | FPS tracker, heatmaps |
| **Quest** | Quest system | Open quest screen, abandon quest |
| **Endurance** | Endurance mode | Start quest, view shop, select perks |
| **Ability** | Player abilities | Dodge, dash, block |

### Action Structure

Each action is built using the fluent builder:

```java
ActionRegistry.register(RadialAction.builder(ActionIds.UI_RADIAL_OPEN)
    .labelKey("devmod.action.radial_open")           // I18n key
    .descriptionKey("devmod.action.radial_open.desc") // I18n desc
    .category(ActionCategory.UI)                      // Categorization
    .menuPath("Root/Home")                            // Radial menu path
    .icon(Items.COMPASS)                              // Visual icon
    .precondition(screenPrecondition())               // When available
    .handler(context -> openScreen())                 // Action logic
    .build());
```

### Preconditions

Reusable precondition factories:

| Factory | Description |
|---------|-------------|
| `screenPrecondition()` | No screen open, not in combat |
| `developerModePrecondition()` | Developer mode enabled |
| `qaSessionActivePrecondition()` | QA testing session active |
| `qaActiveTestPrecondition()` | Active test running |

---

## Decision

### Why Centralized Registration?

1. **Discoverability**: All actions in one file, searchable
2. **Consistency**: Same precondition patterns across actions
3. **Keybind Management**: Single source for conflict detection
4. **Documentation**: Easy to generate action catalog

### Why 2700+ LOC?

The size is proportional to:
1. **100+ registered actions** with full metadata
2. **Category-specific preconditions** with detailed error messages
3. **Complex action handlers** for screens with state setup
4. **Debug visualizer toggles** with cycling logic

---

## Alternatives Considered

### Option A: Annotation-Based Registration
```java
@ClientAction(id = "ui.radial.open", category = UI)
public void openRadialMenu(ActionContext ctx) { ... }
```

**Rejected**: Harder to track all actions; runtime reflection overhead.

### Option B: Per-Feature Registration
- Each feature registers its own actions
- DevModClientActions just calls feature.registerActions()

**Partially Adopted**: `ArenaActionRegistry.registerClientActions()` does this.

### Option C: Data-Driven Actions
- Actions defined in JSON
- Loaded at runtime

**Rejected**: Handlers require code; would split definition from implementation.

---

## Consequences

### Positive
- Single source of truth for client actions
- Easy to add new actions following existing patterns
- Keybind conflicts detected at registration
- I18n keys consistently structured

### Negative
- Large file (2725 LOC)
- Many imports (100+)
- Changes require recompilation

### Mitigations
- Actions grouped by `registerXxxActions()` methods
- Each action is self-contained (no cross-references)
- IDE navigation works well with ActionIds constants

---

## Future Considerations

1. **Split by Category**: Move each category to its own class (e.g., `DebugClientActions`)
2. **Action Documentation Generator**: Auto-generate action catalog from registrations
3. **Runtime Action Discovery**: Allow mods to register additional actions

---

## References

- [DevModClientActions.java](../../src/main/java/com/devmod/actions/client/DevModClientActions.java)
- [ActionRegistry.java](../../src/main/java/com/devmod/actions/ActionRegistry.java)
- [RadialAction.java](../../src/main/java/com/devmod/actions/RadialAction.java)
