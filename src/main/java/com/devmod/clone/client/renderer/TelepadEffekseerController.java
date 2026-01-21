package com.devmod.clone.client.renderer;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;

import com.devmod.DevMod;
import com.devmod.clone.block.TelepadBlock;
import com.devmod.clone.block.entity.TelepadBlockEntity;
import com.devmod.client.vfx.effekseer.EffekseerClient;
import com.devmod.client.vfx.effekseer.api.ParticleEmitter;
import com.devmod.client.vfx.effekseer.registry.EffectRegistry;

public final class TelepadEffekseerController {
    private static final ResourceLocation EFFECT_RING = ResourceLocation.fromNamespaceAndPath(DevMod.MODID, "telepad/portal");
    private static final ResourceLocation EFFECT_AURA = ResourceLocation.fromNamespaceAndPath(DevMod.MODID, "telepad/aura");
    private static final ResourceLocation EFFECT_CORE = ResourceLocation.fromNamespaceAndPath(DevMod.MODID, "telepad/core");
    private static final ResourceLocation EMITTER_RING = ResourceLocation.fromNamespaceAndPath(DevMod.MODID, "telepad_portal_ring");
    private static final ResourceLocation EMITTER_AURA = ResourceLocation.fromNamespaceAndPath(DevMod.MODID, "telepad_portal_aura");
    private static final ResourceLocation EMITTER_CORE = ResourceLocation.fromNamespaceAndPath(DevMod.MODID, "telepad_portal_core");
    private static final float SCALE_RING = 1.4f;
    private static final float SCALE_AURA = 1.8f;
    private static final float SCALE_CORE = 0.9f;
    private static final float CENTER_Y = 1.55f;
    private static final float AURA_Y_OFFSET = 0.12f;
    private static final float CORE_Y_OFFSET = -0.05f;
    private static final float MIN_DYNAMIC_INPUT = 0.2f;
    private static final float MAX_DYNAMIC_INPUT = 1.6f;
    private static final float ROTATION_PITCH = (float) (Math.PI * 0.5);
    private static final int EXPIRE_TICKS = 40;
    private static final int RESPAWN_TICKS = 60;
    private static final Set<ResourceLocation> MISSING_EFFECT_LOG = new HashSet<>();
    private static final Set<ResourceLocation> DUMMY_EMITTER_LOG = new HashSet<>();

    private static final Map<Level, Long2ObjectMap> ACTIVE = new WeakHashMap<>();

    private TelepadEffekseerController() {
    }

    public static void update(TelepadBlockEntity be, float intensity) {
        Level level = be.getLevel();
        if (level == null) {
            return;
        }
        BlockPos pos = be.getBlockPos();
        long key = pos.asLong();
        long now = level.getGameTime();

        Long2ObjectMap emitters = ACTIVE.computeIfAbsent(level, _lvl -> new Long2ObjectMap());
        EmitterState state = emitters.get(key);

        if (intensity <= 0.02f) {
            if (state != null) {
                state.stop();
                emitters.remove(key);
            }
            cleanup(level, emitters, now);
            return;
        }

        boolean firstSpawn = state == null;
        if (state == null || now - state.lastSpawnTick > RESPAWN_TICKS) {
            if (state != null) {
                state.stop();
            }
            logMissingEffect(EFFECT_RING);
            logMissingEffect(EFFECT_AURA);
            logMissingEffect(EFFECT_CORE);
            ParticleEmitter ring = EffekseerClient.play(EFFECT_RING, ParticleEmitter.Type.WORLD, EMITTER_RING);
            ParticleEmitter aura = EffekseerClient.play(EFFECT_AURA, ParticleEmitter.Type.WORLD, EMITTER_AURA);
            ParticleEmitter core = EffekseerClient.play(EFFECT_CORE, ParticleEmitter.Type.WORLD, EMITTER_CORE);
            logDummyEmitter(EFFECT_RING, ring);
            logDummyEmitter(EFFECT_AURA, aura);
            logDummyEmitter(EFFECT_CORE, core);
            state = new EmitterState(ring, aura, core);
            state.lastSpawnTick = now;
            emitters.put(key, state);
            ensureActive(state.ring);
            ensureActive(state.aura);
            ensureActive(state.core);
            if (firstSpawn) {
                DevMod.LOGGER.info("[TelepadEffekseer] Spawned ring={} aura={} core={} (exists r/a/c: {}/{}/{})",
                    state.ring.handle, state.aura.handle, state.core.handle,
                    state.ring.exists(), state.aura.exists(), state.core.exists());
            }
        }

        if (!state.ring.exists()) {
            state.ring = EffekseerClient.play(EFFECT_RING, ParticleEmitter.Type.WORLD, EMITTER_RING);
            logDummyEmitter(EFFECT_RING, state.ring);
        }
        if (!state.aura.exists()) {
            state.aura = EffekseerClient.play(EFFECT_AURA, ParticleEmitter.Type.WORLD, EMITTER_AURA);
            logDummyEmitter(EFFECT_AURA, state.aura);
        }
        if (!state.core.exists()) {
            state.core = EffekseerClient.play(EFFECT_CORE, ParticleEmitter.Type.WORLD, EMITTER_CORE);
            logDummyEmitter(EFFECT_CORE, state.core);
        }

        state.lastSeenTick = now;
        updateEmitter(be, state.ring, intensity, SCALE_RING, CENTER_Y, 1.0f);
        updateEmitter(be, state.aura, intensity, SCALE_AURA, CENTER_Y + AURA_Y_OFFSET, 0.85f);
        updateEmitter(be, state.core, intensity, SCALE_CORE, CENTER_Y + CORE_Y_OFFSET, 0.65f);
        cleanup(level, emitters, now);
    }

