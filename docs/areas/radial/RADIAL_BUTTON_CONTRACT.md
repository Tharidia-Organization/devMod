# Radial Button Contract

> **Status**: HISTORICAL (spec snapshot; not enforced by current implementation)

This document defines the behavioral contract for each action exposed via the Radial Menu.

## Contract Schema

Each action must define:

| Field | Description |
|-------|-------------|
| **id** | Unique action identifier from `ActionIds.java` |
| **labelKey** | i18n translation key |
| **category** | `ActionCategory` enum value |
| **actionType** | `ActionType` enum (may be null for legacy) |
| **visibilityPredicate** | Controls if action is shown in menu (null = always visible) |
| **precondition** | Execution gate - blocks with error if fails |
| **permissionLevel** | Explicit op level required (-1 = none) |
| **effects** | What happens on execution |
| **fallback** | Behavior when precondition fails |
| **uiFeedback** | `NONE` / `TOAST` / `DIALOG` / `CHAT` |
| **telemetry** | Events emitted |
| **errorHandling** | How errors are reported to user |

### Two-Level Gating Model

```
┌─────────────────────────────────────────────────────────┐
│  VISIBILITY GATE (visibilityPredicate)                  │
│  - Checked before rendering                              │
│  - If false → item is HIDDEN (not shown in menu)        │
│  - Use for: permission-based hiding (Scenario 9)        │
└─────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────┐
│  EXECUTION GATE (precondition)                          │
│  - Checked when action is invoked                       │
│  - If false → BLOCKED with error feedback               │
│  - Telemetry: radial_action_blocked                     │
│  - Use for: runtime state checks                        │
└─────────────────────────────────────────────────────────┘
```

### Implementation Reference

```java
// In RadialAction (actions/)
@Nullable Predicate<ActionContext> visibilityPredicate;  // Visibility gate
ActionPrecondition precondition;                         // Execution gate
int permissionLevel;                                     // Explicit permission
UIFeedback uiFeedback;                                   // Feedback type

// Usage
action.isVisible(context)  // Check visibility gate
action.getPrecondition().test(context)  // Check execution gate
```

---

## UI / Screen Actions

### UI_RADIAL_OPEN
| Field | Value |
|-------|-------|
| id | `devmod.ui.radial.open` |
| labelKey | `devmod.action.ui.radial.open` |
| category | `UI` |
| actionType | `NAVIGATE_SCREEN` |
| preconditions | Always available (keybind) |
| effects | Opens RadialMenuScreenV3 |
| fallback | N/A |
| telemetry | `radial_menu_opened` |
| errorHandling | Silent (keybind handler) |

### UI_SETTINGS_OPEN
| Field | Value |
|-------|-------|
| id | `devmod.ui.settings.open` |
| labelKey | `devmod.action.ui.settings.open` |
| category | `UI` |
| actionType | `NAVIGATE_SCREEN` |
| preconditions | `always()` |
| effects | Opens DevMod settings screen |
| fallback | N/A |
| telemetry | `radial_action_invoked` |
| errorHandling | Exception logged, screen closed |

### UI_ITEM_EDITOR_OPEN_AUTO
| Field | Value |
|-------|-------|
| id | `devmod.ui.item_editor.open_auto` |
| labelKey | `devmod.action.ui.item_editor.open_auto` |
| category | `UI` |
| actionType | `NAVIGATE_SCREEN` |
| preconditions | `requiresHeldItem()` |
| effects | Opens Item Editor for held item type |
| fallback | `radial_action_blocked` with PRECONDITION_FAILED |
| telemetry | `radial_action_invoked` |
| errorHandling | Toast message "No item in hand" |

### UI_TELEMETRY_DASHBOARD_OPEN
| Field | Value |
|-------|-------|
| id | `devmod.ui.telemetry_dashboard.open` |
| labelKey | `devmod.action.ui.telemetry_dashboard.open` |
| category | `TELEMETRY` |
| actionType | `OPEN_EXTERNAL` |
| preconditions | `requiresPermissionOrClient(2)` |
| effects | Opens dashboard URL in browser |
| fallback | Confirmation dialog with copy fallback |
| telemetry | `radial_action_invoked`, `external_url_opened`, `external_url_copied` |
| errorHandling | `OpenExternalConfirmScreen` with error display |

