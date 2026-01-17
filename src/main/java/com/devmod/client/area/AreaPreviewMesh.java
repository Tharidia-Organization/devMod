package com.devmod.client.area;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.joml.Matrix4f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.synth.SimplexNoise;

import com.devmod.area.builder.AreaShapeGenerator;
import com.devmod.area.data.AreaDimensions;
import com.devmod.area.data.AreaPalette;
import com.devmod.area.data.AreaShape;

/**
 * Generates a holographic preview mesh for an Area.
 * Shows floor, walls, and ceiling as semi-transparent colored blocks.
 * Supports biome terrain generation with height variations.
 */
public final class AreaPreviewMesh {
    private static final Logger LOGGER = LoggerFactory.getLogger(AreaPreviewMesh.class);

    private final List<Quad> quads = new ArrayList<>();
    private final int width;
    private final int length;
    private final int height;

    // Colors for standard room elements (RGB values 0-1)
    private static final float[] FLOOR_COLOR = {0.3f, 0.5f, 0.7f};     // Blue-gray
    private static final float[] WALL_COLOR = {0.4f, 0.4f, 0.5f};      // Dark gray
    private static final float[] CEILING_COLOR = {0.5f, 0.5f, 0.6f};   // Light gray

    // Biome-specific colors
    private static final Map<String, float[]> BIOME_COLORS = new HashMap<>();
    static {
        BIOME_COLORS.put("plains", new float[]{0.4f, 0.7f, 0.3f});      // Green grass
        BIOME_COLORS.put("forest", new float[]{0.3f, 0.6f, 0.25f});    // Dark green
        BIOME_COLORS.put("desert", new float[]{0.9f, 0.85f, 0.6f});    // Sand
        BIOME_COLORS.put("badlands", new float[]{0.8f, 0.5f, 0.3f});   // Orange terracotta
        BIOME_COLORS.put("snowy", new float[]{0.95f, 0.95f, 0.98f});   // White snow
        BIOME_COLORS.put("frozen", new float[]{0.8f, 0.9f, 0.95f});    // Ice blue
        BIOME_COLORS.put("ice", new float[]{0.7f, 0.85f, 0.95f});      // Ice blue
        BIOME_COLORS.put("taiga", new float[]{0.35f, 0.5f, 0.35f});    // Podzol brown-green
        BIOME_COLORS.put("jungle", new float[]{0.2f, 0.55f, 0.2f});    // Deep green
        BIOME_COLORS.put("swamp", new float[]{0.35f, 0.45f, 0.3f});    // Murky green
        BIOME_COLORS.put("mushroom", new float[]{0.6f, 0.5f, 0.6f});   // Mycelium purple
        BIOME_COLORS.put("beach", new float[]{0.95f, 0.9f, 0.7f});     // Sand
        BIOME_COLORS.put("ocean", new float[]{0.2f, 0.4f, 0.7f});      // Deep blue
        BIOME_COLORS.put("river", new float[]{0.3f, 0.5f, 0.7f});      // Blue
        BIOME_COLORS.put("mountain", new float[]{0.5f, 0.5f, 0.5f});   // Gray stone
        BIOME_COLORS.put("peak", new float[]{0.6f, 0.6f, 0.6f});       // Light stone
        BIOME_COLORS.put("stony", new float[]{0.55f, 0.55f, 0.55f});   // Stone
        BIOME_COLORS.put("nether", new float[]{0.6f, 0.25f, 0.2f});    // Netherrack red
        BIOME_COLORS.put("soul", new float[]{0.4f, 0.3f, 0.25f});      // Soul sand brown
        BIOME_COLORS.put("basalt", new float[]{0.25f, 0.25f, 0.3f});   // Dark basalt
        BIOME_COLORS.put("crimson", new float[]{0.6f, 0.15f, 0.2f});   // Crimson red
        BIOME_COLORS.put("warped", new float[]{0.2f, 0.5f, 0.5f});     // Warped teal
        BIOME_COLORS.put("end", new float[]{0.85f, 0.85f, 0.7f});      // End stone yellow
        BIOME_COLORS.put("deep_dark", new float[]{0.15f, 0.2f, 0.25f}); // Sculk dark
        BIOME_COLORS.put("cherry", new float[]{0.9f, 0.7f, 0.8f});     // Cherry pink
        BIOME_COLORS.put("mangrove", new float[]{0.45f, 0.35f, 0.3f}); // Mud brown
        BIOME_COLORS.put("meadow", new float[]{0.5f, 0.75f, 0.4f});    // Bright green
        BIOME_COLORS.put("savanna", new float[]{0.7f, 0.65f, 0.4f});   // Dry grass
    }

