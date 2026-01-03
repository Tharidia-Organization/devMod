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

/**
 * Network payload for syncing armor stats via CompoundTag.
 * Uses NBT serialization for flexible stat storage.
 *
 * <p><b>CRITICAL:</b> The record field order MUST match the encode/decode order in STREAM_CODEC.
 * If fields are reordered, added, or removed, update the codec accordingly.</p>
 *
 * <p>Field order: item, statsTag, isGlobal, slot</p>
 */
public record ArmorStatsPayload(
    @Nonnull ItemStack item,
    @Nonnull CompoundTag statsTag,
    boolean isGlobal,
    int slot // -1 when global or unspecified; 0-3 for HEAD/CHEST/LEGS/FEET
) implements CustomPacketPayload, PayloadValidation.SizedPayload {

    public static final Type<ArmorStatsPayload> TYPE = new Type<>(
        Objects.requireNonNull(ResourceLocation.fromNamespaceAndPath("devmod", "armor_stats_v2"))
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, ArmorStatsPayload> STREAM_CODEC = StreamCodec.of(
        (buffer, val) -> {
            ItemStack.STREAM_CODEC.encode(buffer, val.item());
            ByteBufCodecs.COMPOUND_TAG.encode(buffer, val.statsTag());
            ByteBufCodecs.BOOL.encode(buffer, val.isGlobal());
            buffer.writeVarInt(val.slot());
        },
        buffer -> new ArmorStatsPayload(
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

    @Override
    public int estimatedSize() {
        // ItemStack (~100-500 bytes) + CompoundTag (~50-200 bytes) + bool + VarInt
        return 256 + statsTag.sizeInBytes();
    }
}