### UI_QUEST_EDITOR_OPEN
| Field | Value |
|-------|-------|
| id | `devmod.ui.quest_editor.open` |
| labelKey | `devmod.action.ui.quest_editor.open` |
| category | `UI` |
| actionType | `NAVIGATE_SCREEN` |
| preconditions | `requiresPermissionOrClient(2)` |
| effects | Opens Quest Editor screen |
| fallback | Action blocked |
| telemetry | `radial_action_invoked` |
| errorHandling | Silent block |

### UI_ENDURANCE_SCREEN_OPEN
| Field | Value |
|-------|-------|
| id | `devmod.ui.endurance_screen.open` |
| labelKey | `devmod.action.ui.endurance_screen.open` |
| category | `UI` |
| actionType | `NAVIGATE_SCREEN` |
| preconditions | `always()` |
| effects | Opens Endurance mode screen |
| fallback | N/A |
| telemetry | `radial_action_invoked` |
| errorHandling | Exception logged |

---

## Debug Toggle Actions

### DEBUG_OVERLAY_TOGGLE
| Field | Value |
|-------|-------|
| id | `devmod.debug.overlay.toggle` |
| labelKey | `devmod.action.debug.overlay.toggle` |
| category | `DEBUG` |
| actionType | `TOGGLE_SETTING` |
| preconditions | `always()` |
| effects | Toggles debug overlay HUD visibility |
| fallback | N/A |
| telemetry | `radial_action_invoked` |
| errorHandling | Silent |
| isToggle | `true` |
| activePredicate | `DevModClientConfig.showDebugOverlay::get` |

### DEBUG_BODY_PARTS_TOGGLE
| Field | Value |
|-------|-------|
| id | `devmod.debug.body_parts.toggle` |
| labelKey | `devmod.action.debug.body_parts.toggle` |
| category | `DEBUG` |
| actionType | `TOGGLE_SETTING` |
| preconditions | `always()` |
| effects | Toggles body part hitbox visualization |
| fallback | N/A |
| telemetry | `radial_action_invoked` |
| errorHandling | Silent |
| isToggle | `true` |

### DEBUG_OVERLAYS_ENABLE_ALL
| Field | Value |
|-------|-------|
| id | `devmod.debug.overlays.enable_all` |
| labelKey | `devmod.action.debug.overlays.enable_all` |
| category | `DEBUG` |
| actionType | `TRIGGER_EVENT` |
| preconditions | `always()` |
| effects | Enables all debug overlays |
| fallback | N/A |
| telemetry | `radial_action_invoked` |
| errorHandling | Silent |

### DEBUG_HEATMAP_TOGGLE
| Field | Value |
|-------|-------|
| id | `devmod.debug.heatmap.toggle` |
| labelKey | `devmod.action.debug.heatmap.toggle` |
| category | `DEBUG` |
| actionType | `TOGGLE_SETTING` |
| preconditions | `always()` |
| effects | Toggles heatmap overlay |
| fallback | N/A |
| telemetry | `radial_action_invoked` |
| errorHandling | Silent |
| isToggle | `true` |

### DEBUG_ROOM_BOUNDS_TOGGLE
| Field | Value |
|-------|-------|
| id | `devmod.debug.room_bounds.toggle` |
| labelKey | `devmod.action.debug.room_bounds.toggle` |
| category | `DEBUG` |
| actionType | `TOGGLE_SETTING` |
| preconditions | `always()` |
| effects | Toggles room bounds visualization |
| fallback | N/A |
| telemetry | `radial_action_invoked` |
| errorHandling | Silent |
| isToggle | `true` |

---

## HUD Toggle Actions

### HUD_IMPACT_TOGGLE
| Field | Value |
|-------|-------|
| id | `devmod.hud.impact.toggle` |
| labelKey | `devmod.action.hud.impact.toggle` |
| category | `HUD` |
| actionType | `TOGGLE_SETTING` |
| preconditions | `always()` |
| effects | Toggles impact HUD visibility |
| fallback | N/A |
| telemetry | `radial_action_invoked` |
| errorHandling | Silent |
| isToggle | `true` |

### HUD_QUEST_TOGGLE
| Field | Value |
|-------|-------|
| id | `devmod.hud.quest.toggle` |
| labelKey | `devmod.action.hud.quest.toggle` |
| category | `HUD` |
| actionType | `TOGGLE_SETTING` |
| preconditions | `always()` |
| effects | Toggles quest tracker HUD |
| fallback | N/A |
| telemetry | `radial_action_invoked` |
| errorHandling | Silent |
| isToggle | `true` |

