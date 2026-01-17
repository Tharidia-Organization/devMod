package com.devmod.area.template;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mojang.serialization.DataResult;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Registry for storing and managing area templates.
 * Persisted as SavedData in the overworld.
 */
public class AreaTemplateRegistry extends SavedData {
    private static final Logger LOGGER = LoggerFactory.getLogger(AreaTemplateRegistry.class);
    private static final String DATA_NAME = "devmod_area_templates";
    private static final int DATA_VERSION = 1;

    private static final String TAG_VERSION = "version";
    private static final String TAG_TEMPLATES = "templates";

    /** Maximum templates allowed in registry */
    public static final int MAX_TEMPLATES = 200;

    private final Map<UUID, AreaTemplate> templates = new ConcurrentHashMap<>();

    /**
     * Gets the AreaTemplateRegistry instance for the server.
     * Data is stored in the overworld.
     */
    @Nonnull
    public static AreaTemplateRegistry get(@Nonnull MinecraftServer server) {
        Objects.requireNonNull(server);
        return Objects.requireNonNull(
            server.overworld().getDataStorage()
                .computeIfAbsent(Objects.requireNonNull(factory()), DATA_NAME)
        );
    }

    private static Factory<AreaTemplateRegistry> factory() {
        return new Factory<>(
            AreaTemplateRegistry::new,
            AreaTemplateRegistry::load,
            net.minecraft.util.datafix.DataFixTypes.LEVEL
        );
    }

    // ========================================================================
    // CRUD Operations
    // ========================================================================

    /**
     * Saves a template to the registry.
     *
     * @param template The template to save
     * @return The template ID, or null if registry is full
     */
    @Nonnull
    public synchronized Optional<UUID> saveTemplate(@Nonnull AreaTemplate template) {
        Objects.requireNonNull(template);

        if (templates.size() >= MAX_TEMPLATES && !templates.containsKey(template.id())) {
            LOGGER.warn("[AreaTemplate] Registry full, cannot save template: {}", template.name());
            return Objects.requireNonNull(Optional.empty());
        }

        templates.put(template.id(), template);
        setDirty();
        LOGGER.debug("[AreaTemplate] Saved template: {} ({})", template.name(), template.id());
        return Objects.requireNonNull(Optional.of(template.id()));
    }

    /**
     * Gets a template by ID.
     */
    @Nonnull
    public Optional<AreaTemplate> getTemplate(@Nonnull UUID id) {
        return Objects.requireNonNull(Optional.ofNullable(templates.get(Objects.requireNonNull(id))));
    }

    /**
     * Deletes a template by ID.
     *
     * @param id The template ID
     * @return True if template was deleted, false if not found
     */
    public boolean deleteTemplate(@Nonnull UUID id) {
        Objects.requireNonNull(id);
        AreaTemplate removed = templates.remove(id);
        if (removed != null) {
            setDirty();
            LOGGER.debug("[AreaTemplate] Deleted template: {} ({})", removed.name(), id);
            return true;
        }
        return false;
    }

    /**
     * Gets all templates as an unmodifiable collection.
     */
    @Nonnull
    public Collection<AreaTemplate> getAllTemplates() {
        return Objects.requireNonNull(Collections.unmodifiableCollection(templates.values()));
    }

    /**
     * Gets the number of templates in registry.
     */
    public int getTemplateCount() {
        return templates.size();
    }

    /**
     * Checks if the registry has room for more templates.
     */
    public boolean hasRoom() {
        return templates.size() < MAX_TEMPLATES;
    }

    // ========================================================================
    // Search Operations
    // ========================================================================

    /**
     * Searches templates by name or description.
     *
     * @param query Search query (case-insensitive)
     * @return List of matching templates
     */
    @Nonnull
    public List<AreaTemplate> searchTemplates(@Nonnull String query) {
        Objects.requireNonNull(query);
        String lowerQuery = query.toLowerCase(Locale.ROOT);

        return Objects.requireNonNull(templates.values().stream()
            .filter(t -> t.name().toLowerCase(Locale.ROOT).contains(lowerQuery)
                      || t.description().toLowerCase(Locale.ROOT).contains(lowerQuery))
            .toList());
    }

    /**
     * Finds templates by author.
     *
     * @param author Author name (case-insensitive)
     * @return List of templates by this author
     */
    @Nonnull
    public List<AreaTemplate> findByAuthor(@Nonnull String author) {
        Objects.requireNonNull(author);
        String lowerAuthor = author.toLowerCase(Locale.ROOT);

        return Objects.requireNonNull(templates.values().stream()
            .filter(t -> t.author().toLowerCase(Locale.ROOT).equals(lowerAuthor))
            .toList());
    }

    /**
     * Gets templates sorted by creation date (newest first).
     */
    @Nonnull
    public List<AreaTemplate> getRecentTemplates(int limit) {
        return Objects.requireNonNull(templates.values().stream()
            .sorted((a, b) -> Long.compare(b.createdAt(), a.createdAt()))
            .limit(limit)
            .toList());
    }

    // ========================================================================
    // Category and Tag Search
    // ========================================================================

    /**
     * Finds all templates in a specific category.
     *
     * @param category The category to filter by
     * @return List of templates in that category
     */
    @Nonnull
    public List<AreaTemplate> findByCategory(@Nonnull TemplateCategory category) {
        Objects.requireNonNull(category);
        return Objects.requireNonNull(templates.values().stream()
            .filter(t -> t.category() == category)
            .toList());
    }

