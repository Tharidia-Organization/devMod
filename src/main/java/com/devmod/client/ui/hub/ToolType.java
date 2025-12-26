package com.devmod.client.ui.hub;

import com.devmod.actions.ActionContext;
import com.devmod.actions.ActionIds;
import com.devmod.actions.ActionOrigin;
import com.devmod.actions.ActionRegistry;
import com.devmod.actions.RadialAction;
import com.devmod.actions.client.ClientActionContexts;

public enum ToolType {
    DEBUG("Debug Overlay", "G", ActionIds.DEBUG_OVERLAY_TOGGLE),
    LIGHT_LEVEL("Light Levels", "L", ActionIds.DEBUG_LIGHT_OVERLAY_TOGGLE),
    HEATMAP("Heatmap", "H", ActionIds.DEBUG_HEATMAP_TOGGLE),
    ROOM_BOUNDS("Room Bounds", "R", ActionIds.DEBUG_ROOM_BOUNDS_TOGGLE),
    PATHFINDING("Pathfinding", "P", ActionIds.DEBUG_PATHFINDING_TOGGLE),
    LINE_OF_SIGHT("Line of Sight", "V", ActionIds.DEBUG_LOS_TOGGLE),
    VERTICAL_LEVELS("Vertical Levels", "Y", ActionIds.DEBUG_VERTICAL_LEVELS_TOGGLE),
    SAFE_SPOTS("Safe Spots", "C", ActionIds.DEBUG_SAFE_SPOTS_TOGGLE);

    private final String label;
    private final String hotkey;
    private final String actionId;

    ToolType(String label, String hotkey, String actionId) {
        this.label = label;
        this.hotkey = hotkey;
        this.actionId = actionId;
    }

    public String getLabel() {
        return label;
    }

    public String getHotkey() {
        return hotkey;
    }

    public boolean isEnabled() {
        RadialAction action = ActionRegistry.getAction(actionId);
        if (action == null) {
            return false;
        }
        return action.isActive(clientContext());
    }

    public void setEnabled(boolean enabled) {
        if (enabled != isEnabled()) {
            ActionRegistry.invoke(actionId, clientContext());
        }
    }

    public void toggle() {
        ActionRegistry.invoke(actionId, clientContext());
    }

    private static ActionContext clientContext() {
        return ClientActionContexts.forClient(ActionOrigin.UI);
    }
}
