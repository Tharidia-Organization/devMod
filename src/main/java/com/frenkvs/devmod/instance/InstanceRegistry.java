package com.frenkvs.devmod.instance;

import com.frenkvs.devmod.util.ConfigPaths;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Central registry for all active instances.
 * Provides lookups by instance ID, player ID, and dimension key.
 * Persists to disk for crash recovery.
 */
public class InstanceRegistry {
    private static final Logger LOGGER = LoggerFactory.getLogger(InstanceRegistry.class);
    public static final InstanceRegistry INSTANCE = new InstanceRegistry();

    // Primary storage: instanceId -> InstanceData
    private final Map<UUID, InstanceData> instances = new ConcurrentHashMap<>();

    // Lookup indices
    private final Map<UUID, UUID> playerToInstance = new ConcurrentHashMap<>();
    private final Map<ResourceKey<Level>, UUID> dimensionToInstance = new ConcurrentHashMap<>();

    // Destruction queue
    private final Set<UUID> pendingDestruction = ConcurrentHashMap.newKeySet();

    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private boolean dirty = false;

    private InstanceRegistry() {}

    // === Instance Lifecycle ===

    /**
     * Create and register a new instance.
     */
    public InstanceData createInstance(String arenaId, UUID ownerId) {
        InstanceData instance = InstanceData.createSolo(ownerId);
        instance.setArena(null, 25, arenaId);  // Default arena settings
        register(instance);
        return instance;
    }

    /**
     * Create and register a new party instance.
     */
    public InstanceData createPartyInstance(String arenaId, UUID ownerId, int maxPlayers) {
        InstanceData instance = InstanceData.createParty(ownerId, maxPlayers);
        instance.setArena(null, 25, arenaId);
        register(instance);
        return instance;
    }

    /**
     * Register a new instance.
     */
    public void register(InstanceData instance) {
        instances.put(instance.getInstanceId(), instance);

        // Index by dimension if set
        if (instance.getDimensionKey() != null) {
            dimensionToInstance.put(instance.getDimensionKey(), instance.getInstanceId());
        }

        dirty = true;
        LOGGER.info("[InstanceRegistry] Registered instance {}", instance.getInstanceId());
    }

    /**
     * Remove an instance completely.
     */
    public void removeInstance(UUID instanceId) {
        unregister(instanceId);
    }

    /**
     * Unregister an instance (after destruction).
     */
    public void unregister(UUID instanceId) {
        InstanceData instance = instances.remove(instanceId);
        if (instance != null) {
            // Remove from indices
            if (instance.getDimensionKey() != null) {
                dimensionToInstance.remove(instance.getDimensionKey());
            }

            // Remove all player mappings
            for (UUID playerId : instance.getCurrentPlayers()) {
                playerToInstance.remove(playerId);
            }

            pendingDestruction.remove(instanceId);
            dirty = true;
            LOGGER.info("[InstanceRegistry] Unregistered instance {}", instanceId);
        }
    }

    // === Player Mapping ===

    /**
     * Map a player to an instance.
     */
    public void mapPlayer(UUID playerId, UUID instanceId) {
        playerToInstance.put(playerId, instanceId);
        dirty = true;
    }

    /**
     * Unmap a player from their instance.
     */
    public void unmapPlayer(UUID playerId) {
        playerToInstance.remove(playerId);
        dirty = true;
    }

    /**
     * Update dimension key for an instance (after dimension is created).
     */
    public void setDimensionKey(UUID instanceId, ResourceKey<Level> dimensionKey) {
        InstanceData instance = instances.get(instanceId);
        if (instance != null) {
            // Remove old mapping if exists
            if (instance.getDimensionKey() != null) {
                dimensionToInstance.remove(instance.getDimensionKey());
            }

            instance.setDimensionKey(dimensionKey);
            dimensionToInstance.put(dimensionKey, instanceId);
            dirty = true;
        }
    }

    // === Lookups ===

    /**
     * Get instance by ID.
     */
    public Optional<InstanceData> getInstance(UUID instanceId) {
        return Optional.ofNullable(instances.get(instanceId));
    }

    /**
     * Get instance containing a player.
     */
    public Optional<InstanceData> getPlayerInstance(UUID playerId) {
        UUID instanceId = playerToInstance.get(playerId);
        if (instanceId != null) {
            return Optional.ofNullable(instances.get(instanceId));
        }
        return Optional.empty();
    }

