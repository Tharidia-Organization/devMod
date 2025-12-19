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
 * Network payload for fuel stats synchronization.
 * Sent from client to server when applying fuel modifications.
 */
public record FuelStatsPayload(
    ItemStack item,
    CompoundTag statsTag,
    boolean isGlobal
) implements CustomPacketPayload {

    public static final Type<FuelStatsPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(DevMod.MODID, "fuel_stats"));

    public static final StreamCodec<RegistryFriendlyByteBuf, FuelStatsPayload> STREAM_CODEC =
        StreamCodec.composite(
            ItemStack.STREAM_CODEC,
            FuelStatsPayload::item,
            ByteBufCodecs.COMPOUND_TAG,
            FuelStatsPayload::statsTag,
            ByteBufCodecs.BOOL,
            FuelStatsPayload::isGlobal,
            FuelStatsPayload::new
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
        FuelStatsPayload that = (FuelStatsPayload) o;
        return isGlobal == that.isGlobal &&
               ItemStack.matches(item, that.item) &&
               Objects.equals(statsTag, that.statsTag);
    }

    @Override
    public int hashCode() {
        return Objects.hash(ItemStack.hashItemAndComponents(item), statsTag, isGlobal);
    }
}
