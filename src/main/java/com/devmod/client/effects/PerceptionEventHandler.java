package com.devmod.client.effects;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

import com.devmod.DevMod;

/**
 * Handles Perception-style events:
 * - Explosion screen shake (TNT, creepers, etc.)
 * - Warden spawn and heartbeat shake
 * - Player fall landing shake
 */
@EventBusSubscriber(modid = DevMod.MODID, value = Dist.CLIENT)
public class PerceptionEventHandler {

    // Track warden heartbeat timing
    private static long lastWardenHeartbeat = 0;
    private static final int WARDEN_HEARTBEAT_INTERVAL_TICKS = 40; // ~2 seconds

    /**
     * Screen shake on explosions (TNT, creepers, ghast fireballs, etc.)
     */
    @SubscribeEvent
    public static void onExplosion(ExplosionEvent.Detonate event) {
        if (!ShakeManager.INSTANCE.isEnabled()) return;
        if (event.getLevel().isClientSide()) {
            Vec3 explosionPos = event.getExplosion().center();
            float power = event.getExplosion().radius();

            ShakeEffect shake = ShakeManager.createExplosion(explosionPos, power);
            ShakeManager.INSTANCE.addShake(shake);
        }
    }

    /**
     * Screen shake when Warden spawns.
     */
    @SubscribeEvent
    public static void onEntityJoinWorld(EntityJoinLevelEvent event) {
        if (!ShakeManager.INSTANCE.isEnabled()) return;
        if (!event.getLevel().isClientSide()) return;

        Entity entity = event.getEntity();
        if (entity instanceof Warden warden) {
            // Strong shake when Warden emerges
            ShakeEffect shake = ShakeManager.createBossEvent(warden);
            ShakeManager.INSTANCE.addShake(shake);
        }
    }

    /**
     * Screen shake on player fall landing.
     */
    @SubscribeEvent
    public static void onLivingFall(LivingFallEvent event) {
        if (!ShakeManager.INSTANCE.isEnabled()) return;

        if (event.getEntity() instanceof Player player) {
            // Only process on client side for the local player
            Minecraft mc = Minecraft.getInstance();
            Player localPlayer = mc.player;
            if (localPlayer == null || player.getId() != localPlayer.getId()) return;
            if (!player.level().isClientSide()) return;

            float fallDistance = event.getDistance();

            // Only shake for significant falls (> 3 blocks)
            if (fallDistance > 3.0f) {
                // Scale intensity based on fall distance
                // 3 blocks = minimal shake, 10+ blocks = strong shake
                float intensity = Math.min((fallDistance - 3f) / 7f, 1.5f);

                ShakeEffect shake = createFallShake(player.position(), intensity);
                ShakeManager.INSTANCE.addShake(shake);
            }
        }
    }

    /**
     * Warden heartbeat shake (checked each tick).
     */
    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Post event) {
        if (!ShakeManager.INSTANCE.isEnabled()) return;

        Entity entity = event.getEntity();
        if (!(entity instanceof Warden warden)) return;

        Level level = warden.level();
        if (!level.isClientSide()) return;

        Minecraft mc = Minecraft.getInstance();
        Player localPlayer = mc.player;
        if (localPlayer == null) return;

        // Check if enough time has passed since last heartbeat
        long currentTime = level.getGameTime();
        if (currentTime - lastWardenHeartbeat < WARDEN_HEARTBEAT_INTERVAL_TICKS) return;

        // Only shake if Warden is nearby (within 32 blocks)
        double distance = localPlayer.distanceTo(warden);
        if (distance > 32) return;

        // Subtle heartbeat shake
        lastWardenHeartbeat = currentTime;
        ShakeEffect shake = createWardenHeartbeatShake(warden.position(), distance);
        ShakeManager.INSTANCE.addShake(shake);
    }

    // ==================== Shake Factory Methods ====================

    /**
     * Creates a fall landing shake.
     * @param position Landing position
     * @param intensity 0.0 - 1.5 based on fall distance
     */
    private static ShakeEffect createFallShake(Vec3 position, float intensity) {
        return ShakeEffect.builder()
            .source(position)
            .duration((int) (6 + intensity * 8))
            .fadeIn(1)
            .fadeOut((int) (4 + intensity * 4))
            .rotation(1.5f * intensity, 15f)
            .offset(0.02f * intensity, 18f)
            .maxDistance(1f) // Only affects the player landing
            .distanceEasing(ShakeEffect.EasingFunction.LINEAR)
            .build();
    }

    /**
     * Creates a subtle Warden heartbeat shake.
     * @param position Warden position
     * @param distance Distance from player to Warden
     */
    private static ShakeEffect createWardenHeartbeatShake(Vec3 position, double distance) {
        // Intensity decreases with distance
        float distanceFactor = 1f - (float) Math.min(distance / 32.0, 1.0);
        float intensity = 0.3f * distanceFactor;

        return ShakeEffect.builder()
            .source(position)
            .duration(8)
            .fadeIn(2)
            .fadeOut(4)
            .rotation(0.5f * intensity, 3f)
            .offset(0.005f * intensity, 4f)
            .maxDistance(32f)
            .distanceEasing(ShakeEffect.EasingFunction.EASE_OUT_QUAD)
            .build();
    }
}
