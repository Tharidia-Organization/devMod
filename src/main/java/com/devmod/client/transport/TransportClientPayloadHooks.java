package com.devmod.client.transport;

import javax.annotation.Nonnull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.client.Minecraft;

import com.devmod.transport.network.TransportChargeUpdatePayload;
import com.devmod.transport.network.TransportConfigOpenPayload;
import com.devmod.transport.network.TransportCountdownPayload;
import com.devmod.transport.network.TransportNetworkListPayload;
import com.devmod.transport.network.TransportPartyStatusPayload;
import com.devmod.transport.network.TransportStatePayload;

/**
 * Client-side payload handlers for transport network packets.
 * These methods are called from the network handler when packets arrive.
 */
public final class TransportClientPayloadHooks {
    private static final Logger LOGGER = LoggerFactory.getLogger(TransportClientPayloadHooks.class);

    private TransportClientPayloadHooks() {}

    /** Singleton overlay instance for HUD rendering. */
    private static final TransportOverlay OVERLAY = new TransportOverlay();

    /**
     * Gets the transport overlay instance for registration.
     */
    @Nonnull
    public static TransportOverlay getOverlay() {
        return OVERLAY;
    }

    /**
     * Handles TRANSPORT_CONFIG_OPEN payload (channel 210).
     * Opens the transport configuration screen.
     */
    public static void handleConfigOpen(@Nonnull TransportConfigOpenPayload payload) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> TransportConfigScreen.open(payload));
    }

    /**
     * Handles TRANSPORT_STATE payload (channel 212).
     * Updates the transport overlay state.
     */
    public static void handleStateUpdate(@Nonnull TransportStatePayload payload) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            if (payload.inTransport()) {
                OVERLAY.updateState(payload);
            } else {
                OVERLAY.reset();
            }
        });
    }

    /**
     * Handles TRANSPORT_CHARGE_UPDATE payload (channel 213).
     * Updates the charging progress in the overlay.
     */
    public static void handleChargeUpdate(@Nonnull TransportChargeUpdatePayload payload) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> OVERLAY.updateCharge(payload));
    }

    /**
     * Handles TRANSPORT_NETWORK_LIST payload (channel 215).
     * Opens or updates the network selection screen.
     */
    public static void handleNetworkList(@Nonnull TransportNetworkListPayload payload) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            // TODO: Open TransportNetworkSelectScreen when implemented
            LOGGER.debug("Received network list: {} nodes in network '{}'",
                payload.nodes().size(), payload.networkName());
        });
    }

    /**
     * Handles TRANSPORT_COUNTDOWN payload (channel 216).
     * Updates countdown display in overlay.
     */
    public static void handleCountdown(@Nonnull TransportCountdownPayload payload) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> OVERLAY.updateCountdown(payload));
    }

    /**
     * Handles TRANSPORT_PARTY_STATUS payload (channel 217).
     * Updates party teleport status in overlay.
     */
    public static void handlePartyStatus(@Nonnull TransportPartyStatusPayload payload) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> OVERLAY.updatePartyStatus(payload));
    }
}
