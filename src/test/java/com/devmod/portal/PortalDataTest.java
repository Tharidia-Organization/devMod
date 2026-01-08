package com.devmod.portal;

import com.devmod.TestBootstrap;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for PortalData record.
 */
class PortalDataTest {

    @BeforeAll
    static void setup() {
        TestBootstrap.init();
    }

    @Nested
    @DisplayName("Portal Creation")
    class PortalCreation {

        @Test
        @DisplayName("New portal has valid UUID")
        void newPortalHasValidUuid() {
            PortalData portal = PortalData.create(PortalColor.BLUE);

            assertNotNull(portal.id());
            assertNotEquals(new UUID(0, 0), portal.id());
        }

        @Test
        @DisplayName("New portal is not linked")
        void newPortalIsNotLinked() {
            PortalData portal = PortalData.create(PortalColor.BLUE);

            assertFalse(portal.isLinked());
            assertTrue(portal.linkedPortalId().isEmpty());
        }

        @Test
        @DisplayName("Portal color is preserved")
        void portalColorPreserved() {
            PortalData portal = PortalData.create(PortalColor.PURPLE);

            assertEquals(PortalColor.PURPLE, portal.color());
        }

        @Test
        @DisplayName("Each portal has unique UUID")
        void eachPortalHasUniqueUuid() {
            PortalData portal1 = PortalData.create(PortalColor.BLUE);
            PortalData portal2 = PortalData.create(PortalColor.BLUE);

            assertNotEquals(portal1.id(), portal2.id());
        }

        @Test
        @DisplayName("New portal has no rune")
        void newPortalHasNoRune() {
            PortalData portal = PortalData.create(PortalColor.RED);

            assertTrue(portal.rune().isEmpty());
        }
    }

    @Nested
    @DisplayName("Portal Linking")
    class PortalLinking {

        @Test
        @DisplayName("Two portals can be linked")
        void twoPortalsCanBeLinked() {
            PortalData portal1 = PortalData.create(PortalColor.BLUE);
            PortalData portal2 = PortalData.create(PortalColor.BLUE);

            PortalData linked1 = portal1.linkTo(portal2.id());
            PortalData linked2 = portal2.linkTo(portal1.id());

            assertTrue(linked1.isLinked());
            assertTrue(linked2.isLinked());
            assertEquals(portal2.id(), linked1.linkedPortalId().orElse(null));
            assertEquals(portal1.id(), linked2.linkedPortalId().orElse(null));
        }

        @Test
        @DisplayName("Portal can be unlinked")
        void portalCanBeUnlinked() {
            PortalData portal = PortalData.create(PortalColor.BLUE)
                .linkTo(UUID.randomUUID());

            assertTrue(portal.isLinked());

            PortalData unlinked = portal.unlink();

            assertFalse(unlinked.isLinked());
            assertTrue(unlinked.linkedPortalId().isEmpty());
        }

        @Test
        @DisplayName("Linking preserves portal ID")
        void linkingPreservesId() {
            PortalData portal = PortalData.create(PortalColor.BLUE);
            UUID originalId = portal.id();

            PortalData linked = portal.linkTo(UUID.randomUUID());

            assertEquals(originalId, linked.id());
        }

        @Test
        @DisplayName("Linking preserves color")
        void linkingPreservesColor() {
            PortalData portal = PortalData.create(PortalColor.ORANGE);

            PortalData linked = portal.linkTo(UUID.randomUUID());

            assertEquals(PortalColor.ORANGE, linked.color());
        }

        @Test
        @DisplayName("Linking preserves rune")
        void linkingPreservesRune() {
            PortalData portal = PortalData.create(PortalColor.BLUE)
                .withRune(RuneType.HASTE);

            PortalData linked = portal.linkTo(UUID.randomUUID());

            assertTrue(linked.rune().isPresent());
            assertEquals(RuneType.HASTE, linked.rune().get());
        }
    }

    @Nested
    @DisplayName("Portal Dimensions")
    class PortalDimensions {

        @Test
        @DisplayName("Portal can store dimension info")
        void portalCanStoreDimension() {
            ResourceLocation dimension = ResourceLocation.parse("minecraft:the_nether");
            PortalData portal = PortalData.create(PortalColor.BLUE)
                .withDimension(dimension);

            assertTrue(portal.dimension().isPresent());
            assertEquals(dimension, portal.dimension().get());
        }

