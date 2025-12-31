package com.devmod.network;

import java.util.Objects;

import javax.annotation.Nonnull;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public record RangedWeaponStatsPayload(
    @Nonnull ItemStack item,
    @Nonnull CompoundTag statsTag,
    boolean isGlobal
) implements CustomPacketPayload, PayloadValidation.SizedPayload {

    public static final Type<RangedWeaponStatsPayload> TYPE = new Type<>(
        Objects.requireNonNull(ResourceLocation.fromNamespaceAndPath("devmod", "ranged_weapon_stats"))
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, RangedWeaponStatsPayload> STREAM_CODEC = StreamCodec.of(
        (buffer, val) -> {
            ItemStack.STREAM_CODEC.encode(buffer, val.item());
            ByteBufCodecs.COMPOUND_TAG.encode(buffer, val.statsTag());
            ByteBufCodecs.BOOL.encode(buffer, val.isGlobal());
        },
        buffer -> new RangedWeaponStatsPayload(
            Objects.requireNonNull(ItemStack.STREAM_CODEC.decode(buffer)),
            Objects.requireNonNull(ByteBufCodecs.COMPOUND_TAG.decode(buffer)),
            ByteBufCodecs.BOOL.decode(buffer)
        )
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    @Override
    public int estimatedSize() {
        // ItemStack (~100-500 bytes) + CompoundTag (~50-200 bytes) + bool
        // Conservative estimate for NBT-heavy payloads
        return 256 + (statsTag != null ? statsTag.sizeInBytes() : 0);
    }
}
