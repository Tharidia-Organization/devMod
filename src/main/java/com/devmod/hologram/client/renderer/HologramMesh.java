package com.devmod.hologram.client.renderer;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import com.devmod.hologram.data.HologramFilter;

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
public final class HologramMesh {
    private final Level level;
    private final Map<BlockPos, BlockState> blocks = new HashMap<>();
    private final int width;
    private final int height;
    private final int depth;
    private final int originX;
    private final int originY;
    private final int originZ;
    private final List<Quad> quads = new ArrayList<>();

    // Filter settings
    @Nullable
    private EnumSet<HologramFilter> activeFilters;
    private boolean filterHighlightOnly;

    // Texture mode
    private boolean texturedMode;

    // Cache for block sprites (only used in textured mode)
    @Nullable
    private Map<BlockState, TextureAtlasSprite> spriteCache;

    /*
     * Build a hologram mesh from the specified terrain region.
     *
     * @param level The level to scan
     * @param minX Minimum X coordinate
     * @param maxX Maximum X coordinate
     * @param minZ Minimum Z coordinate
     * @param maxZ Maximum Z coordinate
     */
    @Nonnull
    public static HologramMesh build(@Nonnull Level level, int minX, int maxX, int minZ, int maxZ) {
        return build(level, minX, maxX, minZ, maxZ, null, false, false);
    }

    /*
     * Build a hologram mesh with filter support.
     *
     * @param level The level to scan
     * @param minX Minimum X coordinate
     * @param maxX Maximum X coordinate
     * @param minZ Minimum Z coordinate
     * @param maxZ Maximum Z coordinate
     * @param filters Active filters for highlighting (null = no filtering)
     * @param highlightOnly If true, only show blocks matching filters
     */
    @Nonnull
    public static HologramMesh build(@Nonnull Level level, int minX, int maxX, int minZ, int maxZ,
                                      @Nullable EnumSet<HologramFilter> filters, boolean highlightOnly) {
        return build(level, minX, maxX, minZ, maxZ, filters, highlightOnly, false);
    }

    /*
     * Build a hologram mesh with filter and texture support.
     *
     * @param level The level to scan
     * @param minX Minimum X coordinate
     * @param maxX Maximum X coordinate
     * @param minZ Minimum Z coordinate
     * @param maxZ Maximum Z coordinate
     * @param filters Active filters for highlighting (null = no filtering)
     * @param highlightOnly If true, only show blocks matching filters
     * @param texturedMode If true, calculate UV coordinates for block textures
     */
    @Nonnull
    public static HologramMesh build(@Nonnull Level level, int minX, int maxX, int minZ, int maxZ,
                                      @Nullable EnumSet<HologramFilter> filters, boolean highlightOnly,
                                      boolean texturedMode) {
        return build(level, minX, maxX, minZ, maxZ, filters, highlightOnly, texturedMode, false, 0, 1);
    }

    /*
     * Build a hologram mesh with filter, texture, and Y-slice support.
     *
     * @param level The level to scan
     * @param minX Minimum X coordinate
     * @param maxX Maximum X coordinate
     * @param minZ Minimum Z coordinate
     * @param maxZ Maximum Z coordinate
     * @param filters Active filters for highlighting (null = no filtering)
     * @param highlightOnly If true, only show blocks matching filters
     * @param texturedMode If true, calculate UV coordinates for block textures
     * @param ySliceEnabled If true, only show blocks within the Y-slice range
     * @param ySliceLevel The center Y level of the slice
     * @param ySliceThickness The thickness of the slice (number of layers)
     */
    @Nonnull
    public static HologramMesh build(@Nonnull Level level, int minX, int maxX, int minZ, int maxZ,
                                      @Nullable EnumSet<HologramFilter> filters, boolean highlightOnly,
                                      boolean texturedMode, boolean ySliceEnabled, int ySliceLevel,
                                      int ySliceThickness) {
        int width = maxX - minX + 1;
        int depth = maxZ - minZ + 1;

        // Find actual Y bounds (or use Y-slice bounds if enabled)
        int minY, maxY;
        if (ySliceEnabled) {
            minY = ySliceLevel - ySliceThickness / 2;
            maxY = ySliceLevel + ySliceThickness / 2;
        } else {
            int[] yBounds = findYBounds(level, minX, maxX, minZ, maxZ);
            minY = yBounds[0];
            maxY = yBounds[1];
        }
        int height = maxY - minY + 1;

        HologramMesh mesh = new HologramMesh(level, minX, minZ, width, depth, minY, height);
        mesh.activeFilters = filters;
        mesh.filterHighlightOnly = highlightOnly;
        mesh.texturedMode = texturedMode;
        if (texturedMode) {
            mesh.spriteCache = new HashMap<>();
        }
        mesh.scanTerrain(level, minX, maxX, minZ, maxZ, minY, maxY);
        mesh.buildMesh();
        return mesh;
    }