        @Test
        @DisplayName("Inter-dimensional portal detected correctly")
        void interDimensionalDetected() {
            PortalData portal1 = PortalData.create(PortalColor.BLUE)
                .withDimension(ResourceLocation.parse("minecraft:overworld"));
            PortalData portal2 = PortalData.create(PortalColor.BLUE)
                .withDimension(ResourceLocation.parse("minecraft:the_nether"));

            assertTrue(portal1.isInterDimensional(portal2));
            assertTrue(portal2.isInterDimensional(portal1));
        }

        @Test
        @DisplayName("Intra-dimensional portal detected correctly")
        void intraDimensionalDetected() {
            PortalData portal1 = PortalData.create(PortalColor.BLUE)
                .withDimension(ResourceLocation.parse("minecraft:overworld"));
            PortalData portal2 = PortalData.create(PortalColor.BLUE)
                .withDimension(ResourceLocation.parse("minecraft:overworld"));

            assertFalse(portal1.isInterDimensional(portal2));
        }

        @Test
        @DisplayName("Null dimensions are not inter-dimensional")
        void nullDimensionsNotInterDimensional() {
            PortalData portal1 = PortalData.create(PortalColor.BLUE);
            PortalData portal2 = PortalData.create(PortalColor.BLUE)
                .withDimension(ResourceLocation.parse("minecraft:the_nether"));

            assertFalse(portal1.isInterDimensional(portal2));
            assertFalse(portal2.isInterDimensional(portal1));
        }
    }

    @Nested
    @DisplayName("Rune Enhancement")
    class RuneEnhancement {

        @Test
        @DisplayName("Portal can have rune applied")
        void portalCanHaveRune() {
            PortalData portal = PortalData.create(PortalColor.BLUE)
                .withRune(RuneType.HASTE);

            assertTrue(portal.rune().isPresent());
            assertEquals(RuneType.HASTE, portal.rune().get());
        }

        @Test
        @DisplayName("Portal without rune returns empty")
        void portalWithoutRuneReturnsEmpty() {
            PortalData portal = PortalData.create(PortalColor.BLUE);

            assertTrue(portal.rune().isEmpty());
        }

        @Test
        @DisplayName("Rune can be removed")
        void runeCanBeRemoved() {
            PortalData portal = PortalData.create(PortalColor.BLUE)
                .withRune(RuneType.INFINITY);

            PortalData noRune = portal.withRune(null);

            assertTrue(noRune.rune().isEmpty());
        }

        @Test
        @DisplayName("All rune types can be applied")
        void allRuneTypesCanBeApplied() {
            for (RuneType rune : RuneType.values()) {
                PortalData portal = PortalData.create(PortalColor.BLUE)
                    .withRune(rune);

                assertTrue(portal.rune().isPresent());
                assertEquals(rune, portal.rune().get());
            }
        }
    }

    @Nested
    @DisplayName("Frame Count")
    class FrameCount {

        @Test
        @DisplayName("New portal has zero frame count")
        void newPortalHasZeroFrameCount() {
            PortalData portal = PortalData.create(PortalColor.BLUE);

            assertEquals(0, portal.frameBlockCount());
        }

        @Test
        @DisplayName("Frame count can be set")
        void frameCountCanBeSet() {
            PortalData portal = PortalData.create(PortalColor.BLUE)
                .withFrameCount(14);

            assertEquals(14, portal.frameBlockCount());
        }

        @Test
        @DisplayName("Frame count preserved through linking")
        void frameCountPreservedThroughLinking() {
            PortalData portal = PortalData.create(PortalColor.BLUE)
                .withFrameCount(20);

            PortalData linked = portal.linkTo(UUID.randomUUID());

            assertEquals(20, linked.frameBlockCount());
        }
    }

    @Nested
    @DisplayName("Creator Ownership")
    class CreatorOwnership {

        @Test
        @DisplayName("New portal has no creator by default")
        void newPortalHasNoCreator() {
            PortalData portal = PortalData.create(PortalColor.BLUE);

            assertTrue(portal.creator().isEmpty());
        }

        @Test
        @DisplayName("Portal can be created with creator")
        void portalCanBeCreatedWithCreator() {
            UUID creatorId = UUID.randomUUID();
            PortalData portal = PortalData.createWithCreator(PortalColor.BLUE, creatorId);

            assertTrue(portal.creator().isPresent());
            assertEquals(creatorId, portal.creator().get());
        }

        @Test
        @DisplayName("Portal creator can be set with withCreator")
        void portalCreatorCanBeSet() {
            UUID creatorId = UUID.randomUUID();
            PortalData portal = PortalData.create(PortalColor.BLUE)
                .withCreator(creatorId);

            assertTrue(portal.creator().isPresent());
            assertEquals(creatorId, portal.creator().get());
        }

