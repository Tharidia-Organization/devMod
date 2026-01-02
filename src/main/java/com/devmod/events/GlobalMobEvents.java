package com.devmod.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;

import com.devmod.telemetry.DeferredEntityProcessor;

import static com.devmod.DevMod.MODID;

@EventBusSubscriber(modid = MODID)
public class GlobalMobEvents {
    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalMobEvents.class);

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        // Server-side only
        if (event.getLevel().isClientSide) return;

        // Only process LivingEntity (includes Mob)
        if (!(event.getEntity() instanceof LivingEntity living)) return;
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) return;

        // PERFORMANCE FIX: Queue for deferred processing instead of immediate
        // This prevents TPS drops when villages/structures spawn many entities
        DeferredEntityProcessor.INSTANCE.queueSpawn(serverLevel, living, "join");
    }

    @SubscribeEvent
    public static void onEntityLeave(EntityLeaveLevelEvent event) {
        // Server-side only, dynamic dimensions only
        if (event.getLevel().isClientSide) return;
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) return;

        String dimId = serverLevel.dimension().location().toString();
        if (!dimId.startsWith("devmod:instance_")) return;

        Entity entity = event.getEntity();
        if (!(entity instanceof LivingEntity)) return;

        // Log detailed removal info for debugging
        Entity.RemovalReason reason = entity.getRemovalReason();
        String reasonStr = reason != null ? reason.name() : "null";
        String entityType = entity.getType().toString();

        LOGGER.warn("[EntityLeave] {} (UUID={}) leaving dimension {} - removalReason={}, isAlive={}, isRemoved={}, tickCount={}",
            entityType, entity.getUUID(), dimId, reasonStr, entity.isAlive(), entity.isRemoved(), entity.tickCount);

        // Log the stack trace to see what's calling the removal
        if (reason == Entity.RemovalReason.DISCARDED || reason == null) {
            LOGGER.warn("[EntityLeave] Stack trace for {} removal:", entityType);
            for (StackTraceElement ste : Thread.currentThread().getStackTrace()) {
                if (ste.getClassName().contains("graveyard") ||
                    ste.getClassName().contains("Acolyte") ||
                    ste.getClassName().contains("devmod") ||
                    ste.getClassName().contains("minecraft")) {
                    LOGGER.warn("  at {}", ste);
                }
            }
        }
    }
}
