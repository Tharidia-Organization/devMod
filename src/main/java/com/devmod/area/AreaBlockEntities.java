package com.devmod.area;

import java.util.Objects;

import com.mojang.datafixers.DSL;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import com.devmod.DevMod;
import com.devmod.area.block.entity.AreaEditorBlockEntity;
import com.devmod.area.block.entity.NexusEditorCentralBlockEntity;

/**
 * Block entity registrations for the Area module.
 */
public final class AreaBlockEntities {
    private AreaBlockEntities() {}

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
        DeferredRegister.create(Objects.requireNonNull(Registries.BLOCK_ENTITY_TYPE), DevMod.MODID);

    /**
     * Area Editor block entity.
     */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AreaEditorBlockEntity>> AREA_EDITOR =
        BLOCK_ENTITY_TYPES.register("area_editor", () ->
            BlockEntityType.Builder.of(
                AreaEditorBlockEntity::new,
                AreaBlocks.AREA_EDITOR.get()
            ).build(Objects.requireNonNull(DSL.remainderType()))
        );

    /**
     * Nexus Editor Central block entity.
     */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<NexusEditorCentralBlockEntity>> NEXUS_EDITOR_CENTRAL =
        BLOCK_ENTITY_TYPES.register("nexus_editor_central", () ->
            BlockEntityType.Builder.of(
                NexusEditorCentralBlockEntity::new,
                AreaBlocks.NEXUS_EDITOR_CENTRAL.get()
            ).build(Objects.requireNonNull(DSL.remainderType()))
        );

    /**
     * Register block entity types with the event bus.
     */
    public static void register(IEventBus eventBus) {
        BLOCK_ENTITY_TYPES.register(Objects.requireNonNull(eventBus));
        DevMod.LOGGER.debug("[Area] Area block entities registered");
    }
}
