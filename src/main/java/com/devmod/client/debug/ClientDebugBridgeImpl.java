package com.devmod.client.debug;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import com.devmod.debug.DebugClientBridge;
import com.devmod.debug.DebugSyncPayload;
import com.devmod.debug.EntityPathingPayload;
import com.devmod.debug.client.EntityScannerClientHandler;
import com.devmod.debug.network.EntityScanDataPayload;

/**
 * Client-side implementation of {@link DebugClientBridge}.
 * Delegates to existing client handler classes without reflection.
 */
@OnlyIn(Dist.CLIENT)
public final class ClientDebugBridgeImpl implements DebugClientBridge {

    @Override
    public void handleDebugSync(DebugSyncPayload payload) {
        DebugNetworkClientHandler.handleDebugSync(payload);
    }

    @Override
    public void handleEntityPathing(EntityPathingPayload payload) {
        DebugNetworkClientHandler.handleEntityPathing(payload);
    }

    @Override
    public void handleScanData(EntityScanDataPayload payload) {
        EntityScannerClientHandler.handleScanData(payload, null);
    }

    @Override
    public void handleOpenScreen(EntityScanDataPayload.OpenScreenPayload payload) {
        EntityScannerClientHandler.handleOpenScreen(payload, null);
    }
}
