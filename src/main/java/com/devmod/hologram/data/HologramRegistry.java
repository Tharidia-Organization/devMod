package com.devmod.hologram.data;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nonnull;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Display;
import net.minecraft.world.level.saveddata.SavedData;

import com.devmod.DevMod;

/**
 * Central registry for all holograms in the world.
 * Handles persistence, CRUD operations, and entity caching.
 *
 * <p>Thread-safe for concurrent access from multiple dimensions.
 */
public class HologramRegistry extends SavedData {
    private static final String DATA_NAME = "devmod_holograms";
    private static final int DATA_VERSION = 1;

    private static final String TAG_VERSION = "version";
    private static final String TAG_HOLOGRAMS = "holograms";

    /**
     * Maximum holograms per dimension (configurable via Config).
     */
    public static final int DEFAULT_MAX_PER_DIMENSION = 64;

    // Primary storage: UUID -> HologramDefinition
    private final Map<UUID, HologramDefinition> holograms = new ConcurrentHashMap<>();

    // Index: BlockPos -> UUID for quick position lookups
    private final Map<BlockPos, UUID> positionIndex = new ConcurrentHashMap<>();

    // Index: Dimension -> Set<UUID> for dimension queries
    private final Map<ResourceLocation, Set<UUID>> dimensionIndex = new ConcurrentHashMap<>();

    // Runtime cache: UUID -> TextDisplay entity (not persisted)
    private final transient Map<UUID, WeakReference<Display.TextDisplay>> entityCache = new ConcurrentHashMap<>();

    public HologramRegistry() {
        super();
    }

    /**
     * Gets the hologram registry for the given server.
     * Data is stored in the overworld.
     */
    @Nonnull
    public static HologramRegistry get(@Nonnull MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        return server.overworld().getDataStorage()
            .computeIfAbsent(
                new Factory<>(HologramRegistry::new, HologramRegistry::load),
                DATA_NAME
            );
    }

    // ========================================================================
    // Hologram CRUD Operations
    // ========================================================================

    /**
     * Creates and registers a new hologram.
     *
     * @param def The hologram definition
     * @return The hologram's UUID if created, or empty if the dimension limit was reached
     */
    @Nonnull
    public Optional<UUID> createHologram(@Nonnull HologramDefinition def) {
        Objects.requireNonNull(def, "def");

        // Check dimension limit
        ResourceLocation dim = def.position().dimension();
        Set<UUID> dimHolograms = dimensionIndex.getOrDefault(dim, Set.of());
        if (dimHolograms.size() >= DEFAULT_MAX_PER_DIMENSION) {
            DevMod.LOGGER.warn("Cannot create hologram: dimension {} has reached limit of {}",
                dim, DEFAULT_MAX_PER_DIMENSION);
            return Optional.empty();
        }

        holograms.put(def.id(), def);
        updateIndexes(def);
        setDirty();

        DevMod.LOGGER.debug("[Hologram] Created: {} at {} ({})", def.label(), def.position().toShortString(), def.id());
        return Optional.of(def.id());
    }

    /**
     * Updates an existing hologram definition.
     * Uses optimistic locking via revision check.
     *
     * @param id               The hologram's UUID
     * @param def              The new definition
     * @param expectedRevision The expected current revision (for optimistic locking)
     * @return true if updated, false if not found or revision mismatch
     */
    public boolean updateHologram(@Nonnull UUID id, @Nonnull HologramDefinition def, int expectedRevision) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(def, "def");

        HologramDefinition existing = holograms.get(id);
        if (existing == null) {
            DevMod.LOGGER.warn("[Hologram] Cannot update {}: not found", id);
            return false;
        }

        if (existing.revision() != expectedRevision) {
            DevMod.LOGGER.warn("[Hologram] Cannot update {}: revision mismatch (expected {}, got {})",
                id, expectedRevision, existing.revision());
            return false;
        }

        // Remove old position from index
        removeFromIndexes(existing);

        // Store updated definition
        holograms.put(id, def);
        updateIndexes(def);
        setDirty();

