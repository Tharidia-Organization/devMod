package com.devmod.foundry.tool.nbt;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import com.devmod.foundry.tool.FoundryToolData;
import com.devmod.foundry.tool.material.IMaterial;
import com.devmod.foundry.tool.material.MaterialVariantId;

/**
 * Minimal material list wrapper for model rendering.
 */
public final class MaterialIdNBT {
    public static final MaterialIdNBT EMPTY = new MaterialIdNBT(List.of());

    private final List<MaterialVariantId> materials;

    private MaterialIdNBT(List<MaterialVariantId> materials) {
        this.materials = List.copyOf(materials);
    }

    public static MaterialIdNBT from(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return EMPTY;
        }
        return FoundryToolData.fromStack(stack)
            .map(data -> fromMaterialIds(data.materials()))
            .orElse(EMPTY);
    }

    public static MaterialIdNBT fromMaterialIds(List<ResourceLocation> materialIds) {
        if (materialIds == null || materialIds.isEmpty()) {
            return EMPTY;
        }
        List<MaterialVariantId> variants = new ArrayList<>(materialIds.size());
        for (ResourceLocation id : materialIds) {
            if (id != null) {
                variants.add(MaterialVariantId.create(id, ""));
            }
        }
        if (variants.isEmpty()) {
            return EMPTY;
        }
        return new MaterialIdNBT(variants);
    }

    public List<MaterialVariantId> getMaterials() {
        return materials;
    }

    public MaterialVariantId getMaterial(int index) {
        if (index < 0 || index >= materials.size()) {
            return IMaterial.UNKNOWN_ID;
        }
        return materials.get(index);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof MaterialIdNBT other)) {
            return false;
        }
        return materials.equals(other.materials);
    }

    @Override
    public int hashCode() {
        return materials.hashCode();
    }

    @Override
    public String toString() {
        return "MaterialIdNBT" + materials;
    }
}
