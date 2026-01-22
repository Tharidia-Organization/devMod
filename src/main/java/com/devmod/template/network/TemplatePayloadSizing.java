package com.devmod.template.network;

import java.util.Map;

import com.devmod.network.PayloadSizeUtil;
import com.devmod.template.data.ComponentPlacement;
import com.devmod.template.data.RelativePosition;
import com.devmod.template.data.RoomTemplate;

final class TemplatePayloadSizing {

    private TemplatePayloadSizing() {}

    static long estimateRoomTemplateSize(RoomTemplate template) {
        long size = 16; // UUID
        size += PayloadSizeUtil.estimatedUtfSize(template.templateId());
        size += PayloadSizeUtil.estimatedUtfSize(template.displayName());
        size += PayloadSizeUtil.estimatedUtfSize(template.zoneId());

        size += PayloadSizeUtil.varIntSize(template.components().size());
        for (ComponentPlacement placement : template.components()) {
            size += estimateComponentPlacementSize(placement);
        }

        size += PayloadSizeUtil.varIntSize(template.materialOverrides().size());
        for (Map.Entry<String, String> entry : template.materialOverrides().entrySet()) {
            size += PayloadSizeUtil.estimatedUtfSize(entry.getKey());
            size += PayloadSizeUtil.estimatedUtfSize(entry.getValue());
        }

        size += PayloadSizeUtil.varIntSize(template.minWidth());
        size += PayloadSizeUtil.varIntSize(template.minDepth());
        size += 1; // isCustom
        size += 8; // createdAt
        size += PayloadSizeUtil.varIntSize(template.revision());
        return size;
    }

    private static long estimateComponentPlacementSize(ComponentPlacement placement) {
        long size = PayloadSizeUtil.estimatedUtfSize(placement.componentId());
        size += estimateRelativePositionSize(placement.position());
        size += PayloadSizeUtil.varIntSize(placement.facing().get3DDataValue());
        size += PayloadSizeUtil.varIntSize(placement.params().size());
        for (Map.Entry<String, String> entry : placement.params().entrySet()) {
            size += PayloadSizeUtil.estimatedUtfSize(entry.getKey());
            size += PayloadSizeUtil.estimatedUtfSize(entry.getValue());
        }
        return size;
    }

    private static long estimateRelativePositionSize(RelativePosition position) {
        long size = PayloadSizeUtil.estimatedUtfSize(position.anchor().getSerializedName());
        size += PayloadSizeUtil.varIntSize(position.offsetX());
        size += PayloadSizeUtil.varIntSize(position.offsetY());
        size += PayloadSizeUtil.varIntSize(position.offsetZ());
        return size;
    }
}
