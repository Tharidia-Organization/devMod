package com.devmod.client.vfx.effekseer.registry;

import java.io.Closeable;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.random.RandomGenerator;
import java.util.stream.Stream;

import javax.annotation.Nullable;

import org.joml.Vector3f;

import com.mojang.blaze3d.pipeline.RenderTarget;

import net.minecraft.resources.ResourceLocation;

import com.devmod.client.vfx.effekseer.api.EffekseerEffect;
import com.devmod.client.vfx.effekseer.api.EffekseerManager;
import com.devmod.client.vfx.effekseer.api.ParticleEmitter;
import com.devmod.client.vfx.effekseer.render.RenderUtil;

public class EffectDefinition implements Closeable {
    public EffectDefinition() {
        for (ParticleEmitter.Type type : ParticleEmitter.Type.values()) {
            oneShotEmitters.put(type, new LinkedHashSet<>());
            namedEmitters.put(type, new LinkedHashMap<>());
        }
    }

    public ParticleEmitter play() {
        return play(ParticleEmitter.Type.WORLD);
    }

    public ParticleEmitter play(ResourceLocation emitterName) {
        return play(ParticleEmitter.Type.WORLD, emitterName);
    }

    public ParticleEmitter play(ParticleEmitter.Type type) {
        if (RenderUtil.isReloadingResourcePacks()) {
            return ParticleEmitter.dummy(type);
        }
        ParticleEmitter emitter = getManager(type).createParticle(getEffect(), type);
        Set<ParticleEmitter> collection = Objects.requireNonNull(oneShotEmitters.get(type));
        collection.add(emitter);
        return emitter;
    }

    public ParticleEmitter play(ParticleEmitter.Type type, ResourceLocation emitterName) {
        if (RenderUtil.isReloadingResourcePacks()) {
            return ParticleEmitter.dummy(type);
        }
        ParticleEmitter emitter = getManager(type).createParticle(getEffect(), type);
        Map<ResourceLocation, ParticleEmitter> collection = Objects.requireNonNull(namedEmitters.get(type));
        ParticleEmitter old = collection.put(emitterName, emitter);
        if (old != null) {
            old.stop();
        }
        return emitter;
    }

    public Optional<ParticleEmitter> getNamedEmitter(ParticleEmitter.Type type, ResourceLocation emitterName) {
        return Optional.ofNullable(namedEmitters.get(type).get(emitterName));
    }

    public EffekseerManager getManager(ParticleEmitter.Type type) {
        return Objects.requireNonNull(managers.get(type));
    }

    public Stream<ParticleEmitter> emitters() {
        return emitterContainers().flatMap(Collection::stream);
    }

    public Stream<ParticleEmitter> emitters(ParticleEmitter.Type type) {
        return emitterContainers(type).flatMap(Collection::stream);
    }

    public Stream<Collection<ParticleEmitter>> emitterContainers() {
        return Stream.concat(
            oneShotEmitters.values().stream(),
            namedEmitters.values().stream().map(Map::values)
        );
    }

    public Stream<Collection<ParticleEmitter>> emitterContainers(ParticleEmitter.Type type) {
        Set<ParticleEmitter> oneshot = Objects.requireNonNull(oneShotEmitters.get(type));
        Collection<ParticleEmitter> named = Objects.requireNonNull(namedEmitters.get(type)).values();
        return Stream.of(oneshot, named);
    }

    public EffekseerEffect getEffect() {
        return effect;
    }

    public EffectDefinition setEffect(EffekseerEffect effect) {
        Objects.requireNonNull(effect);
        if (this.effect == effect) {
            return this;
        }
        if (this.effect != null) {
            emitters().forEach(ParticleEmitter::stop);
            managers().forEach(EffekseerManager::close);
            this.effect.close();
            this.managers.clear();
        }
        this.effect = effect;
        initManager();
        return this;
    }

    public Stream<EffekseerManager> managers() {
        return managers.values().stream();
    }

    private EffekseerEffect effect;
    private final EnumMap<ParticleEmitter.Type, EffekseerManager> managers = new EnumMap<>(ParticleEmitter.Type.class);
    private final EnumMap<ParticleEmitter.Type, Set<ParticleEmitter>> oneShotEmitters = new EnumMap<>(ParticleEmitter.Type.class);
    private final EnumMap<ParticleEmitter.Type, Map<ResourceLocation, ParticleEmitter>> namedEmitters = new EnumMap<>(ParticleEmitter.Type.class);
    private static final RandomGenerator RNG = new Random();
    private static final int GC_DELAY = 20;
    private final int magicLoadBalancer = Math.abs(RNG.nextInt() >>> 2) % GC_DELAY;
    private int gcTicks;
    private final EnumMap<ParticleEmitter.Type, IntRef> backgroundColorIds = new EnumMap<>(ParticleEmitter.Type.class);
    private final EnumMap<ParticleEmitter.Type, IntRef> backgroundDepthIds = new EnumMap<>(ParticleEmitter.Type.class);

