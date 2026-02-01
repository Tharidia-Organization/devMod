package com.devmod.clone.client.model;


import javax.annotation.Nonnull;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

import com.devmod.DevMod;
import com.devmod.clone.block.entity.CloneMachineBlockEntity;

import software.bernie.geckolib.model.GeoModel;

/**
 * GeckoLib model for Clone machine blocks.
 * Dynamically loads the correct Bedrock Edition .geo.json model based on block type.
 */
public class CloneMachineModel extends GeoModel<CloneMachineBlockEntity> {

    // Fallback model name if block type cannot be determined
    private static final String FALLBACK_MODEL = "clone_pulverizer";

    public CloneMachineModel() {
    }

    /**
     * Gets the machine name from the block entity's block type.
     */
    private String getMachineName(@Nonnull CloneMachineBlockEntity entity) {
        ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(entity.getBlockState().getBlock());
        if (blockId != null && blockId.getNamespace().equals(DevMod.MODID)) {
            return blockId.getPath();
        }
        return FALLBACK_MODEL;
    }

    @Override
    @Nonnull
    public ResourceLocation getModelResource(@Nonnull CloneMachineBlockEntity entity) {
        String machineName = getMachineName(entity);
        return ResourceLocation.fromNamespaceAndPath(DevMod.MODID, "geo/block/" + machineName + ".geo.json");
    }

    @Override
    @Nonnull
    public ResourceLocation getTextureResource(@Nonnull CloneMachineBlockEntity entity) {
        String machineName = getMachineName(entity);
        return ResourceLocation.fromNamespaceAndPath(DevMod.MODID, "textures/block/clone/" + machineName + ".png");
    }

    @Override
    @Nonnull
    public ResourceLocation getAnimationResource(@Nonnull CloneMachineBlockEntity entity) {
        String machineName = getMachineName(entity);
        return ResourceLocation.fromNamespaceAndPath(DevMod.MODID, "animations/block/" + machineName + ".animation.json");
    }
}
