package com.devmod.quest;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.devmod.TestBootstrap;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Items;

@DisplayName("QuestObjective")
class QuestObjectiveTest {

    @BeforeAll
    static void bootstrap() {
        TestBootstrap.init();
    }

    // ========================================================================
    // Kill Objective
    // ========================================================================

    @Nested
    @DisplayName("Kill")
    class KillTests {

        @Test
        @DisplayName("Not complete when current < required")
        void notCompleteWhenLess() {
            var kill = new QuestObjective.Kill("k1", EntityType.ZOMBIE, 10, 5);
            assertFalse(kill.isComplete());
        }

        @Test
        @DisplayName("Complete when current >= required")
        void completeWhenAtRequired() {
            var kill = new QuestObjective.Kill("k1", EntityType.ZOMBIE, 10, 10);
            assertTrue(kill.isComplete());
        }

        @Test
        @DisplayName("Complete when current exceeds required")
        void completeWhenExceeds() {
            var kill = new QuestObjective.Kill("k1", EntityType.ZOMBIE, 10, 15);
            assertTrue(kill.isComplete());
        }

        @Test
        @DisplayName("Progress calculates correctly")
        void progressCalculates() {
            var kill = new QuestObjective.Kill("k1", EntityType.ZOMBIE, 10, 5);
            assertEquals(0.5f, kill.progress(), 0.001f);
        }

        @Test
        @DisplayName("Progress capped at 1.0")
        void progressCappedAtOne() {
            var kill = new QuestObjective.Kill("k1", EntityType.ZOMBIE, 10, 20);
            assertEquals(1.0f, kill.progress(), 0.001f);
        }

        @Test
        @DisplayName("Progress 1.0 for zero required")
        void progressOneForZeroRequired() {
            var kill = new QuestObjective.Kill("k1", EntityType.ZOMBIE, 0, 0);
            assertEquals(1.0f, kill.progress(), 0.001f);
        }

        @Test
        @DisplayName("increment creates new with current+1")
        void incrementCreatesNew() {
            var kill = new QuestObjective.Kill("k1", EntityType.ZOMBIE, 10, 5);
            var incremented = kill.increment();
            assertEquals(6, incremented.current());
            assertEquals(5, kill.current()); // original unchanged
        }

        @Test
        @DisplayName("withCurrent creates new with given current")
        void withCurrentCreatesNew() {
            var kill = new QuestObjective.Kill("k1", EntityType.ZOMBIE, 10, 0);
            var updated = kill.withCurrent(7);
            assertEquals(7, updated.current());
            assertEquals(0, kill.current());
        }
    }

    // ========================================================================
    // Collect Objective
    // ========================================================================

    @Nested
    @DisplayName("Collect")
    class CollectTests {

        @Test
        @DisplayName("Not complete when current < required")
        void notCompleteWhenLess() {
            var collect = new QuestObjective.Collect("c1", Items.DIAMOND, 32, 10);
            assertFalse(collect.isComplete());
        }

        @Test
        @DisplayName("Complete when current >= required")
        void completeWhenAtRequired() {
            var collect = new QuestObjective.Collect("c1", Items.DIAMOND, 32, 32);
            assertTrue(collect.isComplete());
        }

        @Test
        @DisplayName("Progress calculates correctly")
        void progressCalculates() {
            var collect = new QuestObjective.Collect("c1", Items.DIAMOND, 20, 10);
            assertEquals(0.5f, collect.progress(), 0.001f);
        }

        @Test
        @DisplayName("increment adds amount")
        void incrementAddsAmount() {
            var collect = new QuestObjective.Collect("c1", Items.DIAMOND, 32, 10);
            var incremented = collect.increment(5);
            assertEquals(15, incremented.current());
        }

        @Test
        @DisplayName("withCurrent creates copy with new value")
        void withCurrentCreates() {
            var collect = new QuestObjective.Collect("c1", Items.DIAMOND, 32, 0);
            var updated = collect.withCurrent(20);
            assertEquals(20, updated.current());
        }
    }

    // ========================================================================
    // Visit Objective
    // ========================================================================

    @Nested
    @DisplayName("Visit")
    class VisitTests {

        @Test
        @DisplayName("Not complete when not visited")
        void notCompleteWhenNotVisited() {
            var visit = new QuestObjective.Visit("v1", new BlockPos(100, 65, 200), 10, false);
            assertFalse(visit.isComplete());
            assertEquals(0.0f, visit.progress(), 0.001f);
        }

        @Test
        @DisplayName("Complete when visited")
        void completeWhenVisited() {
            var visit = new QuestObjective.Visit("v1", new BlockPos(100, 65, 200), 10, true);
            assertTrue(visit.isComplete());
            assertEquals(1.0f, visit.progress(), 0.001f);
        }

