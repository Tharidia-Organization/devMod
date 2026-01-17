package com.devmod.zone.runtime;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import com.devmod.config.Config;
import com.devmod.zone.data.ZoneDefinition;
import com.devmod.zone.data.ZoneRegistry;

/**
 * Resolves which zone a position belongs to.
 * Uses priority-based resolution when zones overlap.
 * Also handles spawn mode selection (BY_TEAM, ROUND_ROBIN, DEFAULT).
 */
public final class ZoneResolver {
    public static final ZoneResolver INSTANCE = new ZoneResolver();

    /** Counter for round-robin spawn selection */
    private final AtomicInteger roundRobinCounter = new AtomicInteger(0);

    private ZoneResolver() {}

    /**
     * Resolves the zone at a given position.
     * When multiple zones contain the position, the one with highest priority wins.
     *
     * @param server The server
     * @param pos    The position to check
     * @return The zone at this position, or empty if outside all zones
     */
    @Nonnull
    public Optional<ZoneDefinition> resolve(@Nonnull MinecraftServer server, @Nonnull BlockPos pos) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(pos, "pos");

        ZoneRegistry registry = ZoneRegistry.get(server);
        return resolveFromCollection(registry.getAllZones(), pos);
    }

    /**
     * Resolves the zone at a given position in a specific level.
     *
     * @param level The server level
     * @param pos   The position to check
     * @return The zone at this position, or empty if outside all zones
     */
    @Nonnull
    public Optional<ZoneDefinition> resolve(@Nonnull ServerLevel level, @Nonnull BlockPos pos) {
        Objects.requireNonNull(level, "level");
        return resolve(level.getServer(), pos);
    }

    /**
     * Resolves the zone from a collection of zones.
     * Used for testing or when working with a subset of zones.
     *
     * @param zones The zones to check
     * @param pos   The position to check
     * @return The zone at this position, or empty if outside all zones
     */
    @Nonnull
    public Optional<ZoneDefinition> resolveFromCollection(
        @Nonnull Collection<ZoneDefinition> zones,
        @Nonnull BlockPos pos
    ) {
        Objects.requireNonNull(zones, "zones");
        Objects.requireNonNull(pos, "pos");

        return Objects.requireNonNull(zones.stream()
            .filter(zone -> zone.bounds().contains(pos))
            .max(Comparator.comparingInt(ZoneDefinition::priority)));
    }

    /**
     * Resolves the zone by string ID.
     *
     * @param server The server
     * @param zoneId The zone string ID
     * @return The zone, or empty if not found
     */
    @Nonnull
    public Optional<ZoneDefinition> resolveById(@Nonnull MinecraftServer server, @Nonnull String zoneId) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(zoneId, "zoneId");

        ZoneRegistry registry = ZoneRegistry.get(server);
        return registry.getZoneById(zoneId);
    }

    /**
     * Resolves a zone by alias or ID.
     *
     * @param server The server
     * @param nameOrAlias The zone ID or alias
     * @return The zone, or empty if not found
     */
    @Nonnull
    public Optional<ZoneDefinition> resolveByNameOrAlias(@Nonnull MinecraftServer server, @Nonnull String nameOrAlias) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(nameOrAlias, "nameOrAlias");

        ZoneRegistry registry = ZoneRegistry.get(server);

        // resolveByAlias handles both alias and direct zoneId lookup
        return registry.resolveByAlias(nameOrAlias);
    }

    /**
     * Checks if a position is inside any zone.
     *
     * @param server The server
     * @param pos    The position to check
     * @return true if inside at least one zone
     */
    public boolean isInsideAnyZone(@Nonnull MinecraftServer server, @Nonnull BlockPos pos) {
        return resolve(server, pos).isPresent();
    }

    /**
     * Gets the spawn position for a zone.
     *
     * @param server The server
     * @param zoneId The zone string ID
     * @param origin The hub origin for calculating absolute position
     * @return The spawn position, or null if zone not found or no spawn defined
     */
    @Nullable
    public BlockPos getSpawnPosition(
        @Nonnull MinecraftServer server,
        @Nonnull String zoneId,
        @Nonnull BlockPos origin
    ) {
        Optional<ZoneDefinition> zoneOpt = resolveById(server, zoneId);
        if (zoneOpt.isEmpty()) {
            return null;
        }

        ZoneDefinition zone = zoneOpt.get();
        BlockPos offset = zone.spawnOffset();
        if (offset == null) {
            // Use center of bounds as fallback
            return zone.bounds().center();
        }

        // Spawn offset is relative to origin
        return origin.offset(offset);
    }

    // ========================================================================
    // Spawn Mode Selection
    // ========================================================================

    /**
     * Selects a spawn zone based on the configured spawn mode.
     *
     * @param server The server
     * @param player The player being spawned (used for BY_TEAM mode)
     * @return The selected zone, or empty if no teleportable zones exist
     */
    public Optional<ZoneDefinition> selectSpawnZone(
        @Nonnull MinecraftServer server,
        @Nonnull ServerPlayer player
    ) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(player, "player");

        ZoneRegistry registry = ZoneRegistry.get(server);
        Config.NexusSpawnMode mode = Config.NEXUS_SPAWN_MODE.get();

        return switch (mode) {
            case BY_TEAM -> getZoneForTeam(registry, player);
            case ROUND_ROBIN -> getNextRoundRobinZone(registry);
            case DEFAULT -> getDefaultZone(registry);
        };
    }

    /**
     * Gets the next zone in round-robin rotation.
     * Cycles through all teleportable zones.
     *
     * @param registry The zone registry
     * @return The next zone in rotation, or empty if none
     */
    private Optional<ZoneDefinition> getNextRoundRobinZone(@Nonnull ZoneRegistry registry) {
        List<ZoneDefinition> teleportable = registry.getAllZones().stream()
            .filter(ZoneDefinition::hasTeleport)
            .sorted(Comparator.comparing(ZoneDefinition::zoneId))
            .toList();

        if (teleportable.isEmpty()) {
            return Optional.empty();
        }

        int index = roundRobinCounter.getAndUpdate(i -> (i + 1) % teleportable.size());
        return Optional.of(teleportable.get(index % teleportable.size()));
    }

    /**
     * Gets a zone based on player's team.
     * Looks for zones with zoneId containing team name keywords.
     *
     * @param registry The zone registry
     * @param player The player to check team for
     * @return A matching zone, or falls back to default/round-robin
     */
    private Optional<ZoneDefinition> getZoneForTeam(
        @Nonnull ZoneRegistry registry,
        @Nonnull ServerPlayer player
    ) {
        // Get player's team
        var team = player.getTeam();
        if (team == null) {
            // No team, fall back to round-robin
            return getNextRoundRobinZone(registry);
        }

        String teamName = team.getName().toLowerCase(Locale.ROOT);

        // Look for zone with matching team keyword in zoneId
        Optional<ZoneDefinition> teamZone = registry.getAllZones().stream()
            .filter(ZoneDefinition::hasTeleport)
            .filter(zone -> zone.zoneId().toLowerCase(Locale.ROOT).contains(teamName))
            .findFirst();

        if (teamZone.isPresent()) {
            return teamZone;
        }

        // Also check for common team color keywords
        String teamColor = team.getColor().getName();
        if (teamColor != null) {
            teamZone = registry.getAllZones().stream()
                .filter(ZoneDefinition::hasTeleport)
                .filter(zone -> zone.zoneId().toLowerCase(Locale.ROOT).contains(teamColor.toLowerCase(Locale.ROOT)))
                .findFirst();

            if (teamZone.isPresent()) {
                return teamZone;
            }
        }

        // No matching zone, fall back to round-robin
        return getNextRoundRobinZone(registry);
    }

    /**
     * Gets the default spawn zone (first teleportable zone by priority).
     *
     * @param registry The zone registry
     * @return The default zone, or empty if none
     */
    private Optional<ZoneDefinition> getDefaultZone(@Nonnull ZoneRegistry registry) {
        return registry.getAllZones().stream()
            .filter(ZoneDefinition::hasTeleport)
            .max(Comparator.comparingInt(ZoneDefinition::priority));
    }

    /**
     * Resets the round-robin counter (for testing or server restart).
     */
    public void resetRoundRobin() {
        roundRobinCounter.set(0);
    }
}
