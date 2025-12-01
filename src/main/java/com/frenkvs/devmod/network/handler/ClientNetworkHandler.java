package com.frenkvs.devmod.network.handler;

import com.frenkvs.devmod.event.client.WorldRenderEvents;
import com.frenkvs.devmod.network.payload.AggroLinkPayload;
import com.frenkvs.devmod.network.payload.PathRenderPayload;

// Client-only network handlers - this class will only be loaded on the client side
public class ClientNetworkHandler {
    
    public static void handleAggroLink(AggroLinkPayload payload) {
        WorldRenderEvents.addAggroLine(payload.sourceId(), payload.targetId());
    }

    public static void handlePathRender(PathRenderPayload payload) {
        // Debug: Log when packet is received on client
        System.out.println("[DevMod Client] Received PathRender packet for mob " + payload.mobId() + 
            " with " + (payload.pathNodes() != null ? payload.pathNodes().size() : 0) + " nodes");
        WorldRenderEvents.updateMobPath(
            payload.mobId(), payload.pathNodes(), payload.endNode(), payload.stuckPos()
        );
    }
}
