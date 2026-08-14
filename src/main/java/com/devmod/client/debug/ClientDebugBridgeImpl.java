package com.devmod.client.debug;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import com.devmod.debug.BeesPayload;
import com.devmod.debug.BlockUpdatesPayload;
import com.devmod.debug.BrainsPayload;
import com.devmod.debug.DebugClientBridge;
import com.devmod.debug.DebugSyncPayload;
import com.devmod.debug.EntityGoalsPayload;
import com.devmod.debug.EntityPathingPayload;
import com.devmod.debug.POIPayload;
import com.devmod.debug.RaidsPayload;
import com.devmod.debug.StructuresPayload;
import com.devmod.debug.client.EntityScannerClientHandler;
import com.devmod.debug.client.NativeDebugClientStore;
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
    public void handleStructures(StructuresPayload payload) {
        NativeDebugClientStore.setStructures(payload);
    }

    @Override
    public void handlePOI(POIPayload payload) {
        NativeDebugClientStore.setPois(payload);
    }

    @Override
    public void handleRaids(RaidsPayload payload) {
        NativeDebugClientStore.setRaids(payload);
    }

    @Override
    public void handleBrains(BrainsPayload payload) {
        NativeDebugClientStore.setBrains(payload);
    }

    @Override
    public void handleGoals(EntityGoalsPayload payload) {
        NativeDebugClientStore.setGoals(payload);
    }

    @Override
    public void handleBees(BeesPayload payload) {
        NativeDebugClientStore.setBees(payload);
    }

    @Override
    public void handleBlockUpdates(BlockUpdatesPayload payload) {
        NativeDebugClientStore.addBlockUpdates(payload);
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