### HUD_ENDURANCE_TOGGLE
| Field | Value |
|-------|-------|
| id | `devmod.endurance.hud.toggle` |
| labelKey | `devmod.action.endurance.hud.toggle` |
| category | `HUD` |
| actionType | `TOGGLE_SETTING` |
| preconditions | `always()` |
| effects | Toggles endurance mode HUD |
| fallback | N/A |
| telemetry | `radial_action_invoked` |
| errorHandling | Silent |
| isToggle | `true` |

---

## Command Shortcut Actions

### COMMAND_GAMEMODE_CREATIVE
| Field | Value |
|-------|-------|
| id | `devmod.command.gamemode.creative` |
| labelKey | `devmod.action.command.gamemode.creative` |
| category | `COMMAND` |
| actionType | `RUN_SERVER_COMMAND` |
| preconditions | `requiresPermissionOrClient(2)` |
| effects | Executes `/gamemode creative` |
| fallback | Action blocked |
| telemetry | `radial_action_invoked` |
| errorHandling | Server feedback via chat |

### COMMAND_GAMEMODE_SURVIVAL
| Field | Value |
|-------|-------|
| id | `devmod.command.gamemode.survival` |
| labelKey | `devmod.action.command.gamemode.survival` |
| category | `COMMAND` |
| actionType | `RUN_SERVER_COMMAND` |
| preconditions | `requiresPermissionOrClient(2)` |
| effects | Executes `/gamemode survival` |
| fallback | Action blocked |
| telemetry | `radial_action_invoked` |
| errorHandling | Server feedback via chat |

### COMMAND_HEAL
| Field | Value |
|-------|-------|
| id | `devmod.command.heal` |
| labelKey | `devmod.action.command.heal` |
| category | `COMMAND` |
| actionType | `RUN_SERVER_COMMAND` |
| preconditions | `requiresPermissionOrClient(2)` |
| effects | Heals player to full health |
| fallback | Action blocked |
| telemetry | `radial_action_invoked` |
| errorHandling | Server feedback via chat |

### COMMAND_TIME_DAY
| Field | Value |
|-------|-------|
| id | `devmod.command.time.day` |
| labelKey | `devmod.action.command.time.day` |
| category | `COMMAND` |
| actionType | `RUN_SERVER_COMMAND` |
| preconditions | `requiresPermissionOrClient(2)` |
| effects | Executes `/time set day` |
| fallback | Action blocked |
| telemetry | `radial_action_invoked` |
| errorHandling | Server feedback via chat |

### COMMAND_WEATHER_CLEAR
| Field | Value |
|-------|-------|
| id | `devmod.command.weather.clear` |
| labelKey | `devmod.action.command.weather.clear` |
| category | `COMMAND` |
| actionType | `RUN_SERVER_COMMAND` |
| preconditions | `requiresPermissionOrClient(2)` |
| effects | Executes `/weather clear` |
| fallback | Action blocked |
| telemetry | `radial_action_invoked` |
| errorHandling | Server feedback via chat |

---

## Arena Actions

### ARENA_CREATE
| Field | Value |
|-------|-------|
| id | `devmod.arena.create` |
| labelKey | `devmod.action.arena.create` |
| category | `ARENA` |
| actionType | `RUN_SERVER_COMMAND` |
| preconditions | `requiresPermissionOrClient(2)` |
| effects | Creates arena at current location |
| fallback | Action blocked |
| telemetry | `radial_action_invoked` |
| errorHandling | Server feedback via chat |

### ARENA_STATUS
| Field | Value |
|-------|-------|
| id | `devmod.arena.status` |
| labelKey | `devmod.action.arena.status` |
| category | `ARENA` |
| actionType | `RUN_SERVER_COMMAND` |
| preconditions | `requiresPermissionOrClient(2)` |
| effects | Shows arena status |
| fallback | Action blocked |
| telemetry | `radial_action_invoked` |
| errorHandling | Server feedback via chat |

