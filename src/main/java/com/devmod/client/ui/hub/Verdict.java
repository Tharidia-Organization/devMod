package com.devmod.client.ui.hub;

import com.devmod.client.ui.editor.core.DesignTokens;

public enum Verdict {
    PASS("PASS", "1", DesignTokens.Semantic.SUCCESS),
    FAIL("FAIL", "2", DesignTokens.Semantic.ERROR),
    SKIP("SKIP", "3", DesignTokens.Text.MUTED());

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
