package com.frenkvs.devmod;

import static com.frenkvs.devmod.DevMod.MODID;
import net.minecraft.client.Minecraft;
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
                // Open the GUI
                Minecraft.getInstance().setScreen(new MobConfigScreen(mob));

                // Block the normal action and stop input
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.SUCCESS);
            }
        }
    }
}
