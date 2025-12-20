package com.devmod.arena.override;

/**
 * Scope of a template override.
 *
 * <p>Implements DD5: Override Scope - Session-based.
 */
public enum OverrideScope {
    /**
     * Override applies only to this player.
     */
    PLAYER,

    /**
     * Override applies to the entire party.
     */
    PARTY,

    /**
     * Override applies to a specific quest only.
     */
    QUEST
}
