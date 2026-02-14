package com.devmod.transport;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("TransportData")
class TransportDataTest {

    private static final UUID NODE_ID = UUID.fromString("12345678-1234-1234-1234-123456789abc");
    private static final ResourceLocation DIM = ResourceLocation.parse("minecraft:overworld");
    private static final BlockPos POS = new BlockPos(100, 64, 200);

    private TransportData createMinimal() {
        return new TransportData(
            NODE_ID, TransportNodeType.TELEPAD, TransportMode.NETWORK,
            TransportColor.CYAN, null, 0,
            DIM, POS, null,
            null, "test_network", TransportData.NetworkSelectionMode.RANDOM,
            null, null, null,
            EnumSet.of(TransportEnhancement.CHARGED),
            null, null, null, null, null,
            TransportData.DEFAULT_CHARGE_TIME, TransportData.DEFAULT_COOLDOWN_TIME,
            null, null, null
        );
    }

    // =========================================================================
    // Query Methods
    // =========================================================================

    @Nested
    @DisplayName("Query methods")
    class QueryMethods {

        @Test
        @DisplayName("isTemporary returns true when TEMPORAL enhancement present")
        void isTemporary() {
            TransportData data = createMinimal().withEnhancement(TransportEnhancement.TEMPORAL);
            assertTrue(data.isTemporary());
        }

        @Test
        @DisplayName("isTemporary returns false without TEMPORAL enhancement")
        void isNotTemporary() {
            assertFalse(createMinimal().isTemporary());
        }

        @Test
        @DisplayName("hasSnapshot returns true when SNAPSHOT enhancement present")
        void hasSnapshot() {
            TransportData data = createMinimal().withEnhancement(TransportEnhancement.SNAPSHOT);
            assertTrue(data.hasSnapshot());
        }

        @Test
        @DisplayName("isInstant returns true with HASTE")
        void isInstantWithHaste() {
            TransportData data = createMinimal().withEnhancement(TransportEnhancement.HASTE);
            assertTrue(data.isInstant());
        }

        @Test
        @DisplayName("isInstant returns true with FLUX_CAPACITOR")
        void isInstantWithFluxCapacitor() {
            TransportData data = createMinimal().withEnhancement(TransportEnhancement.FLUX_CAPACITOR);
            assertTrue(data.isInstant());
        }

        @Test
        @DisplayName("isInstant returns true with zero chargeTime")
        void isInstantWithZeroCharge() {
            TransportData data = createMinimal().withChargeTime(0);
            assertTrue(data.isInstant());
        }

        @Test
        @DisplayName("isInstant returns false with non-zero charge and no haste")
        void notInstant() {
            assertFalse(createMinimal().isInstant());
        }

        @Test
        @DisplayName("canCrossDimension returns true with GATE enhancement")
        void canCrossDimension() {
            TransportData data = createMinimal().withEnhancement(TransportEnhancement.GATE);
            assertTrue(data.canCrossDimension());
        }

        @Test
        @DisplayName("isLinked returns true when mode is LINKED and linkedNodeId set")
        void isLinked() {
            UUID targetId = UUID.randomUUID();
            TransportData data = createMinimal().linkTo(targetId);
            assertTrue(data.isLinked());
        }

        @Test
        @DisplayName("isLinked returns false when no linkedNodeId")
        void notLinked() {
            assertFalse(createMinimal().isLinked());
        }

        @Test
        @DisplayName("hasNetwork returns true for NETWORK mode with name")
        void hasNetwork() {
            assertTrue(createMinimal().hasNetwork());
        }

        @Test
        @DisplayName("hasNetwork returns false for empty network name")
        void hasNetworkEmptyName() {
            TransportData data = createMinimal().withNetwork("", TransportData.NetworkSelectionMode.RANDOM);
            assertFalse(data.hasNetwork());
        }

        @Test
        @DisplayName("hasFixedDestination returns true when FIXED mode with dest")
        void hasFixedDest() {
            ResourceLocation destDim = ResourceLocation.parse("minecraft:the_nether");
            BlockPos destPos = new BlockPos(0, 100, 0);
            TransportData data = createMinimal().withFixedDestination(destDim, destPos);
            assertTrue(data.hasFixedDestination());
        }

