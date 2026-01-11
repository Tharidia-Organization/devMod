package com.devmod.clone;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;

import com.mojang.datafixers.types.Type;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import com.devmod.DevMod;
import com.devmod.clone.block.entity.CentrifugeBlockEntity;
import com.devmod.clone.block.entity.CloneMachineBlockEntity;
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

    /**
     * Empty map used to return null via Map.get() - standard null-returning pattern.
     * Minecraft's API is annotated @Nonnull but accepts null by design (no data fixer needed for mods).
     */
    private static final Map<String, Type<?>> EMPTY_TYPE_MAP = Collections.emptyMap();

    /**
     * Returns null Type for build() via Map.get() on missing key.
     */
    private static Type<?> noDataFixer() {
        return EMPTY_TYPE_MAP.get("");
    }

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
        DeferredRegister.create(Objects.requireNonNull(Registries.BLOCK_ENTITY_TYPE), DevMod.MODID);

    /**
     * The telepad block entity.
     * Handles charging and teleportation logic.
     */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TelepadBlockEntity>> TELEPAD =
        BLOCK_ENTITY_TYPES.register("telepad", () ->
            BlockEntityType.Builder.of(
                TelepadBlockEntity::new,
                CloneBlocks.TELEPAD.get()
            ).build(noDataFixer())
        );

    /**
     * The imprinter block entity.
     * Handles automatic entity scanning.
     */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ImprinterBlockEntity>> IMPRINTER =
        BLOCK_ENTITY_TYPES.register("imprinter", () ->
            BlockEntityType.Builder.of(
                ImprinterBlockEntity::new,
                CloneBlocks.IMPRINTER.get()
            ).build(noDataFixer())
        );

    /**
     * The neurocell block entity.
     * Handles cloning process and data preparation.
     */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<NeurocellBlockEntity>> NEUROCELL =
        BLOCK_ENTITY_TYPES.register("neurocell", () ->
            BlockEntityType.Builder.of(
                NeurocellBlockEntity::new,
                CloneBlocks.NEUROCELL.get()
            ).build(noDataFixer())
        );

    /**
     * The reformer block entity.
     * Handles entity spawning from clone data.
     */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ReformerBlockEntity>> REFORMER =
        BLOCK_ENTITY_TYPES.register("reformer", () ->
            BlockEntityType.Builder.of(
                ReformerBlockEntity::new,
                CloneBlocks.REFORMER.get()
            ).build(noDataFixer())
        );

    /**
     * The large neurocell block entity (3x3x3).
     * Handles cloning for larger entities.
     */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<NeurocellLBlockEntity>> NEUROCELL_L =
        BLOCK_ENTITY_TYPES.register("neurocell_l", () ->
            BlockEntityType.Builder.of(
                NeurocellLBlockEntity::new,
                CloneBlocks.NEUROCELL_L.get()
            ).build(noDataFixer())
        );

    /**
     * The centrifuge block entity.
     * Handles automatic crafting processing.
     */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CentrifugeBlockEntity>> CENTRIFUGE =
        BLOCK_ENTITY_TYPES.register("centrifuge", () ->
            BlockEntityType.Builder.of(
                CentrifugeBlockEntity::new,
                CloneBlocks.CENTRIFUGE.get()
            ).build(noDataFixer())
        );

    /**
     * GeckoLib animated block entity for all Clone machine blocks.
     * Supports Oritech-style models with unlimited rotations and animations.
     */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CloneMachineBlockEntity>> CLONE_MACHINE =
        BLOCK_ENTITY_TYPES.register("clone_machine", () ->
            BlockEntityType.Builder.of(
                CloneMachineBlockEntity::new,
                CloneBlocks.CLONE_PULVERIZER.get(),
                CloneBlocks.CLONE_FOUNDRY.get(),
                CloneBlocks.CLONE_PUMP.get(),
                CloneBlocks.CLONE_STEAM_ENGINE.get(),
                CloneBlocks.CLONE_LAVA_GENERATOR.get(),
                CloneBlocks.CLONE_REFINERY.get(),
                CloneBlocks.CLONE_SHRINKER.get(),
                CloneBlocks.CLONE_TREEFELLER.get(),
                CloneBlocks.CLONE_TECH_DOOR.get(),
                CloneBlocks.CLONE_BIO_GENERATOR.get(),
                CloneBlocks.CLONE_ASSEMBLER.get(),
                CloneBlocks.CLONE_CENTRIFUGE_L.get(),
                CloneBlocks.CLONE_FUEL_GENERATOR.get(),
                CloneBlocks.CLONE_ATOMIC_FORGE.get(),
                CloneBlocks.CLONE_LASER_ARM.get(),
                CloneBlocks.CLONE_DRILL.get(),
                CloneBlocks.CLONE_FERTILIZER.get(),
                CloneBlocks.CLONE_CRUSHER.get(),
                CloneBlocks.CLONE_SMELTER.get(),
                CloneBlocks.CLONE_STORAGE_UNIT.get(),
                CloneBlocks.CLONE_ENERGY_PIPE.get(),
                CloneBlocks.CLONE_TANK.get(),
                CloneBlocks.CLONE_BATTERY.get(),
                CloneBlocks.CLONE_SOLAR_PANEL.get(),
                CloneBlocks.CLONE_MOTOR.get(),
                CloneBlocks.CLONE_CONVEYOR.get(),
                CloneBlocks.CLONE_REACTOR.get(),
                CloneBlocks.CLONE_PROCESSOR.get(),
                CloneBlocks.CLONE_CHARGER.get()
            ).build(noDataFixer())
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
