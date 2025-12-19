package com.frenkvs.devmod.network;

import com.frenkvs.devmod.DevMod;
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
        new Type<>(ResourceLocation.fromNamespaceAndPath(DevMod.MODID, "food_stats"));

    public static final StreamCodec<RegistryFriendlyByteBuf, FoodStatsPayload> STREAM_CODEC =
        StreamCodec.composite(
            ItemStack.STREAM_CODEC,
            FoodStatsPayload::item,
            ByteBufCodecs.COMPOUND_TAG,
            FoodStatsPayload::statsTag,
            ByteBufCodecs.BOOL,
            FoodStatsPayload::isGlobal,
            FoodStatsPayload::new
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
               ItemStack.matches(item, that.item) &&
               Objects.equals(statsTag, that.statsTag);
    }

    @Override
    public int hashCode() {
        return Objects.hash(ItemStack.hashItemAndComponents(item), statsTag, isGlobal);
    }
}
