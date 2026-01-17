package com.devmod.clone.item;

import java.util.function.Consumer;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

import com.devmod.clone.client.renderer.CloneMachineItemRenderer;

import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.client.GeoRenderProvider;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Block item that renders Clone machine models via GeckoLib.
 */
public class CloneMachineBlockItem extends BlockItem implements GeoItem {

    @SuppressWarnings("this-escape")
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    @SuppressWarnings("this-escape")
    public CloneMachineBlockItem(Block block, Item.Properties properties) {
        super(block, properties);
        GeoItem.registerSyncedAnimatable(this);
    }

    @Override
    public void registerControllers(@Nonnull AnimatableManager.ControllerRegistrar controllers) {
        // Items use the static pose from the .geo model by default.
    }

    @Override
    @Nonnull
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    @Override
    public void createGeoRenderer(Consumer<GeoRenderProvider> consumer) {
        consumer.accept(new GeoRenderProvider() {
            @Nullable private CloneMachineItemRenderer renderer;

            @Override
            @Nullable
            public BlockEntityWithoutLevelRenderer getGeoItemRenderer() {
                if (renderer == null) {
                    renderer = new CloneMachineItemRenderer();
                }
                return renderer;
            }
        });
    }
}
