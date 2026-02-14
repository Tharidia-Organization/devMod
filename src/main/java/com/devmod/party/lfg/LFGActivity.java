package com.devmod.party.lfg;

/**
 * Standard activity types for Looking-For-Group listings.
 */
public enum LFGActivity {
    ARENA_EASY("Arena (Easy)", 0xFF55FF55),
    ARENA_NORMAL("Arena (Normal)", 0xFFFFAA00),
    ARENA_HARD("Arena (Hard)", 0xFFFF5555),
    BOSS_FIGHT("Boss Fight", 0xFFAA00AA),
    EXPLORATION("Exploration", 0xFF55FFFF),
    QUEST("Quest", 0xFFFFFF55),
    CUSTOM("Custom", 0xFFAAAAAA);

    private final String displayName;
    private final int color;

    LFGActivity(String displayName, int color) {
        this.displayName = displayName;
        this.color = color;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getColor() {
        return color;
    }

    public static LFGActivity fromOrdinal(int ordinal) {
        LFGActivity[] values = values();
        if (ordinal >= 0 && ordinal < values.length) {
            return values[ordinal];
        }
        return CUSTOM;
    }
}
