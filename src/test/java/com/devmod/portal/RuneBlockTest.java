package com.devmod.portal;

import java.util.Objects;

import javax.annotation.Nullable;

import com.devmod.TestBootstrap;
import com.devmod.portal.block.RuneBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.state.properties.AttachFace;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Unit tests for the RuneBlock system.
 * Tests the 5 rune types as placeable blocks that attach to portal frames.
 */
class RuneBlockTest {

    @BeforeAll
    static void setup() {
        TestBootstrap.init();
    }

    @Nested
    @DisplayName("RuneType Enum")
    class RuneTypeEnumTests {

        @Test
        @DisplayName("All 5 rune types are present")
        void all5RuneTypesPresent() {
            assertEquals(5, RuneType.values().length,
                "Should have exactly 5 rune types: HASTE, GATE, ENHANCER, STRONG_ENHANCER, INFINITY");
        }

        @Test
        @DisplayName("Rune types have correct serialized names")
        void runeTypesHaveCorrectNames() {
            assertEquals("haste", RuneType.HASTE.getSerializedName());
            assertEquals("gate", RuneType.GATE.getSerializedName());
            assertEquals("enhancer", RuneType.ENHANCER.getSerializedName());
            assertEquals("strong_enhancer", RuneType.STRONG_ENHANCER.getSerializedName());
            assertEquals("infinity", RuneType.INFINITY.getSerializedName());
        }

        @Test
        @DisplayName("Each rune has unique glow color")
        void eachRuneHasUniqueColor() {
            RuneType[] runes = RuneType.values();
            for (int i = 0; i < runes.length; i++) {
                for (int j = i + 1; j < runes.length; j++) {
                    assertNotEquals(runes[i].getGlowColor(), runes[j].getGlowColor(),
                        runes[i].name() + " and " + runes[j].name() + " should have different colors");
                }
            }
        }

        @Test
        @DisplayName("byIndex returns correct rune for all indices")
        void byIndexReturnsCorrectRune() {
            assertEquals(RuneType.HASTE, RuneType.byIndex(0));
            assertEquals(RuneType.GATE, RuneType.byIndex(1));
            assertEquals(RuneType.ENHANCER, RuneType.byIndex(2));
            assertEquals(RuneType.STRONG_ENHANCER, RuneType.byIndex(3));
            assertEquals(RuneType.INFINITY, RuneType.byIndex(4));
        }

        @Test
        @DisplayName("byIndex returns null for invalid indices")
        void byIndexInvalidReturnsNull() {
            assertNull(RuneType.byIndex(-1));
            assertNull(RuneType.byIndex(5));
            assertNull(RuneType.byIndex(100));
        }

        @Test
        @DisplayName("getIndex returns correct ordinal")
        void getIndexReturnsOrdinal() {
            for (RuneType rune : RuneType.values()) {
                assertEquals(rune.ordinal(), rune.getIndex(),
                    rune.name() + " index should match ordinal");
            }
        }
    }

    @Nested
    @DisplayName("Rune Range Values")
    class RuneRangeTests {

        @Test
        @DisplayName("ENHANCER provides range bonus")
        void enhancerProvidesRangeBonus() {
            assertTrue(RuneType.ENHANCER.getRangeBonus() > 0,
                "ENHANCER should provide positive range bonus");
        }

        @Test
        @DisplayName("STRONG_ENHANCER provides more range than ENHANCER")
        void strongEnhancerProvidesMoreRange() {
            assertTrue(RuneType.STRONG_ENHANCER.getRangeBonus() > RuneType.ENHANCER.getRangeBonus(),
                "STRONG_ENHANCER should provide more range than ENHANCER");
        }

        @Test
        @DisplayName("INFINITY provides unlimited range")
        void infinityProvidesUnlimitedRange() {
            assertEquals(Integer.MAX_VALUE, RuneType.INFINITY.getRangeBonus(),
                "INFINITY should provide unlimited (MAX_VALUE) range");
        }

        @Test
        @DisplayName("HASTE and GATE provide no range bonus")
        void hasteAndGateNoRangeBonus() {
            assertEquals(0, RuneType.HASTE.getRangeBonus(),
                "HASTE should not affect range");
            assertEquals(0, RuneType.GATE.getRangeBonus(),
                "GATE should not affect range");
        }
    }

