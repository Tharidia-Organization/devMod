package com.devmod.client.vfx.effekseer.api;

import Effekseer.swig.EffekseerBackendCore;

import com.devmod.client.vfx.effekseer.util.Helpers;

public class Effekseer extends SafeFinalized<EffekseerBackendCore> {
    public Effekseer() {
        this(Helpers.checkPlatform(EffekseerBackendCore::new));
    }

    public static boolean init() {
        return EffekseerBackendCore.InitializeWithOpenGL();
    }

    public static void terminate() {
        EffekseerBackendCore.Terminate();
    }

    public static DeviceType getDeviceType() {
        return DeviceType.fromNativeOrdinal(EffekseerBackendCore.GetDevice().swigValue());
    }

    public final EffekseerBackendCore getImpl() {
        return impl;
    }

    protected Effekseer(EffekseerBackendCore impl) {
        super(impl, EffekseerBackendCore::delete);
        this.impl = impl;
    }

    private final EffekseerBackendCore impl;
}