        @Test
        @DisplayName("markVisited creates visited copy")
        void markVisitedCreatesCopy() {
            var visit = new QuestObjective.Visit("v1", new BlockPos(100, 65, 200), 10, false);
            var visited = visit.markVisited();
            assertTrue(visited.visited());
            assertFalse(visit.visited()); // original unchanged
        }
    }

    // ========================================================================
    // Interact Objective
    // ========================================================================

    @Nested
    @DisplayName("Interact")
    class InteractTests {

        @Test
        @DisplayName("Not complete when not interacted")
        void notCompleteWhenNotInteracted() {
            var interact = new QuestObjective.Interact("i1", "npc_1", "Bob", false);
            assertFalse(interact.isComplete());
            assertEquals(0.0f, interact.progress(), 0.001f);
        }

        @Test
        @DisplayName("Complete when interacted")
        void completeWhenInteracted() {
            var interact = new QuestObjective.Interact("i1", "npc_1", "Bob", true);
            assertTrue(interact.isComplete());
            assertEquals(1.0f, interact.progress(), 0.001f);
        }

        @Test
        @DisplayName("markInteracted creates interacted copy")
        void markInteractedCreatesCopy() {
            var interact = new QuestObjective.Interact("i1", "npc_1", "Bob", false);
            var interacted = interact.markInteracted();
            assertTrue(interacted.interacted());
            assertFalse(interact.interacted());
        }
    }

    // ========================================================================
    // CraftItem Objective
    // ========================================================================

    @Nested
    @DisplayName("CraftItem")
    class CraftItemTests {

        @Test
        @DisplayName("Not complete when crafted < required")
        void notCompleteWhenLess() {
            var craft = new QuestObjective.CraftItem("cr1", Items.DIAMOND_SWORD, 1, 0);
            assertFalse(craft.isComplete());
        }

        @Test
        @DisplayName("Complete when crafted >= required")
        void completeWhenAtRequired() {
            var craft = new QuestObjective.CraftItem("cr1", Items.DIAMOND_SWORD, 1, 1);
            assertTrue(craft.isComplete());
        }

        @Test
        @DisplayName("Progress calculates correctly")
        void progressCalculates() {
            var craft = new QuestObjective.CraftItem("cr1", Items.IRON_SWORD, 4, 2);
            assertEquals(0.5f, craft.progress(), 0.001f);
        }

        @Test
        @DisplayName("increment adds amount")
        void incrementAdds() {
            var craft = new QuestObjective.CraftItem("cr1", Items.DIAMOND_SWORD, 3, 1);
            var incremented = craft.increment(2);
            assertEquals(3, incremented.crafted());
        }

        @Test
        @DisplayName("withCrafted creates copy")
        void withCraftedCreatesCopy() {
            var craft = new QuestObjective.CraftItem("cr1", Items.DIAMOND_SWORD, 3, 0);
            var updated = craft.withCrafted(2);
            assertEquals(2, updated.crafted());
        }
    }

    // ========================================================================
    // ReachLevel Objective
    // ========================================================================

    @Nested
    @DisplayName("ReachLevel")
    class ReachLevelTests {

        @Test
        @DisplayName("Not complete when below target")
        void notCompleteWhenBelow() {
            var level = new QuestObjective.ReachLevel("l1", 30, 15);
            assertFalse(level.isComplete());
        }

        @Test
        @DisplayName("Complete when at target")
        void completeWhenAtTarget() {
            var level = new QuestObjective.ReachLevel("l1", 30, 30);
            assertTrue(level.isComplete());
        }

        @Test
        @DisplayName("Complete when above target")
        void completeWhenAboveTarget() {
            var level = new QuestObjective.ReachLevel("l1", 30, 50);
            assertTrue(level.isComplete());
        }

        @Test
        @DisplayName("Progress calculates correctly")
        void progressCalculates() {
            var level = new QuestObjective.ReachLevel("l1", 20, 10);
            assertEquals(0.5f, level.progress(), 0.001f);
        }

        @Test
        @DisplayName("Progress 1.0 for zero target")
        void progressOneForZeroTarget() {
            var level = new QuestObjective.ReachLevel("l1", 0, 0);
            assertEquals(1.0f, level.progress(), 0.001f);
        }

        @Test
        @DisplayName("withLevel creates copy")
        void withLevelCreatesCopy() {
            var level = new QuestObjective.ReachLevel("l1", 30, 0);
            var updated = level.withLevel(25);
            assertEquals(25, updated.currentLevel());
            assertEquals(0, level.currentLevel());
        }
    }
}
