package com.devmod.client.events;

import com.devmod.DevMod;

import static com.devmod.DevMod.MODID;
import com.devmod.actions.ActionIds;
import com.devmod.actions.ActionOrigin;
import com.devmod.actions.ActionRegistry;
import com.devmod.actions.client.ClientActionContexts;
import net.minecraft.world.InteractionHand; // <--- Important
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Mob;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

@EventBusSubscriber(modid = MODID, value = Dist.CLIENT)
public class InteractionEvents {

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        // 1. SAFETY: Execute only on Client side (avoids strange errors in singleplayer)
        if (!event.getLevel().isClientSide()) return;

        // 2. CRASH FIX: Execute only with main hand (Right)
        // Without this, the event fires twice and crashes the GUI
        if (event.getHand() != InteractionHand.MAIN_HAND) return;

        // Check if you have the right item
        if (event.getItemStack().getItem() == DevMod.VIEWER_ITEM.get()) {

            if (event.getTarget() instanceof Mob mob) {
                // Route through ActionRegistry for consistency
                ActionRegistry.invoke(ActionIds.UI_MOB_CONFIG_OPEN,
                    ClientActionContexts.forClient(ActionOrigin.EVENT, mob));

                // Block the normal action and stop input
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.SUCCESS);
            }
        }
    }
}
