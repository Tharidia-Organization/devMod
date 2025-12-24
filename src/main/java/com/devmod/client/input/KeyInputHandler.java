package com.devmod.client.input;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

import java.util.Objects;

/**
 * Contains all keybind definitions for DevMod.
 *
 * <p>This class is client-only and will not be loaded on dedicated servers.
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
 * <p><b>Defaults:</b> Only the Radial Menu is bound by default. All other keybinds ship
 * unassigned; the keys documented below are suggested mappings.</p>
 *
 * @see com.devmod.ui.radial.RadialMenuScreenV3
 */
@OnlyIn(Dist.CLIENT)
public class KeyInputHandler {

    // ═══════════════════════════════════════════════════════════════════════════
    // PRIMARY ACCESS - Radial Menu (Main entry point)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Opens the Radial Menu - the primary interface for accessing all DevMod tools.
     *
     * <p><b>Default key:</b> G</p>
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

    public static final KeyMapping OPEN_SETTINGS_KEY = new KeyMapping(
            "key.devmod.settings",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_UNKNOWN,
            "key.categories.devmod"
    );

    public static final KeyMapping OPEN_WEAPON_EDITOR_KEY = new KeyMapping(
            "key.devmod.weapon_editor",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_UNKNOWN,
            "key.categories.devmod"
    );

    public static final KeyMapping OPEN_DASHBOARD_KEY = new KeyMapping(
            "key.devmod.dashboard",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_UNKNOWN,
            "key.categories.devmod"
    );

    public static final KeyMapping INSPECT_MOB_KEY = new KeyMapping(
            "key.devmod.inspect_mob",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_UNKNOWN,
            "key.categories.devmod"
    );

    // ═══════════════════════════════════════════════════════════════════════════
    // DEBUG OVERLAYS - Visual debugging tools (toggleable)
    // ═══════════════════════════════════════════════════════════════════════════

    public static final KeyMapping TOGGLE_DEBUG_OVERLAY_KEY = new KeyMapping(
            "key.devmod.debug_overlay",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_UNKNOWN,
            "key.categories.devmod"
    );

    public static final KeyMapping TOGGLE_LIGHT_OVERLAY_KEY = new KeyMapping(
            "key.devmod.light_overlay",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_UNKNOWN,
            "key.categories.devmod"
    );

    public static final KeyMapping TOGGLE_HEATMAP_KEY = new KeyMapping(
            "key.devmod.heatmap",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_UNKNOWN,
            "key.categories.devmod"
    );

    public static final KeyMapping DISMISS_IMPACT_HUD_KEY = new KeyMapping(
            "key.devmod.dismiss_impact_hud",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_UNKNOWN,
            "key.categories.devmod"
    );

    public static final KeyMapping TOGGLE_ROOM_BOUNDS_KEY = new KeyMapping(
            "key.devmod.room_bounds",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_UNKNOWN,
            "key.categories.devmod"
    );

    public static final KeyMapping TOGGLE_PATHFINDING_KEY = new KeyMapping(
            "key.devmod.pathfinding",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_UNKNOWN,
            "key.categories.devmod"
    );

    public static final KeyMapping TOGGLE_LOS_KEY = new KeyMapping(
            "key.devmod.los",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_UNKNOWN,
            "key.categories.devmod"
    );

    public static final KeyMapping TOGGLE_VERTICAL_LEVELS_KEY = new KeyMapping(
            "key.devmod.vertical_levels",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_UNKNOWN,
            "key.categories.devmod"
    );

    public static final KeyMapping TOGGLE_SAFE_SPOT_KEY = new KeyMapping(
            "key.devmod.safe_spot",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_UNKNOWN,
            "key.categories.devmod"
    );

    public static final KeyMapping TOGGLE_ATTRIBUTE_MONITOR_KEY = new KeyMapping(
            "key.devmod.attribute_monitor",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_UNKNOWN,
            "key.categories.devmod"
    );

    // ═══════════════════════════════════════════════════════════════════════════
    // PERFORMANCE MONITORING
    // ═══════════════════════════════════════════════════════════════════════════

