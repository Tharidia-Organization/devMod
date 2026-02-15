package com.devmod.client.nexus;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import com.devmod.runtime.network.NexusClientBridge;
import com.devmod.runtime.network.NexusDialogPayload;
import com.devmod.runtime.network.NexusLogSnapshotPayload;
import com.devmod.runtime.network.NexusUiPayload;

/**
 * Client-side implementation of {@link NexusClientBridge}.
 * Delegates to existing client handler without reflection.
 */
@OnlyIn(Dist.CLIENT)
public final class ClientNexusBridgeImpl implements NexusClientBridge {

    @Override
    public void openDialog(NexusDialogPayload payload) {
        NexusDialogClientHandler.openDialog(payload);
    }

    @Override
    public void openUi(NexusUiPayload payload) {
        NexusDialogClientHandler.openUi(payload);
    }

    @Override
    public void applyLogSnapshot(NexusLogSnapshotPayload payload) {
        NexusDialogClientHandler.applyLogSnapshot(payload);
    }
}
