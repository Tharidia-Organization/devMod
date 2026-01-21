package com.devmod.foundry.tool.material;

import net.minecraft.resources.ResourceLocation;

import com.devmod.DevMod;

/**
 * Marker interface for material constants used by the foundry model system.
 */
public interface IMaterial {
    ResourceLocation UNKNOWN_MATERIAL_ID = ResourceLocation.fromNamespaceAndPath(DevMod.MODID, "unknown");
    MaterialVariantId UNKNOWN_ID = MaterialVariantId.create(UNKNOWN_MATERIAL_ID, "");
}
