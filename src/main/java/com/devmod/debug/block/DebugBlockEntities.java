package com.devmod.debug.block;

import java.util.Objects;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import com.devmod.DevMod;
import com.devmod.debug.block.entity.EntityScannerBlockEntity;

/**
 * Block entity type registrations for the Debug module.
 */
public final class DebugBlockEntities {
    private DebugBlockEntities() {}

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
        DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, DevMod.MODID);

    /**
     * The entity scanner block entity.
     * Scans nearby entities and stores their debug data.
     */
    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<EntityScannerBlockEntity>> ENTITY_SCANNER =
        BLOCK_ENTITY_TYPES.register("entity_scanner", () ->
            BlockEntityType.Builder.of(
                EntityScannerBlockEntity::new,
                DebugBlocks.ENTITY_SCANNER.get()
            ).build(null)
        );

    /**
     * Register block entity types on the mod event bus.
     * Called from DevMod constructor.
     */
    public static void register(IEventBus eventBus) {
        BLOCK_ENTITY_TYPES.register(Objects.requireNonNull(eventBus, "eventBus"));
        DevMod.LOGGER.debug("[Debug] Block entity types registered");
    }
}
