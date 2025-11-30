package com.frenkvs.devmod.event.common;

import com.frenkvs.devmod.event.client.WorldRenderEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;

@EventBusSubscriber(modid = "devmod", bus = EventBusSubscriber.Bus.GAME)
public class ArrowEvents {

    @SubscribeEvent
    public static void onProjectileImpact(ProjectileImpactEvent event) {
        // 1. Controlliamo se è una FRECCIA (o un tridente, balestra, ecc.)
        if (!(event.getEntity() instanceof AbstractArrow arrow)) return;

        // Se siamo sul Client (grafica), non calcoliamo nulla, lasciamo fare al server
        if (arrow.level().isClientSide) {
            HitResult result = event.getRayTraceResult();
            Vec3 hitPos = result.getLocation();
            
            // Calculate arrow speed at impact
            Vec3 velocity = arrow.getDeltaMovement();
            double speed = Math.sqrt(velocity.x * velocity.x + velocity.y * velocity.y + velocity.z * velocity.z);
            
            WorldRenderEvents.addArrowHit(hitPos.x, hitPos.y, hitPos.z, speed);
            return;
        }

        ServerLevel level = (ServerLevel) arrow.level();
        HitResult result = event.getRayTraceResult();
        Vec3 hitPos = result.getLocation();

        // 2. EFFETTO VISIVO (Goal: Ben visibile)
        // Creiamo un'esplosione di particelle nel punto esatto dell'impatto
        // "FLASH" è molto visibile, come un piccolo fulmine

        // 3. SUONO D'IMPATTO
        level.playSound(null, hitPos.x, hitPos.y, hitPos.z, SoundEvents.AMETHYST_BLOCK_HIT, SoundSource.PLAYERS, 1.0f, 2.0f);


    }
}
