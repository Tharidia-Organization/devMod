package com.devmod.foundry.tool.part;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;

import com.devmod.foundry.tool.material.IMaterial;
import com.devmod.foundry.tool.material.MaterialVariantId;

/** Interface for items that store a material. */
public interface IMaterialItem extends ItemLike {
    MaterialVariantId getMaterial(ItemStack stack);

    static MaterialVariantId getMaterialFromStack(ItemStack stack) {
        if (stack.getItem() instanceof IMaterialItem item) {
            return item.getMaterial(stack);
        }
        return IMaterial.UNKNOWN_ID;
    }
}
