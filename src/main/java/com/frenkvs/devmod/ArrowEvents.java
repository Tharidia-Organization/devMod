package com.frenkvs.devmod;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.phys.EntityHitResult;
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
            System.out.println("Arrow hit detected on client side!");
            HitResult result = event.getRayTraceResult();
            Vec3 hitPos = result.getLocation();
            System.out.println("Hit position: " + hitPos.x + ", " + hitPos.y + ", " + hitPos.z);
            WorldRenderEvents.addArrowHit(hitPos.x, hitPos.y, hitPos.z);
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


        // 4. CALCOLO PARTE DEL CORPO (Solo se colpiamo un'entità)
        if (result.getType() == HitResult.Type.ENTITY) {
            EntityHitResult entityResult = (EntityHitResult) result;
            Entity target = entityResult.getEntity();

            // Chi ha sparato la freccia? (Per mandargli il messaggio)
            if (arrow.getOwner() instanceof ServerPlayer shooter && target instanceof LivingEntity victim) {

                // --- MATEMATICA DEL CORPO ---
                double feetY = victim.getY();
                double headY = feetY + victim.getBbHeight() * 0.85; // Altezza occhi circa
                double hitY = hitPos.y;

                String bodyPart;
                int color; // Codice colore per il messaggio

                if (hitY >= headY) {
                    bodyPart = "TESTA (HEADSHOT!)";
                    color = 0xFF5555; // Rosso

                    // Suono speciale "DING" per headshot
                    level.playSound(null, shooter.getX(), shooter.getY(), shooter.getZ(), SoundEvents.ARROW_HIT_PLAYER, SoundSource.PLAYERS, 1.0f, 1.5f);

                } else if (hitY <= feetY + (victim.getBbHeight() * 0.3)) {
                    bodyPart = "GAMBE";
                    color = 0x55FFFF; // Azzurro
                } else {
                    bodyPart = "TORSO";
                    color = 0x55FF55; // Verde
                }

                // 5. MESSAGGIO OVERLAY (Action Bar - quella sopra l'inventario)
                shooter.sendSystemMessage(Component.literal("§7Colpito: §f" + victim.getName().getString() + " §7su: §" + getChatColorChar(color) + bodyPart));

                // Messaggio visivo veloce sopra la hotbar
                shooter.displayClientMessage(Component.literal("🎯 " + bodyPart), true);
            }
        }
    }

    // Piccolo aiuto per convertire i colori in codici chat Minecraft (es. Rosso -> 'c')
    private static char getChatColorChar(int color) {
        if (color == 0xFF5555) return 'c'; // Red
        if (color == 0x55FFFF) return 'b'; // Aqua
        return 'a'; // Green
    }
}