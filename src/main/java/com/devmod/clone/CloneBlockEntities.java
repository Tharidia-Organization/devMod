package com.devmod.clone;

import java.util.Objects;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import com.devmod.DevMod;
import com.devmod.clone.block.entity.ImprinterBlockEntity;
import com.devmod.clone.block.entity.NeurocellBlockEntity;
import com.devmod.clone.block.entity.NeurocellLBlockEntity;
import com.devmod.clone.block.entity.ReformerBlockEntity;
import com.devmod.clone.block.entity.TelepadBlockEntity;

/**
 * Block entity type registrations for the Clone module.
 */
public final class CloneBlockEntities {
    private CloneBlockEntities() {}

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
        DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, DevMod.MODID);

    /**
     * The telepad block entity.
     * Handles charging and teleportation logic.
     */
    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TelepadBlockEntity>> TELEPAD =
        BLOCK_ENTITY_TYPES.register("telepad", () ->
            BlockEntityType.Builder.of(
                TelepadBlockEntity::new,
                CloneBlocks.TELEPAD.get()
            ).build(null)
        );

    /**
     * The imprinter block entity.
     * Handles automatic entity scanning.
     */
    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ImprinterBlockEntity>> IMPRINTER =
        BLOCK_ENTITY_TYPES.register("imprinter", () ->
            BlockEntityType.Builder.of(
                ImprinterBlockEntity::new,
                CloneBlocks.IMPRINTER.get()
            ).build(null)
        );

    /**
     * The neurocell block entity.
     * Handles cloning process and data preparation.
     */
    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<NeurocellBlockEntity>> NEUROCELL =
        BLOCK_ENTITY_TYPES.register("neurocell", () ->
            BlockEntityType.Builder.of(
                NeurocellBlockEntity::new,
                CloneBlocks.NEUROCELL.get()
            ).build(null)
        );

    /**
     * The reformer block entity.
     * Handles entity spawning from clone data.
     */
    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ReformerBlockEntity>> REFORMER =
        BLOCK_ENTITY_TYPES.register("reformer", () ->
            BlockEntityType.Builder.of(
                ReformerBlockEntity::new,
                CloneBlocks.REFORMER.get()
            ).build(null)
        );

    /**
     * The large neurocell block entity (3x3x3).
     * Handles cloning for larger entities.
     */
    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<NeurocellLBlockEntity>> NEUROCELL_L =
        BLOCK_ENTITY_TYPES.register("neurocell_l", () ->
            BlockEntityType.Builder.of(
                NeurocellLBlockEntity::new,
                CloneBlocks.NEUROCELL_L.get()
            ).build(null)
        );

    /**
     * Register block entity types on the mod event bus.
     * Called from DevMod constructor.
     */
    public static void register(IEventBus eventBus) {
        BLOCK_ENTITY_TYPES.register(Objects.requireNonNull(eventBus, "eventBus"));
        DevMod.LOGGER.debug("[Clone] Block entity types registered");
    }
}
