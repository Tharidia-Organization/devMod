package com.devmod.network;

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
 * Typed armor stats payload (v2) that mirrors WeaponStatsPayloadV2.
 * Carries the serialized stats tag plus the target slot for specific applications.
 */
public record ArmorStatsPayloadV2(
    @Nonnull ItemStack item,
    @Nonnull CompoundTag statsTag,
    boolean isGlobal,
    int slot // -1 when global or unspecified; 0-3 for HEAD/CHEST/LEGS/FEET
) implements CustomPacketPayload {

    public static final Type<ArmorStatsPayloadV2> TYPE = new Type<>(
        Objects.requireNonNull(ResourceLocation.fromNamespaceAndPath("devmod", "armor_stats_v2"))
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, ArmorStatsPayloadV2> STREAM_CODEC = StreamCodec.of(
        (buffer, val) -> {
            ItemStack.STREAM_CODEC.encode(buffer, val.item());
            ByteBufCodecs.COMPOUND_TAG.encode(buffer, val.statsTag());
            ByteBufCodecs.BOOL.encode(buffer, val.isGlobal());
            buffer.writeVarInt(val.slot());
        },
        buffer -> new ArmorStatsPayloadV2(
            Objects.requireNonNull(ItemStack.STREAM_CODEC.decode(buffer)),
            Objects.requireNonNull(ByteBufCodecs.COMPOUND_TAG.decode(buffer)),
            ByteBufCodecs.BOOL.decode(buffer),
            buffer.readVarInt()
        )
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
