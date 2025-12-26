# Radial Menu Audit

> Last updated: 2025-12-21
> Status: HISTORICAL (legacy snapshot; superseded by `docs/areas/radial/README.md`)

**Versione**: 1.0
**Stato**: Architettura matura, miglioramenti incrementali richiesti

---

## 1. Architettura Attuale

### 1.1 Componenti Core

| Componente | File | Stato | Note |
|------------|------|-------|------|
| ActionRegistry | `actions/ActionRegistry.java` | ✅ OK | Registry centrale con preconditions, confirmation, telemetria base |
| RadialAction (core) | `actions/RadialAction.java` | ✅ OK | Builder pattern, handler tipizzato |
| ActionContext | `actions/ActionContext.java` | ✅ OK | Contesto ricco (player, origin, modifiers) |
| ActionIds | `actions/ActionIds.java` | ✅ OK | 150+ azioni definite |
| RadialMenuRegistry | `ui/radial/RadialMenuRegistry.java` | ✅ OK | 24 categorie, tutte usano `registry()` |
| RadialMenuScreenV3 | `ui/radial/RadialMenuScreenV3.java` | ⚠️ Parziale | Manca telemetria menu_opened |

### 1.2 Componenti Legacy (da deprecare)

| Componente | File | Linee | Problema |
|------------|------|-------|----------|
| CommandAction | `ui/radial/RadialAction.java` | 248-295 | Esegue `sendCommand()` diretto |
| CustomAction | `ui/radial/RadialAction.java` | 405-444 | Runnable opaco |
| RadialMenuItem.command() | `ui/radial/RadialMenuItem.java` | 193-202 | Factory per CommandAction |
| RadialMenuItem.custom() | `ui/radial/RadialMenuItem.java` | 174-184 | Factory per CustomAction |

**Verifica usi legacy**: `grep -r "RadialMenuItem.(command|custom)\(" src/` → **0 risultati**
I factory legacy esistono ma NON sono usati. Tutto il codice usa `RadialMenuItem.registry()`.

---

## 2. Elenco Azioni (ActionIds.java)

### 2.1 UI / Screens (17 azioni)

| ActionId | Categoria | Tipo |
|----------|-----------|------|
| UI_RADIAL_OPEN | UI | NAVIGATE_SCREEN |
| UI_SETTINGS_OPEN | UI | NAVIGATE_SCREEN |
| UI_ITEM_EDITOR_OPEN_AUTO | UI | NAVIGATE_SCREEN |
| UI_ITEM_EDITOR_OPEN_WEAPON | UI | NAVIGATE_SCREEN |
| UI_ITEM_EDITOR_OPEN_ARMOR | UI | NAVIGATE_SCREEN |
| UI_ITEM_EDITOR_OPEN_SHIELD | UI | NAVIGATE_SCREEN |
| UI_ITEM_EDITOR_OPEN_GENERAL | UI | NAVIGATE_SCREEN |
| UI_ITEM_EDITOR_OPEN_RECIPE | UI | NAVIGATE_SCREEN |
| UI_ITEM_EDITOR_OPEN_FOOD | UI | NAVIGATE_SCREEN |
| UI_ITEM_EDITOR_OPEN_FUEL | UI | NAVIGATE_SCREEN |
| UI_ITEM_EDITOR_OPEN_USABLE | UI | NAVIGATE_SCREEN |
| UI_TELEMETRY_DASHBOARD_OPEN | UI | NAVIGATE_SCREEN |
| UI_MOB_CONFIG_OPEN | UI | NAVIGATE_SCREEN |
| UI_MOB_EQUIPMENT_OPEN | UI | NAVIGATE_SCREEN |
| UI_ROOM_BOUNDS_EDITOR_OPEN | UI | NAVIGATE_SCREEN |
| UI_TESTING_HUB_OPEN | UI | NAVIGATE_SCREEN |
| + altri 30 UI_* | UI | NAVIGATE_SCREEN |

### 2.2 Debug / HUD Toggles (50 azioni)

