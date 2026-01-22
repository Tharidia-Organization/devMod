package com.devmod.foundry.model;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.material.Fluids;

import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.devmod.foundry.fluid.FoundryFluidTank;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class FoundryFluidTankNbtTest {

    private static HolderLookup.Provider registries() {
        Bootstrap.bootStrap();
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
}
