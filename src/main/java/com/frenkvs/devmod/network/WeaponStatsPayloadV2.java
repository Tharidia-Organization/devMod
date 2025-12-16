package com.frenkvs.devmod.network;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nonnull;
import java.util.Objects;

/**
 * Typed weapon stats payload (v2) carrying the serialized stats tag.
 * Keeps legacy compatibility by still using a CompoundTag, but with a distinct packet id.
 */
public record WeaponStatsPayloadV2(
    @Nonnull ItemStack item,
    @Nonnull CompoundTag statsTag,
    boolean isGlobal
) implements CustomPacketPayload {

    public static final Type<WeaponStatsPayloadV2> TYPE = new Type<>(
        Objects.requireNonNull(ResourceLocation.fromNamespaceAndPath("devmod", "weapon_stats_v2"))
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, WeaponStatsPayloadV2> STREAM_CODEC = StreamCodec.of(
        (buffer, val) -> {
            ItemStack.STREAM_CODEC.encode(buffer, val.item());
            ByteBufCodecs.COMPOUND_TAG.encode(buffer, val.statsTag());
            ByteBufCodecs.BOOL.encode(buffer, val.isGlobal());
        },
        buffer -> new WeaponStatsPayloadV2(
            Objects.requireNonNull(ItemStack.STREAM_CODEC.decode(buffer)),
            Objects.requireNonNull(ByteBufCodecs.COMPOUND_TAG.decode(buffer)),
            ByteBufCodecs.BOOL.decode(buffer)
        )
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
