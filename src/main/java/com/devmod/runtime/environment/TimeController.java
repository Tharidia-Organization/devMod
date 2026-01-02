package com.devmod.runtime.environment;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import com.devmod.arena.zone.ZoneEnvironment;
import com.devmod.network.NetworkHandler;

/**
 * Controls time per-dimension for arena instances.
 * Allows fixing time to day/night for specific mob testing conditions.
 */
public class TimeController {
    private static final Logger LOGGER = LoggerFactory.getLogger(TimeController.class);

    /** Time settings per dimension */
    private final Map<ResourceKey<Level>, TimeSettings> dimensionTime = new ConcurrentHashMap<>();

    /** How often to enforce time (in ticks) - every second */
    private static final int ENFORCEMENT_INTERVAL = 20;

    private int tickCounter = 0;

    /**
     * Sets the time configuration for a dimension.
     *
     * @param dimensionKey The dimension to configure
     * @param config The time configuration
     */
    public void setTime(ResourceKey<Level> dimensionKey, ZoneEnvironment.TimeConfig config) {
        if (config == ZoneEnvironment.TimeConfig.ANY) {
            dimensionTime.remove(dimensionKey);
            return;
        }

        TimeSettings settings = new TimeSettings(
            config.getFixedTime(),
            config.isFrozen(),
            config
        );
        dimensionTime.put(dimensionKey, settings);
        LOGGER.debug("Set time for {}: {} (frozen={})", dimensionKey.location(), config, config.isFrozen());
    }

    /**
     * Syncs environment settings to a player entering a dimension.
     * Call this when a player joins or teleports to an arena dimension.
     *
     * @param player The player to sync
     * @param dimensionKey The dimension
     * @param biomeId The biome ID (or empty string)
     */
    public void syncToPlayer(ServerPlayer player, ResourceKey<Level> dimensionKey, String biomeId) {
        TimeSettings settings = dimensionTime.get(dimensionKey);
        if (settings != null && settings.frozen) {
            // Send frozen time override to client
            EnvironmentSyncPayload payload = EnvironmentSyncPayload.frozen(
                dimensionKey.location().toString(),
                settings.fixedTime,
                biomeId != null ? biomeId : ""
            );
            NetworkHandler.sendEnvironmentSync(player, payload);
            LOGGER.debug("Synced environment to {}: dimension={}, time={}",
                player.getName().getString(), dimensionKey.location(), settings.fixedTime);
        } else {
            // Send explicit clear to remove any stale time override on client
            // This is important when entering a dimension with TimeConfig.ANY
            EnvironmentSyncPayload payload = EnvironmentSyncPayload.clear(dimensionKey.location().toString());
            NetworkHandler.sendEnvironmentSync(player, payload);
            LOGGER.debug("Cleared environment sync for {}: dimension={} (no frozen time)",
                player.getName().getString(), dimensionKey.location());
        }
    }

    /**
     * Clears environment sync for a player leaving an arena dimension.
     */
    public void clearSyncForPlayer(ServerPlayer player, ResourceKey<Level> dimensionKey) {
        EnvironmentSyncPayload payload = EnvironmentSyncPayload.clear(dimensionKey.location().toString());
        NetworkHandler.sendEnvironmentSync(player, payload);
    }

    /**
     * Sets a specific time tick for a dimension.
     *
     * @param dimensionKey The dimension to configure
     * @param time The time tick (0-24000)
     * @param frozen Whether to freeze time at this value
     */
    public void setTime(ResourceKey<Level> dimensionKey, long time, boolean frozen) {
        TimeSettings settings = new TimeSettings(time, frozen, null);
        dimensionTime.put(dimensionKey, settings);
        LOGGER.debug("Set time for {}: {} (frozen={})", dimensionKey.location(), time, frozen);
    }

    /**
     * Gets the time override for a dimension.
     */
    public Optional<Long> getTimeOverride(ResourceKey<Level> dimensionKey) {
        TimeSettings settings = dimensionTime.get(dimensionKey);
        if (settings != null && settings.frozen) {
            return Optional.of(settings.fixedTime);
        }
        return Optional.empty();
    }

    /**
     * Gets the time settings for a dimension.
     */
    public Optional<TimeSettings> getSettings(ResourceKey<Level> dimensionKey) {
        return Optional.ofNullable(dimensionTime.get(dimensionKey));
    }

    /**
     * Removes time settings for a dimension.
     */
    public void removeDimension(ResourceKey<Level> dimensionKey) {
        dimensionTime.remove(dimensionKey);
    }

    /**
     * Ticks the time controller.
     * Enforces frozen time on configured dimensions.
     *
     * @param server The Minecraft server
     */
    public void tick(MinecraftServer server) {
        tickCounter++;
        if (tickCounter < ENFORCEMENT_INTERVAL) {
            return;
        }
        tickCounter = 0;

        // Enforce frozen time on all configured dimensions
        for (var entry : dimensionTime.entrySet()) {
            ResourceKey<Level> dimensionKey = entry.getKey();
            TimeSettings settings = entry.getValue();

            if (!settings.frozen) {
                continue;
            }

            ServerLevel level = server.getLevel(Objects.requireNonNull(dimensionKey));
            if (level == null) {
                continue;
            }

            // Set the day time to our fixed value
            long currentTime = level.getDayTime();
            long targetTime = settings.fixedTime;

            // Only set if different to avoid unnecessary updates
            if (Math.abs(currentTime % 24000 - targetTime) > 1) {
                level.setDayTime(targetTime);
            }
        }
    }

    /**
     * Checks if a dimension has frozen time.
     */
    public boolean isTimeFrozen(ResourceKey<Level> dimensionKey) {
        TimeSettings settings = dimensionTime.get(dimensionKey);
        return settings != null && settings.frozen;
    }

    /**
     * Gets the current effective time for a dimension.
     * Returns the frozen time if set, otherwise the actual level time.
     */
    public long getEffectiveTime(ResourceKey<Level> dimensionKey, ServerLevel level) {
        TimeSettings settings = dimensionTime.get(dimensionKey);
        if (settings != null && settings.frozen) {
            return settings.fixedTime;
        }
        return level.getDayTime();
    }

    /**
     * Time settings for a dimension.
     */
    public record TimeSettings(
        long fixedTime,
        boolean frozen,
        ZoneEnvironment.TimeConfig config
    ) {
        /**
         * Gets the time as a string for display.
         */
        public String getTimeString() {
            if (config != null) {
                return config.name();
            }
            return formatTime(fixedTime);
        }

        private static String formatTime(long time) {
            long hour = (time / 1000 + 6) % 24;
            long minute = (time % 1000) * 60 / 1000;
            return String.format("%02d:%02d", hour, minute);
        }
    }
}
