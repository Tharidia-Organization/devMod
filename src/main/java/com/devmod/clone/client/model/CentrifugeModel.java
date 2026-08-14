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
            DevMod.MODID, "geo/block/centrifuge.geo.json");
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            DevMod.MODID, "textures/block/clone/centrifuge.png");
    private static final ResourceLocation ANIMATION = ResourceLocation.fromNamespaceAndPath(
            DevMod.MODID, "animations/block/centrifuge.animation.json");

    // GeoModel declares getModelResource(T)/getTextureResource(T) both abstract and
    // deprecated in GeckoLib 4.9: the two-arg overloads delegate to these, so they must
    // still be implemented and the warning cannot be avoided by overriding anything else.
    @SuppressWarnings("deprecation")
    @Override
    @Nonnull
    public ResourceLocation getModelResource(@Nonnull CentrifugeBlockEntity entity) {
        return MODEL;
    }

    // GeoModel declares getModelResource(T)/getTextureResource(T) both abstract and
    // deprecated in GeckoLib 4.9: the two-arg overloads delegate to these, so they must
    // still be implemented and the warning cannot be avoided by overriding anything else.
    @SuppressWarnings("deprecation")
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
