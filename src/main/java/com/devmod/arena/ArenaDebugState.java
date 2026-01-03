package com.devmod.arena;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ArenaDebugState {

    /** Permission required to see the debug HUD */
    public static final String PERMISSION_VIEW_HUD = "devmod.arena.debug.hud";

    /** Per-player HUD enabled state (default: OFF) */
    private final Map<UUID, Boolean> hudEnabledState = new ConcurrentHashMap<>();

    /** Global HUD enabled flag (server-wide disable) */
    private volatile boolean globalHudEnabled = true;

    private ArenaDebugState() {}

    public static ArenaDebugState getInstance() {
        return Holder.INSTANCE;
    }

    private static final class Holder {
        private static final ArenaDebugState INSTANCE = new ArenaDebugState();
    }

    /**
     * Checks if the HUD is enabled for a specific player.
     * Default is OFF - player must explicitly enable.
     *
     * @param playerId The player's UUID
     * @return true if HUD is enabled for this player AND globally
     */
    public boolean isHudEnabled(UUID playerId) {
        if (!globalHudEnabled) {
            return false;
        }
        return hudEnabledState.getOrDefault(playerId, false);
    }

    /**
     * Enables the debug HUD for a player
     *
     * @param playerId The player's UUID
     */
    public void enableHud(UUID playerId) {
        hudEnabledState.put(playerId, true);
    }

    /**
     * Disables the debug HUD for a player
     *
     * @param playerId The player's UUID
     */
    public void disableHud(UUID playerId) {
        hudEnabledState.put(playerId, false);
    }

    /**
     * Toggles the debug HUD for a player
     *
     * @param playerId The player's UUID
     * @return The new state (true = enabled)
     */
    public boolean toggleHud(UUID playerId) {
        boolean newState = !isHudEnabled(playerId);
        hudEnabledState.put(playerId, newState);
        return newState;
    }

    /**
     * Gets the global HUD enabled state
     *
     * @return true if HUD is globally enabled
     */
    public boolean isGlobalHudEnabled() {
        return globalHudEnabled;
    }

    /**
     * Sets the global HUD enabled state.
     * When false, no player can see the HUD regardless of their personal setting.
     *
     * @param enabled true to enable globally
     */
    public void setGlobalHudEnabled(boolean enabled) {
        this.globalHudEnabled = enabled;
    }

    /**
     * Clears the HUD state for a player (e.g., on logout)
     *
     * @param playerId The player's UUID
     */
    public void clearPlayerState(UUID playerId) {
        hudEnabledState.remove(playerId);
    }

    /**
     * Gets the count of players with HUD enabled
     */
    public int getEnabledPlayerCount() {
        return (int) hudEnabledState.values().stream().filter(Boolean::booleanValue).count();
    }

    /**
     * Resets all player HUD states (e.g., on server restart)
     */
    public void resetAllStates() {
        hudEnabledState.clear();
    }
}