    @Nested
    @DisplayName("Rune Effect Flags")
    class RuneEffectTests {

        @Test
        @DisplayName("HASTE allows instant teleport")
        void hasteAllowsInstantTeleport() {
            assertTrue(RuneType.HASTE.allowsInstantTeleport(),
                "HASTE should allow instant teleportation");
        }

        @Test
        @DisplayName("Only HASTE allows instant teleport")
        void onlyHasteAllowsInstantTeleport() {
            for (RuneType rune : RuneType.values()) {
                if (rune == RuneType.HASTE) {
                    assertTrue(rune.allowsInstantTeleport());
                } else {
                    assertFalse(rune.allowsInstantTeleport(),
                        rune.name() + " should not allow instant teleport");
                }
            }
        }

        @Test
        @DisplayName("GATE allows cross-dimension linking")
        void gateAllowsCrossDimension() {
            assertTrue(RuneType.GATE.allowsCrossDimension(),
                "GATE should allow cross-dimension linking");
        }

        @Test
        @DisplayName("Only GATE allows cross-dimension linking")
        void onlyGateAllowsCrossDimension() {
            for (RuneType rune : RuneType.values()) {
                if (rune == RuneType.GATE) {
                    assertTrue(rune.allowsCrossDimension());
                } else {
                    assertFalse(rune.allowsCrossDimension(),
                        rune.name() + " should not allow cross-dimension");
                }
            }
        }
    }

    @Nested
    @DisplayName("Wall-Mounted Block State Properties")
    class WallMountedProperties {

        @Test
        @DisplayName("RuneBlock has FACING property")
        void runeBlockHasFacingProperty() {
            assertNotNull(RuneBlock.FACING,
                "RuneBlock should have a FACING property");
        }

        @Test
        @DisplayName("RuneBlock has FACE property for wall/floor/ceiling")
        void runeBlockHasFaceProperty() {
            assertNotNull(RuneBlock.FACE,
                "RuneBlock should have a FACE property for attachment");
        }

        @Test
        @DisplayName("Default state has WALL face and NORTH facing")
        void defaultStateIsWallNorth() {
            // Skip if registries not available (requires game test environment)
            RuneBlock block;
            try {
                block = new RuneBlock(RuneType.HASTE,
                    net.minecraft.world.level.block.state.BlockBehaviour.Properties.of());
            } catch (IllegalStateException e) {
                assumeTrue(false, "Skipped: Block creation requires registry initialization");
                return;
            }
            var defaultState = block.defaultBlockState();

            assertEquals(AttachFace.WALL, defaultState.getValue(RuneBlock.FACE),
                "Default face should be WALL");
            assertEquals(Direction.NORTH, defaultState.getValue(RuneBlock.FACING),
                "Default facing should be NORTH");
        }
    }

    @Nested
    @DisplayName("Shape by Direction")
    class ShapeByDirection {

        @Test
        @DisplayName("Floor shape is flat (1 pixel high)")
        void floorShapeIsFlat() {
            var shape = RuneBlock.FLOOR_SHAPE;
            assertNotNull(shape, "Floor shape should exist");
            assertEquals(1.0, shape.bounds().getYsize() * 16, 0.01,
                "Floor shape should be 1 pixel high");
        }

        @Test
        @DisplayName("Ceiling shape is flat at top")
        void ceilingShapeIsFlat() {
            var shape = RuneBlock.CEILING_SHAPE;
            assertNotNull(shape, "Ceiling shape should exist");
            assertEquals(1.0, shape.bounds().getYsize() * 16, 0.01,
                "Ceiling shape should be 1 pixel high");
        }

        @Test
        @DisplayName("Wall shapes are thin (1 pixel thick)")
        void wallShapesAreThin() {
            var northShape = RuneBlock.NORTH_SHAPE;
            var southShape = RuneBlock.SOUTH_SHAPE;
            var eastShape = RuneBlock.EAST_SHAPE;
            var westShape = RuneBlock.WEST_SHAPE;

            assertNotNull(northShape, "North shape should exist");
            assertNotNull(southShape, "South shape should exist");
            assertNotNull(eastShape, "East shape should exist");
            assertNotNull(westShape, "West shape should exist");

            assertEquals(1.0, northShape.bounds().getZsize() * 16, 0.01,
                "North shape should be 1 pixel thick in Z");
            assertEquals(1.0, southShape.bounds().getZsize() * 16, 0.01,
                "South shape should be 1 pixel thick in Z");
            assertEquals(1.0, eastShape.bounds().getXsize() * 16, 0.01,
                "East shape should be 1 pixel thick in X");
            assertEquals(1.0, westShape.bounds().getXsize() * 16, 0.01,
                "West shape should be 1 pixel thick in X");
        }
    }

