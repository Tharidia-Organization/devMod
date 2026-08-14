package com.devmod.portal;

import java.util.HashMap;
import java.util.Map;

import com.devmod.TestBootstrap;
import com.devmod.portal.block.CustomPortalBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests the flood fill behind {@code clearPortalBlocks} and {@code updatePortalBlockState}: both
 * must cover the whole portal, up to the largest interior the frame detector accepts
 * ({@link PortalFrameDetector#MAX_INTERIOR_BLOCKS} = 21x21). A short fill strands portal blocks
 * that no player can then remove: they are unbreakable, blast proof and drop nothing.
 *
 * <p>Block states are mocked: {@code Bootstrap} freezes the block registry, so a real
 * {@link CustomPortalBlock} cannot be constructed from a unit test.
 */
class PortalBlockClearingTest {

    private static final ResourceLocation OVERWORLD = ResourceLocation.parse("minecraft:overworld");
    private static final int SIDE = PortalFrameDetector.MAX_INTERIOR_SIZE;

    private static CustomPortalBlock portalBlock;
    private static BlockState blueState;
    private static BlockState blueLinkedState;
    private static BlockState redState;
    private static BlockState airState;

    private Map<BlockPos, BlockState> world;
    private MinecraftServer server;
    private PortalRegistry registry;

    @BeforeAll
    static void bootstrap() {
        TestBootstrap.init();

        portalBlock = mock(CustomPortalBlock.class);
        blueState = portalState(PortalColor.BLUE, false);
        blueLinkedState = portalState(PortalColor.BLUE, true);
        redState = portalState(PortalColor.RED, false);

        when(blueState.setValue(CustomPortalBlock.LINKED, true)).thenReturn(blueLinkedState);
        when(blueLinkedState.setValue(CustomPortalBlock.LINKED, false)).thenReturn(blueState);

        airState = mock(BlockState.class);
        when(airState.getBlock()).thenReturn(mock(Block.class));
    }

    private static BlockState portalState(PortalColor color, boolean linked) {
        BlockState state = mock(BlockState.class);
        when(state.getBlock()).thenReturn(portalBlock);
        when(state.getValue(CustomPortalBlock.COLOR)).thenReturn(color);
        when(state.getValue(CustomPortalBlock.LINKED)).thenReturn(linked);
        return state;
    }

    @BeforeEach
    void setUp() {
        world = new HashMap<>();
        registry = new PortalRegistry();

        ServerLevel level = mock(ServerLevel.class);
        when(level.getBlockState(any())).thenAnswer(
            invocation -> world.getOrDefault(((BlockPos) invocation.getArgument(0)).immutable(), airState));
        when(level.removeBlock(any(), anyBoolean())).thenAnswer(
            invocation -> world.remove(((BlockPos) invocation.getArgument(0)).immutable()) != null);
        when(level.setBlock(any(), any(), anyInt())).thenAnswer(invocation -> {
            world.put(((BlockPos) invocation.getArgument(0)).immutable(), invocation.getArgument(1));
            return true;
        });

        server = mock(MinecraftServer.class);
        when(server.getLevel(any())).thenReturn(level);
    }

    /** Fills a {@code width x height} slab of portal blocks in the z=0 plane, bottom-left at origin. */
    private void fill(BlockPos origin, int width, int height, BlockState state) {
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                world.put(origin.offset(x, y, 0), state);
            }
        }
    }

    private PortalData registerPortal(BlockPos center) {
        PortalData portal = PortalData.create(PortalColor.BLUE, OVERWORLD, center, 0);
        registry.register(portal);
        return portal;
    }

    @Nested
    @DisplayName("Clearing")
    class Clearing {

        @Test
        @DisplayName("The largest legal portal is cleared in full")
        void clearsLargestLegalPortal() {
            fill(BlockPos.ZERO, SIDE, SIDE, blueState);
            assertEquals(PortalFrameDetector.MAX_INTERIOR_BLOCKS, world.size(), "test setup");
            PortalData portal = registerPortal(new BlockPos(SIDE / 2, SIDE / 2, 0));

            registry.unregister(portal.id(), server);

            assertTrue(world.isEmpty(),
                "Portal blocks left behind are unbreakable and unobtainable: " + world.keySet());
        }

        @Test
        @DisplayName("A neighbouring portal of another color survives")
        void otherColorSurvives() {
            fill(BlockPos.ZERO, SIDE, SIDE, blueState);
            BlockPos red = new BlockPos(SIDE, 0, 0);
            world.put(red, redState);
            PortalData portal = registerPortal(new BlockPos(SIDE / 2, SIDE / 2, 0));

            registry.unregister(portal.id(), server);

            assertEquals(Map.of(red, redState), world, "Only the portal's own run belongs to it");
        }

        @Test
        @DisplayName("An oversized run is truncated, not half-applied")
        void oversizedRunFailsSafe() {
            int total = SIDE * (SIDE + 1);
            fill(BlockPos.ZERO, SIDE, SIDE + 1, blueState);
            PortalData portal = registerPortal(new BlockPos(SIDE / 2, SIDE / 2, 0));

            registry.unregister(portal.id(), server);

            assertTrue(registry.get(portal.id()).isEmpty(), "The registry entry still goes away");
            assertEquals(total - PortalFrameDetector.MAX_INTERIOR_BLOCKS, world.size(),
                "The fill stops at the bound instead of walking an unbounded blob");
        }

        @Test
        @DisplayName("A center with no portal block clears nothing")
        void missingCenterClearsNothing() {
            BlockPos stray = new BlockPos(50, 0, 0);
            world.put(stray, blueState);
            PortalData portal = registerPortal(new BlockPos(0, 0, 0));

            registry.unregister(portal.id(), server);

            assertEquals(Map.of(stray, blueState), world, "A disconnected run is not this portal's");
        }
    }

    @Nested
    @DisplayName("Linked state")
    class LinkedState {

        @Test
        @DisplayName("Linking marks every block of the largest legal portal")
        void linkMarksWholePortal() {
            fill(BlockPos.ZERO, SIDE, SIDE, blueState);
            PortalData portal = registerPortal(new BlockPos(SIDE / 2, SIDE / 2, 0));
            world.put(new BlockPos(100, 0, 0), blueState);
            PortalData partner = registerPortal(new BlockPos(100, 0, 0));

            assertTrue(registry.link(portal.id(), partner.id(), server));

            for (Map.Entry<BlockPos, BlockState> entry : world.entrySet()) {
                assertSame(blueLinkedState, entry.getValue(),
                    "Block at " + entry.getKey() + " still shows the unlinked appearance");
            }
        }

        @Test
        @DisplayName("Unlinking clears the mark from every block")
        void unlinkClearsWholePortal() {
            fill(BlockPos.ZERO, SIDE, SIDE, blueLinkedState);
            PortalData portal = registerPortal(new BlockPos(SIDE / 2, SIDE / 2, 0));
            world.put(new BlockPos(100, 0, 0), blueLinkedState);
            PortalData partner = registerPortal(new BlockPos(100, 0, 0));
            assertTrue(registry.link(portal.id(), partner.id()));

            assertTrue(registry.unlink(portal.id(), server));

            for (Map.Entry<BlockPos, BlockState> entry : world.entrySet()) {
                assertFalse(entry.getValue().getValue(CustomPortalBlock.LINKED),
                    "Block at " + entry.getKey() + " kept a stale LINKED appearance");
            }
        }
    }
}
