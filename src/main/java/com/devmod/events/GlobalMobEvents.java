package com.devmod.events;

import com.devmod.DevMod;

import static com.devmod.DevMod.MODID;

import com.devmod.telemetry.DeferredEntityProcessor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

/**
 * Global mob event handler.
 *
 * PERFORMANCE FIX: Instead of processing mob configs immediately on spawn
 * (which causes TPS drops when structures generate many entities at once),
 * we now queue spawns for deferred processing via DeferredEntityProcessor.
 *
 * This distributes the load across multiple ticks, maintaining stable TPS.
 */
@EventBusSubscriber(modid = MODID)
public class GlobalMobEvents {

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
}
