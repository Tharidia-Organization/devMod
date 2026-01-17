package com.devmod.area.builder;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

import javax.annotation.Nonnull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import com.devmod.area.data.AreaDefinition;
import com.devmod.area.data.AreaOptions;
import com.devmod.area.data.AreaShape;
import com.devmod.portal.PortalBlocks;
import com.devmod.portal.PortalColor;
import com.devmod.portal.PortalData;
import com.devmod.portal.PortalRegistry;
import com.devmod.portal.block.CustomPortalBlock;

/**
 * Constructs areas based on AreaDefinition specifications.
 * Supports both custom palette-based building and biome generation.
 * Uses AreaBlockMapGenerator for consistent block placement logic.
 */
public class AreaBuilder {
    private static final Logger LOGGER = LoggerFactory.getLogger(AreaBuilder.class);

    /**
     * Block update flag for area building.
     * Uses UPDATE_CLIENTS (2) to sync to clients without triggering expensive neighbor updates.
     * This provides ~90% performance improvement over UPDATE_ALL for large areas.
     */
    private static final int AREA_BUILD_FLAGS = Block.UPDATE_CLIENTS;

    private final ServerLevel level;
    private final AreaDefinition definition;

    public AreaBuilder(@Nonnull ServerLevel level, @Nonnull AreaDefinition definition) {
        this.level = Objects.requireNonNull(level);
        this.definition = Objects.requireNonNull(definition);
    }

    /**
     * Builds the area synchronously.
     * For large areas, use AreaBuildTask for multi-tick building.
     */
    public void build() {
        LOGGER.info("[Area] Building area '{}' at {}", definition.name(), definition.centerPosition());

        if (definition.isBiomeGeneration()) {
            buildBiomeArea();
        } else {
            buildCustomArea();
        }

        // Spawn portal connection points if enabled
        if (definition.options().spawnPortals()) {
            spawnPortalConnectionPoints();
        }

        LOGGER.info("[Area] Finished building area '{}'", definition.name());
    }

    /**
     * Builds a biome-based area using BiomeAreaGenerator.
     */
    private void buildBiomeArea() {
        Set<BlockPos> floorPositions = AreaShapeGenerator.generateFloor(
            Objects.requireNonNull(definition.shape()),
            Objects.requireNonNull(definition.centerPosition()),
            Objects.requireNonNull(definition.dimensions()),
            definition.customShapeNbt()
        );

        LOGGER.info("[Area] Generating biome terrain for '{}' with {} floor positions",
            definition.name(), floorPositions.size());

        int blocksPlaced = BiomeAreaGenerator.generate(Objects.requireNonNull(level), Objects.requireNonNull(definition), floorPositions);

        LOGGER.info("[Area] Biome generation complete: {} blocks placed", blocksPlaced);
    }

    /**
     * Builds a custom palette-based area using shared block map generator.
     */
    private void buildCustomArea() {
        AreaOptions options = definition.options();

        // Build floor
        if (options.hasFloor()) {
            Map<BlockPos, BlockState> floorBlocks = AreaBlockMapGenerator.generateFloorBlocks(Objects.requireNonNull(definition));
            int count = placeBlocks(floorBlocks);
            LOGGER.debug("[Area] Placed {} floor blocks", count);
        }

        // Build walls
        if (options.hasWalls()) {
            Map<BlockPos, BlockState> wallBlocks = AreaBlockMapGenerator.generateWallBlocks(Objects.requireNonNull(definition));
            int count = placeBlocks(wallBlocks);
            LOGGER.debug("[Area] Placed {} wall blocks", count);
        }

        // Build ceiling
        if (options.hasCeiling()) {
            Map<BlockPos, BlockState> ceilingBlocks = AreaBlockMapGenerator.generateCeilingBlocks(Objects.requireNonNull(definition));
            int count = placeBlocks(ceilingBlocks);
            LOGGER.debug("[Area] Placed {} ceiling blocks", count);
        }
    }

    /**
     * Places blocks from a pre-generated map.
     *
     * @param blocks Map of positions to block states
     * @return Number of blocks placed
     */
    private int placeBlocks(@Nonnull Map<BlockPos, BlockState> blocks) {
        int count = 0;
        for (Map.Entry<BlockPos, BlockState> entry : blocks.entrySet()) {
            BlockState state = entry.getValue();
            if (state != null && !state.isAir()) {
                level.setBlock(Objects.requireNonNull(entry.getKey()), state, AREA_BUILD_FLAGS);
                count++;
            }
        }
        return count;
    }

    /**
     * Clears the area (fills with air).
     */
    public void clear() {
        LOGGER.info("[Area] Clearing area '{}' at {}", definition.name(), definition.centerPosition());

        int minBuildHeight = level.getMinBuildHeight();
        int maxBuildHeight = level.getMaxBuildHeight();

        Set<BlockPos> floorPositions = AreaShapeGenerator.generateFloor(
            Objects.requireNonNull(definition.shape()),
            Objects.requireNonNull(definition.centerPosition()),
            Objects.requireNonNull(definition.dimensions()),
            definition.customShapeNbt()
        );

        // Clear all Y levels within world bounds
        for (BlockPos floorPos : floorPositions) {
            for (int y = 0; y <= definition.dimensions().height(); y++) {
                BlockPos pos = floorPos.above(y);
                // Skip positions outside world bounds
                if (pos.getY() < minBuildHeight || pos.getY() >= maxBuildHeight) {
                    continue;
                }
                if (!level.getBlockState(Objects.requireNonNull(pos)).isAir()) {
                    level.removeBlock(Objects.requireNonNull(pos), false);
                }
            }
        }

        LOGGER.info("[Area] Finished clearing area '{}'", definition.name());
    }