        @Test
        @DisplayName("hasPosition and hasDimension")
        void hasPositionAndDimension() {
            TransportData data = createMinimal();
            assertTrue(data.hasPosition());
            assertTrue(data.hasDimension());
            assertTrue(data.getPosition().isPresent());
            assertTrue(data.getDimension().isPresent());
        }
    }

    // =========================================================================
    // resolveDisplayName
    // =========================================================================

    @Nested
    @DisplayName("resolveDisplayName")
    class ResolveDisplayName {

        @Test
        @DisplayName("returns displayName when set")
        void returnsDisplayName() {
            TransportData data = createMinimal().withDisplayName("My Portal");
            assertEquals("My Portal", data.resolveDisplayName());
        }

        @Test
        @DisplayName("falls back to networkName when no displayName")
        void fallsToNetworkName() {
            // createMinimal has networkName="test_network" and no displayName
            assertEquals("test_network", createMinimal().resolveDisplayName());
        }

        @Test
        @DisplayName("falls back to nodeType name when no displayName or networkName")
        void fallsToNodeType() {
            TransportData data = new TransportData(
                NODE_ID, TransportNodeType.TELEPAD, TransportMode.LINKED,
                TransportColor.CYAN, null, 0,
                DIM, POS, null,
                null, null, null,
                null, null, null,
                EnumSet.noneOf(TransportEnhancement.class),
                null, null, null, null, null,
                40, 60, null, null, null
            );
            assertEquals("telepad", data.resolveDisplayName());
        }

        @Test
        @DisplayName("ignores empty displayName")
        void ignoresEmptyDisplayName() {
            TransportData data = createMinimal().withDisplayName("");
            // Falls back to networkName
            assertEquals("test_network", data.resolveDisplayName());
        }
    }

    // =========================================================================
    // Effective Range
    // =========================================================================

    @Nested
    @DisplayName("getEffectiveRange")
    class EffectiveRange {

        @Test
        @DisplayName("base range is 100")
        void baseRange() {
            assertEquals(100, createMinimal().getEffectiveRange());
        }

        @Test
        @DisplayName("ENHANCER adds 100")
        void enhancerBonus() {
            TransportData data = createMinimal().withEnhancement(TransportEnhancement.ENHANCER);
            assertEquals(200, data.getEffectiveRange());
        }

        @Test
        @DisplayName("STRONG_ENHANCER adds 500")
        void strongEnhancerBonus() {
            TransportData data = createMinimal().withEnhancement(TransportEnhancement.STRONG_ENHANCER);
            assertEquals(600, data.getEffectiveRange());
        }

        @Test
        @DisplayName("INFINITY returns MAX_VALUE")
        void infinityRange() {
            TransportData data = createMinimal().withEnhancement(TransportEnhancement.INFINITY);
            assertEquals(Integer.MAX_VALUE, data.getEffectiveRange());
        }

        @Test
        @DisplayName("RANGE_AMPLIFIER adds 100")
        void rangeAmplifierBonus() {
            TransportData data = createMinimal().withEnhancement(TransportEnhancement.RANGE_AMPLIFIER);
            assertEquals(200, data.getEffectiveRange());
        }

        @Test
        @DisplayName("multiple range enhancements stack")
        void stackedRange() {
            TransportData data = createMinimal()
                .withEnhancement(TransportEnhancement.ENHANCER)
                .withEnhancement(TransportEnhancement.RANGE_AMPLIFIER);
            assertEquals(300, data.getEffectiveRange());
        }
    }

    // =========================================================================
    // Effective Charge Time
    // =========================================================================

    @Nested
    @DisplayName("getEffectiveChargeTime")
    class EffectiveChargeTime {

        @Test
        @DisplayName("returns charge time within bounds")
        void normalChargeTime() {
            TransportData data = createMinimal(); // 40 ticks
            assertEquals(40, data.getEffectiveChargeTime());
        }

