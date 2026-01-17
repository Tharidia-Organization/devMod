package com.devmod.runtime.network;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.server.level.ServerPlayer;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import com.devmod.config.Config;
import com.devmod.network.ChannelId;
import com.devmod.network.PayloadValidation.PayloadLimits;
import com.devmod.network.handlers.NetworkHandlerBase;
import com.devmod.network.handlers.PayloadRegistrar;
import com.devmod.runtime.InstanceData;
import com.devmod.runtime.InstanceManager;
import com.devmod.runtime.InstanceRegistry;

import static com.devmod.network.PayloadValidation.validated;

/**
 * Network handler for the Admin Instance Control Panel.
 * Handles syncing instance data to clients and processing admin actions.
 */
public final class AdminInstanceNetworkHandler extends NetworkHandlerBase implements PayloadRegistrar {
    private static final Logger LOGGER = LoggerFactory.getLogger(AdminInstanceNetworkHandler.class);

    // Cached reflection for client handlers
    private static final Method APPLY_SYNC_METHOD;

    static {
        Method syncMethod = null;

        if (FMLEnvironment.dist == Dist.CLIENT) {
            try {
                Class<?> handlerClass = Class.forName("com.devmod.client.ui.admin.AdminInstanceClientHandler");
                syncMethod = handlerClass.getMethod("applySync", AdminInstanceSyncPayload.class);
            } catch (ClassNotFoundException | NoSuchMethodException e) {
                // Expected on dedicated servers
            }
        }

        APPLY_SYNC_METHOD = syncMethod;
    }

    public static final AdminInstanceNetworkHandler INSTANCE = new AdminInstanceNetworkHandler();

    private AdminInstanceNetworkHandler() {}

    /** Consume a future to satisfy FutureReturnValueIgnored warnings. */
    private static void ignoreFuture(java.util.concurrent.CompletableFuture<?> future) {
        if (future == null) {
            LOGGER.trace("Future was null");
        }
    }

    @Override
    public String getDomainName() {
        return "admin_instance";
    }

    @Override
    public void registerPayloads(RegisterPayloadHandlersEvent event) {
        // ADMIN_INSTANCE_SYNC: Server -> Client (sync instance data)
        event.registrar(ChannelId.ADMIN_INSTANCE_SYNC.asString()).playToClient(
            nn(AdminInstanceSyncPayload.TYPE),
            nn(AdminInstanceSyncPayload.STREAM_CODEC),
            validated(AdminInstanceNetworkHandler::handleSyncClient, PayloadLimits.LARGE)
        );

        // ADMIN_INSTANCE_ACTION: Client -> Server (admin actions)
        event.registrar(ChannelId.ADMIN_INSTANCE_ACTION.asString()).playToServer(
            nn(AdminInstanceActionPayload.TYPE),
            nn(AdminInstanceActionPayload.STREAM_CODEC),
            validated(AdminInstanceNetworkHandler::handleActionServer, PayloadLimits.SMALL)
        );
    }

    // =========================================================================
    // CLIENT HANDLERS
    // =========================================================================

    private static void handleSyncClient(AdminInstanceSyncPayload payload, IPayloadContext ctx) {
        if (FMLEnvironment.dist != Dist.CLIENT) {
            return;
        }

        ignoreFuture(ctx.enqueueWork(() -> {
            try {
                applySync(payload);
            } catch (Exception e) {
                LOGGER.error("[AdminInstanceNetwork] Failed to apply sync", e);
            }
        }));
    }

    private static void applySync(AdminInstanceSyncPayload payload) {
        if (APPLY_SYNC_METHOD == null) {
            return; // Client handler not available
        }
        try {
            APPLY_SYNC_METHOD.invoke(null, payload);
        } catch (Exception e) {
            LOGGER.debug("[AdminInstanceNetwork] Client sync handler unavailable: {}", e.getMessage());
        }
    }

    // =========================================================================
    // SERVER HANDLERS
    // =========================================================================

    private static void handleActionServer(AdminInstanceActionPayload payload, IPayloadContext ctx) {
        ignoreFuture(ctx.enqueueWork(() -> {
            try {
                if (ctx.player() instanceof ServerPlayer player) {
                    processAction(player, payload);
                }
            } catch (Exception e) {
                LOGGER.error("[AdminInstanceNetwork] Failed to handle action", e);
            }
        }));
    }

