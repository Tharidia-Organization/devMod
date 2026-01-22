package com.devmod.hologram.network;

import com.devmod.hologram.data.HologramDefinition;
import com.devmod.hologram.data.HologramOptions;
import com.devmod.hologram.data.HologramPosition;
import com.devmod.hologram.data.HologramStyle;
import com.devmod.network.PayloadSizeUtil;

final class HologramPayloadSizing {

    private HologramPayloadSizing() {}

    static long estimateDefinitionSize(HologramDefinition def) {
        long size = 16; // UUID
        size += PayloadSizeUtil.estimatedUtfSize(def.label());
        size += PayloadSizeUtil.varIntSize(def.type().ordinal());
        size += PayloadSizeUtil.varIntSize(def.lines().size());
        for (String line : def.lines()) {
            size += PayloadSizeUtil.estimatedUtfSize(line);
        }
        size += estimateStyleSize(def.style());
        size += estimatePositionSize(def.position());
        size += estimateOptionsSize(def.options());
        size += 1; // creator UUID present
        if (def.creatorUUID() != null) {
            size += 16;
        }
        size += 8; // createdAt
        size += 8; // updatedAt
        size += PayloadSizeUtil.varIntSize(def.revision());
        return size;
    }

    static long estimateStyleSize(HologramStyle style) {
        long size = 4; // textColor
        size += 4; // backgroundColor
        size += 4; // scale
        size += 4; // lineSpacing
        size += PayloadSizeUtil.varIntSize(style.billboard().ordinal());
        size += 1; // shadow
        size += 1; // seeThrough
        size += 4; // glowColor
        return size;
    }

    static long estimateOptionsSize(HologramOptions options) {
        long size = 1; // enabled
        size += 1; // locked
        size += PayloadSizeUtil.varIntSize(options.refreshInterval());
        size += PayloadSizeUtil.varIntSize(options.visibilityRange());
        size += 1; // linkedZone present
        if (options.linkedZone() != null) {
            size += PayloadSizeUtil.estimatedUtfSize(options.linkedZone());
        }
        return size;
    }

    static long estimatePositionSize(HologramPosition position) {
        long size = 8; // BlockPos
        size += 8L * 3; // offset x/y/z
        size += PayloadSizeUtil.estimatedUtfSize(position.dimension().toString());
        size += 4; // yaw
        size += 4; // pitch
        return size;
    }
}
