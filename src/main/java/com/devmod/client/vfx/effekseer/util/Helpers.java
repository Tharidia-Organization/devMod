package com.devmod.client.vfx.effekseer.util;

import java.util.function.Supplier;

import com.devmod.client.vfx.effekseer.installer.NativePlatform;

public final class Helpers {
    private Helpers() {
    }

    public static <T> T checkPlatform(Supplier<T> constructor) {
        if (NativePlatform.isRunningOnUnsupportedPlatform()) {
            throw new UnsupportedOperationException("Unsupported platform");
        }
        return constructor.get();
    }
}