### ARENA_AUTOSMOKE_RUN
| Field | Value |
|-------|-------|
| id | `devmod.arena.autosmoke.run` |
| labelKey | `devmod.action.arena.autosmoke.run` |
| category | `ARENA` |
| actionType | `RUN_SERVER_COMMAND` |
| preconditions | `requiresPermissionOrClient(2)` |
| effects | Runs automated smoke test |
| fallback | Action blocked |
| telemetry | `radial_action_invoked` |
| errorHandling | Server feedback via chat |

---

## Telemetry Actions

### TELEMETRY_DASHBOARD_SERVER_OPEN
| Field | Value |
|-------|-------|
| id | `devmod.telemetry.dashboard.open` |
| labelKey | `devmod.action.telemetry.dashboard.open` |
| category | `TELEMETRY` |
| actionType | `OPEN_EXTERNAL` |
| preconditions | `requiresPermissionOrClient(2)` |
| effects | Starts dashboard server, opens in browser |
| fallback | `OpenExternalConfirmScreen` with copy |
| telemetry | `radial_action_invoked`, `external_url_opened` |
| errorHandling | Confirmation dialog, copy fallback |

### TELEMETRY_DASHBOARD_SERVER_START
| Field | Value |
|-------|-------|
| id | `devmod.telemetry.dashboard.start` |
| labelKey | `devmod.action.telemetry.dashboard.start` |
| category | `TELEMETRY` |
| actionType | `RUN_SERVER_COMMAND` |
| preconditions | `requiresPermissionOrClient(2)` |
| effects | Starts telemetry dashboard server |
| fallback | Action blocked |
| telemetry | `radial_action_invoked` |
| errorHandling | Server feedback via chat |

### TELEMETRY_EXPORT_ALL
| Field | Value |
|-------|-------|
| id | `devmod.telemetry.export.all` |
| labelKey | `devmod.action.telemetry.export.all` |
| category | `TELEMETRY` |
| actionType | `RUN_SERVER_COMMAND` |
| preconditions | `requiresPermissionOrClient(2)` |
| effects | Exports all telemetry data |
| fallback | Action blocked |
| telemetry | `radial_action_invoked` |
| errorHandling | Server feedback via chat |

---

## Endurance Actions

### ENDURANCE_QUEST_START
| Field | Value |
|-------|-------|
| id | `devmod.endurance.quest.start` |
| labelKey | `devmod.action.endurance.quest.start` |
| category | `ENDURANCE` |
| actionType | `SEND_SERVER_RPC` |
| preconditions | `requiresPlayer()` |
| effects | Starts new endurance quest |
| fallback | Action blocked |
| telemetry | `radial_action_invoked` |
| errorHandling | Server response via packet |

### ENDURANCE_QUEST_CONTINUE
| Field | Value |
|-------|-------|
| id | `devmod.endurance.quest.continue` |
| labelKey | `devmod.action.endurance.quest.continue` |
| category | `ENDURANCE` |
| actionType | `SEND_SERVER_RPC` |
| preconditions | `requiresPlayer()`, `hasActiveQuest()` |
| effects | Continues existing endurance quest |
| fallback | Action blocked |
| telemetry | `radial_action_invoked` |
| errorHandling | Server response via packet |

### ENDURANCE_QUEST_EXIT
| Field | Value |
|-------|-------|
| id | `devmod.endurance.quest.exit` |
| labelKey | `devmod.action.endurance.quest.exit` |
| category | `ENDURANCE` |
| actionType | `SEND_SERVER_RPC` |
| preconditions | `requiresPlayer()`, `hasActiveQuest()` |
| effects | Exits current endurance quest |
| fallback | Action blocked |
| telemetry | `radial_action_invoked` |
| errorHandling | Server response via packet |
| requiresConfirm | `true` |

---

## Config Toggle Actions

### CONFIG_TELEMETRY_TOGGLE
| Field | Value |
|-------|-------|
| id | `devmod.config.telemetry.toggle` |
| labelKey | `devmod.action.config.telemetry.toggle` |
| category | `CONFIG` |
| actionType | `TOGGLE_SETTING` |
| preconditions | `always()` |
| effects | Toggles telemetry collection |
| fallback | N/A |
| telemetry | `radial_action_invoked` |
| errorHandling | Silent |
| isToggle | `true` |

