package com.devmod.foundry.fluid;

import java.util.function.Consumer;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;

import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.common.SoundActions;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.fluids.FluidType.Properties;

/**
 * Simple molten fluid type using lava textures with tinting.
 */
public final class MoltenFluidType extends FluidType {
    private static final ResourceLocation LAVA_STILL = ResourceLocation.withDefaultNamespace("block/lava_still");
    private static final ResourceLocation LAVA_FLOW = ResourceLocation.withDefaultNamespace("block/lava_flow");

    private final int tintColor;

    public MoltenFluidType(int tintColor, int lightLevel) {
        super(Properties.create()
            .density(2000)
            .viscosity(10000)
            .temperature(1000)
            .lightLevel(lightLevel)
            .sound(SoundActions.BUCKET_FILL, SoundEvents.BUCKET_FILL_LAVA)
            .sound(SoundActions.BUCKET_EMPTY, SoundEvents.BUCKET_EMPTY_LAVA)
        );
        this.tintColor = tintColor;
    }

    @Override
    public void initializeClient(Consumer<IClientFluidTypeExtensions> consumer) {
        consumer.accept(new IClientFluidTypeExtensions() {
            @Override
            public ResourceLocation getStillTexture() {
                return LAVA_STILL;
            }

            @Override
            public ResourceLocation getFlowingTexture() {
                return LAVA_FLOW;
            }

            @Override
            public int getTintColor() {
                return tintColor;
            }
        });
    }
}
