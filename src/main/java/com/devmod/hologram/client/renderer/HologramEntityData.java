package com.devmod.hologram.client.renderer;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

import com.devmod.hologram.data.EntityFilterType;

/**
 * Lightweight snapshot of entity position data for hologram rendering.
 * Stores relative positions and pre-computed display data to minimize
 * per-frame calculations.
 *
 * @param entityId    The entity ID for retrieval from level
 * @param type        The entity type
 * @param filterType  The filter category for this entity
 * @param relativeX   X position relative to hologram origin
 * @param relativeY   Y position relative to hologram origin
 * @param relativeZ   Z position relative to hologram origin
 * @param bbWidth     Bounding box width for cube rendering
 * @param bbHeight    Bounding box height for cube rendering
 * @param color       Pre-computed color based on entity type (RGB)
 * @param isHostile   Whether this is a hostile entity
 */
public record HologramEntityData(
    int entityId,
    EntityType<?> type,
    EntityFilterType filterType,
    float relativeX,
    float relativeY,
    float relativeZ,
    float bbWidth,
    float bbHeight,
    int color,
    boolean isHostile
) {
    /**
     * Create a HologramEntityData from an entity with position relative to origin.
     *
     * @param entity  The source entity
     * @param originX X coordinate of hologram origin
     * @param originY Y coordinate of hologram origin (usually min build height)
     * @param originZ Z coordinate of hologram origin
     * @return The entity data snapshot
     */
    public static HologramEntityData fromEntity(Entity entity, double originX, double originY, double originZ) {
        EntityFilterType filterType = EntityFilterType.fromEntity(entity);
        return new HologramEntityData(
            entity.getId(),
            entity.getType(),
            filterType,
            (float) (entity.getX() - originX),
            (float) (entity.getY() - originY),
            (float) (entity.getZ() - originZ),
            entity.getBbWidth(),
            entity.getBbHeight(),
            filterType.getColor(),
            filterType == EntityFilterType.HOSTILE
        );
    }
}
