package com.devmod.endurance;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PrestigeMilestoneTest {

    @Test
    @DisplayName("14 milestones are defined")
    void fourteenMilestonesDefined() {
        assertEquals(14, PrestigeMilestone.getAllMilestones().size());
    }

    @Test
    @DisplayName("getAllMilestones returns a defensive copy")
    void getAllMilestonesIsDefensiveCopy() {
        List<PrestigeMilestone> list1 = PrestigeMilestone.getAllMilestones();
        List<PrestigeMilestone> list2 = PrestigeMilestone.getAllMilestones();
        assertNotSame(list1, list2);
    }

    @Test
    @DisplayName("No milestones unlocked at prestige 0")
    void noMilestonesAtZero() {
        assertTrue(PrestigeMilestone.getUnlockedMilestones(0).isEmpty());
    }

    @Test
    @DisplayName("First milestone unlocks at prestige 5")
    void firstMilestoneAtFive() {
        List<PrestigeMilestone> unlocked = PrestigeMilestone.getUnlockedMilestones(5);
        assertEquals(1, unlocked.size());
        assertEquals("prestige_5_tokens", unlocked.get(0).getId());
    }

    @Test
    @DisplayName("Multiple milestones unlock at prestige 100 (tokens + title)")
    void multipleMilestonesAtSamePrestige() {
        List<PrestigeMilestone> unlocked = PrestigeMilestone.getUnlockedMilestones(100);
        // At 100: 5,10,15,25,35,50,75,100(tokens),100(title) = 9
        assertEquals(9, unlocked.size());
    }

    @Test
    @DisplayName("All milestones unlocked at prestige 1000")
    void allMilestonesAtMax() {
        assertEquals(14, PrestigeMilestone.getUnlockedMilestones(1000).size());
    }

    @Test
    @DisplayName("getNextMilestone returns first unachieved milestone")
    void nextMilestoneReturnsFirst() {
        Optional<PrestigeMilestone> next = PrestigeMilestone.getNextMilestone(0);
        assertTrue(next.isPresent());
        assertEquals(5, next.get().getRequiredPrestige());
    }

    @Test
    @DisplayName("getNextMilestone returns empty at max prestige")
    void nextMilestoneEmptyAtMax() {
        Optional<PrestigeMilestone> next = PrestigeMilestone.getNextMilestone(1000);
        assertFalse(next.isPresent());
    }

    @Test
    @DisplayName("getNewlyUnlockedMilestones detects crossing thresholds")
    void newlyUnlockedDetectsThresholdCrossing() {
        List<PrestigeMilestone> newly = PrestigeMilestone.getNewlyUnlockedMilestones(4, 10);
        // Should include prestige 5 and prestige 10
        assertEquals(2, newly.size());
    }

    @Test
    @DisplayName("getNewlyUnlockedMilestones returns empty when no new milestones")
    void newlyUnlockedEmptyWhenNone() {
        List<PrestigeMilestone> newly = PrestigeMilestone.getNewlyUnlockedMilestones(6, 9);
        assertTrue(newly.isEmpty());
    }

    @Test
    @DisplayName("isMilestoneUnlocked checks specific milestone by ID")
    void isMilestoneUnlockedById() {
        assertTrue(PrestigeMilestone.isMilestoneUnlocked("prestige_5_tokens", 5));
        assertFalse(PrestigeMilestone.isMilestoneUnlocked("prestige_5_tokens", 4));
        assertFalse(PrestigeMilestone.isMilestoneUnlocked("nonexistent_id", 1000));
    }

    @Test
    @DisplayName("getExtraPerkSlots counts PERK_SLOT milestones")
    void extraPerkSlots() {
        assertEquals(0, PrestigeMilestone.getExtraPerkSlots(0));
        assertEquals(1, PrestigeMilestone.getExtraPerkSlots(10));   // prestige_10_perk_slot
        assertEquals(2, PrestigeMilestone.getExtraPerkSlots(150));  // prestige_10 + prestige_150
    }

    @Test
    @DisplayName("getTokenMultiplier multiplies TOKEN_MULTIPLIER milestones")
    void tokenMultiplier() {
        assertEquals(1.0f, PrestigeMilestone.getTokenMultiplier(0), 0.001f);
        // At prestige 5: 1.05
        assertEquals(1.05f, PrestigeMilestone.getTokenMultiplier(5), 0.001f);
        // At prestige 100: 1.05 * 1.15 = 1.2075
        assertEquals(1.05f * 1.15f, PrestigeMilestone.getTokenMultiplier(100), 0.001f);
    }

    @Test
    @DisplayName("getUnlockedExclusivePerks returns perk IDs")
    void exclusivePerks() {
        assertTrue(PrestigeMilestone.getUnlockedExclusivePerks(0).isEmpty());
        List<String> perks = PrestigeMilestone.getUnlockedExclusivePerks(75);
        assertEquals(1, perks.size());
        assertEquals("phoenix_protocol", perks.get(0));

        List<String> allPerks = PrestigeMilestone.getUnlockedExclusivePerks(1000);
        assertEquals(2, allPerks.size());
        assertTrue(allPerks.contains("immortal_spirit"));
    }

    @Test
    @DisplayName("getUnlockedArenas returns arena IDs")
    void unlockedArenas() {
        assertTrue(PrestigeMilestone.getUnlockedArenas(0).isEmpty());
        List<String> arenas = PrestigeMilestone.getUnlockedArenas(50);
        assertEquals(1, arenas.size());
        assertEquals("hell_pit", arenas.get(0));

        List<String> allArenas = PrestigeMilestone.getUnlockedArenas(200);
        assertEquals(2, allArenas.size());
        assertTrue(allArenas.contains("void_sanctum"));
    }

    @Test
    @DisplayName("getUnlockedMutators returns mutator IDs")
    void unlockedMutators() {
        assertTrue(PrestigeMilestone.getUnlockedMutators(0).isEmpty());
        List<String> muts = PrestigeMilestone.getUnlockedMutators(15);
        assertEquals(1, muts.size());
        assertEquals("double_or_nothing", muts.get(0));
    }

    @Test
    @DisplayName("getHighestTitle returns latest earned title")
    void highestTitle() {
        assertFalse(PrestigeMilestone.getHighestTitle(0).isPresent());
        assertEquals("Endurance Veteran", PrestigeMilestone.getHighestTitle(25).orElse(""));
        assertEquals("The Centurion", PrestigeMilestone.getHighestTitle(100).orElse(""));
        assertEquals("Endurance Legend", PrestigeMilestone.getHighestTitle(500).orElse(""));
    }

    @Test
    @DisplayName("Instance getProgress returns 0.0-1.0 range")
    void instanceProgress() {
        PrestigeMilestone ms = PrestigeMilestone.getAllMilestones().get(0); // prestige 5
        assertEquals(0.0f, ms.getProgress(0), 0.001f);
        assertEquals(0.5f, ms.getProgress(2), 0.1f);
        assertEquals(1.0f, ms.getProgress(5), 0.001f);
        assertEquals(1.0f, ms.getProgress(100), 0.001f);
    }

    @Test
    @DisplayName("Instance getPrestigeRemaining returns correct value")
    void instancePrestigeRemaining() {
        PrestigeMilestone ms = PrestigeMilestone.getAllMilestones().get(0); // prestige 5
        assertEquals(5, ms.getPrestigeRemaining(0));
        assertEquals(3, ms.getPrestigeRemaining(2));
        assertEquals(0, ms.getPrestigeRemaining(5));
        assertEquals(0, ms.getPrestigeRemaining(100));
    }

    @Test
    @DisplayName("All milestone types are represented")
    void allTypesRepresented() {
        List<PrestigeMilestone> all = PrestigeMilestone.getAllMilestones();
        for (PrestigeMilestone.MilestoneType type : PrestigeMilestone.MilestoneType.values()) {
            assertTrue(all.stream().anyMatch(m -> m.getType() == type),
                "No milestone of type " + type);
        }
    }
}
