package com.devmod.endurance.api;

import com.devmod.endurance.QuestType;

/**
 * Public API re-export of quest type constants.
 *
 * <p>Provides stable references to {@link QuestType} values so external
 * modules do not need to import from the endurance root package.
 * The original enum remains functional for backward compatibility.</p>
 */
public final class QuestTypes {

    /** Standard cooperative quest for small parties (2-6 players). */
    public static final QuestType PVE_COOP = QuestType.PVE_COOP;

    /** Raid boss encounters for larger groups (4-10 players). */
    public static final QuestType RAID_BOSS = QuestType.RAID_BOSS;

    /** Server-wide events for massive groups (8-20 players). */
    public static final QuestType EVENT = QuestType.EVENT;

    /**
     * Parse a quest type from name, returning PVE_COOP for invalid input.
     * Delegates to {@link QuestType#fromName(String)}.
     */
    public static QuestType fromName(String name) {
        return QuestType.fromName(name);
    }

    /**
     * Parse a quest type from ordinal, returning PVE_COOP for invalid input.
     * Delegates to {@link QuestType#fromOrdinal(int)}.
     */
    public static QuestType fromOrdinal(int ordinal) {
        return QuestType.fromOrdinal(ordinal);
    }

    private QuestTypes() {}
}
