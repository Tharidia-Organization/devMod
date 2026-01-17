# DevMod Radial Menu Census

**Version:** 1.0
**Last Updated:** 2024-12-27
**Status:** Complete Audit

This document provides a complete inventory of all DevMod features and their invocation methods.

---

## Executive Summary

| Metric | Count |
|--------|-------|
| **Action IDs (ActionIds.java)** | 298 |
| **Registered Keybinds** | 37 |
| **Chat Commands** | 45+ |
| **UI Screens** | 50+ |
| **Debug Features** | 30+ |
| **Event Listeners** | 8+ |

### Architecture Assessment

The mod already has a solid action-based architecture:

- **ActionRegistry** - Central registry with `invoke()`, `search()`, `actionsForContext()`
- **RadialAction** - Rich action model with preconditions, icons, categories, toggle support
- **ActionIds** - Comprehensive constants for all 298 action IDs
- **DevModClientActions** - Registers 200+ client-side actions
- **KeyInputHandler** - 37 keybinds, most already invoke ActionRegistry

**Key Finding:** The radial-first architecture is already 80% complete. Main gaps are:
1. Some keybinds have inline logic instead of calling `ActionRegistry.invoke()`
2. Commands don't consistently route through ActionRegistry
3. No orphan detection mechanism exists

---

## Feature Inventory by Category

### 1. UI / Screens (ActionCategory.UI)

| Feature ID | Description | Trigger (Current) | In Radial? | Radial Path |
|------------|-------------|-------------------|------------|-------------|
| `UI_RADIAL_OPEN` | Open Radial Menu | Keybind G | N/A | Root entry point |
| `UI_SETTINGS_OPEN` | Unified Settings | Keybind (unbound) | YES | Tools > Settings |
| `UI_ITEM_EDITOR_OPEN_AUTO` | Item Editor (auto-detect) | Keybind (unbound) | YES | Tools > Editors > Item |
| `UI_ITEM_EDITOR_OPEN_WEAPON` | Weapon Editor | Radial | YES | Tools > Editors > Weapon |
| `UI_ITEM_EDITOR_OPEN_ARMOR` | Armor Editor | Radial | YES | Tools > Editors > Armor |
| `UI_ITEM_EDITOR_OPEN_SHIELD` | Shield Editor | Radial | YES | Tools > Editors > Shield |
| `UI_ITEM_EDITOR_OPEN_GENERAL` | General Item Editor | Radial | YES | Tools > Editors > General |
| `UI_ITEM_EDITOR_OPEN_RECIPE` | Recipe Editor | Radial | YES | Tools > Editors > Recipe |
| `UI_ITEM_EDITOR_OPEN_FOOD` | Food Editor | Radial | YES | Tools > Editors > Food |
| `UI_ITEM_EDITOR_OPEN_FUEL` | Fuel Editor | Radial | YES | Tools > Editors > Fuel |
| `UI_ITEM_EDITOR_OPEN_USABLE` | Usable Item Editor | Radial | YES | Tools > Editors > Usable |
| `UI_TELEMETRY_DASHBOARD_OPEN` | Telemetry Dashboard | Keybind (unbound) | YES | Telemetry > Dashboard |
| `UI_MOB_CONFIG_OPEN` | Mob Config Screen | Keybind (unbound) | YES | Tools > Mob Config |
| `UI_MOB_EQUIPMENT_OPEN` | Mob Equipment Screen | Radial | YES | Tools > Mob Equipment |
| `UI_ROOM_BOUNDS_EDITOR_OPEN` | Room Bounds Editor | Keybind+Shift | YES | Analyze > Room Bounds > Editor |
| `UI_TESTING_HUB_OPEN` | Testing Hub | Keybind (unbound) | YES | Tools > Testing Hub |
| `UI_QUICK_TEST_WIZARD_OPEN` | Quick Test Wizard | Radial | YES | Tools > Quick Test |
| `UI_BADGE_TESTS_OPEN` | Badge Tests Screen | Radial | YES | Tools > Testing > Badges |
| `UI_VOXELLAB_UI_TESTS_OPEN` | VoxelLab UI Tests | Radial | YES | Tools > Testing > VoxelLab UI |
| `UI_QA_TESTING_OPEN` | QA Testing Screen | Keybind (unbound) | YES | Tools > QA Testing |
| `UI_KEYBINDS_OPEN` | Keybinds Screen | Radial | YES | Tools > Keybinds |
| `UI_PARTY_OPEN` | Party Screen | Keybind (unbound) | YES | Play > Party |
| `UI_PARTY_INVITE_POPUP_OPEN` | Party Invite Popup | Event | NO | - |
| `UI_MAILBOX_OPEN` | Mailbox Screen | Keybind M | YES | Play > Mailbox |
| `UI_TESTER_TASKS_OPEN` | Tester Tasks Screen | Keybind T | YES | Tools > Tester Tasks |
| `UI_QUEST_EDITOR_OPEN` | Quest Editor | Keybind (unbound) | YES | Play > Quest Editor |
| `UI_ENDURANCE_EDITOR_OPEN` | Endurance Settings | Keybind (unbound) | YES | Play > Endurance > Settings |
| `UI_ENDURANCE_SCREEN_OPEN` | Endurance Quest Screen | Radial | YES | Play > Endurance > Status |
| `UI_ENDURANCE_SHOP_OPEN` | Endurance Shop | Radial | YES | Play > Endurance > Shop |
| `UI_VOXELLAB_OPEN` | VoxelLab Screen | Radial | YES | Tools > VoxelLab |
| `UI_STAMINA_EDITOR_OPEN` | Stamina System Editor | Radial | YES | Tools > Editors > Stamina |
| `UI_QUEST_DEATH_OPEN` | Quest Death Screen | Event | NO | - |
| `UI_PERK_SELECTION_OPEN` | Perk Selection | Event | NO | - |
| `UI_QUEST_COMPLETION_OPEN` | Quest Completion | Event | NO | - |
| `UI_WAVE_CHECKPOINT_OPEN` | Wave Checkpoint | Event | NO | - |
| `UI_WELCOME_OPEN` | Welcome Screen | Radial | YES | Tools > Welcome |
| `UI_ONBOARDING_START` | Start Onboarding | Radial | YES | Tools > Onboarding |
| `UI_ONBOARDING_SKIP` | Skip Onboarding | Radial | YES | Tools > Onboarding (Skip) |

