package com.devmod.actions.policy;

import com.devmod.actions.ActionContext;
import com.devmod.actions.catalog.ActionSpec;

/**
 * Checks that the invoking player has the required Minecraft permission level
 * for this action. Permission level 0 means no permission check.
 *
 * <p>Replaces the scattered {@code requiresPermission} / {@code requiresPermissionOrClient}
 * precondition checks with a unified policy.
 */
public final class PermissionPolicy implements ActionPolicy {

    public static final PermissionPolicy INSTANCE = new PermissionPolicy();

    private PermissionPolicy() {}

    @Override
    public PolicyResult evaluate(ActionSpec spec, ActionContext context) {
        int requiredLevel = spec.permissionLevel();
        if (requiredLevel <= 0) {
            return PolicyResult.allow();
        }

        // Check CommandSourceStack first (most common server-side path)
        var source = context.getSource();
        if (source != null) {
            if (source.hasPermission(requiredLevel)) {
                return PolicyResult.allow();
            }
            return deny(spec, requiredLevel);
        }

        // Check player (client-side or when no source)
        var player = context.getPlayer();
        if (player != null) {
            if (player.hasPermissions(requiredLevel)) {
                return PolicyResult.allow();
            }
            return deny(spec, requiredLevel);
        }

        // No player and no source - deny
        return deny(spec, requiredLevel);
    }

    private PolicyResult deny(ActionSpec spec, int requiredLevel) {
        return PolicyResult.deny(
            "Action '" + spec.id() + "' requires permission level " + requiredLevel,
            policyName()
        );
    }

    @Override
    public String policyName() {
        return "PermissionPolicy";
    }

    @Override
    public int priority() {
        return 100;
    }
}
