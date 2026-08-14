package com.devmod.debug;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import io.netty.buffer.Unpooled;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;

import com.devmod.TestBootstrap;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for debug payload records: DebugSyncPayload, DebugTogglePayload, BrainsPayload,
 * EntityGoalsPayload, BeesPayload, BlockUpdatesPayload, EntityPathingPayload, StructuresPayload,
 * RaidsPayload, POIPayload.
 * Tests record construction, getFeature() mapping, estimatedSize, clear() factories,
 * and the decode-side count bounds that keep a hostile server from allocating without limit.
 */
@DisplayName("Debug Payloads")
class DebugPayloadTest {

    @BeforeAll
    static void bootstrap() {
        TestBootstrap.init();
    }

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
    // BrainsPayload
    // ==========================================

    @Nested
    @DisplayName("BrainsPayload")
    class BrainsPayloadTests {

        @Test
        @DisplayName("TargetLink record fields")
        void targetLinkFields() {
            BrainsPayload.TargetLink link = new BrainsPayload.TargetLink(42, 7);
            assertEquals(42, link.entityId());
            assertEquals(7, link.targetId());
        }

        @Test
        @DisplayName("estimatedSize grows with more links")
        void estimatedSizeGrows() {
            BrainsPayload empty = new BrainsPayload(List.of());
            BrainsPayload withLinks = new BrainsPayload(List.of(
                new BrainsPayload.TargetLink(1, 2),
                new BrainsPayload.TargetLink(3, 4)
            ));
            assertTrue(withLinks.estimatedSize() > empty.estimatedSize());
        }

        @Test
        @DisplayName("TYPE has correct resource location")
        void typeHasCorrectId() {
            assertNotNull(BrainsPayload.TYPE);
            assertTrue(BrainsPayload.TYPE.id().toString().contains("debug_brains"));
        }

        @Test
        @DisplayName("encode/decode round-trip preserves links")
        void roundTrip() {
            BrainsPayload original = new BrainsPayload(List.of(
                new BrainsPayload.TargetLink(11, 22),
                new BrainsPayload.TargetLink(33, 44)
            ));

            FriendlyByteBuf buf = new FriendlyByteBuf(Objects.requireNonNull(Unpooled.buffer()));
            BrainsPayload.STREAM_CODEC.encode(buf, original);
            buf.readerIndex(0);
            BrainsPayload decoded = BrainsPayload.STREAM_CODEC.decode(buf);

            assertEquals(original.targets(), decoded.targets());
            buf.release();
        }

        @Test
        @DisplayName("decode caps the link count")
        void decodeCapsCount() {
            List<BrainsPayload.TargetLink> links = new ArrayList<>();
            for (int i = 0; i < 400; i++) {
                links.add(new BrainsPayload.TargetLink(i, i + 1));
            }

            FriendlyByteBuf buf = new FriendlyByteBuf(Objects.requireNonNull(Unpooled.buffer()));
            BrainsPayload.STREAM_CODEC.encode(buf, new BrainsPayload(links));
            buf.readerIndex(0);
            BrainsPayload decoded = BrainsPayload.STREAM_CODEC.decode(buf);

            assertEquals(BrainsPayload.maxTargets(), decoded.targets().size());
            buf.release();
        }
    }

    // ==========================================
    // EntityGoalsPayload
    // ==========================================

    @Nested
    @DisplayName("EntityGoalsPayload")
    class EntityGoalsPayloadTests {

        private EntityGoalsPayload.MobGoals mobWithGoals(int entityId, int goalCount) {
            List<EntityGoalsPayload.GoalInfo> goals = new ArrayList<>(goalCount);
            for (int i = 0; i < goalCount; i++) {
                goals.add(new EntityGoalsPayload.GoalInfo(i, i % 2 == 0, i % 3 == 0, "Goal" + i));
            }
            return new EntityGoalsPayload.MobGoals(entityId, goals);
        }

        @Test
        @DisplayName("GoalInfo record fields")
        void goalInfoFields() {
            EntityGoalsPayload.GoalInfo goal =
                new EntityGoalsPayload.GoalInfo(3, true, true, "NearestAttackableTargetGoal");
            assertEquals(3, goal.priority());
            assertTrue(goal.running());
            assertTrue(goal.targetSelector());
            assertEquals("NearestAttackableTargetGoal", goal.name());
        }

        @Test
        @DisplayName("estimatedSize grows with more goals")
        void estimatedSizeGrows() {
            EntityGoalsPayload empty = new EntityGoalsPayload(List.of());
            EntityGoalsPayload withGoals = new EntityGoalsPayload(List.of(mobWithGoals(1, 4)));
            assertTrue(withGoals.estimatedSize() > empty.estimatedSize());
        }

