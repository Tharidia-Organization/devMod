# Traceability Matrix

> Last updated: 2025-12-26
> Status: CURRENT (verified against code)
> Source of truth: `ChannelId`, `NetworkHandler`, `KeyInputHandler`, command registration classes.

---

## Commands -> Entrypoints -> Components

| Command Root | Entrypoint | Components |
|---|---|---|
| `/devtest` | `TestHarnessCommands` | QA tools, endurance helpers, test HUD/panel. |
| `/arena` | `ArenaCommandEvents` → `ArenaCommands` | Arena templates, autosmoke, ops. |
| `/devdebug` | `DebugCommand` | Debug toggles + native debug sender. |
| `/devmod telemetry` | `TelemetryReloadCommand` | Telemetry reload/dump/export/scan. |
| `/devmod dashboard` | `DashboardCommand` | Telemetry dashboard server. |
| `/devmod dungeon` | `DungeonCommand` | Dungeon run debug flow. |
| `/mailbox` | `MailboxCommands` | Mailbox admin ops. |
| `/news` | `MailboxCommands` | News CRUD + publish. |

---

## Keybinds -> UI

Default-bound keys (others are unbound by default):

| Key | Mapping | Screen/Overlay |
|---|---|---|
| `G` | `OPEN_RADIAL_MENU_KEY` | `RadialMenuScreen` |
| `M` | `OPEN_MAILBOX_KEY` | `MailboxScreen` |
| `T` | `OPEN_TESTER_TASKS_KEY` | `TesterTaskScreen` |

Unbound by default (registered in `KeyInputHandler`): settings, editor, dashboard, QA/testing, quest actions/HUD, overlays, ability keys.

---

## Network Payloads -> Handlers

| System | ChannelId (source) | Handler |
|---|---|---|
| Mob/Item/Config | `MOB_STATS`, `WEAPON_*`, `ARMOR_STATS`, `USABLE_STATS`, `FOOD_STATS`, `FUEL_STATS`, `RECIPE_SYNC` | `MobItemNetworkHandler`, `ConfigNetworkHandler` |
| Endurance | `START_QUEST`, `QUEST_*`, `SHOP_*`, `PERK_*`, `WAVE_DIRECTIVE_*` | `EnduranceNetworkHandler` |
| Party | `PARTY_*`, `INVITE_*`, `QUEST_SEQUENCE` | `PartyNetworkHandler` |
| Ability | `ABILITY_ACTION` | `AbilityNetworkHandler` |
| Arena | `BUILD_PROGRESS` | client hooks via `NetworkHandler` |
| Telemetry | `TELEMETRY_BATCH` | `ConfigNetworkHandler` |
| Mailbox | `MAILBOX_*`, `NEWS_*`, `TASK_*` | `MailboxNetworkHandler` |
| Debug | `DEBUG_*` | `DebugNetworkHandler` |

Full registry and IDs: `src/main/java/com/devmod/network/ChannelId.java`.

---

## Persistence Surfaces

| System | Persistence |
|---|---|
| Config/Overrides | `config/devmod/*` via `ConfigPaths` |
| Telemetry | DuckDB + NDJSON (`telemetry/duckdb`, `run/*`) |
| Mailbox | DuckDB (`DuckDbMailboxRepository`) |
| Arena templates | `config/devmod/arena_templates/` |

---

## Cross-References
- [[ENTRYPOINTS]]
- [[PROJECT_TOPOLOGY]]
- [[areas/telemetry/README]]