        @Test
        @DisplayName("returns 0 for instant nodes")
        void instantChargeTime() {
            TransportData data = createMinimal().withEnhancement(TransportEnhancement.HASTE);
            assertEquals(0, data.getEffectiveChargeTime());
        }

        @Test
        @DisplayName("clamps to CHARGE_MAX for excessive values")
        void clampsToMax() {
            TransportData data = createMinimal().withChargeTime(999);
            assertEquals(TransportData.CHARGE_MAX, data.getEffectiveChargeTime());
        }

        @Test
        @DisplayName("clamps to CHARGE_MIN for negative values")
        void clampsToMin() {
            TransportData data = createMinimal().withChargeTime(-10);
            assertEquals(TransportData.CHARGE_MIN, data.getEffectiveChargeTime());
        }
    }

    // =========================================================================
    // Builder / Immutability
    // =========================================================================

    @Nested
    @DisplayName("Builder methods")
    class BuilderMethods {

        @Test
        @DisplayName("withMode changes mode without mutating original")
        void withMode() {
            TransportData original = createMinimal();
            TransportData changed = original.withMode(TransportMode.FIXED);
            assertEquals(TransportMode.NETWORK, original.mode());
            assertEquals(TransportMode.FIXED, changed.mode());
        }

        @Test
        @DisplayName("withColor changes color")
        void withColor() {
            TransportData changed = createMinimal().withColor(TransportColor.RED);
            assertEquals(TransportColor.RED, changed.color());
        }

        @Test
        @DisplayName("withEnhancement adds enhancement immutably")
        void withEnhancement() {
            TransportData original = createMinimal();
            TransportData changed = original.withEnhancement(TransportEnhancement.HASTE);
            assertFalse(original.enhancements().contains(TransportEnhancement.HASTE));
            assertTrue(changed.enhancements().contains(TransportEnhancement.HASTE));
        }

        @Test
        @DisplayName("withoutEnhancement removes enhancement immutably")
        void withoutEnhancement() {
            TransportData original = createMinimal(); // has CHARGED
            TransportData changed = original.withoutEnhancement(TransportEnhancement.CHARGED);
            assertTrue(original.enhancements().contains(TransportEnhancement.CHARGED));
            assertFalse(changed.enhancements().contains(TransportEnhancement.CHARGED));
        }

        @Test
        @DisplayName("withoutEnhancement on empty set returns same instance")
        void withoutEnhancementOnEmpty() {
            TransportData data = createMinimal().withEnhancements(EnumSet.noneOf(TransportEnhancement.class));
            assertSame(data, data.withoutEnhancement(TransportEnhancement.HASTE));
        }

        @Test
        @DisplayName("linkTo sets mode to LINKED and clears network fields")
        void linkTo() {
            UUID targetId = UUID.randomUUID();
            TransportData linked = createMinimal().linkTo(targetId);
            assertEquals(TransportMode.LINKED, linked.mode());
            assertEquals(targetId, linked.linkedNodeId());
            assertNull(linked.networkName());
        }

        @Test
        @DisplayName("unlink clears linkedNodeId")
        void unlink() {
            UUID targetId = UUID.randomUUID();
            TransportData linked = createMinimal().linkTo(targetId);
            TransportData unlinked = linked.unlink();
            assertNull(unlinked.linkedNodeId());
        }

        @Test
        @DisplayName("withNetwork sets mode to NETWORK")
        void withNetwork() {
            TransportData data = createMinimal().withNetwork("hub", TransportData.NetworkSelectionMode.NEAREST);
            assertEquals(TransportMode.NETWORK, data.mode());
            assertEquals("hub", data.networkName());
            assertEquals(TransportData.NetworkSelectionMode.NEAREST, data.selectionMode());
        }

        @Test
        @DisplayName("withNetwork defaults selection mode to RANDOM when null")
        void withNetworkNullSelection() {
            TransportData data = createMinimal().withNetwork("hub", null);
            assertEquals(TransportData.NetworkSelectionMode.RANDOM, data.selectionMode());
        }

