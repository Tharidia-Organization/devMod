package com.devmod.npc.component;

import java.util.Objects;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;

import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import com.devmod.DevMod;
import com.devmod.npc.data.NpcConfiguration;

/**
 * Data component registry for the NPC module.
 * Provides NPC_CONFIG component for storing NPC configuration on items.
 */
public final class NpcComponents {
    private NpcComponents() {}

    public static final DeferredRegister<DataComponentType<?>> COMPONENTS =
        DeferredRegister.create(Objects.requireNonNull(Registries.DATA_COMPONENT_TYPE), DevMod.MODID);

    /**
     * NPC configuration component for storing NPC settings on the Neurocell NPC item.
     * Contains display name, skin, behavior, appearance, and dialog settings.
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<NpcConfiguration>> NPC_CONFIG =
        COMPONENTS.register("npc_config", () -> DataComponentType.<NpcConfiguration>builder()
            .persistent(Objects.requireNonNull(NpcConfiguration.CODEC))
            .networkSynchronized(Objects.requireNonNull(NpcConfiguration.STREAM_CODEC))
            .build());

    /**
     * Initialize the component registry.
     * Called from NpcModule during mod initialization.
     */
    public static void init() {
        DevMod.LOGGER.debug("[NPC] NPC components initialized");
    }
}
