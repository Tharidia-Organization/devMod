package com.devmod.transport;

import com.devmod.DevMod;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.Objects;

/**
 * Network payload for food stats synchronization.
 * Sent from client to server when applying food modifications.
 */
public record FoodStatsPayload(
    ItemStack item,
    CompoundTag statsTag,
    boolean isGlobal
) implements CustomPacketPayload {

    public static final Type<FoodStatsPayload> TYPE =
        new Type<>(Objects.requireNonNull(ResourceLocation.fromNamespaceAndPath(DevMod.MODID, "food_stats")));

    public static final StreamCodec<RegistryFriendlyByteBuf, FoodStatsPayload> STREAM_CODEC =
        StreamCodec.composite(
            Objects.requireNonNull(ItemStack.STREAM_CODEC),
            FoodStatsPayload::item,
            Objects.requireNonNull(ByteBufCodecs.COMPOUND_TAG),
            FoodStatsPayload::statsTag,
            Objects.requireNonNull(ByteBufCodecs.BOOL),
            FoodStatsPayload::isGlobal,
            (itemStack, tag, global) -> new FoodStatsPayload(itemStack, tag, global != null && global)
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
        if (o == null || getClass() != o.getClass()) return false;
        FoodStatsPayload that = (FoodStatsPayload) o;
        return isGlobal == that.isGlobal &&
               ItemStack.matches(Objects.requireNonNull(item), Objects.requireNonNull(that.item)) &&
               Objects.equals(statsTag, that.statsTag);
    }

    @Override
    public int hashCode() {
        return Objects.hash(ItemStack.hashItemAndComponents(item), statsTag, isGlobal);
    }
}
