package com.devmod.entity;

import java.util.Objects;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import com.devmod.DevMod;
import com.devmod.clone.entity.PlayerCloneEntity;

/**
 * Entity type registrations for DevMod.
 * Currently includes the NexaEntity (Nexus hub avatar).
 */
public final class ModEntities {
    private ModEntities() {}

    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
        DeferredRegister.create(Registries.ENTITY_TYPE, DevMod.MODID);

    /**
     * The Nexa entity - the Nexus hub AI avatar.
     * A floating humanoid that greets players and provides guidance.
     */
    public static final DeferredHolder<EntityType<?>, EntityType<NexaEntity>> NEXA =
        ENTITY_TYPES.register("nexa", () -> EntityType.Builder.of(NexaEntity::new, MobCategory.MISC)
            .sized(0.6F, 1.8F)  // Same hitbox as player
            .clientTrackingRange(10)
            .updateInterval(2)
            .fireImmune()
            .build("nexa")
        );

    /**
     * The Player Clone entity - a companion clone of a player.
     * Created by the clone system REFORMER.
     */
    public static final DeferredHolder<EntityType<?>, EntityType<PlayerCloneEntity>> PLAYER_CLONE =
        ENTITY_TYPES.register("player_clone", () -> EntityType.Builder.of(PlayerCloneEntity::new, MobCategory.CREATURE)
            .sized(0.6F, 1.8F)  // Same hitbox as player
            .clientTrackingRange(10)
            .updateInterval(2)
            .build("player_clone")
        );

    /**
     * Register entity types on the mod event bus.
     * Called from DevMod constructor.
     */
    public static void register(IEventBus eventBus) {
        ENTITY_TYPES.register(Objects.requireNonNull(eventBus, "eventBus"));
        DevMod.LOGGER.debug("[ModEntities] Entity types registered");
    }

    /**
     * Called during mod initialization to ensure entities are registered.
     */
    public static void init() {
        // Static init ensures entity types are registered
        DevMod.LOGGER.debug("[ModEntities] Entities initialized");
    }
}