    /**
     * Get instance by dimension key.
     */
    public Optional<InstanceData> getInstanceByDimension(ResourceKey<Level> dimensionKey) {
        UUID instanceId = dimensionToInstance.get(dimensionKey);
        if (instanceId != null) {
            return Optional.ofNullable(instances.get(instanceId));
        }
        return Optional.empty();
    }

    /**
     * Check if a player is in any instance.
     */
    public boolean isPlayerInInstance(UUID playerId) {
        return playerToInstance.containsKey(playerId);
    }

    /**
     * Get instance ID for a player (may be null).
     */
    @Nullable
    public UUID getPlayerInstanceId(UUID playerId) {
        return playerToInstance.get(playerId);
    }

    // === Queries ===

    /**
     * Get all active instances.
     */
    public Collection<InstanceData> getAllInstances() {
        return Collections.unmodifiableCollection(instances.values());
    }

    /**
     * Get all instances in a specific state.
     */
    public List<InstanceData> getInstancesByState(InstanceState state) {
        return instances.values().stream()
            .filter(i -> i.getState() == state)
            .collect(Collectors.toList());
    }

    /**
     * Get instances owned by a player.
     */
    public List<InstanceData> getInstancesOwnedBy(UUID ownerId) {
        return instances.values().stream()
            .filter(i -> i.getOwnerId().equals(ownerId))
            .collect(Collectors.toList());
    }

    /**
     * Get empty instances (candidates for destruction).
     */
    public List<InstanceData> getEmptyInstances() {
        return instances.values().stream()
            .filter(InstanceData::isEmpty)
            .filter(i -> i.getState() == InstanceState.ACTIVE || i.getState() == InstanceState.COMPLETING)
            .collect(Collectors.toList());
    }

    /**
     * Get instances ready for destruction.
     */
    public List<InstanceData> getInstancesReadyForDestruction() {
        return instances.values().stream()
            .filter(InstanceData::shouldDestroy)
            .collect(Collectors.toList());
    }

    /**
     * Count active instances.
     */
    public int getActiveInstanceCount() {
        return (int) instances.values().stream()
            .filter(i -> i.getState() != InstanceState.DESTROYED)
            .count();
    }

    /**
     * Count total players in instances.
     */
    public int getTotalPlayersInInstances() {
        return playerToInstance.size();
    }

    // === Destruction Queue ===

    /**
     * Schedule an instance for destruction.
     */
    public void scheduleDestruction(UUID instanceId) {
        InstanceData instance = instances.get(instanceId);
        if (instance != null) {
            instance.scheduleDestruction();
            pendingDestruction.add(instanceId);
            dirty = true;
        }
    }

    /**
     * Cancel scheduled destruction.
     */
    public void cancelDestruction(UUID instanceId) {
        InstanceData instance = instances.get(instanceId);
        if (instance != null) {
            instance.cancelDestruction();
            pendingDestruction.remove(instanceId);
            dirty = true;
        }
    }

    /**
     * Get instances pending destruction.
     */
    public Set<UUID> getPendingDestruction() {
        return Collections.unmodifiableSet(pendingDestruction);
    }

    /**
     * Process pending destructions - destroy instances that are ready.
     * Should be called periodically (e.g., from server tick or startup).
     */
    public void processPendingDestructions() {
        List<UUID> toDestroy = new ArrayList<>();

        for (UUID instanceId : pendingDestruction) {
            InstanceData instance = instances.get(instanceId);
            if (instance != null && instance.shouldDestroy()) {
                toDestroy.add(instanceId);
            }
        }

        for (UUID instanceId : toDestroy) {
            LOGGER.info("[InstanceRegistry] Processing destruction for instance {}", instanceId);
            // The actual dimension destruction is handled by DynamicDimensionManager
            // Here we just clean up our registry
            DynamicDimensionManager.INSTANCE.destroyDimensionAsync(instanceId)
                .thenAccept(success -> {
                    if (success) {
                        unregister(instanceId);
                    } else {
                        LOGGER.warn("[InstanceRegistry] Failed to destroy dimension for {}", instanceId);
                    }
                });
        }
    }

    // === Persistence ===

