package com.devmod.foundry.network;

import java.util.Objects;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import com.devmod.DevMod;
import com.devmod.foundry.menu.FoundryStencilTableMenu;

/**
 * Network handler for Foundry module packets.
 */
@SuppressWarnings("removal")
@EventBusSubscriber(modid = DevMod.MODID, bus = EventBusSubscriber.Bus.MOD)
public final class FoundryNetworking {
    private FoundryNetworking() {}

    @SubscribeEvent
    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar(DevMod.MODID)
            .versioned("1.0.0")
            .optional();

        // Stencil Table pattern selection
        registrar.playToServer(
            StencilTableSelectPacket.TYPE,
            StencilTableSelectPacket.STREAM_CODEC,
            FoundryNetworking::handleStencilTableSelect
        );

        DevMod.LOGGER.info("[Foundry] Network payloads registered");
    }

    private static void handleStencilTableSelect(StencilTableSelectPacket packet, IPayloadContext context) {
        var unused = context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            AbstractContainerMenu menu = player.containerMenu;
            if (!(menu instanceof FoundryStencilTableMenu stencilMenu)) {
                return;
            }
            stencilMenu.selectPatternById(packet.patternId());
        });
    }

    /**
     * Sends a packet to the server.
     */
    public static void sendToServer(StencilTableSelectPacket packet) {
        PacketDistributor.sendToServer(Objects.requireNonNull(packet));
    }
}
