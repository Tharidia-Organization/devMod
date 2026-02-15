package com.devmod.quest;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.devmod.TestBootstrap;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Items;

@DisplayName("QuestProgress")
class QuestProgressTest {

    @BeforeAll
    static void bootstrap() {
        TestBootstrap.init();
    }

    private QuestProgress createProgressWithKillObjectives() {
        return new QuestProgress("test_quest", List.of(
            new QuestObjective.Kill("kill_zombies", EntityType.ZOMBIE, 10, 0),
            new QuestObjective.Kill("kill_skeletons", EntityType.SKELETON, 5, 0)
        ));
    }

    // ========================================================================
    // Construction
    // ========================================================================

    @Nested
    @DisplayName("Construction")
    class ConstructionTests {

        @Test
        @DisplayName("Creates with quest ID and objectives")
        void createsWithIdAndObjectives() {
            QuestProgress progress = createProgressWithKillObjectives();
            assertEquals("test_quest", progress.getQuestId());
            assertEquals(2, progress.getObjectives().size());
        }

        @Test
        @DisplayName("New progress starts as NOT_STARTED")
        void startsAsNotStarted() {
            QuestProgress progress = createProgressWithKillObjectives();
            assertEquals(QuestStatus.NOT_STARTED, progress.getStatus());
        }

        @Test
        @DisplayName("New progress is not tracked")
        void notTrackedInitially() {
            QuestProgress progress = createProgressWithKillObjectives();
            assertFalse(progress.isTracked());
        }

        @Test
        @DisplayName("Start and complete ticks are zero initially")
        void ticksAreZeroInitially() {
            QuestProgress progress = createProgressWithKillObjectives();
            assertEquals(0, progress.getStartedAtTick());
            assertEquals(0, progress.getCompletedAtTick());
        }
    }

    // ========================================================================
    // Objective Management
    // ========================================================================

    @Nested
    @DisplayName("Objective Management")
    class ObjectiveManagementTests {

        @Test
        @DisplayName("getObjective finds by ID")
        void getObjectiveFindsById() {
            QuestProgress progress = createProgressWithKillObjectives();
            QuestObjective found = progress.getObjective("kill_zombies");
            assertNotNull(found);
            assertEquals("kill_zombies", found.id());
        }

        @Test
        @DisplayName("getObjective returns null for unknown ID")
        void getObjectiveReturnsNullForUnknown() {
            QuestProgress progress = createProgressWithKillObjectives();
            assertNull(progress.getObjective("nonexistent"));
        }

        @Test
        @DisplayName("updateObjective replaces objective by ID")
        void updateObjectiveReplacesById() {
            QuestProgress progress = createProgressWithKillObjectives();
            QuestObjective.Kill updated = new QuestObjective.Kill("kill_zombies", EntityType.ZOMBIE, 10, 5);
            progress.updateObjective("kill_zombies", updated);

            QuestObjective found = progress.getObjective("kill_zombies");
            assertNotNull(found);
            assertInstanceOf(QuestObjective.Kill.class, found);
            assertEquals(5, ((QuestObjective.Kill) found).current());
        }

        @Test
        @DisplayName("updateObjective with unknown ID does nothing")
        void updateObjectiveUnknownIdNoOp() {
            QuestProgress progress = createProgressWithKillObjectives();
            QuestObjective.Kill unknown = new QuestObjective.Kill("unknown", EntityType.ZOMBIE, 1, 1);
            progress.updateObjective("unknown", unknown);
            // Should not throw, and original objectives remain intact
            assertEquals(2, progress.getObjectives().size());
        }
    }

    // ========================================================================
    // Overall Progress
    // ========================================================================

    @Nested
    @DisplayName("Overall Progress")
    class OverallProgressTests {

        @Test
        @DisplayName("0.0 progress for fresh objectives")
        void zeroProgressForFresh() {
            QuestProgress progress = createProgressWithKillObjectives();
            assertEquals(0f, progress.getOverallProgress(), 0.001f);
        }

        @Test
        @DisplayName("Partial progress calculated correctly")
        void partialProgressCalculated() {
            QuestProgress progress = createProgressWithKillObjectives();
            // Update first objective to 5/10 (50%)
            progress.updateObjective("kill_zombies",
                new QuestObjective.Kill("kill_zombies", EntityType.ZOMBIE, 10, 5));
            // Average: (0.5 + 0.0) / 2 = 0.25
            assertEquals(0.25f, progress.getOverallProgress(), 0.001f);
        }

