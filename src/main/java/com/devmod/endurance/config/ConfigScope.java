package com.devmod.endurance.config;

/**
 * Defines the scope for config changes in the Endurance system.
 */
public enum ConfigScope {
    /**
     * Global changes - requires OP level 2+.
     * Permanently modifies server config file (devmod-mechanics.toml).
     * Takes effect immediately for all future sessions.
     */
    GLOBAL,

    /**
     * Proposal for global changes - available to testers.
     * Saved to pending queue for admin approval.
     * Does not take effect until approved by OP.
     */
    PROPOSAL,

    /**
     * Session-only override - available to quest host.
     * Only affects the current quest session.
     * Resets when quest ends.
     */
    SESSION
}
