package com.frenkvs.devmod.ui.hub;

import com.frenkvs.devmod.ui.UIConstants;

/**
 * Enum per i possibili verdetti di un test.
 */
public enum Verdict {
    PASS("PASS", "1", UIConstants.Status.SUCCESS),
    FAIL("FAIL", "2", UIConstants.Status.ERROR),
    SKIP("SKIP", "3", UIConstants.Text.MUTED);

    private final String label;
    private final String hotkey;
    private final int color;

    Verdict(String label, String hotkey, int color) {
        this.label = label;
        this.hotkey = hotkey;
        this.color = color;
    }

    public String getLabel() {
        return label;
    }

    public String getHotkey() {
        return hotkey;
    }

    public int getColor() {
        return color;
    }
}
