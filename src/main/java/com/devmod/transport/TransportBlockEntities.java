package com.devmod.transport;

import java.util.Objects;

import javax.annotation.Nullable;

import com.mojang.datafixers.types.Type;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import com.devmod.DevMod;
import com.devmod.transport.block.entity.TransportCoreBlockEntity;

/**
 * Block entity type registrations for the Transport module.
 * Registers BlockEntity types for the Warp Core system.
 */
public final class TransportBlockEntities {
    private TransportBlockEntities() {}

    /**
     * Returns null Type for build() when no data fixer is needed.
     */
    @Nullable
    private static Type<?> noDataFixer() {
        return null;
    }

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
        DeferredRegister.create(Objects.requireNonNull(Registries.BLOCK_ENTITY_TYPE), DevMod.MODID);

    /**
     * The Warp Core block entity.
     * Handles transport node configuration, module detection, and player charging.
     */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TransportCoreBlockEntity>> WARP_CORE =
        BLOCK_ENTITY_TYPES.register("warp_core", () ->
            BlockEntityType.Builder.of(
                TransportCoreBlockEntity::new,
                TransportBlocks.WARP_CORE.get()
            ).build(noDataFixer())
        );

    /**
     * Register block entity types on the mod event bus.
     * Called from DevMod constructor.
     */
    public static void register(IEventBus eventBus) {
        BLOCK_ENTITY_TYPES.register(Objects.requireNonNull(eventBus, "eventBus"));
        DevMod.LOGGER.debug("[Transport] Block entity types registered");
    }
}
