package com.devmod.actions;

public final class ActionIds {
    private ActionIds() {}

    // UI / Screens
    public static final String UI_RADIAL_OPEN = "devmod.ui.radial.open";
    public static final String UI_SETTINGS_OPEN = "devmod.ui.settings.open";
    public static final String UI_RADIAL_SETTINGS_OPEN = "devmod.ui.radial_settings.open";
    public static final String UI_ITEM_EDITOR_OPEN_AUTO = "devmod.ui.item_editor.open_auto";
    public static final String UI_ITEM_EDITOR_OPEN_WEAPON = "devmod.ui.item_editor.open_weapon";
    public static final String UI_ITEM_EDITOR_OPEN_ARMOR = "devmod.ui.item_editor.open_armor";
    public static final String UI_ITEM_EDITOR_OPEN_SHIELD = "devmod.ui.item_editor.open_shield";
    public static final String UI_ITEM_EDITOR_OPEN_GENERAL = "devmod.ui.item_editor.open_general";
    public static final String UI_ITEM_EDITOR_OPEN_RECIPE = "devmod.ui.item_editor.open_recipe";
    public static final String UI_ITEM_EDITOR_OPEN_FOOD = "devmod.ui.item_editor.open_food";
    public static final String UI_ITEM_EDITOR_OPEN_FUEL = "devmod.ui.item_editor.open_fuel";
    public static final String UI_ITEM_EDITOR_OPEN_USABLE = "devmod.ui.item_editor.open_usable";
    public static final String UI_TELEMETRY_DASHBOARD_OPEN = "devmod.ui.telemetry_dashboard.open";
    public static final String UI_MOB_CONFIG_OPEN = "devmod.ui.mob_config.open";
    public static final String UI_MOB_EQUIPMENT_OPEN = "devmod.ui.mob_equipment.open";
    public static final String UI_ROOM_BOUNDS_EDITOR_OPEN = "devmod.ui.room_bounds_editor.open";
    public static final String UI_ROOM_BOUNDS_POINT_A = "devmod.ui.room_bounds_editor.point_a";
    public static final String UI_ROOM_BOUNDS_POINT_B = "devmod.ui.room_bounds_editor.point_b";
    public static final String UI_ROOM_BOUNDS_SAVE = "devmod.ui.room_bounds_editor.save";
    public static final String UI_ROOM_BOUNDS_DELETE_LAST = "devmod.ui.room_bounds_editor.delete_last";
    public static final String UI_TESTING_HUB_OPEN = "devmod.ui.testing_hub.open";
    public static final String UI_QUICK_TEST_WIZARD_OPEN = "devmod.ui.quick_test_wizard.open";
    public static final String UI_BADGE_TESTS_OPEN = "devmod.ui.badge_tests.open";
    public static final String UI_VOXELLAB_UI_TESTS_OPEN = "devmod.ui.voxellab_ui_tests.open";
    public static final String UI_QA_TESTING_OPEN = "devmod.ui.qa_testing.open";
    public static final String UI_KEYBINDS_OPEN = "devmod.ui.keybinds.open";
    public static final String UI_PARTY_OPEN = "devmod.ui.party.open";
    public static final String UI_PARTY_INVITE_POPUP_OPEN = "devmod.ui.party_invite_popup.open";
    public static final String UI_NOTIFICATION_CENTER_OPEN = "devmod.ui.notification_center.open";
    public static final String UI_MAILBOX_OPEN = "devmod.ui.mailbox.open";
    public static final String UI_TESTER_TASKS_OPEN = "devmod.ui.tester_tasks.open";
    public static final String UI_QUEST_EDITOR_OPEN = "devmod.ui.quest_editor.open";
    public static final String UI_ENDURANCE_EDITOR_OPEN = "devmod.ui.endurance_editor.open";
    public static final String UI_ENDURANCE_SCREEN_OPEN = "devmod.ui.endurance_screen.open";
    public static final String UI_ENDURANCE_SHOP_OPEN = "devmod.ui.endurance_shop.open";
    public static final String UI_VOXELLAB_OPEN = "devmod.ui.voxellab.open";
    public static final String UI_STAMINA_EDITOR_OPEN = "devmod.ui.stamina_editor.open";
    public static final String UI_QUEST_DEATH_OPEN = "devmod.ui.quest_death.open";
    public static final String UI_PERK_SELECTION_OPEN = "devmod.ui.perk_selection.open";
    public static final String UI_QUEST_COMPLETION_OPEN = "devmod.ui.quest_completion.open";
    public static final String UI_WAVE_CHECKPOINT_OPEN = "devmod.ui.wave_checkpoint.open";
    public static final String UI_WELCOME_OPEN = "devmod.ui.welcome.open";
    public static final String UI_ONBOARDING_START = "devmod.ui.onboarding.start";
    public static final String UI_ONBOARDING_SKIP = "devmod.ui.onboarding.skip";
    public static final String UI_SEASON_PASS_OPEN = "devmod.ui.season_pass.open";

