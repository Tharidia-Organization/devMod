package com.devmod.client.vfx.effekseer;

import java.io.File;
import java.io.IOException;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.resources.ResourceLocation;

import com.devmod.DevMod;
import com.devmod.client.vfx.effekseer.api.ParticleEmitter;
import com.devmod.client.vfx.effekseer.installer.NativeLibraryInstaller;
import com.devmod.client.vfx.effekseer.installer.NativePlatform;
import com.devmod.client.vfx.effekseer.registry.EffectRegistry;

public final class EffekseerClient {
    private static final Logger LOGGER = LoggerFactory.getLogger(EffekseerClient.class);
    private static final String DLL_NAME = "EffekseerNativeForJava";
    private static boolean initialized;

    private EffekseerClient() {
    }

    public static void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        installNativeLibrary();
    }

    public static ParticleEmitter play(ResourceLocation effect) {
        return play(effect, ParticleEmitter.Type.WORLD);
    }

    public static ParticleEmitter play(ResourceLocation effect, ParticleEmitter.Type type) {
        return Optional.ofNullable(EffectRegistry.get(effect))
            .map(def -> def.play(type))
            .orElseGet(() -> ParticleEmitter.dummy(type));
    }

    public static ParticleEmitter play(ResourceLocation effect, ParticleEmitter.Type type, ResourceLocation emitterName) {
        return Optional.ofNullable(EffectRegistry.get(effect))
            .map(def -> def.play(type, emitterName))
            .orElseGet(() -> ParticleEmitter.dummy(type));
    }

    private static void installNativeLibrary() {
        NativePlatform platform = NativePlatform.current();
        if (platform.isUnsupported()) {
            LOGGER.warn("[Effekseer] Unsupported platform {}, effects disabled", platform);
            return;
        }
        File dll = platform.getNativeInstallPath(DLL_NAME);
        String resource = "assets/%s/%s".formatted(DevMod.MODID, platform.formatFileName(DLL_NAME));
        try {
            boolean updated = NativeLibraryInstaller.installOrUpdate(resource, dll);
            if (updated) {
                LOGGER.info("[Effekseer] Installed native library at {}", dll.getCanonicalPath());
            } else {
                LOGGER.info("[Effekseer] Loading native library at {}", dll.getCanonicalPath());
            }
            System.load(dll.getCanonicalPath());
        } catch (IOException | UnsatisfiedLinkError e) {
            throw new ExceptionInInitializerError(e);
        }
    }
}
