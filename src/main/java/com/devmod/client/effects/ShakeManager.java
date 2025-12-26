package com.devmod.client.effects;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.joml.Vector3f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import com.devmod.config.Config;

/**
 * Manages all active screen shake effects.
 * Thread-safe singleton that handles shake lifecycle and accumulation.
 */
public class ShakeManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(ShakeManager.class);

    public static final ShakeManager INSTANCE = new ShakeManager();

    private final Map<UUID, ShakeEffect> activeShakes = new ConcurrentHashMap<>();

    private ShakeManager() {}

    /**
     * Checks if screen shake is enabled (from config).
     */
    public boolean isEnabled() {
        try {
            return Config.SCREEN_SHAKE_ENABLED.get();
        } catch (Exception e) {
            return true; // Default enabled if config not loaded
        }
    }

    /**
     * Gets the global intensity multiplier (from config).
     */
    public float getGlobalIntensity() {
        try {
            return Config.SCREEN_SHAKE_INTENSITY.get().floatValue();
        } catch (Exception e) {
            return 1.0f; // Default intensity if config not loaded
        }
    }

    /**
     * Adds a new shake effect.
     */
    public void addShake(ShakeEffect shake) {
        if (shake == null) {
            LOGGER.warn("[ShakeManager] Attempted to add null shake!");
            return;
        }
        if (!isEnabled()) {
            LOGGER.info("[ShakeManager] Shake system disabled - shake NOT added. Enable in config.");
            return;
        }
        activeShakes.put(shake.getUuid(), shake);
        LOGGER.info("[ShakeManager] Added shake effect! Total active: {}", activeShakes.size());
    }

    /**
     * Updates all active shakes. Called once per tick from GameRendererMixin.
     */
    public void tick() {
        if (!isEnabled()) {
            if (!activeShakes.isEmpty()) {
                LOGGER.info("[ShakeManager] Shake disabled but have {} active shakes - clearing", activeShakes.size());
                activeShakes.clear();
            }
            return;
        }

        if (activeShakes.isEmpty()) return;

        Player player = Minecraft.getInstance().player;
        if (player == null) return;

        LOGGER.info("[ShakeManager] tick() called with {} active shakes", activeShakes.size());

        Iterator<ShakeEffect> iterator = activeShakes.values().iterator();
        while (iterator.hasNext()) {
            ShakeEffect shake = iterator.next();
            shake.update(player);
            if (shake.isFinished()) {
                iterator.remove();
                LOGGER.info("[ShakeManager] Shake finished, remaining: {}", activeShakes.size());
            }
        }
    }

    /**
     * Gets accumulated rotation from all active shakes.
     * @param partialTicks Partial tick for interpolation
     * @return Combined rotation (pitch, yaw, roll) in degrees
     */
    public Vector3f getAccumulatedRotation(float partialTicks) {
        if (!isEnabled() || activeShakes.isEmpty()) {
            return new Vector3f(0, 0, 0);
        }

        Vector3f total = new Vector3f(0, 0, 0);
        for (ShakeEffect shake : activeShakes.values()) {
            Vector3f rot = shake.getRotation(partialTicks);
            total.add(rot);
        }

        // Apply global intensity
        total.mul(getGlobalIntensity());
        return total;
    }

    /**
     * Gets accumulated offset from all active shakes.
     * @param partialTicks Partial tick for interpolation
     * @return Combined offset in blocks
     */
    public Vector3f getAccumulatedOffset(float partialTicks) {
        if (!isEnabled() || activeShakes.isEmpty()) {
            return new Vector3f(0, 0, 0);
        }

        Vector3f total = new Vector3f(0, 0, 0);
        for (ShakeEffect shake : activeShakes.values()) {
            Vector3f off = shake.getOffset(partialTicks);
            total.add(off);
        }

        total.mul(getGlobalIntensity());
        return total;
    }

    /**
     * Gets accumulated FOV change from all active shakes.
     */
    public float getAccumulatedFov(float partialTicks) {
        if (!isEnabled() || activeShakes.isEmpty()) {
            return 0f;
        }

        float total = 0f;
        for (ShakeEffect shake : activeShakes.values()) {
            total += shake.getFovChange(partialTicks);
        }

        return total * getGlobalIntensity();
    }

    /**
     * Clears all active shakes.
     */
    public void clear() {
        activeShakes.clear();
    }

    /**
     * Gets the number of active shakes.
     */
    public int getActiveCount() {
        return activeShakes.size();
    }

    // ==================== Preset Shakes ====================

    /**
     * Creates a light hit shake (for small damage).
     */
    public static ShakeEffect createLightHit(Vec3 position) {
        return ShakeEffect.builder()
            .source(position)
            .duration(8)
            .fadeIn(1)
            .fadeOut(4)
            .rotation(1.5f, 12f)
            .maxDistance(16f)
            .distanceEasing(ShakeEffect.EasingFunction.EASE_OUT_QUAD)
            .build();
    }

    /**
     * Creates a medium hit shake (for moderate damage).
     */
    public static ShakeEffect createMediumHit(Vec3 position) {
        return ShakeEffect.builder()
            .source(position)
            .duration(12)
            .fadeIn(1)
            .fadeOut(6)
            .rotation(3f, 10f)
            .offset(0.02f, 15f)
            .maxDistance(24f)
            .distanceEasing(ShakeEffect.EasingFunction.EASE_OUT_QUAD)
            .build();
    }

    /**
     * Creates a heavy hit shake (for critical/high damage).
     */
    public static ShakeEffect createHeavyHit(Vec3 position) {
        return ShakeEffect.builder()
            .source(position)
            .duration(18)
            .fadeIn(2)
            .fadeOut(10)
            .rotation(5f, 8f)
            .offset(0.05f, 12f)
            .fov(2f, 6f)
            .maxDistance(32f)
            .distanceEasing(ShakeEffect.EasingFunction.EASE_OUT_CUBIC)
            .build();
    }

    /**
     * Creates an explosion shake.
     */
    public static ShakeEffect createExplosion(Vec3 position, float power) {
        float intensity = Math.min(power / 4f, 2f); // Normalize TNT (4) to 1.0
        return ShakeEffect.builder()
            .source(position)
            .duration((int) (20 + power * 5))
            .fadeIn(2)
            .fadeOut((int) (10 + power * 3))
            .rotation(4f * intensity, 6f)
            .offset(0.08f * intensity, 8f)
            .fov(3f * intensity, 4f)
            .maxDistance(16f + power * 8f)
            .distanceEasing(ShakeEffect.EasingFunction.EASE_OUT_EXPO)
            .build();
    }

    /**
     * Creates a dramatic boss event shake.
     */
    public static ShakeEffect createBossEvent(Entity boss) {
        return ShakeEffect.builder()
            .source(boss)
            .duration(40)
            .fadeIn(5)
            .fadeOut(20)
            .rotation(6f, 4f)
            .offset(0.1f, 6f)
            .fov(4f, 3f)
            .maxDistance(64f)
            .distanceEasing(ShakeEffect.EasingFunction.EASE_OUT_CUBIC)
            .build();
    }

    /**
     * Creates a continuous ambient shake (e.g., earthquake, warden heartbeat).
     */
    public static ShakeEffect createAmbient(Vec3 position, int duration, float intensity) {
        return ShakeEffect.builder()
            .source(position)
            .duration(duration)
            .fadeIn(10)
            .fadeOut(10)
            .rotation(1f * intensity, 2f)
            .offset(0.01f * intensity, 3f)
            .maxDistance(48f)
            .distanceEasing(ShakeEffect.EasingFunction.LINEAR)
            .build();
    }

    /**
     * Creates a damage-scaled shake based on damage amount.
     * @param position Hit position
     * @param damage Damage amount
     * @param isCritical Whether it was a critical hit
     * @param isHeadshot Whether it hit the head
     */
    public static ShakeEffect createDamageShake(Vec3 position, float damage, boolean isCritical, boolean isHeadshot) {
        // Scale intensity based on damage (assuming 20 HP max, so 10 damage = 50% HP)
        float damageScale = Math.min(damage / 10f, 2f);

        // Multipliers for special hits
        float critMult = isCritical ? 1.5f : 1f;
        float headMult = isHeadshot ? 1.3f : 1f;
        float totalMult = damageScale * critMult * headMult;

        // Duration scales with damage
        int duration = (int) (8 + damage * 0.5f);

        return ShakeEffect.builder()
            .source(position)
            .duration(Math.min(duration, 30))
            .fadeIn(1)
            .fadeOut((int) (duration * 0.6f))
            .rotation(2f * totalMult, 10f)
            .offset(0.03f * totalMult, 12f)
            .fov(isCritical ? 1.5f * totalMult : 0f, 8f)
            .maxDistance(24f)
            .distanceEasing(ShakeEffect.EasingFunction.EASE_OUT_QUAD)
            .build();
    }
}