    // Debug / HUD toggles
    public static final String DEBUG_OVERLAY_TOGGLE = "devmod.debug.overlay.toggle";
    public static final String DEBUG_BODY_PARTS_TOGGLE = "devmod.debug.body_parts.toggle";
    public static final String DEBUG_OVERLAYS_ENABLE_ALL = "devmod.debug.overlays.enable_all";
    public static final String DEBUG_OVERLAYS_DISABLE_ALL = "devmod.debug.overlays.disable_all";
    public static final String DEBUG_NATIVE_ENTITY_PATHING_TOGGLE = "devmod.debug.native.entity_pathing.toggle";
    public static final String DEBUG_NATIVE_ENTITY_GOALS_TOGGLE = "devmod.debug.native.entity_goals.toggle";
    public static final String DEBUG_NATIVE_ENTITY_BRAINS_TOGGLE = "devmod.debug.native.entity_brains.toggle";
    public static final String DEBUG_NATIVE_POI_TOGGLE = "devmod.debug.native.poi.toggle";
    public static final String DEBUG_NATIVE_RAIDS_TOGGLE = "devmod.debug.native.raids.toggle";
    public static final String DEBUG_NATIVE_BEES_TOGGLE = "devmod.debug.native.bees.toggle";
    public static final String DEBUG_NATIVE_GAME_EVENTS_TOGGLE = "devmod.debug.native.game_events.toggle";
    public static final String DEBUG_NATIVE_STRUCTURES_TOGGLE = "devmod.debug.native.structures.toggle";
    public static final String DEBUG_LIGHT_OVERLAY_TOGGLE = "devmod.debug.light_overlay.toggle";
    public static final String DEBUG_HEATMAP_CYCLE = "devmod.debug.heatmap.cycle";
    public static final String DEBUG_HEATMAP_TOGGLE = "devmod.debug.heatmap.toggle";
    public static final String DEBUG_HEATMAP_DEATH_TOGGLE = "devmod.debug.heatmap.death.toggle";
    public static final String DEBUG_HEATMAP_MOVEMENT_TOGGLE = "devmod.debug.heatmap.movement.toggle";
    public static final String DEBUG_HEATMAP_CAMPING_TOGGLE = "devmod.debug.heatmap.camping.toggle";
    public static final String DEBUG_HEATMAP_STUCK_TOGGLE = "devmod.debug.heatmap.stuck.toggle";
    public static final String DEBUG_HEATMAP_AGGRO_DROP_TOGGLE = "devmod.debug.heatmap.aggro_drop.toggle";
    public static final String DEBUG_HEATMAP_KITING_TOGGLE = "devmod.debug.heatmap.kiting.toggle";
    public static final String DEBUG_HEATMAP_LIGHT_SPAWNABLE_TOGGLE = "devmod.debug.heatmap.light_spawnable.toggle";
    public static final String DEBUG_HEATMAP_LIGHT_DARK_TOGGLE = "devmod.debug.heatmap.light_dark.toggle";
    public static final String DEBUG_HEATMAP_CLEAR_CURRENT = "devmod.debug.heatmap.clear_current";
    public static final String DEBUG_HEATMAP_CLEAR_ALL = "devmod.debug.heatmap.clear_all";
    public static final String DEBUG_ROOM_BOUNDS_TOGGLE = "devmod.debug.room_bounds.toggle";
    public static final String DEBUG_ROOM_BOUNDS_RELOAD = "devmod.debug.room_bounds.reload";
    public static final String DEBUG_ROOM_BOUNDS_GAPS_TOGGLE = "devmod.debug.room_bounds.gaps.toggle";
    public static final String DEBUG_ROOM_BOUNDS_CLEAR = "devmod.debug.room_bounds.clear";
    public static final String DEBUG_PATHFINDING_TOGGLE = "devmod.debug.pathfinding.toggle";
    public static final String DEBUG_LOS_TOGGLE = "devmod.debug.los.toggle";
    public static final String DEBUG_AGGRO_RANGE_TOGGLE = "devmod.debug.aggro_range.toggle";
    public static final String DEBUG_VERTICAL_LEVELS_TOGGLE = "devmod.debug.vertical_levels.toggle";
    public static final String DEBUG_SAFE_SPOTS_TOGGLE = "devmod.debug.safe_spots.toggle";
    public static final String DEBUG_ATTRIBUTE_MONITOR_TOGGLE = "devmod.debug.attribute_monitor.toggle";
    public static final String DEBUG_FPS_TRACKER_TOGGLE = "devmod.debug.fps_tracker.toggle";
    public static final String DEBUG_PROFILER_TOGGLE = "devmod.debug.profiler.toggle";
    public static final String DEBUG_ENTITY_DENSITY_TOGGLE = "devmod.debug.entity_density.toggle";
    public static final String DEBUG_BOSS_PHASE_TOGGLE = "devmod.debug.boss_phase.toggle";
    public static final String DEBUG_SKILL_EFFICACY_TOGGLE = "devmod.debug.skill_efficacy.toggle";
    public static final String DEBUG_SPAWNABILITY_TOGGLE = "devmod.debug.spawnability.toggle";
    public static final String DEBUG_CHUNK_PERF_TOGGLE = "devmod.debug.chunk_perf.toggle";
    public static final String DEBUG_ECONOMY_TOGGLE = "devmod.debug.economy.toggle";
    public static final String DEBUG_ECONOMY_VIEW_CYCLE = "devmod.debug.economy.view_cycle";
    public static final String DEBUG_ECONOMY_SORT_CYCLE = "devmod.debug.economy.sort_cycle";
    public static final String DEBUG_IMPACT_DISMISS = "devmod.hud.impact.dismiss";
    public static final String DEBUG_SCREEN_SHAKE_TEST = "devmod.debug.screen_shake.test";
    public static final String DEBUG_COMMAND_HELP = "devmod.debug.command.help";
    public static final String DEBUG_COMMAND_LIST = "devmod.debug.command.list";
    public static final String DEBUG_COMMAND_OFF = "devmod.debug.command.off";
    public static final String DEBUG_COMMAND_TOGGLE = "devmod.debug.command.toggle";

