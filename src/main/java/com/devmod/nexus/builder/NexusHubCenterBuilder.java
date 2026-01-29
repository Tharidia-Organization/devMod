package com.devmod.nexus.builder;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import javax.annotation.Nonnull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.block.state.properties.StairsShape;

import com.devmod.nexus.NexusDecorBlocks;
import com.devmod.runtime.NexusBuildStep;

/**
 * Builds a clean, fluid sci-fi Nexus hub.
 *
 * Design principles:
 * - FLUID: No random holes, continuous solid surfaces
 * - CLEAR: Well-defined corridors with obvious direction
 * - REFERENCE POINTS: Each destination has distinct visual marker (unique colors)
 * - TECH AESTHETIC: Clean patterns, smooth transitions using slab→stair→block
 */
public final class NexusHubCenterBuilder {
    private static final Logger LOGGER = LoggerFactory.getLogger(NexusHubCenterBuilder.class);

    private static final int FLAGS = 2 | 16 | 64;

    // ========================================================================
    // Geometry
    // ========================================================================

    private static final int HUB_RADIUS = 80;
    private static final int CENTER_RADIUS = 10;
    private static final int RING1_RADIUS = 22;
    private static final int RING2_RADIUS = 36;
    private static final int RING3_RADIUS = 50;

    private static final int CORRIDOR_WIDTH = 5;  // Must be odd for center line
    private static final int CORRIDOR_LENGTH = 30;

    // ========================================================================
    // Color Scheme per Direction (8 corridors = 8 distinct colors)
    // ========================================================================

    /**
     * Complete color scheme for smooth transitions: block + slab + stair.
     */
    private record CorridorColors(
        @Nonnull String name,
        @Nonnull BlockState block,     // Full block
        @Nonnull BlockState slab,      // Half slab (for smooth start)
        @Nonnull BlockState stair      // Stair (for smooth transition)
    ) {}

    private final CorridorColors[] corridorSchemes;

    // ========================================================================
    // Block Palette (shared)
    // ========================================================================

    @Nonnull private final BlockState baseFloor;      // Dark base everywhere
    @Nonnull private final BlockState ringFloor;      // White ring surfaces
    @Nonnull private final BlockState corridorFloor;  // Light gray corridor
    @Nonnull private final BlockState corridorSlab;   // Light gray slab
    @Nonnull private final BlockState corridorStair;  // Light gray stair
    @Nonnull private final BlockState edgeTrim;       // Steel edge trim
    @Nonnull private final BlockState spawnMarker;    // Center spawn glow
    @Nonnull private final BlockState telepadCore;    // Telepad block
    @Nonnull private final BlockState air;

    private final int floorY;

