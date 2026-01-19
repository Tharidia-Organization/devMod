package com.devmod.foundry.recipe;

import java.util.Objects;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

import net.neoforged.neoforge.fluids.FluidStack;

/**
 * Shared codecs for foundry recipes.
 */
public final class FoundryCodecs {
    private FoundryCodecs() {}

    public static final Codec<FluidStack> FLUID_STACK_CODEC = RecordCodecBuilder.create(instance ->
        instance.group(
            ResourceLocation.CODEC.fieldOf("fluid")
                .forGetter(stack -> Objects.requireNonNull(BuiltInRegistries.FLUID.getKey(stack.getFluid()))),
            Codec.INT.fieldOf("amount").forGetter(FluidStack::getAmount)
        ).apply(instance, (id, amount) -> new FluidStack(BuiltInRegistries.FLUID.get(id), amount))
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, FluidStack> FLUID_STACK_STREAM_CODEC =
        StreamCodec.composite(
            ResourceLocation.STREAM_CODEC, stack -> Objects.requireNonNull(BuiltInRegistries.FLUID.getKey(stack.getFluid())),
            ByteBufCodecs.INT, FluidStack::getAmount,
            (id, amount) -> new FluidStack(BuiltInRegistries.FLUID.get(id), amount)
        );
}
