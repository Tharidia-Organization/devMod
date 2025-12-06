package com.frenkvs.devmod.event.common;

import com.frenkvs.devmod.client.screen.MobConfigScreen;
import com.frenkvs.devmod.devmod;
import com.frenkvs.devmod.permission.PermissionManager;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand; // <--- Importante
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Mob;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

@EventBusSubscriber(modid = "devmod", value = Dist.CLIENT)
public class InteractionEvents {

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        // Check if player has OP level 4 or higher
        if (!PermissionManager.isClientOp()) return;
        
        // 1. SICUREZZA: Esegui solo sul lato Client (evita errori strani in singleplayer)
        if (!event.getLevel().isClientSide()) return;

        // 2. FIX DEL CRASH: Esegui solo con la mano principale (Destra)
        // Senza questo, l'evento scatta due volte e manda in tilt la GUI
        if (event.getHand() != InteractionHand.MAIN_HAND) return;

        // Controlla se hai l'item giusto
        if (event.getItemStack().getItem() == devmod.VIEWER_ITEM.get()) {

            if (event.getTarget() instanceof Mob mob) {
                // Apri la GUI
                Minecraft.getInstance().setScreen(new MobConfigScreen(mob));

                // Blocca l'azione normale e ferma l'input
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.SUCCESS);
            }
        }
    }
}