package com.frenkvs.devmod;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

/**
 * Contains all keybind definitions for DevMod.
 *
 * <h2>Keybind Categories</h2>
 * <ul>
 *   <li><b>Primary Access</b>: G (Radial Menu) - Main entry point to all DevMod tools</li>
 *   <li><b>Screens</b>: K (Settings), M (Item Editor), J (Dashboard), N (QA Testing)</li>
 *   <li><b>Debug Overlays</b>: O, L, H, R, P, V, Y, C, B, Z</li>
 *   <li><b>Performance</b>: F6, F8, F9 (Entity Density, FPS, Profiler)</li>
 *   <li><b>Quest System</b>: \, [, ], F10, F11, F12</li>
 * </ul>
 *
 * <h2>Design Notes</h2>
 * <ul>
 *   <li>Most overlays are accessible via the Radial Menu (G key)</li>
 *   <li>Direct keybinds are for power users who want quick access</li>
 *   <li>F-keys are reserved for less frequently used features</li>
 *   <li>Letter keys use mnemonics (L=Light, H=Heatmap, P=Pathfinding)</li>
 * </ul>
 *
 * <p><b>NOTE:</b> Input event handling is in {@code KeyInputEvents.java} (top-level class)
 * because NeoForge does NOT scan inner classes for @EventBusSubscriber annotations.</p>
 *
 * @see com.frenkvs.devmod.ui.radial.RadialMenuScreenV2
 * @see com.frenkvs.devmod.rendering.RenderEvents#handleKeyBindings
 */
public class KeyInputHandler {

    // ═══════════════════════════════════════════════════════════════════════════
    // PRIMARY ACCESS - Radial Menu (Main entry point)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Opens the Radial Menu - the primary interface for accessing all DevMod tools.
     *
     * <p>The Radial Menu provides categorized access to:</p>
     * <ul>
     *   <li>Debug overlays (hitboxes, light levels, pathfinding)</li>
     *   <li>Spatial analysis tools (heatmaps, room bounds, safe spots)</li>
     *   <li>Performance monitors (FPS tracker, profiler)</li>
     *   <li>Configuration screens (settings, editors)</li>
     *   <li>Quest system tools</li>
     * </ul>
     *
     * <p><b>Default key:</b> G</p>
     * <p><b>Mnemonic:</b> G = "Go to menu" / General access</p>
     *
     * @see com.frenkvs.devmod.ui.radial.RadialMenuScreenV2
     */
    public static final KeyMapping OPEN_RADIAL_MENU_KEY = new KeyMapping(
            "key.devmod.radial_menu",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_G,
            "key.categories.devmod"
    );

    // ═══════════════════════════════════════════════════════════════════════════
    // CONFIGURATION SCREENS - Direct access to editors and settings
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Opens the unified Settings Panel for DevMod configuration.
     *
     * <p>Provides access to all DevMod settings including overlay toggles,
     * telemetry options, and UI preferences.</p>
     *
     * <p><b>Default key:</b> K</p>
     * <p><b>Mnemonic:</b> K = "Konfiguration" / Settings</p>
     *
     * @see com.frenkvs.devmod.ui.unified.UnifiedSettingsScreen
     */
    public static final KeyMapping OPEN_SETTINGS_KEY = new KeyMapping(
            "key.devmod.settings",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_K,
            "key.categories.devmod"
    );

    /**
     * Opens the Weapon/Item Editor for modifying held item attributes.
     *
     * <p>Allows editing damage, attack speed, and other attributes
     * of the currently held weapon or tool.</p>
     *
     * <p><b>Default key:</b> M</p>
     * <p><b>Mnemonic:</b> M = "Modify" item</p>
     *
     * @see com.frenkvs.devmod.WeaponEditorScreen
     */
    public static final KeyMapping OPEN_WEAPON_EDITOR_KEY = new KeyMapping(
            "key.devmod.weapon_editor",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_M,
            "key.categories.devmod"
    );

    /**
     * Opens the Telemetry Dashboard for viewing combat and performance statistics.
     *
     * <p>Displays aggregated telemetry data including DPS charts,
     * damage distribution, and session statistics.</p>
     *
     * <p><b>Default key:</b> J</p>
     * <p><b>Mnemonic:</b> J = "Journal" / Dashboard</p>
     *
     * @see com.frenkvs.devmod.TelemetryDashboardScreen
     */
    public static final KeyMapping OPEN_DASHBOARD_KEY = new KeyMapping(
            "key.devmod.dashboard",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_J,
            "key.categories.devmod"
    );

