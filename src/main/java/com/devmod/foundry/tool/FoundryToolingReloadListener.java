package com.devmod.foundry.tool;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;

import com.devmod.DevMod;
import com.devmod.foundry.tool.material.FoundryMaterialReloadListener;
import com.devmod.foundry.tool.modifier.FoundryModifierReloadListener;

/**
 * Registers reload listeners for foundry tool data.
 */
@EventBusSubscriber(modid = DevMod.MODID)
public final class FoundryToolingReloadListener {
    private FoundryToolingReloadListener() {}

    @SubscribeEvent
    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new FoundryMaterialReloadListener());
        event.addListener(new FoundryToolDefinitionReloadListener());
        event.addListener(new FoundryModifierReloadListener());
    }
}
