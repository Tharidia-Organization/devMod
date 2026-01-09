package com.devmod.hologram.client.renderer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.annotation.Nonnull;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Represents a holographic mesh of terrain blocks.
 * Uses greedy meshing to merge adjacent same-color faces into larger quads,
 * significantly reducing the number of draw calls.
 *
 * <p>The mesh is built by:
 * <ol>
 *   <li>Scanning terrain to collect non-air blocks</li>
 *   <li>Finding Y bounds (min/max height of actual blocks)</li>
 *   <li>Running greedy meshing for each face direction</li>
 * </ol>
 */
public class HologramMesh {
    private final Map<BlockPos, BlockState> blocks = new HashMap<>();
    private final int width;
    private final int height;
    private final int depth;
    private final int minY;
    private final List<Quad> quads = new ArrayList<>();

    /**
     * Build a hologram mesh from the specified terrain region.
     *
     * @param level The level to scan
     * @param minX Minimum X coordinate
     * @param maxX Maximum X coordinate
     * @param minZ Minimum Z coordinate
     * @param maxZ Maximum Z coordinate
     */
    public HologramMesh(@Nonnull Level level, int minX, int maxX, int minZ, int maxZ) {
        Objects.requireNonNull(level, "level");

        this.width = maxX - minX + 1;
        this.depth = maxZ - minZ + 1;

        // Find actual Y bounds
        int[] yBounds = findYBounds(level, minX, maxX, minZ, maxZ);
        this.minY = yBounds[0];
        int maxY = yBounds[1];
        this.height = maxY - minY + 1;

        // Scan terrain and build mesh
        scanTerrain(level, minX, maxX, minZ, maxZ, minY, maxY);
        buildMesh();
    }

