# Entry Points Inventory

> Last updated: 2025-12-26
> Status: CURRENT (verified against code)
> Source of truth: `@EventBusSubscriber`, `RegisterCommandsEvent`, `KeyInputHandler`, `ChannelId`, `NetworkHandler`.
> Quick queries: `rg -n "@EventBusSubscriber" src/main/java/com/devmod`, `rg -n "RegisterCommandsEvent" src/main/java/com/devmod`.

---

## 1. Mod Lifecycle

| Entry Point | File | Notes |
|---|---|---|
| `@Mod("devmod")` | `src/main/java/com/devmod/DevMod.java` | Common init: config registration, arena bootstrap, gameplay overrides, Debug payload registration. |
| Mod config load/reload | `src/main/java/com/devmod/DevMod.java` | `ModConfigEvent.Loading` + `ModConfigEvent.Reloading`. |
| Client mod init | `src/main/java/com/devmod/client/DevModClient.java` | Config screen factory, overlays, keybinds, client actions. |
| Client setup event | `src/main/java/com/devmod/client/DevModClient.java` | `FMLClientSetupEvent` init + Settings load. |

---

## 2. Commands (Server)

| Command Root | Registration | Notes |
|---|---|---|
| `/devtest` | `src/main/java/com/devmod/gametest/TestHarnessCommands.java` | HUD/panel/debug tools + endurance helpers. |
| `/arena` | `src/main/java/com/devmod/arena/ArenaCommandEvents.java` → `com.devmod.arena.command.ArenaCommands` | Arena template ops, autosmoke, force, etc. |
| `/devdebug` | `src/main/java/com/devmod/debug/DebugCommand.java` | Debug feature toggles. |
| `/devmod telemetry` | `src/main/java/com/devmod/telemetry/TelemetryReloadCommand.java` | Reload/dump/export/scan/analysis. |
| `/devmod dashboard` | `src/main/java/com/devmod/telemetry/dashboard/DashboardCommand.java` | Dashboard server open/start/stop/status. |
| `/devmod dungeon` | `src/main/java/com/devmod/telemetry/dungeon/DungeonCommand.java` | Dungeon run debug controls. |
| `/mailbox` | `src/main/java/com/devmod/mailbox/admin/MailboxCommands.java` | Mailbox admin ops. |
| `/news` | `src/main/java/com/devmod/mailbox/admin/MailboxCommands.java` | News CRUD + publish. |

---

## 3. Keybinds

Source of truth: `src/main/java/com/devmod/client/input/KeyInputHandler.java`.

Default-bound keys:
- `G` → Radial menu (`OPEN_RADIAL_MENU_KEY`)
- `M` → Mailbox (`OPEN_MAILBOX_KEY`)
- `T` → Tester tasks (`OPEN_TESTER_TASKS_KEY`)

All other key mappings are registered but **unbound by default** (GLFW `KEY_UNKNOWN`). This includes settings, editor, dashboard, QA/testing, quest actions, overlays, and ability keys.

---

## 4. UI Screens (Primary Entry Screens)

| Screen | File | Trigger |
|---|---|---|
| Radial menu | `src/main/java/com/devmod/client/ui/radial/RadialMenuScreen.java` | Keybind (G) + actions. |
| Item editor | `src/main/java/com/devmod/client/ui/editor/ItemEditorScreen.java` | Action/keybind (unbound by default). |
| Settings | `src/main/java/com/devmod/client/ui/unified/UnifiedSettingsScreen.java` | Action/keybind (unbound by default). |
| Telemetry dashboard | `src/main/java/com/devmod/client/ui/screens/TelemetryDashboardScreen.java` | Action/keybind (unbound by default). |
| Testing hub | `src/main/java/com/devmod/client/ui/hub/TestingHub.java` | `/devtest qa` or action. |
| QA screen | `src/main/java/com/devmod/client/testing/QATestingScreen.java` | Action/keybind (unbound by default). |
| Endurance quest UI | `src/main/java/com/devmod/client/endurance/EnduranceQuestScreen.java` | Quest flow. |
| Perk selection | `src/main/java/com/devmod/client/endurance/PerkSelectionScreen.java` | Quest flow. |
| Kit selection | `src/main/java/com/devmod/client/endurance/KitSelectionScreen.java` | Quest flow. |
| Wave directives | `src/main/java/com/devmod/client/endurance/WaveDirectiveScreen.java` | Quest flow. |
| Endurance shop | `src/main/java/com/devmod/client/endurance/EnduranceShopScreen.java` | Quest flow. |
| Party screen | `src/main/java/com/devmod/client/party/PartyScreen.java` | Action/keybind (unbound by default). |
| Quest editor | `src/main/java/com/devmod/client/quest/QuestEditorScreen.java` | Action/keybind (unbound by default). |
| Mailbox | `src/main/java/com/devmod/mailbox/client/screen/MailboxScreen.java` | Keybind (M) + actions. |
| Mail compose | `src/main/java/com/devmod/mailbox/client/screen/MailboxComposeScreen.java` | Mailbox flow. |
| News | `src/main/java/com/devmod/mailbox/client/screen/NewsScreen.java` | Mailbox flow. |
| Tester tasks | `src/main/java/com/devmod/mailbox/client/screen/TesterTaskScreen.java` | Keybind (T) + actions. |
| Quick test wizard | `src/main/java/com/devmod/client/ui/wizard/QuickTestWizard.java` | Action/command. |
| Arena test wizard | `src/main/java/com/devmod/client/arena/ui/ArenaTestWizard.java` | Action/command. |

---

## 5. Event Bus Entrypoints (Selected)

Core server-side event handlers:
- `src/main/java/com/devmod/telemetry/TelemetryEvents.java` (server lifecycle, tick, telemetry hooks)
- `src/main/java/com/devmod/arena/ArenaCommandEvents.java` (server lifecycle, tick, command bootstrap)
- `src/main/java/com/devmod/runtime/InstanceEventHandler.java` (server lifecycle, tick, player events)
- `src/main/java/com/devmod/endurance/EnduranceEventHandler.java` (quest lifecycle + ticks)
- `src/main/java/com/devmod/debug/DebugEvents.java` (debug tick + player logout)
- `src/main/java/com/devmod/abilities/AbilityEventHandler.java` (ability tick/cooldowns)
- `src/main/java/com/devmod/combat/DamageHandler.java` (damage hooks)

Client-side event handlers include overlays/render hooks in `src/main/java/com/devmod/client/` and mailbox overlay notifiers in `src/main/java/com/devmod/mailbox/client/overlay/`.

---

## 6. Network Payload Entrypoints

Source of truth:
- `src/main/java/com/devmod/network/ChannelId.java` (IDs + direction)
- `src/main/java/com/devmod/network/NetworkHandler.java` (payload registration)
- `src/main/java/com/devmod/debug/DebugNetworkHandler.java` (debug payloads)
- `src/main/java/com/devmod/network/PacketValidator.java` (validation)

`NetworkHandler` registers payloads via `RegisterPayloadHandlersEvent` and routes to domain handlers (arena/endurance/party/config/mailbox/etc.).
