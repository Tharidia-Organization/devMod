package com.frenkvs.devmod.instance;

import com.frenkvs.devmod.endurance.QuestType;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Data for a single instance dimension.
 * Tracks the instance lifecycle, players, and quest state.
 */
public class InstanceData {
    private static final Logger LOGGER = LoggerFactory.getLogger(InstanceData.class);

    // === Identifiers ===
    private final UUID instanceId;
    private ResourceKey<Level> dimensionKey;
    private final long createdAt;

    // === State ===
    private InstanceState state;

    // === Players ===
    private final Set<UUID> currentPlayers;
    private final int maxPlayers;
    private final UUID ownerId;  // Player who created the instance

    // === Arena ===
    private BlockPos arenaCenter;
    private int arenaRadius;
    private String arenaTemplate;

    // === Quest State ===
    private ResourceLocation questMobId;
    private int currentWave;
    private int totalWaves;
    private long questStartTime;
    private boolean endlessMode;

    // === Cleanup ===
    private long markedForDestructionAt;
    public static final long DESTROY_DELAY_MS = 5000; // 5 seconds

    /**
     * Create a new instance for a single player (solo quest).
     */
    public static InstanceData createSolo(UUID ownerId) {
        return new InstanceData(UUID.randomUUID(), ownerId, 1);
    }

    /**
     * Create a new instance for a party.
     * Note: maxPlayers is no longer hardcoded to 4 - it depends on QuestType.
     */
    public static InstanceData createParty(UUID ownerId, int maxPlayers) {
        return new InstanceData(UUID.randomUUID(), ownerId, maxPlayers);
    }

    /**
     * Create a new instance for a specific quest type.
     * Uses the quest type's max players setting.
     *
     * @param ownerId Owner/leader UUID
     * @param questType Quest type determining max players and arena size
     * @return New InstanceData configured for the quest type
     */
    public static InstanceData createForQuestType(UUID ownerId, QuestType questType) {
        return new InstanceData(UUID.randomUUID(), ownerId, questType.maxPlayers);
    }

    private InstanceData(UUID instanceId, UUID ownerId, int maxPlayers) {
        this.instanceId = instanceId;
        this.ownerId = ownerId;
        this.maxPlayers = maxPlayers;
        this.createdAt = System.currentTimeMillis();
        this.state = InstanceState.CREATING;
        this.currentPlayers = ConcurrentHashMap.newKeySet();
        this.markedForDestructionAt = 0;
    }

    /**
     * Constructor for deserialization.
     */
    private InstanceData(UUID instanceId, UUID ownerId, int maxPlayers, long createdAt) {
        this.instanceId = instanceId;
        this.ownerId = ownerId;
        this.maxPlayers = maxPlayers;
        this.createdAt = createdAt;
        this.state = InstanceState.CREATING;
        this.currentPlayers = ConcurrentHashMap.newKeySet();
        this.markedForDestructionAt = 0;
    }

    // === State Management ===

    public InstanceState getState() {
        return state;
    }

    /**
     * Set the instance state with validation.
     * Logs a warning if the transition is invalid but still allows it
     * to prevent deadlocks in error scenarios.
     *
     * @param newState The new state to transition to
     * @return true if the transition was valid, false if it was forced
     */
    public boolean setState(InstanceState newState) {
        InstanceState oldState = this.state;

        if (oldState == newState) {
            LOGGER.debug("[Instance] {} already in state {}", instanceId, newState);
            return true;
        }

        boolean valid = oldState.canTransitionTo(newState);
        if (!valid) {
            LOGGER.warn("[Instance] {} INVALID state transition: {} -> {} (valid: {})",
                instanceId, oldState, newState, oldState.getValidNextStates());
            // Still allow the transition to prevent deadlocks, but log the warning
        }

        this.state = newState;
        LOGGER.info("[Instance] {} state changed: {} -> {}{}", instanceId, oldState, newState,
            valid ? "" : " [FORCED]");

        return valid;
    }

    /**
     * Force a state change without validation (for recovery scenarios).
     * Use sparingly - prefer setState() which logs invalid transitions.
     */
    public void forceState(InstanceState newState) {
        InstanceState oldState = this.state;
        this.state = newState;
        LOGGER.info("[Instance] {} state FORCED: {} -> {}", instanceId, oldState, newState);
    }

    public boolean isActive() {
        return state == InstanceState.ACTIVE;
    }

