package com.devmod.clone.client.model;

import javax.annotation.Nonnull;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

import com.devmod.DevMod;
import com.devmod.clone.item.CloneMachineBlockItem;

import software.bernie.geckolib.model.GeoModel;

/**
 * GeckoLib model for Clone machine block items.
 * Resolves model, texture, and animation based on the item registry name.
 */
public class CloneMachineItemModel extends GeoModel<CloneMachineBlockItem> {

    private static final String FALLBACK_MODEL = "clone_pulverizer";

    private String getMachineName(@Nonnull CloneMachineBlockItem item) {
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(item);
        if (itemId != null && itemId.getNamespace().equals(DevMod.MODID)) {
            return itemId.getPath();
        }
        return FALLBACK_MODEL;
    }

    // GeoModel declares getModelResource(T)/getTextureResource(T) both abstract and
    // deprecated in GeckoLib 4.9: the two-arg overloads delegate to these, so they must
    // still be implemented and the warning cannot be avoided by overriding anything else.
    @SuppressWarnings("deprecation")
    @Override
    @Nonnull
    public ResourceLocation getModelResource(@Nonnull CloneMachineBlockItem item) {
        String machineName = getMachineName(item);
        return ResourceLocation.fromNamespaceAndPath(DevMod.MODID, "geo/block/" + machineName + ".geo.json");
    }

    // GeoModel declares getModelResource(T)/getTextureResource(T) both abstract and
    // deprecated in GeckoLib 4.9: the two-arg overloads delegate to these, so they must
    // still be implemented and the warning cannot be avoided by overriding anything else.
    @SuppressWarnings("deprecation")
    @Override
    @Nonnull
    public ResourceLocation getTextureResource(@Nonnull CloneMachineBlockItem item) {
        String machineName = getMachineName(item);
        return ResourceLocation.fromNamespaceAndPath(DevMod.MODID, "textures/block/clone/" + machineName + ".png");
    }

    @Override
    @Nonnull
    public ResourceLocation getAnimationResource(@Nonnull CloneMachineBlockItem item) {
        String machineName = getMachineName(item);
        return ResourceLocation.fromNamespaceAndPath(DevMod.MODID, "animations/block/" + machineName + ".animation.json");
    }
}
