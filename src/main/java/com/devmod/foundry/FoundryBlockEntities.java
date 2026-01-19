package com.devmod.foundry;

import java.util.Objects;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import com.devmod.DevMod;
import com.devmod.foundry.block.entity.FoundryCastingBasinBlockEntity;
import com.devmod.foundry.block.entity.FoundryCastingTableBlockEntity;
import com.devmod.foundry.block.entity.FoundryControllerBlockEntity;
import com.devmod.foundry.block.entity.FoundryDrainBlockEntity;
import com.devmod.foundry.block.entity.FoundryFaucetBlockEntity;
import com.devmod.foundry.block.entity.FoundryTankBlockEntity;

/**
 * Foundry module block entity registrations.
 */
public final class FoundryBlockEntities {
    private FoundryBlockEntities() {}

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
        DeferredRegister.create(Objects.requireNonNull(Registries.BLOCK_ENTITY_TYPE), DevMod.MODID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FoundryControllerBlockEntity>> FOUNDRY_CONTROLLER =
        BLOCK_ENTITIES.register("foundry_controller",
            () -> BlockEntityType.Builder.of(FoundryControllerBlockEntity::new,
                Objects.requireNonNull(FoundryBlocks.FOUNDRY_CONTROLLER.get())).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FoundryDrainBlockEntity>> FOUNDRY_DRAIN =
        BLOCK_ENTITIES.register("foundry_drain",
            () -> BlockEntityType.Builder.of(FoundryDrainBlockEntity::new,
                Objects.requireNonNull(FoundryBlocks.FOUNDRY_DRAIN.get())).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FoundryTankBlockEntity>> FOUNDRY_TANK =
        BLOCK_ENTITIES.register("foundry_tank",
            () -> BlockEntityType.Builder.of(FoundryTankBlockEntity::new,
                Objects.requireNonNull(FoundryBlocks.FOUNDRY_TANK.get())).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FoundryFaucetBlockEntity>> FOUNDRY_FAUCET =
        BLOCK_ENTITIES.register("foundry_faucet",
            () -> BlockEntityType.Builder.of(FoundryFaucetBlockEntity::new,
                Objects.requireNonNull(FoundryBlocks.FOUNDRY_FAUCET.get())).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FoundryCastingTableBlockEntity>> FOUNDRY_CASTING_TABLE =
        BLOCK_ENTITIES.register("foundry_casting_table",
            () -> BlockEntityType.Builder.of(FoundryCastingTableBlockEntity::new,
                Objects.requireNonNull(FoundryBlocks.FOUNDRY_CASTING_TABLE.get())).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FoundryCastingBasinBlockEntity>> FOUNDRY_CASTING_BASIN =
        BLOCK_ENTITIES.register("foundry_casting_basin",
            () -> BlockEntityType.Builder.of(FoundryCastingBasinBlockEntity::new,
                Objects.requireNonNull(FoundryBlocks.FOUNDRY_CASTING_BASIN.get())).build(null));

    public static void register(IEventBus modEventBus) {
        BLOCK_ENTITIES.register(Objects.requireNonNull(modEventBus));
        DevMod.LOGGER.info("[Foundry] Block entities registered");
    }
}