    public static final String HUD_QUICK_HELP_TOGGLE = "devmod.hud.quick_help.toggle";
    public static final String HUD_IMPACT_TOGGLE = "devmod.hud.impact.toggle";
    public static final String HUD_IMPACT_CONTROLLER_TOGGLE = "devmod.hud.impact.controller.toggle";
    public static final String HUD_IMPACT_3D_TOGGLE = "devmod.hud.impact_3d.toggle";
    public static final String HUD_IMPACT_DISPLAY_MODE_CYCLE = "devmod.hud.impact.display_mode.cycle";
    public static final String HUD_IMPACT_PRESET_MINIMAL = "devmod.hud.impact.preset.minimal";
    public static final String HUD_IMPACT_PRESET_DETAILED = "devmod.hud.impact.preset.detailed";
    public static final String HUD_IMPACT_PRESET_TRAINING = "devmod.hud.impact.preset.training";
    public static final String HUD_IMPACT_SHOW_RECAP = "devmod.hud.impact.show_recap";
    public static final String HUD_QUEST_TOGGLE = "devmod.hud.quest.toggle";
    public static final String HUD_ENDURANCE_TOGGLE = "devmod.endurance.hud.toggle";
    public static final String HUD_ENDURANCE_DETAILS_TOGGLE = "devmod.endurance.hud.details_toggle";
    public static final String HUD_PARTY_TOGGLE = "devmod.hud.party.toggle";

