package com.devmod.debug;

import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for debug payload records: DebugSyncPayload, DebugTogglePayload,
 * EntityGoalsPayload, EntityPathingPayload, RaidsPayload, POIPayload.
 * Tests record construction, getFeature() mapping, estimatedSize, and clear() factories.
 */
@DisplayName("Debug Payloads")
class DebugPayloadTest {

    // ==========================================
    // DebugSyncPayload
    // ==========================================

    @Nested
    @DisplayName("DebugSyncPayload")
    class DebugSyncPayloadTests {

        @Test
        @DisplayName("Record stores featureId and enabled")
        void recordFields() {
            DebugSyncPayload payload = new DebugSyncPayload("entity_pathing", true);
            assertEquals("entity_pathing", payload.featureId());
            assertTrue(payload.enabled());
        }

        @ParameterizedTest
        @EnumSource(DebugFeature.class)
        @DisplayName("getFeature() resolves all DebugFeature values")
        void getFeatureResolvesAll(DebugFeature feature) {
            DebugSyncPayload payload = new DebugSyncPayload(feature.getId(), true);
            assertEquals(feature, payload.getFeature());
        }

        @Test
        @DisplayName("getFeature() returns null for unknown feature ID")
        void getFeatureReturnsNullForUnknown() {
            DebugSyncPayload payload = new DebugSyncPayload("nonexistent_feature", true);
            assertNull(payload.getFeature());
        }

        @Test
        @DisplayName("getFeature() is case-insensitive via toUpperCase")
        void getFeatureCaseInsensitive() {
            DebugSyncPayload payload = new DebugSyncPayload("ENTITY_PATHING", true);
            assertEquals(DebugFeature.ENTITY_PATHING, payload.getFeature());
        }

        @Test
        @DisplayName("estimatedSize is positive")
        void estimatedSizePositive() {
            DebugSyncPayload payload = new DebugSyncPayload("poi", false);
            assertTrue(payload.estimatedSize() > 0);
        }

        @Test
        @DisplayName("TYPE has correct resource location")
        void typeHasCorrectId() {
            assertNotNull(DebugSyncPayload.TYPE);
            assertTrue(DebugSyncPayload.TYPE.id().toString().contains("debug_sync"));
        }
    }

    // ==========================================
    // DebugTogglePayload
    // ==========================================

    @Nested
    @DisplayName("DebugTogglePayload")
    class DebugTogglePayloadTests {

        @Test
        @DisplayName("Record stores featureId")
        void recordFields() {
            DebugTogglePayload payload = new DebugTogglePayload("raids");
            assertEquals("raids", payload.featureId());
        }

        @ParameterizedTest
        @EnumSource(DebugFeature.class)
        @DisplayName("getFeature() resolves all DebugFeature values")
        void getFeatureResolvesAll(DebugFeature feature) {
            DebugTogglePayload payload = new DebugTogglePayload(feature.getId());
            assertEquals(feature, payload.getFeature());
        }

        @Test
        @DisplayName("getFeature() returns null for garbage input")
        void getFeatureReturnsNullForGarbage() {
            DebugTogglePayload payload = new DebugTogglePayload("not_a_real_feature");
            assertNull(payload.getFeature());
        }

        @Test
        @DisplayName("estimatedSize accounts for string length")
        void estimatedSizeAccountsForLength() {
            DebugTogglePayload short_ = new DebugTogglePayload("poi");
            DebugTogglePayload long_ = new DebugTogglePayload("entity_pathing");
            assertTrue(long_.estimatedSize() > short_.estimatedSize());
        }

        @Test
        @DisplayName("TYPE has correct resource location")
        void typeHasCorrectId() {
            assertNotNull(DebugTogglePayload.TYPE);
            assertTrue(DebugTogglePayload.TYPE.id().toString().contains("debug_toggle"));
        }
    }

    // ==========================================
    // EntityGoalsPayload
    // ==========================================

    @Nested
    @DisplayName("EntityGoalsPayload")
    class EntityGoalsPayloadTests {

        @Test
        @DisplayName("Record stores all fields")
        void recordFields() {
            EntityGoalsPayload.GoalInfo goal = new EntityGoalsPayload.GoalInfo(1, true, "FloatGoal");
            EntityGoalsPayload.GoalInfo targetGoal = new EntityGoalsPayload.GoalInfo(2, false, "NearestAttackableTarget");
            EntityGoalsPayload payload = new EntityGoalsPayload(42, "Zombie", 10.0, 65.0, -20.0,
                List.of(goal), List.of(targetGoal));

            assertEquals(42, payload.entityId());
            assertEquals("Zombie", payload.entityName());
            assertEquals(10.0, payload.posX());
            assertEquals(65.0, payload.posY());
            assertEquals(-20.0, payload.posZ());
            assertEquals(1, payload.goals().size());
            assertEquals(1, payload.targetGoals().size());
        }