### CONFIG_IMPACT_VFX_TOGGLE
| Field | Value |
|-------|-------|
| id | `devmod.config.impact_vfx.toggle` |
| labelKey | `devmod.action.config.impact_vfx.toggle` |
| category | `CONFIG` |
| actionType | `TOGGLE_SETTING` |
| preconditions | `always()` |
| effects | Toggles impact visual effects |
| fallback | N/A |
| telemetry | `radial_action_invoked` |
| errorHandling | Silent |
| isToggle | `true` |

### CONFIG_SCREEN_SHAKE_TOGGLE
| Field | Value |
|-------|-------|
| id | `devmod.config.screen_shake.toggle` |
| labelKey | `devmod.action.config.screen_shake.toggle` |
| category | `CONFIG` |
| actionType | `TOGGLE_SETTING` |
| preconditions | `always()` |
| effects | Toggles screen shake effect |
| fallback | N/A |
| telemetry | `radial_action_invoked` |
| errorHandling | Silent |
| isToggle | `true` |

---

## Testing Actions

### TEST_QA_OPEN
| Field | Value |
|-------|-------|
| id | `devmod.testing.qa.open` |
| labelKey | `devmod.action.testing.qa.open` |
| category | `TESTING` |
| actionType | `NAVIGATE_SCREEN` |
| preconditions | `always()` |
| effects | Opens QA testing screen |
| fallback | N/A |
| telemetry | `radial_action_invoked` |
| errorHandling | Exception logged |

### QA_SESSION_START
| Field | Value |
|-------|-------|
| id | `devmod.testing.session.start` |
| labelKey | `devmod.action.testing.session.start` |
| category | `TESTING` |
| actionType | `TRIGGER_EVENT` |
| preconditions | `always()` |
| effects | Starts new QA testing session |
| fallback | N/A |
| telemetry | `radial_action_invoked` |
| errorHandling | Silent |

### QA_REPORT_SAVE
| Field | Value |
|-------|-------|
| id | `devmod.testing.report.save` |
| labelKey | `devmod.action.testing.report.save` |
| category | `TESTING` |
| actionType | `TRIGGER_EVENT` |
| preconditions | `hasActiveSession()` |
| effects | Saves QA report to file |
| fallback | Action blocked |
| telemetry | `radial_action_invoked` |
| errorHandling | Toast on failure |

---

## Action Summary by Category

| Category | Count | Primary ActionTypes |
|----------|-------|---------------------|
| UI | 27 | NAVIGATE_SCREEN |
| DEBUG | 46 | TOGGLE_SETTING, TRIGGER_EVENT |
| HUD | 6 | TOGGLE_SETTING |
| CONFIG | 33 | TOGGLE_SETTING |
| COMMAND | 6 | RUN_SERVER_COMMAND |
| ARENA | 18 | RUN_SERVER_COMMAND |
| TELEMETRY | 32 | RUN_SERVER_COMMAND, OPEN_EXTERNAL |
| ENDURANCE | 4 | SEND_SERVER_RPC |
| TESTING | 23 | NAVIGATE_SCREEN, TRIGGER_EVENT |
| ABILITY | 2 | TRIGGER_EVENT |
| **TOTAL** | **197** | |

---

## Precondition Reference

| Precondition | Description |
|--------------|-------------|
| `always()` | Always available |
| `requiresPlayer()` | Player must be in world |
| `requiresPermissionOrClient(n)` | Op level n OR singleplayer |
| `requiresHeldItem()` | Player must hold an item |
| `requiresHeldWeapon()` | Player must hold a weapon |
| `hasActiveQuest()` | Player must have active quest |
| `hasActiveSession()` | QA session must be active |

---

## Telemetry Events Reference

| Event Type | Trigger |
|------------|---------|
| `radial_menu_opened` | Menu screen opens |
| `radial_menu_closed` | Menu screen closes |
| `radial_time_to_first_action` | First action after open |
| `radial_action_invoked` | Action executes successfully |
| `radial_action_blocked` | Precondition fails |
| `radial_action_failed` | Execution throws exception |
| `external_url_opened` | Browser opened URL |
| `external_url_copied` | URL copied to clipboard |

---

## Error Codes Reference

| Code | Description |
|------|-------------|
| `UNKNOWN_ACTION` | Action ID not found in registry |
| `PRECONDITION_FAILED` | Precondition check returned false |
| `REQUIRES_CONFIRM` | Action requires confirmation |
| `EXCEPTION` | Handler threw exception |
