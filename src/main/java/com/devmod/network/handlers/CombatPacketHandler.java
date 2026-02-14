package com.devmod.network.handlers;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import com.devmod.config.TesterModality;
import com.devmod.endurance.CombatFlowSyncPayload;
import com.devmod.network.ChannelId;
import com.devmod.network.ImpactSyncPayload;
import com.devmod.network.NetworkHandler;
import com.devmod.network.PayloadValidation.PayloadLimits;

import static com.devmod.network.PayloadValidation.validated;

/**
 * Domain-specific handler for combat-related payloads:
 * combat flow sync and impact sync.
 */
public final class CombatPacketHandler extends NetworkHandlerBase implements PayloadRegistrar {

    public static final CombatPacketHandler INSTANCE = new CombatPacketHandler();

    private CombatPacketHandler() {}

    @Override
    public String getDomainName() {
        return "combat";
    }

    @Override
    public void registerPayloads(RegisterPayloadHandlersEvent event) {
        if (TesterModality.isEnabled()) {
            event.registrar(ChannelId.COMBAT_FLOW_SYNC.asString()).playToClient(
                nn(CombatFlowSyncPayload.TYPE),
                nn(CombatFlowSyncPayload.STREAM_CODEC),
                validated(CombatPacketHandler::handleCombatFlowSync, PayloadLimits.SMALL)
            );
        }
        event.registrar(ChannelId.IMPACT_SYNC.asString()).playToClient(
            nn(ImpactSyncPayload.TYPE),
            nn(ImpactSyncPayload.STREAM_CODEC),
            validated(CombatPacketHandler::handleImpactSync, PayloadLimits.SMALL)
        );
    }

    // =================================================================================
    // COMBAT FLOW SYNC (client-side)
    // =================================================================================
    private static void handleCombatFlowSync(CombatFlowSyncPayload payload, IPayloadContext context) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            observeFuture(context.enqueueWork(() ->
                NetworkHandler.withClientHooks(hooks -> hooks.handleCombatFlowSync(payload))), "combat flow sync");
        }
    }

    // =================================================================================
    // IMPACT SYNC (client-side)
    // =================================================================================
    private static void handleImpactSync(ImpactSyncPayload payload, IPayloadContext context) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            observeFuture(context.enqueueWork(() ->
                NetworkHandler.withClientHooks(hooks -> hooks.handleImpactSync(payload))), "impact sync");
        }
    }
}
