package com.devmod.foundry;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
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
import com.devmod.shared.SharedColorTokens;

/**
 * Foundry molten fluid registrations.
 */
public final class FoundryFluids {
    private FoundryFluids() {}

    public static final DeferredRegister<FluidType> FLUID_TYPES =
        DeferredRegister.create(Objects.requireNonNull(NeoForgeRegistries.FLUID_TYPES), DevMod.MODID);

    public static final DeferredRegister<net.minecraft.world.level.material.Fluid> FLUIDS =
        DeferredRegister.create(Objects.requireNonNull(Registries.FLUID), DevMod.MODID);

    public static final MoltenFluid MOLTEN_IRON = registerMolten("molten_iron", SharedColorTokens.Foundry.Fluids.MOLTEN_IRON, 10, false);
    public static final MoltenFluid MOLTEN_GOLD = registerMolten("molten_gold", SharedColorTokens.Foundry.Fluids.MOLTEN_GOLD, 10, false);
    public static final MoltenFluid MOLTEN_COPPER = registerMolten("molten_copper", SharedColorTokens.Foundry.Fluids.MOLTEN_COPPER, 9, false);
    public static final MoltenFluid MOLTEN_TIN = registerMolten("molten_tin", SharedColorTokens.Foundry.Fluids.MOLTEN_TIN, 7, true);
    public static final MoltenFluid MOLTEN_BRONZE = registerMolten("molten_bronze", SharedColorTokens.Foundry.Fluids.MOLTEN_BRONZE, 10, true);
    public static final MoltenFluid MOLTEN_STEEL = registerMolten("molten_steel", SharedColorTokens.Foundry.Fluids.MOLTEN_STEEL, 12, true);
    public static final MoltenFluid MOLTEN_COBALT = registerMolten("molten_cobalt", SharedColorTokens.Foundry.Fluids.MOLTEN_COBALT, 14, true);
    public static final MoltenFluid MOLTEN_MANYULLYN = registerMolten("molten_manyullyn", SharedColorTokens.Foundry.Fluids.MOLTEN_MANYULLYN, 15, true);
    public static final MoltenFluid MOLTEN_LEAD = registerMolten("molten_lead", SharedColorTokens.Foundry.Fluids.MOLTEN_LEAD, 8, true);
    public static final MoltenFluid MOLTEN_SILVER = registerMolten("molten_silver", SharedColorTokens.Foundry.Fluids.MOLTEN_SILVER, 11, true);
    public static final MoltenFluid MOLTEN_NICKEL = registerMolten("molten_nickel", SharedColorTokens.Foundry.Fluids.MOLTEN_NICKEL, 10, true);
    public static final MoltenFluid MOLTEN_ELECTRUM = registerMolten("molten_electrum", SharedColorTokens.Foundry.Fluids.MOLTEN_ELECTRUM, 12, true);
    public static final MoltenFluid MOLTEN_INVAR = registerMolten("molten_invar", SharedColorTokens.Foundry.Fluids.MOLTEN_INVAR, 11, true);
    public static final MoltenFluid MOLTEN_ARDITE = registerMolten("molten_ardite", SharedColorTokens.Foundry.Fluids.MOLTEN_ARDITE, 15, true);
    public static final MoltenFluid MOLTEN_NETHERITE = registerMolten("molten_netherite", SharedColorTokens.Foundry.Fluids.MOLTEN_NETHERITE, 15, false);
    public static final MoltenFluid MOLTEN_VOID_METAL = registerMolten("molten_void_metal", SharedColorTokens.Foundry.Fluids.MOLTEN_VOID_METAL, 10, true);

    public static void init(IEventBus modEventBus) {
        FLUID_TYPES.register(Objects.requireNonNull(modEventBus));
        FLUIDS.register(Objects.requireNonNull(modEventBus));
    }

    private static MoltenFluid registerMolten(String name, int tintColor, int lightLevel, boolean customTextures) {
        ResourceLocation stillTexture = customTextures ? moltenTexture(name, true) : null;
        ResourceLocation flowingTexture = customTextures ? moltenTexture(name, false) : null;
        DeferredHolder<FluidType, FluidType> type = FLUID_TYPES.register(
            name,
            () -> new MoltenFluidType(tintColor, lightLevel, stillTexture, flowingTexture)
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

    private static ResourceLocation moltenTexture(String name, boolean still) {
        String suffix = still ? "_still" : "_flow";
        return ResourceLocation.fromNamespaceAndPath(DevMod.MODID, "block/fluid/" + name + suffix);
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
