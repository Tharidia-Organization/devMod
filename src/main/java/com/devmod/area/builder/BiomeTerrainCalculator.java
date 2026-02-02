package com.devmod.area.builder;

import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.synth.SimplexNoise;

import com.devmod.area.data.BiomeGenerationConfig.TerrainStyle;

/**
 * Calculates terrain heights for biome generation using multi-octave Simplex noise.
 * Extracted from BiomeAreaGenerator for better modularity.
 */
public final class BiomeTerrainCalculator {

    private BiomeTerrainCalculator() {}

    // ============================================================================
    // NOISE CONSTANTS
    // ============================================================================

    /** Base noise scale for terrain height - increased for visible variation in small areas */
    private static final double NOISE_SCALE_PRIMARY = 0.08;

    /** Secondary noise scale for detail */
    private static final double NOISE_SCALE_SECONDARY = 0.2;

    /** Tertiary noise scale for micro-detail */
    private static final double NOISE_SCALE_TERTIARY = 0.35;

    /** Weight for primary noise */
    private static final double WEIGHT_PRIMARY = 0.6;

    /** Weight for secondary noise */
    private static final double WEIGHT_SECONDARY = 0.3;

    /** Weight for tertiary noise */
    private static final double WEIGHT_TERTIARY = 0.1;

    // ============================================================================
    // AMPLITUDE CONSTANTS
    // ============================================================================

    /** Default terrain amplitude */
    private static final int DEFAULT_AMPLITUDE = 16;

    /** Amplified terrain amplitude */
    private static final int AMPLIFIED_AMPLITUDE = 48;

    /** Floating island amplitude */
    private static final int FLOATING_AMPLITUDE = 24;

    // ============================================================================
    // PUBLIC API
    // ============================================================================

    /**
     * Calculates terrain height at a position using multi-octave Simplex noise.
     *
     * @param x         World X coordinate
     * @param z         World Z coordinate
     * @param noise     SimplexNoise generator
     * @param style     Terrain style (FLAT, NATURAL, AMPLIFIED, FLOATING)
     * @param baseY     Base Y level of the area
     * @param maxHeight Maximum height of the area
     * @return Terrain height, or -1 for floating island gaps
     */
    public static int calculateTerrainHeight(int x, int z, SimplexNoise noise,
                                              TerrainStyle style, int baseY, int maxHeight) {
        return switch (style) {
            case FLAT -> baseY;

            case NATURAL -> calculateNaturalHeight(x, z, noise, baseY, maxHeight);

            case AMPLIFIED -> calculateAmplifiedHeight(x, z, noise, baseY, maxHeight);

            case FLOATING -> calculateFloatingHeight(x, z, noise, baseY, maxHeight);
        };
    }

    // ============================================================================
    // INTERNAL CALCULATIONS
    // ============================================================================

    private static int calculateNaturalHeight(int x, int z, SimplexNoise noise, int baseY, int maxHeight) {
        double n1 = noise.getValue(x * NOISE_SCALE_PRIMARY, z * NOISE_SCALE_PRIMARY);
        double n2 = noise.getValue(x * NOISE_SCALE_SECONDARY + 1000, z * NOISE_SCALE_SECONDARY + 1000);
        double n3 = noise.getValue(x * NOISE_SCALE_TERTIARY + 2000, z * NOISE_SCALE_TERTIARY + 2000);

        double combined = (n1 * WEIGHT_PRIMARY + n2 * WEIGHT_SECONDARY + n3 * WEIGHT_TERTIARY);
        // Scale noise - Perlin/Simplex returns ~[-0.7, 0.7], not [-1, 1]
        combined = Mth.clamp((combined / 0.7 + 1.0) / 2.0, 0.0, 1.0);

        // Scale amplitude to fit within area height
        int scaledAmplitude = Math.min(DEFAULT_AMPLITUDE, (int) (maxHeight * 0.8));
        int height = (int) (combined * scaledAmplitude);

        // CRIT-02 fix: Use long arithmetic to prevent overflow
        long resultY = (long) baseY + height;
        long maxY = (long) baseY + maxHeight - 1;
        return (int) Mth.clamp(resultY, baseY, Math.min(maxY, Integer.MAX_VALUE));
    }

    private static int calculateAmplifiedHeight(int x, int z, SimplexNoise noise, int baseY, int maxHeight) {
        double n1 = noise.getValue(x * NOISE_SCALE_PRIMARY * 0.8, z * NOISE_SCALE_PRIMARY * 0.8);
        double n2 = noise.getValue(x * NOISE_SCALE_SECONDARY, z * NOISE_SCALE_SECONDARY);

        double combined = n1 * 0.7 + n2 * 0.3;
        // Scale noise - Perlin/Simplex returns ~[-0.7, 0.7], not [-1, 1]
        combined = Mth.clamp((combined / 0.7 + 1.0) / 2.0, 0.0, 1.0);

        // Apply exponential scaling for dramatic peaks
        combined = Math.pow(combined, 0.8);

        // Scale amplitude to fit within area height
        int scaledAmplitude = Math.min(AMPLIFIED_AMPLITUDE, (int) (maxHeight * 0.8));
        int height = (int) (combined * scaledAmplitude);

        // CRIT-02 fix: Use long arithmetic to prevent overflow
        long resultY = (long) baseY + height;
        long maxY = (long) baseY + maxHeight - 1;
        return (int) Mth.clamp(resultY, baseY, Math.min(maxY, Integer.MAX_VALUE));
    }

    private static int calculateFloatingHeight(int x, int z, SimplexNoise noise, int baseY, int maxHeight) {
        // Calculate base island shape
        double islandNoise = noise.getValue(x * NOISE_SCALE_PRIMARY * 0.5, z * NOISE_SCALE_PRIMARY * 0.5);
        // Scale noise - Perlin/Simplex returns ~[-0.7, 0.7]
        islandNoise = Mth.clamp((islandNoise / 0.7 + 1.0) / 2.0, 0.0, 1.0);

        // Create distinct islands using threshold
        if (islandNoise < 0.35) {
            return baseY - 1; // No terrain here (gap below base)
        }

        // Height variation on islands
        double heightNoise = noise.getValue(x * NOISE_SCALE_SECONDARY + 500, z * NOISE_SCALE_SECONDARY + 500);
        heightNoise = Mth.clamp((heightNoise / 0.7 + 1.0) / 2.0, 0.0, 1.0);

        // Scale amplitude to fit within area height
        int scaledAmplitude = Math.min(FLOATING_AMPLITUDE, (int) (maxHeight * 0.8));
        int heightOffset = (int) (heightNoise * scaledAmplitude);

        // CRIT-02 fix: Use long arithmetic to prevent overflow
        long resultY = (long) baseY + heightOffset;
        long maxY = (long) baseY + maxHeight - 1;
        return (int) Mth.clamp(resultY, baseY, Math.min(maxY, Integer.MAX_VALUE));
    }
}
