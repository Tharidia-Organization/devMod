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

    // Scheduled executor per evasion check (sostituisce Thread.sleep)
    private static final ScheduledExecutorService EVASION_SCHEDULER =
        Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "DevMod-ArrowEvasion");
            t.setDaemon(true);
            return t;
        });

    @SubscribeEvent
    public static void onProjectileImpact(ProjectileImpactEvent event) {
        // 1. Controlliamo se è una FRECCIA (o un tridente, balestra, ecc.)
        if (!(event.getEntity() instanceof AbstractArrow arrow)) return;

        // Se siamo sul Client (grafica), non calcoliamo nulla, lasciamo fare al server
        if (arrow.level().isClientSide) return;

        ServerLevel level = (ServerLevel) arrow.level();
        HitResult result = event.getRayTraceResult();
        Vec3 hitPos = result.getLocation();

        // 2. EFFETTO VISIVO (Goal: Ben visibile)
        // Creiamo un'esplosione di particelle nel punto esatto dell'impatto
        // "FLASH" è molto visibile, come un piccolo fulmine
        level.sendParticles(Objects.requireNonNull(ParticleTypes.FLASH), hitPos.x, hitPos.y, hitPos.z, 1, 0, 0, 0, 0);
        // Aggiungiamo un po' di fumo dorato (TOTEM_OF_UNDYING) per renderlo epico
        level.sendParticles(Objects.requireNonNull(ParticleTypes.TOTEM_OF_UNDYING), hitPos.x, hitPos.y, hitPos.z, 10, 0.2, 0.2, 0.2, 0.1);

        // 3. SUONO D'IMPATTO
        level.playSound(null, hitPos.x, hitPos.y, hitPos.z, Objects.requireNonNull(SoundEvents.AMETHYST_BLOCK_HIT), SoundSource.PLAYERS, 1.0f, 2.0f);


        // 4. CALCOLO PARTE DEL CORPO (Solo se colpiamo un'entità)
        if (result.getType() == HitResult.Type.ENTITY) {
            EntityHitResult entityResult = (EntityHitResult) result;
            Entity target = entityResult.getEntity();

            // Chi ha sparato la freccia? (Per mandargli il messaggio)
            if (arrow.getOwner() instanceof ServerPlayer shooter && target instanceof LivingEntity victim) {

                // Track per rilevare evasione Enderman
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
                        // Suono speciale "DING" per headshot
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

                // 5. MESSAGGIO OVERLAY (Action Bar - quella sopra l'inventario)
                shooter.sendSystemMessage(I18n.translate("devmod.arrow.hit_on", victim.getName().getString(), bodyPart));

                // Messaggio visivo veloce sopra la hotbar
                shooter.displayClientMessage(I18n.translate("devmod.arrow.hit_indicator", bodyPart), true);
            }
        }
    }

    // === Tracking per rilevare evasioni Enderman con frecce ===
    // Record per salvare i dati al momento dell'impatto
    private record PendingArrowHit(long timestamp, Vec3 hitPos, Vec3 targetPos, ServerPlayer shooter) {}

    // Mappa: targetId -> dati dell'impatto
    private static final Map<Integer, PendingArrowHit> pendingArrowHits = new ConcurrentHashMap<>();

    /**
     * Registra un potenziale colpo freccia su Enderman per verificare l'evasione.
     * IMPORTANTE: Salva la posizione del target ORA, prima che si teletrasporti!
     */
    @SuppressWarnings("null") // Vec3 from record fields are guaranteed non-null
    private static void trackPotentialEvasion(ServerPlayer shooter, LivingEntity target, Vec3 hitPos) {
        if (!(target instanceof EnderMan)) return;

        long now = System.currentTimeMillis();
        // Salva la posizione del target al momento dell'impatto!
        Vec3 targetPos = target.position().add(0, target.getBbHeight() * 0.5, 0);

        pendingArrowHits.put(target.getId(), new PendingArrowHit(now, hitPos, targetPos, shooter));

        LOGGER.debug("Arrow hit on Enderman tracked at hitPos={}, targetPos={}", hitPos, targetPos);

        // Schedula controllo evasione dopo 150ms (usa ScheduledExecutor invece di Thread.sleep)
        final int targetId = target.getId();
        EVASION_SCHEDULER.schedule(() -> {
            PendingArrowHit pending = pendingArrowHits.remove(targetId);
            if (pending == null) return;

            // Controlla se l'Enderman si è mosso significativamente (teletrasporto)
            // NOTA: target potrebbe non essere più valido, usiamo i dati salvati
            Vec3 currentPos = target.isAlive() ? target.position() : pending.targetPos;
            double distMoved = currentPos.distanceTo(pending.targetPos);
            boolean probablyEvaded = distMoved > 5.0; // Se si è mosso di più di 5 blocchi, ha evaso

            LOGGER.debug("Enderman moved {} blocks, evaded={}", String.format("%.1f", distMoved), probablyEvaded);

            // Spawn evasion panel (proxy handles dist check)
            if (probablyEvaded) {
                ClientVFXProxy.spawnArrowEvasionPanel(pending.shooter, target, pending.hitPos, pending.targetPos);
            }
        }, 150, TimeUnit.MILLISECONDS);
    }
}
