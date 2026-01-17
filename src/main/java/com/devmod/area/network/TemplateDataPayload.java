package com.devmod.area.network;

import java.util.Objects;
import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import com.devmod.DevMod;
import com.devmod.area.data.AreaDimensions;
import com.devmod.area.data.AreaGenerationType;
import com.devmod.area.data.AreaOptions;
import com.devmod.area.data.AreaPalette;
import com.devmod.area.data.AreaShape;
import com.devmod.area.data.BiomeGenerationConfig;

/**
 * Server -> Client: Full template data for loading into editor.
 * Contains all configuration needed to populate the AreaBuilderScreen.
 *
 * @param templateId     UUID of the template being loaded
 * @param generationType Type of generation (CUSTOM or BIOME)
 * @param shape          Area shape
 * @param dimensions     Area dimensions
 * @param palette        Material palette
 * @param biomeConfig    Biome config (nullable, only for BIOME generation)
 * @param options        Area options
 * @param presetId       Palette preset ID
 */
public record TemplateDataPayload(
    UUID templateId,
    AreaGenerationType generationType,
    AreaShape shape,
    AreaDimensions dimensions,
    AreaPalette palette,
    @Nullable BiomeGenerationConfig biomeConfig,
    AreaOptions options,
    String presetId
) implements CustomPacketPayload {

    public static final Type<TemplateDataPayload> TYPE =
        new Type<>(Objects.requireNonNull(
            ResourceLocation.fromNamespaceAndPath(DevMod.MODID, "template_data")));

    public static final StreamCodec<FriendlyByteBuf, TemplateDataPayload> STREAM_CODEC =
        StreamCodec.of(
            (buf, payload) -> {
                UUIDUtil.STREAM_CODEC.encode(buf, Objects.requireNonNull(payload.templateId));
                AreaGenerationType.STREAM_CODEC.encode(buf, Objects.requireNonNull(payload.generationType));
                AreaShape.STREAM_CODEC.encode(buf, Objects.requireNonNull(payload.shape));
                AreaDimensions.STREAM_CODEC.encode(buf, Objects.requireNonNull(payload.dimensions));
                AreaPalette.STREAM_CODEC.encode(buf, Objects.requireNonNull(payload.palette));
                encodeOptionalBiomeConfig(buf, payload.biomeConfig);
                AreaOptions.STREAM_CODEC.encode(buf, Objects.requireNonNull(payload.options));
                ByteBufCodecs.STRING_UTF8.encode(buf, Objects.requireNonNull(payload.presetId));
            },
            buf -> new TemplateDataPayload(
                UUIDUtil.STREAM_CODEC.decode(buf),
                AreaGenerationType.STREAM_CODEC.decode(buf),
                AreaShape.STREAM_CODEC.decode(buf),
                AreaDimensions.STREAM_CODEC.decode(buf),
                AreaPalette.STREAM_CODEC.decode(buf),
                BiomeGenerationConfig.OPTIONAL_STREAM_CODEC.decode(buf),
                AreaOptions.STREAM_CODEC.decode(buf),
                ByteBufCodecs.STRING_UTF8.decode(buf)
            )
        );

    /** Helper to encode nullable biomeConfig with explicit null handling */
    private static void encodeOptionalBiomeConfig(FriendlyByteBuf buf, @Nullable BiomeGenerationConfig config) {
        buf.writeBoolean(config != null);
        if (config != null) {
            BiomeGenerationConfig.STREAM_CODEC.encode(buf, config);
        }
    }

    @Override
    @Nonnull
    public Type<? extends CustomPacketPayload> type() {
        return Objects.requireNonNull(TYPE);
    }
}