    // Config toggles (client)
    public static final String CONFIG_BODY_PART_DETECTION_TOGGLE = "devmod.config.body_part_detection.toggle";
    public static final String CONFIG_TELEMETRY_TOGGLE = "devmod.config.telemetry.toggle";
    public static final String CONFIG_TELEMETRY_HITS_TOGGLE = "devmod.config.telemetry.hits.toggle";
    public static final String CONFIG_TELEMETRY_DEATHS_TOGGLE = "devmod.config.telemetry.deaths.toggle";
    public static final String CONFIG_TELEMETRY_SPAWNS_TOGGLE = "devmod.config.telemetry.spawns.toggle";
    public static final String CONFIG_IMPACT_HUD_HISTORY_TOGGLE = "devmod.config.impact_hud.history.toggle";
    public static final String CONFIG_IMPACT_HUD_DPS_TOGGLE = "devmod.config.impact_hud.dps.toggle";
    public static final String CONFIG_IMPACT_HUD_POSITION_TOP_LEFT = "devmod.config.impact_hud.position.top_left";
    public static final String CONFIG_IMPACT_HUD_POSITION_TOP_RIGHT = "devmod.config.impact_hud.position.top_right";
    public static final String CONFIG_IMPACT_HUD_POSITION_CENTER_LEFT = "devmod.config.impact_hud.position.center_left";
    public static final String CONFIG_IMPACT_HUD_POSITION_CENTER_RIGHT = "devmod.config.impact_hud.position.center_right";
    public static final String CONFIG_IMPACT_HUD_POSITION_BOTTOM_LEFT = "devmod.config.impact_hud.position.bottom_left";
    public static final String CONFIG_IMPACT_HUD_POSITION_BOTTOM_RIGHT = "devmod.config.impact_hud.position.bottom_right";
    public static final String CONFIG_IMPACT_HUD_OFFSET_X_MINUS = "devmod.config.impact_hud.offset_x.minus";
    public static final String CONFIG_IMPACT_HUD_OFFSET_X_PLUS = "devmod.config.impact_hud.offset_x.plus";
    public static final String CONFIG_IMPACT_HUD_OFFSET_Y_MINUS = "devmod.config.impact_hud.offset_y.minus";
    public static final String CONFIG_IMPACT_HUD_OFFSET_Y_PLUS = "devmod.config.impact_hud.offset_y.plus";
    public static final String CONFIG_IMPACT_HUD_PRESET_EXPORT = "devmod.config.impact_hud.preset.export";
    public static final String CONFIG_IMPACT_HUD_PRESET_IMPORT = "devmod.config.impact_hud.preset.import";
    public static final String CONFIG_IMPACT_HUD_RESET_DEFAULTS = "devmod.config.impact_hud.reset_defaults";
    public static final String CONFIG_IMPACT_VFX_TOGGLE = "devmod.config.impact_vfx.toggle";
    public static final String CONFIG_IMPACT_VFX_VORTEX_TOGGLE = "devmod.config.impact_vfx.vortex.toggle";
    public static final String CONFIG_IMPACT_VFX_SLASH_TOGGLE = "devmod.config.impact_vfx.slash.toggle";
    public static final String CONFIG_IMPACT_VFX_LINES_TOGGLE = "devmod.config.impact_vfx.lines.toggle";
    public static final String CONFIG_IMPACT_VFX_INTENSITY_LOW = "devmod.config.impact_vfx.intensity.low";
    public static final String CONFIG_IMPACT_VFX_INTENSITY_MED = "devmod.config.impact_vfx.intensity.med";
    public static final String CONFIG_IMPACT_VFX_INTENSITY_HIGH = "devmod.config.impact_vfx.intensity.high";
    public static final String CONFIG_IMPACT_VFX_INTENSITY_MAX = "devmod.config.impact_vfx.intensity.max";
    public static final String CONFIG_IMPACT_VFX_RESET_DEFAULTS = "devmod.config.impact_vfx.reset_defaults";
    public static final String CONFIG_SCREEN_SHAKE_TOGGLE = "devmod.config.screen_shake.toggle";
    public static final String CONFIG_PROJECTILE_TRAILS_TOGGLE = "devmod.config.projectile_trails.toggle";
    public static final String CONFIG_BADGE_POPUPS_TOGGLE = "devmod.config.badge_popups.toggle";