    public NexusHubCenterBuilder(int floorY) {
        this.floorY = floorY;

        // Initialize shared palette
        baseFloor = Objects.requireNonNull(NexusDecorBlocks.NEXUS_DARK_SEAMLESS.get().defaultBlockState());
        ringFloor = Objects.requireNonNull(NexusDecorBlocks.NEXUS_WHITE_PURE.get().defaultBlockState());
        corridorFloor = Objects.requireNonNull(NexusDecorBlocks.NEXUS_LIGHT_SEAMLESS.get().defaultBlockState());
        corridorSlab = Objects.requireNonNull(NexusDecorBlocks.NEXUS_LIGHT_SEAMLESS_SLAB.get().defaultBlockState());
        corridorStair = Objects.requireNonNull(NexusDecorBlocks.NEXUS_LIGHT_SEAMLESS_STAIRS.get().defaultBlockState());
        edgeTrim = Objects.requireNonNull(NexusDecorBlocks.NEXUS_STEEL_SEAMLESS.get().defaultBlockState());
        spawnMarker = Objects.requireNonNull(NexusDecorBlocks.NEXUS_FLOOR_LIGHT.get().defaultBlockState());
        telepadCore = Objects.requireNonNull(NexusDecorBlocks.NEXUS_TELEPAD_CORE.get().defaultBlockState());
        air = Objects.requireNonNull(Blocks.AIR.defaultBlockState());

        // Initialize 8 distinct color schemes (one per direction)
        // Each has block + slab + stair for smooth transitions
        corridorSchemes = new CorridorColors[] {
            // N (0°) - Blue neon
            new CorridorColors("N",
                Objects.requireNonNull(NexusDecorBlocks.NEXUS_NEON_BLUE.get().defaultBlockState()),
                Objects.requireNonNull(NexusDecorBlocks.NEXUS_NEON_BLUE_SLAB.get().defaultBlockState()),
                Objects.requireNonNull(NexusDecorBlocks.NEXUS_NEON_BLUE_STAIRS.get().defaultBlockState())),

            // NE (45°) - Gold circuit
            new CorridorColors("NE",
                Objects.requireNonNull(NexusDecorBlocks.NEXUS_CIRCUIT_GOLD.get().defaultBlockState()),
                Objects.requireNonNull(NexusDecorBlocks.NEXUS_CIRCUIT_GOLD_SLAB.get().defaultBlockState()),
                Objects.requireNonNull(NexusDecorBlocks.NEXUS_CIRCUIT_GOLD_STAIRS.get().defaultBlockState())),

            // E (90°) - Teal circuit
            new CorridorColors("E",
                Objects.requireNonNull(NexusDecorBlocks.NEXUS_CIRCUIT.get().defaultBlockState()),
                Objects.requireNonNull(NexusDecorBlocks.NEXUS_CIRCUIT_SLAB.get().defaultBlockState()),
                Objects.requireNonNull(NexusDecorBlocks.NEXUS_CIRCUIT_STAIRS.get().defaultBlockState())),

            // SE (135°) - Warm ember
            new CorridorColors("SE",
                Objects.requireNonNull(NexusDecorBlocks.NEXUS_EMBER.get().defaultBlockState()),
                Objects.requireNonNull(NexusDecorBlocks.NEXUS_EMBER_SLAB.get().defaultBlockState()),
                Objects.requireNonNull(NexusDecorBlocks.NEXUS_EMBER_STAIRS.get().defaultBlockState())),

            // S (180°) - Purple tile
            new CorridorColors("S",
                Objects.requireNonNull(NexusDecorBlocks.NEXUS_TILE_PURPLE.get().defaultBlockState()),
                Objects.requireNonNull(NexusDecorBlocks.NEXUS_TILE_PURPLE_SLAB.get().defaultBlockState()),
                Objects.requireNonNull(NexusDecorBlocks.NEXUS_TILE_PURPLE_STAIRS.get().defaultBlockState())),

            // SW (225°) - Yellow conduit
            new CorridorColors("SW",
                Objects.requireNonNull(NexusDecorBlocks.NEXUS_CONDUIT_YELLOW.get().defaultBlockState()),
                Objects.requireNonNull(NexusDecorBlocks.NEXUS_CONDUIT_YELLOW_SLAB.get().defaultBlockState()),
                Objects.requireNonNull(NexusDecorBlocks.NEXUS_CONDUIT_YELLOW_STAIRS.get().defaultBlockState())),

            // W (270°) - Blue conduit
            new CorridorColors("W",
                Objects.requireNonNull(NexusDecorBlocks.NEXUS_CONDUIT.get().defaultBlockState()),
                Objects.requireNonNull(NexusDecorBlocks.NEXUS_CONDUIT_SLAB.get().defaultBlockState()),
                Objects.requireNonNull(NexusDecorBlocks.NEXUS_CONDUIT_STAIRS.get().defaultBlockState())),

            // NW (315°) - White grid
            new CorridorColors("NW",
                Objects.requireNonNull(NexusDecorBlocks.NEXUS_WHITE_GRID.get().defaultBlockState()),
                Objects.requireNonNull(NexusDecorBlocks.NEXUS_WHITE_GRID_SLAB.get().defaultBlockState()),
                Objects.requireNonNull(NexusDecorBlocks.NEXUS_WHITE_GRID_STAIRS.get().defaultBlockState()))
        };
    }

    // ========================================================================
    // Build Methods
    // ========================================================================

