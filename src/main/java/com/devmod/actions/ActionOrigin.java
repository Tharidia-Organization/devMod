package com.devmod.actions;

public enum ActionOrigin {
    RADIAL,
    KEYBIND,
    COMMAND,
    UI,
    EVENT,
    NETWORK,  // P1: Explicit origin for network-received action requests
    UNKNOWN;

    /**
     * Returns true if this origin is from a trusted local context
     * (keybind, local UI, local command).
     * Network origins are untrusted and require whitelist validation.
     */
    public boolean isTrusted() {
        return switch (this) {
            case RADIAL, KEYBIND, COMMAND, UI, EVENT -> true;
            case NETWORK, UNKNOWN -> false;
        };
    }
}
