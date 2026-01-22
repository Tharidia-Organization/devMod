package com.devmod.area.network;

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
import com.devmod.network.PayloadSizeUtil;
import com.devmod.network.PayloadValidation;

/**
 * Client -> Server: Request to save current area configuration as a template.
 *
 * @param sourceAreaId The area ID to create template from
 * @param templateName Name for the new template
 * @param description  Description of the template
 */
public record SaveAreaTemplatePayload(
    UUID sourceAreaId,
    String templateName,
    String description
) implements CustomPacketPayload, PayloadValidation.SizedPayload {

    /** Maximum template name length */
    public static final int MAX_NAME_LENGTH = 64;
    /** Maximum description length */
    public static final int MAX_DESCRIPTION_LENGTH = 256;

    public static final Type<SaveAreaTemplatePayload> TYPE =
        new Type<>(Objects.requireNonNull(ResourceLocation.fromNamespaceAndPath(DevMod.MODID, "save_area_template")));

    public static final StreamCodec<FriendlyByteBuf, SaveAreaTemplatePayload> STREAM_CODEC =
        StreamCodec.composite(
            Objects.requireNonNull(UUIDUtil.STREAM_CODEC), SaveAreaTemplatePayload::sourceAreaId,
            Objects.requireNonNull(ByteBufCodecs.STRING_UTF8), SaveAreaTemplatePayload::templateName,
            Objects.requireNonNull(ByteBufCodecs.STRING_UTF8), SaveAreaTemplatePayload::description,
            SaveAreaTemplatePayload::new
        );

    @Override
    @Nonnull
    public Type<? extends CustomPacketPayload> type() {
        return Objects.requireNonNull(TYPE);
    }

    @Override
    public int estimatedSize() {
        long size = 16; // UUID
        size += PayloadSizeUtil.estimatedUtfSize(templateName);
        size += PayloadSizeUtil.estimatedUtfSize(description);
        return PayloadSizeUtil.clampToInt(size);
    }

    /**
     * Validates the payload data.
     * M-06 fix: Description must not be blank to make templates easier to identify.
     */
    public boolean isValid() {
        return sourceAreaId != null
            && templateName != null
            && !templateName.isBlank()
            && templateName.length() <= MAX_NAME_LENGTH
            && description != null
            // M-06 fix: Require non-blank description for better template usability
            && !description.isBlank()
            && description.length() <= MAX_DESCRIPTION_LENGTH;
    }
}
