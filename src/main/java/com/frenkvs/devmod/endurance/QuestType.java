package com.frenkvs.devmod.endurance;

/**
 * Defines the different types of Endurance Quests with scaling parameters.
 * Each type supports different player counts and difficulty scaling.
 */
public enum QuestType {
    /**
     * Standard cooperative quest for small parties.
     * Best for casual play and learning mechanics.
     */
    PVE_COOP(2, 6, 64, 1.0f, "Standard Coop", "Standard cooperative quest for 2-6 players"),

    /**
     * Raid boss encounters for larger groups.
     * Features enhanced bosses and larger arenas.
     */
    RAID_BOSS(4, 10, 96, 1.5f, "Raid Boss", "Large group boss encounters for 4-10 players"),

    /**
     * Server-wide events for massive groups.
     * Features a single massive boss with high difficulty.
     */
    EVENT(8, 20, 128, 2.0f, "Server Event", "Server-wide events for 8-20 players");

    /** Minimum players required to start this quest type */
    public final int minPlayers;

    /** Maximum players allowed in this quest type */
    public final int maxPlayers;

    /** Default arena size in blocks for this quest type */
    public final int defaultArenaSize;

    /** Difficulty multiplier applied to mob counts and boss stats */
    public final float difficultyMultiplier;

    /** Short display name for UI */
    public final String displayName;

    /** Description for tooltips and info panels */
    public final String description;

    QuestType(int minPlayers, int maxPlayers, int defaultArenaSize,
              float difficultyMultiplier, String displayName, String description) {
        this.minPlayers = minPlayers;
        this.maxPlayers = maxPlayers;
        this.defaultArenaSize = defaultArenaSize;
        this.difficultyMultiplier = difficultyMultiplier;
        this.displayName = displayName;
        this.description = description;
    }

    /**
     * Calculate scaled mob count based on player count.
     * Formula: baseMobCount * sqrt(playerCount) * difficultyMultiplier
     *
     * @param baseMobCount Base mob count for the wave
     * @param playerCount Number of players in the quest
     * @return Scaled mob count
     */
    public int getScaledMobCount(int baseMobCount, int playerCount) {
        int clampedPlayers = Math.max(1, Math.min(playerCount, maxPlayers));
        double scaleFactor = Math.sqrt(clampedPlayers) * difficultyMultiplier;
        return (int) Math.ceil(baseMobCount * scaleFactor);
    }

    /**
     * Calculate scaled boss health based on player count.
     * Formula: baseHealth * (1 + (playerCount - 1) * 0.3) * difficultyMultiplier
     *
     * @param baseHealth Base boss health
     * @param playerCount Number of players in the quest
     * @return Scaled boss health
     */
    public float getScaledBossHealth(float baseHealth, int playerCount) {
        int clampedPlayers = Math.max(1, Math.min(playerCount, maxPlayers));
        float playerScale = 1.0f + (clampedPlayers - 1) * 0.3f;
        return baseHealth * playerScale * difficultyMultiplier;
    }

    /**
     * Calculate scaled boss damage based on player count.
     * Formula: baseDamage * (1 + (playerCount - 1) * 0.1) * sqrt(difficultyMultiplier)
     * Note: Damage scales slower than health to keep fights manageable.
     *
     * @param baseDamage Base boss damage
     * @param playerCount Number of players in the quest
     * @return Scaled boss damage
     */
    public float getScaledBossDamage(float baseDamage, int playerCount) {
        int clampedPlayers = Math.max(1, Math.min(playerCount, maxPlayers));
        float playerScale = 1.0f + (clampedPlayers - 1) * 0.1f;
        return baseDamage * playerScale * (float) Math.sqrt(difficultyMultiplier);
    }

    /**
     * Check if the given player count is valid for this quest type.
     *
     * @param playerCount Number of players
     * @return true if valid, false otherwise
     */
    public boolean isValidPlayerCount(int playerCount) {
        return playerCount >= minPlayers && playerCount <= maxPlayers;
    }

    /**
     * Check if solo play is allowed for this quest type.
     * Only PVE_COOP allows solo (minPlayers check is relaxed for testing).
     *
     * @return true if solo play is allowed
     */
    public boolean allowsSoloPlay() {
        return this == PVE_COOP;
    }

    /**
     * Get the recommended arena size based on actual player count.
     * Larger groups may need slightly bigger arenas.
     *
     * @param playerCount Number of players
     * @return Recommended arena size
     */
    public int getRecommendedArenaSize(int playerCount) {
        if (playerCount <= minPlayers) {
            return defaultArenaSize;
        }
        // Scale arena slightly for very large groups
        float extraScale = 1.0f + (playerCount - minPlayers) * 0.05f;
        return (int) (defaultArenaSize * Math.min(extraScale, 1.25f));
    }

    /**
     * Get quest type by ordinal, with fallback to PVE_COOP.
     *
     * @param ordinal Enum ordinal
     * @return QuestType or PVE_COOP if invalid
     */
    public static QuestType fromOrdinal(int ordinal) {
        QuestType[] values = values();
        if (ordinal >= 0 && ordinal < values.length) {
            return values[ordinal];
        }
        return PVE_COOP;
    }

    /**
     * Get quest type by name, with fallback to PVE_COOP.
     *
     * @param name Enum name (case-insensitive)
     * @return QuestType or PVE_COOP if not found
     */
    public static QuestType fromName(String name) {
        if (name == null || name.isEmpty()) {
            return PVE_COOP;
        }
        try {
            return valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            return PVE_COOP;
        }
    }
}
