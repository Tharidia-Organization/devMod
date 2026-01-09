package com.devmod.entity;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;

import com.devmod.DevMod;
import com.devmod.clone.entity.PlayerCloneEntity;

/**
 * Event handlers for entity registration (common side).
 * Registers entity attributes on the mod event bus.
 */
@EventBusSubscriber(modid = DevMod.MODID)
public final class ModEntityEvents {
    private ModEntityEvents() {}

    /**
     * Register entity attributes.
     * Called during mod initialization.
     */
    @SubscribeEvent
    public static void onEntityAttributeCreation(EntityAttributeCreationEvent event) {
        event.put(ModEntities.NEXA.get(), NexaEntity.createAttributes().build());
        event.put(ModEntities.PLAYER_CLONE.get(), PlayerCloneEntity.createAttributes().build());
        DevMod.LOGGER.debug("[ModEntities] Registered entity attributes");
    }
}