    // Endurance actions
    public static final String QUEST_TASK_COMPLETE = "devmod.quest.task.complete";
    public static final String ENDURANCE_QUEST_START = "devmod.endurance.quest.start";
    public static final String ENDURANCE_QUEST_CONTINUE = "devmod.endurance.quest.continue";
    public static final String ENDURANCE_QUEST_EXIT = "devmod.endurance.quest.exit";

    // Abilities
    public static final String ABILITY_DASH = "devmod.ability.dash";
    public static final String ABILITY_DODGE = "devmod.ability.dodge";

    // Command shortcuts (vanilla)
    public static final String COMMAND_GAMEMODE_CREATIVE = "devmod.command.gamemode.creative";
    public static final String COMMAND_GAMEMODE_SURVIVAL = "devmod.command.gamemode.survival";
    public static final String COMMAND_HEAL = "devmod.command.heal";
    public static final String COMMAND_TIME_DAY = "devmod.command.time.day";
    public static final String COMMAND_TIME_NIGHT = "devmod.command.time.night";
    public static final String COMMAND_WEATHER_CLEAR = "devmod.command.weather.clear";
    public static final String COMMAND_NEXUS_RIFTSTAMP = "devmod.command.nexus.riftstamp";
    public static final String COMMAND_NEXUS_HELP = "devmod.command.nexus.help";
    public static final String COMMAND_NEXUS_ZONES = "devmod.command.nexus.zones";
    public static final String COMMAND_NEXUS_ENTER = "devmod.command.nexus.enter";
    public static final String COMMAND_NEXUS_RETURN = "devmod.command.nexus.return";
    public static final String COMMAND_NEXUS_TP_HUB = "devmod.command.nexus.tp.hub";
    public static final String COMMAND_NEXUS_TP_OVERVIEW = "devmod.command.nexus.tp.overview";
    public static final String COMMAND_NEXUS_TP_COMBAT = "devmod.command.nexus.tp.combat";
    public static final String COMMAND_NEXUS_TP_ARENA = "devmod.command.nexus.tp.arena";
    public static final String COMMAND_NEXUS_TP_UI = "devmod.command.nexus.tp.ui";
    public static final String COMMAND_NEXUS_TP_TELEMETRY = "devmod.command.nexus.tp.telemetry";
    public static final String COMMAND_NEXUS_TP_SHOWCASE = "devmod.command.nexus.tp.showcase";
    public static final String COMMAND_NEXUS_TP_INTEGRATION = "devmod.command.nexus.tp.integration";
    public static final String COMMAND_NEXUS_TP_SANDBOX = "devmod.command.nexus.tp.sandbox";
    public static final String COMMAND_NEXUS_TP_MECHANICS = "devmod.command.nexus.tp.mechanics";
    public static final String COMMAND_NEXUS_TP_TUTORIAL = "devmod.command.nexus.tp.tutorial";
    public static final String COMMAND_NEXUS_TP_GATE_PROGRESSION = "devmod.command.nexus.tp.gate_progression";
    public static final String COMMAND_NEXUS_TP_CLASSES = "devmod.command.nexus.tp.classes";
    public static final String COMMAND_NEXUS_TP_BUILDING_WEST = "devmod.command.nexus.tp.building_west";
    public static final String COMMAND_NEXUS_TP_BUILDING_EAST = "devmod.command.nexus.tp.building_east";
    public static final String COMMAND_NEXUS_TP_QUEST_NORTHEAST = "devmod.command.nexus.tp.quest_northeast";
    public static final String COMMAND_NEXUS_TP_QUEST_NORTHWEST = "devmod.command.nexus.tp.quest_northwest";
    public static final String COMMAND_NEXUS_TP_WAR_HUB_EAST = "devmod.command.nexus.tp.war_hub_east";
    public static final String COMMAND_NEXUS_TP_WAR_HUB_WEST = "devmod.command.nexus.tp.war_hub_west";
    public static final String COMMAND_NEXUS_TP_ECONOMIA_EAST = "devmod.command.nexus.tp.economia_east";
    public static final String COMMAND_NEXUS_TP_ECONOMIA_WEST = "devmod.command.nexus.tp.economia_west";
    public static final String COMMAND_NEXUS_TP_TOWN_MANAGEMENT = "devmod.command.nexus.tp.town_management";
    public static final String COMMAND_NEXUS_TP_EVENTI = "devmod.command.nexus.tp.eventi";
    public static final String COMMAND_NEXUS_TP_DM_MOD = "devmod.command.nexus.tp.dm_mod";
    public static final String COMMAND_NEXUS_STATUS = "devmod.command.nexus.status";
    public static final String COMMAND_NEXUS_REBUILD = "devmod.command.nexus.rebuild";
    public static final String COMMAND_NEXUS_LOCK = "devmod.command.nexus.lock";
    public static final String COMMAND_NEXUS_UNLOCK = "devmod.command.nexus.unlock";
    public static final String COMMAND_NEXUS_AVATAR_STATUS = "devmod.command.nexus.avatar.status";
    public static final String COMMAND_NEXUS_AVATAR_SPAWN = "devmod.command.nexus.avatar.spawn";
    public static final String COMMAND_NEXUS_AVATAR_REMOVE = "devmod.command.nexus.avatar.remove";

