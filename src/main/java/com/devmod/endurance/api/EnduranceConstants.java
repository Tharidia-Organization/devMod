package com.devmod.endurance.api;

import com.devmod.endurance.EnduranceTags;

/**
 * Public API re-export of endurance constants.
 *
 * <p>External modules should import constants from here instead of
 * {@link com.devmod.endurance.EnduranceTags} directly. The original
 * class remains functional for backward compatibility.</p>
 */
public final class EnduranceConstants {

    /** NBT tag key for quest id on entities and blocks. */
    public static final String QUEST_ID = EnduranceTags.QUEST_ID;

    /** NBT tag key for arena id. */
    public static final String ARENA_ID = EnduranceTags.ARENA_ID;

    /** NBT tag key for mob id. */
    public static final String MOB_ID = EnduranceTags.MOB_ID;

    /** NBT tag key for mob id override. */
    public static final String MOB_ID_OVERRIDE = EnduranceTags.MOB_ID_OVERRIDE;

    /** NBT tag key for spawn affix. */
    public static final String AFFIX = EnduranceTags.AFFIX;

    /** NBT tag key for practice dummy marker. */
    public static final String PRACTICE_DUMMY = EnduranceTags.PRACTICE_DUMMY;

    private EnduranceConstants() {}
}