        @Test
        @DisplayName("A full payload stays inside the LARGE handler limit")
        void fullPayloadFitsHandlerLimit() {
            List<EntityGoalsPayload.MobGoals> mobs = new ArrayList<>();
            for (int i = 0; i < EntityGoalsPayload.maxMobs(); i++) {
                List<EntityGoalsPayload.GoalInfo> goals = new ArrayList<>();
                // A realistic mob: both selectors together, vanilla-length goal class names.
                for (int g = 0; g < 18; g++) {
                    goals.add(new EntityGoalsPayload.GoalInfo(g, false, g > 12, "NearestAttackableTargetGoal"));
                }
                mobs.add(new EntityGoalsPayload.MobGoals(i, goals));
            }

            assertTrue(new EntityGoalsPayload(mobs).estimatedSize() < 32768,
                "a full snapshot must not be rejected by PayloadLimits.LARGE");
        }

        @Test
        @DisplayName("TYPE has correct resource location")
        void typeHasCorrectId() {
            assertNotNull(EntityGoalsPayload.TYPE);
            assertTrue(EntityGoalsPayload.TYPE.id().toString().contains("debug_goals"));
        }

        @Test
        @DisplayName("encode/decode round-trip preserves both selectors")
        void roundTrip() {
            EntityGoalsPayload original = new EntityGoalsPayload(List.of(
                new EntityGoalsPayload.MobGoals(11, List.of(
                    new EntityGoalsPayload.GoalInfo(0, true, false, "FloatGoal"),
                    new EntityGoalsPayload.GoalInfo(2, false, true, "HurtByTargetGoal")
                )),
                new EntityGoalsPayload.MobGoals(22, List.of(
                    new EntityGoalsPayload.GoalInfo(1, false, false, "RandomStrollGoal")
                ))
            ));

            FriendlyByteBuf buf = new FriendlyByteBuf(Objects.requireNonNull(Unpooled.buffer()));
            EntityGoalsPayload.STREAM_CODEC.encode(buf, original);
            buf.readerIndex(0);
            EntityGoalsPayload decoded = EntityGoalsPayload.STREAM_CODEC.decode(buf);

            assertEquals(original.mobs(), decoded.mobs());
            buf.release();
        }

        @Test
        @DisplayName("decode caps the mob count")
        void decodeCapsMobCount() {
            List<EntityGoalsPayload.MobGoals> mobs = new ArrayList<>();
            for (int i = 0; i < 400; i++) {
                mobs.add(mobWithGoals(i, 2));
            }

            FriendlyByteBuf buf = new FriendlyByteBuf(Objects.requireNonNull(Unpooled.buffer()));
            EntityGoalsPayload.STREAM_CODEC.encode(buf, new EntityGoalsPayload(mobs));
            buf.readerIndex(0);
            EntityGoalsPayload decoded = EntityGoalsPayload.STREAM_CODEC.decode(buf);

            assertEquals(EntityGoalsPayload.maxMobs(), decoded.mobs().size());
            buf.release();
        }

        @Test
        @DisplayName("decode caps the goal count of a single mob")
        void decodeCapsGoalCount() {
            FriendlyByteBuf buf = new FriendlyByteBuf(Objects.requireNonNull(Unpooled.buffer()));
            EntityGoalsPayload.STREAM_CODEC.encode(buf,
                new EntityGoalsPayload(List.of(mobWithGoals(1, 400))));
            buf.readerIndex(0);
            EntityGoalsPayload decoded = EntityGoalsPayload.STREAM_CODEC.decode(buf);

            assertEquals(1, decoded.mobs().size());
            assertEquals(EntityGoalsPayload.maxGoalsPerMob(),
                Objects.requireNonNull(decoded.mobs().get(0)).goals().size());
            buf.release();
        }
    }

    // ==========================================
    // BeesPayload
    // ==========================================

    @Nested
    @DisplayName("BeesPayload")
    class BeesPayloadTests {

        @Test
        @DisplayName("BeeInfo tolerates a bee with neither hive nor flower")
        void beeInfoFields() {
            BeesPayload.BeeInfo info = new BeesPayload.BeeInfo(5, null, null);
            assertEquals(5, info.entityId());
            assertNull(info.hivePos());
            assertNull(info.flowerPos());
        }

        @Test
        @DisplayName("estimatedSize grows with remembered positions")
        void estimatedSizeGrows() {
            BeesPayload without = new BeesPayload(List.of(new BeesPayload.BeeInfo(1, null, null)));
            BeesPayload with = new BeesPayload(List.of(
                new BeesPayload.BeeInfo(1, new BlockPos(10, 64, -20), new BlockPos(12, 63, -18))));
            assertTrue(with.estimatedSize() > without.estimatedSize());
        }

        @Test
        @DisplayName("TYPE has correct resource location")
        void typeHasCorrectId() {
            assertNotNull(BeesPayload.TYPE);
            assertTrue(BeesPayload.TYPE.id().toString().contains("debug_bees"));
        }

