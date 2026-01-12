package com.devmod.clone.client.model;

import javax.annotation.Nonnull;

import net.minecraft.resources.ResourceLocation;

import com.devmod.DevMod;
import com.devmod.clone.block.entity.CentrifugeBlockEntity;

import software.bernie.geckolib.model.GeoModel;

/**
 * GeckoLib model for the Centrifuge block.
 * Loads the Bedrock Edition .geo.json model with drum animations.
 */
public class CentrifugeModel extends GeoModel<CentrifugeBlockEntity> {

    private static final ResourceLocation MODEL = ResourceLocation.fromNamespaceAndPath(
            DevMod.MODID, "geo/block/clone_centrifuge_l.geo.json");
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            DevMod.MODID, "textures/block/clone/clone_centrifuge_l.png");
    private static final ResourceLocation ANIMATION = ResourceLocation.fromNamespaceAndPath(
            DevMod.MODID, "animations/block/clone_centrifuge_l.animation.json");

    @Override
    @Nonnull
    public ResourceLocation getModelResource(@Nonnull CentrifugeBlockEntity entity) {
        return MODEL;
    }

    @Override
    @Nonnull
    public ResourceLocation getTextureResource(@Nonnull CentrifugeBlockEntity entity) {
        return TEXTURE;
    }

    @Override
    @Nonnull
    public ResourceLocation getAnimationResource(@Nonnull CentrifugeBlockEntity entity) {
        return ANIMATION;
    }
}