        @Test
        @DisplayName("Full progress when all complete")
        void fullProgressWhenAllComplete() {
            QuestProgress progress = createProgressWithKillObjectives();
            progress.updateObjective("kill_zombies",
                new QuestObjective.Kill("kill_zombies", EntityType.ZOMBIE, 10, 10));
            progress.updateObjective("kill_skeletons",
                new QuestObjective.Kill("kill_skeletons", EntityType.SKELETON, 5, 5));
            assertEquals(1.0f, progress.getOverallProgress(), 0.001f);
        }

        @Test
        @DisplayName("Empty objectives returns 0")
        void emptyObjectivesReturnsZero() {
            QuestProgress progress = new QuestProgress("empty", List.of());
            assertEquals(0f, progress.getOverallProgress(), 0.001f);
        }
    }

    // ========================================================================
    // Completion Check
    // ========================================================================

    @Nested
    @DisplayName("Completion Check")
    class CompletionCheckTests {

        @Test
        @DisplayName("Not complete when no objectives done")
        void notCompleteWhenNoneDone() {
            QuestProgress progress = createProgressWithKillObjectives();
            assertFalse(progress.areAllObjectivesComplete());
        }

        @Test
        @DisplayName("Not complete when only some objectives done")
        void notCompleteWhenPartial() {
            QuestProgress progress = createProgressWithKillObjectives();
            progress.updateObjective("kill_zombies",
                new QuestObjective.Kill("kill_zombies", EntityType.ZOMBIE, 10, 10));
            assertFalse(progress.areAllObjectivesComplete());
        }

        @Test
        @DisplayName("Complete when all objectives done")
        void completeWhenAllDone() {
            QuestProgress progress = createProgressWithKillObjectives();
            progress.updateObjective("kill_zombies",
                new QuestObjective.Kill("kill_zombies", EntityType.ZOMBIE, 10, 10));
            progress.updateObjective("kill_skeletons",
                new QuestObjective.Kill("kill_skeletons", EntityType.SKELETON, 5, 5));
            assertTrue(progress.areAllObjectivesComplete());
        }

        @Test
        @DisplayName("Empty objectives list is not complete")
        void emptyIsNotComplete() {
            QuestProgress progress = new QuestProgress("empty", List.of());
            assertFalse(progress.areAllObjectivesComplete());
        }
    }

    // ========================================================================
    // Completed Count & Summary
    // ========================================================================

    @Nested
    @DisplayName("Completed Count & Summary")
    class CompletedCountTests {

        @Test
        @DisplayName("getCompletedObjectiveCount counts correctly")
        void countsCorrectly() {
            QuestProgress progress = createProgressWithKillObjectives();
            assertEquals(0, progress.getCompletedObjectiveCount());

            progress.updateObjective("kill_zombies",
                new QuestObjective.Kill("kill_zombies", EntityType.ZOMBIE, 10, 10));
            assertEquals(1, progress.getCompletedObjectiveCount());
        }

        @Test
        @DisplayName("getProgressSummary format")
        void progressSummaryFormat() {
            QuestProgress progress = createProgressWithKillObjectives();
            assertEquals("0/2 objectives", progress.getProgressSummary());

            progress.updateObjective("kill_zombies",
                new QuestObjective.Kill("kill_zombies", EntityType.ZOMBIE, 10, 10));
            assertEquals("1/2 objectives", progress.getProgressSummary());
        }
    }

    // ========================================================================
    // Status & Tracking
    // ========================================================================

    @Nested
    @DisplayName("Status & Tracking")
    class StatusTrackingTests {

        @Test
        @DisplayName("setStatus changes status")
        void setStatusChanges() {
            QuestProgress progress = createProgressWithKillObjectives();
            progress.setStatus(QuestStatus.ACTIVE);
            assertEquals(QuestStatus.ACTIVE, progress.getStatus());
        }

        @Test
        @DisplayName("setTracked changes tracking state")
        void setTrackedChanges() {
            QuestProgress progress = createProgressWithKillObjectives();
            progress.setTracked(true);
            assertTrue(progress.isTracked());
        }

        @Test
        @DisplayName("setStartedAtTick stores tick")
        void setStartedAtTickStores() {
            QuestProgress progress = createProgressWithKillObjectives();
            progress.setStartedAtTick(1000L);
            assertEquals(1000L, progress.getStartedAtTick());
        }

        @Test
        @DisplayName("setCompletedAtTick stores tick")
        void setCompletedAtTickStores() {
            QuestProgress progress = createProgressWithKillObjectives();
            progress.setCompletedAtTick(5000L);
            assertEquals(5000L, progress.getCompletedAtTick());
        }
    }
}