    private static void processAction(ServerPlayer player, AdminInstanceActionPayload payload) {
        // Check permission
        if (!hasAdminPermission(player)) {
            LOGGER.warn("[AdminInstanceNetwork] Player {} attempted admin action without permission",
                player.getName().getString());
            return;
        }

        AdminInstanceActionPayload.ActionType action = payload.action();
        UUID instanceId = payload.instanceId();
        UUID playerId = payload.playerId();

        LOGGER.info("[AdminInstanceNetwork] Player {} performing action {} on instance {}",
            player.getName().getString(), action, instanceId);

        switch (action) {
            case REFRESH -> {
                // Send updated sync to requesting player
                sendSyncToPlayer(player);
            }

            case END_SUCCESS -> {
                if (instanceId != null) {
                    InstanceManager.INSTANCE.endInstanceQuest(instanceId, true, "Admin terminated (success)");
                    sendSyncToPlayer(player);
                }
            }

            case END_FAILURE -> {
                if (instanceId != null) {
                    InstanceManager.INSTANCE.endInstanceQuest(instanceId, false, "Admin terminated");
                    sendSyncToPlayer(player);
                }
            }

            case KICK_PLAYER -> {
                if (instanceId != null && playerId != null) {
                    kickPlayerFromInstance(playerId, instanceId);
                    sendSyncToPlayer(player);
                }
            }

            case FORCE_DESTROY -> {
                if (instanceId != null) {
                    forceDestroyInstance(instanceId);
                    sendSyncToPlayer(player);
                }
            }
        }
    }

    private static void kickPlayerFromInstance(UUID playerId, UUID instanceId) {
        InstanceManager.INSTANCE.forceEndPlayerInstances(playerId);
        LOGGER.info("[AdminInstanceNetwork] Kicked player {} from instance {}", playerId, instanceId);
    }

    private static void forceDestroyInstance(UUID instanceId) {
        InstanceRegistry.INSTANCE.scheduleDestruction(instanceId);
        InstanceRegistry.INSTANCE.processPendingDestructions();
        LOGGER.info("[AdminInstanceNetwork] Force destroyed instance {}", instanceId);
    }

    // =========================================================================
    // PUBLIC API
    // =========================================================================

    /**
     * Send instance sync data to a player.
     */
    public static void sendSyncToPlayer(ServerPlayer player) {
        AdminInstanceSyncPayload payload = buildSyncPayload();
        PacketDistributor.sendToPlayer(Objects.requireNonNull(player, "player"), payload);
    }

    /**
     * Build a sync payload with current instance data.
     */
    public static AdminInstanceSyncPayload buildSyncPayload() {
        List<AdminInstanceSyncPayload.InstanceSnapshot> snapshots = new ArrayList<>();

        for (InstanceData instance : InstanceRegistry.INSTANCE.getAllInstances()) {
            // Get player names
            List<String> playerNames = new ArrayList<>();
            for (UUID playerId : instance.getCurrentPlayers()) {
                String name = getPlayerName(playerId);
                if (name != null) {
                    playerNames.add(name);
                }
            }

            long ageSeconds = instance.getAge() / 1000;

            AdminInstanceSyncPayload.InstanceSnapshot snapshot = new AdminInstanceSyncPayload.InstanceSnapshot(
                instance.getInstanceId(),
                instance.getState().ordinal(),
                instance.getArenaTemplate(),
                playerNames,
                instance.getCurrentWave(),
                instance.getTotalWaves(),
                ageSeconds,
                instance.getCurrentPlayers().size(),
                instance.getMaxPlayers()
            );

            snapshots.add(snapshot);
        }

        return new AdminInstanceSyncPayload(
            snapshots,
            InstanceRegistry.INSTANCE.getTotalPlayersInInstances(),
            InstanceRegistry.INSTANCE.getActiveInstanceCount(),
            InstanceRegistry.INSTANCE.getPendingDestruction().size()
        );
    }

    /**
     * Check if a player has admin permission for the instance panel.
     */
    public static boolean hasAdminPermission(ServerPlayer player) {
        int requiredLevel = Config.ADMIN_PANEL_PERMISSION_LEVEL.get();
        return player.hasPermissions(requiredLevel);
    }

    /**
     * Get player name by UUID (from server player list or cache).
     */
    private static String getPlayerName(UUID playerId) {
        if (playerId == null) return null;

        var server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null) return null;

        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        if (player != null) {
            return player.getName().getString();
        }

        // Try to get cached name from user cache
        var profile = server.getProfileCache();
        if (profile != null) {
            var cached = profile.get(playerId);
            if (cached.isPresent()) {
                return cached.get().getName();
            }
        }

        return null;
    }
}