    /**
     * Opens MobConfigScreen for the mob the player is looking at.
     *
     * <p>Allows inspecting and modifying mob attributes (health, damage, speed)
     * for the targeted entity. Requires looking at a valid Mob entity.</p>
     *
     * <p><b>Default key:</b> X</p>
     * <p><b>Mnemonic:</b> X = "eXamine" mob (avoids I=Inventory conflict)</p>
     *
     * @see com.frenkvs.devmod.MobConfigScreen
     */
    public static final KeyMapping INSPECT_MOB_KEY = new KeyMapping(
            "key.devmod.inspect_mob",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_X,
            "key.categories.devmod"
    );

    // ═══════════════════════════════════════════════════════════════════════════
    // DEBUG OVERLAYS - Visual debugging tools (toggleable)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Toggles the main Debug Overlay showing mob info, hitboxes, and body parts.
     *
     * <p>Displays real-time information about nearby entities including
     * health, attributes, AI state, and body part hitboxes.</p>
     *
     * <p><b>Default key:</b> O</p>
     * <p><b>Mnemonic:</b> O = "Overlay" debug</p>
     *
     * @see com.frenkvs.devmod.rendering.MobDebugOverlay
     */
    public static final KeyMapping TOGGLE_DEBUG_OVERLAY_KEY = new KeyMapping(
            "key.devmod.debug_overlay",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_O,
            "key.categories.devmod"
    );

    /**
     * Toggles the Light Level Overlay showing block light values.
     *
     * <p>Displays colored markers on blocks indicating light level:
     * red for hostile-spawnable (0-7), yellow for marginal (8-11),
     * green for safe (12+).</p>
     *
     * <p><b>Default key:</b> L</p>
     * <p><b>Mnemonic:</b> L = "Light" levels</p>
     *
     * @see com.frenkvs.devmod.rendering.LightLevelOverlay
     */
    public static final KeyMapping TOGGLE_LIGHT_OVERLAY_KEY = new KeyMapping(
            "key.devmod.light_overlay",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_L,
            "key.categories.devmod"
    );

    /**
     * Cycles through Heatmap visualization types.
     *
     * <p>Available heatmap types: Death, Movement, Camping, Stuck,
     * Aggro Drop, Kiting. Shows spatial analysis of player behavior.</p>
     *
     * <p><b>Default key:</b> H</p>
     * <p><b>Mnemonic:</b> H = "Heatmap"</p>
     *
     * @see com.frenkvs.devmod.rendering.HeatmapVisualizer
     */
    public static final KeyMapping TOGGLE_HEATMAP_KEY = new KeyMapping(
            "key.devmod.heatmap",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_H,
            "key.categories.devmod"
    );

    /**
     * Toggles Room Bounds Visualizer showing detected room boundaries.
     *
     * <p>Displays wireframe boxes around detected enclosed spaces,
     * useful for understanding mob spawning and pathfinding constraints.</p>
     *
     * <p><b>Default key:</b> R</p>
     * <p><b>Mnemonic:</b> R = "Room" bounds</p>
     *
     * @see com.frenkvs.devmod.rendering.RoomBoundsVisualizer
     */
    public static final KeyMapping TOGGLE_ROOM_BOUNDS_KEY = new KeyMapping(
            "key.devmod.room_bounds",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_R,
            "key.categories.devmod"
    );

    /**
     * Toggles Pathfinding Debugger showing mob navigation paths.
     *
     * <p>Renders the actual pathfinding nodes and connections
     * that mobs use for navigation. Useful for understanding AI behavior.</p>
     *
     * <p><b>Default key:</b> P</p>
     * <p><b>Mnemonic:</b> P = "Pathfinding"</p>
     *
     * @see com.frenkvs.devmod.rendering.PathfindingDebugger
     */
    public static final KeyMapping TOGGLE_PATHFINDING_KEY = new KeyMapping(
            "key.devmod.pathfinding",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_P,
            "key.categories.devmod"
    );

    /**
     * Toggles Line of Sight Visualizer showing mob vision cones.
     *
     * <p>Displays what mobs can see and their detection ranges.
     * Useful for stealth mechanics testing.</p>
     *
     * <p><b>Default key:</b> V</p>
     * <p><b>Mnemonic:</b> V = "Vision" / Line of sight</p>
     *
     * @see com.frenkvs.devmod.rendering.LineOfSightVisualizer
     */
    public static final KeyMapping TOGGLE_LOS_KEY = new KeyMapping(
            "key.devmod.los",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_V,
            "key.categories.devmod"
    );