    // Arena
    public static final String ARENA_HELP = "devmod.arena.help";
    public static final String ARENA_CREATE = "devmod.arena.create";
    public static final String ARENA_TEMPLATE_LIST = "devmod.arena.template.list";
    public static final String ARENA_TEMPLATE_INFO = "devmod.arena.template.info";
    public static final String ARENA_TEMPLATE_RELOAD = "devmod.arena.template.reload";
    public static final String ARENA_TEMPLATE_STATUS = "devmod.arena.template.status";
    public static final String ARENA_AUTOSMOKE_RUN = "devmod.arena.autosmoke.run";
    public static final String ARENA_AUTOSMOKE_STATUS = "devmod.arena.autosmoke.status";
    public static final String ARENA_AUTOSMOKE_SCHEDULE_STATUS = "devmod.arena.autosmoke.schedule_status";
    public static final String ARENA_STATUS = "devmod.arena.status";
    public static final String ARENA_VALIDATE = "devmod.arena.validate";
    public static final String ARENA_FORCE = "devmod.arena.force";
    public static final String ARENA_FORCE_CLEAR = "devmod.arena.force.clear";
    public static final String ARENA_FORCE_STATUS = "devmod.arena.force.status";
    public static final String ARENA_METRICS = "devmod.arena.metrics";
    public static final String ARENA_HUD_TOGGLE = "devmod.arena.hud.toggle";
    public static final String ARENA_HUD_ON = "devmod.arena.hud.on";
    public static final String ARENA_HUD_OFF = "devmod.arena.hud.off";
    public static final String ARENA_HUD_STATUS = "devmod.arena.hud.status";
    public static final String ARENA_QUICK_TEST_WIZARD_OPEN = "devmod.arena.quick_test_wizard.open";

    // Telemetry admin
    public static final String TELEMETRY_RELOAD = "devmod.telemetry.reload";
    public static final String TELEMETRY_DUMP_WEAPONS = "devmod.telemetry.dump.weapons";
    public static final String TELEMETRY_DUMP_ROOMS = "devmod.telemetry.dump.rooms";
    public static final String TELEMETRY_DUMP_FIGHTS = "devmod.telemetry.dump.fights";
    public static final String TELEMETRY_DUMP_MINIONS = "devmod.telemetry.dump.minions";
    public static final String TELEMETRY_EXPORT_HEATMAPS = "devmod.telemetry.export.heatmaps";
    public static final String TELEMETRY_EXPORT_PNG = "devmod.telemetry.export.png";
    public static final String TELEMETRY_EXPORT_CSV = "devmod.telemetry.export.csv";
    public static final String TELEMETRY_EXPORT_JSON = "devmod.telemetry.export.json";
    public static final String TELEMETRY_EXPORT_ALL = "devmod.telemetry.export.all";
    public static final String TELEMETRY_EXPORT_HEATMAP_DEATH = "devmod.telemetry.export.heatmap.death";
    public static final String TELEMETRY_EXPORT_HEATMAP_MOVEMENT = "devmod.telemetry.export.heatmap.movement";
    public static final String TELEMETRY_EXPORT_HEATMAP_CAMPING = "devmod.telemetry.export.heatmap.camping";
    public static final String TELEMETRY_EXPORT_HEATMAP_STUCK = "devmod.telemetry.export.heatmap.stuck";
    public static final String TELEMETRY_EXPORT_HEATMAP_AGGRO_DROP = "devmod.telemetry.export.heatmap.aggro_drop";
    public static final String TELEMETRY_EXPORT_HEATMAP_KITING = "devmod.telemetry.export.heatmap.kiting";
    public static final String TELEMETRY_EXPORT_HEATMAP_CHOKE_POINTS = "devmod.telemetry.export.heatmap.choke_points";
    public static final String TELEMETRY_EXPORT_HEATMAP_PARKOUR_FALLS = "devmod.telemetry.export.heatmap.parkour_falls";
    public static final String TELEMETRY_EXPORT_DAMAGE_STATS = "devmod.telemetry.export.damage_stats";
    public static final String TELEMETRY_SCAN_LIGHT_ALL = "devmod.telemetry.scan.light.all";
    public static final String TELEMETRY_SCAN_LIGHT_ROOM = "devmod.telemetry.scan.light.room";
    public static final String TELEMETRY_SPAWNABILITY = "devmod.telemetry.spawnability";
    public static final String TELEMETRY_DESIRELINES_DUMP = "devmod.telemetry.desirelines.dump";
    public static final String TELEMETRY_DESIRELINES_ANALYZE = "devmod.telemetry.desirelines.analyze";
    public static final String TELEMETRY_DUNGEONS_DUMP = "devmod.telemetry.dungeons.dump";
    public static final String TELEMETRY_DUNGEONS_STATS = "devmod.telemetry.dungeons.stats";
    public static final String TELEMETRY_BACKTRACKING_DUMP = "devmod.telemetry.backtracking.dump";
    public static final String TELEMETRY_BACKTRACKING_CONFUSING = "devmod.telemetry.backtracking.confusing";

