package com.devmod.endurance.api;

/**
 * Public API interface for combat action types.
 *
 * <p>Implemented by {@link com.devmod.endurance.ComboSystem.ActionType}.
 * External modules should depend on this interface rather than the concrete enum
 * when only read access is needed.</p>
 */
public interface IActionType {

    /** Human-readable display name (e.g. "Perfect Dodge!"). */
    String getDisplayName();

    /** Base score points awarded for this action. */
    int getBasePoints();

    /** Style score points awarded for this action. */
    int getStylePoints();
}
