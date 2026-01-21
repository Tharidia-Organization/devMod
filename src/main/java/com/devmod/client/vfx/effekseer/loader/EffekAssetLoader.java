package com.devmod.client.vfx.effekseer.loader;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.IntFunction;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import com.devmod.client.vfx.effekseer.api.EffekseerEffect;
import com.devmod.client.vfx.effekseer.api.TextureType;
import com.devmod.client.vfx.effekseer.EffekseerClient;
import com.devmod.client.vfx.effekseer.installer.NativePlatform;
import com.devmod.client.vfx.effekseer.registry.EffectDefinition;
import com.devmod.client.vfx.effekseer.render.EffekRenderer;
import com.devmod.client.vfx.effekseer.render.RenderUtil;
import javax.annotation.Nullable;

public class EffekAssetLoader extends SimplePreparableReloadListener<EffekAssetLoader.Preparations> {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static EffekAssetLoader INSTANCE;

    public static EffekAssetLoader get() {
        return INSTANCE;
    }

    @Nullable
    public EffectDefinition get(ResourceLocation id) {
        return loadedEffects.get(id);
    }

    public Set<Map.Entry<ResourceLocation, EffectDefinition>> entries() {
        return loadedEffects.entrySet();
    }

    public void forEach(BiConsumer<ResourceLocation, EffectDefinition> action) {
        loadedEffects.forEach(action);
    }

    private Optional<EffekseerEffect> loadEffect(ResourceManager manager, ResourceLocation name, Resource efkefc) {
        try (var input = efkefc.open()) {
            EffekseerEffect effect = new EffekseerEffect();
            boolean success = effect.load(input, 1);
            if (!success) {
                LOGGER.error("[Effekseer] Failed to load {}", name);
                return Optional.empty();
            }

            try {
                for (TextureType texType : TextureType.values()) {
                    int count = effect.textureCount(texType);
                    load(manager, name, count, i -> effect.getTexturePath(i, texType),
                        (b, len, i) -> effect.loadTexture(b, len, i, texType));
                }
                load(manager, name, effect.modelCount(), effect::getModelPath, effect::loadModel);
                load(manager, name, effect.curveCount(), effect::getCurvePath, effect::loadCurve);
                load(manager, name, effect.materialCount(), effect::getMaterialPath, effect::loadMaterial);
                return Optional.of(effect);
            } catch (FileNotFoundException ex) {
                LOGGER.error("[Effekseer] Failed to load {}", name, ex);
                effect.close();
                return Optional.empty();
            }
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    private void load(
            ResourceManager manager,
            ResourceLocation name, int count,
            IntFunction<String> pathGetter,
            TriConsumer<byte[]> loadMethod
    ) throws IOException {
        String modid = name.getNamespace();
        for (int i = 0; i < count; i++) {
            String effekAssetPath = pathGetter.apply(i);
            String normalizedPath = effekAssetPath.replace('\\', '/');
            String normalizedLower = normalizedPath.toLowerCase(Locale.ROOT);
            String mcAssetPath = (Path.of("effeks", name.getPath()).getParent() + "/" + normalizedLower)
                .replace('\\', '/')
                .replace("//", "/");
            String fallbackMcAssetPath = ("effeks/" + name.getPath() + "/" + normalizedLower)
                .replace('\\', '/')
                .replace("//", "/");

            ResourceLocation main = ResourceLocation.fromNamespaceAndPath(modid, mcAssetPath);
            ResourceLocation fallback = ResourceLocation.fromNamespaceAndPath(modid, fallbackMcAssetPath);
            Resource resource = getResourceOrUseFallbackPath(manager, main, fallback)
                .orElseThrow(() -> new FileNotFoundException("Failed to load %s or %s".formatted(main, fallback)));
            try (var input = resource.open()) {
                byte[] data = input.readAllBytes();
                boolean success = loadMethod.accept(data, data.length, i);
                if (!success) {
                    String info = "Failed to load effek data %s".formatted(effekAssetPath);
                    LOGGER.debug("{}; asset path {}", info, mcAssetPath);
                    throw new EffekLoadException(info);
                }
            }
        }
    }

    private static Optional<Resource> getResourceOrUseFallbackPath(ResourceManager manager,
            ResourceLocation path, ResourceLocation fallback) {
        return manager.getResource(path).or(() -> manager.getResource(fallback));
    }

    protected static class Preparations {
        private final Map<ResourceLocation, EffectDefinition> loadedEffects = new LinkedHashMap<>();
    }

    private void unloadAll() {
        loadedEffects.values().forEach(EffectDefinition::close);
        loadedEffects.clear();
    }

    @Override
    protected Preparations prepare(ResourceManager manager, ProfilerFiller profiler) {
        return new Preparations();
    }

    private static ResourceLocation createEffekName(ResourceLocation location) {
        String filePath = location.getPath();
        if (filePath.startsWith("effeks/")) {
            filePath = filePath.substring("effeks/".length());
        }
        if (filePath.endsWith(".efkefc") || filePath.endsWith(".efkpkg")) {
            filePath = filePath.substring(0, filePath.length() - ".efkefc".length());
        }
        return ResourceLocation.fromNamespaceAndPath(location.getNamespace(), filePath);
    }

    @Override
    protected void apply(Preparations prep, ResourceManager manager, ProfilerFiller profiler) {
        INSTANCE = this;
        EffekseerClient.init();
        EffekRenderer.init();
        if (NativePlatform.isRunningOnUnsupportedPlatform()) {
            unloadAll();
            return;
        }
        unloadAll();
        RenderUtil.refreshBackgroundFrameBuffer();
        RenderUtil.runPixelStoreCodeHealthily(() -> {
            manager.listResources("effeks", rl -> rl.getPath().endsWith(".efkefc") || rl.getPath().endsWith(".efkpkg"))
                .forEach((location, resource) -> {
                    ResourceLocation name = createEffekName(location);
                    loadEffect(manager, name, resource)
                        .ifPresent(effect -> prep.loadedEffects.put(name, new EffectDefinition().setEffect(effect)));
                });
            loadedEffects.putAll(prep.loadedEffects);
        });
        if (!loadedEffects.isEmpty()) {
            LOGGER.info("[Effekseer] Loaded {} effects: {}", loadedEffects.size(), loadedEffects.keySet());
        } else {
            LOGGER.warn("[Effekseer] No effects loaded from assets/effeks");
        }
    }

    @FunctionalInterface
    private interface TriConsumer<T> {
        boolean accept(T bytes, int length, int index);
    }

    private final Map<ResourceLocation, EffectDefinition> loadedEffects = new LinkedHashMap<>();
}