    /**
     * Save registry to disk.
     */
    public void save() {
        if (!dirty) return;

        try {
            Path registryFile = getRegistryFile();
            Files.createDirectories(registryFile.getParent());

            List<Map<String, Object>> instanceList = new ArrayList<>();
            for (InstanceData instance : instances.values()) {
                instanceList.add(instance.toMap());
            }

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("version", 1);
            data.put("instances", instanceList);

            // Player mappings
            Map<String, String> playerMappings = new HashMap<>();
            for (Map.Entry<UUID, UUID> entry : playerToInstance.entrySet()) {
                playerMappings.put(entry.getKey().toString(), entry.getValue().toString());
            }
            data.put("playerMappings", playerMappings);

            String json = gson.toJson(data);
            Files.writeString(registryFile, json);
            dirty = false;

            LOGGER.debug("[InstanceRegistry] Saved {} instances to disk", instances.size());
        } catch (IOException e) {
            LOGGER.error("[InstanceRegistry] Failed to save registry", e);
        }
    }

    /**
     * Load registry from disk (for crash recovery).
     */

    public void load() {
        Path registryFile = getRegistryFile();
        if (!Files.exists(registryFile)) {
            LOGGER.info("[InstanceRegistry] No registry file found, starting fresh");
            return;
        }

        try {
            String json = Files.readString(registryFile);
            Type mapType = new TypeToken<Map<String, Object>>() {}.getType();
            Map<String, Object> data = gson.fromJson(json, mapType);

            if (data == null) return;

            // Load instances (type-safe extraction)
            List<Map<String, Object>> instanceList = getMapList(data, "instances");
            for (Map<String, Object> instanceMap : instanceList) {
                try {
                    InstanceData instance = InstanceData.fromMap(instanceMap);
                    instances.put(instance.getInstanceId(), instance);

                    if (instance.getDimensionKey() != null) {
                        dimensionToInstance.put(instance.getDimensionKey(), instance.getInstanceId());
                    }

                    if (instance.isMarkedForDestruction()) {
                        pendingDestruction.add(instance.getInstanceId());
                    }
                } catch (Exception e) {
                    LOGGER.warn("[InstanceRegistry] Failed to load instance: {}", e.getMessage());
                }
            }

            // Load player mappings (type-safe extraction)
            Map<String, String> playerMappings = getStringMap(data, "playerMappings");
            for (Map.Entry<String, String> entry : playerMappings.entrySet()) {
                try {
                    UUID playerId = UUID.fromString(entry.getKey());
                    UUID instanceId = UUID.fromString(entry.getValue());
                    playerToInstance.put(playerId, instanceId);
                } catch (Exception e) {
                    LOGGER.warn("[InstanceRegistry] Invalid player mapping: {}", e.getMessage());
                }
            }

            LOGGER.info("[InstanceRegistry] Loaded {} instances from disk", instances.size());
        } catch (Exception e) {
            LOGGER.error("[InstanceRegistry] Failed to load registry", e);
        }
    }

    // =========================================================================
    // JSON PARSING HELPERS (type-safe)
    // =========================================================================

    /**
     * Safely extracts a List of Maps from a parent Map.
     */
    private static List<Map<String, Object>> getMapList(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof List<?> list) {
            List<Map<String, Object>> result = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?> m) {
                    Map<String, Object> typedMap = new HashMap<>();
                    for (Map.Entry<?, ?> entry : m.entrySet()) {
                        if (entry.getKey() instanceof String k) {
                            typedMap.put(k, entry.getValue());
                        }
                    }
                    result.add(typedMap);
                }
            }
            return result;
        }
        return List.of();
    }

    /**
     * Safely extracts a Map of String to String from a parent Map.
     */
    private static Map<String, String> getStringMap(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Map<?, ?> m) {
            Map<String, String> result = new HashMap<>();
            for (Map.Entry<?, ?> entry : m.entrySet()) {
                if (entry.getKey() instanceof String k && entry.getValue() instanceof String v) {
                    result.put(k, v);
                }
            }
            return result;
        }
        return Map.of();
    }

    /**
     * Clear all data (for testing or reset).
     */
    public void clear() {
        instances.clear();
        playerToInstance.clear();
        dimensionToInstance.clear();
        pendingDestruction.clear();
        dirty = true;
        LOGGER.info("[InstanceRegistry] Registry cleared");
    }

    /**
     * Mark registry as dirty (needs save).
     */
    public void markDirty() {
        this.dirty = true;
    }

    private Path getRegistryFile() {
        return ConfigPaths.getConfigDir().resolve("instances.json");
    }

    // === Debug ===

    public void logStatus() {
        LOGGER.info("[InstanceRegistry] Status: {} instances, {} players, {} pending destruction",
            instances.size(), playerToInstance.size(), pendingDestruction.size());

        for (InstanceData instance : instances.values()) {
            LOGGER.info("  - {}", instance);
        }
    }
}
