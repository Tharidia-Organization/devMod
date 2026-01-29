package com.devmod.area.data;

import java.util.Objects;

import javax.annotation.Nonnull;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.ResourceLocation;

/**
 * Represents a section in a Biome Tour - a multi-biome area with transitions.
 * Each section defines a biome, its length along the path, and the transition
 * style to the next section.
 */
public record BiomeTourSection(
    @Nonnull ResourceLocation biomeId,
    int length,
    @Nonnull TransitionStyle transition
) {
    /**
     * Minimum length for a biome section in blocks.
     */
    public static final int MIN_LENGTH = 8;

    /**
     * Maximum length for a biome section in blocks.
     */
    public static final int MAX_LENGTH = 256;

    /**
     * Default section length.
     */
    public static final int DEFAULT_LENGTH = 32;

    /**
     * Transition styles between biome sections.
     */
    public enum TransitionStyle {
        /**
         * Sharp boundary - instant biome change.
         */
        SHARP("sharp"),

        /**
         * Blended boundary - mixes blocks from both biomes.
         */
        BLEND("blend"),

        /**
         * Gradient boundary - smooth terrain height transition.
         */
        GRADIENT("gradient");

        private final String id;

        TransitionStyle(String id) {
            this.id = id;
        }

        public String getId() {
            return id;
        }

        public static TransitionStyle fromId(String id) {
            for (TransitionStyle style : values()) {
                if (style.id.equals(id)) {
                    return style;
                }
            }
            return SHARP;
        }

        public static final Codec<TransitionStyle> CODEC = Codec.STRING.xmap(
            TransitionStyle::fromId,
            TransitionStyle::getId
        );
    }

    public static final Codec<BiomeTourSection> CODEC = RecordCodecBuilder.create(instance ->
        instance.group(
            ResourceLocation.CODEC.fieldOf("biomeId").forGetter(BiomeTourSection::biomeId),
            Codec.intRange(MIN_LENGTH, MAX_LENGTH).fieldOf("length").forGetter(BiomeTourSection::length),
            TransitionStyle.CODEC.fieldOf("transition").forGetter(BiomeTourSection::transition)
        ).apply(instance, BiomeTourSection::new)
    );

    public BiomeTourSection {
        Objects.requireNonNull(biomeId, "biomeId cannot be null");
        Objects.requireNonNull(transition, "transition cannot be null");
        if (length < MIN_LENGTH) {
            length = MIN_LENGTH;
        } else if (length > MAX_LENGTH) {
            length = MAX_LENGTH;
        }
    }

    /**
     * Creates a default plains section.
     */
    public static BiomeTourSection plains() {
        return new BiomeTourSection(
            ResourceLocation.withDefaultNamespace("plains"),
            DEFAULT_LENGTH,
            TransitionStyle.BLEND
        );
    }

    /**
     * Creates a default forest section.
     */
    public static BiomeTourSection forest() {
        return new BiomeTourSection(
            ResourceLocation.withDefaultNamespace("forest"),
            DEFAULT_LENGTH,
            TransitionStyle.BLEND
        );
    }

    /**
     * Creates a default desert section.
     */
    public static BiomeTourSection desert() {
        return new BiomeTourSection(
            ResourceLocation.withDefaultNamespace("desert"),
            DEFAULT_LENGTH,
            TransitionStyle.GRADIENT
        );
    }

    /**
     * Creates a copy with a different biome.
     */
    public BiomeTourSection withBiome(@Nonnull ResourceLocation biomeId) {
        return new BiomeTourSection(Objects.requireNonNull(biomeId), length, transition);
    }

    /**
     * Creates a copy with a different length.
     */
    public BiomeTourSection withLength(int length) {
        return new BiomeTourSection(biomeId, length, transition);
    }

    /**
     * Creates a copy with a different transition style.
     */
    public BiomeTourSection withTransition(@Nonnull TransitionStyle transition) {
        return new BiomeTourSection(biomeId, length, Objects.requireNonNull(transition));
    }

    /**
     * Gets the display name of the biome (last part of the resource location).
     */
    public String getBiomeDisplayName() {
        String path = biomeId.getPath();
        // Convert snake_case to Title Case
        StringBuilder result = new StringBuilder();
        boolean capitalizeNext = true;
        for (int i = 0; i < path.length(); i++) {
            char c = path.charAt(i);
            if (c == '_') {
                result.append(' ');
                capitalizeNext = true;
            } else if (capitalizeNext) {
                result.append(Character.toUpperCase(c));
                capitalizeNext = false;
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }
}
