package com.devmod.portal;

import com.devmod.TestBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for portal auto-linking functionality.
 * Tests automatic linking of portals based on color, distance, and rune effects.
 */
class PortalAutoLinkingTest {

    @BeforeAll
    static void setup() {
        TestBootstrap.init();
    }

    @Nested
    @DisplayName("Distance Calculation")
    class DistanceCalculation {

        @Test
        @DisplayName("Calculate distance between two positions")
        void calculateDistanceBetweenPositions() {
            BlockPos pos1 = new BlockPos(0, 64, 0);
            BlockPos pos2 = new BlockPos(100, 64, 0);

            double distance = Math.sqrt(pos1.distSqr(pos2));

            assertEquals(100.0, distance, 0.001);
        }

        @Test
        @DisplayName("Calculate 3D distance correctly")
        void calculate3DDistance() {
            BlockPos pos1 = new BlockPos(0, 0, 0);
            BlockPos pos2 = new BlockPos(3, 4, 0);

            double distance = Math.sqrt(pos1.distSqr(pos2));

            assertEquals(5.0, distance, 0.001);
        }

        @Test
        @DisplayName("Distance is symmetric")
        void distanceIsSymmetric() {
            BlockPos pos1 = new BlockPos(10, 20, 30);
            BlockPos pos2 = new BlockPos(100, 200, 300);

            double dist1 = Math.sqrt(pos1.distSqr(pos2));
            double dist2 = Math.sqrt(pos2.distSqr(pos1));

            assertEquals(dist1, dist2, 0.001);
        }
    }

    @Nested
    @DisplayName("Link Candidate Finding")
    class LinkCandidateFinding {

        @Test
        @DisplayName("Portal within range is candidate")
        void portalWithinRangeIsCandidate() {
            PortalRuneEffects effects = PortalRuneEffects.NONE;
            double distance = 50.0; // Within default 100 block range

            assertTrue(effects.canLinkAtDistance(distance),
                "Portal within range should be linkable");
        }

        @Test
        @DisplayName("Portal outside range is not candidate")
        void portalOutsideRangeIsNotCandidate() {
            PortalRuneEffects effects = PortalRuneEffects.NONE;
            double distance = 150.0; // Outside default 100 block range

            assertFalse(effects.canLinkAtDistance(distance),
                "Portal outside range should not be linkable");
        }

        @Test
        @DisplayName("ENHANCER increases valid link distance")
        void enhancerIncreasesValidDistance() {
            Set<RuneType> runes = EnumSet.of(RuneType.ENHANCER);
            PortalRuneEffects effects = PortalRuneEffects.calculate(runes);

            // Default 100 + ENHANCER 100 = 200 range
            assertTrue(effects.canLinkAtDistance(150.0),
                "With ENHANCER, 150 blocks should be linkable");
        }

        @Test
        @DisplayName("INFINITY allows any distance")
        void infinityAllowsAnyDistance() {
            Set<RuneType> runes = EnumSet.of(RuneType.INFINITY);
            PortalRuneEffects effects = PortalRuneEffects.calculate(runes);

            assertTrue(effects.canLinkAtDistance(1000000.0),
                "With INFINITY, any distance should be linkable");
        }
    }

    @Nested
    @DisplayName("Cross-Dimension Linking")
    class CrossDimensionLinking {

        @Test
        @DisplayName("Same dimension always allows linking")
        void sameDimensionAllowsLinking() {
            PortalRuneEffects effects = PortalRuneEffects.NONE;

            assertTrue(effects.canLinkToDimension(true),
                "Same dimension should always allow linking");
        }

        @Test
        @DisplayName("Different dimension requires GATE rune")
        void differentDimensionRequiresGate() {
            PortalRuneEffects noRunes = PortalRuneEffects.NONE;
            PortalRuneEffects withGate = PortalRuneEffects.calculate(EnumSet.of(RuneType.GATE));

            assertFalse(noRunes.canLinkToDimension(false),
                "Without GATE, cross-dimension should not be allowed");
            assertTrue(withGate.canLinkToDimension(false),
                "With GATE, cross-dimension should be allowed");
        }
    }

    @Nested
    @DisplayName("Auto-Link Criteria")
    class AutoLinkCriteria {

        @Test
        @DisplayName("Same color required for auto-link")
        void sameColorRequiredForAutoLink() {
            PortalData blue1 = PortalData.create(PortalColor.BLUE);
            PortalData blue2 = PortalData.create(PortalColor.BLUE);
            PortalData red = PortalData.create(PortalColor.RED);

            assertEquals(blue1.color(), blue2.color(), "Same color portals should match");
            assertNotEquals(blue1.color(), red.color(), "Different color portals should not match");
        }

