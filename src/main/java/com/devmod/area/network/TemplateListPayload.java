package com.devmod.area.network;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import javax.annotation.Nonnull;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import com.devmod.DevMod;
import com.devmod.area.data.AreaShape;
import com.devmod.network.PayloadSizeUtil;
import com.devmod.network.PayloadValidation;
/**
 * Server -> Client: Template list for template selection UI.
 * Contains list of template summaries with basic info.
 */
public record TemplateListPayload(List<TemplateSummary> templates) implements CustomPacketPayload, PayloadValidation.SizedPayload {

    /** Maximum templates to sync at once */
    public static final int MAX_TEMPLATES = 200;

    public static final Type<TemplateListPayload> TYPE =
        new Type<>(Objects.requireNonNull(ResourceLocation.fromNamespaceAndPath(DevMod.MODID, "template_list")));

    /**
     * Summary of a template for UI display.
     *
     * @param id          Template UUID
     * @param name        Display name
     * @param description Short description
     * @param author      Creator name
     * @param shape       Area shape type
     * @param createdAt   Creation timestamp
     */
    public record TemplateSummary(
        UUID id,
        String name,
        String description,
        String author,
        AreaShape shape,
        long createdAt
    ) {
        public static final StreamCodec<FriendlyByteBuf, TemplateSummary> STREAM_CODEC =
            StreamCodec.composite(
                Objects.requireNonNull(UUIDUtil.STREAM_CODEC), TemplateSummary::id,
                Objects.requireNonNull(ByteBufCodecs.STRING_UTF8), TemplateSummary::name,
                Objects.requireNonNull(ByteBufCodecs.STRING_UTF8), TemplateSummary::description,
                Objects.requireNonNull(ByteBufCodecs.STRING_UTF8), TemplateSummary::author,
                Objects.requireNonNull(AreaShape.STREAM_CODEC), TemplateSummary::shape,
                Objects.requireNonNull(ByteBufCodecs.VAR_LONG), TemplateSummary::createdAt,
                TemplateSummary::create
            );

        /** Factory method to handle Long -> long conversion for StreamCodec */
        private static TemplateSummary create(UUID id, String name, String description,
                                               String author, AreaShape shape, Long createdAt) {
            return new TemplateSummary(id, name, description, author, shape,
                createdAt != null ? createdAt.longValue() : 0L);
        }
    }

    public static final StreamCodec<FriendlyByteBuf, TemplateListPayload> STREAM_CODEC =
        StreamCodec.composite(
            Objects.requireNonNull(TemplateSummary.STREAM_CODEC.apply(
                Objects.requireNonNull(ByteBufCodecs.list(MAX_TEMPLATES)))),
            TemplateListPayload::templates,
            TemplateListPayload::new
        );

    @Override
    @Nonnull
    public Type<? extends CustomPacketPayload> type() {
        return Objects.requireNonNull(TYPE);
    }

    @Override
    public int estimatedSize() {
        long size = PayloadSizeUtil.varIntSize(templates.size());
        for (TemplateSummary summary : templates) {
            size += 16; // UUID
            size += PayloadSizeUtil.estimatedUtfSize(summary.name());
            size += PayloadSizeUtil.estimatedUtfSize(summary.description());
            size += PayloadSizeUtil.estimatedUtfSize(summary.author());
            size += PayloadSizeUtil.varIntSize(summary.shape().ordinal());
            size += PayloadSizeUtil.varLongSize(summary.createdAt());
        }
        return PayloadSizeUtil.clampToInt(size);
    }
}