    public void build(@Nonnull ServerLevel level, @Nonnull BlockPos origin) {
        Objects.requireNonNull(level);
        Objects.requireNonNull(origin);
        LOGGER.info("[NexusHub] Building fluid hub at {} (floorY={})", origin, floorY);

        // Step 1: Solid base floor (no holes)
        buildSolidBase(level, origin);

        // Step 2: Concentric rings stepping up
        buildRings(level, origin);

        // Step 3: Clear corridors with visual lanes
        buildCorridors(level, origin);

        // Step 4: Center platform with spawn marker
        buildCenterPlatform(level, origin);

        // Step 5: Endpoint platforms with distinct markers
        buildEndpointPlatforms(level, origin);

        LOGGER.info("[NexusHub] Hub complete!");
    }

    public List<NexusBuildStep> buildSteps(@Nonnull ServerLevel level, @Nonnull BlockPos origin) {
        List<NexusBuildStep> steps = new ArrayList<>();
        steps.add(new NexusBuildStep("base", () -> buildSolidBase(level, origin)));
        steps.add(new NexusBuildStep("rings", () -> buildRings(level, origin)));
        steps.add(new NexusBuildStep("corridors", () -> buildCorridors(level, origin)));
        steps.add(new NexusBuildStep("center", () -> buildCenterPlatform(level, origin)));
        steps.add(new NexusBuildStep("endpoints", () -> buildEndpointPlatforms(level, origin)));
        return steps;
    }

    // ========================================================================
    // Step 1: Solid Base Floor
    // ========================================================================