        @Test
        @DisplayName("Creator preserved through linking")
        void creatorPreservedThroughLinking() {
            UUID creatorId = UUID.randomUUID();
            PortalData portal = PortalData.createWithCreator(PortalColor.BLUE, creatorId)
                .linkTo(UUID.randomUUID());

            assertTrue(portal.creator().isPresent());
            assertEquals(creatorId, portal.creator().get());
        }

        @Test
        @DisplayName("Creator preserved through unlinking")
        void creatorPreservedThroughUnlinking() {
            UUID creatorId = UUID.randomUUID();
            PortalData portal = PortalData.createWithCreator(PortalColor.BLUE, creatorId)
                .linkTo(UUID.randomUUID())
                .unlink();

            assertTrue(portal.creator().isPresent());
            assertEquals(creatorId, portal.creator().get());
        }

        @Test
        @DisplayName("Creator preserved through withRune")
        void creatorPreservedThroughWithRune() {
            UUID creatorId = UUID.randomUUID();
            PortalData portal = PortalData.createWithCreator(PortalColor.BLUE, creatorId)
                .withRune(RuneType.HASTE);

            assertTrue(portal.creator().isPresent());
            assertEquals(creatorId, portal.creator().get());
        }

        @Test
        @DisplayName("Creator can be cleared")
        void creatorCanBeCleared() {
            UUID creatorId = UUID.randomUUID();
            PortalData portal = PortalData.createWithCreator(PortalColor.BLUE, creatorId)
                .withCreator(null);

            assertTrue(portal.creator().isEmpty());
        }

        @Test
        @DisplayName("isOwnedBy returns true for matching creator")
        void isOwnedByReturnsTrueForMatchingCreator() {
            UUID creatorId = UUID.randomUUID();
            PortalData portal = PortalData.createWithCreator(PortalColor.BLUE, creatorId);

            assertTrue(portal.isOwnedBy(creatorId));
        }

        @Test
        @DisplayName("isOwnedBy returns false for non-matching creator")
        void isOwnedByReturnsFalseForNonMatchingCreator() {
            UUID creatorId = UUID.randomUUID();
            UUID otherId = UUID.randomUUID();
            PortalData portal = PortalData.createWithCreator(PortalColor.BLUE, creatorId);

            assertFalse(portal.isOwnedBy(otherId));
        }

        @Test
        @DisplayName("isOwnedBy returns false for portal without creator")
        void isOwnedByReturnsFalseForNoCreator() {
            PortalData portal = PortalData.create(PortalColor.BLUE);

            assertFalse(portal.isOwnedBy(UUID.randomUUID()));
        }

        @Test
        @DisplayName("hasCreator returns true when creator is set")
        void hasCreatorReturnsTrueWhenSet() {
            PortalData portal = PortalData.createWithCreator(PortalColor.BLUE, UUID.randomUUID());

            assertTrue(portal.hasCreator());
        }

        @Test
        @DisplayName("hasCreator returns false when creator is not set")
        void hasCreatorReturnsFalseWhenNotSet() {
            PortalData portal = PortalData.create(PortalColor.BLUE);

            assertFalse(portal.hasCreator());
        }
    }

    @Nested
    @DisplayName("Immutability")
    class Immutability {

        @Test
        @DisplayName("linkTo returns new instance")
        void linkToReturnsNewInstance() {
            PortalData original = PortalData.create(PortalColor.BLUE);
            PortalData linked = original.linkTo(UUID.randomUUID());

            assertNotSame(original, linked);
            assertFalse(original.isLinked());
            assertTrue(linked.isLinked());
        }

        @Test
        @DisplayName("unlink returns new instance")
        void unlinkReturnsNewInstance() {
            PortalData original = PortalData.create(PortalColor.BLUE)
                .linkTo(UUID.randomUUID());
            PortalData unlinked = original.unlink();

            assertNotSame(original, unlinked);
            assertTrue(original.isLinked());
            assertFalse(unlinked.isLinked());
        }

        @Test
        @DisplayName("withRune returns new instance")
        void withRuneReturnsNewInstance() {
            PortalData original = PortalData.create(PortalColor.BLUE);
            PortalData withRune = original.withRune(RuneType.GATE);

            assertNotSame(original, withRune);
            assertTrue(original.rune().isEmpty());
            assertTrue(withRune.rune().isPresent());
        }
    }
}
