package com.devmod.endurance;

import com.devmod.endurance.api.IActionType;
import com.devmod.endurance.api.IStyleRank;
import com.devmod.endurance.config.EnduranceConfigManager;

/**
 * DMC-style combo and style ranking system - Core Types.
 *
 * <p>This class contains the core enums used throughout the combo system.
 * Session management is handled by {@link com.devmod.endurance.combat.ComboSystemFacade}.</p>
 *
 * @see com.devmod.endurance.combat.ComboSystemFacade for session management
 * @see com.devmod.endurance.combat.api.IComboSession for session interface
 */
public final class ComboSystem {

    private ComboSystem() {}

    /**
     * Style rank thresholds - DMC-inspired ranking system.
     * Implements {@link IStyleRank} so external modules can depend on the interface.
     */
    public enum StyleRank implements IStyleRank {
        D("Dull", 0, EnduranceColors.StyleRank.D, 1.0f),
        C("Crazy", 500, EnduranceColors.StyleRank.C, 1.2f),
        B("Brutal", 1500, EnduranceColors.StyleRank.B, 1.5f),
        A("Apocalyptic", 3500, EnduranceColors.StyleRank.A, 2.0f),
        S("Savage", 7000, EnduranceColors.StyleRank.S, 3.0f),
        SS("Sadistic", 12000, EnduranceColors.StyleRank.SS, 4.0f),
        SSS("Sensational", 20000, EnduranceColors.StyleRank.SSS, 5.0f);

        private final String displayName;
        private final int threshold;
        private final int color;
        private final float multiplier;

        StyleRank(String displayName, int threshold, int color, float multiplier) {
            this.displayName = displayName;
            this.threshold = threshold;
            this.color = color;
            this.multiplier = multiplier;
        }

        public String getDisplayName() { return displayName; }
        public int getThreshold() { return threshold; }
        public int getColor() { return color; }
        public float getMultiplier() { return multiplier; }

        /**
         * Get style rank from score using default thresholds.
         */
        public static StyleRank fromScore(int styleScore) {
            StyleRank result = D;
            for (StyleRank rank : values()) {
                if (styleScore >= rank.getThreshold()) {
                    result = rank;
                }
            }
            return result;
        }

        /**
         * Get style rank from score using config thresholds.
         */
        public static StyleRank fromScore(int styleScore, java.util.UUID questId) {
            if (questId == null) {
                return fromScore(styleScore);
            }

            EnduranceConfigManager config = EnduranceConfigManager.INSTANCE;

            if (styleScore >= config.getStyleRankSSSThreshold(questId)) return SSS;
            if (styleScore >= config.getStyleRankSSThreshold(questId)) return SS;
            if (styleScore >= config.getStyleRankSThreshold(questId)) return S;
            if (styleScore >= config.getStyleRankAThreshold(questId)) return A;
            if (styleScore >= config.getStyleRankBThreshold(questId)) return B;
            if (styleScore >= config.getStyleRankCThreshold(questId)) return C;
            return D;
        }

        public StyleRank getNext() {
            int idx = this.ordinal();
            StyleRank[] values = values();
            return idx < values.length - 1 ? values[idx + 1] : this;
        }

        /**
         * Network-safe rank id (currently ordinal-based).
         * Centralizes ordinal usage for easier future migration.
         */
        @SuppressWarnings("EnumOrdinal")
        public int getNetworkId() {
            return this.ordinal();
        }

        /**
         * Resolve rank from network id, clamped to valid range.
         */
        public static StyleRank fromNetworkId(int id) {
            StyleRank[] values = values();
            int safe = Math.max(0, Math.min(id, values.length - 1));
            return values[safe];
        }
    }

    /**
     * Types of combat actions that contribute to style.
     * Implements {@link IActionType} so external modules can depend on the interface.
     */
    public enum ActionType implements IActionType {
        // Basic attacks
        LIGHT_ATTACK("Light Attack", 10, 50),
        HEAVY_ATTACK("Heavy Attack", 25, 100),
        CRITICAL_HIT("Critical Hit!", 50, 150),

        // Special attacks
        AERIAL_ATTACK("Aerial!", 40, 120),
        BACKSTAB("Backstab!", 60, 180),
        HEADSHOT("Headshot!", 80, 200),

        // Defensive actions
        PERFECT_DODGE("Perfect Dodge!", 100, 250),
        PARRY("Parry!", 120, 300),
        COUNTER_ATTACK("Counter!", 150, 350),

        // Kills
        QUICK_KILL("Quick Kill!", 75, 200),
        MULTI_KILL("Multi-Kill!", 150, 400),
        OVERKILL("Overkill!", 100, 250),

        // Combos
        COMBO_5("5 Hit Combo!", 50, 100),
        COMBO_10("10 Hit Combo!", 100, 200),
        COMBO_25("25 Hit Combo!", 250, 500),
        COMBO_50("50 Hit Combo!", 500, 1000),
        COMBO_100("100 Hit Combo!", 1000, 2000),

        // Finishers
        EXECUTION("EXECUTED!", 200, 500),

        // Special
        NO_DAMAGE_WAVE("Untouchable!", 500, 1000),
        SPEED_CLEAR("Speed Demon!", 300, 600),
        VARIETY_MASTER("Variety Master!", 200, 400);

        private final String displayName;
        private final int basePoints;
        private final int stylePoints;

        ActionType(String displayName, int basePoints, int stylePoints) {
            this.displayName = displayName;
            this.basePoints = basePoints;
            this.stylePoints = stylePoints;
        }

        public String getDisplayName() { return displayName; }
        public int getBasePoints() { return basePoints; }
        public int getStylePoints() { return stylePoints; }
    }
}
