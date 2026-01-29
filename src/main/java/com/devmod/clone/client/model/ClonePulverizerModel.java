package com.devmod.clone.client.model;

import java.util.Objects;

import javax.annotation.Nonnull;

import net.minecraft.resources.ResourceLocation;

import com.devmod.DevMod;
import com.devmod.clone.block.entity.ClonePulverizerBlockEntity;

import software.bernie.geckolib.model.GeoModel;

/**
 * GeckoLib model for the Clone Pulverizer block.
 * Loads the Bedrock Edition .geo.json model with roller animations.
 */
public class ClonePulverizerModel extends GeoModel<ClonePulverizerBlockEntity> {

    private static final ResourceLocation MODEL = ResourceLocation.fromNamespaceAndPath(
            DevMod.MODID, "geo/block/clone_pulverizer.geo.json");
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            DevMod.MODID, "textures/block/clone/clone_pulverizer.png");
    private static final ResourceLocation ANIMATION = ResourceLocation.fromNamespaceAndPath(
            DevMod.MODID, "animations/block/clone_pulverizer.animation.json");

    @Override
    @Nonnull
    @SuppressWarnings("removal")
    public ResourceLocation getModelResource(ClonePulverizerBlockEntity entity) {
        return Objects.requireNonNull(MODEL);
    }

    @Override
    @Nonnull
    @SuppressWarnings("removal")
    public ResourceLocation getTextureResource(ClonePulverizerBlockEntity entity) {
        return Objects.requireNonNull(TEXTURE);
    }

    @Override
    @Nonnull
    public ResourceLocation getAnimationResource(ClonePulverizerBlockEntity entity) {
        return Objects.requireNonNull(ANIMATION);
    }
}