### 2. Debug / HUD Toggles (ActionCategory.DEBUG)

| Feature ID | Description | Trigger (Current) | In Radial? | Radial Path |
|------------|-------------|-------------------|------------|-------------|
| `DEBUG_OVERLAY_TOGGLE` | Main Debug Overlay | Keybind (unbound) | YES | Analyze > Debug > Overlay |
| `DEBUG_BODY_PARTS_TOGGLE` | Body Part Visualization | Keybind+Shift | YES | Analyze > Debug > Body Parts |
| `DEBUG_OVERLAYS_ENABLE_ALL` | Enable All Overlays | Radial | YES | Analyze > Debug > Enable All |
| `DEBUG_OVERLAYS_DISABLE_ALL` | Disable All Overlays | Radial | YES | Analyze > Debug > Disable All |
| `DEBUG_NATIVE_ENTITY_PATHING_TOGGLE` | Native Entity Pathing | Command | YES | Analyze > Native > Pathing |
| `DEBUG_NATIVE_ENTITY_GOALS_TOGGLE` | Native Entity Goals | Command | YES | Analyze > Native > Goals |
| `DEBUG_NATIVE_ENTITY_BRAINS_TOGGLE` | Native Entity Brains | Command | YES | Analyze > Native > Brains |
| `DEBUG_NATIVE_POI_TOGGLE` | Native POI Debug | Command | YES | Analyze > Native > POI |
| `DEBUG_NATIVE_RAIDS_TOGGLE` | Native Raids Debug | Command | YES | Analyze > Native > Raids |
| `DEBUG_NATIVE_BEES_TOGGLE` | Native Bees Debug | Command | YES | Analyze > Native > Bees |
| `DEBUG_NATIVE_GAME_EVENTS_TOGGLE` | Native Game Events | Command | YES | Analyze > Native > Events |
| `DEBUG_NATIVE_STRUCTURES_TOGGLE` | Native Structures | Command | YES | Analyze > Native > Structures |
| `DEBUG_LIGHT_OVERLAY_TOGGLE` | Light Level Overlay | Keybind (unbound) | YES | Analyze > Light > Overlay |
| `DEBUG_HEATMAP_CYCLE` | Cycle Heatmap Type | Keybind (unbound) | YES | Analyze > Heatmaps > Cycle |
| `DEBUG_HEATMAP_TOGGLE` | Toggle Heatmap | Radial | YES | Analyze > Heatmaps > Toggle |
| `DEBUG_HEATMAP_DEATH_TOGGLE` | Death Heatmap | Radial | YES | Analyze > Heatmaps > Death |
| `DEBUG_HEATMAP_MOVEMENT_TOGGLE` | Movement Heatmap | Radial | YES | Analyze > Heatmaps > Movement |
| `DEBUG_HEATMAP_CAMPING_TOGGLE` | Camping Heatmap | Radial | YES | Analyze > Heatmaps > Camping |
| `DEBUG_HEATMAP_STUCK_TOGGLE` | Stuck Heatmap | Radial | YES | Analyze > Heatmaps > Stuck |
| `DEBUG_HEATMAP_AGGRO_DROP_TOGGLE` | Aggro Drop Heatmap | Radial | YES | Analyze > Heatmaps > Aggro Drop |
| `DEBUG_HEATMAP_KITING_TOGGLE` | Kiting Heatmap | Radial | YES | Analyze > Heatmaps > Kiting |
| `DEBUG_HEATMAP_LIGHT_SPAWNABLE_TOGGLE` | Light Spawnable | Radial | YES | Analyze > Heatmaps > Light Spawn |
| `DEBUG_HEATMAP_LIGHT_DARK_TOGGLE` | Light Dark Areas | Radial | YES | Analyze > Heatmaps > Light Dark |
| `DEBUG_HEATMAP_CLEAR_CURRENT` | Clear Current Heatmap | Radial | YES | Analyze > Heatmaps > Clear |
| `DEBUG_HEATMAP_CLEAR_ALL` | Clear All Heatmaps | Radial | YES | Analyze > Heatmaps > Clear All |
| `DEBUG_ROOM_BOUNDS_TOGGLE` | Room Bounds Viz | Keybind (unbound) | YES | Analyze > Room Bounds > Toggle |
| `DEBUG_ROOM_BOUNDS_RELOAD` | Reload Room Bounds | Keybind+Ctrl | YES | Analyze > Room Bounds > Reload |
| `DEBUG_PATHFINDING_TOGGLE` | Pathfinding Debug | Keybind (unbound) | YES | Analyze > Pathfinding |
| `DEBUG_LOS_TOGGLE` | Line of Sight Debug | Keybind (unbound) | YES | Analyze > Line of Sight |
| `DEBUG_AGGRO_RANGE_TOGGLE` | Aggro Range Viz | Radial | YES | Analyze > Aggro Range |
| `DEBUG_VERTICAL_LEVELS_TOGGLE` | Vertical Levels | Keybind (unbound) | YES | Analyze > Vertical Levels |
| `DEBUG_SAFE_SPOTS_TOGGLE` | Safe Spots | Keybind (unbound) | YES | Analyze > Safe Spots |
| `DEBUG_ATTRIBUTE_MONITOR_TOGGLE` | Attribute Monitor | Keybind (unbound) | YES | Analyze > Attributes |
| `DEBUG_FPS_TRACKER_TOGGLE` | FPS Tracker | Keybind (unbound) | YES | Analyze > Performance > FPS |
| `DEBUG_PROFILER_TOGGLE` | Profiler | Keybind (unbound) | YES | Analyze > Performance > Profiler |
| `DEBUG_ENTITY_DENSITY_TOGGLE` | Entity Density | Keybind (unbound) | YES | Analyze > Entity Density |
| `DEBUG_BOSS_PHASE_TOGGLE` | Boss Phase Tracker | Keybind (unbound) | YES | Combat > Boss Phase |
| `DEBUG_SKILL_EFFICACY_TOGGLE` | Skill Efficacy | Keybind (unbound) | YES | Combat > Skill Efficacy |
| `DEBUG_SPAWNABILITY_TOGGLE` | Spawnability Analysis | Keybind (unbound) | YES | Analyze > Spawnability |
| `DEBUG_CHUNK_PERF_TOGGLE` | Chunk Performance | Keybind (unbound) | YES | Analyze > Performance > Chunks |
| `DEBUG_ECONOMY_TOGGLE` | Economy Overlay | Keybind (unbound) | YES | Analyze > Economy |
| `DEBUG_ECONOMY_VIEW_CYCLE` | Economy View Cycle | Keybind+Shift | YES | Analyze > Economy > Views |
| `DEBUG_ECONOMY_SORT_CYCLE` | Economy Sort Cycle | Keybind+Ctrl | YES | Analyze > Economy > Sort |
| `DEBUG_IMPACT_DISMISS` | Dismiss Impact HUD | Keybind (unbound) | YES | Combat > Impact > Dismiss |
| `DEBUG_SCREEN_SHAKE_TEST` | Test Screen Shake | Keybind (unbound) | YES | Combat > Test Shake |
| `DEBUG_COMMAND_HELP` | Debug Command Help | Command | YES | Analyze > Help |
| `DEBUG_COMMAND_LIST` | List Debug Features | Command | YES | Analyze > List |
| `DEBUG_COMMAND_OFF` | Disable All Debug | Command | YES | Analyze > Disable All |
| `DEBUG_COMMAND_TOGGLE` | Toggle Debug Feature | Command | YES | Analyze > Toggle |

