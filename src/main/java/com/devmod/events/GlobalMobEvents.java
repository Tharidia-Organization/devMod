package com.devmod.events;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

import com.devmod.telemetry.DeferredEntityProcessor;

import static com.devmod.DevMod.MODID;

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
