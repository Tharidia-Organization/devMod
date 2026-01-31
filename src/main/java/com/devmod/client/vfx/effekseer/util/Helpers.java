package com.devmod.client.vfx.effekseer.util;

import java.util.function.Supplier;

import com.devmod.client.vfx.effekseer.EffekseerClient;
import com.devmod.client.vfx.effekseer.installer.NativePlatform;

public final class Helpers {
    private Helpers() {
    }

    public static <T> T checkPlatform(Supplier<T> constructor) {
        if (NativePlatform.isRunningOnUnsupportedPlatform()) {
            throw new UnsupportedOperationException("Unsupported platform");
        }
        if (!EffekseerClient.isAvailable()) {
            throw new UnsupportedOperationException("Effekseer native library not available");
        }
        return constructor.get();
    }
}
