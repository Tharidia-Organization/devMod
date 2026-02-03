package com.devmod.nexus.builder;

import java.util.List;
import java.util.Objects;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import com.devmod.nexus.NexusDecorBlocks;
import com.devmod.nexus.data.ZoneSlotPresets;
import com.devmod.runtime.NexusBuildStep;
import com.devmod.runtime.NexusPalette;
/**
 * Builds the minimal foundation structure for the Nexus hub.
 * This replaces the 2705-line NexusHubBuilder with a simple foundation.
 *
 * <p>Foundation consists of:
 * <ul>
 *   <li>Bedrock base layer (Y=0-3) - prevents escape</li>
 *   <li>Floor layer at configured Y - base for all zones</li>
 *   <li>Center spawn platform - decorated spawn area</li>
 *   <li>Radial corridors - paths connecting zones</li>
 *   <li>Border markers - visual world border indicators</li>
 * </ul>
 *
 * <p>Zone content is NOT built here - it's handled by the Area module
 * when areas are linked to zone slots.
 */
@SuppressWarnings("null") // BlockState constants from Blocks.* are never null
public final class NexusFoundationBuilder {
    private static final Logger LOGGER = LoggerFactory.getLogger(NexusFoundationBuilder.class);

    // Block placement flags: NOTIFY_CLIENTS | NO_NEIGHBOR_DROPS | PREVENT_REBUILD_RENDER
    private static final int PLACEMENT_FLAGS = 2 | 16 | 64;

    // Legacy NexusHubCenterBuilder endpoint platform cleanup (old staggered build)
    private static final int LEGACY_ENDPOINT_DISTANCE = 86; // RING3_RADIUS(50) + CORRIDOR_LENGTH(30) + 6
    private static final int LEGACY_ENDPOINT_CLEAR_RADIUS = 6;
    private static final int LEGACY_ENDPOINT_CLEAR_HEIGHT = 8;

    // ========================================================================
    // Block Palette
    // ========================================================================

    private final BlockState bedrock;
    private final BlockState floor;
    private final BlockState floorAccent;
    private final BlockState corridor;
    private final BlockState corridorAccent;
    private final BlockState centerFloor;
    private final BlockState centerAccent;
    private final BlockState centerLight;
    private final BlockState borderMarker;
    private final BlockState borderLight;
    private final BlockState air;

    // ========================================================================
    // Configuration
    // ========================================================================

    private final int hubSize;
    private final int centerSize;
    private final int corridorWidth;
    private final int floorY;
    private final int bedrockLayers;

    /**
     * Create a foundation builder with default settings from ZoneSlotPresets.
     */
    public NexusFoundationBuilder() {
        this(
            ZoneSlotPresets.getHubSize(),
            ZoneSlotPresets.getCenterSize(),
            ZoneSlotPresets.getCorridorWidth(),
            ZoneSlotPresets.getFloorY(),
            4 // bedrock layers
        );
    }

