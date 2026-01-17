package com.devmod.npc.data;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nonnull;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

import com.devmod.DevMod;

/**
 * Central registry for all NPCs in the world.
 * Handles persistence, CRUD operations, and visit tracking.
 *
 * <p>Thread-safe for concurrent access from multiple dimensions.
 */
public class NpcRegistry extends SavedData {
    private static final String DATA_NAME = "devmod_npc_registry";
    private static final int DATA_VERSION = 1;

    private static final String TAG_VERSION = "version";
    private static final String TAG_NPCS = "npcs";
    private static final String TAG_VISITS = "visits";
    private static final String TAG_MEMORIES = "memories";

    private final Map<UUID, NpcConfiguration> npcs = new ConcurrentHashMap<>();
    private final Map<UUID, Map<UUID, NpcVisitData>> visits = new ConcurrentHashMap<>();
    private final Map<UUID, Map<UUID, DialogMemory>> memories = new ConcurrentHashMap<>();

    public NpcRegistry() {
        super();
    }

    /**
     * Gets the NPC registry for the given server.
     * Data is stored in the overworld.
     */
    @Nonnull
    public static NpcRegistry get(@Nonnull MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        return Objects.requireNonNull(server.overworld().getDataStorage()
            .computeIfAbsent(
                new Factory<>(NpcRegistry::new, NpcRegistry::load),
                DATA_NAME
            ));
    }

    // ========================================================================
    // NPC CRUD Operations
    // ========================================================================

    /**
     * Creates and registers a new NPC.
     *
     * @param config The NPC configuration
     * @return The NPC's UUID
     */
    @Nonnull
    public UUID createNpc(@Nonnull NpcConfiguration config) {
        Objects.requireNonNull(config, "config");
        npcs.put(config.id(), config);
        setDirty();
        DevMod.LOGGER.debug("Created NPC: {} ({})", config.displayName(), config.id());
        return config.id();
    }

    /**
     * Updates an existing NPC configuration.
     * Uses optimistic locking via revision check.
     *
     * @param id               The NPC's UUID
     * @param config           The new configuration
     * @param expectedRevision The expected current revision (for optimistic locking)
     * @return true if updated, false if NPC not found or revision mismatch
     */
    public boolean updateNpc(@Nonnull UUID id, @Nonnull NpcConfiguration config, int expectedRevision) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(config, "config");

        NpcConfiguration existing = npcs.get(id);
        if (existing == null) {
            DevMod.LOGGER.warn("Cannot update NPC {}: not found", id);
            return false;
        }

        if (existing.revision() != expectedRevision) {
            DevMod.LOGGER.warn("Cannot update NPC {}: revision mismatch (expected {}, got {})",
                id, expectedRevision, existing.revision());
            return false;
        }

        // Ensure the config has the correct ID and incremented revision
        NpcConfiguration updated = new NpcConfiguration(
            id,
            config.displayName(),
            config.skinPlayerUUID(),
            config.skinPlayerName(),
            config.behavior(),
            config.appearance(),
            config.dialogSetId(),
            config.ownerUUID(),
            config.spawnPosition(),
            config.dimension(),
            existing.createdAt(),
            System.currentTimeMillis(),
            existing.revision() + 1
        );

