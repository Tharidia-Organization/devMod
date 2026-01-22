package com.devmod.hologram.network;

import java.util.Objects;

import javax.annotation.Nonnull;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import com.devmod.DevMod;
import com.devmod.hologram.data.HologramDefinition;
import com.devmod.network.PayloadSizeUtil;
import com.devmod.network.PayloadValidation;

/**
 * Server to Client payload for opening the hologram editor.
 * Sent when a player clicks on an existing hologram with the HologramPlacer.
 */
public record OpenHologramEditorPayload(
    @Nonnull HologramDefinition definition
) implements CustomPacketPayload, PayloadValidation.SizedPayload {

    public static final ResourceLocation ID = Objects.requireNonNull(
        ResourceLocation.fromNamespaceAndPath(DevMod.MODID, "hologram_editor_open"));
    public static final Type<OpenHologramEditorPayload> TYPE = new Type<>(ID);

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenHologramEditorPayload> STREAM_CODEC =
        StreamCodec.composite(
            HologramDefinition.STREAM_CODEC.cast(),
            OpenHologramEditorPayload::definition,
            OpenHologramEditorPayload::new
        );

    public OpenHologramEditorPayload {
        Objects.requireNonNull(definition, "definition");
    }

    @Override
    @Nonnull
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    @Override
    public int estimatedSize() {
        long size = HologramPayloadSizing.estimateDefinitionSize(definition);
        return PayloadSizeUtil.clampToInt(size);
    }
}
