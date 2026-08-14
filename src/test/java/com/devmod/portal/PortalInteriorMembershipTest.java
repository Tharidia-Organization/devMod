package com.devmod.portal;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import javax.annotation.Nullable;

import com.devmod.TestBootstrap;
import com.devmod.portal.block.CustomPortalBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests that {@link PortalRegistry#findPortalContaining} resolves a position to the portal it is
 * actually inside, and that the cached answer follows both the world and the registry.
 *
 * <p>The layout is a portal hub: two blue portals close enough that the old nearest-center
 * heuristic sent an entity standing in the wide portal to the small one. Portal A's interior
 * spans x 0..10 at y 0..2 (center 5,1,0), portal B's spans x 13..15 (center 14,1,0), and the two
 * are separated by the frame gap at x 11..12. The position (10,1,0) is inside A but 4 blocks from
 * B's center and 5 from A's, so nearest-center resolves it to B while membership resolves it to A.
 *
 * <p>Block states are mocked: {@code Bootstrap} freezes the block registry, so a real
 * {@link CustomPortalBlock} cannot be constructed from a unit test.
 */
class PortalInteriorMembershipTest {

    private static final ResourceLocation OVERWORLD = ResourceLocation.parse("minecraft:overworld");

    private static final BlockPos A_CENTER = new BlockPos(5, 1, 0);
    private static final BlockPos B_CENTER = new BlockPos(14, 1, 0);
    /** Inside portal A, but nearer to B's center than to A's. */
    private static final BlockPos A_EDGE_NEAR_B = new BlockPos(10, 1, 0);

    private static final Map<PortalColor, BlockState> PORTAL_STATES = new EnumMap<>(PortalColor.class);
    private static BlockState airState;

    private FakeWorld world;
    private PortalRegistry registry;
    private PortalData portalA;
    private PortalData portalB;

    @BeforeAll
    static void bootstrap() {
        TestBootstrap.init();

        CustomPortalBlock portalBlock = mock(CustomPortalBlock.class);
        for (PortalColor color : PortalColor.values()) {
            BlockState state = mock(BlockState.class);
            when(state.getBlock()).thenReturn(portalBlock);
            when(state.getValue(CustomPortalBlock.COLOR)).thenReturn(color);
            PORTAL_STATES.put(color, state);
        }

        airState = mock(BlockState.class);
        when(airState.getBlock()).thenReturn(mock(Block.class));
    }

    @BeforeEach
    void setUp() {
        world = new FakeWorld();
        registry = new PortalRegistry();

        fillInterior(0, 10, PortalColor.BLUE);
        fillInterior(13, 15, PortalColor.BLUE);

        portalA = PortalData.create(PortalColor.BLUE, OVERWORLD, A_CENTER, 0);
        portalB = PortalData.create(PortalColor.BLUE, OVERWORLD, B_CENTER, 0);
        registry.register(portalA);
        registry.register(portalB);
    }

    private void fillInterior(int minX, int maxX, PortalColor color) {
        BlockState state = PORTAL_STATES.get(color);
        for (int x = minX; x <= maxX; x++) {
            for (int y = 0; y <= 2; y++) {
                world.set(new BlockPos(x, y, 0), state);
            }
        }
    }

    private Optional<PortalData> find(BlockPos pos, PortalColor color) {
        return registry.findPortalContaining(world, OVERWORLD, pos, color);
    }

    @Nested
    @DisplayName("Interior membership")
    class Membership {

        @Test
        @DisplayName("A position resolves to the portal it is inside, not the nearest center")
        void resolvesToContainingPortal() {
            assertEquals(Optional.of(portalA), find(A_EDGE_NEAR_B, PortalColor.BLUE),
                "Position is inside portal A even though portal B's center is closer");
        }

        @Test
        @DisplayName("Every interior position of each portal resolves to its own portal")
        void wholeInteriorResolves() {
            for (int y = 0; y <= 2; y++) {
                for (int x = 0; x <= 10; x++) {
                    assertEquals(Optional.of(portalA), find(new BlockPos(x, y, 0), PortalColor.BLUE),
                        "x=" + x + " y=" + y + " belongs to portal A");
                }
                for (int x = 13; x <= 15; x++) {
                    assertEquals(Optional.of(portalB), find(new BlockPos(x, y, 0), PortalColor.BLUE),
                        "x=" + x + " y=" + y + " belongs to portal B");
                }
            }
        }

        @Test
        @DisplayName("A color mismatch resolves to nothing")
        void colorMustMatch() {
            assertTrue(find(A_EDGE_NEAR_B, PortalColor.RED).isEmpty());
        }

        @Test
        @DisplayName("A run of portal blocks with no registered center resolves to nothing")
        void unregisteredInteriorResolvesToNothing() {
            fillInterior(20, 22, PortalColor.BLUE);
            BlockPos pos = new BlockPos(21, 1, 0);

            assertTrue(find(pos, PortalColor.BLUE).isEmpty());
            // The negative is cached, so the repeat must give the same answer.
            assertTrue(find(pos, PortalColor.BLUE).isEmpty());
        }

        @Test
        @DisplayName("A position with no portal block resolves to nothing")
        void airResolvesToNothing() {
            assertTrue(find(new BlockPos(11, 1, 0), PortalColor.BLUE).isEmpty());
        }

        @Test
        @DisplayName("A neighbouring portal of another color is not part of the run")
        void otherColorDoesNotJoinTheRun() {
            fillInterior(11, 12, PortalColor.RED);

            assertEquals(Optional.of(portalA), find(A_EDGE_NEAR_B, PortalColor.BLUE));
            assertTrue(find(new BlockPos(12, 1, 0), PortalColor.RED).isEmpty(),
                "The red blocks bridge A and B but belong to neither");
        }
    }

    @Nested
    @DisplayName("Cache invalidation")
    class Invalidation {

        @Test
        @DisplayName("Unregistering drops the cached membership")
        void unregisterDropsMembership() {
            assertEquals(Optional.of(portalA), find(A_EDGE_NEAR_B, PortalColor.BLUE));

            registry.unregister(portalA.id());

            assertTrue(find(A_EDGE_NEAR_B, PortalColor.BLUE).isEmpty(),
                "A cached hit must not outlive the portal it describes");
        }

        @Test
        @DisplayName("Registering a portal is visible to an already-queried position")
        void registerRefreshesMembership() {
            fillInterior(20, 22, PortalColor.BLUE);
            BlockPos pos = new BlockPos(21, 1, 0);
            assertTrue(find(pos, PortalColor.BLUE).isEmpty());

            PortalData portalC = PortalData.create(PortalColor.BLUE, OVERWORLD, pos, 0);
            registry.register(portalC);

            assertEquals(Optional.of(portalC), find(pos, PortalColor.BLUE),
                "A cached negative must not survive a registration");
        }

        @Test
        @DisplayName("Broken portal blocks drop the cached membership")
        void brokenBlocksDropMembership() {
            assertEquals(Optional.of(portalA), find(A_EDGE_NEAR_B, PortalColor.BLUE));

            // Stands in for CustomPortalBlock#onRemove: the blocks between the center and the
            // queried position go away, and the removal invalidates.
            for (int x = 6; x <= 10; x++) {
                for (int y = 0; y <= 2; y++) {
                    world.clear(new BlockPos(x, y, 0));
                }
            }
            registry.invalidateInteriorIndex();

            assertTrue(find(A_EDGE_NEAR_B, PortalColor.BLUE).isEmpty());
            assertEquals(Optional.of(portalA), find(A_CENTER, PortalColor.BLUE),
                "What is left of portal A still resolves");
        }

        @Test
        @DisplayName("Linking needs no invalidation: the cache holds ids, not portal data")
        void linkIsVisibleWithoutInvalidation() {
            assertEquals(Optional.of(portalA), find(A_EDGE_NEAR_B, PortalColor.BLUE));

            assertTrue(registry.link(portalA.id(), portalB.id()));

            PortalData resolved = find(A_EDGE_NEAR_B, PortalColor.BLUE).orElseThrow();
            assertEquals(Optional.of(portalB.id()), resolved.linkedPortalId());
        }
    }

    /** Minimal {@link BlockGetter} over a block map; everything unset is air. */
    private static final class FakeWorld implements BlockGetter {
        private final Map<BlockPos, BlockState> blocks = new HashMap<>();

        void set(BlockPos pos, BlockState state) {
            blocks.put(pos.immutable(), state);
        }

        void clear(BlockPos pos) {
            blocks.remove(pos.immutable());
        }

        @Override
        @Nullable
        public BlockEntity getBlockEntity(BlockPos pos) {
            return null;
        }

        @Override
        public BlockState getBlockState(BlockPos pos) {
            return blocks.getOrDefault(pos, airState);
        }

        @Override
        public FluidState getFluidState(BlockPos pos) {
            return Fluids.EMPTY.defaultFluidState();
        }

        @Override
        public int getHeight() {
            return 384;
        }

        @Override
        public int getMinBuildHeight() {
            return -64;
        }
    }
}
