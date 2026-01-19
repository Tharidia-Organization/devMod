package com.devmod.foundry;

import java.util.Objects;

import java.util.concurrent.atomic.AtomicReference;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import com.devmod.DevMod;
import com.devmod.foundry.fluid.MoltenFluidType;

/**
 * Foundry molten fluid registrations.
 */
public final class FoundryFluids {
    private FoundryFluids() {}

    public static final DeferredRegister<FluidType> FLUID_TYPES =
        DeferredRegister.create(Objects.requireNonNull(NeoForgeRegistries.FLUID_TYPES), DevMod.MODID);

    public static final DeferredRegister<net.minecraft.world.level.material.Fluid> FLUIDS =
        DeferredRegister.create(Objects.requireNonNull(Registries.FLUID), DevMod.MODID);

    public static final MoltenFluid MOLTEN_IRON = registerMolten("molten_iron", 0xFFCD8B7A, 10);
    public static final MoltenFluid MOLTEN_GOLD = registerMolten("molten_gold", 0xFFF9D65C, 10);
    public static final MoltenFluid MOLTEN_COPPER = registerMolten("molten_copper", 0xFFD97B52, 9);
    public static final MoltenFluid MOLTEN_TIN = registerMolten("molten_tin", 0xFFB9C7D1, 7);
    public static final MoltenFluid MOLTEN_BRONZE = registerMolten("molten_bronze", 0xFFCF8B3A, 10);

    public static void init(IEventBus modEventBus) {
        FLUID_TYPES.register(Objects.requireNonNull(modEventBus));
        FLUIDS.register(Objects.requireNonNull(modEventBus));
    }

    private static MoltenFluid registerMolten(String name, int tintColor, int lightLevel) {
        DeferredHolder<FluidType, FluidType> type = FLUID_TYPES.register(
            name,
            () -> new MoltenFluidType(tintColor, lightLevel)
        );

        AtomicReference<BaseFlowingFluid.Properties> propsRef = new AtomicReference<>();

        DeferredHolder<net.minecraft.world.level.material.Fluid, BaseFlowingFluid.Source> source =
            FLUIDS.register(name, () -> new BaseFlowingFluid.Source(Objects.requireNonNull(propsRef.get())));
        DeferredHolder<net.minecraft.world.level.material.Fluid, BaseFlowingFluid.Flowing> flowing =
            FLUIDS.register("flowing_" + name, () -> new BaseFlowingFluid.Flowing(Objects.requireNonNull(propsRef.get())));

        DeferredHolder<Block, LiquidBlock> block = DevMod.BLOCKS.register(
            name,
            () -> new LiquidBlock(Objects.requireNonNull(source.get()),
                BlockBehaviour.Properties.of().noCollission().strength(100.0F).noLootTable())
        );

        DeferredHolder<Item, BucketItem> bucket = DevMod.ITEMS.register(
            name + "_bucket",
            () -> new BucketItem(Objects.requireNonNull(source.get()),
                new Item.Properties().craftRemainder(Items.BUCKET).stacksTo(1))
        );

        BaseFlowingFluid.Properties properties = new BaseFlowingFluid.Properties(type, source, flowing)
            .bucket(bucket)
            .block(block)
            .slopeFindDistance(2)
            .levelDecreasePerBlock(2)
            .tickRate(15)
            .explosionResistance(100.0F);
        propsRef.set(properties);

        return new MoltenFluid(type, source, flowing, block, bucket, properties);
    }

    public record MoltenFluid(
        DeferredHolder<FluidType, FluidType> type,
        DeferredHolder<net.minecraft.world.level.material.Fluid, BaseFlowingFluid.Source> source,
        DeferredHolder<net.minecraft.world.level.material.Fluid, BaseFlowingFluid.Flowing> flowing,
        DeferredHolder<Block, LiquidBlock> block,
        DeferredHolder<Item, BucketItem> bucket,
        BaseFlowingFluid.Properties properties
    ) {}
}