        @Test
        @DisplayName("GoalInfo record fields")
        void goalInfoFields() {
            EntityGoalsPayload.GoalInfo goal = new EntityGoalsPayload.GoalInfo(3, true, "MeleeAttack");
            assertEquals(3, goal.priority());
            assertTrue(goal.isRunning());
            assertEquals("MeleeAttack", goal.name());
        }

        @Test
        @DisplayName("clear() creates empty payload for entity")
        void clearCreatesEmpty() {
            EntityGoalsPayload cleared = EntityGoalsPayload.clear(123);
            assertEquals(123, cleared.entityId());
            assertEquals("", cleared.entityName());
            assertTrue(cleared.goals().isEmpty());
            assertTrue(cleared.targetGoals().isEmpty());
        }

        @Test
        @DisplayName("estimatedSize is positive for non-empty payload")
        void estimatedSizePositive() {
            EntityGoalsPayload payload = new EntityGoalsPayload(1, "Test", 0, 0, 0,
                List.of(new EntityGoalsPayload.GoalInfo(1, true, "Goal")),
                List.of());
            assertTrue(payload.estimatedSize() > 0);
        }

        @Test
        @DisplayName("estimatedSize grows with more goals")
        void estimatedSizeGrowsWithGoals() {
            EntityGoalsPayload empty = EntityGoalsPayload.clear(1);
            EntityGoalsPayload withGoals = new EntityGoalsPayload(1, "Test", 0, 0, 0,
                List.of(
                    new EntityGoalsPayload.GoalInfo(1, true, "Goal1"),
                    new EntityGoalsPayload.GoalInfo(2, false, "Goal2"),
                    new EntityGoalsPayload.GoalInfo(3, true, "Goal3")
                ),
                List.of(new EntityGoalsPayload.GoalInfo(1, false, "Target1")));
            assertTrue(withGoals.estimatedSize() > empty.estimatedSize());
        }

        @Test
        @DisplayName("TYPE has correct resource location")
        void typeHasCorrectId() {
            assertNotNull(EntityGoalsPayload.TYPE);
            assertTrue(EntityGoalsPayload.TYPE.id().toString().contains("debug_goals"));
        }
    }

    // ==========================================
    // EntityPathingPayload
    // ==========================================

    @Nested
    @DisplayName("EntityPathingPayload")
    class EntityPathingPayloadTests {

        @Test
        @DisplayName("Record stores all fields")
        void recordFields() {
            EntityPathingPayload.PathNode node = new EntityPathingPayload.PathNode(1.0, 2.0, 3.0, 0, 0.5f);
            EntityPathingPayload payload = new EntityPathingPayload(42, "Spider", List.of(node),
                10.0, 65.0, -20.0, true, 1.5f);

            assertEquals(42, payload.entityId());
            assertEquals("Spider", payload.entityName());
            assertEquals(1, payload.nodes().size());
            assertEquals(10.0, payload.targetX());
            assertEquals(65.0, payload.targetY());
            assertEquals(-20.0, payload.targetZ());
            assertTrue(payload.canReach());
            assertEquals(1.5f, payload.maxDistanceToWaypoint());
        }

        @Test
        @DisplayName("PathNode record fields")
        void pathNodeFields() {
            EntityPathingPayload.PathNode node = new EntityPathingPayload.PathNode(5.5, 10.0, -3.2, 2, 0.75f);
            assertEquals(5.5, node.x());
            assertEquals(10.0, node.y());
            assertEquals(-3.2, node.z());
            assertEquals(2, node.nodeType());
            assertEquals(0.75f, node.costMalus());
        }

        @Test
        @DisplayName("clear() creates empty payload for entity")
        void clearCreatesEmpty() {
            EntityPathingPayload cleared = EntityPathingPayload.clear(999);
            assertEquals(999, cleared.entityId());
            assertEquals("", cleared.entityName());
            assertTrue(cleared.nodes().isEmpty());
            assertFalse(cleared.canReach());
        }

        @Test
        @DisplayName("estimatedSize grows with more nodes")
        void estimatedSizeGrowsWithNodes() {
            EntityPathingPayload empty = EntityPathingPayload.clear(1);
            EntityPathingPayload withNodes = new EntityPathingPayload(1, "Test",
                List.of(
                    new EntityPathingPayload.PathNode(1, 2, 3, 0, 0.1f),
                    new EntityPathingPayload.PathNode(4, 5, 6, 1, 0.2f),
                    new EntityPathingPayload.PathNode(7, 8, 9, 2, 0.3f)
                ),
                10, 10, 10, true, 2.0f);
            assertTrue(withNodes.estimatedSize() > empty.estimatedSize());
        }