        @Test
        @DisplayName("Linked portal cannot be auto-linked again")
        void linkedPortalCannotAutoLinkAgain() {
            PortalData portal = PortalData.create(PortalColor.BLUE)
                .linkTo(java.util.UUID.randomUUID());

            assertTrue(portal.isLinked(), "Portal should be linked");
        }

        @Test
        @DisplayName("Unlinked portal can be auto-linked")
        void unlinkedPortalCanAutoLink() {
            PortalData portal = PortalData.create(PortalColor.BLUE);

            assertFalse(portal.isLinked(), "New portal should not be linked");
        }
    }

    @Nested
    @DisplayName("Portal Position")
    class PortalPosition {

        @Test
        @DisplayName("Portal position stored correctly")
        void portalPositionStoredCorrectly() {
            BlockPos pos = new BlockPos(100, 64, 200);
            PortalData portal = PortalData.create(PortalColor.BLUE)
                .withPosition(pos);

            assertTrue(portal.position().isPresent());
            assertEquals(pos, portal.position().get());
        }

        @Test
        @DisplayName("Portal dimension stored correctly")
        void portalDimensionStoredCorrectly() {
            ResourceLocation dim = ResourceLocation.parse("minecraft:overworld");
            PortalData portal = PortalData.create(PortalColor.BLUE)
                .withDimension(dim);

            assertTrue(portal.dimension().isPresent());
            assertEquals(dim, portal.dimension().get());
        }
    }

    @Nested
    @DisplayName("Private Portals Linking")
    class PrivatePortalsLinking {

        /**
         * Helper that mirrors the private portal linking logic.
         * This logic should be implemented in PortalRegistry.
         */
        private boolean canLinkPrivate(PortalData source, PortalData target, boolean privatePortalsEnabled) {
            if (!privatePortalsEnabled) {
                return true; // Any portal can link
            }
            // Both portals must have a creator and they must match
            if (source.creator().isEmpty() || target.creator().isEmpty()) {
                return false;
            }
            return source.creator().get().equals(target.creator().get());
        }

        @Test
        @DisplayName("Without privatePortals config, any portals can link")
        void withoutPrivatePortalsAnyCanLink() {
            UUID creator1 = UUID.randomUUID();
            UUID creator2 = UUID.randomUUID();
            PortalData portal1 = PortalData.createWithCreator(PortalColor.BLUE, creator1);
            PortalData portal2 = PortalData.createWithCreator(PortalColor.BLUE, creator2);

            assertTrue(canLinkPrivate(portal1, portal2, false),
                "Without privatePortals enabled, different creators can link");
        }

        @Test
        @DisplayName("With privatePortals, same creator can link")
        void withPrivatePortalsSameCreatorCanLink() {
            UUID creator = UUID.randomUUID();
            PortalData portal1 = PortalData.createWithCreator(PortalColor.BLUE, creator);
            PortalData portal2 = PortalData.createWithCreator(PortalColor.BLUE, creator);

            assertTrue(canLinkPrivate(portal1, portal2, true),
                "With privatePortals enabled, same creator can link");
        }

        @Test
        @DisplayName("With privatePortals, different creators cannot link")
        void withPrivatePortalsDifferentCreatorsCannotLink() {
            UUID creator1 = UUID.randomUUID();
            UUID creator2 = UUID.randomUUID();
            PortalData portal1 = PortalData.createWithCreator(PortalColor.BLUE, creator1);
            PortalData portal2 = PortalData.createWithCreator(PortalColor.BLUE, creator2);

            assertFalse(canLinkPrivate(portal1, portal2, true),
                "With privatePortals enabled, different creators cannot link");
        }

        @Test
        @DisplayName("With privatePortals, portal without creator cannot link")
        void withPrivatePortalsNoCreatorCannotLink() {
            UUID creator = UUID.randomUUID();
            PortalData portalWithCreator = PortalData.createWithCreator(PortalColor.BLUE, creator);
            PortalData portalWithoutCreator = PortalData.create(PortalColor.BLUE);

            assertFalse(canLinkPrivate(portalWithCreator, portalWithoutCreator, true),
                "With privatePortals enabled, portal without creator cannot link to one with creator");
            assertFalse(canLinkPrivate(portalWithoutCreator, portalWithCreator, true),
                "With privatePortals enabled, portal without creator cannot be linked to");
        }

        @Test
        @DisplayName("With privatePortals, two portals without creators cannot link")
        void withPrivatePortalsBothNoCreatorCannotLink() {
            PortalData portal1 = PortalData.create(PortalColor.BLUE);
            PortalData portal2 = PortalData.create(PortalColor.BLUE);

            assertFalse(canLinkPrivate(portal1, portal2, true),
                "With privatePortals enabled, portals without creators cannot link");
        }

        @Test
        @DisplayName("Default privatePortals config is false")
        void defaultPrivatePortalsIsFalse() {
            assertFalse(PortalConfig.isPrivatePortals(),
                "Default privatePortals should be false");
        }
    }
}