    public static final KeyMapping TOGGLE_FPS_TRACKER_KEY = new KeyMapping(
            "key.devmod.fps_tracker",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_UNKNOWN,
            "key.categories.devmod"
    );

    public static final KeyMapping TOGGLE_PROFILER_KEY = new KeyMapping(
            "key.devmod.profiler",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_UNKNOWN,
            "key.categories.devmod"
    );

    public static final KeyMapping TOGGLE_ENTITY_DENSITY_KEY = new KeyMapping(
            "key.devmod.entity_density",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_UNKNOWN,
            "key.categories.devmod"
    );

    public static final KeyMapping TOGGLE_BOSS_PHASE_KEY = new KeyMapping(
            "key.devmod.boss_phase",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_UNKNOWN,
            "key.categories.devmod"
    );

    public static final KeyMapping TOGGLE_SKILL_EFFICACY_KEY = new KeyMapping(
            "key.devmod.skill_efficacy",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_UNKNOWN,
            "key.categories.devmod"
    );

    public static final KeyMapping TOGGLE_SPAWNABILITY_KEY = new KeyMapping(
            "key.devmod.spawnability",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_UNKNOWN,
            "key.categories.devmod"
    );

    // ═══════════════════════════════════════════════════════════════════════════
    // TESTING & QA
    // ═══════════════════════════════════════════════════════════════════════════

    public static final KeyMapping OPEN_QA_TESTING_KEY = new KeyMapping(
            "key.devmod.qa_testing",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_UNKNOWN,
            "key.categories.devmod"
    );

    public static final KeyMapping OPEN_TESTING_HUB_KEY = new KeyMapping(
            "key.devmod.testing_hub",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_UNKNOWN,
            "key.categories.devmod"
    );

    // ═══════════════════════════════════════════════════════════════════════════
    // QUEST SYSTEM
    // ═══════════════════════════════════════════════════════════════════════════

    public static final KeyMapping TOGGLE_QUEST_HUD_KEY = new KeyMapping(
            "key.devmod.quest_hud",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_UNKNOWN,
            "key.categories.devmod"
    );

    public static final KeyMapping QUEST_COMPLETE_TASK_KEY = new KeyMapping(
            "key.devmod.quest_complete",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_UNKNOWN,
            "key.categories.devmod"
    );

    public static final KeyMapping OPEN_QUEST_EDITOR_KEY = new KeyMapping(
            "key.devmod.quest_editor",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_UNKNOWN,
            "key.categories.devmod"
    );

    public static final KeyMapping OPEN_ENDURANCE_QUEST_KEY = new KeyMapping(
            "key.devmod.endurance_quest",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_UNKNOWN,
            "key.categories.devmod"
    );

    public static final KeyMapping QUEST_CONTINUE_KEY = new KeyMapping(
            "key.devmod.quest_continue",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_UNKNOWN,
            "key.categories.devmod"
    );

    public static final KeyMapping QUEST_EXIT_KEY = new KeyMapping(
            "key.devmod.quest_exit",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_UNKNOWN,
            "key.categories.devmod"
    );

    public static final KeyMapping OPEN_PARTY_KEY = new KeyMapping(
            "key.devmod.party",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_UNKNOWN,
            "key.categories.devmod"
    );

    // ═══════════════════════════════════════════════════════════════════════════
    // ECONOMY & MISC
    // ═══════════════════════════════════════════════════════════════════════════

    public static final KeyMapping TOGGLE_ECONOMY_KEY = new KeyMapping(
            "key.devmod.economy",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_UNKNOWN,
            "key.categories.devmod"
    );

    public static final KeyMapping TOGGLE_CHUNK_PERF_KEY = new KeyMapping(
            "key.devmod.chunk_perf",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_UNKNOWN,
            "key.categories.devmod"
    );

    public static final KeyMapping TOGGLE_HELP_KEY = new KeyMapping(
            "key.devmod.help_overlay",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_UNKNOWN,
            "key.categories.devmod"
    );

    // ═══════════════════════════════════════════════════════════════════════════
    // EFFECTS
    // ═══════════════════════════════════════════════════════════════════════════