    private void buildSolidBase(@Nonnull ServerLevel level, @Nonnull BlockPos origin) {
        int radius = HUB_RADIUS;
        int y = floorY;

        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                double dist = Math.sqrt(x * x + z * z);
                if (dist <= radius) {
                    // Solid dark floor everywhere
                    setBlock(level, origin.getX() + x, y, origin.getZ() + z, baseFloor);

                    // Clear space above (generous height)
                    for (int dy = 1; dy <= 12; dy++) {
                        setBlock(level, origin.getX() + x, y + dy, origin.getZ() + z, air);
                    }
                }
            }
        }
    }

    // ========================================================================
    // Step 2: Concentric Rings
    // ========================================================================

    private void buildRings(@Nonnull ServerLevel level, @Nonnull BlockPos origin) {
        // Ring 1: Y+1 (from center to ring1)
        buildRingLayer(level, origin, CENTER_RADIUS, RING1_RADIUS, 1);

        // Ring 2: Y+2 (from ring1 to ring2)
        buildRingLayer(level, origin, RING1_RADIUS, RING2_RADIUS, 2);

        // Ring 3: Y+3 (from ring2 to ring3)
        buildRingLayer(level, origin, RING2_RADIUS, RING3_RADIUS, 3);
    }

    private void buildRingLayer(@Nonnull ServerLevel level, @Nonnull BlockPos origin,
                                int innerR, int outerR, int ringLevel) {
        int topY = floorY + ringLevel;

        for (int x = -outerR - 1; x <= outerR + 1; x++) {
            for (int z = -outerR - 1; z <= outerR + 1; z++) {
                double dist = Math.sqrt(x * x + z * z);

                // Skip corridor zones (will be filled separately)
                if (isInCorridorPath(x, z)) continue;

                // Main ring surface
                if (dist > innerR && dist <= outerR) {
                    // Fill below for solid 3D effect
                    for (int fillY = floorY + 1; fillY < topY; fillY++) {
                        setBlock(level, origin.getX() + x, fillY, origin.getZ() + z, edgeTrim);
                    }

                    // Subtle trim at inner/outer edges, white in between
                    if (dist < innerR + 1.2 || (ringLevel == 3 && dist > outerR - 1.2)) {
                        setBlock(level, origin.getX() + x, topY, origin.getZ() + z, edgeTrim);
                    } else {
                        setBlock(level, origin.getX() + x, topY, origin.getZ() + z, ringFloor);
                    }
                }

                // Smooth transition stairs at inner edge
                if (dist > innerR - 1 && dist <= innerR) {
                    Direction facing = getRadialDirection(x, z);
                    BlockState ringStair = Objects.requireNonNull(
                        NexusDecorBlocks.NEXUS_WHITE_PURE_STAIRS.get().defaultBlockState()
                            .setValue(Objects.requireNonNull(StairBlock.FACING), Objects.requireNonNull(facing))
                            .setValue(Objects.requireNonNull(StairBlock.HALF), Half.BOTTOM)
                            .setValue(Objects.requireNonNull(StairBlock.SHAPE), StairsShape.STRAIGHT));
                    setBlock(level, origin.getX() + x, topY, origin.getZ() + z, ringStair);

                    // Fill below stairs
                    for (int fillY = floorY + 1; fillY < topY; fillY++) {
                        setBlock(level, origin.getX() + x, fillY, origin.getZ() + z, edgeTrim);
                    }
                }
            }
        }
    }

    // ========================================================================
    // Step 3: Clear Corridors
    // ========================================================================

    private void buildCorridors(@Nonnull ServerLevel level, @Nonnull BlockPos origin) {
        // 8 corridors: N, NE, E, SE, S, SW, W, NW - each with unique color
        double[] angles = {0, 45, 90, 135, 180, 225, 270, 315};
        for (int i = 0; i < angles.length; i++) {
            buildSingleCorridor(level, origin, angles[i], corridorSchemes[i]);
        }
    }

    private void buildSingleCorridor(@Nonnull ServerLevel level, @Nonnull BlockPos origin,
                                     double angleDeg, CorridorColors colors) {
        double angleRad = Math.toRadians(angleDeg - 90);
        double dirX = Math.cos(angleRad);
        double dirZ = Math.sin(angleRad);

        int halfWidth = CORRIDOR_WIDTH / 2;
        int startDist = CENTER_RADIUS - 2;
        int endDist = RING3_RADIUS + CORRIDOR_LENGTH;

        Direction stairFacing = getCorridorStairFacing(angleDeg);

        // Precompute stair states for this corridor
        BlockState coloredStair = Objects.requireNonNull(colors.stair()
            .setValue(Objects.requireNonNull(StairBlock.FACING), Objects.requireNonNull(stairFacing))
            .setValue(Objects.requireNonNull(StairBlock.HALF), Half.BOTTOM)
            .setValue(Objects.requireNonNull(StairBlock.SHAPE), StairsShape.STRAIGHT));
        BlockState grayStair = Objects.requireNonNull(corridorStair
            .setValue(Objects.requireNonNull(StairBlock.FACING), Objects.requireNonNull(stairFacing))
            .setValue(Objects.requireNonNull(StairBlock.HALF), Half.BOTTOM)
            .setValue(Objects.requireNonNull(StairBlock.SHAPE), StairsShape.STRAIGHT));

        // Iterate over ALL positions in the bounding area and check if they're in the corridor
        // This ensures no gaps even for diagonal corridors
        int maxRange = endDist + halfWidth + 2;

        for (int rx = -maxRange; rx <= maxRange; rx++) {
            for (int rz = -maxRange; rz <= maxRange; rz++) {
                int bx = origin.getX() + rx;
                int bz = origin.getZ() + rz;

                // Check if this position is within the corridor bounds
                // Project position onto corridor axis and perpendicular
                double alongCorridor = rx * dirX + rz * dirZ;  // Distance along corridor direction
                double perpDistance = Math.abs(rx * -dirZ + rz * dirX);  // Perpendicular distance

                // Skip if outside corridor bounds
                if (alongCorridor < startDist || alongCorridor > endDist) continue;
                if (perpDistance > halfWidth + 0.5) continue;

                // Calculate distance from center for height
                double distFromCenter = Math.sqrt(rx * rx + rz * rz);

                int targetY = getHeightForDistance(distFromCenter);
                int prevY = getHeightForDistance(distFromCenter - 1.5);
                int nextY = getHeightForDistance(distFromCenter + 1.5);

                // Determine position within corridor width
                int widthPos = (int) Math.round(rx * -dirZ + rz * dirX);
                boolean isCenter = (Math.abs(widthPos) == 0);
                boolean isEdge = (Math.abs(widthPos) >= halfWidth);
                boolean isRising = (targetY > prevY) && distFromCenter > CENTER_RADIUS;
                boolean willRise = (nextY > targetY) && distFromCenter > CENTER_RADIUS - 2;

                // Determine what block to place based on position and transition state
                // Smooth transition: slab (half step before) → stair (at transition) → block (after)
                if (isRising) {
                    // At transition point: use stair
                    if (isCenter || isEdge) {
                        setBlock(level, bx, targetY, bz, coloredStair);
                    } else {
                        setBlock(level, bx, targetY, bz, grayStair);
                    }
                } else if (willRise) {
                    // Before transition: use slab for smooth approach
                    BlockState slabState;
                    if (isCenter || isEdge) {
                        slabState = Objects.requireNonNull(colors.slab().setValue(Objects.requireNonNull(SlabBlock.TYPE), SlabType.TOP));
                    } else {
                        slabState = Objects.requireNonNull(corridorSlab.setValue(Objects.requireNonNull(SlabBlock.TYPE), SlabType.TOP));
                    }
                    setBlock(level, bx, targetY, bz, slabState);
                } else {
                    // Normal section: use full blocks
                    BlockState block;
                    if (isCenter) {
                        block = Objects.requireNonNull(colors.block());    // Corridor-specific center line
                    } else if (isEdge) {
                        block = Objects.requireNonNull(colors.block());    // Corridor-specific edge
                    } else {
                        block = corridorFloor;     // Light gray main floor (shared)
                    }
                    setBlock(level, bx, targetY, bz, block);
                }

                // Fill below for solid appearance
                for (int fillY = floorY + 1; fillY < targetY; fillY++) {
                    setBlock(level, bx, fillY, bz, edgeTrim);
                }

                // Clear above
                for (int dy = 1; dy <= 6; dy++) {
                    setBlock(level, bx, targetY + dy, bz, air);
                }
            }
        }
    }

    // ========================================================================
    // Step 4: Center Platform
    // ========================================================================

    private void buildCenterPlatform(@Nonnull ServerLevel level, @Nonnull BlockPos origin) {
        int y = floorY;
        int radius = CENTER_RADIUS;

        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                double dist = Math.sqrt(x * x + z * z);

                if (dist <= radius) {
                    BlockState block;

                    // Concentric pattern for tech look (subtle, no bright glows)
                    if (dist <= 2) {
                        block = spawnMarker;  // Center marker (subtle glow)
                    } else if (dist <= 4) {
                        block = baseFloor;    // Dark inner
                    } else if (dist <= 6) {
                        block = ringFloor;    // White ring
                    } else if (dist <= 8) {
                        block = baseFloor;    // Dark middle
                    } else {
                        block = edgeTrim;     // Steel outer edge
                    }

                    setBlock(level, origin.getX() + x, y, origin.getZ() + z, block);
                }
            }
        }
    }

    // ========================================================================
    // Step 5: Endpoint Platforms (with distinct markers)
    // ========================================================================

    private void buildEndpointPlatforms(@Nonnull ServerLevel level, @Nonnull BlockPos origin) {
        double[] angles = {0, 45, 90, 135, 180, 225, 270, 315};

        for (int i = 0; i < angles.length; i++) {
            buildEndpointPlatform(level, origin, angles[i], corridorSchemes[i]);
        }
    }

    private void buildEndpointPlatform(@Nonnull ServerLevel level, @Nonnull BlockPos origin,
                                        double angleDeg, CorridorColors colors) {
        double angleRad = Math.toRadians(angleDeg - 90);
        int distance = RING3_RADIUS + CORRIDOR_LENGTH + 6;
        LOGGER.debug("[NexusHub] Building endpoint platform: {} at {}°", colors.name(), angleDeg);

        int cx = origin.getX() + (int) Math.round(Math.cos(angleRad) * distance);
        int cz = origin.getZ() + (int) Math.round(Math.sin(angleRad) * distance);
        int y = floorY + 3;

        int platformSize = 4;

        // Build platform with corridor-specific colors
        for (int x = -platformSize; x <= platformSize; x++) {
            for (int z = -platformSize; z <= platformSize; z++) {
                double dist = Math.sqrt(x * x + z * z);

                if (dist <= platformSize) {
                    BlockState block;

                    // Distinct pattern: outer ring (corridor color), inner platform, center telepad
                    if (dist > platformSize - 1) {
                        block = Objects.requireNonNull(colors.block());    // Corridor-colored edge
                    } else if (dist <= 1.5) {
                        block = telepadCore;       // Telepad center
                    } else {
                        block = Objects.requireNonNull(colors.block());    // Corridor-colored platform
                    }

                    setBlock(level, cx + x, y, cz + z, block);

                    // Solid fill below
                    for (int fillY = floorY; fillY < y; fillY++) {
                        setBlock(level, cx + x, fillY, cz + z, edgeTrim);
                    }

                    // Clear above
                    for (int dy = 1; dy <= 6; dy++) {
                        setBlock(level, cx + x, y + dy, cz + z, air);
                    }
                }
            }
        }

        // Add directional indicator (arrow pointing back to center)
        addDirectionalMarker(level, cx, cz, y, angleDeg, colors);
    }

    /**
     * Add a simple arrow/indicator pointing back to hub center.
     */
    private void addDirectionalMarker(@Nonnull ServerLevel level, int cx, int cz, int y,
                                      double angleDeg, CorridorColors colors) {
        // Simple 3-block arrow pointing toward center
        double angleRad = Math.toRadians(angleDeg - 90 + 180); // Reverse direction
        double dx = Math.cos(angleRad);
        double dz = Math.sin(angleRad);

        // Arrow tip (3 blocks) - use corridor block color
        for (int i = 2; i <= 4; i++) {
            int ax = cx + (int) Math.round(dx * i);
            int az = cz + (int) Math.round(dz * i);
            setBlock(level, ax, y, az, Objects.requireNonNull(colors.block()));
        }
    }

    // ========================================================================
    // Helper Methods
    // ========================================================================

    private boolean isInCorridorPath(int x, int z) {
        int halfWidth = CORRIDOR_WIDTH / 2 + 1;
        double dist = Math.sqrt(x * x + z * z);

        if (dist < CENTER_RADIUS - 2) return false;

        // Cardinal corridors (N, S, E, W)
        if (Math.abs(x) <= halfWidth && Math.abs(z) > CENTER_RADIUS - 3) return true;
        if (Math.abs(z) <= halfWidth && Math.abs(x) > CENTER_RADIUS - 3) return true;

        // Diagonal corridors (NE, SE, SW, NW)
        double diagWidth = halfWidth * 1.5;
        if (Math.abs(x - z) <= diagWidth && x > 0 && z > 0 && dist > CENTER_RADIUS - 2) return true;
        if (Math.abs(x + z) <= diagWidth && x > 0 && z < 0 && dist > CENTER_RADIUS - 2) return true;
        if (Math.abs(x - z) <= diagWidth && x < 0 && z < 0 && dist > CENTER_RADIUS - 2) return true;
        if (Math.abs(x + z) <= diagWidth && x < 0 && z > 0 && dist > CENTER_RADIUS - 2) return true;

        return false;
    }

    private int getHeightForDistance(double dist) {
        if (dist <= CENTER_RADIUS) return floorY;
        if (dist <= RING1_RADIUS) return floorY + 1;
        if (dist <= RING2_RADIUS) return floorY + 2;
        return floorY + 3;
    }

    private Direction getRadialDirection(int x, int z) {
        if (Math.abs(x) > Math.abs(z)) {
            return x > 0 ? Direction.EAST : Direction.WEST;
        } else {
            return z > 0 ? Direction.SOUTH : Direction.NORTH;
        }
    }

    private Direction getCorridorStairFacing(double angleDeg) {
        int angle = ((int) angleDeg) % 360;
        return switch (angle) {
            case 0 -> Direction.SOUTH;
            case 45 -> Direction.SOUTH;
            case 90 -> Direction.WEST;
            case 135 -> Direction.WEST;
            case 180 -> Direction.NORTH;
            case 225 -> Direction.NORTH;
            case 270 -> Direction.EAST;
            case 315 -> Direction.EAST;
            default -> Direction.NORTH;
        };
    }

    private void setBlock(@Nonnull ServerLevel level, int x, int y, int z, @Nonnull BlockState state) {
        BlockPos pos = new BlockPos(x, y, z);
        if (!level.isLoaded(pos)) {
            level.getChunk(pos);
        }
        level.setBlock(pos, state, FLAGS);
    }

    public static int getTotalRadius() {
        return HUB_RADIUS + CORRIDOR_LENGTH + 15;
    }
}
