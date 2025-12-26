package com.devmod.endurance;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class PrestigeMilestone {

    private final int requiredPrestige;
    private final String id;
    private final String nameKey;
    private final String descriptionKey;
    private final MilestoneType type;
    private final String unlockValue;

    /**
     * Types of milestone rewards.
     */
    public enum MilestoneType {
        PERK_SLOT("Extra Perk Slot", 0xFF55FF),
        MUTATOR_UNLOCK("Mutator Unlock", 0xFF5555),
        ARENA_UNLOCK("Arena Unlock", 0x5555FF),
        COSMETIC_TITLE("Cosmetic Title", 0xFFAA00),
        STARTING_BONUS("Starting Bonus", 0x55FF55),
        TOKEN_MULTIPLIER("Token Multiplier", 0xFFD700),
        EXCLUSIVE_PERK("Exclusive Perk", 0xFF00FF);

        public final String displayName;
        public final int color;

        MilestoneType(String displayName, int color) {
            this.displayName = displayName;
            this.color = color;
        }
    }

    public PrestigeMilestone(int requiredPrestige, String id, String nameKey,
                              String descriptionKey, MilestoneType type, String unlockValue) {
        this.requiredPrestige = requiredPrestige;
        this.id = id;
        this.nameKey = nameKey;
        this.descriptionKey = descriptionKey;
        this.type = type;
        this.unlockValue = unlockValue;
    }

    // ========== Static Milestone Definitions ==========

    private static final List<PrestigeMilestone> ALL_MILESTONES = new ArrayList<>();

    static {
        // Early game milestones (frequent, teach system)
        ALL_MILESTONES.add(new PrestigeMilestone(5,
            "prestige_5_tokens",
            "devmod.milestone.first_steps",
            "devmod.milestone.first_steps.desc",
            MilestoneType.TOKEN_MULTIPLIER,
            "1.05")); // +5% tokens

        ALL_MILESTONES.add(new PrestigeMilestone(10,
            "prestige_10_perk_slot",
            "devmod.milestone.expanded_mind",
            "devmod.milestone.expanded_mind.desc",
            MilestoneType.PERK_SLOT,
            "1")); // +1 perk slot

        ALL_MILESTONES.add(new PrestigeMilestone(15,
            "prestige_15_mutator",
            "devmod.milestone.chaos_touched",
            "devmod.milestone.chaos_touched.desc",
            MilestoneType.MUTATOR_UNLOCK,
            "double_or_nothing")); // Unlock special mutator

        // Mid game milestones
        ALL_MILESTONES.add(new PrestigeMilestone(25,
            "prestige_25_title",
            "devmod.milestone.veteran",
            "devmod.milestone.veteran.desc",
            MilestoneType.COSMETIC_TITLE,
            "Endurance Veteran"));

        ALL_MILESTONES.add(new PrestigeMilestone(35,
            "prestige_35_starting",
            "devmod.milestone.prepared",
            "devmod.milestone.prepared.desc",
            MilestoneType.STARTING_BONUS,
            "healing_surge")); // Start with healing item

        ALL_MILESTONES.add(new PrestigeMilestone(50,
            "prestige_50_arena",
            "devmod.milestone.arena_master",
            "devmod.milestone.arena_master.desc",
            MilestoneType.ARENA_UNLOCK,
            "hell_pit")); // Unlock Hell Pit arena

        // Late game milestones
        ALL_MILESTONES.add(new PrestigeMilestone(75,
            "prestige_75_perk",
            "devmod.milestone.ascendant",
            "devmod.milestone.ascendant.desc",
            MilestoneType.EXCLUSIVE_PERK,
            "phoenix_protocol")); // Exclusive comeback perk

        ALL_MILESTONES.add(new PrestigeMilestone(100,
            "prestige_100_tokens",
            "devmod.milestone.centurion",
            "devmod.milestone.centurion.desc",
            MilestoneType.TOKEN_MULTIPLIER,
            "1.15")); // +15% tokens total

        ALL_MILESTONES.add(new PrestigeMilestone(100,
            "prestige_100_title",
            "devmod.milestone.centurion_title",
            "devmod.milestone.centurion_title.desc",
            MilestoneType.COSMETIC_TITLE,
            "The Centurion"));

        ALL_MILESTONES.add(new PrestigeMilestone(150,
            "prestige_150_perk_slot",
            "devmod.milestone.awakened",
            "devmod.milestone.awakened.desc",
            MilestoneType.PERK_SLOT,
            "1")); // Second extra perk slot

        ALL_MILESTONES.add(new PrestigeMilestone(200,
            "prestige_200_arena",
            "devmod.milestone.ultimate_challenger",
            "devmod.milestone.ultimate_challenger.desc",
            MilestoneType.ARENA_UNLOCK,
            "void_sanctum")); // Unlock hardest arena

        // Endgame prestige milestones
        ALL_MILESTONES.add(new PrestigeMilestone(300,
            "prestige_300_mutator",
            "devmod.milestone.chaos_incarnate",
            "devmod.milestone.chaos_incarnate.desc",
            MilestoneType.MUTATOR_UNLOCK,
            "mayhem_mode")); // All mutators active

        ALL_MILESTONES.add(new PrestigeMilestone(500,
            "prestige_500_title",
            "devmod.milestone.legend",
            "devmod.milestone.legend.desc",
            MilestoneType.COSMETIC_TITLE,
            "Endurance Legend"));

        ALL_MILESTONES.add(new PrestigeMilestone(1000,
            "prestige_1000_all",
            "devmod.milestone.immortal",
            "devmod.milestone.immortal.desc",
            MilestoneType.EXCLUSIVE_PERK,
            "immortal_spirit")); // Ultimate perk
    }

    // ========== Static Accessors ==========

    /**
     * Get all defined milestones.
     */
    public static List<PrestigeMilestone> getAllMilestones() {
        return new ArrayList<>(ALL_MILESTONES);
    }

    /**
     * Get milestones unlocked at a given prestige level.
     */
    public static List<PrestigeMilestone> getUnlockedMilestones(int totalPrestige) {
        return ALL_MILESTONES.stream()
                .filter(m -> totalPrestige >= m.requiredPrestige)
                .toList();
    }

    /**
     * Get next milestone to work towards.
     */
    public static Optional<PrestigeMilestone> getNextMilestone(int totalPrestige) {
        return ALL_MILESTONES.stream()
                .filter(m -> totalPrestige < m.requiredPrestige)
                .findFirst();
    }

    /**
     * Get milestones newly unlocked by reaching a prestige level.
     * Used for notifications when player reaches new milestones.
     */
    public static List<PrestigeMilestone> getNewlyUnlockedMilestones(int previousPrestige, int newPrestige) {
        return ALL_MILESTONES.stream()
                .filter(m -> previousPrestige < m.requiredPrestige && newPrestige >= m.requiredPrestige)
                .toList();
    }

    /**
     * Check if a specific milestone is unlocked.
     */
    public static boolean isMilestoneUnlocked(String milestoneId, int totalPrestige) {
        return ALL_MILESTONES.stream()
                .filter(m -> m.id.equals(milestoneId))
                .anyMatch(m -> totalPrestige >= m.requiredPrestige);
    }

    /**
     * Count total perk slots from prestige milestones.
     */
    public static int getExtraPerkSlots(int totalPrestige) {
        return (int) ALL_MILESTONES.stream()
                .filter(m -> m.type == MilestoneType.PERK_SLOT && totalPrestige >= m.requiredPrestige)
                .count();
    }

    /**
     * Get token multiplier from prestige milestones.
     */
    public static float getTokenMultiplier(int totalPrestige) {
        return ALL_MILESTONES.stream()
                .filter(m -> m.type == MilestoneType.TOKEN_MULTIPLIER && totalPrestige >= m.requiredPrestige)
                .map(m -> Float.parseFloat(m.unlockValue))
                .reduce(1.0f, (a, b) -> a * b);
    }

    /**
     * Get unlocked exclusive perks from prestige milestones.
     */
    public static List<String> getUnlockedExclusivePerks(int totalPrestige) {
        return ALL_MILESTONES.stream()
                .filter(m -> m.type == MilestoneType.EXCLUSIVE_PERK && totalPrestige >= m.requiredPrestige)
                .map(m -> m.unlockValue)
                .toList();
    }

    /**
     * Get unlocked arenas from prestige milestones.
     */
    public static List<String> getUnlockedArenas(int totalPrestige) {
        return ALL_MILESTONES.stream()
                .filter(m -> m.type == MilestoneType.ARENA_UNLOCK && totalPrestige >= m.requiredPrestige)
                .map(m -> m.unlockValue)
                .toList();
    }

    /**
     * Get unlocked mutators from prestige milestones.
     */
    public static List<String> getUnlockedMutators(int totalPrestige) {
        return ALL_MILESTONES.stream()
                .filter(m -> m.type == MilestoneType.MUTATOR_UNLOCK && totalPrestige >= m.requiredPrestige)
                .map(m -> m.unlockValue)
                .toList();
    }

    /**
     * Get player's highest unlocked title.
     */
    public static Optional<String> getHighestTitle(int totalPrestige) {
        return ALL_MILESTONES.stream()
                .filter(m -> m.type == MilestoneType.COSMETIC_TITLE && totalPrestige >= m.requiredPrestige)
                .reduce((first, second) -> second) // Get last (highest prestige) title
                .map(m -> m.unlockValue);
    }

    // ========== Instance Methods ==========

    public int getRequiredPrestige() { return requiredPrestige; }
    public String getId() { return id; }
    public String getNameKey() { return nameKey; }
    public String getDescriptionKey() { return descriptionKey; }
    public MilestoneType getType() { return type; }
    public String getUnlockValue() { return unlockValue; }

    /**
     * Calculate progress percentage towards this milestone.
     */
    public float getProgress(int currentPrestige) {
        if (currentPrestige >= requiredPrestige) return 1.0f;
        return (float) currentPrestige / requiredPrestige;
    }

    /**
     * Get prestige remaining to unlock this milestone.
     */
    public int getPrestigeRemaining(int currentPrestige) {
        return Math.max(0, requiredPrestige - currentPrestige);
    }
}
