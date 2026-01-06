package com.devmod.client.ui.radial;

import javax.annotation.Nullable;

import com.devmod.actions.ActionRegistry;
import com.devmod.actions.ActionType;

public final class RadialActionSafety {
    private RadialActionSafety() {}

    public enum RiskLevel {
        SAFE("safe"),
        CAUTION("risk"),
        DANGER("danger");

        private final String id;

        RiskLevel(String id) {
            this.id = id;
        }

        public String getId() {
            return id;
        }
    }

    public static RiskLevel evaluate(@Nullable RadialMenuItem item) {
        if (item == null) {
            return RiskLevel.SAFE;
        }
        String actionId = item.getAction().getRegistryId();
        if (actionId == null) {
            return RiskLevel.SAFE;
        }
        com.devmod.actions.RadialAction action = ActionRegistry.getAction(actionId);
        if (action == null) {
            return RiskLevel.SAFE;
        }

        if (action.requiresConfirm()) {
            return RiskLevel.DANGER;
        }
        ActionType actionType = action.getActionType();
        int permissionLevel = action.getPermissionLevel();

        if (permissionLevel >= 2) {
            return RiskLevel.DANGER;
        }
        if (actionType == ActionType.RUN_SERVER_COMMAND) {
            return RiskLevel.DANGER;
        }

        if (permissionLevel == 1) {
            return RiskLevel.CAUTION;
        }
        if (actionType == ActionType.OPEN_EXTERNAL || actionType == ActionType.SEND_SERVER_RPC) {
            return RiskLevel.CAUTION;
        }

        return RiskLevel.SAFE;
    }

    public static boolean shouldConfirm(@Nullable RadialMenuItem item) {
        return evaluate(item) == RiskLevel.DANGER;
    }

    public static boolean isSafe(@Nullable RadialMenuItem item) {
        return evaluate(item) == RiskLevel.SAFE;
    }
}