    // Noise constants - must match BiomeAreaGenerator for preview accuracy
    private static final double NOISE_SCALE_PRIMARY = 0.08;    // Match BiomeAreaGenerator
    private static final double NOISE_SCALE_SECONDARY = 0.2;   // Match BiomeAreaGenerator
    private static final double NOISE_SCALE_TERTIARY = 0.35;   // Match BiomeAreaGenerator
    private static final double WEIGHT_PRIMARY = 0.6;
    private static final double WEIGHT_SECONDARY = 0.3;
    private static final double WEIGHT_TERTIARY = 0.1;
    private static final int DEFAULT_AMPLITUDE = 16;           // Match BiomeAreaGenerator
    private static final int AMPLIFIED_AMPLITUDE = 48;         // Match BiomeAreaGenerator (was 32)
    private static final int FLOATING_AMPLITUDE = 24;          // Match BiomeAreaGenerator (was 20)

    /**
     * Creates a preview mesh for standard room (no biome).
     */
    public AreaPreviewMesh(
        @Nonnull AreaShape shape,
        @Nonnull BlockPos center,
        @Nonnull AreaDimensions dimensions,
        @Nonnull AreaPalette palette
    ) {
        this(shape, center, dimensions, palette, false, null, null, 0L);
    }

    /**
     * Creates a preview mesh with optional biome terrain.
     *
     * @param shape        The area shape
     * @param center       The center position
     * @param dimensions   The area dimensions
     * @param palette      The block palette (for color hints)
     * @param useBiome     Whether to generate biome terrain
     * @param biomeId      The biome ID (e.g., "minecraft:desert")
     * @param terrainStyle The terrain style ("NATURAL", "FLAT", "AMPLIFIED", "FLOATING")
     * @param seed         The seed for terrain generation
     */
    public AreaPreviewMesh(
        @Nonnull AreaShape shape,
        @Nonnull BlockPos center,
        @Nonnull AreaDimensions dimensions,
        @Nonnull AreaPalette palette,
        boolean useBiome,
        @Nullable String biomeId,
        @Nullable String terrainStyle,
        long seed
    ) {
        this.width = dimensions.width();
        this.length = dimensions.length();
        this.height = dimensions.height();

        if (useBiome && biomeId != null && !biomeId.isEmpty()) {
            // Generate biome terrain preview
            generateBiomeTerrain(shape, center, dimensions, biomeId, terrainStyle, seed);
        } else {
            // Generate standard room preview
            generateStandardRoom(shape, center, dimensions);
        }
    }

    /**
     * Generates standard room preview (floor, walls, ceiling).
     */
    private void generateStandardRoom(AreaShape shape, BlockPos center, AreaDimensions dimensions) {
        Set<BlockPos> floorPositions = AreaShapeGenerator.generateFloor(Objects.requireNonNull(shape), Objects.requireNonNull(center), Objects.requireNonNull(dimensions), null);
        Set<BlockPos> wallPositions = AreaShapeGenerator.generateWalls(Objects.requireNonNull(shape), Objects.requireNonNull(center), Objects.requireNonNull(dimensions), null);
        Set<BlockPos> ceilingPositions = generateCeiling(floorPositions, dimensions);

        buildFloorQuads(floorPositions, FLOOR_COLOR);
        buildWallQuads(wallPositions, WALL_COLOR);
        buildCeilingQuads(ceilingPositions, CEILING_COLOR);
    }

