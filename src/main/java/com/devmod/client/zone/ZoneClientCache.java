package com.devmod.client.zone;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;

import com.devmod.zone.network.ZoneSyncPayload;

/**
 * Client-side cache for zone data.
 * Used for rendering zone boundaries and tracking player zone state.
 */
public final class ZoneClientCache {
    public static final ZoneClientCache INSTANCE = new ZoneClientCache();

    /** Cached zones by UUID */
    private final Map<UUID, ZoneSyncPayload.ZoneSummary> zones = new ConcurrentHashMap<>();

    /** Current registry version for cache invalidation */
    private volatile long registryVersion = -1;

    /** Current zone the player is in (for display) */
    @Nullable
    private volatile ZoneSyncPayload.ZoneSummary currentZone = null;

    private ZoneClientCache() {}

    /**
     * Updates the cache with new zone data from the server.
     */
    public void update(@Nonnull List<ZoneSyncPayload.ZoneSummary> zoneSummaries, long version) {
        zones.clear();
        for (var zone : zoneSummaries) {
            zones.put(zone.id(), zone);
        }
        this.registryVersion = version;
    }

    /**
     * Gets all cached zones.
     */
    @Nonnull
    public Collection<ZoneSyncPayload.ZoneSummary> getAllZones() {
        return Collections.unmodifiableCollection(zones.values());
    }

    /**
     * Gets a zone by UUID.
     */
    @Nonnull
    public Optional<ZoneSyncPayload.ZoneSummary> getZone(@Nonnull UUID id) {
        return Optional.ofNullable(zones.get(id));
    }

    /**
     * Gets a zone by string ID.
     */
    @Nonnull
    public Optional<ZoneSyncPayload.ZoneSummary> getZone(@Nonnull String zoneId) {
        return zones.values().stream()
            .filter(z -> z.zoneId().equals(zoneId))
            .findFirst();
    }

    /**
     * Finds the zone containing a position (highest priority wins).
     */
    @Nonnull
    public Optional<ZoneSyncPayload.ZoneSummary> getZoneAt(@Nonnull BlockPos pos) {
        return zones.values().stream()
            .filter(z -> z.bounds().contains(pos))
            .max((a, b) -> Integer.compare(a.priority(), b.priority()));
    }

    /**
     * Sets the current zone the player is in.
     */
    public void setCurrentZone(@Nullable ZoneSyncPayload.ZoneSummary zone) {
        this.currentZone = zone;
    }

    /**
     * Gets the current zone the player is in.
     */
    @Nullable
    public ZoneSyncPayload.ZoneSummary getCurrentZone() {
        return currentZone;
    }

    /**
     * Gets the registry version for cache validation.
     */
    public long getRegistryVersion() {
        return registryVersion;
    }

    /**
     * Clears all cached data (on dimension change or disconnect).
     */
    public void clear() {
        zones.clear();
        registryVersion = -1;
        currentZone = null;
    }

    /**
     * Checks if the cache has any zones.
     */
    public boolean isEmpty() {
        return zones.isEmpty();
    }

    /**
     * Gets the count of cached zones.
     */
    public int size() {
        return zones.size();
    }
}