    /**
     * Toggles Vertical Levels Visualizer showing Y-coordinate bands.
     *
     * <p>Displays horizontal planes at key Y-levels (sea level, diamond level, etc.)
     * for spatial reference during exploration.</p>
     *
     * <p><b>Default key:</b> Y</p>
     * <p><b>Mnemonic:</b> Y = "Y-levels" (vertical axis)</p>
     *
     * @see com.frenkvs.devmod.rendering.VerticalLevelsVisualizer
     */
    public static final KeyMapping TOGGLE_VERTICAL_LEVELS_KEY = new KeyMapping(
            "key.devmod.vertical_levels",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_Y,
            "key.categories.devmod"
    );

    /**
     * Toggles Safe Spot Visualizer showing safe camping locations.
     *
     * <p>Highlights positions where mobs cannot reach the player,
     * useful for identifying cheese spots or safe rest areas.</p>
     *
     * <p><b>Default key:</b> C</p>
     * <p><b>Mnemonic:</b> C = "Camping" / safe spots</p>
     *
     * @see com.frenkvs.devmod.rendering.SafeSpotVisualizer
     */
    public static final KeyMapping TOGGLE_SAFE_SPOT_KEY = new KeyMapping(
            "key.devmod.safe_spot",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_C,
            "key.categories.devmod"
    );

    /**
     * Toggles Attribute Monitoring System for real-time attribute tracking.
     *
     * <p>Shows floating 3D rays and panels with live attribute values
     * for monitored entities. Useful for tracking buff/debuff effects.</p>
     *
     * <p><b>Default key:</b> U</p>
     * <p><b>Mnemonic:</b> U = "attribUtes" monitor</p>
     *
     * @see com.frenkvs.devmod.attributes.AttributeMonitoringSystem
     */
    public static final KeyMapping TOGGLE_ATTRIBUTE_MONITOR_KEY = new KeyMapping(
            "key.devmod.attribute_monitor",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_U,
            "key.categories.devmod"
    );

    // ═══════════════════════════════════════════════════════════════════════════
    // PERFORMANCE MONITORING - FPS, profiling, entity analysis
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Toggles FPS Tracker overlay showing frame rate statistics.
     *
     * <p>Displays current FPS, min/max/avg values, and frame time graph.
     * Useful for performance testing and optimization.</p>
     *
     * <p><b>Default key:</b> F8</p>
     *
     * @see com.frenkvs.devmod.telemetry.FpsTracker
     */
    public static final KeyMapping TOGGLE_FPS_TRACKER_KEY = new KeyMapping(
            "key.devmod.fps_tracker",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_F8,
            "key.categories.devmod"
    );

    /**
     * Toggles Performance Profiler showing detailed timing breakdowns.
     *
     * <p>Shows timing for rendering, ticking, and DevMod subsystems.
     * Helps identify performance bottlenecks.</p>
     *
     * <p><b>Default key:</b> F9</p>
     *
     * @see com.frenkvs.devmod.telemetry.PerformanceProfiler
     */
    public static final KeyMapping TOGGLE_PROFILER_KEY = new KeyMapping(
            "key.devmod.profiler",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_F9,
            "key.categories.devmod"
    );

    /**
     * Toggles Entity Density Overlay showing mob concentration areas.
     *
     * <p>Displays heatmap-style visualization of entity distribution
     * in the current area. Useful for spawn analysis.</p>
     *
     * <p><b>Default key:</b> F6</p>
     *
     * @see com.frenkvs.devmod.hud.EntityDensityOverlay
     */
    public static final KeyMapping TOGGLE_ENTITY_DENSITY_KEY = new KeyMapping(
            "key.devmod.entity_density",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_F6,
            "key.categories.devmod"
    );

    /**
     * Toggles Boss Phase Overlay showing boss fight state information.
     *
     * <p>Displays current phase, phase transitions, and boss-specific
     * mechanics for supported boss entities.</p>
     *
     * <p><b>Default key:</b> B</p>
     * <p><b>Mnemonic:</b> B = "Boss" phases</p>
     *
     * @see com.frenkvs.devmod.hud.BossPhaseOverlay
     */
    public static final KeyMapping TOGGLE_BOSS_PHASE_KEY = new KeyMapping(
            "key.devmod.boss_phase",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_B,
            "key.categories.devmod"
    );