    public static final KeyMapping TEST_SCREEN_SHAKE_KEY = new KeyMapping(
            "key.devmod.test_shake",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_UNKNOWN,
            "key.categories.devmod"
    );

    // ═══════════════════════════════════════════════════════════════════════════
    // ABILITY SYSTEM
    // ═══════════════════════════════════════════════════════════════════════════

    public static final KeyMapping DASH_KEY = new KeyMapping(
            "key.devmod.dash",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_UNKNOWN,
            "key.categories.devmod.abilities"
    );

    public static final KeyMapping DODGE_KEY = new KeyMapping(
            "key.devmod.dodge",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_UNKNOWN,
            "key.categories.devmod.abilities"
    );

    // ═══════════════════════════════════════════════════════════════════════════
    // KEYBIND REGISTRATION
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Registers all keybindings with NeoForge.
     * Called from DevModClient during mod initialization.
     */
    public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(Objects.requireNonNull(OPEN_SETTINGS_KEY));
        event.register(Objects.requireNonNull(OPEN_WEAPON_EDITOR_KEY));
        event.register(Objects.requireNonNull(TOGGLE_DEBUG_OVERLAY_KEY));
        event.register(Objects.requireNonNull(TOGGLE_LIGHT_OVERLAY_KEY));
        event.register(Objects.requireNonNull(TOGGLE_HEATMAP_KEY));
        event.register(Objects.requireNonNull(DISMISS_IMPACT_HUD_KEY));
        event.register(Objects.requireNonNull(TOGGLE_ROOM_BOUNDS_KEY));
        event.register(Objects.requireNonNull(TOGGLE_PATHFINDING_KEY));
        event.register(Objects.requireNonNull(TOGGLE_LOS_KEY));
        event.register(Objects.requireNonNull(TOGGLE_VERTICAL_LEVELS_KEY));
        event.register(Objects.requireNonNull(TOGGLE_SAFE_SPOT_KEY));
        event.register(Objects.requireNonNull(OPEN_DASHBOARD_KEY));
        event.register(Objects.requireNonNull(TOGGLE_ATTRIBUTE_MONITOR_KEY));
        event.register(Objects.requireNonNull(TOGGLE_FPS_TRACKER_KEY));
        event.register(Objects.requireNonNull(TOGGLE_PROFILER_KEY));
        event.register(Objects.requireNonNull(OPEN_QA_TESTING_KEY));
        event.register(Objects.requireNonNull(OPEN_TESTING_HUB_KEY));
        event.register(Objects.requireNonNull(TOGGLE_BOSS_PHASE_KEY));
        event.register(Objects.requireNonNull(TOGGLE_ENTITY_DENSITY_KEY));
        event.register(Objects.requireNonNull(TOGGLE_SKILL_EFFICACY_KEY));
        event.register(Objects.requireNonNull(TOGGLE_SPAWNABILITY_KEY));
        event.register(Objects.requireNonNull(TOGGLE_QUEST_HUD_KEY));
        event.register(Objects.requireNonNull(QUEST_COMPLETE_TASK_KEY));
        event.register(Objects.requireNonNull(OPEN_QUEST_EDITOR_KEY));
        event.register(Objects.requireNonNull(TOGGLE_ECONOMY_KEY));
        event.register(Objects.requireNonNull(TOGGLE_CHUNK_PERF_KEY));
        event.register(Objects.requireNonNull(OPEN_ENDURANCE_QUEST_KEY));
        event.register(Objects.requireNonNull(QUEST_CONTINUE_KEY));
        event.register(Objects.requireNonNull(QUEST_EXIT_KEY));
        event.register(Objects.requireNonNull(OPEN_PARTY_KEY));
        event.register(Objects.requireNonNull(TOGGLE_HELP_KEY));
        event.register(Objects.requireNonNull(OPEN_RADIAL_MENU_KEY));
        event.register(Objects.requireNonNull(INSPECT_MOB_KEY));
        event.register(Objects.requireNonNull(TEST_SCREEN_SHAKE_KEY));
        event.register(Objects.requireNonNull(DASH_KEY));
        event.register(Objects.requireNonNull(DODGE_KEY));
    }
}