### 3. HUD Toggles

| Feature ID | Description | Trigger (Current) | In Radial? | Radial Path |
|------------|-------------|-------------------|------------|-------------|
| `HUD_QUICK_HELP_TOGGLE` | Quick Help Overlay | Keybind (unbound) | YES | Tools > Quick Help |
| `HUD_IMPACT_TOGGLE` | Impact HUD | Radial | YES | Combat > Impact > Toggle |
| `HUD_IMPACT_3D_TOGGLE` | Impact 3D Display | Radial | YES | Combat > Impact > 3D |
| `HUD_QUEST_TOGGLE` | Quest HUD | Keybind (unbound) | YES | Play > Quest HUD |
| `HUD_ENDURANCE_TOGGLE` | Endurance HUD | Keybind+Shift | YES | Play > Endurance > HUD |
| `HUD_ENDURANCE_DETAILS_TOGGLE` | Endurance Details | Keybind+Ctrl | YES | Play > Endurance > Details |

### 4. Config Actions (ActionCategory.CONFIG)

| Feature ID | Description | Trigger (Current) | In Radial? | Radial Path |
|------------|-------------|-------------------|------------|-------------|
| `CONFIG_BODY_PART_DETECTION_TOGGLE` | Body Part Detection | Radial | YES | Tools > Config > Body Parts |
| `CONFIG_TELEMETRY_TOGGLE` | Telemetry Master Toggle | Radial | YES | Telemetry > Enable/Disable |
| `CONFIG_TELEMETRY_HITS_TOGGLE` | Telemetry Hits | Radial | YES | Telemetry > Config > Hits |
| `CONFIG_TELEMETRY_DEATHS_TOGGLE` | Telemetry Deaths | Radial | YES | Telemetry > Config > Deaths |
| `CONFIG_TELEMETRY_SPAWNS_TOGGLE` | Telemetry Spawns | Radial | YES | Telemetry > Config > Spawns |
| `CONFIG_IMPACT_HUD_HISTORY_TOGGLE` | Impact HUD History | Radial | YES | Combat > Impact > History |
| `CONFIG_IMPACT_HUD_DPS_TOGGLE` | Impact HUD DPS | Radial | YES | Combat > Impact > DPS |
| `CONFIG_IMPACT_HUD_POSITION_*` | Impact HUD Positions | Radial | YES | Combat > Impact > Position |
| `CONFIG_IMPACT_HUD_OFFSET_*` | Impact HUD Offsets | Radial | YES | Combat > Impact > Offset |
| `CONFIG_IMPACT_HUD_PRESET_EXPORT` | Export Impact Preset | Radial | YES | Combat > Impact > Export |
| `CONFIG_IMPACT_HUD_PRESET_IMPORT` | Import Impact Preset | Radial | YES | Combat > Impact > Import |
| `CONFIG_IMPACT_HUD_RESET_DEFAULTS` | Reset Impact HUD | Radial | YES | Combat > Impact > Reset |
| `CONFIG_IMPACT_VFX_TOGGLE` | Impact VFX Toggle | Radial | YES | Combat > VFX > Toggle |
| `CONFIG_IMPACT_VFX_VORTEX_TOGGLE` | VFX Vortex | Radial | YES | Combat > VFX > Vortex |
| `CONFIG_IMPACT_VFX_SLASH_TOGGLE` | VFX Slash | Radial | YES | Combat > VFX > Slash |
| `CONFIG_IMPACT_VFX_LINES_TOGGLE` | VFX Lines | Radial | YES | Combat > VFX > Lines |
| `CONFIG_IMPACT_VFX_INTENSITY_*` | VFX Intensity Levels | Radial | YES | Combat > VFX > Intensity |
| `CONFIG_IMPACT_VFX_RESET_DEFAULTS` | Reset VFX Defaults | Radial | YES | Combat > VFX > Reset |
| `CONFIG_SCREEN_SHAKE_TOGGLE` | Screen Shake | Radial | YES | Combat > Screen Shake |
| `CONFIG_PROJECTILE_TRAILS_TOGGLE` | Projectile Trails | Radial | YES | Combat > Projectile Trails |
| `CONFIG_BADGE_POPUPS_TOGGLE` | Badge Popups | Radial | YES | Play > Badge Popups |