    /**
     * Generates biome terrain preview with height variations.
     */
    private void generateBiomeTerrain(AreaShape shape, BlockPos center, AreaDimensions dimensions,
                                       String biomeId, @Nullable String terrainStyle, long seed) {
        LOGGER.info("[AreaPreviewMesh] generateBiomeTerrain called:");
        LOGGER.info("[AreaPreviewMesh]   biomeId={}", biomeId);
        LOGGER.info("[AreaPreviewMesh]   terrainStyle={}", terrainStyle);
        LOGGER.info("[AreaPreviewMesh]   seed={}", seed);
        LOGGER.info("[AreaPreviewMesh]   shape={}", shape);
        LOGGER.info("[AreaPreviewMesh]   center={}", center);
        LOGGER.info("[AreaPreviewMesh]   dimensions={}x{}x{}, floorY={}",
            dimensions.width(), dimensions.length(), dimensions.height(), dimensions.floorY());

        // Get biome color
        float[] baseColor = getBiomeColor(biomeId);
        LOGGER.info("[AreaPreviewMesh]   biomeColor=RGB({}, {}, {})", baseColor[0], baseColor[1], baseColor[2]);

        // Get terrain amplitude based on style, but clamp to available height
        int baseAmplitude = getTerrainAmplitude(terrainStyle);
        // Scale amplitude to fit within area height - use 80% of height for terrain variation
        int maxUsableAmplitude = (int) (height * 0.8);
        int amplitude = Math.min(baseAmplitude, maxUsableAmplitude);
        boolean isFlat = "FLAT".equalsIgnoreCase(terrainStyle);
        boolean isFloating = "FLOATING".equalsIgnoreCase(terrainStyle);
        boolean isAmplified = "AMPLIFIED".equalsIgnoreCase(terrainStyle);
        LOGGER.info("[AreaPreviewMesh]   baseAmplitude={}, scaledAmplitude={} (maxUsable={}), isFlat={}, isFloating={}",
            baseAmplitude, amplitude, maxUsableAmplitude, isFlat, isFloating);

        // Create noise generator matching server logic
        long effectiveSeed = seed == 0L ? System.nanoTime() : seed;
        RandomSource random = RandomSource.create(effectiveSeed);
        SimplexNoise noise = new SimplexNoise(Objects.requireNonNull(random));

        // Generate floor positions for the shape
        Set<BlockPos> shapePositions = AreaShapeGenerator.generateFloor(Objects.requireNonNull(shape), Objects.requireNonNull(center), dimensions, null);

        int baseY = dimensions.floorY();
        int maxHeight = dimensions.height();

        // Generate terrain columns
        Map<BlockPos, Integer> terrainHeights = new HashMap<>();

        // Debug: track noise value range
        double minNoise = Double.MAX_VALUE;
        double maxNoise = Double.MIN_VALUE;

        for (BlockPos floorPos : shapePositions) {
            int terrainHeight;

            if (isFlat) {
                terrainHeight = baseY;
            } else if (isFloating) {
                // Floating islands - some gaps
                double islandNoise = noise.getValue(
                    floorPos.getX() * NOISE_SCALE_PRIMARY * 0.5,
                    floorPos.getZ() * NOISE_SCALE_PRIMARY * 0.5
                );
                islandNoise = Mth.clamp((islandNoise / 0.7 + 1.0) / 2.0, 0.0, 1.0);
                if (islandNoise < 0.35) {
                    continue; // Gap in floating island
                }
                double heightNoise = noise.getValue(
                    floorPos.getX() * NOISE_SCALE_SECONDARY + 500,
                    floorPos.getZ() * NOISE_SCALE_SECONDARY + 500
                );
                heightNoise = Mth.clamp((heightNoise / 0.7 + 1.0) / 2.0, 0.0, 1.0);
                terrainHeight = baseY + (int) (heightNoise * amplitude);
            } else {
                // Natural terrain with height variations
                double n1;
                double n2;
                double combined;

                if (isAmplified) {
                    n1 = noise.getValue(floorPos.getX() * NOISE_SCALE_PRIMARY * 0.8,
                        floorPos.getZ() * NOISE_SCALE_PRIMARY * 0.8);
                    n2 = noise.getValue(floorPos.getX() * NOISE_SCALE_SECONDARY,
                        floorPos.getZ() * NOISE_SCALE_SECONDARY);
                    combined = n1 * 0.7 + n2 * 0.3;
                } else {
                    n1 = noise.getValue(floorPos.getX() * NOISE_SCALE_PRIMARY,
                        floorPos.getZ() * NOISE_SCALE_PRIMARY);
                    n2 = noise.getValue(floorPos.getX() * NOISE_SCALE_SECONDARY + 1000,
                        floorPos.getZ() * NOISE_SCALE_SECONDARY + 1000);
                    double n3 = noise.getValue(floorPos.getX() * NOISE_SCALE_TERTIARY + 2000,
                                               floorPos.getZ() * NOISE_SCALE_TERTIARY + 2000);
                    combined = n1 * WEIGHT_PRIMARY + n2 * WEIGHT_SECONDARY + n3 * WEIGHT_TERTIARY;
                }

                // Track for debugging
                minNoise = Math.min(minNoise, combined);
                maxNoise = Math.max(maxNoise, combined);

                // Scale noise more aggressively - Perlin returns ~[-0.7, 0.7], not [-1, 1]
                combined = Mth.clamp((combined / 0.7 + 1.0) / 2.0, 0.0, 1.0);
                if (isAmplified) {
                    combined = Math.pow(combined, 0.8);
                }
                terrainHeight = baseY + (int) (combined * amplitude);
            }

            terrainHeight = Mth.clamp(terrainHeight, baseY, baseY + maxHeight - 1);
            terrainHeights.put(floorPos, terrainHeight);
        }

        // Log noise range for debugging
        if (minNoise != Double.MAX_VALUE) {
            LOGGER.info("[AreaPreviewMesh]   noiseRange: min={}, max={}, range={}",
                String.format("%.4f", minNoise), String.format("%.4f", maxNoise),
                String.format("%.4f", maxNoise - minNoise));
        }

        // Build terrain surface quads
        for (Map.Entry<BlockPos, Integer> entry : terrainHeights.entrySet()) {
            BlockPos basePos = entry.getKey();
            int terrainY = entry.getValue();

            // Add top surface
            addBlockTopFaceAt(basePos.getX(), terrainY, basePos.getZ(), baseColor[0], baseColor[1], baseColor[2]);

            // Add side faces where terrain drops
            addTerrainSideFaces(basePos, terrainY, terrainHeights, baseColor);
        }

        // Log terrain height statistics
        if (!terrainHeights.isEmpty()) {
            int terrainMinY = terrainHeights.values().stream().mapToInt(Integer::intValue).min().orElse(baseY);
            int terrainMaxY = terrainHeights.values().stream().mapToInt(Integer::intValue).max().orElse(baseY);
            LOGGER.info("[AreaPreviewMesh]   terrainHeights: {} positions, minY={}, maxY={}, range={}",
                terrainHeights.size(), terrainMinY, terrainMaxY, terrainMaxY - terrainMinY);
        }

        // Add bounding walls (semi-transparent outline)
        Set<BlockPos> perimeter = AreaShapeGenerator.generatePerimeter(Objects.requireNonNull(shape), Objects.requireNonNull(center), dimensions, null);
        for (BlockPos perimPos : perimeter) {
            int terrainY = terrainHeights.getOrDefault(perimPos, baseY);
            // Draw wall from terrain to max height
            for (int y = terrainY + 1; y <= baseY + height; y++) {
                addBlockSideFacesAt(perimPos.getX(), y, perimPos.getZ(),
                    WALL_COLOR[0] * 0.5f, WALL_COLOR[1] * 0.5f, WALL_COLOR[2] * 0.5f);
            }
        }

        LOGGER.info("[AreaPreviewMesh]   Generated {} quads total for biome terrain", quads.size());
    }

