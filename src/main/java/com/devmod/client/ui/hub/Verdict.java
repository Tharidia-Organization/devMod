package com.devmod.client.ui.hub;

/**
 * Verdict options for test case evaluation.
 */
public enum Verdict {
    PASS("PASS", "1"),
    FAIL("FAIL", "2"),
    SKIP("SKIP", "3");

    private final String label;
    private final String hotkey;

    Verdict(String label, String hotkey) {
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