        @Test
        @DisplayName("withFixedDestination sets mode to FIXED")
        void withFixedDest() {
            ResourceLocation destDim = ResourceLocation.parse("minecraft:the_nether");
            BlockPos destPos = new BlockPos(0, 100, 0);
            TransportData data = createMinimal().withFixedDestination(destDim, destPos);
            assertEquals(TransportMode.FIXED, data.mode());
            assertEquals(destDim, data.fixedDestDim());
            assertEquals(destPos, data.fixedDestPos());
        }

        @Test
        @DisplayName("withTemporalData adds TEMPORAL enhancement")
        void withTemporalData() {
            TransportData data = createMinimal().withTemporalData(100L, 200L, TransportData.TemporalPhase.ACTIVE);
            assertTrue(data.isTemporary());
            assertEquals(100L, data.createdTick());
            assertEquals(200L, data.expirationTick());
            assertEquals(TransportData.TemporalPhase.ACTIVE, data.currentPhase());
        }

        @Test
        @DisplayName("withTiming sets charge and cooldown")
        void withTiming() {
            TransportData data = createMinimal().withTiming(10, 20);
            assertEquals(10, data.chargeTime());
            assertEquals(20, data.cooldownTime());
        }
    }

    // =========================================================================
    // Factory Methods
    // =========================================================================

    @Nested
    @DisplayName("Factory methods")
    class FactoryMethods {

        @Test
        @DisplayName("createPortal creates LINKED portal")
        void createPortal() {
            TransportData portal = TransportData.createPortal(TransportColor.BLUE, DIM, POS);
            assertEquals(TransportNodeType.PORTAL, portal.nodeType());
            assertEquals(TransportMode.LINKED, portal.mode());
            assertEquals(TransportColor.BLUE, portal.color());
            assertEquals(80, portal.chargeTime());
            assertEquals(100, portal.cooldownTime());
            assertNotNull(portal.id());
        }

        @Test
        @DisplayName("createTelepad creates NETWORK telepad with defaults")
        void createTelepad() {
            TransportData telepad = TransportData.createTelepad("hub", DIM, POS);
            assertEquals(TransportNodeType.TELEPAD, telepad.nodeType());
            assertEquals(TransportMode.NETWORK, telepad.mode());
            assertEquals("hub", telepad.networkName());
            assertEquals(TransportColor.CYAN, telepad.color());
            assertEquals(TransportData.DEFAULT_CHARGE_TIME, telepad.chargeTime());
        }

        @Test
        @DisplayName("createRiftGate creates FIXED temporary node with correct expiration")
        void createRiftGate() {
            ResourceLocation destDim = ResourceLocation.parse("minecraft:the_nether");
            BlockPos destPos = new BlockPos(0, 100, 0);
            TransportData rift = TransportData.createRiftGate(DIM, POS, destDim, destPos, 1000L, 200L);
            assertEquals(TransportNodeType.RIFT_GATE, rift.nodeType());
            assertEquals(TransportMode.FIXED, rift.mode());
            assertTrue(rift.isTemporary());
            assertTrue(rift.isInstant());
            assertEquals(1000L, rift.createdTick());
            assertEquals(1200L, rift.expirationTick());
            assertEquals(TransportData.TemporalPhase.CALIBRATING, rift.currentPhase());
        }

        @Test
        @DisplayName("createWaypoint creates WAYPOINT node with displayName")
        void createWaypoint() {
            TransportData wp = TransportData.createWaypoint("Spawn", DIM, POS);
            assertEquals(TransportNodeType.WAYPOINT, wp.nodeType());
            assertEquals(TransportMode.WAYPOINT, wp.mode());
            assertEquals("Spawn", wp.displayName());
            assertEquals(0, wp.chargeTime()); // Instant
        }

        @Test
        @DisplayName("createZone creates FIXED zone node")
        void createZone() {
            ResourceLocation destDim = ResourceLocation.parse("minecraft:the_nether");
            BlockPos destPos = new BlockPos(0, 100, 0);
            TransportData zone = TransportData.createZone(TransportColor.GREEN, DIM, POS, destDim, destPos, "Nether Hub");
            assertEquals(TransportNodeType.ZONE, zone.nodeType());
            assertEquals(TransportMode.FIXED, zone.mode());
            assertTrue(zone.isInstant()); // Zones have HASTE
            assertEquals("Nether Hub", zone.displayName());
        }