    private static void updateEmitter(TelepadBlockEntity be, ParticleEmitter emitter, float intensity,
                                      float scale, float centerY, float inputScale) {
        BlockPos pos = be.getBlockPos();
        float x = pos.getX() + 0.5f;
        float y = pos.getY() + centerY;
        float z = pos.getZ() + 0.5f;
        emitter.setPosition(x, y, z);
        emitter.setScale(scale, scale, scale);

        Direction facing = be.getBlockState().getValue(TelepadBlock.FACING);
        float rotation = switch (facing) {
            case SOUTH -> (float) Math.PI;
            case WEST -> (float) (Math.PI * 0.5);
            case EAST -> (float) (-Math.PI * 0.5);
            default -> 0.0f;
        };
        emitter.setRotation(ROTATION_PITCH, rotation, 0.0f);

        float pulse = Mth.clamp(intensity * inputScale, MIN_DYNAMIC_INPUT, MAX_DYNAMIC_INPUT);
        emitter.setDynamicInput(0, pulse);
    }

    private static void ensureActive(ParticleEmitter emitter) {
        emitter.setVisibility(true);
        emitter.resume();
    }

    private static void logMissingEffect(ResourceLocation effectId) {
        if (!MISSING_EFFECT_LOG.add(effectId)) {
            return;
        }
        if (EffectRegistry.get(effectId) == null) {
            DevMod.LOGGER.warn("[TelepadEffekseer] Effect '{}' not found in registry", effectId);
        }
    }

    private static void logDummyEmitter(ResourceLocation effectId, ParticleEmitter emitter) {
        if (emitter.exists() || !DUMMY_EMITTER_LOG.add(effectId)) {
            return;
        }
        DevMod.LOGGER.warn("[TelepadEffekseer] Effect '{}' returned a dummy emitter", effectId);
    }

    private static void cleanup(Level level, Long2ObjectMap emitters, long now) {
        if (emitters.lastCleanupTick + 20 > now) {
            return;
        }
        emitters.lastCleanupTick = now;
        emitters.cleanup(now, EXPIRE_TICKS);
        if (emitters.isEmpty()) {
            ACTIVE.remove(level);
        }
    }

    private static final class EmitterState {
        private ParticleEmitter ring;
        private ParticleEmitter aura;
        private ParticleEmitter core;
        private long lastSeenTick;
        private long lastSpawnTick;

        private EmitterState(ParticleEmitter ring, ParticleEmitter aura, ParticleEmitter core) {
            this.ring = ring;
            this.aura = aura;
            this.core = core;
        }

        private void stop() {
            ring.stop();
            aura.stop();
            core.stop();
        }
    }

    private static final class Long2ObjectMap {
        private final it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<EmitterState> backing =
            new it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<>();
        private long lastCleanupTick = 0L;

        private @Nullable EmitterState get(long key) {
            return backing.get(key);
        }

        private void put(long key, EmitterState state) {
            backing.put(key, state);
        }

        private void remove(long key) {
            backing.remove(key);
        }

        private void cleanup(long now, int expireTicks) {
            backing.long2ObjectEntrySet().removeIf(entry -> entry.getValue() == null
                || now - entry.getValue().lastSeenTick > expireTicks);
        }

        private boolean isEmpty() {
            return backing.isEmpty();
        }
    }
}
