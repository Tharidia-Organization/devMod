package com.devmod.transport.executor;

import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Manages cooldowns for transport nodes.
 * Follows pattern from TelepadBlockEntity arrival cooldown.
 *
 * <p>Default timing (Bibbia Estetica Rule 7):
 * <ul>
 *   <li>ARRIVAL_COOLDOWN: 60 ticks (3s) - after arriving at destination</li>
 *   <li>REUSE_COOLDOWN: 100 ticks (5s) - before using same node again</li>
 * </ul>
 */
public class CooldownManager {

    /**
     * Types of cooldowns.
     */
    public enum CooldownType {
        /** Post-arrival cooldown (prevents immediate re-teleport on arrival). */
        ARRIVAL(60),

        /** Reuse cooldown (prevents spamming same portal). */
        REUSE(100),

        /** Global cooldown (prevents any teleport). */
        GLOBAL(20);

        private final int defaultTicks;

        CooldownType(int defaultTicks) {
            this.defaultTicks = defaultTicks;
        }

        public int getDefaultTicks() {
            return defaultTicks;
        }
    }

    /**
     * Record representing a cooldown entry.
     */
    public record CooldownEntry(
        @Nullable UUID nodeId,  // null for GLOBAL cooldown
        long expirationTick,
        @Nonnull CooldownType type
    ) {
        /**
         * Returns true if this cooldown has expired.
         */
        public boolean isExpired(long currentTick) {
            return currentTick >= expirationTick;
        }

        /**
         * Returns remaining ticks until expiration.
         */
        public long getRemainingTicks(long currentTick) {
            return Math.max(0, expirationTick - currentTick);
        }
    }

    // Key: entityId + ":" + type + ":" + nodeId (or "global" for GLOBAL type)
    private final Map<String, CooldownEntry> cooldowns = new ConcurrentHashMap<>();

    /**
     * Sets a cooldown for an entity at a specific node.
     *
     * @param entityId the entity UUID
     * @param nodeId the node UUID (null for GLOBAL)
     * @param type the cooldown type
     * @param currentTick the current game tick
     * @param durationTicks cooldown duration in ticks
     */
    public void setCooldown(
        @Nonnull UUID entityId,
        @Nullable UUID nodeId,
        @Nonnull CooldownType type,
        long currentTick,
        int durationTicks
    ) {
        String key = buildKey(entityId, nodeId, type);
        cooldowns.put(key, new CooldownEntry(nodeId, currentTick + durationTicks, type));
    }

    /**
     * Sets a cooldown using default duration for the type.
     */
    public void setCooldown(
        @Nonnull UUID entityId,
        @Nullable UUID nodeId,
        @Nonnull CooldownType type,
        long currentTick
    ) {
        setCooldown(entityId, nodeId, type, currentTick, type.getDefaultTicks());
    }

    /**
     * Checks if an entity is on cooldown at a specific node.
     *
     * @param entityId the entity UUID
     * @param nodeId the node UUID (null checks only GLOBAL)
     * @param type the cooldown type to check
     * @param currentTick the current game tick
     * @return true if on cooldown
     */
    public boolean isOnCooldown(
        @Nonnull UUID entityId,
        @Nullable UUID nodeId,
        @Nonnull CooldownType type,
        long currentTick
    ) {
        String key = buildKey(entityId, nodeId, type);
        CooldownEntry entry = cooldowns.get(key);
        return entry != null && !entry.isExpired(currentTick);
    }

    /**
     * Checks if an entity has any active cooldown for a node.
     */
    public boolean hasAnyCooldown(@Nonnull UUID entityId, @Nullable UUID nodeId, long currentTick) {
        for (CooldownType type : CooldownType.values()) {
            if (isOnCooldown(entityId, nodeId, type, currentTick)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks if an entity has a global cooldown.
     */
    public boolean hasGlobalCooldown(@Nonnull UUID entityId, long currentTick) {
        return isOnCooldown(entityId, null, CooldownType.GLOBAL, currentTick);
    }

    /**
     * Gets the cooldown entry for an entity/node/type combination.
     */
    @Nonnull
    public Optional<CooldownEntry> getCooldown(
        @Nonnull UUID entityId,
        @Nullable UUID nodeId,
        @Nonnull CooldownType type
    ) {
        String key = buildKey(entityId, nodeId, type);
        return Optional.ofNullable(cooldowns.get(key));
    }

    /**
     * Clears all cooldowns for an entity.
     */
    public void clearCooldowns(@Nonnull UUID entityId) {
        String prefix = entityId.toString() + ":";
        cooldowns.keySet().removeIf(key -> key.startsWith(prefix));
    }

    /**
     * Clears a specific cooldown.
     */
    public void clearCooldown(
        @Nonnull UUID entityId,
        @Nullable UUID nodeId,
        @Nonnull CooldownType type
    ) {
        String key = buildKey(entityId, nodeId, type);
        cooldowns.remove(key);
    }

    /**
     * Processes expired cooldowns and removes them.
     *
     * @param currentTick the current game tick
     */
    public void tick(long currentTick) {
        Iterator<Map.Entry<String, CooldownEntry>> iterator = cooldowns.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, CooldownEntry> entry = iterator.next();
            if (entry.getValue().isExpired(currentTick)) {
                iterator.remove();
            }
        }
    }

    /**
     * Clears all cooldowns (used on server shutdown).
     */
    public void clear() {
        cooldowns.clear();
    }

    /**
     * Returns the number of active cooldowns.
     */
    public int size() {
        return cooldowns.size();
    }

    private String buildKey(@Nonnull UUID entityId, @Nullable UUID nodeId, @Nonnull CooldownType type) {
        String nodeIdStr = nodeId != null ? nodeId.toString() : "global";
        return entityId.toString() + ":" + type.name() + ":" + nodeIdStr;
    }
}