    /**
     * Toggles Skill Efficacy Overlay showing combat skill effectiveness.
     *
     * <p>Displays metrics on attack efficiency, damage output consistency,
     * and combat skill ratings.</p>
     *
     * <p><b>Default key:</b> Z</p>
     * <p><b>Note:</b> Z chosen to avoid F5 conflict (Minecraft camera toggle)</p>
     *
     * @see com.frenkvs.devmod.hud.SkillEfficacyOverlay
     */
    public static final KeyMapping TOGGLE_SKILL_EFFICACY_KEY = new KeyMapping(
            "key.devmod.skill_efficacy",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_Z,
            "key.categories.devmod"
    );

    /**
     * Toggles Spawnability Map showing where mobs can spawn.
     *
     * <p>Displays colored overlay indicating spawn-valid blocks based on
     * light level, block type, and spawn conditions.</p>
     *
     * <p><b>Default key:</b> F4</p>
     *
     * @see com.frenkvs.devmod.rendering.SpawnabilityMap
     */
    public static final KeyMapping TOGGLE_SPAWNABILITY_KEY = new KeyMapping(
            "key.devmod.spawnability",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_F4,
            "key.categories.devmod"
    );

    // ═══════════════════════════════════════════════════════════════════════════
    // TESTING & QA - Development and testing tools
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Opens QA Testing Screen for structured testing workflows.
     *
     * <p>Provides guided testing scenarios, checklists, and automated
     * test case management for quality assurance.</p>
     *
     * <p><b>Default key:</b> N</p>
     * <p><b>Mnemonic:</b> N = "New" test</p>
     *
     * @see com.frenkvs.devmod.testing.QATestingScreen
     */
    public static final KeyMapping OPEN_QA_TESTING_KEY = new KeyMapping(
            "key.devmod.qa_testing",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_N,
            "key.categories.devmod"
    );

    /**
     * Opens Testing Hub for comprehensive test management.
     *
     * <p>Central hub for all testing features including session management,
     * test history, and batch test execution.</p>
     *
     * <p><b>Default key:</b> F7</p>
     *
     * @see com.frenkvs.devmod.testing.TestingHubScreen
     */
    public static final KeyMapping OPEN_TESTING_HUB_KEY = new KeyMapping(
            "key.devmod.testing_hub",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_F7,
            "key.categories.devmod"
    );

    // ═══════════════════════════════════════════════════════════════════════════
    // QUEST SYSTEM - Quest management and gameplay
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Toggles Quest HUD showing active quest progress.
     *
     * <p>Displays current quest objectives, progress bars, and task status
     * in a non-intrusive overlay.</p>
     *
     * <p><b>Default key:</b> \ (Backslash)</p>
     *
     * @see com.frenkvs.devmod.quest.QuestHudOverlay
     */
    public static final KeyMapping TOGGLE_QUEST_HUD_KEY = new KeyMapping(
            "key.devmod.quest_hud",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_BACKSLASH,
            "key.categories.devmod"
    );

    /**
     * Manually completes the current quest task (debug/testing).
     *
     * <p>Forces completion of the current objective. Useful for testing
     * quest progression without performing actual tasks.</p>
     *
     * <p><b>Default key:</b> ] (Right bracket)</p>
     *
     * @see com.frenkvs.devmod.quest.QuestManager
     */
    public static final KeyMapping QUEST_COMPLETE_TASK_KEY = new KeyMapping(
            "key.devmod.quest_complete",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_RIGHT_BRACKET,
            "key.categories.devmod"
    );

    /**
     * Opens Quest Editor for creating and editing quests.
     *
     * <p>Full-featured quest editor with task creation, condition setup,
     * and reward configuration.</p>
     *
     * <p><b>Default key:</b> [ (Left bracket)</p>
     *
     * @see com.frenkvs.devmod.quest.QuestEditorScreen
     */
    public static final KeyMapping OPEN_QUEST_EDITOR_KEY = new KeyMapping(
            "key.devmod.quest_editor",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_LEFT_BRACKET,
            "key.categories.devmod"
    );

    /**
     * Opens Endurance Quest mode for wave-based survival challenges.
     *
     * <p>Starts or manages endurance quests with progressive difficulty,
     * perk selection, and shop system.</p>
     *
     * <p><b>Default key:</b> F10</p>
     * <p><b>Modifiers:</b> Shift=HUD toggle, Ctrl=Details panel</p>
     *
     * @see com.frenkvs.devmod.endurance.EnduranceQuestScreen
     */
    public static final KeyMapping OPEN_ENDURANCE_QUEST_KEY = new KeyMapping(
            "key.devmod.endurance_quest",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_F10,
            "key.categories.devmod"
    );

