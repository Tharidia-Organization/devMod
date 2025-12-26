package com.devmod.client.arena.hud;

import java.util.UUID;

import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

import net.neoforged.neoforge.client.settings.KeyConflictContext;

import com.devmod.arena.ArenaDebugState;
public class ArenaHudKeyBinding {

    private static final Logger LOGGER = LoggerFactory.getLogger(ArenaHudKeyBinding.class);

    /**
     * Keybind to toggle the Arena Debug HUD.
     * Uses Shift+H to avoid conflict with H (Heatmap toggle).
     */
    public static final KeyMapping TOGGLE_ARENA_HUD_KEY = new KeyMapping(
            "key.devmod.arena_debug_hud",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_F7,  // F7 as primary, will check for Shift modifier
            "key.categories.devmod.arena"
    );

    private ArenaHudKeyBinding() {}

    /**
     * Handles the key press event for the Arena Debug HUD toggle.
     * Should be called from the input event handler.
     *
     * @return true if the key was consumed, false otherwise
     */
    public static boolean handleKeyPress() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return false;
        }

        // Check if our keybind was pressed with Shift modifier
        if (TOGGLE_ARENA_HUD_KEY.consumeClick()) {
            long windowHandle = mc.getWindow().getWindow();
            boolean shiftHeld = GLFW.glfwGetKey(windowHandle, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS ||
                                GLFW.glfwGetKey(windowHandle, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;

            // Only toggle if Shift is held (to differentiate from Testing Hub F7)
            if (shiftHeld && mc.player != null) {
                UUID playerId = mc.player.getUUID();
                boolean newState = ArenaDebugState.getInstance().toggleHud(playerId);

                // Send chat feedback to player
                String status = newState ? "§aENABLED" : "§cDISABLED";
                LOGGER.info("Arena Debug HUD {} for player {}", status, playerId);

                // Return true to indicate key was consumed
                return true;
            }
        }

        return false;
    }

    /**
     * Programmatically toggles the HUD for a player.
     *
     * @param playerId The player's UUID
     * @return The new state (true = enabled)
     */
    public static boolean toggle(UUID playerId) {
        return ArenaDebugState.getInstance().toggleHud(playerId);
    }

    /**
     * Enables the HUD for a player.
     *
     * @param playerId The player's UUID
     */
    public static void enable(UUID playerId) {
        ArenaDebugState.getInstance().enableHud(playerId);
    }

    /**
     * Disables the HUD for a player.
     *
     * @param playerId The player's UUID
     */
    public static void disable(UUID playerId) {
        ArenaDebugState.getInstance().disableHud(playerId);
    }

    /**
     * Checks if the HUD is enabled for a player.
     *
     * @param playerId The player's UUID
     * @return true if enabled
     */
    public static boolean isEnabled(UUID playerId) {
        return ArenaDebugState.getInstance().isHudEnabled(playerId);
    }
}