        @Test
        @DisplayName("createRecoveryPoint creates RETURN recovery node")
        void createRecoveryPoint() {
            UUID playerId = UUID.randomUUID();
            TransportData rp = TransportData.createRecoveryPoint(playerId, DIM, POS);
            assertEquals(TransportNodeType.RECOVERY_POINT, rp.nodeType());
            assertEquals(TransportMode.RETURN, rp.mode());
            assertEquals(playerId, rp.creatorId());
            assertTrue(rp.enhancements().contains(TransportEnhancement.RECOVERY));
        }
    }

    // =========================================================================
    // Inner Enum Tests
    // =========================================================================

    @Nested
    @DisplayName("NetworkSelectionMode.byIndex")
    class NetworkSelectionModeTests {

        @Test
        @DisplayName("valid index returns correct mode")
        void validIndex() {
            assertEquals(TransportData.NetworkSelectionMode.RANDOM,
                TransportData.NetworkSelectionMode.byIndex(0));
            assertEquals(TransportData.NetworkSelectionMode.MANUAL,
                TransportData.NetworkSelectionMode.byIndex(3));
        }

        @Test
        @DisplayName("negative index returns RANDOM")
        void negativeIndex() {
            assertEquals(TransportData.NetworkSelectionMode.RANDOM,
                TransportData.NetworkSelectionMode.byIndex(-1));
        }

        @Test
        @DisplayName("out of bounds index returns RANDOM")
        void outOfBoundsIndex() {
            assertEquals(TransportData.NetworkSelectionMode.RANDOM,
                TransportData.NetworkSelectionMode.byIndex(999));
        }
    }

    @Nested
    @DisplayName("TemporalPhase.byIndex")
    class TemporalPhaseTests {

        @Test
        @DisplayName("valid index returns correct phase")
        void validIndex() {
            assertEquals(TransportData.TemporalPhase.CALIBRATING,
                TransportData.TemporalPhase.byIndex(0));
            assertEquals(TransportData.TemporalPhase.EXPIRED,
                TransportData.TemporalPhase.byIndex(3));
        }

        @Test
        @DisplayName("negative index returns ACTIVE")
        void negativeIndex() {
            assertEquals(TransportData.TemporalPhase.ACTIVE,
                TransportData.TemporalPhase.byIndex(-1));
        }

        @Test
        @DisplayName("out of bounds index returns ACTIVE")
        void outOfBoundsIndex() {
            assertEquals(TransportData.TemporalPhase.ACTIVE,
                TransportData.TemporalPhase.byIndex(999));
        }
    }

    // =========================================================================
    // NBT Serialization Round-Trip
    // =========================================================================

    @Nested
    @DisplayName("NBT serialization")
    class NbtSerialization {

        @Test
        @DisplayName("minimal node round-trips through save/load")
        void minimalRoundTrip() {
            TransportData original = createMinimal();
            CompoundTag tag = original.save();
            TransportData loaded = TransportData.load(tag);

            assertEquals(original.id(), loaded.id());
            assertEquals(original.nodeType(), loaded.nodeType());
            assertEquals(original.mode(), loaded.mode());
            assertEquals(original.color(), loaded.color());
            assertEquals(original.dimension(), loaded.dimension());
            assertEquals(original.position(), loaded.position());
            assertEquals(original.networkName(), loaded.networkName());
            assertEquals(original.chargeTime(), loaded.chargeTime());
            assertEquals(original.cooldownTime(), loaded.cooldownTime());
        }