    /**
     * Gets the color for a biome.
     */
    private float[] getBiomeColor(String biomeId) {
        String biomePath = biomeId.toLowerCase(Locale.ROOT);
        // Remove namespace if present
        if (biomePath.contains(":")) {
            biomePath = biomePath.substring(biomePath.indexOf(":") + 1);
        }

        // Find matching biome color
        for (Map.Entry<String, float[]> entry : BIOME_COLORS.entrySet()) {
            if (biomePath.contains(entry.getKey())) {
                return entry.getValue();
            }
        }

        // Default to grass green
        return new float[]{0.4f, 0.7f, 0.3f};
    }

    /**
     * Gets terrain amplitude for style.
     */
    private int getTerrainAmplitude(@Nullable String style) {
        if (style == null) return DEFAULT_AMPLITUDE;
        return switch (style.toUpperCase(Locale.ROOT)) {
            case "FLAT" -> 0;
            case "AMPLIFIED" -> AMPLIFIED_AMPLITUDE;
            case "FLOATING" -> FLOATING_AMPLITUDE;
            default -> DEFAULT_AMPLITUDE;
        };
    }

    /**
     * Adds side faces where terrain drops to neighboring blocks.
     */
    private void addTerrainSideFaces(BlockPos pos, int terrainY, Map<BlockPos, Integer> heights, float[] color) {
        // Check each neighbor
        int[][] neighbors = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
        float darkerR = color[0] * 0.7f;
        float darkerG = color[1] * 0.7f;
        float darkerB = color[2] * 0.7f;

        for (int[] offset : neighbors) {
            BlockPos neighborPos = pos.offset(offset[0], 0, offset[1]);
            Integer neighborHeight = heights.get(neighborPos);

            if (neighborHeight == null || neighborHeight < terrainY) {
                // Neighbor is lower or doesn't exist - draw side face
                int minY = neighborHeight != null ? neighborHeight + 1 : terrainY;
                for (int y = minY; y <= terrainY; y++) {
                    if (offset[0] == -1) { // West
                        addWestFace(pos.getX(), y, pos.getZ(), darkerR, darkerG, darkerB);
                    } else if (offset[0] == 1) { // East
                        addEastFace(pos.getX(), y, pos.getZ(), darkerR, darkerG, darkerB);
                    } else if (offset[1] == -1) { // North
                        addNorthFace(pos.getX(), y, pos.getZ(), darkerR, darkerG, darkerB);
                    } else { // South
                        addSouthFace(pos.getX(), y, pos.getZ(), darkerR, darkerG, darkerB);
                    }
                }
            }
        }
    }