| ActionId | Categoria | Tipo |
|----------|-----------|------|
| DEBUG_OVERLAY_TOGGLE | DEBUG | TOGGLE_SETTING |
| DEBUG_BODY_PARTS_TOGGLE | DEBUG | TOGGLE_SETTING |
| DEBUG_OVERLAYS_ENABLE_ALL | DEBUG | TRIGGER_EVENT |
| DEBUG_OVERLAYS_DISABLE_ALL | DEBUG | TRIGGER_EVENT |
| DEBUG_NATIVE_ENTITY_PATHING_TOGGLE | DEBUG | TOGGLE_SETTING |
| DEBUG_NATIVE_ENTITY_GOALS_TOGGLE | DEBUG | TOGGLE_SETTING |
| DEBUG_NATIVE_ENTITY_BRAINS_TOGGLE | DEBUG | TOGGLE_SETTING |
| DEBUG_NATIVE_POI_TOGGLE | DEBUG | TOGGLE_SETTING |
| DEBUG_NATIVE_RAIDS_TOGGLE | DEBUG | TOGGLE_SETTING |
| DEBUG_NATIVE_BEES_TOGGLE | DEBUG | TOGGLE_SETTING |
| DEBUG_NATIVE_GAME_EVENTS_TOGGLE | DEBUG | TOGGLE_SETTING |
| DEBUG_NATIVE_STRUCTURES_TOGGLE | DEBUG | TOGGLE_SETTING |
| DEBUG_LIGHT_OVERLAY_TOGGLE | DEBUG | TOGGLE_SETTING |
| DEBUG_HEATMAP_CYCLE | DEBUG | TRIGGER_EVENT |
| DEBUG_HEATMAP_TOGGLE | DEBUG | TOGGLE_SETTING |
| DEBUG_HEATMAP_*_TOGGLE (8 tipi) | DEBUG | TOGGLE_SETTING |
| HUD_IMPACT_TOGGLE | HUD | TOGGLE_SETTING |
| HUD_IMPACT_3D_TOGGLE | HUD | TOGGLE_SETTING |
| HUD_QUEST_TOGGLE | HUD | TOGGLE_SETTING |
| HUD_ENDURANCE_TOGGLE | HUD | TOGGLE_SETTING |
| + altri 20 DEBUG_* | DEBUG | vari |

### 2.3 Config Toggles (20 azioni)

| ActionId | Categoria | Tipo |
|----------|-----------|------|
| CONFIG_BODY_PART_DETECTION_TOGGLE | CONFIG | TOGGLE_SETTING |
| CONFIG_TELEMETRY_TOGGLE | CONFIG | TOGGLE_SETTING |
| CONFIG_TELEMETRY_HITS_TOGGLE | CONFIG | TOGGLE_SETTING |
| CONFIG_TELEMETRY_DEATHS_TOGGLE | CONFIG | TOGGLE_SETTING |
| CONFIG_TELEMETRY_SPAWNS_TOGGLE | CONFIG | TOGGLE_SETTING |
| CONFIG_IMPACT_HUD_* (15 azioni) | CONFIG | TOGGLE_SETTING |
| CONFIG_IMPACT_VFX_* (8 azioni) | CONFIG | TOGGLE_SETTING |
| CONFIG_SCREEN_SHAKE_TOGGLE | CONFIG | TOGGLE_SETTING |
| CONFIG_PROJECTILE_TRAILS_TOGGLE | CONFIG | TOGGLE_SETTING |
| CONFIG_BADGE_POPUPS_TOGGLE | CONFIG | TOGGLE_SETTING |

### 2.4 Command Shortcuts (6 azioni)

| ActionId | Comando | Implementazione |
|----------|---------|-----------------|
| COMMAND_GAMEMODE_CREATIVE | `gamemode creative` | `context.executeCommand()` ✅ |
| COMMAND_GAMEMODE_SURVIVAL | `gamemode survival` | `context.executeCommand()` ✅ |
| COMMAND_HEAL | `heal` | `context.executeCommand()` ✅ |
| COMMAND_TIME_DAY | `time set day` | `context.executeCommand()` ✅ |
| COMMAND_TIME_NIGHT | `time set night` | `context.executeCommand()` ✅ |
| COMMAND_WEATHER_CLEAR | `weather clear` | `context.executeCommand()` ✅ |

**Nota**: Tutti i command shortcuts passano attraverso ActionRegistry e usano `context.executeCommand()`, NON `sendCommand()` diretto. Architettura corretta.

### 2.5 Arena (20 azioni)

| ActionId | Categoria | Tipo |
|----------|-----------|------|
| ARENA_HELP | ARENA | RUN_SERVER_COMMAND |
| ARENA_CREATE | ARENA | RUN_SERVER_COMMAND |
| ARENA_TEMPLATE_LIST | ARENA | RUN_SERVER_COMMAND |
| ARENA_TEMPLATE_INFO | ARENA | RUN_SERVER_COMMAND |
| ARENA_TEMPLATE_RELOAD | ARENA | RUN_SERVER_COMMAND |
| ARENA_AUTOSMOKE_* (3 azioni) | ARENA | RUN_SERVER_COMMAND |
| ARENA_STATUS | ARENA | RUN_SERVER_COMMAND |
| ARENA_VALIDATE | ARENA | RUN_SERVER_COMMAND |
| ARENA_FORCE_* (3 azioni) | ARENA | RUN_SERVER_COMMAND |
| ARENA_METRICS | ARENA | RUN_SERVER_COMMAND |
| ARENA_HUD_* (4 azioni) | ARENA | TOGGLE_SETTING |
| ARENA_QUICK_TEST_WIZARD_OPEN | ARENA | NAVIGATE_SCREEN |