        DevMod.LOGGER.debug("[Hologram] Updated: {} (rev {})", id, def.revision());
        return true;
    }

    /**
     * Deletes a hologram from the registry.
     *
     * @param id The hologram's UUID
     * @return true if deleted, false if not found
     */
    public boolean deleteHologram(@Nonnull UUID id) {
        Objects.requireNonNull(id, "id");

        HologramDefinition removed = holograms.remove(id);
        if (removed != null) {
            removeFromIndexes(removed);
            entityCache.remove(id);
            setDirty();
            DevMod.LOGGER.debug("[Hologram] Deleted: {} ({})", removed.label(), id);
            return true;
        }
        return false;
    }

    /**
     * Gets a hologram definition by ID.
     */
    @Nonnull
    public Optional<HologramDefinition> getHologram(@Nonnull UUID id) {
        Objects.requireNonNull(id, "id");
        return Optional.ofNullable(holograms.get(id));
    }

    /**
     * Gets all registered holograms.
     */
    @Nonnull
    public Collection<HologramDefinition> getAllHolograms() {
        return List.copyOf(holograms.values());
    }

    /**
     * Gets a hologram at a specific block position.
     */
    @Nonnull
    public Optional<HologramDefinition> getHologramAt(@Nonnull BlockPos pos) {
        Objects.requireNonNull(pos, "pos");
        UUID id = positionIndex.get(pos);
        return id != null ? getHologram(id) : Optional.empty();
    }

    /**
     * Gets all holograms in a specific dimension.
     */
    @Nonnull
    public List<HologramDefinition> getHologramsInDimension(@Nonnull ResourceLocation dimension) {
        Objects.requireNonNull(dimension, "dimension");
        Set<UUID> ids = dimensionIndex.getOrDefault(dimension, Set.of());
        List<HologramDefinition> result = new ArrayList<>(ids.size());
        for (UUID id : ids) {
            HologramDefinition def = holograms.get(id);
            if (def != null) {
                result.add(def);
            }
        }
        return result;
    }

    /**
     * Gets all holograms created by a specific player.
     */
    @Nonnull
    public List<HologramDefinition> getHologramsByCreator(@Nonnull UUID creatorUUID) {
        Objects.requireNonNull(creatorUUID, "creatorUUID");
        List<HologramDefinition> result = new ArrayList<>();
        for (HologramDefinition def : holograms.values()) {
            if (creatorUUID.equals(def.creatorUUID())) {
                result.add(def);
            }
        }
        return result;
    }

    /**
     * Returns the total number of registered holograms.
     */
    public int getCount() {
        return holograms.size();
    }

    /**
     * Returns the number of holograms in a dimension.
     */
    public int getCountInDimension(@Nonnull ResourceLocation dimension) {
        Objects.requireNonNull(dimension, "dimension");
        return dimensionIndex.getOrDefault(dimension, Set.of()).size();
    }

    // ========================================================================
    // Entity Cache (Runtime Only)
    // ========================================================================

    /**
     * Caches a TextDisplay entity reference for a hologram.
     */
    public void cacheEntity(@Nonnull UUID hologramId, @Nonnull Display.TextDisplay entity) {
        Objects.requireNonNull(hologramId, "hologramId");
        Objects.requireNonNull(entity, "entity");
        entityCache.put(hologramId, new WeakReference<>(entity));
    }

    /**
     * Gets the cached TextDisplay entity for a hologram.
     */
    @Nonnull
    public Optional<Display.TextDisplay> getCachedEntity(@Nonnull UUID hologramId) {
        Objects.requireNonNull(hologramId, "hologramId");
        WeakReference<Display.TextDisplay> ref = entityCache.get(hologramId);
        if (ref != null) {
            Display.TextDisplay entity = ref.get();
            if (entity != null && entity.isAlive()) {
                return Optional.of(entity);
            }
            entityCache.remove(hologramId);
        }
        return Optional.empty();
    }

    /**
     * Removes a cached entity reference.
     */
    public void removeCachedEntity(@Nonnull UUID hologramId) {
        Objects.requireNonNull(hologramId, "hologramId");
        entityCache.remove(hologramId);
    }

    /**
     * Clears all cached entity references.
     */
    public void clearEntityCache() {
        entityCache.clear();
    }

    // ========================================================================
    // Index Management
    // ========================================================================

    private void updateIndexes(@Nonnull HologramDefinition def) {
        // Position index
        positionIndex.put(def.position().blockPos(), def.id());

        // Dimension index
        dimensionIndex
            .computeIfAbsent(def.position().dimension(), k -> ConcurrentHashMap.newKeySet())
            .add(def.id());
    }

    private void removeFromIndexes(@Nonnull HologramDefinition def) {
        // Position index
        positionIndex.remove(def.position().blockPos());

        // Dimension index
        Set<UUID> dimSet = dimensionIndex.get(def.position().dimension());
        if (dimSet != null) {
            dimSet.remove(def.id());
            if (dimSet.isEmpty()) {
                dimensionIndex.remove(def.position().dimension());
            }
        }
    }

    // ========================================================================
    // Persistence
    // ========================================================================

    @Override
    @Nonnull
    public CompoundTag save(@Nonnull CompoundTag tag, @Nonnull HolderLookup.Provider registries) {
        tag.putInt(TAG_VERSION, DATA_VERSION);

        // Save holograms
        ListTag hologramList = new ListTag();
        for (HologramDefinition def : holograms.values()) {
            HologramDefinition.CODEC.encodeStart(NbtOps.INSTANCE, def)
                .result()
                .ifPresent(hologramList::add);
        }
        tag.put(TAG_HOLOGRAMS, hologramList);

        return tag;
    }

    @Nonnull
    public static HologramRegistry load(@Nonnull CompoundTag tag, @Nonnull HolderLookup.Provider registries) {
        HologramRegistry registry = new HologramRegistry();

        int version = tag.getInt(TAG_VERSION);
        if (version < DATA_VERSION) {
            DevMod.LOGGER.info("[Hologram] Migrating HologramRegistry from version {} to {}", version, DATA_VERSION);
            // Add migration logic here when needed
        }

        // Load holograms
        if (tag.contains(TAG_HOLOGRAMS, Tag.TAG_LIST)) {
            ListTag hologramList = tag.getList(TAG_HOLOGRAMS, Tag.TAG_COMPOUND);
            for (int i = 0; i < hologramList.size(); i++) {
                HologramDefinition.CODEC.parse(NbtOps.INSTANCE, hologramList.getCompound(i))
                    .result()
                    .ifPresent(def -> {
                        registry.holograms.put(def.id(), def);
                        registry.updateIndexes(def);
                    });
            }
        }

        DevMod.LOGGER.info("[Hologram] Loaded HologramRegistry: {} holograms", registry.holograms.size());
        return registry;
    }

    // ========================================================================
    // Migration Support
    // ========================================================================

    /**
     * Imports a hologram definition from legacy system without triggering normal creation limits.
     * Used by HologramMigration.
     */
    public void importLegacyHologram(@Nonnull HologramDefinition def) {
        Objects.requireNonNull(def, "def");
        holograms.put(def.id(), def);
        updateIndexes(def);
        setDirty();
        DevMod.LOGGER.debug("[Hologram] Imported legacy hologram: {} at {}", def.label(), def.position().toShortString());
    }
}
