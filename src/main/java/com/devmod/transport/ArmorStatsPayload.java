package com.devmod.transport;

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
 * Client-to-server payload for armor stats changes.
 * Sends armor stat modifications to the server for application.
 *
 * @see EDITOR_DESIGN_SYSTEM.md Section 4.2
 */
public record ArmorStatsPayload(
    @Nonnull ItemStack item,
    @Nonnull CompoundTag statsTag,
    boolean isGlobal
) implements CustomPacketPayload {

    public static final Type<ArmorStatsPayload> TYPE = new Type<>(
        Objects.requireNonNull(ResourceLocation.fromNamespaceAndPath("devmod", "armor_stats"))
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, ArmorStatsPayload> STREAM_CODEC = StreamCodec.of(
        (buffer, val) -> {
            ItemStack.STREAM_CODEC.encode(buffer, val.item());
            ByteBufCodecs.COMPOUND_TAG.encode(buffer, val.statsTag());
            ByteBufCodecs.BOOL.encode(buffer, val.isGlobal());
        },
        buffer -> new ArmorStatsPayload(
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