    /**
     * Spawns portal connection points at area boundaries.
     * Creates portals at the cardinal edges (N, S, E, W) and registers them
     * with the PortalRegistry using the area name as a network for cross-linking.
     *
     * <p>Each portal is a simple 1-wide x 2-high portal placed at the midpoint
     * of each cardinal edge, one block inside the wall boundary.
     *
     * <p>MED-04 fix: Portal placement only works correctly for RECTANGULAR shapes.
     * For other shapes (CIRCULAR, HEXAGONAL, OCTAGONAL, CUSTOM_NBT), the calculated
     * positions would be outside the actual floor area, so portal spawning is skipped.
     */
    private void spawnPortalConnectionPoints() {
        // MED-04 fix: Only spawn portals for rectangular shapes
        // Cardinal edge midpoints don't exist for non-rectangular shapes
        AreaShape shape = definition.shape();
        if (shape != AreaShape.RECTANGULAR) {
            LOGGER.info("[Area] Skipping portal spawn for non-rectangular shape '{}' in area '{}'",
                shape.getSerializedName(), definition.name());
            return;
        }

        BlockPos center = definition.centerPosition();
        int minX = AreaShapeGenerator.minOffset(definition.dimensions().width());
        int maxX = AreaShapeGenerator.maxOffset(definition.dimensions().width());
        int minZ = AreaShapeGenerator.minOffset(definition.dimensions().length());
        int maxZ = AreaShapeGenerator.maxOffset(definition.dimensions().length());
        int floorY = definition.dimensions().floorY();

        // Portal positions at cardinal directions (inside the walls)
        BlockPos northPos = new BlockPos(center.getX(), floorY + 1, center.getZ() + minZ + 1);
        BlockPos southPos = new BlockPos(center.getX(), floorY + 1, center.getZ() + maxZ - 1);
        BlockPos eastPos = new BlockPos(center.getX() + maxX - 1, floorY + 1, center.getZ());
        BlockPos westPos = new BlockPos(center.getX() + minX + 1, floorY + 1, center.getZ());

        // Determine portal axis based on direction (portals are perpendicular to facing direction)
        // North/South portals face along Z axis, so they use X axis (player walks through Z)
        // East/West portals face along X axis, so they use Z axis (player walks through X)
        PortalColor color = PortalColor.CYAN; // Use cyan for area portals
        // MED-01 fix: Use area UUID instead of name for network to prevent collisions
        // between areas with the same name
        String networkName = "area_" + definition.id().toString();

        PortalRegistry registry = PortalRegistry.get(Objects.requireNonNull(level));

        // Spawn portals at each cardinal position
        spawnPortalAt(northPos, Direction.Axis.X, color, networkName, registry);
        spawnPortalAt(southPos, Direction.Axis.X, color, networkName, registry);
        spawnPortalAt(eastPos, Direction.Axis.Z, color, networkName, registry);
        spawnPortalAt(westPos, Direction.Axis.Z, color, networkName, registry);

        LOGGER.info("[Area] Spawned 4 portal connection points for area '{}' in network '{}'",
            definition.name(), networkName);
    }

    /**
     * Spawns a single portal (2 blocks high) at the specified position.
     *
     * @param basePos     The bottom position of the portal
     * @param axis        The horizontal axis of the portal
     * @param color       The portal color
     * @param networkName The network name for cross-area linking
     * @param registry    The portal registry
     */
    private void spawnPortalAt(
        @Nonnull BlockPos basePos,
        @Nonnull Direction.Axis axis,
        @Nonnull PortalColor color,
        @Nonnull String networkName,
        @Nonnull PortalRegistry registry
    ) {
        // Check if portal blocks already exist at this position
        BlockState existingBottom = level.getBlockState(Objects.requireNonNull(basePos));
        if (existingBottom.getBlock() instanceof CustomPortalBlock) {
            LOGGER.debug("[Area] Portal already exists at {}, skipping", basePos);
            return;
        }

        // Create the portal block state
        BlockState portalState = Objects.requireNonNull(PortalBlocks.CUSTOM_PORTAL.get().defaultBlockState())
            .setValue(Objects.requireNonNull(CustomPortalBlock.AXIS), axis)
            .setValue(Objects.requireNonNull(CustomPortalBlock.COLOR), color)
            .setValue(Objects.requireNonNull(CustomPortalBlock.LINKED), false);

        // Place 2-high portal
        level.setBlock(Objects.requireNonNull(basePos), Objects.requireNonNull(portalState), Block.UPDATE_ALL);
        BlockPos topPos = basePos.above();
        level.setBlock(Objects.requireNonNull(topPos), Objects.requireNonNull(portalState), Block.UPDATE_ALL);

        // Register the portal (use bottom position as the portal center)
        PortalData portalData = Objects.requireNonNull(PortalData.create(
            color,
            level.dimension().location(),
            basePos,
            0 // No frame block count for auto-generated portals
        ).withNetwork(networkName));

        registry.register(portalData);

        LOGGER.debug("[Area] Spawned portal at {} with network '{}'", basePos, networkName);
    }
}