### 2.6 Telemetry (30 azioni)

| ActionId | Categoria | Tipo |
|----------|-----------|------|
| TELEMETRY_RELOAD | TELEMETRY | RUN_SERVER_COMMAND |
| TELEMETRY_DUMP_* (4 azioni) | TELEMETRY | RUN_SERVER_COMMAND |
| TELEMETRY_EXPORT_* (15 azioni) | TELEMETRY | RUN_SERVER_COMMAND |
| TELEMETRY_SCAN_* (2 azioni) | TELEMETRY | RUN_SERVER_COMMAND |
| TELEMETRY_SPAWNABILITY | TELEMETRY | RUN_SERVER_COMMAND |
| TELEMETRY_DESIRELINES_* (2 azioni) | TELEMETRY | RUN_SERVER_COMMAND |
| TELEMETRY_DUNGEONS_* (2 azioni) | TELEMETRY | RUN_SERVER_COMMAND |
| TELEMETRY_BACKTRACKING_* (2 azioni) | TELEMETRY | RUN_SERVER_COMMAND |
| TELEMETRY_DASHBOARD_SERVER_OPEN | TELEMETRY | OPEN_EXTERNAL |
| TELEMETRY_DASHBOARD_SERVER_START | TELEMETRY | RUN_SERVER_COMMAND |
| TELEMETRY_DASHBOARD_SERVER_STOP | TELEMETRY | RUN_SERVER_COMMAND |
| TELEMETRY_DASHBOARD_SERVER_STATUS | TELEMETRY | RUN_SERVER_COMMAND |
| DUNGEON_* (4 azioni) | TELEMETRY | RUN_SERVER_COMMAND |

### 2.7 Endurance / Quests (10 azioni)

| ActionId | Categoria | Tipo |
|----------|-----------|------|
| QUEST_TASK_COMPLETE | GAMEPLAY | SEND_SERVER_RPC |
| ENDURANCE_QUEST_START | GAMEPLAY | SEND_SERVER_RPC |
| ENDURANCE_QUEST_CONTINUE | GAMEPLAY | SEND_SERVER_RPC |
| ENDURANCE_QUEST_EXIT | GAMEPLAY | SEND_SERVER_RPC |
| UI_ENDURANCE_SCREEN_OPEN | UI | NAVIGATE_SCREEN |
| UI_ENDURANCE_SHOP_OPEN | UI | NAVIGATE_SCREEN |
| UI_QUEST_EDITOR_OPEN | UI | NAVIGATE_SCREEN |
| UI_PERK_SELECTION_OPEN | UI | NAVIGATE_SCREEN |
| UI_WAVE_CHECKPOINT_OPEN | UI | NAVIGATE_SCREEN |
| UI_QUEST_DEATH_OPEN | UI | NAVIGATE_SCREEN |

### 2.8 Abilities (2 azioni)

| ActionId | Categoria | Tipo |
|----------|-----------|------|
| ABILITY_DASH | GAMEPLAY | TRIGGER_EVENT |
| ABILITY_DODGE | GAMEPLAY | TRIGGER_EVENT |

### 2.9 Testing / QA (30 azioni)

| ActionId | Categoria | Tipo |
|----------|-----------|------|
| TEST_HUD_* (5 azioni) | TESTING | RUN_SERVER_COMMAND |
| TEST_PANEL_* (4 azioni) | TESTING | RUN_SERVER_COMMAND |
| TEST_DEBUG_* (5 azioni) | TESTING | RUN_SERVER_COMMAND |
| TEST_ENDURANCE_* (6 azioni) | TESTING | RUN_SERVER_COMMAND |
| TEST_INFO | TESTING | RUN_SERVER_COMMAND |
| TEST_QA_OPEN | TESTING | NAVIGATE_SCREEN |
| TEST_BODYPART_INFO | TESTING | RUN_SERVER_COMMAND |
| QA_SESSION_* (2 azioni) | QA | TRIGGER_EVENT |
| QA_REPORT_* (2 azioni) | QA | TRIGGER_EVENT |
| QA_TEST_* (4 azioni) | QA | TRIGGER_EVENT |

