package com.devmod.area.template;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

import com.devmod.area.data.AreaDefinition;
import com.devmod.area.data.AreaDimensions;
import com.devmod.area.data.AreaGenerationType;
import com.devmod.area.data.AreaOptions;
import com.devmod.area.data.AreaPalette;
import com.devmod.area.data.AreaShape;
import com.devmod.area.data.BiomeGenerationConfig;

/**
 * A reusable template for area configurations.
 * Contains all structural/visual settings without instance-specific data.
 *
 * @param id              Unique identifier for this template
 * @param name            Display name of the template
 * @param description     Optional description of what this template is for
 * @param author          Name of the player who created this template
 * @param createdAt       Timestamp when the template was created
 * @param generationType  Type of generation (CUSTOM or BIOME)
 * @param shape           Geometric shape of the area
 * @param dimensions      Size dimensions of the area
 * @param palette         Material palette for CUSTOM generation
 * @param biomeConfig     Biome config for BIOME generation (null if CUSTOM)
 * @param options         Construction options
 * @param customShapeNbt  NBT data for CUSTOM_NBT shape (null if not using custom shape)
 * @param category        Template category for organization
 * @param tags            User-defined tags for search/filtering
 */
public record AreaTemplate(
    UUID id,
    String name,
    String description,
    String author,
    long createdAt,
    AreaGenerationType generationType,
    AreaShape shape,
    AreaDimensions dimensions,
    AreaPalette palette,
    @Nullable BiomeGenerationConfig biomeConfig,
    AreaOptions options,
    @Nullable CompoundTag customShapeNbt,
    TemplateCategory category,
    Set<String> tags
) {
    /** Maximum template name length */
    public static final int MAX_NAME_LENGTH = 64;
    /** Maximum description length */
    public static final int MAX_DESCRIPTION_LENGTH = 256;
    /** Maximum number of tags per template */
    public static final int MAX_TAGS = 10;
    /** Maximum length of each tag */
    public static final int MAX_TAG_LENGTH = 32;
    /** L-04 fix: Pattern for valid tag characters (alphanumeric, dash, underscore only) */
    private static final Pattern VALID_TAG_PATTERN = Pattern.compile("^[a-z0-9_-]+$");

    /**
     * Compact constructor to validate and normalize tags.
     */
    public AreaTemplate {
        // Normalize tags: limit count, truncate length, sanitize, make immutable
        if (tags == null) {
            tags = Set.of();
        } else {
            tags = Set.copyOf(tags.stream()
                .filter(t -> t != null && !t.isBlank())
                .map(String::toLowerCase)
                .map(t -> t.length() > MAX_TAG_LENGTH ? t.substring(0, MAX_TAG_LENGTH) : t)
                // L-04 fix: Filter out tags with injection characters (only allow alphanumeric, dash, underscore)
                .filter(t -> VALID_TAG_PATTERN.matcher(t).matches())
                .limit(MAX_TAGS)
                .collect(Collectors.toSet()));
        }
        // Default category
        if (category == null) {
            category = TemplateCategory.OTHER;
        }
    }

    public static final Codec<AreaTemplate> CODEC = RecordCodecBuilder.create(instance ->
        instance.group(
            UUIDUtil.CODEC.fieldOf("id").forGetter(AreaTemplate::id),
            Codec.STRING.fieldOf("name").forGetter(AreaTemplate::name),
            Codec.STRING.optionalFieldOf("description", "").forGetter(AreaTemplate::description),
            Codec.STRING.optionalFieldOf("author", "Unknown").forGetter(AreaTemplate::author),
            Codec.LONG.optionalFieldOf("createdAt", 0L).forGetter(AreaTemplate::createdAt),
            AreaGenerationType.CODEC.fieldOf("generationType").forGetter(AreaTemplate::generationType),
            AreaShape.CODEC.fieldOf("shape").forGetter(AreaTemplate::shape),
            AreaDimensions.CODEC.fieldOf("dimensions").forGetter(AreaTemplate::dimensions),
            AreaPalette.CODEC.fieldOf("palette").forGetter(AreaTemplate::palette),
            BiomeGenerationConfig.CODEC.optionalFieldOf("biomeConfig")
                .forGetter(t -> Optional.ofNullable(t.biomeConfig())),
            AreaOptions.CODEC.fieldOf("options").forGetter(AreaTemplate::options),
            CompoundTag.CODEC.optionalFieldOf("customShapeNbt")
                .forGetter(t -> Optional.ofNullable(t.customShapeNbt())),
            TemplateCategory.CODEC.optionalFieldOf("category", TemplateCategory.OTHER)
                .forGetter(AreaTemplate::category),
            Codec.STRING.listOf().xmap(HashSet::new, ArrayList::new)
                .optionalFieldOf("tags", new HashSet<>())
                .forGetter(t -> new HashSet<>(t.tags()))
        ).apply(instance, (id, name, desc, auth, time, genType, shape, dims, pal, biomeOpt, opts, customOpt, cat, tagSet) ->
            new AreaTemplate(id, name, desc, auth, time, genType, shape, dims, pal,
                biomeOpt.orElse(null), opts, customOpt.orElse(null), cat, tagSet))
    );

    /**
     * Creates a template from an existing area definition with default category.
     *
     * @param def         The area definition to create template from
     * @param name        Name for the template
     * @param description Description for the template
     * @param author      Author name (usually player name)
     * @return A new AreaTemplate containing the area's configuration
     */
    @Nonnull
    public static AreaTemplate fromDefinition(
        @Nonnull AreaDefinition def,
        @Nonnull String name,
        @Nonnull String description,
        @Nonnull String author
    ) {
        return fromDefinition(def, name, description, author, TemplateCategory.OTHER, Objects.requireNonNull(Set.of()));
    }

    /**
     * Creates a template from an existing area definition.
     *
     * @param def         The area definition to create template from
     * @param name        Name for the template
     * @param description Description for the template
     * @param author      Author name (usually player name)
     * @param category    Template category
     * @param tags        Template tags
     * @return A new AreaTemplate containing the area's configuration
     */
    @Nonnull
    public static AreaTemplate fromDefinition(
        @Nonnull AreaDefinition def,
        @Nonnull String name,
        @Nonnull String description,
        @Nonnull String author,
        @Nonnull TemplateCategory category,
        @Nonnull Set<String> tags
    ) {
        Objects.requireNonNull(def);
        Objects.requireNonNull(name);
        Objects.requireNonNull(description);
        Objects.requireNonNull(author);
        Objects.requireNonNull(category);
        Objects.requireNonNull(tags);

        return new AreaTemplate(
            UUID.randomUUID(),
            truncate(name, MAX_NAME_LENGTH),
            truncate(description, MAX_DESCRIPTION_LENGTH),
            author,
            System.currentTimeMillis(),
            def.generationType(),
            def.shape(),
            def.dimensions(),
            def.palette(),
            def.biomeConfig(),
            def.options(),
            def.customShapeNbt(),
            category,
            tags
        );
    }

    /**
     * Applies this template to create a new AreaDefinition.
     *
     * @param newId       The UUID for the new area
     * @param newName     Display name for the new area
     * @param center      Center position for the new area
     * @param dimensionId Dimension for the new area
     * @param creatorUUID UUID of the player creating the area
     * @return A new AreaDefinition with template settings applied
     */
    @Nonnull
    public AreaDefinition toDefinition(
        @Nonnull UUID newId,
        @Nonnull String newName,
        @Nonnull BlockPos center,
        @Nonnull ResourceLocation dimensionId,
        @Nullable UUID creatorUUID
    ) {
        Objects.requireNonNull(newId);
        Objects.requireNonNull(newName);
        Objects.requireNonNull(center);
        Objects.requireNonNull(dimensionId);

        long now = System.currentTimeMillis();
        return new AreaDefinition(
            newId,
            newName,
            generationType,
            shape,
            dimensions,
            center,
            dimensionId,
            palette,
            biomeConfig,
            options,
            creatorUUID,
            now,
            now,
            0, // Initial revision
            false, // Not main hub by default
            null, // No linked zone
            customShapeNbt
        );
    }

    /**
     * Creates a copy of this template with a new name.
     */
    @Nonnull
    public AreaTemplate withName(@Nonnull String newName) {
        return new AreaTemplate(
            id, Objects.requireNonNull(newName), description, author, createdAt,
            generationType, shape, dimensions, palette, biomeConfig, options, customShapeNbt,
            category, tags
        );
    }

    /**
     * Creates a copy of this template with a new description.
     */
    @Nonnull
    public AreaTemplate withDescription(@Nonnull String newDescription) {
        return new AreaTemplate(
            id, name, Objects.requireNonNull(newDescription), author, createdAt,
            generationType, shape, dimensions, palette, biomeConfig, options, customShapeNbt,
            category, tags
        );
    }

    /**
     * Creates a copy of this template with a new category.
     */
    @Nonnull
    public AreaTemplate withCategory(@Nonnull TemplateCategory newCategory) {
        return new AreaTemplate(
            id, name, description, author, createdAt,
            generationType, shape, dimensions, palette, biomeConfig, options, customShapeNbt,
            Objects.requireNonNull(newCategory), tags
        );
    }

    /**
     * Creates a copy of this template with new tags.
     */
    @Nonnull
    public AreaTemplate withTags(@Nonnull Set<String> newTags) {
        return new AreaTemplate(
            id, name, description, author, createdAt,
            generationType, shape, dimensions, palette, biomeConfig, options, customShapeNbt,
            category, Objects.requireNonNull(newTags)
        );
    }

    /**
     * Creates a copy of this template with an additional tag.
     */
    @Nonnull
    public AreaTemplate addTag(@Nonnull String tag) {
        Objects.requireNonNull(tag);
        if (tags.size() >= MAX_TAGS) {
            return this; // Already at max
        }
        Set<String> newTags = new HashSet<>(tags);
        newTags.add(tag.toLowerCase(Locale.ROOT));
        return withTags(newTags);
    }

    /**
     * Creates a copy of this template without the specified tag.
     */
    @Nonnull
    public AreaTemplate removeTag(@Nonnull String tag) {
        Objects.requireNonNull(tag);
        Set<String> newTags = new HashSet<>(tags);
        newTags.remove(tag.toLowerCase(Locale.ROOT));
        return withTags(newTags);
    }

    /**
     * Checks if this template has the specified tag.
     */
    public boolean hasTag(@Nonnull String tag) {
        return tags.contains(tag.toLowerCase(Locale.ROOT));
    }

    private static String truncate(String str, int maxLength) {
        return str.length() <= maxLength ? str : str.substring(0, maxLength);
    }
}
