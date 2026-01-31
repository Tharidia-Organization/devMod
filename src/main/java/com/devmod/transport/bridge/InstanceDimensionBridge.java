package com.devmod.transport.bridge;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import com.devmod.runtime.DynamicDimensionManager;
import com.devmod.runtime.InstanceData;
import com.devmod.runtime.InstanceManager;
import com.devmod.runtime.InstanceRegistry;
import com.devmod.transport.TransportColor;
import com.devmod.transport.TransportData;
import com.devmod.transport.TransportRegistry;
import com.devmod.transport.executor.RecoveryManager;

/**
 * Bridge between the Transport system and Instance dimensions.
 *
 * <p>Provides:
 * <ul>
 *   <li>Teleportation to/from instance dimensions</li>
 *   <li>Instance lifecycle integration</li>
 *   <li>Recovery point management for instance exits</li>
 * </ul>
 *
 * <p>Integrates with InstanceManager, InstanceRegistry, and DynamicDimensionManager
 * for seamless instance dimension teleportation and lifecycle management.
 */
public final class InstanceDimensionBridge {
    private static final Logger LOGGER = LoggerFactory.getLogger(InstanceDimensionBridge.class);

    public static final InstanceDimensionBridge INSTANCE = new InstanceDimensionBridge();

    private InstanceDimensionBridge() {}

    /**
     * Checks if instance dimensions are available for transport.
     */
    public boolean isAvailable() {
        return InstanceManager.INSTANCE.isReady();
    }

    /**
     * Teleports a player to an instance dimension.
     *
     * @param player The player to teleport
     * @param instanceId The instance UUID
     * @param spawnPos Position within the instance (optional, uses default if null)
     * @return true if teleport was successful
     */
    public boolean teleportToInstance(
            ServerPlayer player,
            UUID instanceId,
            @Nullable BlockPos spawnPos) {

        if (!isAvailable()) {
            LOGGER.warn("[InstanceBridge] Instance dimensions not available");
            return false;
        }

        // Verify instance exists
        Optional<InstanceData> instanceOpt = InstanceRegistry.INSTANCE.getInstance(instanceId);
        if (instanceOpt.isEmpty()) {
            LOGGER.warn("[InstanceBridge] Instance {} not found", instanceId);
            return false;
        }

        // Save recovery point before entering instance
        RecoveryManager.INSTANCE.saveRecoveryPoint(player);

        // Teleport player to instance
        boolean success = DynamicDimensionManager.INSTANCE.teleportToInstance(player, instanceId);

        if (success) {
            LOGGER.info("[InstanceBridge] Teleported {} to instance {}", player.getName().getString(), instanceId);
        } else {
            LOGGER.error("[InstanceBridge] Failed to teleport {} to instance {}", player.getName().getString(), instanceId);
        }

        return success;
    }

    /**
     * Teleports a player out of an instance, back to their recovery point.
     *
     * @param player The player to teleport
     * @return true if teleport was successful
     */
    public boolean exitInstance(ServerPlayer player) {
        // Check if player is in an instance
        if (!isPlayerInInstance(player)) {
            LOGGER.debug("[InstanceBridge] Player {} is not in an instance", player.getName().getString());
            return false;
        }

        // Try to restore to recovery point
        if (RecoveryManager.INSTANCE.hasRecoveryPoint(Objects.requireNonNull(player.getUUID()))) {
            return RecoveryManager.INSTANCE.restorePlayer(player);
        }

        // Fallback: teleport to overworld spawn
        MinecraftServer server = player.getServer();
        if (server == null) {
            return false;
        }

        ServerLevel overworld = server.overworld();
        BlockPos spawn = overworld.getSharedSpawnPos();

        player.teleportTo(
            overworld,
            spawn.getX() + 0.5,
            spawn.getY() + 0.5,
            spawn.getZ() + 0.5,
            player.getYRot(),
            player.getXRot()
        );

        LOGGER.info("[InstanceBridge] Exited {} from instance to overworld spawn", player.getName().getString());
        return true;
    }

    /**
     * Checks if a player is currently in an instance dimension.
     */
    public boolean isPlayerInInstance(ServerPlayer player) {
        return InstanceManager.INSTANCE.isPlayerInInstance(player.getUUID());
    }

    /**
     * Gets the instance ID for a player's current dimension.
     *
     * @return Instance UUID, or empty if not in an instance
     */
    public Optional<UUID> getPlayerInstance(ServerPlayer player) {
        return Optional.ofNullable(InstanceRegistry.INSTANCE.getPlayerInstanceId(player.getUUID()));
    }

    /**
     * Creates a transport node linked to an instance.
     *
     * @param registry The transport registry
     * @param instanceId The instance to link to
     * @param displayName Display name for the node
     * @return The created transport data, or empty if failed
     */
    public Optional<TransportData> createInstanceNode(
            TransportRegistry registry,
            UUID instanceId,
            String displayName) {

        Optional<InstanceData> instanceOpt = InstanceRegistry.INSTANCE.getInstance(instanceId);
        if (instanceOpt.isEmpty()) {
            LOGGER.warn("[InstanceBridge] Cannot create instance node - instance {} not found", instanceId);
            return Optional.empty();
        }

        InstanceData instance = instanceOpt.get();
        ResourceKey<Level> dimensionKey = instance.getDimensionKey();
        if (dimensionKey == null) {
            LOGGER.warn("[InstanceBridge] Cannot create instance node - instance {} has no dimension", instanceId);
            return Optional.empty();
        }

        // Get arena center as spawn position
        BlockPos spawnPos = instance.getArenaCenter();
        if (spawnPos == null) {
            spawnPos = BlockPos.ZERO;
        }

        ResourceLocation dimLoc = dimensionKey.location();

        // Create transport node for the instance
        TransportData nodeData = TransportData.createZone(
            TransportColor.PURPLE,
            dimLoc, spawnPos,
            dimLoc, spawnPos,
            displayName
        );

        registry.register(nodeData);
        LOGGER.info("[InstanceBridge] Created transport node for instance {}: {}", instanceId, displayName);

        return Optional.of(nodeData);
    }

    /**
     * Handles instance end event - cleans up any transport nodes.
     *
     * @param server The Minecraft server
     * @param instanceId The instance that ended
     */
    public void onInstanceEnd(MinecraftServer server, UUID instanceId) {
        LOGGER.info("[InstanceBridge] Instance end event received for {}", instanceId);

        // Get transport registry and clean up any nodes associated with this instance
        ServerLevel overworld = server.overworld();
        TransportRegistry registry = TransportRegistry.get(overworld);

        // Find and remove transport nodes that were created for this instance
        // Instance nodes use PURPLE color by convention
        for (TransportData node : registry.getByColor(TransportColor.PURPLE)) {
            String nodeName = node.displayName();
            if (nodeName != null && nodeName.contains(instanceId.toString())) {
                registry.unregister(node.id());
                LOGGER.debug("[InstanceBridge] Removed transport node {} for ended instance {}", node.id(), instanceId);
            }
        }
    }
}
