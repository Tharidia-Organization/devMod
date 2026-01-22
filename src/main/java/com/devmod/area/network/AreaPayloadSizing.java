package com.devmod.area.network;

import java.util.Map;

import com.devmod.area.data.AreaDimensions;
import com.devmod.area.data.AreaOptions;
import com.devmod.area.data.AreaPalette;
import com.devmod.area.data.BiomeGenerationConfig;
import com.devmod.network.PayloadSizeUtil;

final class AreaPayloadSizing {

    private AreaPayloadSizing() {}

    static long estimateDimensionsSize(AreaDimensions dimensions) {
        long size = PayloadSizeUtil.varIntSize(dimensions.width());
        size += PayloadSizeUtil.varIntSize(dimensions.length());
        size += PayloadSizeUtil.varIntSize(dimensions.height());
        size += PayloadSizeUtil.varIntSize(dimensions.floorY());
        return size;
    }

    static long estimatePaletteSize(AreaPalette palette) {
        long size = PayloadSizeUtil.varIntSize(palette.materials().size());
        for (Map.Entry<String, String> entry : palette.materials().entrySet()) {
            size += PayloadSizeUtil.estimatedUtfSize(entry.getKey());
            size += PayloadSizeUtil.estimatedUtfSize(entry.getValue());
        }
        size += PayloadSizeUtil.estimatedUtfSize(palette.presetId());
        size += 1; // customized
        return size;
    }

    static long estimateOptionsSize(AreaOptions options) {
        long size = 1; // hasWalls
        size += 1; // hasCeiling
        size += 1; // hasFloor
        size += PayloadSizeUtil.varIntSize(options.wallStyle().ordinal());
        size += 1; // spawnPortals
        size += 1; // allowBuilding
        size += 1; // linkedZoneId present
        if (options.linkedZoneId() != null) {
            size += PayloadSizeUtil.estimatedUtfSize(options.linkedZoneId());
        }
        return size;
    }

    static long estimateBiomeConfigSize(BiomeGenerationConfig config) {
        long size = PayloadSizeUtil.estimatedUtfSize(config.biomeId().toString());
        size += PayloadSizeUtil.varLongSize(config.seed());
        size += 1; // generateStructures
        size += 1; // generateFeatures
        size += 1; // generateOres
        size += PayloadSizeUtil.varIntSize(config.seaLevel());
        size += PayloadSizeUtil.varIntSize(config.terrainStyle().ordinal());
        return size;
    }
}