    /**
     * Create a foundation builder with custom settings.
     */
    public NexusFoundationBuilder(int hubSize, int centerSize, int corridorWidth, int floorY, int bedrockLayers) {
        this.hubSize = hubSize;
        this.centerSize = centerSize;
        this.corridorWidth = corridorWidth;
        this.floorY = floorY;
        this.bedrockLayers = bedrockLayers;

        NexusPalette palette = NexusPalette.load();
        // Bedrock remains vanilla for escape prevention
        bedrock = Objects.requireNonNull(Blocks.BEDROCK.defaultBlockState());
        // Custom Nexus blocks for aesthetic futuristic look
        // NEXUS_AZURE_DARK as main floor (user requested)
        floor = resolvePalette(palette.get(NexusPalette.Key.FLOOR),
            Objects.requireNonNull(NexusDecorBlocks.NEXUS_AZURE_DARK.get().defaultBlockState()));
        // Dark panel for grid accent lines
        floorAccent = resolvePalette(palette.get(NexusPalette.Key.FLOOR_ALT),
            Objects.requireNonNull(NexusDecorBlocks.NEXUS_PANEL_DARK.get().defaultBlockState()));
        // Soft steel grid for corridors
        corridor = resolvePalette(palette.get(NexusPalette.Key.GRID),
            Objects.requireNonNull(NexusDecorBlocks.NEXUS_GRID.get().defaultBlockState()));
        // Teal circuit pattern for corridor edges
        corridorAccent = resolvePalette(palette.get(NexusPalette.Key.ACCENT),
            Objects.requireNonNull(NexusDecorBlocks.NEXUS_CIRCUIT.get().defaultBlockState()));
        // Tiled pattern for center platform
        centerFloor = resolvePalette(palette.get(NexusPalette.Key.FLOOR_HUB),
            Objects.requireNonNull(NexusDecorBlocks.NEXUS_TILE.get().defaultBlockState()));
        // Bright cyan for center accents (contrast with dark floor)
        centerAccent = resolvePalette(palette.get(NexusPalette.Key.ACCENT),
            Objects.requireNonNull(NexusDecorBlocks.NEXUS_AZURE.get().defaultBlockState()));
        // Light-emitting panel for illumination
        centerLight = resolvePalette(palette.get(NexusPalette.Key.LIGHT),
            Objects.requireNonNull(NexusDecorBlocks.NEXUS_LIGHT.get().defaultBlockState()));
        // Dark onyx for corner border pillars
        borderMarker = resolvePalette(palette.get(NexusPalette.Key.BORDER),
            Objects.requireNonNull(NexusDecorBlocks.NEXUS_ONYX.get().defaultBlockState()));
        // Cyan light on top of border pillars
        borderLight = resolvePalette(palette.get(NexusPalette.Key.LIGHT_WARM),
            Objects.requireNonNull(NexusDecorBlocks.NEXUS_AZURE_LIGHT.get().defaultBlockState()));
        air = Objects.requireNonNull(Blocks.AIR.defaultBlockState());
    }

    // ========================================================================
    // Main Build Method
    // ========================================================================

    /**
     * Build the complete foundation at the given origin.
     * Origin is typically (0, floorY, 0) for the Nexus dimension.
     *
     * @param level the server level to build in
     * @param origin the center position of the hub
     */
    public void build(@Nonnull ServerLevel level, @Nonnull BlockPos origin) {
        Objects.requireNonNull(level);
        Objects.requireNonNull(origin);

        long startTime = System.currentTimeMillis();
        LOGGER.info("[Nexus] Building foundation at {} (size: {}x{})", origin, hubSize, hubSize);

        // Cleanup legacy endpoint platforms from old staggered hub builds.
        clearLegacyEndpointPlatforms(level, origin);

        // Step 1: Bedrock base
        buildBedrockLayer(level, origin);
        LOGGER.debug("[Nexus] Bedrock layer complete");

        // Step 2: Floor base
        buildFloorLayer(level, origin);
        LOGGER.debug("[Nexus] Floor layer complete");

        // Step 3: Center platform
        buildCenterPlatform(level, origin);
        LOGGER.debug("[Nexus] Center platform complete");

        // Step 4: Corridors
        buildCorridors(level, origin);
        LOGGER.debug("[Nexus] Corridors complete");

        // Step 5: Border markers
        buildBorderMarkers(level, origin);
        LOGGER.debug("[Nexus] Border markers complete");

        long elapsed = System.currentTimeMillis() - startTime;
        LOGGER.info("[Nexus] Foundation complete in {}ms", elapsed);
    }

    // ========================================================================
    // Build Components
    // ========================================================================

