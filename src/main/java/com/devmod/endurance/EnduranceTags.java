package com.devmod.endurance;

public final class EnduranceTags {
    public static final String QUEST_ID = "endurance_quest_id";
    public static final String ARENA_ID = "endurance_arena_id";
    public static final String MOB_ID = "endurance_mob_id";
    public static final String MOB_ID_OVERRIDE = "endurance_mob_id_override";
    public static final String AFFIX = "endurance_affix";
    public static final String PRACTICE_DUMMY = "endurance_practice_dummy";
    /**
     * Marks a mob spawned as a boss.
     *
     * <p>The value is "endurance_boss" because that is what the writers have always written
     * (BossWaveSystem and the clone-wave integration). Two readers asked for
     * "endurance_is_boss" instead, a key nothing in the repository ever wrote, so isBoss was
     * permanently false: NemesisEvolutionManager.recordBossDefeat, TideManager.onBossKilled and
     * the boss branch of the impact HUD could never fire. Three features dead over one word.
     *
     * <p>Hence this constant: the two spellings could only diverge because the key was a literal
     * repeated at five call sites.
     */
    public static final String BOSS = "endurance_boss";

    private EnduranceTags() {}
}
