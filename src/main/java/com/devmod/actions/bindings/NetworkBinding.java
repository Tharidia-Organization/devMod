package com.devmod.actions.bindings;

import java.util.Objects;

import com.devmod.actions.ActionOrigin;

/**
 * Network packet binding adapter. Represents an action triggered by a network packet.
 * Always uses {@link ActionOrigin#NETWORK} (untrusted).
 */
public record NetworkBinding(
    String actionId,
    String networkChannel
) implements ActionBinding {

    public NetworkBinding {
        Objects.requireNonNull(actionId, "actionId");
        Objects.requireNonNull(networkChannel, "networkChannel");
    }

    @Override
    public ActionOrigin origin() {
        return ActionOrigin.NETWORK;
    }

    @Override
    public String describe() {
        return "Network[" + networkChannel + " -> " + actionId + "]";
    }
}