    /**
     * Build bedrock layer at Y=0 to Y=bedrockLayers-1.
     * Prevents players from escaping below the hub.
     */
    private void buildBedrockLayer(@Nonnull ServerLevel level, @Nonnull BlockPos origin) {
        int halfSize = hubSize / 2;
        int minX = origin.getX() - halfSize;
        int maxX = origin.getX() + halfSize;
        int minZ = origin.getZ() - halfSize;
        int maxZ = origin.getZ() + halfSize;

        for (int y = 0; y < bedrockLayers; y++) {
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    setBlock(level, x, y, z, bedrock);
                }
            }
        }
    }

    /**
     * Build the base floor layer.
     * This provides a foundation that zones will build on top of.
     */
    private void buildFloorLayer(@Nonnull ServerLevel level, @Nonnull BlockPos origin) {
        int halfSize = hubSize / 2;
        int minX = origin.getX() - halfSize;
        int maxX = origin.getX() + halfSize;
        int minZ = origin.getZ() - halfSize;
        int maxZ = origin.getZ() + halfSize;

        // Build floor with grid pattern
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                // Grid pattern: accent every 16 blocks
                boolean isGridLine = (x % 16 == 0) || (z % 16 == 0);
                setBlock(level, x, floorY, z, isGridLine ? floorAccent : floor);

                // Clear space above floor
                for (int y = floorY + 1; y < floorY + 4; y++) {
                    setBlock(level, x, y, z, air);
                }
            }
        }
    }

    /**
     * Build the decorated center spawn platform.
     */
    private void buildCenterPlatform(@Nonnull ServerLevel level, @Nonnull BlockPos origin) {
        int halfCenter = centerSize / 2;
        int minX = origin.getX() - halfCenter;
        int maxX = origin.getX() + halfCenter;
        int minZ = origin.getZ() - halfCenter;
        int maxZ = origin.getZ() + halfCenter;

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                int dx = Math.abs(x - origin.getX());
                int dz = Math.abs(z - origin.getZ());

                // Circular pattern in center
                double dist = Math.sqrt(dx * dx + dz * dz);

                if (dist < 8) {
                    // Inner circle - light
                    setBlock(level, x, floorY, z, centerLight);
                } else if (dist < 16) {
                    // Ring around inner
                    setBlock(level, x, floorY, z, centerAccent);
                } else if (dist < halfCenter - 4) {
                    // Main center floor
                    setBlock(level, x, floorY, z, centerFloor);
                } else {
                    // Outer edge
                    setBlock(level, x, floorY, z, floorAccent);
                }
            }
        }

        // Build center pillar/beacon
        buildCenterPillar(level, origin);
    }

    /**
     * Build a decorative pillar at the exact center.
     */
    private void buildCenterPillar(@Nonnull ServerLevel level, @Nonnull BlockPos origin) {
        int x = origin.getX();
        int z = origin.getZ();

        // Small raised platform
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                setBlock(level, x + dx, floorY + 1, z + dz, centerAccent);
            }
        }

        // Center beacon
        setBlock(level, x, floorY + 1, z, centerLight);
        setBlock(level, x, floorY + 2, z, centerLight);
    }

    /**
     * Build corridors connecting the center to the edges.
     * 8 corridors: N, NE, E, SE, S, SW, W, NW
     */
    private void buildCorridors(@Nonnull ServerLevel level, @Nonnull BlockPos origin) {
        int halfCenter = centerSize / 2;
        int halfSize = hubSize / 2;
        int halfCorridor = corridorWidth / 2;

        // Cardinal corridors (N, E, S, W)
        buildCardinalCorridor(level, origin, 0, -1, halfCenter, halfSize, halfCorridor); // North
        buildCardinalCorridor(level, origin, 1, 0, halfCenter, halfSize, halfCorridor);  // East
        buildCardinalCorridor(level, origin, 0, 1, halfCenter, halfSize, halfCorridor);  // South
        buildCardinalCorridor(level, origin, -1, 0, halfCenter, halfSize, halfCorridor); // West

        // Diagonal corridors (NE, SE, SW, NW)
        buildDiagonalCorridor(level, origin, 1, -1, halfCenter, halfSize, halfCorridor);  // NE
        buildDiagonalCorridor(level, origin, 1, 1, halfCenter, halfSize, halfCorridor);   // SE
        buildDiagonalCorridor(level, origin, -1, 1, halfCenter, halfSize, halfCorridor);  // SW
        buildDiagonalCorridor(level, origin, -1, -1, halfCenter, halfSize, halfCorridor); // NW
    }

    /**
     * Build a cardinal corridor (along X or Z axis).
     */
    private void buildCardinalCorridor(
            @Nonnull ServerLevel level,
            @Nonnull BlockPos origin,
            int dirX, int dirZ,
            int startDist, int endDist, int halfWidth
    ) {
        int cx = origin.getX();
        int cz = origin.getZ();

        for (int dist = startDist; dist < endDist; dist++) {
            int centerX = cx + dirX * dist;
            int centerZ = cz + dirZ * dist;

            for (int w = -halfWidth; w <= halfWidth; w++) {
                int x = centerX + (dirZ != 0 ? w : 0);
                int z = centerZ + (dirX != 0 ? w : 0);

                // Edge or center pattern
                boolean isEdge = (w == -halfWidth || w == halfWidth);
                setBlock(level, x, floorY, z, isEdge ? corridorAccent : corridor);

                // Lights every 16 blocks
                if (dist % 16 == 0 && w == 0) {
                    setBlock(level, x, floorY, z, centerLight);
                }
            }
        }
    }

    /**
     * Build a diagonal corridor.
     */
    private void buildDiagonalCorridor(
            @Nonnull ServerLevel level,
            @Nonnull BlockPos origin,
            int dirX, int dirZ,
            int startDist, int endDist, int halfWidth
    ) {
        int cx = origin.getX();
        int cz = origin.getZ();

        for (int dist = startDist; dist < endDist; dist++) {
            int centerX = cx + (int)(dirX * dist * 0.7071); // cos(45°)
            int centerZ = cz + (int)(dirZ * dist * 0.7071);

            // Build wider corridor for diagonals
            for (int dx = -halfWidth; dx <= halfWidth; dx++) {
                for (int dz = -halfWidth; dz <= halfWidth; dz++) {
                    int x = centerX + dx;
                    int z = centerZ + dz;

                    // Only place if within corridor bounds
                    double localDist = Math.sqrt(dx * dx + dz * dz);
                    if (localDist <= halfWidth) {
                        boolean isEdge = localDist >= halfWidth - 1;
                        setBlock(level, x, floorY, z, isEdge ? corridorAccent : corridor);
                    }
                }
            }
        }
    }

    /**
     * Build markers at the 4 corners of the hub boundary.
     */
    private void buildBorderMarkers(@Nonnull ServerLevel level, @Nonnull BlockPos origin) {
        int halfSize = hubSize / 2;

        // 4 corner markers
        int[][] corners = {
            {-halfSize, -halfSize},
            {halfSize, -halfSize},
            {halfSize, halfSize},
            {-halfSize, halfSize}
        };

        for (int[] corner : corners) {
            int x = origin.getX() + corner[0];
            int z = origin.getZ() + corner[1];

            // Build pillar
            for (int y = floorY; y < floorY + 8; y++) {
                setBlock(level, x, y, z, borderMarker);
            }
            // Light on top
            setBlock(level, x, floorY + 8, z, borderLight);
        }
    }

    // ========================================================================
    // Utility Methods
    // ========================================================================

    /**
     * Set a block in the level.
     */
    private void setBlock(@Nonnull ServerLevel level, int x, int y, int z, @Nonnull BlockState state) {
        BlockPos pos = new BlockPos(x, y, z);
        // Ensure chunk is loaded before placing block
        if (!level.isLoaded(pos)) {
            level.getChunk(pos); // Force load the chunk
        }
        level.setBlock(pos, state, PLACEMENT_FLAGS);
    }

    private static BlockState resolvePalette(@Nullable BlockState candidate, @Nonnull BlockState fallback) {
        return candidate != null ? candidate : fallback;
    }

    /**
     * Get the configured floor Y level.
     */
    public int getFloorY() {
        return floorY;
    }

    /**
     * Get the configured hub size.
     */
    public int getHubSize() {
        return hubSize;
    }

    /**
     * Get the configured center size.
     */
    public int getCenterSize() {
        return centerSize;
    }

    // ========================================================================
    // Staggered Build Support (Micro-Steps)
    // ========================================================================

    /** Chunk size for micro-step processing (16x16 blocks per step) */
    private static final int CHUNK_SIZE = 16;

    /**
     * Get build steps for staggered (multi-tick) construction.
     * Heavy operations (bedrock, floor) are split into chunk-sized micro-steps
     * to avoid blocking the server thread.
     *
     * @param level the server level
     * @param origin the hub origin position
     * @return list of build steps
     */
    public List<NexusBuildStep> buildSteps(@Nonnull ServerLevel level, @Nonnull BlockPos origin) {
        Objects.requireNonNull(level);
        Objects.requireNonNull(origin);

        List<NexusBuildStep> steps = new java.util.ArrayList<>();

        steps.add(new NexusBuildStep("cleanup", () -> clearLegacyEndpointPlatforms(level, origin)));

        int hubHalf = hubSize / 2;
        int minChunkX = (origin.getX() - hubHalf) >> 4;
        int maxChunkX = (origin.getX() + hubHalf) >> 4;
        int minChunkZ = (origin.getZ() - hubHalf) >> 4;
        int maxChunkZ = (origin.getZ() + hubHalf) >> 4;

        // Phase 1: Bedrock micro-steps (circular area under hub center)
        int bedrockSteps = 0;
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                final int cx = chunkX;
                final int cz = chunkZ;
                steps.add(new NexusBuildStep(
                    "bedrock",
                    () -> buildBedrockChunkSquare(level, origin, cx, cz, hubHalf)
                ));
                bedrockSteps++;
            }
        }
        LOGGER.info("[Nexus] Added {} bedrock steps", bedrockSteps);

        steps.add(new NexusBuildStep("floor", () -> buildFloorLayer(level, origin)));
        steps.add(new NexusBuildStep("center", () -> buildCenterPlatform(level, origin)));
        steps.add(new NexusBuildStep("corridors", () -> buildCorridors(level, origin)));
        steps.add(new NexusBuildStep("borders", () -> buildBorderMarkers(level, origin)));

        LOGGER.info("[Nexus] Created {} total micro-steps for foundation build (bedrock={})",
            steps.size(), bedrockSteps);
        return steps;
    }

    /**
     * Build bedrock for a single chunk column (square area).
     */
    private void buildBedrockChunkSquare(@Nonnull ServerLevel level, @Nonnull BlockPos origin,
                                          int chunkX, int chunkZ, int hubHalf) {
        int startX = chunkX << 4;
        int startZ = chunkZ << 4;
        int endX = startX + CHUNK_SIZE - 1;
        int endZ = startZ + CHUNK_SIZE - 1;

        int minX = origin.getX() - hubHalf;
        int maxX = origin.getX() + hubHalf;
        int minZ = origin.getZ() - hubHalf;
        int maxZ = origin.getZ() + hubHalf;

        for (int y = 0; y < bedrockLayers; y++) {
            for (int x = startX; x <= endX; x++) {
                for (int z = startZ; z <= endZ; z++) {
                    if (x >= minX && x <= maxX && z >= minZ && z <= maxZ) {
                        setBlock(level, x, y, z, bedrock);
                    }
                }
            }
        }
    }

    /**
     * Clear legacy endpoint platforms from old NexusHubCenterBuilder layouts.
     * These were small floating platforms placed beyond the main hub.
     */
    private void clearLegacyEndpointPlatforms(@Nonnull ServerLevel level, @Nonnull BlockPos origin) {
        double[] angles = {0, 45, 90, 135, 180, 225, 270, 315};
        int yMin = floorY;
        int yMax = floorY + LEGACY_ENDPOINT_CLEAR_HEIGHT;
        int radius = LEGACY_ENDPOINT_CLEAR_RADIUS;

        for (double angleDeg : angles) {
            double angleRad = Math.toRadians(angleDeg - 90);
            int cx = origin.getX() + (int) Math.round(Math.cos(angleRad) * LEGACY_ENDPOINT_DISTANCE);
            int cz = origin.getZ() + (int) Math.round(Math.sin(angleRad) * LEGACY_ENDPOINT_DISTANCE);

            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    int ax = cx + x;
                    int az = cz + z;
                    for (int y = yMin; y <= yMax; y++) {
                        setBlock(level, ax, y, az, air);
                    }
                }
            }
        }
    }

    /**
     * Static convenience method for staggered build with default settings.
     *
     * @param level the server level
     * @param origin the hub origin position
     * @return list of build steps
     */
    public static List<NexusBuildStep> createBuildSteps(@Nonnull ServerLevel level, @Nonnull BlockPos origin) {
        return new NexusFoundationBuilder().buildSteps(level, origin);
    }
}
