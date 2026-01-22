package com.devmod.nexus.network;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import com.devmod.DevMod;
import com.devmod.network.PayloadSizeUtil;
import com.devmod.network.PayloadValidation;
import com.devmod.nexus.data.SlotType;
import com.devmod.portal.PortalColor;

/**
 * Server -> Client: Slot list for Nexus UI.
 * Contains list of slot summaries with basic info.
 */
public record SlotListPayload(List<SlotSummary> slots) implements CustomPacketPayload, PayloadValidation.SizedPayload {

    /** Maximum slots to sync at once */
    public static final int MAX_SLOTS = 100;

    public static final Type<SlotListPayload> TYPE =
        new Type<>(Objects.requireNonNull(ResourceLocation.fromNamespaceAndPath(DevMod.MODID, "nexus_slot_list")));

    /**
     * Summary of a slot for UI display.
     */
    public record SlotSummary(
        @Nonnull String slotId,
        @Nonnull String displayName,
        @Nonnull SlotType type,
        @Nonnull PortalColor portalColor,
        boolean hasLinkedArea,
        @Nullable UUID linkedAreaId,
        @Nullable String linkedAreaName
    ) {
        public static final StreamCodec<FriendlyByteBuf, SlotSummary> STREAM_CODEC = StreamCodec.of(
            (buf, summary) -> {
                buf.writeUtf(summary.slotId);
                buf.writeUtf(summary.displayName);
                buf.writeUtf(Objects.requireNonNull(summary.type.name()));
                PortalColor.STREAM_CODEC.encode(buf, summary.portalColor);
                buf.writeBoolean(summary.hasLinkedArea);
                if (summary.hasLinkedArea && summary.linkedAreaId != null) {
                    buf.writeUUID(summary.linkedAreaId);
                    buf.writeUtf(summary.linkedAreaName != null ? summary.linkedAreaName : "");
                }
            },
            buf -> {
                String slotId = buf.readUtf();
                String displayName = buf.readUtf();
                SlotType type = SlotType.valueOf(buf.readUtf());
                PortalColor portalColor = PortalColor.STREAM_CODEC.decode(buf);
                boolean hasLinkedArea = buf.readBoolean();
                UUID linkedAreaId = null;
                String linkedAreaName = null;
                if (hasLinkedArea) {
                    linkedAreaId = buf.readUUID();
                    linkedAreaName = buf.readUtf();
                }
                return new SlotSummary(Objects.requireNonNull(slotId), Objects.requireNonNull(displayName), type, Objects.requireNonNull(portalColor), hasLinkedArea, linkedAreaId, linkedAreaName);
            }
        );
    }

    public static final StreamCodec<FriendlyByteBuf, SlotListPayload> STREAM_CODEC =
        StreamCodec.composite(
            Objects.requireNonNull(SlotSummary.STREAM_CODEC.apply(
                Objects.requireNonNull(ByteBufCodecs.list(MAX_SLOTS)))),
            SlotListPayload::slots,
            SlotListPayload::new
        );

    @Override
    @Nonnull
    public Type<? extends CustomPacketPayload> type() {
        return Objects.requireNonNull(TYPE);
    }

    @Override
    public int estimatedSize() {
        long size = PayloadSizeUtil.varIntSize(slots.size());
        for (SlotSummary summary : slots) {
            size += PayloadSizeUtil.estimatedUtfSize(summary.slotId());
            size += PayloadSizeUtil.estimatedUtfSize(summary.displayName());
            size += PayloadSizeUtil.estimatedUtfSize(summary.type().name());
            size += PayloadSizeUtil.varIntSize(summary.portalColor().getIndex());
            size += 1; // hasLinkedArea
            if (summary.hasLinkedArea() && summary.linkedAreaId() != null) {
                size += 16; // linkedAreaId
                String linkedName = summary.linkedAreaName() != null ? summary.linkedAreaName() : "";
                size += PayloadSizeUtil.estimatedUtfSize(linkedName);
            }
        }
        return PayloadSizeUtil.clampToInt(size);
    }
}
