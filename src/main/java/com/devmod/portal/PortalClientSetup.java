package com.devmod.portal;

import com.devmod.DevMod;
import com.devmod.portal.block.CustomPortalBlock;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;

/**
 * Client-side setup for the portal module.
 * Registers block and item color handlers for tinting.
 */
@EventBusSubscriber(modid = DevMod.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class PortalClientSetup {
    private PortalClientSetup() {}

    /**
     * Registers block color handlers for the custom portal block.
     * The portal block uses a grayscale texture that is tinted based on the COLOR property.
     */
    @SubscribeEvent
    public static void registerBlockColors(RegisterColorHandlersEvent.Block event) {
        event.register(
            (state, level, pos, tintIndex) -> {
                if (tintIndex == 0) {
                    PortalColor color = state.getValue(CustomPortalBlock.COLOR);
                    return color.getColor();
                }
                return 0xFFFFFF;
            },
            PortalBlocks.CUSTOM_PORTAL.get()
        );
        DevMod.LOGGER.debug("[Portal] Registered block colors for custom portal");
    }

    // Item color handler removed - using pre-colored textures instead
}