    public static final String TELEMETRY_DASHBOARD_SERVER_OPEN = "devmod.telemetry.dashboard.open";
    public static final String TELEMETRY_DASHBOARD_SERVER_START = "devmod.telemetry.dashboard.start";
    public static final String TELEMETRY_DASHBOARD_SERVER_STOP = "devmod.telemetry.dashboard.stop";
    public static final String TELEMETRY_DASHBOARD_SERVER_STATUS = "devmod.telemetry.dashboard.status";

    // Dungeon telemetry
    public static final String DUNGEON_HELP = "devmod.telemetry.dungeon.help";
    public static final String DUNGEON_START = "devmod.telemetry.dungeon.start";
    public static final String DUNGEON_END = "devmod.telemetry.dungeon.end";
    public static final String DUNGEON_STATUS = "devmod.telemetry.dungeon.status";

    // Test harness
    public static final String TEST_HUD_ON = "devmod.testing.hud.on";
    public static final String TEST_HUD_OFF = "devmod.testing.hud.off";
    public static final String TEST_HUD_TOGGLE = "devmod.testing.hud.toggle";
    public static final String TEST_HUD_EXPORT = "devmod.testing.hud.export";
    public static final String TEST_HUD_IMPORT = "devmod.testing.hud.import";
    public static final String TEST_PANEL_ON = "devmod.testing.panel.on";
    public static final String TEST_PANEL_OFF = "devmod.testing.panel.off";
    public static final String TEST_PANEL_TOGGLE = "devmod.testing.panel.toggle";
    public static final String TEST_DEBUG_ON = "devmod.testing.debug.on";
    public static final String TEST_DEBUG_OFF = "devmod.testing.debug.off";
    public static final String TEST_DEBUG_TOGGLE = "devmod.testing.debug.toggle";
    public static final String TEST_ENDURANCE_STATS = "devmod.testing.endurance.stats";
    public static final String TEST_ENDURANCE_PERKS = "devmod.testing.endurance.perks";
    public static final String TEST_ENDURANCE_SMOKE = "devmod.testing.endurance.smoke";
    public static final String TEST_ENDURANCE_EXPORT_TABLE = "devmod.testing.endurance.export.table";
    public static final String TEST_ENDURANCE_EXPORT_ALL = "devmod.testing.endurance.export.all";
    public static final String TEST_ENDURANCE_AUTOSMOKE = "devmod.testing.endurance.autosmoke";
    public static final String TEST_DEBUGBOX = "devmod.testing.debugbox";
    public static final String TEST_DEBUGCLEAR = "devmod.testing.debugclear";
    public static final String TEST_PANELCLEAR = "devmod.testing.panelclear";
    public static final String TEST_INFO = "devmod.testing.info";
    public static final String TEST_QA_OPEN = "devmod.testing.qa.open";
    public static final String TEST_BODYPART_INFO = "devmod.testing.bodypart.info";

