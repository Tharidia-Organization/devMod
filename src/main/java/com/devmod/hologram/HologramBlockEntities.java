package com.devmod.hologram;

import java.util.Objects;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import com.devmod.DevMod;
import com.devmod.hologram.block.entity.HologramProjectorBlockEntity;

/**
 * Block entity type registrations for the Hologram module.
 */
public final class HologramBlockEntities {
    private HologramBlockEntities() {}

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
        DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, DevMod.MODID);

    /**
     * The hologram projector block entity.
     * Stores scan settings and transient mesh/VBO data.
     */
    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<HologramProjectorBlockEntity>> HOLOGRAM_PROJECTOR =
        BLOCK_ENTITY_TYPES.register("hologram_projector", () ->
            BlockEntityType.Builder.of(
                HologramProjectorBlockEntity::new,
                HologramBlocks.HOLOGRAM_PROJECTOR.get()
            ).build(null)
        );

    /**
     * Register block entity types on the mod event bus.
     * Called from DevMod constructor.
     */
    public static void register(IEventBus eventBus) {
        BLOCK_ENTITY_TYPES.register(Objects.requireNonNull(eventBus, "eventBus"));
        DevMod.LOGGER.debug("[Hologram] Block entity types registered");
    }
}
