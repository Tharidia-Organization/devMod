package com.devmod.client.vfx.effekseer.registry;

import java.util.Collection;
import java.util.Map;
import java.util.function.BiConsumer;

import javax.annotation.Nullable;

import net.minecraft.resources.ResourceLocation;

import com.devmod.client.vfx.effekseer.api.EffekseerManager;
import com.devmod.client.vfx.effekseer.api.ParticleEmitter;
import com.devmod.client.vfx.effekseer.loader.EffekAssetLoader;

public final class EffectRegistry {
    private EffectRegistry() {
    }

    @Nullable
    public static EffectDefinition get(ResourceLocation id) {
        EffekAssetLoader loader = EffekAssetLoader.get();
        return loader == null ? null : loader.get(id);
    }

    public static Collection<Map.Entry<ResourceLocation, EffectDefinition>> entries() {
        EffekAssetLoader loader = EffekAssetLoader.get();
        return loader == null ? java.util.List.of() : loader.entries();
    }

    public static void clearAllPlaying() {
        entries().stream()
            .map(Map.Entry::getValue)
            .flatMap(EffectDefinition::emitters)
            .forEach(ParticleEmitter::stop);

        entries().stream()
            .map(Map.Entry::getValue)
            .flatMap(EffectDefinition::managers)
            .forEach(EffekseerManager::stopAllEffects);
    }

    public static void forEach(BiConsumer<ResourceLocation, EffectDefinition> action) {
        EffekAssetLoader loader = EffekAssetLoader.get();
        if (loader != null) {
            loader.forEach(action);
        }
    }
}