    public void draw(
            ParticleEmitter.Type type,
            Vector3f front, Vector3f pos,
            int w, int h, float[] camera, float[] projection,
            float deltaFrames, float partialTicks,
            @Nullable RenderTarget background
    ) {
        EffekseerManager manager = Objects.requireNonNull(managers.get(type));
        manager.setViewport(w, h);
        manager.setCameraMatrix(camera);
        manager.setProjectionMatrix(projection);
        manager.setCameraParameter(
            front.x, front.y, front.z,
            pos.x, pos.y, pos.z
        );

        IntRef backgroundColorId = backgroundColorIds.get(type);
        IntRef backgroundDepthId = backgroundDepthIds.get(type);

        if (background == null) {
            unsetBackgrounds(manager, backgroundColorId, backgroundDepthId);
        } else if (background.getColorTextureId() != backgroundColorId.value
                || background.getDepthTextureId() != backgroundDepthId.value) {
            unsetBackgrounds(manager, backgroundColorId, backgroundDepthId);
            backgroundColorId.value = background.getColorTextureId();
            backgroundDepthId.value = background.getDepthTextureId();
            manager.getImpl().SetBackground(backgroundColorId.value, false);
            manager.getImpl().SetDepth(backgroundDepthId.value, false);
        }

        manager.startUpdate();
        manager.update(deltaFrames);
        manager.endUpdate();

        emitters(type).forEach(emitter -> emitter.runPreDrawCallbacks(partialTicks));
        manager.draw();

        if (type == ParticleEmitter.Type.WORLD) {
            gcTicks = (gcTicks + 1) % GC_DELAY;
            if (gcTicks == magicLoadBalancer) {
                emitterContainers().forEach(container -> container.removeIf(emitter -> !emitter.exists()));
            }
        }
    }

    private void unsetBackgrounds(EffekseerManager manager, IntRef backgroundColorId, IntRef backgroundDepthId) {
        if (backgroundColorId.value != -1) {
            backgroundColorId.value = -1;
            manager.getImpl().UnsetBackground();
        }
        if (backgroundDepthId.value != -1) {
            backgroundDepthId.value = -1;
            manager.getImpl().UnsetDepth();
        }
    }

    private void unsetBackgrounds(ParticleEmitter.Type type) {
        unsetBackgrounds(managers.get(type), backgroundColorIds.get(type), backgroundDepthIds.get(type));
    }

    private void initManager() {
        for (ParticleEmitter.Type type : ParticleEmitter.Type.values()) {
            backgroundColorIds.put(type, new IntRef(-1));
            backgroundDepthIds.put(type, new IntRef(-1));
            EffekseerManager old = this.managers.put(type, new EffekseerManager());
            Optional.ofNullable(old).ifPresent(EffekseerManager::close);
        }
        EffekseerManager worldManager = Objects.requireNonNull(managers.get(ParticleEmitter.Type.WORLD));
        EffekseerManager fpvMhManager = Objects.requireNonNull(managers.get(ParticleEmitter.Type.FIRST_PERSON_MAINHAND));
        EffekseerManager fpvOhManager = Objects.requireNonNull(managers.get(ParticleEmitter.Type.FIRST_PERSON_OFFHAND));
        if (!worldManager.init(9000)) {
            throw new IllegalStateException("Failed to initialize EffekseerManager");
        }
        if (!fpvMhManager.init(500)) {
            throw new IllegalStateException("Failed to initialize (fpv mainhand) EffekseerManager");
        }
        if (!fpvOhManager.init(500)) {
            throw new IllegalStateException("Failed to initialize (fpv offhand) EffekseerManager");
        }
        worldManager.setupWorkerThreads(2);
        fpvMhManager.setupWorkerThreads(1);
        fpvOhManager.setupWorkerThreads(1);
    }

    @Override
    public void close() {
        Arrays.stream(ParticleEmitter.Type.values()).forEach(this::unsetBackgrounds);
        managers.values().forEach(EffekseerManager::close);
        effect.close();
    }

    private static final class IntRef {
        private int value;

        private IntRef(int value) {
            this.value = value;
        }
    }
}