    /**
     * Continue/Respawn action during Endurance Quest.
     *
     * <p>Used to respawn after death or continue to the next wave
     * during active endurance quests.</p>
     *
     * <p><b>Default key:</b> F11</p>
     *
     * @see com.frenkvs.devmod.endurance.EnduranceQuestManager
     */
    public static final KeyMapping QUEST_CONTINUE_KEY = new KeyMapping(
            "key.devmod.quest_continue",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_F11,
            "key.categories.devmod"
    );

    /**
     * Exit/Give Up action during Endurance Quest.
     *
     * <p>Abandons the current endurance quest and returns to normal gameplay.
     * Progress will be lost.</p>
     *
     * <p><b>Default key:</b> F12</p>
     *
     * @see com.frenkvs.devmod.endurance.EnduranceQuestManager
     */
    public static final KeyMapping QUEST_EXIT_KEY = new KeyMapping(
            "key.devmod.quest_exit",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_F12,
            "key.categories.devmod"
    );

    /**
     * Opens Party Management Screen for multiplayer Endurance Quests.
     *
     * <p>Allows creating/joining parties, inviting players, managing
     * ready status, and selecting quest type for multiplayer sessions.</p>
     *
     * <p><b>Default key:</b> F5</p>
     * <p><b>Note:</b> Changed from P (used by Pathfinding) to F5</p>
     *
     * @see com.frenkvs.devmod.party.PartyScreen
     */
    public static final KeyMapping OPEN_PARTY_KEY = new KeyMapping(
            "key.devmod.party",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_F5,
            "key.categories.devmod"
    );

    // ═══════════════════════════════════════════════════════════════════════════
    // ECONOMY & MISC - Additional overlays and utilities
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Toggles Economy Overlay showing item and loot statistics.
     *
     * <p>Displays item acquisition rates, drop statistics, and
     * economic flow analysis.</p>
     *
     * <p><b>Default key:</b> ; (Semicolon)</p>
     * <p><b>Note:</b> Semicolon chosen to avoid F3 conflict (Minecraft debug)</p>
     *
     * @see com.frenkvs.devmod.telemetry.EconomyOverlay
     */
    public static final KeyMapping TOGGLE_ECONOMY_KEY = new KeyMapping(
            "key.devmod.economy",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_SEMICOLON,
            "key.categories.devmod"
    );

    /**
     * Toggles Chunk Performance Visualizer showing chunk load times.
     *
     * <p>Displays per-chunk performance metrics useful for identifying
     * lag sources and optimization opportunities.</p>
     *
     * <p><b>Default key:</b> ' (Apostrophe)</p>
     * <p><b>Note:</b> Apostrophe chosen to avoid F2 conflict (Minecraft screenshot)</p>
     *
     * @see com.frenkvs.devmod.rendering.ChunkPerformanceVisualizer
     */
    public static final KeyMapping TOGGLE_CHUNK_PERF_KEY = new KeyMapping(
            "key.devmod.chunk_perf",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_APOSTROPHE,
            "key.categories.devmod"
    );

    /**
     * Toggles Quick Help Overlay showing keybind reference.
     *
     * <p>Displays a summary of all DevMod keybinds for quick reference
     * during gameplay without opening the full settings menu.</p>
     *
     * <p><b>Default key:</b> ` (Grave/Backtick)</p>
     *
     * @see com.frenkvs.devmod.hud.QuickHelpOverlay
     */
    public static final KeyMapping TOGGLE_HELP_KEY = new KeyMapping(
            "key.devmod.help_overlay",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_GRAVE_ACCENT,
            "key.categories.devmod"
    );

    // ═══════════════════════════════════════════════════════════════════════════
    // EFFECTS - Visual effects testing
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Triggers a test screen shake effect.
     *
     * <p>Useful for testing and demonstrating the screen shake system.
     * Creates a medium-intensity shake at the player's position.</p>
     *
     * <p><b>Default key:</b> 0 (zero key, works on all keyboards)</p>
     *
     * @see com.frenkvs.devmod.effects.ShakeManager
     */
    public static final KeyMapping TEST_SCREEN_SHAKE_KEY = new KeyMapping(
            "key.devmod.test_shake",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_0,
            "key.categories.devmod"
    );
}
