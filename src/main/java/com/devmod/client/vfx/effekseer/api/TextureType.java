package com.devmod.client.vfx.effekseer.api;

import Effekseer.swig.EffekseerTextureType;

public enum TextureType {
    COLOR(EffekseerTextureType.Color),
    NORMAL(EffekseerTextureType.Normal),
    DISTORTION(EffekseerTextureType.Distortion);

    private final EffekseerTextureType impl;

    TextureType(EffekseerTextureType impl) {
        this.impl = impl;
    }

    public EffekseerTextureType getImpl() {
        return impl;
    }
}
