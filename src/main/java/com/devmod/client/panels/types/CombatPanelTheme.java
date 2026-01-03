package com.devmod.client.panels.types;

import com.devmod.client.ui.editor.core.DesignTokens;

/**
 * Color tokens for combat panel rendering.
 */
public final class CombatPanelTheme {
    private CombatPanelTheme() {}

    public static final class Damage {
        public static final int CRITICAL = DesignTokens.CombatPanel.Damage.CRITICAL;
        public static final int HIGH = DesignTokens.CombatPanel.Damage.HIGH;
        public static final int MEDIUM = DesignTokens.CombatPanel.Damage.MEDIUM;
        public static final int LOW = DesignTokens.CombatPanel.Damage.LOW;

        private Damage() {}
    }
}
