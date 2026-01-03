package com.devmod.client.endurance;

import com.devmod.client.ui.editor.core.DesignTokens;

/**
 * Centralized color tokens for endurance client UI.
 */
public final class EnduranceUiTheme {
    private EnduranceUiTheme() {}

    public static final class Accent {
        public static final int ORANGE = DesignTokens.EnduranceUi.Accent.ORANGE;
        public static final int PURPLE = DesignTokens.EnduranceUi.Accent.PURPLE;
        public static final int GOLD = DesignTokens.EnduranceUi.Accent.GOLD;
        public static final int GOLD_RGB = DesignTokens.EnduranceUi.Accent.GOLD_RGB;

        private Accent() {}
    }

    public static final class QuestTier {
        public static final int HARD = DesignTokens.EnduranceUi.QuestTier.HARD;
        public static final int ELITE = DesignTokens.EnduranceUi.QuestTier.ELITE;

        private QuestTier() {}
    }

    public static final class Challenge {
        public static final int HARD = DesignTokens.EnduranceUi.Challenge.HARD;

        private Challenge() {}
    }

    public static final class DeathScreen {
        public static final int BG = DesignTokens.EnduranceUi.DeathScreen.BG;
        public static final int PANEL_BG = DesignTokens.EnduranceUi.DeathScreen.PANEL_BG;

        private DeathScreen() {}
    }

    public static final class CompletionScreen {
        public static final int BACKDROP_RGB = DesignTokens.EnduranceUi.CompletionScreen.BACKDROP_RGB;
        public static final int PANEL_RGB = DesignTokens.EnduranceUi.CompletionScreen.PANEL_RGB;
        public static final int GOLD_RGB = DesignTokens.EnduranceUi.CompletionScreen.GOLD_RGB;

        private CompletionScreen() {}
    }

    public static final class PerkSelection {
        public static final int TAG_REQUIRED = DesignTokens.EnduranceUi.PerkSelection.TAG_REQUIRED;
        public static final int TAG_OPTIONAL = DesignTokens.EnduranceUi.PerkSelection.TAG_OPTIONAL;

        public static final int SYNERGY_COMPLETE = DesignTokens.EnduranceUi.PerkSelection.SYNERGY_COMPLETE;
        public static final int SYNERGY_STRONG = DesignTokens.EnduranceUi.PerkSelection.SYNERGY_STRONG;
        public static final int SYNERGY_MODERATE = DesignTokens.EnduranceUi.PerkSelection.SYNERGY_MODERATE;
        public static final int SYNERGY_MINOR = DesignTokens.EnduranceUi.PerkSelection.SYNERGY_MINOR;

        private PerkSelection() {}
    }

    public static final class KitSelection {
        public static final int ACCENT_PURPLE = DesignTokens.EnduranceUi.KitSelection.ACCENT_PURPLE;
        public static final int BTN_SUCCESS_HOVER = DesignTokens.EnduranceUi.KitSelection.BTN_SUCCESS_HOVER;
        public static final int BTN_SUCCESS_BORDER_HOVER = DesignTokens.EnduranceUi.KitSelection.BTN_SUCCESS_BORDER_HOVER;
        public static final int BTN_SUCCESS_BORDER = DesignTokens.EnduranceUi.KitSelection.BTN_SUCCESS_BORDER;
        public static final int SCRIM = DesignTokens.EnduranceUi.KitSelection.SCRIM;

        private KitSelection() {}
    }

    public static final class KitCategory {
        public static final int ALL = DesignTokens.EnduranceUi.KitCategory.ALL;
        public static final int ARMOR = DesignTokens.EnduranceUi.KitCategory.ARMOR;
        public static final int WEAPONS = DesignTokens.EnduranceUi.KitCategory.WEAPONS;
        public static final int TOOLS = DesignTokens.EnduranceUi.KitCategory.TOOLS;
        public static final int POTIONS = DesignTokens.EnduranceUi.KitCategory.POTIONS;
        public static final int FOOD = DesignTokens.EnduranceUi.KitCategory.FOOD;
        public static final int COMBAT = DesignTokens.EnduranceUi.KitCategory.COMBAT;
        public static final int BLOCKS = DesignTokens.EnduranceUi.KitCategory.BLOCKS;

        private KitCategory() {}
    }
}
