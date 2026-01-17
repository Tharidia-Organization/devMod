package com.devmod.zone.runtime;

import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;

import com.devmod.DevMod;
import com.devmod.config.Config;
import com.devmod.zone.data.ZoneDefinition;
import com.devmod.zone.network.ZoneNetworkHandler;

/**
 * Tracks player zone state and triggers zone entry events.
 * Replaces NexusZoneTracker with data-driven zone support.
 */
public final class ZoneTracker {
    public static final ZoneTracker INSTANCE = new ZoneTracker();

    /** Player zone states (thread-safe) */
    private final Map<UUID, ZoneState> states = new ConcurrentHashMap<>();

    private ZoneTracker() {}

    /**
     * Ticks the zone tracker for a level.
     * Called from server tick event.
     *
     * @param level The server level (Nexus dimension)
     */
    public void tick(@Nonnull ServerLevel level) {
        Objects.requireNonNull(level, "level");

        // Skip if zone features are disabled
        if (!Config.NEXUS_ZONE_CUES.get() && !Config.NEXUS_ZONE_HINTS.get()) {
            return;
        }

        long now = level.getGameTime();
        int cooldown = Math.max(0, Config.NEXUS_ZONE_CUE_COOLDOWN.get());
        Set<UUID> seen = new HashSet<>();

        for (ServerPlayer player : level.players()) {
            UUID playerId = player.getUUID();
            seen.add(playerId);

            // Resolve current zone
            Optional<ZoneDefinition> currentZoneOpt = ZoneResolver.INSTANCE.resolve(level, player.blockPosition());
            ZoneDefinition currentZone = currentZoneOpt.orElse(null);

            // Get or create player state
            ZoneState state = states.computeIfAbsent(playerId, id -> new ZoneState());

            // Check for zone change
            UUID currentZoneId = currentZone != null ? currentZone.id() : null;
            if (!Objects.equals(currentZoneId, state.zoneId)) {
                state.zoneId = currentZoneId;
                state.zoneStringId = currentZone != null ? currentZone.zoneId() : null;

                // Trigger zone entry
                if (currentZone != null && now - state.lastCueTick >= cooldown) {
                    onZoneEnter(player, currentZone);
                    state.lastCueTick = now;
                }
            }
        }

        // Clean up disconnected players
        states.keySet().retainAll(seen);
    }

    /**
     * Called when a player enters a zone.
     */
    private void onZoneEnter(@Nonnull ServerPlayer player, @Nonnull ZoneDefinition zone) {
        // Play cue sound (server-side)
        if (Config.NEXUS_ZONE_CUES.get() && zone.cueSound() != null) {
            player.level().playSound(
                null,
                player.blockPosition(),
                zone.cueSound(),
                SoundSource.AMBIENT,
                0.45f, 1.1f
            );
        }

        // Show hint message (server-side action bar)
        if (Config.NEXUS_ZONE_HINTS.get() && !zone.hintMessage().isEmpty()) {
            player.displayClientMessage(
                Component.literal(zone.hintMessage()),
                true
            );
        }

        // Send zone enter notification to client (for advanced client-side effects)
        ZoneNetworkHandler.sendZoneEnter(player, zone);

        DevMod.LOGGER.debug("[Zone] Player {} entered zone: {} ({})",
            player.getName().getString(), zone.displayName(), zone.zoneId());
    }

    /**
     * Clears state for a player (on logout/dimension change).
     *
     * @param playerId The player UUID
     */
    public void clear(@Nonnull UUID playerId) {
        states.remove(Objects.requireNonNull(playerId, "playerId"));
    }

    /**
     * Gets the current zone for a player.
     *
     * @param playerId The player UUID
     * @return The current zone string ID, or null if outside all zones
     */
    @Nullable
    public String getCurrentZone(@Nonnull UUID playerId) {
        ZoneState state = states.get(Objects.requireNonNull(playerId, "playerId"));
        return state != null ? state.zoneStringId : null;
    }

    /**
     * Gets the current zone UUID for a player.
     *
     * @param playerId The player UUID
     * @return The current zone UUID, or null if outside all zones
     */
    @Nullable
    public UUID getCurrentZoneId(@Nonnull UUID playerId) {
        ZoneState state = states.get(Objects.requireNonNull(playerId, "playerId"));
        return state != null ? state.zoneId : null;
    }

    /**
     * Checks if a player is in a specific zone.
     *
     * @param playerId The player UUID
     * @param zoneId   The zone string ID to check
     * @return true if player is in the specified zone
     */
    public boolean isInZone(@Nonnull UUID playerId, @Nonnull String zoneId) {
        String current = getCurrentZone(playerId);
        return zoneId.equals(current);
    }

    /**
     * Gets the number of players currently being tracked.
     */
    public int getTrackedPlayerCount() {
        return states.size();
    }

    /**
     * Clears all tracking state (on server shutdown).
     */
    public void clearAll() {
        int count = states.size();
        states.clear();
        if (count > 0) {
            DevMod.LOGGER.debug("[Zone] Cleared {} zone tracking states", count);
        }
    }

    /**
     * Player zone state.
     */
    private static final class ZoneState {
        @Nullable UUID zoneId = null;
        @Nullable String zoneStringId = null;
        long lastCueTick = 0;
    }
}