        npcs.put(id, updated);
        setDirty();
        DevMod.LOGGER.debug("Updated NPC: {} (rev {})", id, updated.revision());
        return true;
    }

    /**
     * Deletes an NPC from the registry.
     *
     * @param id The NPC's UUID
     * @return true if deleted, false if not found
     */
    public boolean deleteNpc(@Nonnull UUID id) {
        Objects.requireNonNull(id, "id");
        NpcConfiguration removed = npcs.remove(id);
        if (removed != null) {
            // Also remove visit data for this NPC
            for (Map<UUID, NpcVisitData> playerVisits : visits.values()) {
                playerVisits.remove(id);
            }
            setDirty();
            DevMod.LOGGER.debug("Deleted NPC: {} ({})", removed.displayName(), id);
            return true;
        }
        return false;
    }

    /**
     * Gets an NPC configuration by ID.
     */
    @Nonnull
    public Optional<NpcConfiguration> getNpc(@Nonnull UUID id) {
        Objects.requireNonNull(id, "id");
        return Objects.requireNonNull(Optional.ofNullable(npcs.get(id)));
    }

    /**
     * Gets all registered NPCs.
     */
    @Nonnull
    public Collection<NpcConfiguration> getAllNpcs() {
        return Objects.requireNonNull(List.copyOf(npcs.values()));
    }

    /**
     * Gets all NPCs in a specific dimension.
     */
    @Nonnull
    public List<NpcConfiguration> getNpcsByDimension(@Nonnull ResourceKey<Level> dimension) {
        Objects.requireNonNull(dimension, "dimension");
        List<NpcConfiguration> result = new ArrayList<>();
        for (NpcConfiguration npc : npcs.values()) {
            if (dimension.equals(npc.dimension())) {
                result.add(npc);
            }
        }
        return result;
    }

    /**
     * Gets all NPCs owned by a specific player.
     */
    @Nonnull
    public List<NpcConfiguration> getNpcsByOwner(@Nonnull UUID ownerUUID) {
        Objects.requireNonNull(ownerUUID, "ownerUUID");
        List<NpcConfiguration> result = new ArrayList<>();
        for (NpcConfiguration npc : npcs.values()) {
            if (npc.ownerUUID().equals(ownerUUID)) {
                result.add(npc);
            }
        }
        return result;
    }

    /**
     * Returns the total number of registered NPCs.
     */
    public int getNpcCount() {
        return npcs.size();
    }

    // ========================================================================
    // Visit Tracking (FirstVisit)
    // ========================================================================

    /**
     * Checks if a player has visited an NPC before.
     *
     * @param playerId The player's UUID
     * @param npcId    The NPC's UUID
     * @return true if the player has visited this NPC before
     */
    public boolean hasVisited(@Nonnull UUID playerId, @Nonnull UUID npcId) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(npcId, "npcId");
        Map<UUID, NpcVisitData> playerVisits = visits.get(playerId);
        return playerVisits != null && playerVisits.containsKey(npcId);
    }

    /**
     * Marks a player as having visited an NPC.
     * Updates the last visit time if already visited.
     *
     * @param playerId The player's UUID
     * @param npcId    The NPC's UUID
     * @return The visit data (new or updated)
     */
    @Nonnull
    public NpcVisitData markVisited(@Nonnull UUID playerId, @Nonnull UUID npcId) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(npcId, "npcId");

        Map<UUID, NpcVisitData> playerVisits = visits.computeIfAbsent(playerId, k -> new ConcurrentHashMap<>());
        NpcVisitData existing = playerVisits.get(npcId);

        NpcVisitData visitData;
        if (existing != null) {
            visitData = existing.touch();
        } else {
            visitData = NpcVisitData.createFirstVisit(playerId, npcId);
        }

        playerVisits.put(npcId, visitData);
        setDirty();
        return visitData;
    }

    /**
     * Gets the visit data for a player and NPC.
     */
    @Nonnull
    public Optional<NpcVisitData> getVisitData(@Nonnull UUID playerId, @Nonnull UUID npcId) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(npcId, "npcId");
        Map<UUID, NpcVisitData> playerVisits = visits.get(playerId);
        return Objects.requireNonNull(playerVisits != null ? Optional.ofNullable(playerVisits.get(npcId)) : Optional.empty());
    }

    /**
     * Gets the visit count for a player and NPC.
     *
     * @param playerId The player's UUID
     * @param npcId    The NPC's UUID
     * @return The number of times the player has visited the NPC (0 if never visited)
     */
    public int getVisitCount(@Nonnull UUID playerId, @Nonnull UUID npcId) {
        return getVisitData(playerId, npcId)
            .map(NpcVisitData::visitCount)
            .orElse(0);
    }

    /**
     * Clears all visit data for a player.
     * Useful for testing or player data reset.
     */
    public void clearPlayerVisits(@Nonnull UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        if (visits.remove(playerId) != null) {
            setDirty();
        }
    }

    // ========================================================================
    // Dialog Memory (Persistent Choice Tracking)
    // ========================================================================

    /**
     * Gets the dialog memory for a player-NPC pair.
     *
     * @param playerId The player's UUID
     * @param npcId    The NPC's UUID
     * @return The dialog memory, or empty if none exists
     */
    @Nonnull
    public Optional<DialogMemory> getMemory(@Nonnull UUID playerId, @Nonnull UUID npcId) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(npcId, "npcId");
        Map<UUID, DialogMemory> playerMemories = memories.get(playerId);
        return Objects.requireNonNull(playerMemories != null ? Optional.ofNullable(playerMemories.get(npcId)) : Optional.empty());
    }

    /**
     * Gets or creates dialog memory for a player-NPC pair.
     *
     * @param playerId The player's UUID
     * @param npcId    The NPC's UUID
     * @return The existing or new dialog memory
     */
    @Nonnull
    public DialogMemory getOrCreateMemory(@Nonnull UUID playerId, @Nonnull UUID npcId) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(npcId, "npcId");
        Map<UUID, DialogMemory> playerMemories = memories.computeIfAbsent(playerId, k -> new ConcurrentHashMap<>());
        return Objects.requireNonNull(playerMemories.computeIfAbsent(npcId, k -> DialogMemory.create(playerId, npcId)));
    }

    /**
     * Updates the dialog memory for a player-NPC pair.
     *
     * @param memory The updated memory
     */
    public void updateMemory(@Nonnull DialogMemory memory) {
        Objects.requireNonNull(memory, "memory");
        memories.computeIfAbsent(memory.playerId(), k -> new ConcurrentHashMap<>())
            .put(memory.npcId(), memory);
        setDirty();
    }

    /**
     * Records that a player selected an option.
     *
     * @param playerId The player's UUID
     * @param npcId    The NPC's UUID
     * @param optionId The selected option ID
     * @return The updated memory
     */
    @Nonnull
    public DialogMemory recordOptionSelected(@Nonnull UUID playerId, @Nonnull UUID npcId, @Nonnull String optionId) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(npcId, "npcId");
        Objects.requireNonNull(optionId, "optionId");

        DialogMemory current = getOrCreateMemory(playerId, npcId);
        DialogMemory updated = current.withSelectedOption(optionId);
        updateMemory(updated);
        return updated;
    }

    /**
     * Sets custom data in dialog memory.
     *
     * @param playerId The player's UUID
     * @param npcId    The NPC's UUID
     * @param key      The data key
     * @param value    The data value
     * @return The updated memory
     */
    @Nonnull
    public DialogMemory setMemoryData(@Nonnull UUID playerId, @Nonnull UUID npcId,
                                       @Nonnull String key, @Nonnull String value) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(npcId, "npcId");
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(value, "value");

        DialogMemory current = getOrCreateMemory(playerId, npcId);
        DialogMemory updated = current.withCustomData(key, value);
        updateMemory(updated);
        return updated;
    }

    /**
     * Checks if a player has selected a specific option with an NPC.
     *
     * @param playerId The player's UUID
     * @param npcId    The NPC's UUID
     * @param optionId The option ID to check
     * @return true if the option has been selected
     */
    public boolean hasSelectedOption(@Nonnull UUID playerId, @Nonnull UUID npcId, @Nonnull String optionId) {
        return getMemory(playerId, npcId)
            .map(m -> m.hasSelectedOption(optionId))
            .orElse(false);
    }

    /**
     * Clears dialog memory for a player-NPC pair.
     */
    public void clearMemory(@Nonnull UUID playerId, @Nonnull UUID npcId) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(npcId, "npcId");
        Map<UUID, DialogMemory> playerMemories = memories.get(playerId);
        if (playerMemories != null && playerMemories.remove(npcId) != null) {
            setDirty();
        }
    }

    /**
     * Clears all dialog memory for a player.
     */
    public void clearPlayerMemory(@Nonnull UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        if (memories.remove(playerId) != null) {
            setDirty();
        }
    }

    // ========================================================================
    // Persistence
    // ========================================================================

    @Override
    @Nonnull
    public CompoundTag save(@Nonnull CompoundTag tag, @Nonnull HolderLookup.Provider registries) {
        tag.putInt(TAG_VERSION, DATA_VERSION);

        // Save NPCs
        ListTag npcList = new ListTag();
        for (NpcConfiguration npc : npcs.values()) {
            NpcConfiguration.CODEC.encodeStart(NbtOps.INSTANCE, npc)
                .result()
                .ifPresent(npcList::add);
        }
        tag.put(TAG_NPCS, npcList);

        // Save visits
        ListTag visitList = new ListTag();
        for (Map.Entry<UUID, Map<UUID, NpcVisitData>> playerEntry : visits.entrySet()) {
            for (NpcVisitData visit : playerEntry.getValue().values()) {
                NpcVisitData.CODEC.encodeStart(NbtOps.INSTANCE, visit)
                    .result()
                    .ifPresent(visitList::add);
            }
        }
        tag.put(TAG_VISITS, visitList);

        // Save dialog memories
        ListTag memoryList = new ListTag();
        for (Map.Entry<UUID, Map<UUID, DialogMemory>> playerEntry : memories.entrySet()) {
            for (DialogMemory memory : playerEntry.getValue().values()) {
                DialogMemory.CODEC.encodeStart(NbtOps.INSTANCE, memory)
                    .result()
                    .ifPresent(memoryList::add);
            }
        }
        tag.put(TAG_MEMORIES, memoryList);

        return tag;
    }

    @Nonnull
    public static NpcRegistry load(@Nonnull CompoundTag tag, @Nonnull HolderLookup.Provider registries) {
        NpcRegistry registry = new NpcRegistry();

        int version = tag.getInt(TAG_VERSION);
        if (version < DATA_VERSION) {
            DevMod.LOGGER.info("Migrating NpcRegistry from version {} to {}", version, DATA_VERSION);
            // Add migration logic here when needed
        }

        // Load NPCs
        if (tag.contains(TAG_NPCS, Tag.TAG_LIST)) {
            ListTag npcList = tag.getList(TAG_NPCS, Tag.TAG_COMPOUND);
            for (int i = 0; i < npcList.size(); i++) {
                NpcConfiguration.CODEC.parse(NbtOps.INSTANCE, npcList.getCompound(i))
                    .result()
                    .ifPresent(npc -> registry.npcs.put(npc.id(), npc));
            }
        }

        // Load visits
        if (tag.contains(TAG_VISITS, Tag.TAG_LIST)) {
            ListTag visitList = tag.getList(TAG_VISITS, Tag.TAG_COMPOUND);
            for (int i = 0; i < visitList.size(); i++) {
                NpcVisitData.CODEC.parse(NbtOps.INSTANCE, visitList.getCompound(i))
                    .result()
                    .ifPresent(visit -> {
                        registry.visits
                            .computeIfAbsent(visit.playerId(), k -> new ConcurrentHashMap<>())
                            .put(visit.npcId(), visit);
                    });
            }
        }

        // Load dialog memories
        if (tag.contains(TAG_MEMORIES, Tag.TAG_LIST)) {
            ListTag memoryList = tag.getList(TAG_MEMORIES, Tag.TAG_COMPOUND);
            for (int i = 0; i < memoryList.size(); i++) {
                DialogMemory.CODEC.parse(NbtOps.INSTANCE, memoryList.getCompound(i))
                    .result()
                    .ifPresent(memory -> {
                        registry.memories
                            .computeIfAbsent(memory.playerId(), k -> new ConcurrentHashMap<>())
                            .put(memory.npcId(), memory);
                    });
            }
        }

        DevMod.LOGGER.info("Loaded NpcRegistry: {} NPCs, {} visit records, {} memory records",
            registry.npcs.size(),
            registry.visits.values().stream().mapToInt(Map::size).sum(),
            registry.memories.values().stream().mapToInt(Map::size).sum());

        return registry;
    }
}