        @Test
        @DisplayName("encode/decode round-trip preserves nullable positions")
        void roundTrip() {
            BeesPayload original = new BeesPayload(List.of(
                new BeesPayload.BeeInfo(1, new BlockPos(10, 64, -20), null),
                new BeesPayload.BeeInfo(2, null, new BlockPos(-5, 70, 8)),
                new BeesPayload.BeeInfo(3, new BlockPos(0, 0, 0), new BlockPos(1, 2, 3))
            ));

            FriendlyByteBuf buf = new FriendlyByteBuf(Objects.requireNonNull(Unpooled.buffer()));
            BeesPayload.STREAM_CODEC.encode(buf, original);
            buf.readerIndex(0);
            BeesPayload decoded = BeesPayload.STREAM_CODEC.decode(buf);

            assertEquals(original.bees(), decoded.bees());
            buf.release();
        }

        @Test
        @DisplayName("decode caps the bee count")
        void decodeCapsCount() {
            List<BeesPayload.BeeInfo> bees = new ArrayList<>();
            for (int i = 0; i < 400; i++) {
                bees.add(new BeesPayload.BeeInfo(i, new BlockPos(i, 64, i), null));
            }

            FriendlyByteBuf buf = new FriendlyByteBuf(Objects.requireNonNull(Unpooled.buffer()));
            BeesPayload.STREAM_CODEC.encode(buf, new BeesPayload(bees));
            buf.readerIndex(0);
            BeesPayload decoded = BeesPayload.STREAM_CODEC.decode(buf);

            assertEquals(BeesPayload.maxBees(), decoded.bees().size());
            buf.release();
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

        @Test
        @DisplayName("decode caps the raid count")
        void decodeCapsCount() {
            List<RaidsPayload.RaidInfo> raids = new ArrayList<>();
            for (int i = 0; i < 200; i++) {
                raids.add(new RaidsPayload.RaidInfo(i, i, 64, i, 1, 2, 3, true, false));
            }

            FriendlyByteBuf buf = new FriendlyByteBuf(Objects.requireNonNull(Unpooled.buffer()));
            RaidsPayload.STREAM_CODEC.encode(buf, new RaidsPayload(raids));
            buf.readerIndex(0);
            RaidsPayload decoded = RaidsPayload.STREAM_CODEC.decode(buf);

            assertTrue(decoded.raids().size() < raids.size(),
                "decode must not allocate every raid the sender claims");
            buf.release();
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

        @Test
        @DisplayName("decode caps the POI count")
        void decodeCapsCount() {
            List<POIPayload.POIInfo> pois = new ArrayList<>();
            for (int i = 0; i < 800; i++) {
                pois.add(new POIPayload.POIInfo(i, 64, i, "minecraft:bed", 1, 1));
            }

            FriendlyByteBuf buf = new FriendlyByteBuf(Objects.requireNonNull(Unpooled.buffer()));
            POIPayload.STREAM_CODEC.encode(buf, new POIPayload(pois));
            buf.readerIndex(0);
            POIPayload decoded = POIPayload.STREAM_CODEC.decode(buf);

            assertEquals(POIPayload.maxPois(), decoded.pois().size());
            buf.release();
        }
    }

    // ==========================================
    // StructuresPayload
    // ==========================================

    @Nested
    @DisplayName("StructuresPayload")
    class StructuresPayloadTests {

        @Test
        @DisplayName("Empty structures payload")
        void emptyStructures() {
            StructuresPayload payload = new StructuresPayload(List.of());
            assertTrue(payload.boxes().isEmpty());
        }

        @Test
        @DisplayName("StructureBox record fields")
        void structureBoxFields() {
            StructuresPayload.StructureBox box = new StructuresPayload.StructureBox(-10, -64, 5, 20, 100, 40);
            assertEquals(-10, box.minX());
            assertEquals(-64, box.minY());
            assertEquals(5, box.minZ());
            assertEquals(20, box.maxX());
            assertEquals(100, box.maxY());
            assertEquals(40, box.maxZ());
        }

        @Test
        @DisplayName("estimatedSize grows with more boxes")
        void estimatedSizeGrows() {
            StructuresPayload empty = new StructuresPayload(List.of());
            StructuresPayload withBoxes = new StructuresPayload(List.of(
                new StructuresPayload.StructureBox(0, 0, 0, 16, 16, 16),
                new StructuresPayload.StructureBox(32, 60, 32, 64, 90, 64)
            ));
            assertTrue(withBoxes.estimatedSize() > empty.estimatedSize());
        }

        @Test
        @DisplayName("TYPE has correct resource location")
        void typeHasCorrectId() {
            assertNotNull(StructuresPayload.TYPE);
            assertTrue(StructuresPayload.TYPE.id().toString().contains("debug_structures"));
        }

        @Test
        @DisplayName("encode/decode round-trip preserves boxes")
        void roundTrip() {
            StructuresPayload original = new StructuresPayload(List.of(
                new StructuresPayload.StructureBox(-100, -60, -100, -20, 30, -20),
                new StructuresPayload.StructureBox(0, 64, 0, 48, 96, 48)
            ));

            FriendlyByteBuf buf = new FriendlyByteBuf(Objects.requireNonNull(Unpooled.buffer()));
            StructuresPayload.STREAM_CODEC.encode(buf, original);
            buf.readerIndex(0);
            StructuresPayload decoded = StructuresPayload.STREAM_CODEC.decode(buf);

            assertEquals(original.boxes(), decoded.boxes());
            buf.release();
        }

        @Test
        @DisplayName("decode caps the box count")
        void decodeCapsCount() {
            List<StructuresPayload.StructureBox> boxes = new ArrayList<>();
            for (int i = 0; i < 400; i++) {
                boxes.add(new StructuresPayload.StructureBox(i, 0, i, i + 8, 16, i + 8));
            }

            FriendlyByteBuf buf = new FriendlyByteBuf(Objects.requireNonNull(Unpooled.buffer()));
            StructuresPayload.STREAM_CODEC.encode(buf, new StructuresPayload(boxes));
            buf.readerIndex(0);
            StructuresPayload decoded = StructuresPayload.STREAM_CODEC.decode(buf);

            assertEquals(StructuresPayload.maxBoxes(), decoded.boxes().size());
            buf.release();
        }
    }

    // ==========================================
    // BlockUpdatesPayload
    // ==========================================

    @Nested
    @DisplayName("BlockUpdatesPayload")
    class BlockUpdatesPayloadTests {

        @Test
        @DisplayName("estimatedSize grows with position count")
        void estimatedSizeGrows() {
            BlockUpdatesPayload one = new BlockUpdatesPayload(100L, List.of(new BlockPos(0, 64, 0)));
            BlockUpdatesPayload two = new BlockUpdatesPayload(100L,
                List.of(new BlockPos(0, 64, 0), new BlockPos(1, 64, 0)));
            assertTrue(two.estimatedSize() > one.estimatedSize());
        }

        @Test
        @DisplayName("TYPE has correct resource location")
        void typeHasCorrectId() {
            assertNotNull(BlockUpdatesPayload.TYPE);
            assertTrue(BlockUpdatesPayload.TYPE.id().toString().contains("debug_block_updates"));
        }

        @Test
        @DisplayName("encode/decode round-trip preserves gameTime and positions")
        void roundTrip() {
            BlockUpdatesPayload original = new BlockUpdatesPayload(123456L, List.of(
                new BlockPos(10, 64, -20),
                new BlockPos(-5, 70, 8),
                new BlockPos(0, 0, 0)
            ));

            FriendlyByteBuf buf = new FriendlyByteBuf(Objects.requireNonNull(Unpooled.buffer()));
            BlockUpdatesPayload.STREAM_CODEC.encode(buf, original);
            buf.readerIndex(0);
            BlockUpdatesPayload decoded = BlockUpdatesPayload.STREAM_CODEC.decode(buf);

            assertEquals(original.gameTime(), decoded.gameTime());
            assertEquals(original.positions(), decoded.positions());
            buf.release();
        }

        @Test
        @DisplayName("A full payload stays inside the MEDIUM size limit it is registered with")
        void fullPayloadFitsMediumLimit() {
            List<BlockPos> positions = new ArrayList<>();
            for (int i = 0; i < BlockUpdatesPayload.maxPositions(); i++) {
                positions.add(new BlockPos(i, 64, i));
            }
            BlockUpdatesPayload full = new BlockUpdatesPayload(Long.MAX_VALUE, positions);
            assertTrue(full.estimatedSize() <= 8192,
                "Full payload must not trip the MEDIUM payload limit: " + full.estimatedSize());
        }

        @Test
        @DisplayName("decode caps the position count")
        void decodeCapsCount() {
            List<BlockPos> positions = new ArrayList<>();
            for (int i = 0; i < 400; i++) {
                positions.add(new BlockPos(i, 64, i));
            }

            FriendlyByteBuf buf = new FriendlyByteBuf(Objects.requireNonNull(Unpooled.buffer()));
            BlockUpdatesPayload.STREAM_CODEC.encode(buf, new BlockUpdatesPayload(1L, positions));
            buf.readerIndex(0);
            BlockUpdatesPayload decoded = BlockUpdatesPayload.STREAM_CODEC.decode(buf);

            assertEquals(BlockUpdatesPayload.maxPositions(), decoded.positions().size());
            buf.release();
        }
    }
}
