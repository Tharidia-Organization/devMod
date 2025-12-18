package com.frenkvs.devmod;

import com.frenkvs.devmod.client.ClientVFXProxy;
import com.frenkvs.devmod.util.I18n;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Server-side arrow event handler for projectile impacts.
 * Client VFX are delegated to ClientVFXHelper via safe dist checks.
 */
@EventBusSubscriber(modid = DevMod.MODID)
public class ArrowEvents {
    private static final Logger LOGGER = LoggerFactory.getLogger(ArrowEvents.class);

    // Scheduled executor for evasion check (replaces Thread.sleep)
    private static final ScheduledExecutorService EVASION_SCHEDULER =
        Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "DevMod-ArrowEvasion");
            t.setDaemon(true);
            return t;
        });

    @SubscribeEvent
    public static void onProjectileImpact(ProjectileImpactEvent event) {
        // 1. Check if it's an ARROW (or trident, crossbow bolt, etc.)
        if (!(event.getEntity() instanceof AbstractArrow arrow)) return;

        // If on Client (graphics), don't calculate anything, let server handle it
        if (arrow.level().isClientSide) return;

        ServerLevel level = (ServerLevel) arrow.level();
        HitResult result = event.getRayTraceResult();
        Vec3 hitPos = result.getLocation();

        // 2. VISUAL EFFECT (Goal: Very visible)
        // Create particle explosion at exact impact point
        // "FLASH" is very visible, like a small lightning
        level.sendParticles(Objects.requireNonNull(ParticleTypes.FLASH), hitPos.x, hitPos.y, hitPos.z, 1, 0, 0, 0, 0);
        // Add some golden smoke (TOTEM_OF_UNDYING) for epic effect
        level.sendParticles(Objects.requireNonNull(ParticleTypes.TOTEM_OF_UNDYING), hitPos.x, hitPos.y, hitPos.z, 10, 0.2, 0.2, 0.2, 0.1);

        // 3. IMPACT SOUND
        level.playSound(null, hitPos.x, hitPos.y, hitPos.z, Objects.requireNonNull(SoundEvents.AMETHYST_BLOCK_HIT), SoundSource.PLAYERS, 1.0f, 2.0f);


        // 4. BODY PART CALCULATION (Only if hitting an entity)
        if (result.getType() == HitResult.Type.ENTITY) {
            EntityHitResult entityResult = (EntityHitResult) result;
            Entity target = entityResult.getEntity();

            // Who fired the arrow? (To send them the message)
            if (arrow.getOwner() instanceof ServerPlayer shooter && target instanceof LivingEntity victim) {

                // Track to detect Enderman evasion
                trackPotentialEvasion(shooter, victim, hitPos);

                // UNIFIED DETECTION: Use the same precise AABB raycast algorithm as DamageHandler
                // This ensures 100% consistency between arrow hits and melee hits
                HitHelper.HitResult hitResult = HitHelper.rayTraceBodyPartWithHitPoint(shooter, victim);
                HitHelper.BodyPart bodyPartEnum = hitResult != null ? hitResult.part() : HitHelper.BodyPart.BODY;

                String bodyPartKey;

                // Map BodyPart enum to translation key
                switch (bodyPartEnum) {
                    case HEAD:
                        bodyPartKey = "devmod.arrow.head_headshot";
                        // Special "DING" sound for headshot
                        level.playSound(null, shooter.getX(), shooter.getY(), shooter.getZ(), Objects.requireNonNull(SoundEvents.ARROW_HIT_PLAYER), SoundSource.PLAYERS, 1.0f, 1.5f);
                        break;

                    case ARMS:
                        bodyPartKey = "devmod.arrow.arms";
                        break;

                    case LEGS:
                        bodyPartKey = "devmod.arrow.legs";
                        break;

                    case BODY:
                    default:
                        bodyPartKey = "devmod.arrow.torso";
                        break;
                }

                String bodyPart = I18n.translate(bodyPartKey).getString();

                // 5. OVERLAY MESSAGE (Action Bar - the one above the inventory)
                shooter.sendSystemMessage(I18n.translate("devmod.arrow.hit_on", victim.getName().getString(), bodyPart));

                // Quick visual message above the hotbar
                shooter.displayClientMessage(I18n.translate("devmod.arrow.hit_indicator", bodyPart), true);
            }
        }
    }

    // === Tracking to detect Enderman evasions with arrows ===
    // Record to save data at the moment of impact
    private record PendingArrowHit(long timestamp, Vec3 hitPos, Vec3 targetPos, ServerPlayer shooter) {}

    // Map: targetId -> impact data
    private static final Map<Integer, PendingArrowHit> pendingArrowHits = new ConcurrentHashMap<>();

    /**
     * Registers a potential arrow hit on Enderman to check for evasion.
     * IMPORTANT: Save the target's position NOW, before it teleports!
     */
    private static void trackPotentialEvasion(ServerPlayer shooter, LivingEntity target, Vec3 hitPos) {
        if (!(target instanceof EnderMan)) return;

        long now = System.currentTimeMillis();
        // Save the target's position at the moment of impact!
        Vec3 targetPos = Objects.requireNonNull(target.position(), "target position")
            .add(0, target.getBbHeight() * 0.5, 0);
        Vec3 safeHitPos = Objects.requireNonNull(hitPos, "hit position");

        pendingArrowHits.put(target.getId(), new PendingArrowHit(now, safeHitPos, targetPos, shooter));

        LOGGER.debug("Arrow hit on Enderman tracked at hitPos={}, targetPos={}", safeHitPos, targetPos);

        // Schedule evasion check after 150ms (uses ScheduledExecutor instead of Thread.sleep)
        final int targetId = target.getId();
        EVASION_SCHEDULER.schedule(() -> {
            PendingArrowHit pending = pendingArrowHits.remove(targetId);
            if (pending == null) return;

            // Check if the Enderman moved significantly (teleport)
            // NOTE: target may no longer be valid, we use saved data
            Vec3 currentPos = target.isAlive()
                ? Objects.requireNonNull(target.position(), "current target position")
                : pending.targetPos;
            double distMoved = currentPos.distanceTo(Objects.requireNonNull(pending.targetPos(), "saved target position"));
            boolean probablyEvaded = distMoved > 5.0; // If it moved more than 5 blocks, it evaded

            LOGGER.debug("Enderman moved {} blocks, evaded={}", String.format("%.1f", distMoved), probablyEvaded);

            // Spawn evasion panel (proxy handles dist check)
            if (probablyEvaded) {
                ClientVFXProxy.spawnArrowEvasionPanel(pending.shooter, target, pending.hitPos, pending.targetPos);
            }
        }, 150, TimeUnit.MILLISECONDS);
    }
}
