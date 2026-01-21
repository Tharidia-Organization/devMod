package com.devmod.foundry.client.model;

import net.neoforged.neoforge.client.model.data.ModelProperty;
import net.neoforged.neoforge.fluids.FluidStack;

import com.devmod.foundry.tool.material.MaterialVariantId;
import com.devmod.foundry.tool.nbt.MaterialIdNBT;

/** Model data properties used for Foundry-style models. */
public final class FoundryModelProperties {
    private FoundryModelProperties() {}

    public static final ModelProperty<FluidStack> FLUID_STACK = new ModelProperty<>();
    public static final ModelProperty<Integer> TANK_CAPACITY = new ModelProperty<>();
    public static final ModelProperty<MaterialVariantId> MATERIAL = new ModelProperty<>();
    public static final ModelProperty<MaterialIdNBT> MATERIALS = new ModelProperty<>();
}