    // ========================================================================
    // Shape Helpers
    // ========================================================================

    private Set<BlockPos> generateCeiling(Set<BlockPos> floorPositions, AreaDimensions dims) {
        Set<BlockPos> ceiling = new HashSet<>();
        for (BlockPos floorPos : floorPositions) {
            ceiling.add(floorPos.above(dims.height()));
        }
        return ceiling;
    }

    // ========================================================================
    // Quad Building
    // ========================================================================

    private void buildFloorQuads(Set<BlockPos> positions, float[] color) {
        for (BlockPos pos : positions) {
            addBlockTopFaceAt(pos.getX(), pos.getY(), pos.getZ(), color[0], color[1], color[2]);
        }
    }

    private void buildWallQuads(Set<BlockPos> positions, float[] color) {
        for (BlockPos pos : positions) {
            addBlockSideFacesAt(pos.getX(), pos.getY(), pos.getZ(), color[0], color[1], color[2]);
        }
    }

    private void buildCeilingQuads(Set<BlockPos> positions, float[] color) {
        for (BlockPos pos : positions) {
            addBlockBottomFaceAt(pos.getX(), pos.getY(), pos.getZ(), color[0], color[1], color[2]);
        }
    }

    private void addBlockTopFaceAt(int x, int y, int z, float r, float g, float b) {
        Quad quad = new Quad(r, g, b);
        quad.v1 = new float[]{x, y + 1, z + 1};
        quad.v2 = new float[]{x + 1, y + 1, z + 1};
        quad.v3 = new float[]{x + 1, y + 1, z};
        quad.v4 = new float[]{x, y + 1, z};
        quads.add(quad);
    }