        @Test
        @DisplayName("full node round-trips through save/load")
        void fullRoundTrip() {
            UUID creatorId = UUID.randomUUID();
            UUID linkedId = UUID.randomUUID();
            Set<UUID> waypointTargets = new HashSet<>();
            waypointTargets.add(UUID.randomUUID());
            waypointTargets.add(UUID.randomUUID());

            TransportData original = new TransportData(
                NODE_ID, TransportNodeType.PORTAL, TransportMode.LINKED,
                TransportColor.PURPLE, ResourceLocation.parse("devmod:custom_model"), 8,
                DIM, POS, net.minecraft.core.Direction.Axis.X,
                linkedId, "test_network", TransportData.NetworkSelectionMode.ROUND_ROBIN,
                ResourceLocation.parse("minecraft:the_nether"), new BlockPos(0, 100, 0),
                waypointTargets,
                EnumSet.of(TransportEnhancement.CHARGED, TransportEnhancement.GATE, TransportEnhancement.HASTE),
                1000L, 2000L, TransportData.TemporalPhase.ACTIVE,
                null, null,
                80, 100,
                creatorId, "Test Portal", "A test description"
            );

            CompoundTag tag = original.save();
            TransportData loaded = TransportData.load(tag);

            assertEquals(original.id(), loaded.id());
            assertEquals(original.nodeType(), loaded.nodeType());
            assertEquals(original.mode(), loaded.mode());
            assertEquals(original.color(), loaded.color());
            assertEquals(original.customModel(), loaded.customModel());
            assertEquals(original.frameCount(), loaded.frameCount());
            assertEquals(original.dimension(), loaded.dimension());
            assertEquals(original.position(), loaded.position());
            assertEquals(original.axis(), loaded.axis());
            assertEquals(original.linkedNodeId(), loaded.linkedNodeId());
            assertEquals(original.networkName(), loaded.networkName());
            assertEquals(original.selectionMode(), loaded.selectionMode());
            assertEquals(original.fixedDestDim(), loaded.fixedDestDim());
            assertEquals(original.fixedDestPos(), loaded.fixedDestPos());
            assertEquals(original.waypointTargets(), loaded.waypointTargets());
            assertEquals(original.enhancements(), loaded.enhancements());
            assertEquals(original.createdTick(), loaded.createdTick());
            assertEquals(original.expirationTick(), loaded.expirationTick());
            assertEquals(original.currentPhase(), loaded.currentPhase());
            assertEquals(original.chargeTime(), loaded.chargeTime());
            assertEquals(original.cooldownTime(), loaded.cooldownTime());
            assertEquals(original.creatorId(), loaded.creatorId());
            assertEquals(original.displayName(), loaded.displayName());
            assertEquals(original.description(), loaded.description());
        }

        @Test
        @DisplayName("load uses defaults for missing timing fields")
        void missingTimingDefaults() {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("id", NODE_ID);
            tag.putString("node_type", "telepad");
            tag.putString("mode", "network");
            tag.putInt("color", 0);
            // No charge_time or cooldown_time

            TransportData loaded = TransportData.load(tag);
            assertEquals(TransportData.DEFAULT_CHARGE_TIME, loaded.chargeTime());
            assertEquals(TransportData.DEFAULT_COOLDOWN_TIME, loaded.cooldownTime());
        }
    }

    // =========================================================================
    // Constants
    // =========================================================================

    @Nested
    @DisplayName("Constants")
    class Constants {

        @Test
        @DisplayName("default charge time is 40 ticks (2 seconds)")
        void defaultChargeTime() {
            assertEquals(40, TransportData.DEFAULT_CHARGE_TIME);
        }

        @Test
        @DisplayName("default cooldown time is 60 ticks (3 seconds)")
        void defaultCooldownTime() {
            assertEquals(60, TransportData.DEFAULT_COOLDOWN_TIME);
        }

        @Test
        @DisplayName("base range is 100 blocks")
        void baseRange() {
            assertEquals(100, TransportData.BASE_RANGE);
        }

        @Test
        @DisplayName("charge max is 200 ticks (10 seconds)")
        void chargeMax() {
            assertEquals(200, TransportData.CHARGE_MAX);
        }

        @Test
        @DisplayName("arrival timeout is 600 ticks (30 seconds)")
        void arrivalTimeout() {
            assertEquals(600, TransportData.ARRIVAL_TIMEOUT);
        }
    }
}
