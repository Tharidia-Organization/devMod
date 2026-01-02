package com.devmod.network;

import java.util.Objects;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import com.devmod.DevMod;

/**
 * Network payload for syncing fuel stats via CompoundTag.
 * Uses NBT serialization for burn time and fuel efficiency.
 *
 * <p><b>CRITICAL:</b> The record field order MUST match the codec field order.
 * Uses StreamCodec.composite() - field order defined by method reference order.</p>
 *
 * <p>Field order: item, statsTag, isGlobal</p>
 */
public record FuelStatsPayload(
    ItemStack item,
    CompoundTag statsTag,
    boolean isGlobal
) implements CustomPacketPayload, PayloadValidation.SizedPayload {

    public static final Type<FuelStatsPayload> TYPE =
        new Type<>(Objects.requireNonNull(ResourceLocation.fromNamespaceAndPath(DevMod.MODID, "fuel_stats")));

    public static final StreamCodec<RegistryFriendlyByteBuf, FuelStatsPayload> STREAM_CODEC =
        StreamCodec.composite(
            Objects.requireNonNull(ItemStack.STREAM_CODEC),
            FuelStatsPayload::item,
            Objects.requireNonNull(ByteBufCodecs.COMPOUND_TAG),
            FuelStatsPayload::statsTag,
            Objects.requireNonNull(ByteBufCodecs.BOOL),
            FuelStatsPayload::isGlobal,
            (itemStack, tag, global) -> new FuelStatsPayload(itemStack, tag, global != null && global)
        );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * Validate payload data.
     */
    public boolean isValid() {
        return item != null && !item.isEmpty() && statsTag != null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FuelStatsPayload that)) return false;
        return isGlobal == that.isGlobal &&
               ItemStack.matches(Objects.requireNonNull(item), Objects.requireNonNull(that.item)) &&
               Objects.equals(statsTag, that.statsTag);
    }

    @Override
    public int hashCode() {
        return Objects.hash(ItemStack.hashItemAndComponents(item), statsTag, isGlobal);
    }

    @Override
    public int estimatedSize() {
        return 256 + (statsTag != null ? statsTag.sizeInBytes() : 0);
    }
}