    public boolean isDestroyed() {
        return state == InstanceState.DESTROYED;
    }

    public boolean canAcceptPlayers() {
        return (state == InstanceState.READY || state == InstanceState.ACTIVE)
            && currentPlayers.size() < maxPlayers;
    }

    // === Player Management ===

    public boolean addPlayer(UUID playerId) {
        if (!canAcceptPlayers()) {
            return false;
        }
        boolean added = currentPlayers.add(playerId);
        if (added) {
            LOGGER.debug("[Instance] Player {} joined instance {}", playerId, instanceId);
        }
        return added;
    }

    public boolean removePlayer(UUID playerId) {
        boolean removed = currentPlayers.remove(playerId);
        if (removed) {
            LOGGER.debug("[Instance] Player {} left instance {}", playerId, instanceId);

            // Check if instance is now empty - schedule destruction for both READY and ACTIVE states
            // This handles edge cases like teleport failures during READY state
            if (currentPlayers.isEmpty() && (state == InstanceState.ACTIVE || state == InstanceState.READY)) {
                scheduleDestruction();
            }
        }
        return removed;
    }

    public boolean hasPlayer(UUID playerId) {
        return currentPlayers.contains(playerId);
    }

    public Set<UUID> getCurrentPlayers() {
        return Collections.unmodifiableSet(currentPlayers);
    }

    /**
     * Alias for getCurrentPlayers() for API consistency.
     */
    public Set<UUID> getPlayerIds() {
        return getCurrentPlayers();
    }

    public int getPlayerCount() {
        return currentPlayers.size();
    }

    public boolean isEmpty() {
        return currentPlayers.isEmpty();
    }

    public boolean isFull() {
        return currentPlayers.size() >= maxPlayers;
    }

    // === Destruction Scheduling ===

    public void scheduleDestruction() {
        if (markedForDestructionAt == 0) {
            markedForDestructionAt = System.currentTimeMillis();
            LOGGER.info("[Instance] {} scheduled for destruction in {}ms", instanceId, DESTROY_DELAY_MS);
        }
    }

    public void cancelDestruction() {
        if (markedForDestructionAt > 0) {
            markedForDestructionAt = 0;
            LOGGER.info("[Instance] {} destruction cancelled", instanceId);
        }
    }

    public boolean isMarkedForDestruction() {
        return markedForDestructionAt > 0;
    }

    public boolean shouldDestroy() {
        return markedForDestructionAt > 0
            && System.currentTimeMillis() >= markedForDestructionAt + DESTROY_DELAY_MS;
    }

    // === Getters ===

    public UUID getInstanceId() {
        return instanceId;
    }

    @Nullable
    public ResourceKey<Level> getDimensionKey() {
        return dimensionKey;
    }

    public void setDimensionKey(ResourceKey<Level> key) {
        this.dimensionKey = key;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public int getMaxPlayers() {
        return maxPlayers;
    }

    @Nullable
    public BlockPos getArenaCenter() {
        return arenaCenter;
    }

    public int getArenaRadius() {
        return arenaRadius;
    }

    @Nullable
    public String getArenaTemplate() {
        return arenaTemplate;
    }

    @Nullable
    public ResourceLocation getQuestMobId() {
        return questMobId;
    }

    public int getCurrentWave() {
        return currentWave;
    }

    public int getTotalWaves() {
        return totalWaves;
    }

    public long getQuestStartTime() {
        return questStartTime;
    }

    public boolean isEndlessMode() {
        return endlessMode;
    }

    public long getMarkedForDestructionAt() {
        return markedForDestructionAt;
    }

    // === Setters ===

    public void setArena(BlockPos center, int radius, String template) {
        this.arenaCenter = center;
        this.arenaRadius = radius;
        this.arenaTemplate = template;
    }

    public void setQuest(ResourceLocation mobId, int totalWaves, boolean endless) {
        this.questMobId = mobId;
        this.totalWaves = totalWaves;
        this.endlessMode = endless;
        this.currentWave = 0;
        this.questStartTime = System.currentTimeMillis();
    }

    public void setCurrentWave(int wave) {
        this.currentWave = wave;
    }

    // === Duration ===

    public long getAge() {
        return System.currentTimeMillis() - createdAt;
    }

    public long getQuestDuration() {
        if (questStartTime == 0) return 0;
        return System.currentTimeMillis() - questStartTime;
    }

    // === Serialization (for registry persistence) ===

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("instanceId", instanceId.toString());
        map.put("ownerId", ownerId.toString());
        map.put("maxPlayers", maxPlayers);
        map.put("createdAt", createdAt);
        map.put("state", state.name());

        if (dimensionKey != null) {
            map.put("dimension", dimensionKey.location().toString());
        }

        List<String> playerIds = new ArrayList<>();
        for (UUID p : currentPlayers) {
            playerIds.add(p.toString());
        }
        map.put("players", playerIds);

        if (arenaCenter != null) {
            map.put("arenaX", arenaCenter.getX());
            map.put("arenaY", arenaCenter.getY());
            map.put("arenaZ", arenaCenter.getZ());
            map.put("arenaRadius", arenaRadius);
        }
        if (arenaTemplate != null) {
            map.put("arenaTemplate", arenaTemplate);
        }

        if (questMobId != null) {
            map.put("questMob", questMobId.toString());
            map.put("currentWave", currentWave);
            map.put("totalWaves", totalWaves);
            map.put("questStartTime", questStartTime);
            map.put("endlessMode", endlessMode);
        }

        map.put("markedForDestruction", markedForDestructionAt);

        return map;
    }