### 5. Endurance Actions (ActionCategory.ENDURANCE)

| Feature ID | Description | Trigger (Current) | In Radial? | Radial Path |
|------------|-------------|-------------------|------------|-------------|
| `QUEST_TASK_COMPLETE` | Complete Quest Task | Keybind (unbound) | YES | Play > Quest > Complete Task |
| `ENDURANCE_QUEST_START` | Start Endurance Quest | Radial | YES | Play > Endurance > Start |
| `ENDURANCE_QUEST_CONTINUE` | Continue Quest | Keybind (unbound) | YES | Play > Endurance > Continue |
| `ENDURANCE_QUEST_EXIT` | Exit Quest | Keybind (unbound) | YES | Play > Endurance > Exit |

### 6. Abilities (ActionCategory.COMBAT)

| Feature ID | Description | Trigger (Current) | In Radial? | Radial Path |
|------------|-------------|-------------------|------------|-------------|
| `ABILITY_DASH` | Dash Ability | Keybind (unbound) | NO | Combat > Abilities > Dash |
| `ABILITY_DODGE` | Dodge Ability | Keybind (unbound) | NO | Combat > Abilities > Dodge |

### 7. Command Shortcuts

| Feature ID | Description | Trigger (Current) | In Radial? | Radial Path |
|------------|-------------|-------------------|------------|-------------|
| `COMMAND_GAMEMODE_CREATIVE` | Creative Mode | Radial | YES | Tools > Gamemode > Creative |
| `COMMAND_GAMEMODE_SURVIVAL` | Survival Mode | Radial | YES | Tools > Gamemode > Survival |
| `COMMAND_HEAL` | Heal Player | Radial | YES | Tools > Heal |
| `COMMAND_TIME_DAY` | Set Time Day | Radial | YES | Tools > Time > Day |
| `COMMAND_TIME_NIGHT` | Set Time Night | Radial | YES | Tools > Time > Night |
| `COMMAND_WEATHER_CLEAR` | Clear Weather | Radial | YES | Tools > Weather > Clear |

