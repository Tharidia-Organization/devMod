package com.devmod.area.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import javax.annotation.Nonnull;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Configuration for a Biome Tour - a multi-biome area with sequential sections.
 * The tour creates a path through different biomes with configurable transitions.
 */
public record BiomeTourConfig(
    @Nonnull List<BiomeTourSection> sections,
    int corridorWidth,
    boolean looping
) {
    /**
     * Minimum corridor width for the tour path.
     */
    public static final int MIN_CORRIDOR_WIDTH = 8;

    /**
     * Maximum corridor width for the tour path.
     */
    public static final int MAX_CORRIDOR_WIDTH = 64;

    /**
     * Default corridor width.
     */
    public static final int DEFAULT_CORRIDOR_WIDTH = 16;

    /**
     * Maximum number of sections in a tour.
     */
    public static final int MAX_SECTIONS = 16;

    public static final Codec<BiomeTourConfig> CODEC = RecordCodecBuilder.create(instance ->
        instance.group(
            BiomeTourSection.CODEC.listOf().fieldOf("sections").forGetter(BiomeTourConfig::sections),
            Codec.intRange(MIN_CORRIDOR_WIDTH, MAX_CORRIDOR_WIDTH).fieldOf("corridorWidth").forGetter(BiomeTourConfig::corridorWidth),
            Codec.BOOL.fieldOf("looping").forGetter(BiomeTourConfig::looping)
        ).apply(instance, BiomeTourConfig::new)
    );

    public BiomeTourConfig {
        Objects.requireNonNull(sections, "sections cannot be null");
        sections = List.copyOf(sections); // Defensive copy
        if (corridorWidth < MIN_CORRIDOR_WIDTH) {
            corridorWidth = MIN_CORRIDOR_WIDTH;
        } else if (corridorWidth > MAX_CORRIDOR_WIDTH) {
            corridorWidth = MAX_CORRIDOR_WIDTH;
        }
    }

    /**
     * Creates a default tour with plains and forest sections.
     */
    public static BiomeTourConfig defaultTour() {
        return new BiomeTourConfig(
            List.of(BiomeTourSection.plains(), BiomeTourSection.forest()),
            DEFAULT_CORRIDOR_WIDTH,
            false
        );
    }

    /**
     * Creates an empty tour configuration.
     */
    public static BiomeTourConfig empty() {
        return new BiomeTourConfig(Collections.emptyList(), DEFAULT_CORRIDOR_WIDTH, false);
    }

    /**
     * Calculates the total length of the tour in blocks.
     */
    public int getTotalLength() {
        return sections.stream().mapToInt(BiomeTourSection::length).sum();
    }

    /**
     * Gets the number of sections.
     */
    public int getSectionCount() {
        return sections.size();
    }

    /**
     * Checks if the tour is valid (has at least one section).
     */
    public boolean isValid() {
        return !sections.isEmpty();
    }

    /**
     * Gets a section by index.
     */
    @Nonnull
    public BiomeTourSection getSection(int index) {
        if (index < 0 || index >= sections.size()) {
            throw new IndexOutOfBoundsException("Section index " + index + " out of bounds for " + sections.size() + " sections");
        }
        return sections.get(index);
    }

    /**
     * Creates a copy with a section added.
     */
    public BiomeTourConfig withSectionAdded(@Nonnull BiomeTourSection section) {
        if (sections.size() >= MAX_SECTIONS) {
            return this; // Don't add if at max
        }
        List<BiomeTourSection> newSections = new ArrayList<>(sections);
        newSections.add(Objects.requireNonNull(section));
        return new BiomeTourConfig(newSections, corridorWidth, looping);
    }

    /**
     * Creates a copy with a section removed.
     */
    public BiomeTourConfig withSectionRemoved(int index) {
        if (index < 0 || index >= sections.size()) {
            return this;
        }
        List<BiomeTourSection> newSections = new ArrayList<>(sections);
        newSections.remove(index);
        return new BiomeTourConfig(newSections, corridorWidth, looping);
    }

    /**
     * Creates a copy with a section updated.
     */
    public BiomeTourConfig withSectionUpdated(int index, @Nonnull BiomeTourSection section) {
        if (index < 0 || index >= sections.size()) {
            return this;
        }
        List<BiomeTourSection> newSections = new ArrayList<>(sections);
        newSections.set(index, Objects.requireNonNull(section));
        return new BiomeTourConfig(newSections, corridorWidth, looping);
    }

    /**
     * Creates a copy with a different corridor width.
     */
    public BiomeTourConfig withCorridorWidth(int width) {
        return new BiomeTourConfig(sections, width, looping);
    }

    /**
     * Creates a copy with looping changed.
     */
    public BiomeTourConfig withLooping(boolean looping) {
        return new BiomeTourConfig(sections, corridorWidth, looping);
    }

    /**
     * Moves a section up in the list.
     */
    public BiomeTourConfig withSectionMovedUp(int index) {
        if (index <= 0 || index >= sections.size()) {
            return this;
        }
        List<BiomeTourSection> newSections = new ArrayList<>(sections);
        Collections.swap(newSections, index, index - 1);
        return new BiomeTourConfig(newSections, corridorWidth, looping);
    }

    /**
     * Moves a section down in the list.
     */
    public BiomeTourConfig withSectionMovedDown(int index) {
        if (index < 0 || index >= sections.size() - 1) {
            return this;
        }
        List<BiomeTourSection> newSections = new ArrayList<>(sections);
        Collections.swap(newSections, index, index + 1);
        return new BiomeTourConfig(newSections, corridorWidth, looping);
    }
}