---

## 3. RadialMenuRegistry - Struttura Categorie

### 3.1 Macro-categorie (6)

| Macro | Colore | Categorie |
|-------|--------|-----------|
| ANALYZE | Blu | Debug, Spatial, Performance |
| TELEMETRY | Cyan | Ops, Data, Scan, Dashboard |
| COMBAT | Rosso | HUD, Heatmaps, Abilities |
| ARENA | Verde | Ops, Templates, Force, Autosmoke, HUD |
| PLAY | Rosa | Endurance, Quests, HUD, Party |
| TOOLS | Giallo | Settings, Testing, Mob Edit, Items, Commands |

### 3.2 Categorie con Subcategory

| Categoria | Subcategory | Items |
|-----------|-------------|-------|
| Debug | native_debug | 8 toggle nativi MC |
| Spatial | room_bounds | 5 azioni editor |
| Telemetry Data | heatmap_exports | 9 tipi export |
| Heatmaps | heatmap_types | 8 tipi heatmap |
| Settings | config_hud, config_effects, config_telemetry, config_combat | Nested config |

### 3.3 Visibility Dinamico

| Categoria | Item | Condizione |
|-----------|------|------------|
| Item Editors | Weapon Editor | `isWeaponItem(getHeldItem())` |
| Item Editors | Armor Editor | `isArmorItem(getHeldItem())` |
| Item Editors | Shield Editor | `isShieldItem(getHeldItem())` |
| Item Editors | General Editor | `isGeneralItem(getHeldItem())` |
| Item Editors | Food Editor | `isFoodItem(getHeldItem())` |
| Item Editors | Fuel Editor | `isFuelItem(getHeldItem())` |
| Item Editors | Usable Editor | `isUsableItem(getHeldItem())` |

---

## 4. Incongruenze Trovate

### 4.1 Nessun uso di pattern legacy

| Pattern | Ricerca | Risultato |
|---------|---------|-----------|
| `RadialMenuItem.command(` | grep | 0 match |
| `RadialMenuItem.custom(` | grep | 0 match |
| `RadialAction.command(` (in business code) | grep | 0 match (solo definizione) |
| `RadialAction.custom(` (in business code) | grep | 0 match (solo definizione) |

**Conclusione**: Il codice è già pulito. I factory legacy esistono ma non sono usati.

### 4.2 Telemetria incompleta

| Evento | Attuale | Richiesto |
|--------|---------|-----------|
| action.invoked | ✅ `{action, origin, player, dimension, success}` | Aggiungere `result`, `errorCode`, `durationMs` |
| radial_menu_opened | ❌ Non presente | Aggiungere con `macroCategory`, `timestamp` |
| radial_time_to_first_action | ❌ Non presente | Aggiungere con `timeMs` |
| radial_menu_closed | ❌ Non presente | Aggiungere con `actionsExecuted`, `durationMs` |

### 4.3 Dashboard senza confirm

| Azione | Attuale | Richiesto |
|--------|---------|-----------|
| TELEMETRY_DASHBOARD_SERVER_OPEN | Apre direttamente browser | Dialog confirm + copy fallback |

---

## 5. Raccomandazioni

### 5.1 Priorità ALTA

1. **ActionResult**: Aggiungere record per risultati strutturati
2. **Telemetria estesa**: Eventi `radial_menu_opened`, `time_to_first_action`, `radial_action_*`
3. **OpenExternalConfirmScreen**: Dialog per apertura URL esterni

### 5.2 Priorità MEDIA

4. **ActionType enum**: Tipizzazione esplicita delle azioni
5. **Deprecare legacy**: `@Deprecated` su CommandAction/CustomAction

### 5.3 Priorità BASSA

6. **Cleanup factory**: Rimuovere factory inutilizzati da RadialMenuItem

---

## 6. File Critici

| File | Linee | Azione |
|------|-------|--------|
| `actions/ActionRegistry.java` | 41-74, 104-128 | Estendere invoke(), telemetria |
| `actions/RadialAction.java` | tutto | Aggiungere actionType |
| `ui/radial/RadialAction.java` | 248-295, 405-444 | @Deprecated |
| `ui/radial/RadialMenuItem.java` | 174-202 | @Deprecated factory |
| `ui/radial/RadialMenuScreenV3.java` | init(), executeItem() | Telemetria menu |
| `telemetry/dashboard/DashboardCommand.java` | 55 | OpenExternalConfirmScreen |
