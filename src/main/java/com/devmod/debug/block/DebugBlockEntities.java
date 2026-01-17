package com.devmod.debug.block;

import java.util.Objects;

import javax.annotation.Nonnull;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.types.Type;

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
        DeferredRegister.create(Objects.requireNonNull(Registries.BLOCK_ENTITY_TYPE, "BLOCK_ENTITY_TYPE registry key"), DevMod.MODID);

    /**
     * The entity scanner block entity.
     * Scans nearby entities and stores their debug data.
     */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<EntityScannerBlockEntity>> ENTITY_SCANNER =
        BLOCK_ENTITY_TYPES.register("entity_scanner", () ->
            BlockEntityType.Builder.of(
                EntityScannerBlockEntity::new,
                DebugBlocks.ENTITY_SCANNER.get()
            ).build(noDataFixer())
        );

    /**
     * Returns a no-op data fixer type.
     * Uses DSL.remainderType() which is the standard pattern for mods without data fixers.
     * This returns a valid non-null Type that performs no data fixing.
     */
    @Nonnull
    private static Type<?> noDataFixer() {
        return Objects.requireNonNull(DSL.remainderType());
    }

    /**
     * Register block entity types on the mod event bus.
     * Called from DevMod constructor.
     */
    public static void register(IEventBus eventBus) {
        BLOCK_ENTITY_TYPES.register(Objects.requireNonNull(eventBus, "eventBus"));
        DevMod.LOGGER.debug("[Debug] Block entity types registered");
    }
}