    /**
     * Check if this mesh was built in textured mode.
     */
    public boolean isTexturedMode() {
        return texturedMode;
    }

    /**
     * Get the Y origin of this mesh (minimum Y of actual terrain).
     * Used to align entity coordinates with mesh coordinates.
     */
    public int getOriginY() {
        return originY;
    }

    private HologramMesh(@Nonnull Level level, int minX, int minZ, int width, int depth, int minY, int height) {
        this.level = level;
        this.width = width;
        this.depth = depth;
        this.height = height;
        this.originX = minX;
        this.originY = minY;
        this.originZ = minZ;
    }

    /*
     * Find the Y bounds of actual terrain in the region.
     */
    private static int[] findYBounds(@Nonnull Level level, int minX, int maxX, int minZ, int maxZ) {
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

    /*
     * Scan terrain and store non-air blocks with relative coordinates.
     * If filterHighlightOnly is true, only stores blocks matching active filters.
     */
    private void scanTerrain(@Nonnull Level level, int minX, int maxX, int minZ, int maxZ, int minY, int maxY) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int y = minY; y <= maxY; y++) {
                    BlockState state = level.getBlockState(pos.set(x, y, z));
                    if (!state.isAir()) {
                        // If highlight-only mode, skip blocks that don't match any filter
                        if (filterHighlightOnly && activeFilters != null && !activeFilters.isEmpty()) {
                            HologramFilter match = HologramFilter.getMatchingFilter(state, activeFilters);
                            if (match == null) {
                                continue; // Skip non-matching blocks
                            }
                        }
                        BlockPos relativePos = new BlockPos(x - minX, y - minY, z - minZ);
                        blocks.put(relativePos, state);
                    }
                }
            }
        }
    }

    /*
     * Build the mesh using greedy meshing for each face direction.
     */
    private void buildMesh() {
        for (Direction direction : Direction.values()) {
            greedyMeshDirection(direction);
        }
    }

    /*
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
                    int color = getBlockColor(state, x, y, z);

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
                        if (!canMergeBlocks(state, testState, x, y, z, tx, ty, tz)) {
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
                            if (!canMergeBlocks(state, testState, x, y, z, tx, ty, tz)) {
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

                    // Add merged quad with color and UV data
                    float r = ((color >> 16) & 0xFF) / 255f;
                    float g = ((color >> 8) & 0xFF) / 255f;
                    float b = (color & 0xFF) / 255f;
                    boolean isFiltered = isFilteredBlock(state);
                    addQuad(direction, x, y, z, uSize, vSize, r, g, b, state, isFiltered);
                }
            }
        }
    }

    /*
     * Convert UV coordinates to block XYZ coordinates.
     */
    private int[] getBlockCoords(Direction direction, int u, int v, int d) {
        return switch (direction) {
            case UP, DOWN -> new int[]{u, d, v};
            case NORTH, SOUTH -> new int[]{u, v, d};
            case WEST, EAST -> new int[]{d, v, u};
        };
    }

    /*
     * Check if coordinates are within bounds.
     */
    private boolean inBounds(int x, int y, int z) {
        return x >= 0 && x < width && y >= 0 && y < height && z >= 0 && z < depth;
    }

    /*
     * Check if a block exists at the given position.
     */
    private boolean hasBlock(int x, int y, int z) {
        return blocks.containsKey(new BlockPos(x, y, z));
    }

    /*
     * Get the block state at the given position.
     */
    private BlockState getBlock(int x, int y, int z) {
        return Objects.requireNonNull(blocks.get(new BlockPos(x, y, z)), "Missing block in mesh");
    }

    private int getBlockColor(BlockState state, int x, int y, int z) {
        // Check if block matches any active filter
        if (activeFilters != null && !activeFilters.isEmpty()) {
            HologramFilter match = HologramFilter.getMatchingFilter(state, activeFilters);
            if (match != null) {
                return match.getHighlightColor(); // Use filter's highlight color
            }
        }

        // Use EmptyBlockGetter for thread-safe map color access (mesh builds on background thread)
        // This gives default colors without biome tinting, which is acceptable for holograms
        int color = state.getMapColor(net.minecraft.world.level.EmptyBlockGetter.INSTANCE, BlockPos.ZERO).col;

        // Fallback to gray if color is 0 (transparent/none) to avoid black rendering
        return color != 0 ? color : com.devmod.client.ui.editor.core.DesignTokens.Nexus.HOLOGRAM_FALLBACK_BLOCK;
    }

    /**
     * Check if a block matches a filter.
     */
    private boolean isFilteredBlock(BlockState state) {
        if (activeFilters == null || activeFilters.isEmpty()) {
            return false;
        }
        return HologramFilter.getMatchingFilter(state, activeFilters) != null;
    }

    /**
     * Check if two blocks can be merged together.
     * In textured mode, merging is disabled because UV coordinates cannot extend
     * beyond sprite bounds in the texture atlas (would sample adjacent sprites).
     * In color mode, blocks with the same color can be merged.
     */
    private boolean canMergeBlocks(BlockState state1, BlockState state2, int x1, int y1, int z1, int x2, int y2, int z2) {
        if (texturedMode) {
            // Never merge in textured mode - UV coords would extend beyond sprite bounds
            return false;
        } else {
            // In color mode, merge by color
            return getBlockColor(state1, x1, y1, z1) == getBlockColor(state2, x2, y2, z2);
        }
    }

    /**
     * Get the TextureAtlasSprite for a block state.
     * Results are cached to avoid repeated lookups.
     */
    @Nullable
    private TextureAtlasSprite getBlockSprite(BlockState state) {
        if (spriteCache == null) {
            return null;
        }

        return spriteCache.computeIfAbsent(state, s -> {
            try {
                BlockRenderDispatcher dispatcher = Minecraft.getInstance().getBlockRenderer();
                BakedModel model = dispatcher.getBlockModel(s);
                return model.getParticleIcon();
            } catch (Exception e) {
                return null;
            }
        });
    }

    /*
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

    /*
     * Add a quad for the given face with UV support.
     */
    private void addQuad(Direction direction, int x, int y, int z, int uSize, int vSize,
                         float r, float g, float b, BlockState state, boolean isFiltered) {
        float[][] vertices = switch (direction) {
            case UP -> new float[][]{
                {x, y + 1, z + vSize},
                {x + uSize, y + 1, z + vSize},
                {x + uSize, y + 1, z},
                {x, y + 1, z}
            };
            case DOWN -> new float[][]{
                {x, y, z},
                {x + uSize, y, z},
                {x + uSize, y, z + vSize},
                {x, y, z + vSize}
            };
            case NORTH -> new float[][]{
                {x, y, z},
                {x, y + vSize, z},
                {x + uSize, y + vSize, z},
                {x + uSize, y, z}
            };
            case SOUTH -> new float[][]{
                {x + uSize, y, z + 1},
                {x + uSize, y + vSize, z + 1},
                {x, y + vSize, z + 1},
                {x, y, z + 1}
            };
            case WEST -> new float[][]{
                {x, y, z + uSize},
                {x, y + vSize, z + uSize},
                {x, y + vSize, z},
                {x, y, z}
            };
            case EAST -> new float[][]{
                {x + 1, y, z},
                {x + 1, y + vSize, z},
                {x + 1, y + vSize, z + uSize},
                {x + 1, y, z + uSize}
            };
        };

        // Calculate UV coordinates
        float[][] uvs;
        if (texturedMode) {
            TextureAtlasSprite sprite = getBlockSprite(state);
            if (sprite != null) {
                // Get sprite bounds in atlas
                float u0 = sprite.getU0();
                float v0 = sprite.getV0();
                float u1 = sprite.getU1();
                float v1 = sprite.getV1();

                // Calculate UVs that tile across the merged quad
                // Each block gets one full texture repeat
                float uScale = u1 - u0;
                float vScale = v1 - v0;

                uvs = switch (direction) {
                    case UP, DOWN -> new float[][]{
                        {u0, v0 + vScale * vSize},
                        {u0 + uScale * uSize, v0 + vScale * vSize},
                        {u0 + uScale * uSize, v0},
                        {u0, v0}
                    };
                    case NORTH, SOUTH -> new float[][]{
                        {u0, v0},
                        {u0, v0 + vScale * vSize},
                        {u0 + uScale * uSize, v0 + vScale * vSize},
                        {u0 + uScale * uSize, v0}
                    };
                    case WEST, EAST -> new float[][]{
                        {u0, v0},
                        {u0, v0 + vScale * vSize},
                        {u0 + uScale * uSize, v0 + vScale * vSize},
                        {u0 + uScale * uSize, v0}
                    };
                };
            } else {
                // Fallback: dummy UVs
                uvs = new float[][]{{0, 0}, {0, 0}, {0, 0}, {0, 0}};
            }
        } else {
            // Color mode: dummy UVs (shader ignores them)
            uvs = new float[][]{{0, 0}, {0, 0}, {0, 0}, {0, 0}};
        }

        // Alpha encodes filter state for shader: 1.0 = filtered, 0.5 = not filtered
        float alpha = isFiltered ? 1.0f : 0.5f;

        quads.add(new Quad(vertices[0], vertices[1], vertices[2], vertices[3],
                          uvs[0], uvs[1], uvs[2], uvs[3], r, g, b, alpha));
    }

    /*
     * Render all quads into the given buffer.
     *
     * @param builder The buffer builder
     * @param poseStack The pose stack
     * @param useUV Whether to include UV coordinates (for POSITION_TEX_COLOR format)
     */
    public void render(@Nonnull BufferBuilder builder, @Nonnull PoseStack poseStack, boolean useUV) {
        Matrix4f pose = poseStack.last().pose();
        for (Quad quad : quads) {
            quad.render(builder, pose, useUV);
        }
    }

    /*
     * Get the number of quads in this mesh.
     */
    public int getQuadCount() {
        return quads.size();
    }

    /*
     * Check if this mesh has no quads.
     */
    public boolean isEmpty() {
        return quads.isEmpty();
    }

    /*
     * Face directions for mesh building.
     */
    private enum Direction {
        UP, DOWN, NORTH, SOUTH, WEST, EAST
    }

    /*
     * A quad with four vertices, UV coordinates, and a color.
     */
    private static class Quad {
        final float[] v1, v2, v3, v4;
        final float[] uv1, uv2, uv3, uv4;
        final float r, g, b, a;

        Quad(float[] v1, float[] v2, float[] v3, float[] v4,
             float[] uv1, float[] uv2, float[] uv3, float[] uv4,
             float r, float g, float b, float a) {
            this.v1 = v1;
            this.v2 = v2;
            this.v3 = v3;
            this.v4 = v4;
            this.uv1 = uv1;
            this.uv2 = uv2;
            this.uv3 = uv3;
            this.uv4 = uv4;
            this.r = r;
            this.g = g;
            this.b = b;
            this.a = a;
        }

        void render(BufferBuilder builder, Matrix4f pose, boolean useUV) {
            if (useUV) {
                // POSITION_TEX_COLOR format
                builder.addVertex(pose, v1[0], v1[1], v1[2]).setUv(uv1[0], uv1[1]).setColor(r, g, b, a);
                builder.addVertex(pose, v2[0], v2[1], v2[2]).setUv(uv2[0], uv2[1]).setColor(r, g, b, a);
                builder.addVertex(pose, v3[0], v3[1], v3[2]).setUv(uv3[0], uv3[1]).setColor(r, g, b, a);
                builder.addVertex(pose, v4[0], v4[1], v4[2]).setUv(uv4[0], uv4[1]).setColor(r, g, b, a);
            } else {
                // POSITION_COLOR format (fallback)
                builder.addVertex(pose, v1[0], v1[1], v1[2]).setColor(r, g, b, a);
                builder.addVertex(pose, v2[0], v2[1], v2[2]).setColor(r, g, b, a);
                builder.addVertex(pose, v3[0], v3[1], v3[2]).setColor(r, g, b, a);
                builder.addVertex(pose, v4[0], v4[1], v4[2]).setColor(r, g, b, a);
            }
        }
    }
}
