package com.devmod.arena.network;

import java.util.Objects;

import net.minecraft.server.level.ServerPlayer;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

import com.devmod.network.ChannelId;
import com.devmod.network.NetworkHandler;
import com.devmod.network.ZoneDebugPayload;
import com.devmod.network.handlers.NetworkHandlerBase;
import com.devmod.network.handlers.PayloadRegistrar;
import com.devmod.runtime.environment.EnvironmentSyncPayload;

import static com.devmod.network.PayloadValidation.PayloadLimits;
import static com.devmod.network.PayloadValidation.validated;

/**
 * Domain-specific network handler for arena-related payloads.
 * Handles build progress, environment sync, and zone debug.
 */
public final class ArenaNetworkHandler extends NetworkHandlerBase implements PayloadRegistrar {

    public static final ArenaNetworkHandler INSTANCE = new ArenaNetworkHandler();

    private ArenaNetworkHandler() {}

    @Override
    public String getDomainName() {
        return "arena";
    }

    @Override
    public void registerPayloads(RegisterPayloadHandlersEvent event) {
        // BUILD_PROGRESS: Server -> Client arena build progress updates
        event.registrar(ChannelId.BUILD_PROGRESS.asString()).playToClient(
            nn(BuildProgressPayload.TYPE),
            nn(BuildProgressPayload.STREAM_CODEC),
            validated((payload, context) -> {
                if (FMLEnvironment.dist == Dist.CLIENT) {
                    observeFuture(context.enqueueWork(() ->
                        NetworkHandler.withClientHooks(hooks -> hooks.handleBuildProgress(payload))), "build progress");
                }
            }, PayloadLimits.SMALL)
        );

        // ENVIRONMENT_SYNC: Server -> Client environment state (frozen time, biome overrides)
        event.registrar(ChannelId.ENVIRONMENT_SYNC.asString()).playToClient(
            nn(EnvironmentSyncPayload.TYPE),
            nn(EnvironmentSyncPayload.STREAM_CODEC),
            validated((payload, context) -> {
                if (FMLEnvironment.dist == Dist.CLIENT) {
                    observeFuture(context.enqueueWork(() ->
                        NetworkHandler.withClientHooks(hooks -> hooks.handleEnvironmentSync(payload))), "environment sync");
                }
            }, PayloadLimits.SMALL)
        );

        // ZONE_DEBUG: Server -> Client zone boundary visualization
        event.registrar(ChannelId.ZONE_DEBUG.asString()).playToClient(
            nn(ZoneDebugPayload.TYPE),
            nn(ZoneDebugPayload.STREAM_CODEC),
            validated((payload, context) -> {
                if (FMLEnvironment.dist == Dist.CLIENT) {
                    observeFuture(context.enqueueWork(() ->
                        NetworkHandler.withClientHooks(hooks -> hooks.handleZoneDebug(payload))), "zone debug");
                }
            }, PayloadLimits.LARGE)
        );
    }

    /**
     * Send environment sync to a player.
     * Used to sync frozen time and biome overrides for arena dimensions.
     */
    public static void sendEnvironmentSync(ServerPlayer player, EnvironmentSyncPayload payload) {
        sendPacket(Objects.requireNonNull(player), Objects.requireNonNull(payload));
    }

    /**
     * Send zone debug data to a player.
     * Used to enable/disable zone boundary visualization on client.
     */
    public static void sendZoneDebug(ServerPlayer player, ZoneDebugPayload payload) {
        sendPacket(Objects.requireNonNull(player), Objects.requireNonNull(payload));
    }
}
