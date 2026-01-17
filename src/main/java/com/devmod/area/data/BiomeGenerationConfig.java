package com.devmod.area.data;

import java.util.Objects;

import javax.annotation.Nonnull;

import io.netty.buffer.ByteBuf;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.StringRepresentable;

/**
 * Configuration for biome-based terrain generation within an area.
 *
 * @param biomeId            Biome resource location (e.g., "minecraft:plains")
 * @param seed               Seed for terrain generation (0 = random)
 * @param generateStructures Whether to generate structures (villages, etc.)
 * @param generateFeatures   Whether to generate features (trees, flowers, etc.)
 * @param generateOres       Whether to generate ores underground
 * @param seaLevel           Sea level for water generation
 * @param terrainStyle       Style of terrain generation
 */
public record BiomeGenerationConfig(
    ResourceLocation biomeId,
    long seed,
    boolean generateStructures,
    boolean generateFeatures,
    boolean generateOres,
    int seaLevel,
    TerrainStyle terrainStyle
) {
    public static final int DEFAULT_SEA_LEVEL = 63;

    public static final BiomeGenerationConfig DEFAULT = new BiomeGenerationConfig(
        ResourceLocation.withDefaultNamespace("plains"),
        0L,
        false,
        true,
        false,
        DEFAULT_SEA_LEVEL,
        TerrainStyle.NATURAL
    );

    public static final Codec<BiomeGenerationConfig> CODEC = RecordCodecBuilder.create(instance ->
        instance.group(
            ResourceLocation.CODEC.fieldOf("biomeId").forGetter(BiomeGenerationConfig::biomeId),
            Codec.LONG.fieldOf("seed").forGetter(BiomeGenerationConfig::seed),
            Codec.BOOL.fieldOf("generateStructures").forGetter(BiomeGenerationConfig::generateStructures),
            Codec.BOOL.fieldOf("generateFeatures").forGetter(BiomeGenerationConfig::generateFeatures),
            Codec.BOOL.fieldOf("generateOres").forGetter(BiomeGenerationConfig::generateOres),
            Codec.INT.fieldOf("seaLevel").forGetter(BiomeGenerationConfig::seaLevel),
            TerrainStyle.CODEC.fieldOf("terrainStyle").forGetter(BiomeGenerationConfig::terrainStyle)
        ).apply(instance, (biome, seed, structures, features, ores, seaLvl, style) ->
            new BiomeGenerationConfig(biome,
                Objects.requireNonNull(seed),
                Objects.requireNonNull(structures),
                Objects.requireNonNull(features),
                Objects.requireNonNull(ores),
                Objects.requireNonNull(seaLvl),
                style))
    );

    /**
     * M-03 fix: Safe decoder that returns NATURAL for invalid TerrainStyle ordinals instead of silent clamping.
     */
    public static final StreamCodec<ByteBuf, BiomeGenerationConfig> STREAM_CODEC = StreamCodec.of(
        (buf, config) -> {
            ResourceLocation.STREAM_CODEC.encode(buf, Objects.requireNonNull(config.biomeId));
            ByteBufCodecs.VAR_LONG.encode(buf, config.seed);
            ByteBufCodecs.BOOL.encode(buf, config.generateStructures);
            ByteBufCodecs.BOOL.encode(buf, config.generateFeatures);
            ByteBufCodecs.BOOL.encode(buf, config.generateOres);
            ByteBufCodecs.VAR_INT.encode(buf, config.seaLevel);
            ByteBufCodecs.VAR_INT.encode(buf, config.terrainStyle.ordinal());
        },
        buf -> {
            ResourceLocation biome = ResourceLocation.STREAM_CODEC.decode(buf);
            long seed = ByteBufCodecs.VAR_LONG.decode(buf);
            boolean structures = ByteBufCodecs.BOOL.decode(buf);
            boolean features = ByteBufCodecs.BOOL.decode(buf);
            boolean ores = ByteBufCodecs.BOOL.decode(buf);
            int seaLvl = ByteBufCodecs.VAR_INT.decode(buf);
            int styleOrdinal = ByteBufCodecs.VAR_INT.decode(buf);

            // M-03 fix: Safe ordinal decoding with warning for unknown values
            TerrainStyle style;
            TerrainStyle[] vals = TerrainStyle.values();
            if (styleOrdinal < 0 || styleOrdinal >= vals.length) {
                org.slf4j.LoggerFactory.getLogger(BiomeGenerationConfig.class)
                    .warn("[BiomeGenerationConfig] Unknown TerrainStyle ordinal {} (max: {}), defaulting to NATURAL.",
                        styleOrdinal, vals.length - 1);
                style = TerrainStyle.NATURAL;
            } else {
                style = vals[styleOrdinal];
            }

            return new BiomeGenerationConfig(biome, seed, structures, features, ores, seaLvl, style);
        }
    );

    /**
     * Optional stream codec for nullable BiomeGenerationConfig.
     * Writes a boolean flag followed by the config data if present.
     */
    public static final StreamCodec<ByteBuf, BiomeGenerationConfig> OPTIONAL_STREAM_CODEC = StreamCodec.of(
        (buf, config) -> {
            boolean present = config != null;
            ByteBufCodecs.BOOL.encode(buf, present);
            if (present) {
                STREAM_CODEC.encode(buf, config);
            }
        },
        buf -> {
            boolean present = ByteBufCodecs.BOOL.decode(buf);
            return present ? STREAM_CODEC.decode(buf) : null;
        }
    );

    /**
     * Returns a copy with a new seed.
     */
    @Nonnull
    public BiomeGenerationConfig withSeed(long newSeed) {
        return new BiomeGenerationConfig(biomeId, newSeed, generateStructures,
            generateFeatures, generateOres, seaLevel, terrainStyle);
    }

    /**
     * Returns a copy with a new biome.
     */
    @Nonnull
    public BiomeGenerationConfig withBiome(@Nonnull ResourceLocation newBiome) {
        return new BiomeGenerationConfig(newBiome, seed, generateStructures,
            generateFeatures, generateOres, seaLevel, terrainStyle);
    }

    /**
     * Returns a copy with a random seed based on current time.
     */
    @Nonnull
    public BiomeGenerationConfig withRandomSeed() {
        return withSeed(System.nanoTime());
    }

    /**
     * Returns a copy with a new terrain style.
     */
    @Nonnull
    public BiomeGenerationConfig withTerrainStyle(@Nonnull TerrainStyle newStyle) {
        return new BiomeGenerationConfig(biomeId, seed, generateStructures,
            generateFeatures, generateOres, seaLevel, newStyle);
    }

    /**
     * Returns a copy with feature generation toggled.
     */
    @Nonnull
    public BiomeGenerationConfig withFeatures(boolean generate) {
        return new BiomeGenerationConfig(biomeId, seed, generateStructures,
            generate, generateOres, seaLevel, terrainStyle);
    }

    /**
     * Returns the effective seed, generating a random one if seed is 0.
     * Uses System.nanoTime() for consistency with withRandomSeed().
     */
    public long getEffectiveSeed() {
        return seed == 0L ? System.nanoTime() : seed;
    }

    /**
     * Terrain generation styles.
     */
    public enum TerrainStyle implements StringRepresentable {
        /**
         * Natural terrain with height variations.
         */
        NATURAL("natural"),

        /**
         * Flat terrain at base level.
         */
        FLAT("flat"),

        /**
         * Amplified terrain with higher mountains.
         */
        AMPLIFIED("amplified"),

        /**
         * Floating islands style.
         */
        FLOATING("floating");

        public static final Codec<TerrainStyle> CODEC = StringRepresentable.fromEnum(TerrainStyle::values);

        private final String name;

        TerrainStyle(String name) {
            this.name = name;
        }

        @Override
        @Nonnull
        public String getSerializedName() {
            return Objects.requireNonNull(name);
        }
    }
}
