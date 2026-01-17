package com.devmod.zone.block.entity;

import java.util.Optional;
import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import com.devmod.zone.ZoneBlockEntities;
import com.devmod.zone.data.ZoneDefinition;
import com.devmod.zone.data.ZonePresets;
import com.devmod.zone.data.ZoneRegistry;

/**
 * Block entity for Zone Marker block.
 * Stores the UUID of the linked zone with orphan reference handling.
 * Uses version-based cache invalidation (same pattern as AreaEditorBlockEntity).
 */
public class ZoneMarkerBlockEntity extends BlockEntity {
    private static final Logger LOGGER = LoggerFactory.getLogger(ZoneMarkerBlockEntity.class);
    private static final String TAG_ZONE_ID = "ZoneId";
    private static final String TAG_ZONE_COLOR = "ZoneColor";

    @Nullable
    private UUID zoneId = null;

    /** Cached zone color for particle rendering (client-side) */
    private int cachedColor = ZonePresets.COLOR_HUB;

    /** Cache for validated state to avoid repeated registry lookups */
    private transient long cachedRegistryVersion = -1;
    private transient boolean cachedIsValid = false;

    public ZoneMarkerBlockEntity(BlockPos pos, BlockState state) {
        super(ZoneBlockEntities.ZONE_MARKER.get(), pos, state);
    }

    // ========================================================================
    // Zone Linkage
    // ========================================================================

    /**
     * Gets the linked zone ID (raw, without validation).
     */
    @Nullable
    public UUID getZoneId() {
        return zoneId;
    }

    /**
     * Gets the linked zone ID, validating it exists in the registry.
     * Returns null if the zone was deleted (orphan reference).
     * Server-side only.
     */
    @Nullable
    public UUID getValidatedZoneId() {
        if (zoneId == null) {
            return null;
        }

        if (!hasValidZone()) {
            return null;
        }

        return zoneId;
    }

    /**
     * Sets the linked zone ID.
     */
    public void setZoneId(@Nullable UUID zoneId) {
        this.zoneId = zoneId;
        invalidateCache();
        updateCachedColor();
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    /**
     * Returns true if this marker has a linked zone ID (may be orphan).
     * For validated check, use {@link #hasValidZone()}.
     */
    public boolean hasZone() {
        return zoneId != null;
    }

    /**
     * Returns true if this marker has a linked zone that exists in the registry.
     * Server-side only - returns hasZone() on client.
     * Uses version-based cache invalidation to detect registry changes.
     */
    public boolean hasValidZone() {
        UUID localZoneId = this.zoneId;
        if (localZoneId == null) {
            return false;
        }

        // On client, we can't validate - trust the server
        var localLevel = this.level;
        if (localLevel == null || localLevel.isClientSide) {
            return true;
        }

        // Validate against registry
        ServerLevel serverLevel = (ServerLevel) localLevel;
        ZoneRegistry registry = ZoneRegistry.get(serverLevel.getServer());
        long currentVersion = registry.getModificationVersion();

        // Use cache if registry hasn't changed
        if (cachedRegistryVersion == currentVersion) {
            return cachedIsValid;
        }

        // Cache miss or stale - revalidate
        cachedIsValid = registry.getZone(localZoneId).isPresent();
        cachedRegistryVersion = currentVersion;

        // If orphan, log
        if (!cachedIsValid) {
            LOGGER.debug("[Zone] Marker at {} has orphan reference to deleted zone {}",
                worldPosition, localZoneId);
        }

        return cachedIsValid;
    }

    /**
     * Gets the linked ZoneDefinition if it exists.
     * Server-side only.
     *
     * @return The zone definition, or empty if orphan/null/client-side
     */
    public Optional<ZoneDefinition> getLinkedZone() {
        UUID localZoneId = this.zoneId;
        var localLevel = this.level;
        if (localZoneId == null || localLevel == null || localLevel.isClientSide) {
            return Optional.empty();
        }

        ServerLevel serverLevel = (ServerLevel) localLevel;
        ZoneRegistry registry = ZoneRegistry.get(serverLevel.getServer());
        Optional<ZoneDefinition> zone = registry.getZone(localZoneId);

        // Update cache based on lookup result
        cachedRegistryVersion = registry.getModificationVersion();
        cachedIsValid = zone.isPresent();

        // Update cached color
        zone.ifPresent(z -> cachedColor = z.color());

        return zone;
    }

    /**
     * Validates and clears orphan references.
     * Call this periodically or on block interaction.
     *
     * @return true if the reference was valid or null, false if orphan was cleared
     */
    public boolean validateAndClearOrphan() {
        if (zoneId == null) {
            return true;
        }

        if (!hasValidZone()) {
            LOGGER.info("[Zone] Clearing orphan reference at {} (zone {} was deleted)",
                worldPosition, zoneId);
            zoneId = null;
            cachedColor = ZonePresets.COLOR_HUB;
            invalidateCache();
            setChanged();
            var localLevel = this.level;
            if (localLevel != null && !localLevel.isClientSide) {
                localLevel.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            }
            return false;
        }

        return true;
    }

    /**
     * Invalidates the validation cache.
     * Call this when the registry might have changed.
     */
    public void invalidateCache() {
        cachedRegistryVersion = -1;
        cachedIsValid = false;
    }

    // ========================================================================
    // Color
    // ========================================================================

    /**
     * Gets the zone color for rendering.
     * Returns cached color (works on both client and server).
     */
    public int getZoneColor() {
        return cachedColor;
    }

    /**
     * Updates the cached color from the linked zone (server-side).
     */
    private void updateCachedColor() {
        var localLevel = this.level;
        if (localLevel == null || localLevel.isClientSide || zoneId == null) {
            return;
        }

        ServerLevel serverLevel = (ServerLevel) localLevel;
        ZoneRegistry registry = ZoneRegistry.get(serverLevel.getServer());
        registry.getZone(zoneId).ifPresent(zone -> cachedColor = zone.color());
    }

    // ========================================================================
    // Server Tick
    // ========================================================================

    /**
     * Server-side tick for zone marker.
     * Currently unused but available for future features (bounds visualization, etc.)
     */
    public static void serverTick(Level level, BlockPos pos, BlockState state, ZoneMarkerBlockEntity be) {
        // Reserved for future use
    }

    // ========================================================================
    // Persistence
    // ========================================================================

    @Override
    protected void saveAdditional(@Nonnull CompoundTag tag, @Nonnull HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (zoneId != null) {
            tag.putUUID(TAG_ZONE_ID, zoneId);
        }
        tag.putInt(TAG_ZONE_COLOR, cachedColor);
    }

    @Override
    protected void loadAdditional(@Nonnull CompoundTag tag, @Nonnull HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.hasUUID(TAG_ZONE_ID)) {
            zoneId = tag.getUUID(TAG_ZONE_ID);
        } else {
            zoneId = null;
        }
        if (tag.contains(TAG_ZONE_COLOR)) {
            cachedColor = tag.getInt(TAG_ZONE_COLOR);
        }
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    @Nonnull
    public CompoundTag getUpdateTag(@Nonnull HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        if (zoneId != null) {
            tag.putUUID(TAG_ZONE_ID, zoneId);
        }
        tag.putInt(TAG_ZONE_COLOR, cachedColor);
        return tag;
    }
}
