package com.devmod.actions.bindings;

import java.util.Objects;

import javax.annotation.Nullable;

import com.devmod.actions.ActionOrigin;

/**
 * Radial menu binding adapter. Represents an action accessible from the radial menu.
 */
public record RadialBinding(
    String actionId,
    String menuPath,
    @Nullable String iconItemId
) implements ActionBinding {

    public RadialBinding {
        Objects.requireNonNull(actionId, "actionId");
        Objects.requireNonNull(menuPath, "menuPath");
    }

    @Override
    public ActionOrigin origin() {
        return ActionOrigin.RADIAL;
    }

    @Override
    public String describe() {
        return "Radial[" + menuPath + " -> " + actionId + "]";
    }
}