### 8. Arena Actions (ActionCategory.ARENA)

| Feature ID | Description | Trigger (Current) | In Radial? | Radial Path |
|------------|-------------|-------------------|------------|-------------|
| `ARENA_HELP` | Arena Help | Command | YES | Arena > Help |
| `ARENA_CREATE` | Create Arena | Command | YES | Arena > Create |
| `ARENA_TEMPLATE_LIST` | List Templates | Command | YES | Arena > Templates > List |
| `ARENA_TEMPLATE_INFO` | Template Info | Command | YES | Arena > Templates > Info |
| `ARENA_TEMPLATE_RELOAD` | Reload Templates | Command | YES | Arena > Templates > Reload |
| `ARENA_AUTOSMOKE_RUN` | Run Autosmoke | Command | YES | Arena > Autosmoke > Run |
| `ARENA_AUTOSMOKE_STATUS` | Autosmoke Status | Command | YES | Arena > Autosmoke > Status |
| `ARENA_AUTOSMOKE_SCHEDULE_STATUS` | Schedule Status | Command | YES | Arena > Autosmoke > Schedule |
| `ARENA_STATUS` | Arena Status | Command | YES | Arena > Status |
| `ARENA_VALIDATE` | Validate Template | Command | YES | Arena > Templates > Validate |
| `ARENA_FORCE` | Force Template | Command | YES | Arena > Force |
| `ARENA_FORCE_CLEAR` | Clear Force | Command | YES | Arena > Force > Clear |
| `ARENA_FORCE_STATUS` | Force Status | Command | YES | Arena > Force > Status |
| `ARENA_METRICS` | Template Metrics | Command | YES | Arena > Templates > Metrics |
| `ARENA_HUD_TOGGLE` | Toggle Arena HUD | Command/Keybind | YES | Arena > HUD > Toggle |
| `ARENA_HUD_ON` | Enable Arena HUD | Command | YES | Arena > HUD > On |
| `ARENA_HUD_OFF` | Disable Arena HUD | Command | YES | Arena > HUD > Off |
| `ARENA_HUD_STATUS` | Arena HUD Status | Command | YES | Arena > HUD > Status |
| `ARENA_QUICK_TEST_WIZARD_OPEN` | Quick Test Wizard | Radial | YES | Arena > Quick Test |

