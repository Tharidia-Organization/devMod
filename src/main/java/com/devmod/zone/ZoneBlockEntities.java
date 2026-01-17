package com.devmod.zone;

import java.util.Objects;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import com.devmod.DevMod;
import com.devmod.zone.block.entity.ZoneMarkerBlockEntity;

/**
 * Block entity registrations for the Zone Marker module.
 */
public final class ZoneBlockEntities {
    private ZoneBlockEntities() {}

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
        DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, DevMod.MODID);

    /**
     * Zone Marker block entity.
     */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ZoneMarkerBlockEntity>> ZONE_MARKER =
        BLOCK_ENTITY_TYPES.register("zone_marker", () ->
            BlockEntityType.Builder.of(
                ZoneMarkerBlockEntity::new,
                ZoneBlocks.ZONE_MARKER.get()
            ).build(null)
        );

    /**
     * Register block entity types with the event bus.
     */
    public static void register(IEventBus eventBus) {
        BLOCK_ENTITY_TYPES.register(Objects.requireNonNull(eventBus));
        DevMod.LOGGER.debug("[Zone] Zone block entities registered");
    }
}