        @Test
        @DisplayName("TYPE has correct resource location")
        void typeHasCorrectId() {
            assertNotNull(EntityPathingPayload.TYPE);
            assertTrue(EntityPathingPayload.TYPE.id().toString().contains("debug_pathing"));
        }
    }

    // ==========================================
    // RaidsPayload
    // ==========================================

    @Nested
    @DisplayName("RaidsPayload")
    class RaidsPayloadTests {

        @Test
        @DisplayName("Empty raids payload")
        void emptyRaids() {
            RaidsPayload payload = new RaidsPayload(List.of());
            assertTrue(payload.raids().isEmpty());
        }

        @Test
        @DisplayName("RaidInfo record fields")
        void raidInfoFields() {
            RaidsPayload.RaidInfo raid = new RaidsPayload.RaidInfo(
                1, 100.0, 64.0, 200.0, 2, 3, 5, true, false);
            assertEquals(1, raid.raidId());
            assertEquals(100.0, raid.centerX());
            assertEquals(64.0, raid.centerY());
            assertEquals(200.0, raid.centerZ());
            assertEquals(2, raid.badOmenLevel());
            assertEquals(3, raid.groupsSpawned());
            assertEquals(5, raid.numGroups());
            assertTrue(raid.isActive());
            assertFalse(raid.isVictory());
        }

        @Test
        @DisplayName("Payload with multiple raids")
        void multipleRaids() {
            RaidsPayload payload = new RaidsPayload(List.of(
                new RaidsPayload.RaidInfo(1, 0, 0, 0, 1, 2, 3, true, false),
                new RaidsPayload.RaidInfo(2, 100, 64, 200, 3, 5, 7, false, true)
            ));
            assertEquals(2, payload.raids().size());
        }

        @Test
        @DisplayName("estimatedSize grows with more raids")
        void estimatedSizeGrows() {
            RaidsPayload empty = new RaidsPayload(List.of());
            RaidsPayload withRaids = new RaidsPayload(List.of(
                new RaidsPayload.RaidInfo(1, 0, 0, 0, 0, 0, 5, true, false),
                new RaidsPayload.RaidInfo(2, 100, 64, 200, 3, 5, 7, false, true)
            ));
            assertTrue(withRaids.estimatedSize() > empty.estimatedSize());
        }

        @Test
        @DisplayName("TYPE has correct resource location")
        void typeHasCorrectId() {
            assertNotNull(RaidsPayload.TYPE);
            assertTrue(RaidsPayload.TYPE.id().toString().contains("debug_raids"));
        }
    }

    // ==========================================
    // POIPayload
    // ==========================================

    @Nested
    @DisplayName("POIPayload")
    class POIPayloadTests {

        @Test
        @DisplayName("Empty POI payload")
        void emptyPOIs() {
            POIPayload payload = new POIPayload(List.of());
            assertTrue(payload.pois().isEmpty());
        }

        @Test
        @DisplayName("POIInfo record fields")
        void poiInfoFields() {
            POIPayload.POIInfo poi = new POIPayload.POIInfo(10, 65, -20, "minecraft:bed", 3, 4);
            assertEquals(10, poi.x());
            assertEquals(65, poi.y());
            assertEquals(-20, poi.z());
            assertEquals("minecraft:bed", poi.type());
            assertEquals(3, poi.freeTickets());
            assertEquals(4, poi.maxTickets());
        }

        @Test
        @DisplayName("Payload with multiple POIs")
        void multiplePOIs() {
            POIPayload payload = new POIPayload(List.of(
                new POIPayload.POIInfo(0, 64, 0, "minecraft:bed", 1, 1),
                new POIPayload.POIInfo(10, 64, 10, "minecraft:cartographer", 0, 1),
                new POIPayload.POIInfo(-5, 64, 5, "minecraft:armorer", 2, 3)
            ));
            assertEquals(3, payload.pois().size());
        }

        @Test
        @DisplayName("estimatedSize grows with more POIs")
        void estimatedSizeGrows() {
            POIPayload empty = new POIPayload(List.of());
            POIPayload withPOIs = new POIPayload(List.of(
                new POIPayload.POIInfo(0, 64, 0, "minecraft:bed", 1, 1),
                new POIPayload.POIInfo(10, 64, 10, "minecraft:toolsmith", 0, 1)
            ));
            assertTrue(withPOIs.estimatedSize() > empty.estimatedSize());
        }

        @Test
        @DisplayName("TYPE has correct resource location")
        void typeHasCorrectId() {
            assertNotNull(POIPayload.TYPE);
            assertTrue(POIPayload.TYPE.id().toString().contains("debug_poi"));
        }
    }
}