### 9. Telemetry Actions (ActionCategory.TELEMETRY)

| Feature ID | Description | Trigger (Current) | In Radial? | Radial Path |
|------------|-------------|-------------------|------------|-------------|
| `TELEMETRY_RELOAD` | Reload Telemetry | Command | YES | Telemetry > Reload |
| `TELEMETRY_DUMP_WEAPONS` | Dump Weapons Data | Command | YES | Telemetry > Dump > Weapons |
| `TELEMETRY_DUMP_ROOMS` | Dump Rooms Data | Command | YES | Telemetry > Dump > Rooms |
| `TELEMETRY_DUMP_FIGHTS` | Dump Fights Data | Command | YES | Telemetry > Dump > Fights |
| `TELEMETRY_DUMP_MINIONS` | Dump Minions Data | Command | YES | Telemetry > Dump > Minions |
| `TELEMETRY_EXPORT_HEATMAPS` | Export Heatmaps | Command | YES | Telemetry > Export > Heatmaps |
| `TELEMETRY_EXPORT_PNG` | Export PNG | Command | YES | Telemetry > Export > PNG |
| `TELEMETRY_EXPORT_CSV` | Export CSV | Command | YES | Telemetry > Export > CSV |
| `TELEMETRY_EXPORT_JSON` | Export JSON | Command | YES | Telemetry > Export > JSON |
| `TELEMETRY_EXPORT_ALL` | Export All | Command | YES | Telemetry > Export > All |
| `TELEMETRY_EXPORT_HEATMAP_*` | Export Specific Heatmap | Command | YES | Telemetry > Export > Heatmap > * |
| `TELEMETRY_EXPORT_DAMAGE_STATS` | Export Damage Stats | Command | YES | Telemetry > Export > Damage |
| `TELEMETRY_SCAN_LIGHT_ALL` | Scan All Light | Command | YES | Telemetry > Scan > Light All |
| `TELEMETRY_SCAN_LIGHT_ROOM` | Scan Room Light | Command | YES | Telemetry > Scan > Light Room |
| `TELEMETRY_SPAWNABILITY` | Spawnability Report | Command | YES | Telemetry > Spawnability |
| `TELEMETRY_DESIRELINES_*` | Desire Lines Analysis | Command | YES | Telemetry > Desire Lines |
| `TELEMETRY_DUNGEONS_*` | Dungeon Analytics | Command | YES | Telemetry > Dungeons |
| `TELEMETRY_BACKTRACKING_*` | Backtracking Analysis | Command | YES | Telemetry > Backtracking |
| `TELEMETRY_DASHBOARD_SERVER_*` | Dashboard Server | Command | YES | Telemetry > Dashboard |