    private void addBlockBottomFaceAt(int x, int y, int z, float r, float g, float b) {
        Quad quad = new Quad(r, g, b);
        quad.v1 = new float[]{x, y, z};
        quad.v2 = new float[]{x + 1, y, z};
        quad.v3 = new float[]{x + 1, y, z + 1};
        quad.v4 = new float[]{x, y, z + 1};
        quads.add(quad);
    }

    private void addBlockSideFacesAt(int x, int y, int z, float r, float g, float b) {
        addNorthFace(x, y, z, r * 0.8f, g * 0.8f, b * 0.8f);
        addSouthFace(x, y, z, r * 0.8f, g * 0.8f, b * 0.8f);
        addWestFace(x, y, z, r * 0.7f, g * 0.7f, b * 0.7f);
        addEastFace(x, y, z, r * 0.7f, g * 0.7f, b * 0.7f);
    }

    private void addNorthFace(int x, int y, int z, float r, float g, float b) {
        Quad quad = new Quad(r, g, b);
        quad.v1 = new float[]{x, y, z};
        quad.v2 = new float[]{x, y + 1, z};
        quad.v3 = new float[]{x + 1, y + 1, z};
        quad.v4 = new float[]{x + 1, y, z};
        quads.add(quad);
    }

    private void addSouthFace(int x, int y, int z, float r, float g, float b) {
        Quad quad = new Quad(r, g, b);
        quad.v1 = new float[]{x + 1, y, z + 1};
        quad.v2 = new float[]{x + 1, y + 1, z + 1};
        quad.v3 = new float[]{x, y + 1, z + 1};
        quad.v4 = new float[]{x, y, z + 1};
        quads.add(quad);
    }

    private void addWestFace(int x, int y, int z, float r, float g, float b) {
        Quad quad = new Quad(r, g, b);
        quad.v1 = new float[]{x, y, z + 1};
        quad.v2 = new float[]{x, y + 1, z + 1};
        quad.v3 = new float[]{x, y + 1, z};
        quad.v4 = new float[]{x, y, z};
        quads.add(quad);
    }

    private void addEastFace(int x, int y, int z, float r, float g, float b) {
        Quad quad = new Quad(r, g, b);
        quad.v1 = new float[]{x + 1, y, z};
        quad.v2 = new float[]{x + 1, y + 1, z};
        quad.v3 = new float[]{x + 1, y + 1, z + 1};
        quad.v4 = new float[]{x + 1, y, z + 1};
        quads.add(quad);
    }

    // ========================================================================
    // Rendering
    // ========================================================================

    /**
     * Render all quads into the given buffer.
     */
    public void render(@Nonnull BufferBuilder builder, @Nonnull PoseStack poseStack, float alpha) {
        Matrix4f pose = poseStack.last().pose();
        for (Quad quad : quads) {
            quad.render(builder, pose, alpha);
        }
    }

    public int getQuadCount() { return quads.size(); }
    public boolean isEmpty() { return quads.isEmpty(); }
    public int getWidth() { return width; }
    public int getLength() { return length; }
    public int getHeight() { return height; }

    // ========================================================================
    // Helper Classes
    // ========================================================================

    private static class Quad {
        float[] v1 = new float[3];
        float[] v2 = new float[3];
        float[] v3 = new float[3];
        float[] v4 = new float[3];
        final float r, g, b;

        Quad(float r, float g, float b) {
            this.r = r;
            this.g = g;
            this.b = b;
        }

        void render(BufferBuilder builder, Matrix4f pose, float alpha) {
            Matrix4f mat = Objects.requireNonNull(pose);
            builder.addVertex(mat, v1[0], v1[1], v1[2]).setColor(r, g, b, alpha);
            builder.addVertex(mat, v2[0], v2[1], v2[2]).setColor(r, g, b, alpha);
            builder.addVertex(mat, v3[0], v3[1], v3[2]).setColor(r, g, b, alpha);
            builder.addVertex(mat, v4[0], v4[1], v4[2]).setColor(r, g, b, alpha);
        }
    }

}
