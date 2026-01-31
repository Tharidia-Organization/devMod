package com.devmod.client.vfx.effekseer.api;

import Effekseer.swig.EffekseerCoreDeviceType;

@SuppressWarnings("ImmutableEnumChecker")
public enum DeviceType {
    UNKNOWN(EffekseerCoreDeviceType.Unknown),
    OPENGL(EffekseerCoreDeviceType.OpenGL),
    DIRECTX9(EffekseerCoreDeviceType.DirectX9),
    DIRECTX11(EffekseerCoreDeviceType.DirectX11);

    private final EffekseerCoreDeviceType impl;

    DeviceType(EffekseerCoreDeviceType impl) {
        this.impl = impl;
    }

    public static DeviceType fromNativeOrdinal(int ordinal) {
        for (DeviceType type : values()) {
            if (type.impl.swigValue() == ordinal) {
                return type;
            }
        }
        return UNKNOWN;
    }
}