    public static InstanceData fromMap(Map<String, Object> map) {
        UUID instanceId = UUID.fromString((String) map.get("instanceId"));
        UUID ownerId = UUID.fromString((String) map.get("ownerId"));
        int maxPlayers = ((Number) map.get("maxPlayers")).intValue();
        long createdAt = ((Number) map.get("createdAt")).longValue();

        InstanceData data = new InstanceData(instanceId, ownerId, maxPlayers, createdAt);
        data.state = InstanceState.valueOf((String) map.get("state"));

        if (map.containsKey("dimension")) {
            String dim = Objects.requireNonNull((String) map.get("dimension"), "dimension");
            ResourceLocation dimLoc = Objects.requireNonNull(ResourceLocation.parse(dim), "dimension location");
            ResourceKey<? extends net.minecraft.core.Registry<Level>> dimensionRegistryKey =
                Objects.requireNonNull(net.minecraft.core.registries.Registries.DIMENSION, "dimension registry key");
            data.dimensionKey = ResourceKey.create(dimensionRegistryKey, dimLoc);
        }

        List<String> playerIds = getStringList(map, "players");
        for (String pid : playerIds) {
            data.currentPlayers.add(UUID.fromString(pid));
        }

        if (map.containsKey("arenaX")) {
            int x = ((Number) map.get("arenaX")).intValue();
            int y = ((Number) map.get("arenaY")).intValue();
            int z = ((Number) map.get("arenaZ")).intValue();
            data.arenaCenter = new BlockPos(x, y, z);
            data.arenaRadius = ((Number) map.get("arenaRadius")).intValue();
        }
        if (map.containsKey("arenaTemplate")) {
            data.arenaTemplate = (String) map.get("arenaTemplate");
        }

        if (map.containsKey("questMob")) {
            String questMob = Objects.requireNonNull((String) map.get("questMob"), "questMob");
            data.questMobId = ResourceLocation.parse(questMob);
            data.currentWave = ((Number) map.get("currentWave")).intValue();
            data.totalWaves = ((Number) map.get("totalWaves")).intValue();
            data.questStartTime = ((Number) map.get("questStartTime")).longValue();
            data.endlessMode = (Boolean) map.getOrDefault("endlessMode", false);
        }

        data.markedForDestructionAt = ((Number) map.getOrDefault("markedForDestruction", 0L)).longValue();

        return data;
    }

    @Override
    public String toString() {
        return "InstanceData{" +
            "id=" + instanceId +
            ", state=" + state +
            ", players=" + currentPlayers.size() + "/" + maxPlayers +
            ", wave=" + currentWave + "/" + totalWaves +
            ", age=" + (getAge() / 1000) + "s" +
            '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        InstanceData that = (InstanceData) o;
        return instanceId.equals(that.instanceId);
    }

    @Override
    public int hashCode() {
        return instanceId.hashCode();
    }

    // =========================================================================
    // JSON PARSING HELPERS (type-safe)
    // =========================================================================

    /**
     * Safely extracts a List of Strings from a Map, avoiding unchecked casts.
     */
    private static List<String> getStringList(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) {
            return List.of();
        }
        if (value instanceof List<?> list) {
            List<String> result = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof String s) {
                    result.add(s);
                }
            }
            return result;
        }
        return List.of();
    }
}
