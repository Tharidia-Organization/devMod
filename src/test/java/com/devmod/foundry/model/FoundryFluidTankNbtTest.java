package com.devmod.foundry.model;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.material.Fluids;

import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.devmod.TestBootstrap;
import com.devmod.foundry.fluid.FoundryFluidTank;
import com.devmod.foundry.quality.FoundryFluidQuality;
import com.devmod.foundry.quality.MaterialQuality;
import com.devmod.foundry.util.FoundryNbtHelper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FoundryFluidTankNbtTest {

    private static HolderLookup.Provider registries() {
        TestBootstrap.init();
        return RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
    }

    @Test
    @DisplayName("FoundryFluidTank preserves capacity and fluids after NBT round-trip")
    void preservesCapacityAndFluids() {
        HolderLookup.Provider provider = registries();
        FoundryFluidTank tank = new FoundryFluidTank(4000);
        FluidStack lava = new FluidStack(Fluids.LAVA, 1000);
        tank.fill(lava, IFluidHandler.FluidAction.EXECUTE);

        CompoundTag tag = tank.save(provider);

        FoundryFluidTank loaded = new FoundryFluidTank(1);
        loaded.load(provider, tag);

        assertEquals(4000, loaded.getCapacity());
        assertEquals(1000, loaded.getUsed());
        assertEquals(1000, loaded.getAmountForFluid(Fluids.LAVA));
        assertFalse(loaded.isEmpty());
    }

    @Test
    @DisplayName("FoundryFluidTank preserves multiple fluids and quality metadata")
    void preservesMultipleFluidsAndMetadata() {
        HolderLookup.Provider provider = registries();
        FoundryFluidTank tank = new FoundryFluidTank(8000);

        FluidStack lava = new FluidStack(Fluids.LAVA, 1200);
        FoundryFluidQuality.applyMoltenState(lava, MaterialQuality.MASTERWORK, 0.86f, 120, 900f);
        tank.fill(lava, IFluidHandler.FluidAction.EXECUTE);

        FluidStack water = new FluidStack(Fluids.WATER, 500);
        FoundryFluidQuality.applyMoltenState(water, MaterialQuality.CRUDE, 0.55f, 40, 250f);
        tank.fill(water, IFluidHandler.FluidAction.EXECUTE);

        CompoundTag tag = tank.save(provider);

        FoundryFluidTank loaded = new FoundryFluidTank(1);
        loaded.load(provider, tag);

        assertEquals(8000, loaded.getCapacity());
        assertEquals(1700, loaded.getUsed());
        assertEquals(1200, loaded.getAmountForFluid(Fluids.LAVA));
        assertEquals(500, loaded.getAmountForFluid(Fluids.WATER));

        FluidStack loadedLava = findFluid(loaded, Fluids.LAVA);
        assertFalse(loadedLava.isEmpty());
        assertEquals(MaterialQuality.MASTERWORK, FoundryFluidQuality.getQuality(loadedLava));
        assertEquals(0.86f, FoundryFluidQuality.getStoredPurity(loadedLava), 0.001f);
        assertEquals(120, FoundryFluidQuality.getOxidationTicks(loadedLava));
        assertEquals(900f, FoundryFluidQuality.getPeakTemperature(loadedLava), 0.001f);

        FluidStack loadedWater = findFluid(loaded, Fluids.WATER);
        assertFalse(loadedWater.isEmpty());
        assertEquals(MaterialQuality.CRUDE, FoundryFluidQuality.getQuality(loadedWater));
        assertEquals(0.55f, FoundryFluidQuality.getStoredPurity(loadedWater), 0.001f);
        assertEquals(40, FoundryFluidQuality.getOxidationTicks(loadedWater));
        assertEquals(250f, FoundryFluidQuality.getPeakTemperature(loadedWater), 0.001f);
    }

    @Test
    @DisplayName("FoundryNbtHelper round-trips fluid stacks and ignores empty")
    void nbtHelperRoundTripAndEmpty() {
        HolderLookup.Provider provider = registries();

        CompoundTag emptyTag = new CompoundTag();
        FoundryNbtHelper.putFluidStack(emptyTag, "Fluid", provider, FluidStack.EMPTY);
        assertFalse(emptyTag.contains("Fluid"));

        FluidStack lava = new FluidStack(Fluids.LAVA, 250);
        FoundryFluidQuality.applyMoltenState(lava, MaterialQuality.REFINED, 0.77f, 15, 700f);

        CompoundTag tag = new CompoundTag();
        FoundryNbtHelper.putFluidStack(tag, "Fluid", provider, lava);
        assertTrue(tag.contains("Fluid"));

        FluidStack read = FoundryNbtHelper.readFluidStack(tag, "Fluid", provider);
        assertFalse(read.isEmpty());
        assertEquals(250, read.getAmount());
        assertEquals(MaterialQuality.REFINED, FoundryFluidQuality.getQuality(read));
        assertEquals(0.77f, FoundryFluidQuality.getStoredPurity(read), 0.001f);
        assertEquals(15, FoundryFluidQuality.getOxidationTicks(read));
        assertEquals(700f, FoundryFluidQuality.getPeakTemperature(read), 0.001f);
    }

    private static FluidStack findFluid(FoundryFluidTank tank, net.minecraft.world.level.material.Fluid fluid) {
        for (FluidStack stack : tank.getFluids()) {
            if (stack.getFluid() == fluid) {
                return stack;
            }
        }
        return FluidStack.EMPTY;
    }
}
