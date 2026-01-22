package com.devmod.clone.client.renderer;

import java.util.Map;
import java.util.WeakHashMap;

import javax.annotation.Nullable;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;

import com.devmod.DevMod;
import com.devmod.client.vfx.effekseer.EffekseerClient;
import com.devmod.client.vfx.effekseer.api.ParticleEmitter;
import com.devmod.clone.block.TelepadBlock;
import com.devmod.clone.block.entity.TelepadBlockEntity;

/**
 * Controls the Effekseer spiral effect above the telepad.
 * Single centered effect that syncs with the shader vortex rotation.
 */
public final class TelepadEffekseerController {
    // 3D spiral effect - follows central vortex rotation
    private static final ResourceLocation EFFECT_SPIRAL = ResourceLocation.fromNamespaceAndPath(DevMod.MODID, "telepad/spiral");

    // Effect positioning - centered above the telepad (matching portal dimensions)
    private static final float CENTER_Y = 2.755f;  // Height above telepad block (center of oval) - updated for 35% larger portal
    private static final float SCALE = 0.0342f;  // Scale reduced by 50% from 0.057

    // Portal aspect ratio for oval shape (height / width = 2.6 / 1.82 ≈ 1.43)
    private static final float ASPECT_RATIO = 2.6f / 1.82f;

    // Dynamic input bounds (lower = more transparent)
    private static final float MIN_DYNAMIC_INPUT = 0.15f;
    private static final float MAX_DYNAMIC_INPUT = 0.5f;

    // Lifecycle timings
    private static final int EXPIRE_TICKS = 40;
    private static final int RESPAWN_TICKS = 200;

    private static final Map<Level, Long2ObjectMap> ACTIVE = new WeakHashMap<>();
    private static boolean loggedOnce = false;

    private TelepadEffekseerController() {
    }

    /**
     * Update the vortex effect with synchronized rotation.
     * @param be The telepad block entity
     * @param intensity Effect intensity (0-1.5)
     * @param vortexRotation Current vortex rotation angle in radians (from shader)
     */
    public static void update(TelepadBlockEntity be, float intensity, float vortexRotation) {
        Level level = be.getLevel();
        if (level == null) {
            return;
        }
        BlockPos pos = be.getBlockPos();
        long key = pos.asLong();
        long now = level.getGameTime();

        Long2ObjectMap emitters = ACTIVE.computeIfAbsent(level, _lvl -> new Long2ObjectMap());
        EmitterState state = emitters.get(key);

        // Stop effects if intensity is too low
        if (intensity <= 0.02f) {
            if (state != null) {
                state.stop();
                emitters.remove(key);
            }
            cleanup(level, emitters, now);
            return;
        }

        // Spawn or respawn effect
        boolean firstSpawn = state == null;
        if (state == null || now - state.lastSpawnTick > RESPAWN_TICKS) {
            if (state != null) {
                state.stop();
            }

            // Create only back emitter (front disabled)
            ResourceLocation backName = ResourceLocation.fromNamespaceAndPath(DevMod.MODID, "telepad_spiral_back_" + key);
            ParticleEmitter back = EffekseerClient.play(EFFECT_SPIRAL, ParticleEmitter.Type.WORLD, backName);

            state = new EmitterState(null, back);
            state.lastSpawnTick = now;
            emitters.put(key, state);

            // Ensure back effect is visible
            back.setVisibility(true);
            back.resume();

            if (firstSpawn && !loggedOnce) {
                DevMod.LOGGER.info("[TelepadEffekseer] Spawned spiral effect (back only) at {}",
                    pos);
                loggedOnce = true;
            }
        }

        // Respawn if back effect died
        if (!state.emitterBack.exists()) {
            ResourceLocation backName = ResourceLocation.fromNamespaceAndPath(DevMod.MODID, "telepad_spiral_back_" + key);
            state.emitterBack = EffekseerClient.play(EFFECT_SPIRAL, ParticleEmitter.Type.WORLD, backName);
            state.emitterBack.setVisibility(true);
            state.emitterBack.resume();
        }

        state.lastSeenTick = now;
        updateEmitter(be, state, intensity, vortexRotation);
        cleanup(level, emitters, now);
    }

    private static void updateEmitter(TelepadBlockEntity be, EmitterState state, float intensity, float vortexRotation) {
        BlockPos pos = be.getBlockPos();

        // Base position: center of block, elevated above telepad
        float x = pos.getX() + 0.5f;
        float y = pos.getY() + CENTER_Y;
        float z = pos.getZ() + 0.5f;

        // Get facing direction for rotation
        Direction facing = be.getBlockState().getValue(TelepadBlock.FACING);
        float facingYaw = switch (facing) {
            case SOUTH -> (float) Math.PI;
            case WEST -> (float) (Math.PI * 0.5);
            case EAST -> (float) (-Math.PI * 0.5);
            default -> 0.0f;
        };

        // Rotate 180° on X (90° + 90° extra requested)
        float flatTilt = (float) Math.PI;

        // Offset behind portal
        float depthOffset = 0.15f;
        float offsetX = facing.getStepX() * depthOffset;
        float offsetZ = facing.getStepZ() * depthOffset;

        // Back emitter only - behind portal
        state.emitterBack.setPosition(x - offsetX, y, z - offsetZ);
        state.emitterBack.setScale(SCALE, SCALE, SCALE * ASPECT_RATIO);
        state.emitterBack.setRotation(flatTilt, facingYaw + (float) Math.PI, 0);

        // Dynamic inputs to control transparency
        float dynamicInput = Mth.clamp(intensity, MIN_DYNAMIC_INPUT, MAX_DYNAMIC_INPUT);
        state.emitterBack.setDynamicInput(0, dynamicInput);
        state.emitterBack.setDynamicInput(1, dynamicInput);
        state.emitterBack.setDynamicInput(2, dynamicInput);
        state.emitterBack.setDynamicInput(3, dynamicInput);
    }

    /**
     * Force stop all effects at a position (called when block is removed).
     */
    public static void stopAt(Level level, BlockPos pos) {
        if (level == null) return;
        Long2ObjectMap emitters = ACTIVE.get(level);
        if (emitters == null) return;

        long key = pos.asLong();
        EmitterState state = emitters.get(key);
        if (state != null) {
            state.stop();
            emitters.remove(key);
        }
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
        private ParticleEmitter emitterFront;
        private ParticleEmitter emitterBack;
        private long lastSeenTick;
        private long lastSpawnTick;

        private EmitterState(ParticleEmitter front, ParticleEmitter back) {
            this.emitterFront = front;
            this.emitterBack = back;
        }

        private void stop() {
            if (emitterFront != null) emitterFront.stop();
            if (emitterBack != null) emitterBack.stop();
        }

        private boolean exists() {
            return emitterBack != null && emitterBack.exists();
        }
    }

    private static final class Long2ObjectMap {
        private final Long2ObjectOpenHashMap<EmitterState> backing = new Long2ObjectOpenHashMap<>();
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
