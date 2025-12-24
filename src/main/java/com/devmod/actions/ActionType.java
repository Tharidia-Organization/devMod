package com.devmod.actions;

/**
 * Enumerates the types of actions that can be performed from the radial menu.
 * Used for telemetry, gating, and consistent behavior patterns.
 */
public enum ActionType {
    /**
     * Opens a Minecraft Screen (client-side UI).
     * Examples: Settings screen, Item Editor, Quest Editor
     */
    NAVIGATE_SCREEN,

    /**
     * Executes a command on the server via CommandSourceStack.
     * Examples: /gamemode, /time set, /weather
     */
    RUN_SERVER_COMMAND,

    /**
     * Sends a network packet (RPC) to the server.
     * Examples: Quest start, Shop purchase, Party invite
     */
    SEND_SERVER_RPC,

    /**
     * Toggles a boolean setting (client or server-side).
     * Examples: Debug overlay, HUD visibility, Config flags
     */
    TOGGLE_SETTING,

    /**
     * Opens an external URL in the system browser.
     * Requires confirmation dialog with copy fallback.
     * Examples: Dashboard, Documentation links
     */
    OPEN_EXTERNAL,

    /**
     * Triggers a one-shot event without persistent state.
     * Examples: Ability activation, Export action, Clear heatmap
     */
    TRIGGER_EVENT,

    /**
     * Navigates to a subcategory within the radial menu.
     * Special type handled by RadialMenuScreen, not ActionRegistry.
     */
    SUBCATEGORY
}
