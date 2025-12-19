package com.frenkvs.devmod.ui.hub;

/**
 * Enum che rappresenta gli editor disponibili nel TestingHub.
 */
public enum EditorType {
    WEAPON("Weapon Editor", "M"),
    MOB_CONFIG("Mob Config", "Click mob");

    private final String label;
    private final String hotkey;

    EditorType(String label, String hotkey) {
        this.label = label;
        this.hotkey = hotkey;
    }

    public String getLabel() {
        return label;
    }

    public String getHotkey() {
        return hotkey;
    }
}