    // QA testing session (client)
    public static final String QA_SESSION_START = "devmod.testing.session.start";
    public static final String QA_SESSION_RESUME = "devmod.testing.session.resume";
    public static final String QA_REPORT_SAVE = "devmod.testing.report.save";
    public static final String QA_REPORT_COPY = "devmod.testing.report.copy";
    public static final String QA_TEST_PASS = "devmod.testing.test.pass";
    public static final String QA_TEST_FAIL = "devmod.testing.test.fail";
    public static final String QA_TEST_SKIP = "devmod.testing.test.skip";
    public static final String QA_TEST_AUTO = "devmod.testing.test.auto";

    // Game Design Config
    public static final String CONFIG_GAMEDESIGN_RELOAD = "devmod.gamedesign.reload";
    public static final String CONFIG_GAMEDESIGN_SAVE = "devmod.gamedesign.save";
    public static final String CONFIG_GAMEDESIGN_RESET = "devmod.gamedesign.reset";

    // Resonance Chain
    public static final String CONFIG_RESONANCE_TOGGLE = "devmod.gamedesign.resonance.toggle";

    // Blood Contracts
    public static final String CONFIG_CONTRACTS_TOGGLE = "devmod.gamedesign.contracts.toggle";

    // Signature Weapons
    public static final String CONFIG_SIGNATURE_WEAPONS_TOGGLE = "devmod.gamedesign.signature_weapons.toggle";

    // Nemesis Evolution
    public static final String CONFIG_NEMESIS_TOGGLE = "devmod.gamedesign.nemesis.toggle";

    // The Tide
    public static final String CONFIG_TIDE_TOGGLE = "devmod.gamedesign.tide.toggle";

    // Presets
    public static final String CONFIG_GAMEDESIGN_PRESET_EASY = "devmod.gamedesign.preset.easy";
    public static final String CONFIG_GAMEDESIGN_PRESET_HARD = "devmod.gamedesign.preset.hard";
    public static final String CONFIG_GAMEDESIGN_PRESET_CHAOS = "devmod.gamedesign.preset.chaos";
    public static final String CONFIG_GAMEDESIGN_PRESET_TUTORIAL = "devmod.gamedesign.preset.tutorial";
    public static final String CONFIG_GAMEDESIGN_PRESET_SPEEDRUN = "devmod.gamedesign.preset.speedrun";

    // Mailbox admin
    public static final String MAILBOX_COMMAND_HELP = "devmod.mailbox.command.help";
    public static final String MAILBOX_COMMAND_STATS = "devmod.mailbox.command.stats";
    public static final String MAILBOX_COMMAND_SEND = "devmod.mailbox.command.send";
    public static final String MAILBOX_COMMAND_BROADCAST = "devmod.mailbox.command.broadcast";
    public static final String MAILBOX_COMMAND_INBOX = "devmod.mailbox.command.inbox";
    public static final String MAILBOX_COMMAND_PURGE = "devmod.mailbox.command.purge";

    // News admin
    public static final String NEWS_COMMAND_HELP = "devmod.news.command.help";
    public static final String NEWS_COMMAND_LIST = "devmod.news.command.list";
    public static final String NEWS_COMMAND_CREATE = "devmod.news.command.create";
    public static final String NEWS_COMMAND_DELETE = "devmod.news.command.delete";
    public static final String NEWS_COMMAND_PUBLISH = "devmod.news.command.publish";

    // Leaderboard commands
    public static final String LEADERBOARD_HELP = "devmod.leaderboard.help";
    public static final String LEADERBOARD_LIST = "devmod.leaderboard.list";
    public static final String LEADERBOARD_TOP = "devmod.leaderboard.top";
    public static final String LEADERBOARD_ME = "devmod.leaderboard.me";
    public static final String LEADERBOARD_PLAYER = "devmod.leaderboard.player";
    public static final String LEADERBOARD_WEEKLY = "devmod.leaderboard.weekly";
    public static final String LEADERBOARD_ARENA = "devmod.leaderboard.arena";
}