    @Nested
    @DisplayName("Rune Block Instance Properties")
    class RuneBlockInstanceProperties {

        @Nullable
        private RuneBlock createTestBlock(RuneType type) {
            try {
                return new RuneBlock(type,
                    net.minecraft.world.level.block.state.BlockBehaviour.Properties.of());
            } catch (IllegalStateException e) {
                // Registries not available in unit test environment
                return null;
            }
        }

        @Test
        @DisplayName("Haste rune block returns HASTE type")
        void hasteRuneReturnsHasteType() {
            RuneBlock hasteBlock = createTestBlock(RuneType.HASTE);
            assumeTrue(hasteBlock != null, "Skipped: Block creation requires registry initialization");
            RuneBlock nonNullBlock = Objects.requireNonNull(hasteBlock, "hasteBlock");
            assertEquals(RuneType.HASTE, nonNullBlock.getRuneType(),
                "HASTE rune block should return HASTE type");
        }

        @Test
        @DisplayName("Gate rune block returns GATE type")
        void gateRuneReturnsGateType() {
            RuneBlock gateBlock = createTestBlock(RuneType.GATE);
            assumeTrue(gateBlock != null, "Skipped: Block creation requires registry initialization");
            RuneBlock nonNullBlock = Objects.requireNonNull(gateBlock, "gateBlock");
            assertEquals(RuneType.GATE, nonNullBlock.getRuneType(),
                "GATE rune block should return GATE type");
        }

        @Test
        @DisplayName("Rune blocks emit light level 7")
        void runeBlocksEmitLight() {
            RuneBlock hasteBlock = createTestBlock(RuneType.HASTE);
            assumeTrue(hasteBlock != null, "Skipped: Block creation requires registry initialization");
            RuneBlock nonNullBlock = Objects.requireNonNull(hasteBlock, "hasteBlock");
            assertEquals(7, nonNullBlock.getLightEmission(
                nonNullBlock.defaultBlockState(), EmptyBlockGetter.INSTANCE, BlockPos.ZERO),
                "Rune blocks should emit light level 7");
        }
    }

    @Nested
    @DisplayName("Mounted Position Calculation")
    class MountedPositionCalculation {

        @Test
        @DisplayName("getMountedOffset returns correct offset for FLOOR")
        void getMountedOffsetFloor() {
            var offset = RuneBlock.getMountedOffset(AttachFace.FLOOR, Direction.NORTH);
            assertEquals(Direction.DOWN, offset,
                "FLOOR mount should attach to block below");
        }

        @Test
        @DisplayName("getMountedOffset returns correct offset for CEILING")
        void getMountedOffsetCeiling() {
            var offset = RuneBlock.getMountedOffset(AttachFace.CEILING, Direction.NORTH);
            assertEquals(Direction.UP, offset,
                "CEILING mount should attach to block above");
        }

        @Test
        @DisplayName("getMountedOffset returns opposite direction for WALL")
        void getMountedOffsetWall() {
            assertEquals(Direction.SOUTH, RuneBlock.getMountedOffset(AttachFace.WALL, Direction.NORTH),
                "WALL NORTH should attach to block at SOUTH");
            assertEquals(Direction.NORTH, RuneBlock.getMountedOffset(AttachFace.WALL, Direction.SOUTH),
                "WALL SOUTH should attach to block at NORTH");
            assertEquals(Direction.WEST, RuneBlock.getMountedOffset(AttachFace.WALL, Direction.EAST),
                "WALL EAST should attach to block at WEST");
            assertEquals(Direction.EAST, RuneBlock.getMountedOffset(AttachFace.WALL, Direction.WEST),
                "WALL WEST should attach to block at EAST");
        }
    }
}