    /**
     * Find the Y bounds of actual terrain in the region.
     */
    private int[] findYBounds(@Nonnull Level level, int minX, int maxX, int minZ, int maxZ) {
        int foundMinY = level.getMaxBuildHeight();
        int foundMaxY = level.getMinBuildHeight();

        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int y = level.getMaxBuildHeight() - 1; y >= level.getMinBuildHeight(); y--) {
                    BlockState state = level.getBlockState(pos.set(x, y, z));
                    if (!state.isAir()) {
                        foundMinY = Math.min(foundMinY, y);
                        foundMaxY = Math.max(foundMaxY, y);
                        break; // Found top block for this column
                    }
                }
            }
        }

        // If no blocks found, default to Y=64
        if (foundMinY > foundMaxY) {
            foundMinY = 64;
            foundMaxY = 64;
        }

        return new int[]{foundMinY, foundMaxY};
    }

    /**
     * Scan terrain and store non-air blocks with relative coordinates.
     */
    private void scanTerrain(@Nonnull Level level, int minX, int maxX, int minZ, int maxZ, int minY, int maxY) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int y = minY; y <= maxY; y++) {
                    BlockState state = level.getBlockState(pos.set(x, y, z));
                    if (!state.isAir()) {
                        BlockPos relativePos = new BlockPos(x - minX, y - minY, z - minZ);
                        blocks.put(relativePos, state);
                    }
                }
            }
        }
    }

    /**
     * Build the mesh using greedy meshing for each face direction.
     */
    private void buildMesh() {
        for (Direction direction : Direction.values()) {
            greedyMeshDirection(direction);
        }
    }

    /**
     * Run greedy meshing for a single face direction.
     * This merges adjacent same-color faces into larger quads.
     */
    private void greedyMeshDirection(Direction direction) {
        // Determine UV/depth dimensions based on direction
        int uMax, vMax, depthMax;
        switch (direction) {
            case UP, DOWN -> {
                uMax = width;
                vMax = depth;
                depthMax = height;
            }
            case NORTH, SOUTH -> {
                uMax = width;
                vMax = height;
                depthMax = depth;
            }
            default -> { // WEST, EAST
                uMax = depth;
                vMax = height;
                depthMax = width;
            }
        }

        // Track which cells have been merged
        boolean[][] merged = new boolean[uMax][vMax];

        // Process each depth slice
        for (int d = 0; d < depthMax; d++) {
            // Reset merged flags for this slice
            for (int u = 0; u < uMax; u++) {
                for (int v = 0; v < vMax; v++) {
                    merged[u][v] = false;
                }
            }

            // Greedy merge faces in this slice
            for (int u = 0; u < uMax; u++) {
                for (int v = 0; v < vMax; v++) {
                    if (merged[u][v]) continue;

                    // Convert UV to block coordinates
                    int[] coords = getBlockCoords(direction, u, v, d);
                    int x = coords[0], y = coords[1], z = coords[2];

                    // Skip if out of bounds or no block or face not visible
                    if (!inBounds(x, y, z) || !hasBlock(x, y, z) || !shouldRenderFace(x, y, z, direction)) {
                        continue;
                    }

                    // Get block color
                    BlockState state = getBlock(x, y, z);
                    int color = state.getMapColor(null, BlockPos.ZERO).col;

                    // Extend in U direction
                    int uSize = 1;
                    for (int uu = u + 1; uu < uMax; uu++) {
                        int[] testCoords = getBlockCoords(direction, uu, v, d);
                        int tx = testCoords[0], ty = testCoords[1], tz = testCoords[2];

                        if (!inBounds(tx, ty, tz) || merged[uu][v] ||
                            !hasBlock(tx, ty, tz) || !shouldRenderFace(tx, ty, tz, direction)) {
                            break;
                        }

                        BlockState testState = getBlock(tx, ty, tz);
                        if (testState.getMapColor(null, BlockPos.ZERO).col != color) {
                            break;
                        }
                        uSize++;
                    }

                    // Extend in V direction
                    int vSize = 1;
                    boolean canExtendV = true;
                    for (int vv = v + 1; vv < vMax && canExtendV; vv++) {
                        for (int uu = u; uu < u + uSize; uu++) {
                            int[] testCoords = getBlockCoords(direction, uu, vv, d);
                            int tx = testCoords[0], ty = testCoords[1], tz = testCoords[2];

                            if (!inBounds(tx, ty, tz) || merged[uu][vv] ||
                                !hasBlock(tx, ty, tz) || !shouldRenderFace(tx, ty, tz, direction)) {
                                canExtendV = false;
                                break;
                            }

                            BlockState testState = getBlock(tx, ty, tz);
                            if (testState.getMapColor(null, BlockPos.ZERO).col != color) {
                                canExtendV = false;
                                break;
                            }
                        }
                        if (canExtendV) vSize++;
                    }

                    // Mark cells as merged
                    for (int uu = u; uu < u + uSize; uu++) {
                        for (int vv = v; vv < v + vSize; vv++) {
                            merged[uu][vv] = true;
                        }
                    }

                    // Add merged quad
                    float r = ((color >> 16) & 0xFF) / 255f;
                    float g = ((color >> 8) & 0xFF) / 255f;
                    float b = (color & 0xFF) / 255f;
                    addQuad(direction, x, y, z, uSize, vSize, r, g, b);
                }
            }
        }
    }

    /**
     * Convert UV coordinates to block XYZ coordinates.
     */
    private int[] getBlockCoords(Direction direction, int u, int v, int d) {
        return switch (direction) {
            case UP, DOWN -> new int[]{u, d, v};
            case NORTH, SOUTH -> new int[]{u, v, d};
            case WEST, EAST -> new int[]{d, v, u};
        };
    }

    /**
     * Check if coordinates are within bounds.
     */
    private boolean inBounds(int x, int y, int z) {
        return x >= 0 && x < width && y >= 0 && y < height && z >= 0 && z < depth;
    }

    /**
     * Check if a block exists at the given position.
     */
    private boolean hasBlock(int x, int y, int z) {
        return blocks.containsKey(new BlockPos(x, y, z));
    }

    /**
     * Get the block state at the given position.
     */
    private BlockState getBlock(int x, int y, int z) {
        return blocks.get(new BlockPos(x, y, z));
    }

    /**
     * Check if a face should be rendered (not obscured by adjacent block).
     */
    private boolean shouldRenderFace(int x, int y, int z, Direction direction) {
        int nx = x, ny = y, nz = z;
        switch (direction) {
            case UP -> ny++;
            case DOWN -> ny--;
            case NORTH -> nz--;
            case SOUTH -> nz++;
            case WEST -> nx--;
            case EAST -> nx++;
        }

        // Face is visible if neighbor is out of bounds or has no block
        if (!inBounds(nx, ny, nz)) return true;
        return !hasBlock(nx, ny, nz);
    }

    /**
     * Add a quad for the given face.
     */
    private void addQuad(Direction direction, int x, int y, int z, int uSize, int vSize, float r, float g, float b) {
        Quad quad = new Quad(r, g, b);

        switch (direction) {
            case UP -> {
                quad.v1 = new float[]{x, y + 1, z + vSize};
                quad.v2 = new float[]{x + uSize, y + 1, z + vSize};
                quad.v3 = new float[]{x + uSize, y + 1, z};
                quad.v4 = new float[]{x, y + 1, z};
            }
            case DOWN -> {
                quad.v1 = new float[]{x, y, z};
                quad.v2 = new float[]{x + uSize, y, z};
                quad.v3 = new float[]{x + uSize, y, z + vSize};
                quad.v4 = new float[]{x, y, z + vSize};
            }
            case NORTH -> {
                quad.v1 = new float[]{x, y, z};
                quad.v2 = new float[]{x, y + vSize, z};
                quad.v3 = new float[]{x + uSize, y + vSize, z};
                quad.v4 = new float[]{x + uSize, y, z};
            }
            case SOUTH -> {
                quad.v1 = new float[]{x + uSize, y, z + 1};
                quad.v2 = new float[]{x + uSize, y + vSize, z + 1};
                quad.v3 = new float[]{x, y + vSize, z + 1};
                quad.v4 = new float[]{x, y, z + 1};
            }
            case WEST -> {
                quad.v1 = new float[]{x, y, z + uSize};
                quad.v2 = new float[]{x, y + vSize, z + uSize};
                quad.v3 = new float[]{x, y + vSize, z};
                quad.v4 = new float[]{x, y, z};
            }
            case EAST -> {
                quad.v1 = new float[]{x + 1, y, z};
                quad.v2 = new float[]{x + 1, y + vSize, z};
                quad.v3 = new float[]{x + 1, y + vSize, z + uSize};
                quad.v4 = new float[]{x + 1, y, z + uSize};
            }
        }

        quads.add(quad);
    }

    /**
     * Render all quads into the given buffer.
     */
    public void render(@Nonnull BufferBuilder builder, @Nonnull PoseStack poseStack) {
        Matrix4f pose = poseStack.last().pose();
        for (Quad quad : quads) {
            quad.render(builder, pose);
        }
    }

    /**
     * Get the number of quads in this mesh.
     */
    public int getQuadCount() {
        return quads.size();
    }

    /**
     * Check if this mesh has no quads.
     */
    public boolean isEmpty() {
        return quads.isEmpty();
    }

    /**
     * Face directions for mesh building.
     */
    private enum Direction {
        UP, DOWN, NORTH, SOUTH, WEST, EAST
    }

    /**
     * A quad with four vertices and a color.
     */
    private static class Quad {
        float[] v1, v2, v3, v4;
        final float r, g, b;

        Quad(float r, float g, float b) {
            this.r = r;
            this.g = g;
            this.b = b;
        }

        void render(BufferBuilder builder, Matrix4f pose) {
            builder.addVertex(pose, v1[0], v1[1], v1[2]).setColor(r, g, b, 1.0f);
            builder.addVertex(pose, v2[0], v2[1], v2[2]).setColor(r, g, b, 1.0f);
            builder.addVertex(pose, v3[0], v3[1], v3[2]).setColor(r, g, b, 1.0f);
            builder.addVertex(pose, v4[0], v4[1], v4[2]).setColor(r, g, b, 1.0f);
        }
    }
}