### 10. Testing Actions (ActionCategory.TESTING)

| Feature ID | Description | Trigger (Current) | In Radial? | Radial Path |
|------------|-------------|-------------------|------------|-------------|
| `TEST_HUD_ON/OFF/TOGGLE` | Test HUD Control | Command | YES | Tools > Testing > HUD |
| `TEST_HUD_EXPORT/IMPORT` | Export/Import Test HUD | Command | YES | Tools > Testing > HUD Export |
| `TEST_PANEL_ON/OFF/TOGGLE` | Test Panel Control | Command | YES | Tools > Testing > Panel |
| `TEST_DEBUG_ON/OFF/TOGGLE` | Test Debug Control | Command | YES | Tools > Testing > Debug |
| `TEST_ENDURANCE_*` | Endurance Test Tools | Command | YES | Tools > Testing > Endurance |
| `TEST_DEBUGBOX` | Show Debug Box | Command | YES | Tools > Testing > Debug Box |
| `TEST_DEBUGCLEAR` | Clear Debug | Command | YES | Tools > Testing > Clear |
| `TEST_INFO` | Test Info | Command | YES | Tools > Testing > Info |
| `TEST_QA_OPEN` | Open QA Screen | Command | YES | Tools > QA Testing |
| `TEST_BODYPART_INFO` | Body Part Info | Command | YES | Tools > Testing > Body Parts |

### 11. QA Session Actions

| Feature ID | Description | Trigger (Current) | In Radial? | Radial Path |
|------------|-------------|-------------------|------------|-------------|
| `QA_SESSION_START` | Start QA Session | Radial | YES | Tools > QA > Start Session |
| `QA_SESSION_RESUME` | Resume QA Session | Radial | YES | Tools > QA > Resume |
| `QA_REPORT_SAVE` | Save QA Report | Radial | YES | Tools > QA > Save Report |
| `QA_REPORT_COPY` | Copy QA Report | Radial | YES | Tools > QA > Copy Report |
| `QA_TEST_PASS/FAIL/SKIP/AUTO` | Test Verdicts | Radial | YES | Tools > QA > Verdicts |

### 12. Game Design Config

| Feature ID | Description | Trigger (Current) | In Radial? | Radial Path |
|------------|-------------|-------------------|------------|-------------|
| `CONFIG_GAMEDESIGN_RELOAD` | Reload Game Design | Radial | YES | Tools > Game Design > Reload |
| `CONFIG_GAMEDESIGN_SAVE` | Save Game Design | Radial | YES | Tools > Game Design > Save |
| `CONFIG_GAMEDESIGN_RESET` | Reset Game Design | Radial | YES | Tools > Game Design > Reset |
| `CONFIG_RESONANCE_TOGGLE` | Resonance Chain | Radial | YES | Tools > Game Design > Resonance |
| `CONFIG_CONTRACTS_TOGGLE` | Blood Contracts | Radial | YES | Tools > Game Design > Contracts |
| `CONFIG_SIGNATURE_WEAPONS_TOGGLE` | Signature Weapons | Radial | YES | Tools > Game Design > Signatures |
| `CONFIG_NEMESIS_TOGGLE` | Nemesis Evolution | Radial | YES | Tools > Game Design > Nemesis |
| `CONFIG_TIDE_TOGGLE` | The Tide | Radial | YES | Tools > Game Design > Tide |
| `CONFIG_GAMEDESIGN_PRESET_*` | Game Design Presets | Radial | YES | Tools > Game Design > Presets |

### 13. Mailbox/News Admin