    /**
     * Finds all templates that have a specific tag.
     *
     * @param tag The tag to search for (case-insensitive)
     * @return List of templates with that tag
     */
    @Nonnull
    public List<AreaTemplate> findByTag(@Nonnull String tag) {
        Objects.requireNonNull(tag);
        String lowerTag = tag.toLowerCase(Locale.ROOT);
        return Objects.requireNonNull(templates.values().stream()
            .filter(t -> t.tags().contains(lowerTag))
            .toList());
    }

    /**
     * Finds all templates that have ALL of the specified tags.
     *
     * @param tags The tags to search for (case-insensitive)
     * @return List of templates with all specified tags
     */
    @Nonnull
    public List<AreaTemplate> findByAllTags(@Nonnull Set<String> tags) {
        Objects.requireNonNull(tags);
        Set<String> lowerTags = tags.stream()
            .map(t -> t.toLowerCase(Locale.ROOT))
            .collect(Collectors.toSet());
        return Objects.requireNonNull(templates.values().stream()
            .filter(t -> t.tags().containsAll(lowerTags))
            .toList());
    }

    /**
     * Finds all templates that have ANY of the specified tags.
     *
     * @param tags The tags to search for (case-insensitive)
     * @return List of templates with at least one of the specified tags
     */
    @Nonnull
    public List<AreaTemplate> findByAnyTag(@Nonnull Set<String> tags) {
        Objects.requireNonNull(tags);
        Set<String> lowerTags = tags.stream()
            .map(t -> t.toLowerCase(Locale.ROOT))
            .collect(Collectors.toSet());
        return Objects.requireNonNull(templates.values().stream()
            .filter(t -> t.tags().stream().anyMatch(lowerTags::contains))
            .toList());
    }

    /**
     * Gets all unique tags used across all templates.
     *
     * @return Set of all tags (lowercase)
     */
    @Nonnull
    public Set<String> getAllTags() {
        return Objects.requireNonNull(templates.values().stream()
            .flatMap(t -> t.tags().stream())
            .collect(Collectors.toSet()));
    }

    /**
     * Gets all categories that have at least one template.
     *
     * @return Set of categories in use
     */
    @Nonnull
    public Set<TemplateCategory> getCategoriesInUse() {
        return Objects.requireNonNull(templates.values().stream()
            .map(AreaTemplate::category)
            .collect(Collectors.toSet()));
    }

    /**
     * Gets the count of templates per category.
     *
     * @return Map of category to template count
     */
    @Nonnull
    public Map<TemplateCategory, Long> getTemplateCounts() {
        return Objects.requireNonNull(templates.values().stream()
            .collect(Collectors.groupingBy(AreaTemplate::category, Collectors.counting())));
    }

    // ========================================================================
    // Serialization
    // ========================================================================

    @Override
    @Nonnull
    public CompoundTag save(@Nonnull CompoundTag tag, @Nonnull HolderLookup.Provider provider) {
        tag.putInt(TAG_VERSION, DATA_VERSION);

        ListTag templatesList = new ListTag();
        for (AreaTemplate template : templates.values()) {
            DataResult<Tag> result = AreaTemplate.CODEC.encodeStart(NbtOps.INSTANCE, template);
            result.result().ifPresent(templatesList::add);
            result.error().ifPresent(error ->
                LOGGER.warn("[AreaTemplate] Failed to encode template {}: {}", template.name(), error.message()));
        }
        tag.put(TAG_TEMPLATES, templatesList);

        return tag;
    }

    // ========================================================================
    // Data Migration
    // ========================================================================

    /**
     * Migrates data from an older version to the current version.
     * Applies migrations incrementally (v1 -> v2 -> v3 -> ... -> current).
     *
     * @param tag         The original NBT data
     * @param fromVersion The version the data was saved with
     * @return The migrated tag (may be the same object if no changes needed)
     */
    @Nonnull
    private static CompoundTag migrateData(@Nonnull CompoundTag tag, int fromVersion) {
        LOGGER.info("[AreaTemplate] Migrating data from version {} to {}", fromVersion, DATA_VERSION);

        int currentVersion = fromVersion;

        // Migration: v0 -> v1 (initial version, no migration needed)
        if (currentVersion < 1) {
            currentVersion = 1;
        }

        // Future migrations would be added here:
        // if (currentVersion < 2) {
        //     tag = migrateV1ToV2(tag);
        //     currentVersion = 2;
        // }

        tag.putInt(TAG_VERSION, DATA_VERSION);

        LOGGER.info("[AreaTemplate] Migration complete, now at version {}", DATA_VERSION);
        return tag;
    }

    @Nonnull
    public static AreaTemplateRegistry load(@Nonnull CompoundTag tag, @Nonnull HolderLookup.Provider provider) {
        AreaTemplateRegistry registry = new AreaTemplateRegistry();

        int version = tag.getInt(TAG_VERSION);
        if (version > DATA_VERSION) {
            LOGGER.warn("[AreaTemplate] Loading data from newer version {} (current: {})", version, DATA_VERSION);
        }

        // Apply migrations if needed (version < current)
        if (version < DATA_VERSION) {
            tag = migrateData(tag, version);
        }

        ListTag templatesList = tag.getList(TAG_TEMPLATES, Tag.TAG_COMPOUND);
        for (int i = 0; i < templatesList.size(); i++) {
            final int index = i;
            CompoundTag templateTag = templatesList.getCompound(i);
            DataResult<AreaTemplate> result = AreaTemplate.CODEC.parse(NbtOps.INSTANCE, templateTag);
            result.result().ifPresent(template -> registry.templates.put(template.id(), template));
            result.error().ifPresent(error ->
                LOGGER.warn("[AreaTemplate] Failed to load template {}: {}", index, error.message()));
        }

        LOGGER.info("[AreaTemplate] Loaded {} templates", registry.templates.size());
        return registry;
    }
}
