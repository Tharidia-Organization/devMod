# Radial Census (DevMod)

Obiettivo: inventario completo delle azioni/funzionalita' accessibili e dei loro trigger attuali, con proposta di collocazione nel Radial.

Legenda priorita': P0 = core workflow/testing/comandi critici, P1 = debug/quality of life, P2 = nicchie/legacy.

## Tabella Feature → Trigger → Target

| FeatureId | Descrizione | Trigger attuale (Keybind/Command/UI/Event) | Classe/Metodo | Radial? | Priorita' | Proposta collocazione radial |
|---|---|---|---|---|---|---|
| devmod.ui.radial.open | Apri Radial Menu (gateway principale) | Keybind `OPEN_RADIAL_MENU_KEY` (G) | `RenderEvents.handleKeyBindings` | S | P0 | Root / Home |
| devmod.ui.settings.open | Apri Unified Settings | Keybind K; UI (radial item) | `RenderEvents.handleKeyBindings`, `UnifiedSettingsScreen` | S | P0 | Root / Config / Settings |
| devmod.ui.item_editor.open_auto | Apri Item Editor (auto-detect) | Keybind M | `RenderEvents.handleKeyBindings` | S | P0 | Root / Tools / Item Editor / Auto |
| devmod.ui.item_editor.open_weapon | Apri Item Editor (Weapon tab) | Keybind Shift+M; UI | `RenderEvents.handleKeyBindings`, `ItemEditorScreen` | S | P0 | Root / Tools / Item Editor / Weapon |
| devmod.ui.item_editor.open_armor | Apri Item Editor (Armor tab) | Keybind Ctrl+M; UI | `RenderEvents.handleKeyBindings`, `ItemEditorScreen` | S | P0 | Root / Tools / Item Editor / Armor |
| devmod.ui.item_editor.open_general | Apri Item Editor (General tab) | UI (radial) | `RadialMenuRegistry` | S | P1 | Root / Tools / Item Editor / General |
| devmod.ui.item_editor.open_recipe | Apri Item Editor (Recipe tab) | UI (radial), UI (CraftingInfoPanel) | `RadialMenuRegistry`, `CraftingInfoPanel` | S | P1 | Root / Tools / Item Editor / Recipes |
| devmod.ui.item_editor.open_food | Apri Item Editor (Food tab) | UI (radial) | `RadialMenuRegistry` | S | P1 | Root / Tools / Item Editor / Food |
| devmod.ui.item_editor.open_fuel | Apri Item Editor (Fuel tab) | UI (radial) | `RadialMenuRegistry` | S | P1 | Root / Tools / Item Editor / Fuel |
| devmod.ui.item_editor.open_usable | Apri Item Editor (Usable tab) | UI (radial) | `RadialMenuRegistry` | S | P1 | Root / Tools / Item Editor / Usable |
| devmod.ui.telemetry_dashboard.open | Apri Telemetry Dashboard (in-game) | Keybind J; UI (TelemetryPage) | `RenderEvents.handleKeyBindings`, `TelemetryPage` | S | P0 | Root / Telemetry / Dashboard |
| devmod.ui.mob_config.open | Apri Mob Config (targeted mob) | Keybind X; Event (viewer item) | `RenderEvents.handleKeyBindings`, `InteractionEvents.onEntityInteract` | S | P0 | Root / Tools / Mob Config |
| devmod.ui.mob_equipment.open | Apri Mob Equipment (da MobConfig) | UI button; UI (radial) | `MobConfigScreen`, `DevModClientActions.openMobEquipment` | S | P1 | Root / Tools / Mob Config / Equipment |
| devmod.ui.party.open | Apri Party Screen | Keybind; UI (radial) | `RenderEvents.handleKeyBindings`, `DevModClientActions.registerUiActions` | S | P0 | Root / Play / Party |
| devmod.ui.testing_hub.open | Apri Testing Hub | Keybind N/F7; command /devtest qa | `RenderEvents.handleKeyBindings`, `TestHarnessCommands` | S | P0 | Root / Testing / Hub |
| devmod.ui.qa_testing.open | Apri QA Testing Screen | Keybind N; UI (radial) | `RenderEvents.handleKeyBindings`, `DevModClientActions.registerUiActions` | S | P2 | Root / Testing / QA |
| devmod.ui.badge_tests.open | Apri Badge Test Screen | UI (radial) | `RadialMenuRegistry` | S | P2 | Root / Testing / Badge Tests |
| devmod.ui.voxellab.open | Apri VoxelLab Screen | UI (radial) | `DevModClientActions.registerUiActions` | S | P2 | Root / Testing / VoxelLab |
| devmod.ui.voxellab_ui_tests.open | Apri VoxelLab UI Test Screen | UI (radial) | `RadialMenuRegistry` | S | P2 | Root / Testing / VoxelLab UI |
| devmod.ui.quick_test_wizard.open | Apri Quick Test Wizard (DevMod) | UI (radial) | `RadialMenuRegistry` | S | P1 | Root / Testing / Quick Test Wizard |
| devmod.arena.quick_test_wizard.open | Apri Quick Test Wizard (Arena) | UI (radial) | `DevModClientActions.openArenaQuickTestWizard` | S | P1 | Root / Arena / Quick Test Wizard |
| devmod.ui.room_bounds_editor.open | Apri Room Bounds Editor | Keybind Shift+R; UI | `RenderEvents.handleKeyBindings` | S | P1 | Root / Debug / Spatial / Room Bounds Editor |
| devmod.ui.welcome.open | Welcome Screen (first run) | Event (ClientModEvents); UI (radial) | `ClientModEvents`, `DevModClientActions` | S | P2 | Root / Help / Welcome |
| devmod.ui.stamina_editor.open | Stamina System Editor | UI (radial) | `DevModClientActions.registerUiActions` | S | P2 | Root / Combat / Stamina Editor |
| devmod.ui.quest_editor.open | Apri Quest Editor | Keybind [; UI (radial) | `RenderEvents.handleKeyBindings`, `RadialMenuRegistry` | S | P0 | Root / Endurance / Quest Editor |
| devmod.ui.endurance_screen.open | Apri Endurance Quest Screen | Keybind F10; UI (radial) | `RenderEvents.handleKeyBindings`, `RadialMenuRegistry` | S | P0 | Root / Endurance / Start |
| devmod.ui.endurance_shop.open | Apri Endurance Shop | UI (EnduranceQuestScreen); UI (radial) | `EnduranceQuestScreen`, `DevModClientActions` | S | P1 | Root / Endurance / Shop |
| devmod.ui.perk_selection.open | Apri Perk Selection | Event (network); UI (radial) | `EnduranceNetworkHandler`, `DevModClientActions` | S | P1 | Root / Endurance / Perks |
| devmod.ui.quest_death.open | Apri Quest Death Screen | Event (network); UI (radial) | `EnduranceNetworkHandler`, `DevModClientActions` | S | P0 | Root / Endurance / Continue |
| devmod.ui.quest_completion.open | Apri Quest Completion Screen | Event (network); UI (radial) | `EnduranceNetworkHandler`, `DevModClientActions` | S | P1 | Root / Endurance / Results |
| devmod.ui.wave_checkpoint.open | Apri Wave Checkpoint Screen | Event (EnduranceQuestOverlay); UI (radial) | `EnduranceQuestOverlay`, `DevModClientActions` | S | P1 | Root / Endurance / Checkpoints |
| devmod.ui.party_invite_popup.open | Apri Party Invite Popup | Event (network); UI (radial) | `PartyNetworkHandler`, `DevModClientActions` | S | P1 | Root / Play / Party / Invites |
| devmod.telemetry.dashboard.open | Apri Telemetry Dashboard (browser) | Command `/devmod dashboard` | `DashboardCommand` | S | P1 | Root / Telemetry / Dashboard / Open Browser |
| devmod.telemetry.dashboard.start | Avvia dashboard server | Command `/devmod dashboard start` | `DashboardCommand` | S | P1 | Root / Telemetry / Dashboard / Start |
| devmod.telemetry.dashboard.stop | Ferma dashboard server | Command `/devmod dashboard stop` | `DashboardCommand` | S | P1 | Root / Telemetry / Dashboard / Stop |
| devmod.telemetry.dashboard.status | Stato dashboard/duckdb | Command `/devmod dashboard status` | `DashboardCommand` | S | P1 | Root / Telemetry / Dashboard / Status |

| devmod.debug.overlay.toggle | Toggle Debug Overlay | Keybind O; UI (radial) | `RenderEvents.handleKeyBindings`, `RadialMenuRegistry` | S | P0 | Root / Debug / Overlays / Debug Overlay |
| devmod.debug.body_parts.toggle | Toggle Body Part Boxes | Keybind Shift+O | `RenderEvents.handleKeyBindings` | S | P0 | Root / Debug / Overlays / Body Parts |
| devmod.debug.overlays.enable_all | Enable all debug overlays | UI (DebugOverlaysPage, radial) | `DebugOverlaysPage`, `DevModClientActions` | S | P1 | Root / Debug / Overlays / Enable All |
| devmod.debug.overlays.disable_all | Disable all debug overlays | UI (DebugOverlaysPage, radial) | `DebugOverlaysPage`, `DevModClientActions` | S | P1 | Root / Debug / Overlays / Disable All |
| devmod.debug.native.entity_pathing.toggle | Toggle native entity pathing | UI (DebugOverlaysPage, radial) | `DebugOverlaysPage`, `DevModClientActions` | S | P1 | Root / Debug / Native / Entity Pathing |
| devmod.debug.native.entity_goals.toggle | Toggle native entity goals | UI (DebugOverlaysPage, radial) | `DebugOverlaysPage`, `DevModClientActions` | S | P1 | Root / Debug / Native / Entity Goals |
| devmod.debug.native.entity_brains.toggle | Toggle native entity brains | UI (DebugOverlaysPage, radial) | `DebugOverlaysPage`, `DevModClientActions` | S | P1 | Root / Debug / Native / Entity Brains |
| devmod.debug.native.poi.toggle | Toggle native POI | UI (DebugOverlaysPage, radial) | `DebugOverlaysPage`, `DevModClientActions` | S | P1 | Root / Debug / Native / POI |
| devmod.debug.native.raids.toggle | Toggle native raids | UI (DebugOverlaysPage, radial) | `DebugOverlaysPage`, `DevModClientActions` | S | P1 | Root / Debug / Native / Raids |
| devmod.debug.native.bees.toggle | Toggle native bees | UI (DebugOverlaysPage, radial) | `DebugOverlaysPage`, `DevModClientActions` | S | P1 | Root / Debug / Native / Bees |
| devmod.debug.native.game_events.toggle | Toggle native game events | UI (DebugOverlaysPage, radial) | `DebugOverlaysPage`, `DevModClientActions` | S | P1 | Root / Debug / Native / Game Events |
| devmod.debug.native.structures.toggle | Toggle native structures | UI (DebugOverlaysPage, radial) | `DebugOverlaysPage`, `DevModClientActions` | S | P1 | Root / Debug / Native / Structures |
| devmod.debug.light_overlay.toggle | Toggle Light Level Overlay | Keybind L; UI | `RenderEvents.handleKeyBindings`, `RadialMenuRegistry` | S | P0 | Root / Debug / Light |
| devmod.debug.heatmap.cycle | Cycle Heatmap | Keybind H; UI | `RenderEvents.handleKeyBindings`, `RadialMenuRegistry` | S | P0 | Root / Debug / Heatmaps / Cycle |
| devmod.debug.heatmap.toggle | Toggle Heatmaps | UI (radial, Testing Hub) | `RadialMenuRegistry`, `QuickToolsPanel` | S | P1 | Root / Debug / Heatmaps / Toggle |
| devmod.debug.heatmap.death.toggle | Toggle Death Heatmap | UI (Telemetry Dashboard, radial) | `TelemetryDashboardScreen`, `RadialMenuRegistry` | S | P1 | Root / Debug / Heatmaps / Types / Death |
| devmod.debug.heatmap.movement.toggle | Toggle Movement Heatmap | UI (Telemetry Dashboard, radial) | `TelemetryDashboardScreen`, `RadialMenuRegistry` | S | P1 | Root / Debug / Heatmaps / Types / Movement |
| devmod.debug.heatmap.camping.toggle | Toggle Camping Heatmap | UI (Telemetry Dashboard, radial) | `TelemetryDashboardScreen`, `RadialMenuRegistry` | S | P1 | Root / Debug / Heatmaps / Types / Camping |
| devmod.debug.heatmap.stuck.toggle | Toggle Stuck Heatmap | UI (Telemetry Dashboard, radial) | `TelemetryDashboardScreen`, `RadialMenuRegistry` | S | P1 | Root / Debug / Heatmaps / Types / Stuck |
| devmod.debug.heatmap.aggro_drop.toggle | Toggle Aggro Drop Heatmap | UI (Telemetry Dashboard, radial) | `TelemetryDashboardScreen`, `RadialMenuRegistry` | S | P1 | Root / Debug / Heatmaps / Types / Aggro Drop |
| devmod.debug.heatmap.kiting.toggle | Toggle Kiting Heatmap | UI (Telemetry Dashboard, radial) | `TelemetryDashboardScreen`, `RadialMenuRegistry` | S | P1 | Root / Debug / Heatmaps / Types / Kiting |
| devmod.debug.heatmap.light_spawnable.toggle | Toggle Light Spawnable Heatmap | UI (Visualizers), radial | `VisualizersPage`, `RadialMenuRegistry` | S | P1 | Root / Debug / Heatmaps / Types / Light Spawnable |
| devmod.debug.heatmap.light_dark.toggle | Toggle Light Dark Heatmap | UI (Visualizers), radial | `VisualizersPage`, `RadialMenuRegistry` | S | P1 | Root / Debug / Heatmaps / Types / Light Dark |
| devmod.debug.heatmap.clear_current | Clear Heatmap corrente | Keybind Ctrl+H | `RenderEvents.handleKeyBindings` | S | P1 | Root / Debug / Heatmaps / Clear Current |
| devmod.debug.heatmap.clear_all | Clear tutte le heatmap | Keybind Shift+H | `RenderEvents.handleKeyBindings` | S | P1 | Root / Debug / Heatmaps / Clear All |
| devmod.debug.room_bounds.toggle | Toggle Room Bounds | Keybind R; UI | `RenderEvents.handleKeyBindings`, `RadialMenuRegistry` | S | P0 | Root / Debug / Spatial / Room Bounds |
| devmod.debug.room_bounds.reload | Reload Room Bounds | Keybind Ctrl+R | `RenderEvents.handleKeyBindings` | S | P1 | Root / Debug / Spatial / Reload |
| devmod.debug.pathfinding.toggle | Toggle Pathfinding | Keybind P; UI | `RenderEvents.handleKeyBindings`, `RadialMenuRegistry` | S | P0 | Root / Debug / AI / Pathfinding |
| devmod.debug.los.toggle | Toggle Line of Sight | Keybind V; UI | `RenderEvents.handleKeyBindings`, `RadialMenuRegistry` | S | P0 | Root / Debug / AI / Line of Sight |
| devmod.debug.aggro_range.toggle | Toggle Aggro Range | UI (DebugOverlaysPage, radial) | `DebugOverlaysPage`, `DevModClientActions` | S | P1 | Root / Debug / AI / Aggro Range |
| devmod.debug.vertical_levels.toggle | Toggle Vertical Levels | Keybind Y; UI | `RenderEvents.handleKeyBindings`, `RadialMenuRegistry` | S | P1 | Root / Debug / Spatial / Vertical Levels |
| devmod.debug.safe_spots.toggle | Toggle Safe Spots | Keybind C; UI | `RenderEvents.handleKeyBindings`, `RadialMenuRegistry` | S | P1 | Root / Debug / Spatial / Safe Spots |
| devmod.debug.attribute_monitor.toggle | Toggle Attribute Monitor | Keybind U; UI | `RenderEvents.handleKeyBindings`, `RadialMenuRegistry` | S | P1 | Root / Debug / Perf / Attribute Monitor |
| devmod.debug.entity_density.toggle | Toggle Entity Density | Keybind F6; UI | `RenderEvents.handleKeyBindings`, `RadialMenuRegistry` | S | P1 | Root / Debug / Perf / Entity Density |
| devmod.debug.boss_phase.toggle | Toggle Boss Phase HUD | Keybind F?; UI | `RenderEvents.handleKeyBindings`, `RadialMenuRegistry` | S | P1 | Root / Debug / Combat / Boss Phase |
| devmod.debug.skill_efficacy.toggle | Toggle Skill Efficacy | Keybind F5; UI | `RenderEvents.handleKeyBindings`, `RadialMenuRegistry` | S | P1 | Root / Debug / Combat / Skill Efficacy |
| devmod.debug.spawnability.toggle | Toggle Spawnability Map | Keybind F4; UI | `RenderEvents.handleKeyBindings`, `RadialMenuRegistry` | S | P1 | Root / Debug / Spatial / Spawnability |
| devmod.debug.fps_tracker.toggle | Toggle FPS Tracker | Keybind F8; UI | `RenderEvents.handleKeyBindings`, `RadialMenuRegistry` | S | P1 | Root / Debug / Perf / FPS Tracker |
| devmod.debug.profiler.toggle | Toggle Profiler | Keybind F9; UI | `RenderEvents.handleKeyBindings`, `RadialMenuRegistry` | S | P1 | Root / Debug / Perf / Profiler |
| devmod.debug.chunk_perf.toggle | Toggle Chunk Performance | Keybind F2 | `RenderEvents.handleKeyBindings` | S | P1 | Root / Debug / Perf / Chunk Perf |
| devmod.debug.economy.toggle | Toggle Economy Overlay | Keybind F3 | `RenderEvents.handleKeyBindings` | S | P1 | Root / Debug / Economy |
| devmod.debug.economy.view_cycle | Cycle Economy View | Keybind Shift+F3 | `RenderEvents.handleKeyBindings` | S | P2 | Root / Debug / Economy / View |
| devmod.debug.economy.sort_cycle | Cycle Economy Sort | Keybind Ctrl+F3 | `RenderEvents.handleKeyBindings` | S | P2 | Root / Debug / Economy / Sort |
| devmod.hud.impact.dismiss | Dismiss Impact HUD | Keybind Backspace | `RenderEvents.handleKeyBindings` | S | P2 | Root / Debug / HUD / Dismiss Impact |
| devmod.hud.quick_help.toggle | Toggle Quick Help Overlay | Keybind F1 | `RenderEvents.handleKeyBindings`, `QuickHelpOverlay` | S | P1 | Root / Help / Keybinds |
| devmod.hud.quest.toggle | Toggle Quest HUD | Keybind `TOGGLE_QUEST_HUD_KEY` | `RenderEvents.handleKeyBindings` | S | P0 | Root / Endurance / HUD |
| devmod.quest.task.complete | Complete current quest task | Keybind `QUEST_COMPLETE_TASK_KEY` | `RenderEvents.handleKeyBindings` | S | P0 | Root / Endurance / Task / Complete |
| devmod.endurance.hud.toggle | Toggle Endurance HUD | Keybind Shift+F10 | `RenderEvents.handleKeyBindings` | S | P0 | Root / Endurance / HUD |
| devmod.endurance.hud.details_toggle | Toggle Endurance HUD details | Keybind Ctrl+F10 | `RenderEvents.handleKeyBindings` | S | P1 | Root / Endurance / HUD / Details |
| devmod.endurance.quest.start | Start Endurance Quest | UI (EnduranceQuestScreen) | `EnduranceQuestScreen.startSelectedQuest` | S | P0 | Root / Endurance / Start |
| devmod.endurance.quest.continue | Continue after death | Keybind F11 | `RenderEvents.handleKeyBindings` | S | P0 | Root / Endurance / Continue |
| devmod.endurance.quest.exit | Exit/Give Up Quest (confirm) | Keybind F12 | `RenderEvents.handleKeyBindings` | S | P0 | Root / Endurance / Exit |
| devmod.ability.dash | Dash ability | Keybind Left Alt | `RenderEvents.handleKeyBindings` | S | P0 | Root / Combat / Abilities / Dash |
| devmod.ability.dodge | Dodge ability | Keybind Left Ctrl | `RenderEvents.handleKeyBindings` | S | P0 | Root / Combat / Abilities / Dodge |
| devmod.debug.screen_shake.test | Trigger screen shake test | Keybind 0 | `RenderEvents.handleKeyBindings` | S | P2 | Root / Debug / VFX / Screen Shake |
| devmod.ui.onboarding.start | Start onboarding tutorial | UI (WelcomeScreen); UI (radial) | `WelcomeScreen`, `DevModClientActions` | S | P2 | Root / Help / Onboarding / Start |
| devmod.ui.onboarding.skip | Skip onboarding tutorial | Event (ESC in overlay); UI (radial) | `RenderEvents.handleKeyBindings`, `DevModClientActions` | S | P2 | Root / Help / Onboarding / Skip |
| devmod.arena.hud.toggle | Toggle Arena Debug HUD | Keybind Shift+F7 (Testing Hub key + modifier); Command /arena hud toggle | `RenderEvents.handleKeyBindings`, `ArenaCommands.toggleDebugHud` | S | P1 | Root / Arena / HUD / Toggle |

| devmod.arena.create | /arena create <template> | Command | `ArenaCommands.createArena` | S | P0 | Root / Arena / Create |
| devmod.arena.template.list | /arena template list | Command | `ArenaCommands.listTemplates` | S | P0 | Root / Arena / Templates / List |
| devmod.arena.template.info | /arena template info <id> | Command | `ArenaCommands.templateInfo` | S | P0 | Root / Arena / Templates / Info |
| devmod.arena.template.reload | /arena template reload | Command | `ArenaCommands.reloadTemplates` | S | P0 | Root / Arena / Templates / Reload |
| devmod.arena.validate | /arena validate <id> | Command | `ArenaCommands.validateTemplate` | S | P0 | Root / Arena / Templates / Validate |
| devmod.arena.metrics | /arena metrics <id> | Command | `ArenaCommands.templateMetrics` | S | P1 | Root / Arena / Templates / Metrics |
| devmod.arena.autosmoke.run | /arena autosmoke run | Command | `ArenaCommands.runAutosmoke` | S | P0 | Root / Arena / Autosmoke / Run |
| devmod.arena.autosmoke.status | /arena autosmoke status | Command | `ArenaCommands.autosmokeStatus` | S | P1 | Root / Arena / Autosmoke / Status |
| devmod.arena.autosmoke.schedule_status | /arena autosmoke schedule | Command | `ArenaCommands.autosmokeScheduleStatus` | S | P1 | Root / Arena / Autosmoke / Schedule |
| devmod.arena.status | /arena status | Command | `ArenaCommands.systemStatus` | S | P1 | Root / Arena / Status |
| devmod.arena.force | /arena force <id> [mins] | Command | `ArenaCommands.forceTemplate` | S | P0 | Root / Arena / Force Template / Set |
| devmod.arena.force.clear | /arena force clear | Command | `ArenaCommands.forceClear` | S | P0 | Root / Arena / Force Template / Clear |
| devmod.arena.force.status | /arena force status | Command | `ArenaCommands.forceStatus` | S | P1 | Root / Arena / Force Template / Status |
| devmod.arena.hud.toggle | /arena hud toggle | Command | `ArenaCommands.toggleDebugHud` | S | P1 | Root / Arena / HUD / Toggle |
| devmod.arena.hud.status | /arena hud status | Command | `ArenaCommands.hudStatus` | S | P1 | Root / Arena / HUD / Status |
| devmod.arena.help | /arena help | Command | `ArenaCommands.sendHelp` | S | P2 | Root / Arena / Help |

| devmod.debug.command.help | /devdebug | Command | `DebugCommand.showHelp` | S | P2 | Root / Debug / Features / Help |
| devmod.debug.command.toggle | /devdebug <feature> | Command | `DebugCommand.toggleFeature` | S | P1 | Root / Debug / Features |
| devmod.debug.command.list | /devdebug list | Command | `DebugCommand.listFeatures` | S | P1 | Root / Debug / Features / List |
| devmod.debug.command.off | /devdebug off | Command | `DebugCommand.disableAll` | S | P1 | Root / Debug / Features / Disable All |

| devmod.telemetry.reload | /devmod telemetry reload | Command | `TelemetryReloadCommand.reload` | S | P0 | Root / Telemetry / Reload |
| devmod.telemetry.dump.weapons | /devmod telemetry dump weapons | Command | `TelemetryReloadCommand.dumpWeapons` | S | P1 | Root / Telemetry / Dump / Weapons |
| devmod.telemetry.dump.rooms | /devmod telemetry dump rooms | Command | `TelemetryReloadCommand.dumpRooms` | S | P1 | Root / Telemetry / Dump / Rooms |
| devmod.telemetry.dump.fights | /devmod telemetry dump fights | Command | `TelemetryReloadCommand.dumpFights` | S | P1 | Root / Telemetry / Dump / Fights |
| devmod.telemetry.dump.minions | /devmod telemetry dump minions | Command | `TelemetryReloadCommand.dumpMinions` | S | P1 | Root / Telemetry / Dump / Minions |
| devmod.telemetry.export.heatmaps | /devmod telemetry export heatmaps | Command | `TelemetryReloadCommand.exportHeatmaps` | S | P1 | Root / Telemetry / Export / Heatmaps |
| devmod.telemetry.export.png | /devmod telemetry export png | Command | `TelemetryReloadCommand.exportHeatmapsPng` | S | P1 | Root / Telemetry / Export / PNG |
| devmod.telemetry.export.csv | /devmod telemetry export csv | Command | `TelemetryReloadCommand.exportCsv` | S | P1 | Root / Telemetry / Export / CSV |
| devmod.telemetry.export.json | /devmod telemetry export json | Command | `TelemetryReloadCommand.exportJsonReport` | S | P1 | Root / Telemetry / Export / JSON |
| devmod.telemetry.export.all | /devmod telemetry export all | Command | `TelemetryReloadCommand.exportAll` | S | P1 | Root / Telemetry / Export / All |
| devmod.telemetry.export.heatmap.death | Export Death Heatmap (local) | UI (Telemetry Dashboard), radial | `TelemetryDashboardScreen`, `DevModClientActions` | S | P1 | Root / Telemetry / Export / Heatmaps / Death |
| devmod.telemetry.export.heatmap.movement | Export Movement Heatmap (local) | UI (Telemetry Dashboard), radial | `TelemetryDashboardScreen`, `DevModClientActions` | S | P1 | Root / Telemetry / Export / Heatmaps / Movement |
| devmod.telemetry.export.heatmap.camping | Export Camping Heatmap (local) | UI (Telemetry Dashboard), radial | `TelemetryDashboardScreen`, `DevModClientActions` | S | P1 | Root / Telemetry / Export / Heatmaps / Camping |
| devmod.telemetry.export.heatmap.stuck | Export Stuck Heatmap (local) | UI (Telemetry Dashboard), radial | `TelemetryDashboardScreen`, `DevModClientActions` | S | P1 | Root / Telemetry / Export / Heatmaps / Stuck |
| devmod.telemetry.export.heatmap.aggro_drop | Export Aggro Drop Heatmap (local) | UI (Telemetry Dashboard), radial | `TelemetryDashboardScreen`, `DevModClientActions` | S | P1 | Root / Telemetry / Export / Heatmaps / Aggro Drop |
| devmod.telemetry.export.heatmap.kiting | Export Kiting Heatmap (local) | UI (Telemetry Dashboard), radial | `TelemetryDashboardScreen`, `DevModClientActions` | S | P1 | Root / Telemetry / Export / Heatmaps / Kiting |
| devmod.telemetry.export.heatmap.choke_points | Export Choke Point Heatmap (local) | UI (Telemetry Dashboard), radial | `TelemetryDashboardScreen`, `DevModClientActions` | S | P1 | Root / Telemetry / Export / Heatmaps / Choke Points |
| devmod.telemetry.export.heatmap.parkour_falls | Export Parkour Fall Heatmap (local) | UI (Telemetry Dashboard), radial | `TelemetryDashboardScreen`, `DevModClientActions` | S | P1 | Root / Telemetry / Export / Heatmaps / Parkour Falls |
| devmod.telemetry.export.damage_stats | Export Damage Stats (local) | UI (Telemetry Dashboard), radial | `TelemetryDashboardScreen`, `DevModClientActions` | S | P1 | Root / Telemetry / Export / Damage Stats |
| devmod.telemetry.scan.light.all | /devmod telemetry scan light | Command | `TelemetryReloadCommand.scanLightAll` | S | P1 | Root / Telemetry / Scan / Light |
| devmod.telemetry.scan.light.room | /devmod telemetry scan light <roomId> | Command | `TelemetryReloadCommand.scanLightRoom` | S | P1 | Root / Telemetry / Scan / Light / Room |
| devmod.telemetry.spawnability | /devmod telemetry spawnability <roomId> | Command | `TelemetryReloadCommand.checkSpawnability` | S | P1 | Root / Telemetry / Scan / Spawnability |
| devmod.telemetry.desirelines.dump | /devmod telemetry desirelines | Command | `TelemetryReloadCommand.dumpDesireLines` | S | P2 | Root / Telemetry / Spatial / Desire Lines |
| devmod.telemetry.desirelines.analyze | /devmod telemetry desirelines <roomId> | Command | `TelemetryReloadCommand.analyzeDesireLines` | S | P2 | Root / Telemetry / Spatial / Desire Lines / Room |
| devmod.telemetry.dungeons.dump | /devmod telemetry dungeons | Command | `TelemetryReloadCommand.dumpDungeonRuns` | S | P2 | Root / Telemetry / Dungeons |
| devmod.telemetry.dungeons.stats | /devmod telemetry dungeons <id> | Command | `TelemetryReloadCommand.getDungeonStats` | S | P2 | Root / Telemetry / Dungeons / Stats |
| devmod.telemetry.backtracking.dump | /devmod telemetry backtracking | Command | `TelemetryReloadCommand.dumpBacktracking` | S | P2 | Root / Telemetry / Spatial / Backtracking |
| devmod.telemetry.backtracking.confusing | /devmod telemetry backtracking confusing | Command | `TelemetryReloadCommand.getMostConfusingRooms` | S | P2 | Root / Telemetry / Spatial / Backtracking / Confusing |

| devmod.telemetry.dungeon.start | /devmod dungeon start <id> | Command | `DungeonCommand.start` | S | P1 | Root / Telemetry / Dungeon / Start |
| devmod.telemetry.dungeon.end | /devmod dungeon end <outcome> [kills] [deaths] [rewards] | Command | `DungeonCommand.end` | S | P1 | Root / Telemetry / Dungeon / End |
| devmod.telemetry.dungeon.status | /devmod dungeon status | Command | `DungeonCommand.status` | S | P1 | Root / Telemetry / Dungeon / Status |

| devmod.testing.hud.on | /devtest hud on | Command | `TestHarnessCommands` | S | P1 | Root / Testing / HUD / On |
| devmod.testing.hud.off | /devtest hud off | Command | `TestHarnessCommands` | S | P1 | Root / Testing / HUD / Off |
| devmod.testing.hud.toggle | /devtest hud toggle | Command | `TestHarnessCommands` | S | P1 | Root / Testing / HUD / Toggle |
| devmod.testing.hud.export | /devtest hud export | Command | `TestHarnessCommands` | S | P2 | Root / Testing / HUD / Export Preset |
| devmod.testing.hud.import | /devtest hud import | Command | `TestHarnessCommands` | S | P2 | Root / Testing / HUD / Import Preset |
| devmod.testing.panel.on | /devtest panel on | Command | `TestHarnessCommands` | S | P2 | Root / Testing / Panels / On |
| devmod.testing.panel.off | /devtest panel off | Command | `TestHarnessCommands` | S | P2 | Root / Testing / Panels / Off |
| devmod.testing.panel.toggle | /devtest panel toggle | Command | `TestHarnessCommands` | S | P2 | Root / Testing / Panels / Toggle |
| devmod.testing.debug.on | /devtest debug on | Command | `TestHarnessCommands` | S | P2 | Root / Testing / Debug / On |
| devmod.testing.debug.off | /devtest debug off | Command | `TestHarnessCommands` | S | P2 | Root / Testing / Debug / Off |
| devmod.testing.debug.toggle | /devtest debug toggle | Command | `TestHarnessCommands` | S | P2 | Root / Testing / Debug / Toggle |
| devmod.testing.debugbox | /devtest debugbox <size> | Command | `TestHarnessCommands` | S | P2 | Root / Testing / Debug / Debug Box |
| devmod.testing.debugclear | /devtest debugclear | Command | `TestHarnessCommands` | S | P2 | Root / Testing / Debug / Clear Shapes |
| devmod.testing.panelclear | /devtest panelclear | Command | `TestHarnessCommands` | S | P2 | Root / Testing / Panels / Clear |
| devmod.testing.info | /devtest info | Command | `TestHarnessCommands` | S | P2 | Root / Testing / Info |
| devmod.testing.qa.open | /devtest qa | Command | `TestHarnessCommands` | S | P2 | Root / Testing / Hub |
| devmod.testing.bodypart.info | /devtest bodypart <part> | Command | `TestHarnessCommands` | S | P2 | Root / Testing / Combat / Body Part |
| devmod.testing.endurance.stats | /devtest endurance stats | Command | `TestHarnessCommands` | S | P2 | Root / Endurance / Stats |
| devmod.testing.endurance.perks | /devtest endurance perks | Command | `TestHarnessCommands` | S | P2 | Root / Endurance / Perks |
| devmod.testing.endurance.smoke | /devtest endurance smoke | Command | `TestHarnessCommands` | S | P2 | Root / Endurance / Smoke |
| devmod.testing.endurance.export.table | /devtest endurance export table | Command | `TestHarnessCommands` | S | P2 | Root / Endurance / Export |
| devmod.testing.endurance.export.all | /devtest endurance export all | Command | `TestHarnessCommands` | S | P2 | Root / Endurance / Export / All |
| devmod.testing.endurance.autosmoke | /devtest endurance autosmoke | Command | `TestHarnessCommands` | S | P2 | Root / Endurance / Autosmoke |

## Orphan features (senza trigger diretto)
- Nessuno noto: le UI principali ora sono mappate ad azioni radial.

Nota: alcuni screen sono system-driven (network/event) e non sono azioni utente dirette; verranno comunque esposti come azioni "view" dove ha senso (es. Endurance results/Perk info).