| Feature ID | Description | Trigger (Current) | In Radial? | Radial Path |
|------------|-------------|-------------------|------------|-------------|
| `MAILBOX_COMMAND_*` | Mailbox Admin | Command | YES | Tools > Admin > Mailbox |
| `NEWS_COMMAND_*` | News Admin | Command | YES | Tools > Admin > News |

---

## Keybinds Inventory

### Currently Bound (3)

| Keybind | Key | Action ID | Notes |
|---------|-----|-----------|-------|
| `OPEN_RADIAL_MENU_KEY` | G | `UI_RADIAL_OPEN` | Primary entry point |
| `OPEN_MAILBOX_KEY` | M | `UI_MAILBOX_OPEN` | Communication |
| `OPEN_TESTER_TASKS_KEY` | T | `UI_TESTER_TASKS_OPEN` | QA tasks |

### Currently Unbound (34)

All other keybinds default to `GLFW_KEY_UNKNOWN` and can be remapped via vanilla keybinds screen.

---

## Gap Analysis

### 1. Keybinds with Inline Logic (Need Migration)

**Location:** `RenderEvents.handleKeyBindings()` (lines 400-600)

Most keybinds already call `ActionRegistry.invoke()`, but handlers check modifiers inline:

```java
// Example of pattern that needs unification:
if (KeyInputHandler.TOGGLE_ECONOMY_KEY.consumeClick()) {
    if (Screen.hasShiftDown()) {
        ActionRegistry.invoke(ActionIds.DEBUG_ECONOMY_VIEW_CYCLE, ...);
    } else if (Screen.hasControlDown()) {
        ActionRegistry.invoke(ActionIds.DEBUG_ECONOMY_SORT_CYCLE, ...);
    } else {
        ActionRegistry.invoke(ActionIds.DEBUG_ECONOMY_TOGGLE, ...);
    }
}
```

**Recommendation:** Create modifier-aware action variants or use context-aware preconditions.

### 2. Commands Not Routed Through ActionRegistry

**Location:** Various command classes

Some commands execute logic directly instead of invoking ActionRegistry:
- `DebugCommand.java` - Partially integrated
- `MailboxCommands.java` - Not integrated
- `DashboardCommand.java` - Not integrated

### 3. Orphan Features (No Radial Access)

| Feature | Location | Issue |
|---------|----------|-------|
| `ABILITY_DASH` | Combat | No radial entry (keybind-only) |
| `ABILITY_DODGE` | Combat | No radial entry (keybind-only) |
| Party invite popup | Event-triggered | N/A (automatic) |
| Quest death screen | Event-triggered | N/A (automatic) |

### 4. Missing Action Registrations

Some ActionIds constants exist but may not have RadialAction registrations:
- Run orphan check to verify

---

## Current MacroCategory Structure

```
ANALYZE (Blue)     - Debug, spatial analysis, visualization
TELEMETRY (Cyan)   - Telemetry, dashboards, exports
COMBAT (Red)       - Combat tools, abilities, heatmaps
ARENA (Emerald)    - Arena ops, templates, autosmoke
PLAY (Green)       - Quests, endurance, party
TOOLS (Orange)     - Settings, editors, testing utilities
```

---

## Priority Actions

### P0 - Critical (Must Have)

| Action | Reason |
|--------|--------|
| Open Radial (G) | Primary entry point |
| Endurance Start/Continue/Exit | Core gameplay |
| Arena Create/Template Reload | Testing workflow |

### P1 - High (Should Have)

| Action | Reason |
|--------|--------|
| Debug toggles | Development workflow |
| Telemetry export | Data analysis |
| Item editors | Balance tuning |

### P2 - Medium (Nice to Have)

| Action | Reason |
|--------|--------|
| Heatmap variants | Detailed analysis |
| Config presets | Convenience |
| Economy overlay | Secondary analytics |

---

## Next Steps

1. **Create RADIAL_NAV_MAP.md** - Define optimal menu structure
2. **Implement ActionKeybindRegistry** - Map keybinds to actions bidirectionally
3. **Migrate RenderEvents handlers** - All keybinds invoke ActionRegistry
4. **Create orphan check test** - CI/runtime validation
5. **Add missing radial entries** - ABILITY_DASH, ABILITY_DODGE
