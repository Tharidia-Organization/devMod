package com.devmod.area.block.entity;

import java.util.Objects;
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
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import com.devmod.area.AreaBlockEntities;
import com.devmod.area.data.AreaDefinition;
import com.devmod.area.data.AreaRegistry;

/**
 * Block entity for Area Editor block.
 * Stores the UUID of the linked area with orphan reference handling.
 */
public class AreaEditorBlockEntity extends BlockEntity {
    private static final Logger LOGGER = LoggerFactory.getLogger(AreaEditorBlockEntity.class);
    private static final String TAG_AREA_ID = "AreaId";

    @Nullable
    private UUID areaId = null;

    /** Cache for validated state to avoid repeated registry lookups */
    private transient long cachedRegistryVersion = -1;
    private transient boolean cachedIsValid = false;

    public AreaEditorBlockEntity(BlockPos pos, BlockState state) {
        super(AreaBlockEntities.AREA_EDITOR.get(), pos, state);
    }

    /**
     * Gets the linked area ID (raw, without validation).
     */
    @Nullable
    public UUID getAreaId() {
        return areaId;
    }

    /**
     * Gets the linked area ID, validating it exists in the registry.
     * Returns null if the area was deleted (orphan reference).
     * Server-side only.
     */
    @Nullable
    public UUID getValidatedAreaId() {
        if (areaId == null) {
            return null;
        }

        if (!hasValidArea()) {
            return null;
        }

        return areaId;
    }

    /**
     * Sets the linked area ID.
     */
    public void setAreaId(@Nullable UUID areaId) {
        this.areaId = areaId;
        invalidateCache();
        setChanged();
        var localLevel = this.level;
        if (localLevel != null && !localLevel.isClientSide) {
            localLevel.sendBlockUpdated(Objects.requireNonNull(worldPosition), Objects.requireNonNull(getBlockState()), Objects.requireNonNull(getBlockState()), 3);
        }
    }

    /**
     * Returns true if this editor has a linked area ID (may be orphan).
     * For validated check, use {@link #hasValidArea()}.
     */
    public boolean hasArea() {
        return areaId != null;
    }

    /**
     * Returns true if this editor has a linked area that exists in the registry.
     * Server-side only - returns hasArea() on client.
     * Uses version-based cache invalidation to detect registry changes.
     */
    public boolean hasValidArea() {
        UUID localAreaId = this.areaId;
        if (localAreaId == null) {
            return false;
        }

        // On client, we can't validate - trust the server
        var localLevel = this.level;
        if (localLevel == null || localLevel.isClientSide) {
            return true;
        }

        // Validate against registry
        ServerLevel serverLevel = (ServerLevel) localLevel;
        AreaRegistry registry = AreaRegistry.get(serverLevel.getServer());
        long currentVersion = registry.getModificationVersion();

        // Use cache if registry hasn't changed
        if (cachedRegistryVersion == currentVersion) {
            return cachedIsValid;
        }

        // Cache miss or stale - revalidate
        cachedIsValid = registry.getArea(localAreaId).isPresent();
        cachedRegistryVersion = currentVersion;

        // If orphan, log
        if (!cachedIsValid) {
            LOGGER.debug("[Area] Editor at {} has orphan reference to deleted area {}",
                worldPosition, localAreaId);
        }

        return cachedIsValid;
    }

    /**
     * Gets the linked AreaDefinition if it exists.
     * Server-side only.
     *
     * @return The area definition, or empty if orphan/null/client-side
     */
    public Optional<AreaDefinition> getLinkedArea() {
        UUID localAreaId = this.areaId;
        var localLevel = this.level;
        if (localAreaId == null || localLevel == null || localLevel.isClientSide) {
            return Optional.empty();
        }

        ServerLevel serverLevel = (ServerLevel) localLevel;
        AreaRegistry registry = AreaRegistry.get(serverLevel.getServer());
        Optional<AreaDefinition> area = registry.getArea(localAreaId);

        // Update cache based on lookup result
        cachedRegistryVersion = registry.getModificationVersion();
        cachedIsValid = area.isPresent();

        return area;
    }

    /**
     * Validates and clears orphan references.
     * Call this periodically or on block interaction.
     *
     * @return true if the reference was valid or null, false if orphan was cleared
     */
    public boolean validateAndClearOrphan() {
        if (areaId == null) {
            return true;
        }

        if (!hasValidArea()) {
            LOGGER.info("[Area] Clearing orphan reference at {} (area {} was deleted)",
                worldPosition, areaId);
            areaId = null;
            invalidateCache();
            setChanged();
            var localLevel = this.level;
            if (localLevel != null && !localLevel.isClientSide) {
                localLevel.sendBlockUpdated(Objects.requireNonNull(worldPosition), Objects.requireNonNull(getBlockState()), Objects.requireNonNull(getBlockState()), 3);
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

    @Override
    protected void saveAdditional(@Nonnull CompoundTag tag, @Nonnull HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (areaId != null) {
            tag.putUUID(TAG_AREA_ID, areaId);
        }
    }

    @Override
    protected void loadAdditional(@Nonnull CompoundTag tag, @Nonnull HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.hasUUID(TAG_AREA_ID)) {
            areaId = tag.getUUID(TAG_AREA_ID);
        } else {
            areaId = null;
        }
        // INC-01 fix: Explicitly invalidate cache after load to ensure fresh validation
        // While transient fields reset to defaults, this is more robust against refactoring
        invalidateCache();
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    @Nonnull
    public CompoundTag getUpdateTag(@Nonnull HolderLookup.Provider registries) {
        CompoundTag tag = Objects.requireNonNull(super.getUpdateTag(registries));
        if (areaId != null) {
            tag.putUUID(TAG_AREA_ID, areaId);
        }
        return tag;
    }
}
