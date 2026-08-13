package com.devmod.transport.executor;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.Vec3;

import com.devmod.debug.DiagnosticLogger;
import com.devmod.transport.TransportData;
import com.devmod.transport.TransportMode;
import com.devmod.transport.TransportRegistry;
import com.devmod.transport.TransportState;
import com.devmod.transport.executor.ChargeManager.ChargeCompleteEvent;
import com.devmod.transport.executor.CooldownManager.CooldownType;
import com.devmod.transport.executor.TransportEffectManager.TransportPhase;
import com.devmod.transport.manager.RiftGateManager;
import com.devmod.transport.network.TransportChargeUpdatePayload;
import com.devmod.transport.network.TransportNetworkHandler;
import com.devmod.transport.network.TransportStatePayload;

/**
 * Central executor for all transport operations.
 * Coordinates charging, cooldowns, teleportation, and effects.
 *
 * <p>This singleton orchestrates:
 * <ul>
 *   <li>{@link ChargeManager} - Player charging states</li>
 *   <li>{@link CooldownManager} - Post-teleport cooldowns</li>
 *   <li>{@link TransportEffectManager} - Particles, sounds, BossBars</li>
 * </ul>
 */
public final class TransportExecutor {
    public static final TransportExecutor INSTANCE = new TransportExecutor();

    private final ChargeManager chargeManager = new ChargeManager();
    private final CooldownManager cooldownManager = new CooldownManager();
    private final TransportEffectManager effectManager = new TransportEffectManager();
    private static final int CHARGE_UPDATE_INTERVAL = 2;
    private final Map<UUID, Long> lastChargeSync = new ConcurrentHashMap<>();
    private final Set<UUID> chargingPlayersLastTick = ConcurrentHashMap.newKeySet();

    private TransportExecutor() {}

    // ============================================================================
    // MANAGER ACCESS
    // ============================================================================

    @Nonnull
    public ChargeManager getChargeManager() {
        return chargeManager;
    }

    @Nonnull
    public CooldownManager getCooldownManager() {
        return cooldownManager;
    }

    @Nonnull
    public TransportEffectManager getEffectManager() {
        return effectManager;
    }

    // ============================================================================
    // CHARGING
    // ============================================================================

    private record DestinationInfo(String name, int distance) {}

    /**
     * Starts charging for a player at a transport node.
     * Validates cooldowns and node configuration before starting.
     *
     * @param player the player
     * @param node the transport node
     * @return true if charging started successfully
     */
    public boolean startCharging(@Nonnull Player player, @Nonnull TransportData node) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return false;
        }
        DiagnosticLogger.transport("startCharging: player=%s, node=%s, mode=%s",
            player.getName().getString(), node.displayName(), node.mode());

        ServerLevel level = serverPlayer.serverLevel();
        long currentTick = level.getGameTime();
        UUID playerId = Objects.requireNonNull(player.getUUID());
        UUID nodeId = Objects.requireNonNull(node.id());
        TransportRegistry registry = TransportRegistry.get(level);
        ChargeManager.ChargingState beforeState = chargeManager.getState(playerId).orElse(null);

        // Check arrival cooldown
        if (cooldownManager.isOnCooldown(playerId, nodeId, CooldownType.ARRIVAL, currentTick)) {
            return false;
        }

        // Check global cooldown
        if (cooldownManager.hasGlobalCooldown(playerId, currentTick)) {
            return false;
        }

        // Check if node is instant (HASTE or FLUX_CAPACITOR)
        if (node.isInstant()) {
            // Immediate teleport
            return executeTeleport(serverPlayer, node);
        }

        // Start charging
        chargeManager.startCharging(player, node);
        ChargeManager.ChargingState afterState = chargeManager.getState(playerId).orElse(null);
        if (afterState != null && (beforeState == null || !beforeState.nodeId().equals(afterState.nodeId()))) {
            sendChargeEnter(serverPlayer, node, registry, afterState);
        }

        // Play start sound
        node.getPosition().ifPresent(pos -> {
            effectManager.playPhaseSound(level, Objects.requireNonNull(pos), TransportPhase.CHARGE_START);
        });

        return true;
    }

    /**
     * Stops charging for a player.
     */
    public void stopCharging(@Nonnull Player player) {
        chargeManager.stopCharging(Objects.requireNonNull(player.getUUID()));
        if (player instanceof ServerPlayer serverPlayer) {
            sendChargeExit(serverPlayer);
        }
    }

    /**
     * Returns whether a player is currently charging.
     */
    public boolean isCharging(@Nonnull Player player) {
        return chargeManager.isCharging(Objects.requireNonNull(player.getUUID()));
    }

    /**
     * Gets the charge progress for a player (0-100).
     */
    public int getChargeProgress(@Nonnull Player player) {
        return chargeManager.getState(Objects.requireNonNull(player.getUUID()))
            .map(ChargeManager.ChargingState::getProgressPercent)
            .orElse(0);
    }

    // ============================================================================
    // TELEPORTATION
    // ============================================================================

    /**
     * Executes teleportation from a source node.
     * Automatically determines destination based on node mode.
     *
     * @param entity the entity to teleport
     * @param source the source transport node
     * @return true if teleport was successful
     */
    public boolean executeTeleport(@Nonnull Entity entity, @Nonnull TransportData source) {
        if (!(entity.level() instanceof ServerLevel level)) {
            return false;
        }
        DiagnosticLogger.transport("executeTeleport: entity=%s, source=%s, mode=%s",
            entity.getName().getString(), source.displayName(), source.mode());

        TransportRegistry registry = TransportRegistry.get(level);

        return switch (source.mode()) {
            case LINKED -> executeLinkTeleport(entity, source, registry, level);
            case NETWORK -> executeNetworkTeleport(entity, source, registry, level);
            case FIXED -> executeFixedTeleport(entity, source, level);
            case ZONE -> executeFixedTeleport(entity, source, level); // Same as FIXED
            case WAYPOINT -> false; // Requires GUI selection
            case INSTANCE -> false; // Handled by InstanceManager
            case RETURN -> executeReturnTeleport(entity, registry, level);
            case PARTY -> false; // Handled by party system
        };
    }

    private boolean executeLinkTeleport(
        @Nonnull Entity entity,
        @Nonnull TransportData source,
        @Nonnull TransportRegistry registry,
        @Nonnull ServerLevel sourceLevel
    ) {
        if (!source.isLinked()) {
            playErrorEffect(sourceLevel, source);
            return false;
        }

        // Safe Optional handling to prevent NoSuchElementException
        UUID linkedId = source.getLinkedNodeId().orElse(null);
        if (linkedId == null) {
            playErrorEffect(sourceLevel, source);
            return false;
        }

        Optional<TransportData> destOpt = registry.get(linkedId);
        TransportData dest = destOpt.orElse(null);
        if (dest == null || dest.getPosition().isEmpty()) {
            playErrorEffect(sourceLevel, source);
            return false;
        }

        return performTeleport(entity, source, dest, sourceLevel);
    }

    private boolean executeNetworkTeleport(
        @Nonnull Entity entity,
        @Nonnull TransportData source,
        @Nonnull TransportRegistry registry,
        @Nonnull ServerLevel sourceLevel
    ) {
        if (!source.hasNetwork()) {
            playErrorEffect(sourceLevel, source);
            return false;
        }

        if (source.selectionMode() == TransportData.NetworkSelectionMode.MANUAL
            && entity instanceof ServerPlayer serverPlayer) {
            TransportNetworkHandler.sendNetworkList(serverPlayer, source, registry);
            return true;
        }

        // Safe Optional handling to prevent NoSuchElementException
        String networkName = source.getNetworkName().orElse(null);
        if (networkName == null) {
            playErrorEffect(sourceLevel, source);
            return false;
        }

        Optional<TransportData> destOpt = registry.getNetworkDestination(
            networkName,
            source.id(),
            source.selectionMode()
        );

        if (destOpt.isEmpty()) {
            playErrorEffect(sourceLevel, source);
            return false;
        }

        return performTeleport(entity, source, destOpt.get(), sourceLevel);
    }

    private boolean executeFixedTeleport(
        @Nonnull Entity entity,
        @Nonnull TransportData source,
        @Nonnull ServerLevel sourceLevel
    ) {
        if (!source.hasFixedDestination()) {
            playErrorEffect(sourceLevel, source);
            return false;
        }

        return performTeleportToPosition(
            entity,
            source,
            null, // fixed destinations are raw coordinates, not a registered node
            source.fixedDestDim(),
            source.fixedDestPos(),
            sourceLevel
        );
    }

    private boolean executeReturnTeleport(
        @Nonnull Entity entity,
        @Nonnull TransportRegistry registry,
        @Nonnull ServerLevel sourceLevel
    ) {
        if (!(entity instanceof ServerPlayer player)) {
            return false;
        }

        Optional<TransportData> returnOpt = registry.getReturnPoint(player.getUUID());
        TransportData returnNode = returnOpt.orElse(null);
        if (returnNode == null || returnNode.getPosition().isEmpty()) {
            return false;
        }

        boolean success = performTeleportToPosition(
            entity,
            null, // No source node for return
            returnNode.id(),
            returnNode.dimension(),
            returnNode.position(),
            sourceLevel
        );

        if (success) {
            // Clear return point after use
            registry.clearReturnPoint(Objects.requireNonNull(player.getUUID()));
        }

        return success;
    }

    /**
     * Teleports an entity from one node to an explicitly chosen destination node.
     *
     * <p>Used by manual waypoint selection, where the destination comes from the
     * player's choice rather than from the source node's own mode. Callers must
     * already have verified that {@code dest} is reachable from {@code source}.
     */
    public boolean teleportToNode(
        @Nonnull Entity entity,
        @Nonnull TransportData source,
        @Nonnull TransportData dest
    ) {
        if (!(entity.level() instanceof ServerLevel sourceLevel)) {
            return false;
        }
        return performTeleport(entity, source, dest, sourceLevel);
    }

    /**
     * Performs the actual teleportation between two nodes.
     */
    private boolean performTeleport(
        @Nonnull Entity entity,
        @Nonnull TransportData source,
        @Nonnull TransportData dest,
        @Nonnull ServerLevel sourceLevel
    ) {
        return performTeleportToPosition(entity, source, dest.id(), dest.dimension(), dest.position(), sourceLevel);
    }

    /**
     * Performs teleportation to a specific position.
     *
     * @param destNodeId the destination node, when the destination is a registered node;
     *                   the arrival cooldown is keyed on it
     */
    private boolean performTeleportToPosition(
        @Nonnull Entity entity,
        @Nullable TransportData source,
        @Nullable UUID destNodeId,
        @Nullable ResourceLocation destDim,
        @Nullable BlockPos destPos,
        @Nonnull ServerLevel sourceLevel
    ) {
        if (destDim == null || destPos == null) {
            return false;
        }

        MinecraftServer server = sourceLevel.getServer();

        // Get destination level
        ServerLevel destLevel = server.getLevel(ResourceKey.create(Registries.DIMENSION, destDim));
        if (destLevel == null) {
            return false;
        }

        // Ensure chunk is loaded
        destLevel.getChunk(destPos);

        // Play departure effects
        if (source != null) {
            source.getPosition().ifPresent(pos -> {
                Vec3 posVec = Vec3.atCenterOf(pos);
                effectManager.spawnDepartureParticles(sourceLevel, posVec);
                effectManager.playPhaseSound(sourceLevel, pos, TransportPhase.TELEPORT_DEPART);
            });
        }

        // Teleport entity
        if (entity instanceof ServerPlayer player) {
            player.teleportTo(
                destLevel,
                destPos.getX() + 0.5,
                destPos.getY() + 0.5,
                destPos.getZ() + 0.5,
                player.getYRot(),
                player.getXRot()
            );
        } else if (destLevel != sourceLevel) {
            // Entity#teleportTo(x, y, z) only moves within the current level, so a
            // cross-dimension move has to go through changeDimension. It returns a
            // new instance in the destination level; the original is discarded.
            Entity moved = entity.changeDimension(new DimensionTransition(
                destLevel,
                new Vec3(destPos.getX() + 0.5, destPos.getY() + 0.5, destPos.getZ() + 0.5),
                Objects.requireNonNull(Vec3.ZERO),
                entity.getYRot(),
                entity.getXRot(),
                Objects.requireNonNull(DimensionTransition.DO_NOTHING)
            ));
            if (moved == null) {
                return false;
            }
        } else {
            entity.teleportTo(
                destPos.getX() + 0.5,
                destPos.getY() + 0.5,
                destPos.getZ() + 0.5
            );
        }

        // Play arrival effects
        Vec3 destPosVec = Vec3.atCenterOf(destPos);
        effectManager.spawnArrivalParticles(destLevel, destPosVec);
        effectManager.playPhaseSound(destLevel, destPos, TransportPhase.TELEPORT_ARRIVE);

        // Set arrival cooldown on the node that was arrived at, which is the node
        // startCharging() checks before letting the entity depart again.
        long currentTick = destLevel.getGameTime();
        cooldownManager.setCooldown(Objects.requireNonNull(entity.getUUID()), destNodeId, CooldownType.ARRIVAL, currentTick);

        return true;
    }

    private void playErrorEffect(@Nonnull ServerLevel level, @Nonnull TransportData node) {
        node.getPosition().ifPresent(pos -> {
            effectManager.playPhaseSound(level, Objects.requireNonNull(pos), TransportPhase.ERROR);
            effectManager.spawnStateParticles(level, Objects.requireNonNull(Vec3.atCenterOf(pos)), TransportState.ERROR, node.color(), 0);
        });
    }

    // ============================================================================
    // OVERLAY SYNC
    // ============================================================================

    private void sendChargeEnter(
        @Nonnull ServerPlayer player,
        @Nonnull TransportData node,
        @Nonnull TransportRegistry registry,
        @Nonnull ChargeManager.ChargingState state
    ) {
        DestinationInfo info = resolveDestinationInfo(node, registry);
        TransportStatePayload payload = TransportStatePayload.enter(
            TransportState.CHARGING,
            node.color(),
            state.currentTicks(),
            state.requiredTicks(),
            info.name(),
            info.distance()
        );
        TransportNetworkHandler.sendStateUpdate(player, payload);
        lastChargeSync.put(Objects.requireNonNull(player.getUUID()), player.serverLevel().getGameTime());
    }

    private void sendChargeUpdate(
        @Nonnull ServerPlayer player,
        @Nonnull ChargeManager.ChargingState state,
        long currentTick
    ) {
        long last = lastChargeSync.getOrDefault(Objects.requireNonNull(player.getUUID()), currentTick - CHARGE_UPDATE_INTERVAL);
        if (currentTick - last < CHARGE_UPDATE_INTERVAL) {
            return;
        }

        TransportChargeUpdatePayload payload = new TransportChargeUpdatePayload(
            state.currentTicks(),
            state.requiredTicks(),
            state.getProgressPercent()
        );
        TransportNetworkHandler.sendChargeUpdate(player, payload);
        lastChargeSync.put(Objects.requireNonNull(player.getUUID()), currentTick);
    }

    private void sendChargeCompleteUpdate(@Nonnull ServerPlayer player, @Nonnull TransportData node) {
        int required = node.getEffectiveChargeTime();
        TransportChargeUpdatePayload payload = new TransportChargeUpdatePayload(required, required, 100);
        TransportNetworkHandler.sendChargeUpdate(player, payload);
        lastChargeSync.put(Objects.requireNonNull(player.getUUID()), player.serverLevel().getGameTime());
    }

    private void sendChargeExit(@Nonnull ServerPlayer player) {
        TransportNetworkHandler.sendStateUpdate(player, TransportStatePayload.exit());
        lastChargeSync.remove(Objects.requireNonNull(player.getUUID()));
        chargingPlayersLastTick.remove(Objects.requireNonNull(player.getUUID()));
    }

    @Nonnull
    private DestinationInfo resolveDestinationInfo(
        @Nonnull TransportData source,
        @Nonnull TransportRegistry registry
    ) {
        String name = "";
        int distance = 0;

        switch (source.mode()) {
            case LINKED -> {
                var destOpt = source.getLinkedNodeId().flatMap(registry::get);
                if (destOpt.isPresent()) {
                    var dest = destOpt.get();
                    name = resolveNodeName(dest);
                    distance = computeDistance(
                        source.dimension(),
                        source.position(),
                        dest.dimension(),
                        dest.position()
                    );
                }
            }
            case NETWORK -> {
                String networkName = source.networkName();
                if (networkName != null && !networkName.isEmpty()) {
                    name = networkName;
                }
            }
            case FIXED, ZONE -> {
                String displayName = source.displayName();
                if (displayName != null && !displayName.isEmpty()) {
                    name = displayName;
                } else {
                    name = source.mode() == TransportMode.ZONE
                        ? "Zone"
                        : "Fixed Destination";
                }
                distance = computeDistance(
                    source.dimension(),
                    source.position(),
                    source.fixedDestDim(),
                    source.fixedDestPos()
                );
            }
            case WAYPOINT -> name = "Select Destination";
            case RETURN -> name = "Return Point";
            case PARTY -> name = "Party";
            case INSTANCE -> name = "Instance";
        }

        return new DestinationInfo(name, distance);
    }

    @Nonnull
    private static String resolveNodeName(@Nonnull TransportData node) {
        return node.resolveDisplayName();
    }

    private static int computeDistance(
        @Nullable ResourceLocation sourceDim,
        @Nullable BlockPos sourcePos,
        @Nullable ResourceLocation destDim,
        @Nullable BlockPos destPos
    ) {
        if (sourcePos == null || destPos == null || sourceDim == null || destDim == null) {
            return 0;
        }
        if (!sourceDim.equals(destDim)) {
            return 0;
        }
        double dist = Math.sqrt(sourcePos.distSqr(destPos));
        return (int) Math.round(dist);
    }

    // ============================================================================
    // COOLDOWNS
    // ============================================================================

    /**
     * Returns whether an entity is on any cooldown.
     */
    public boolean isOnCooldown(@Nonnull Entity entity) {
        if (!(entity.level() instanceof ServerLevel level)) {
            return false;
        }
        return cooldownManager.hasGlobalCooldown(Objects.requireNonNull(entity.getUUID()), level.getGameTime());
    }

    /**
     * Sets a cooldown for an entity.
     */
    public void setCooldown(@Nonnull Entity entity, int ticks) {
        if (entity.level() instanceof ServerLevel level) {
            cooldownManager.setCooldown(
                Objects.requireNonNull(entity.getUUID()),
                null,
                CooldownType.GLOBAL,
                level.getGameTime(),
                ticks
            );
        }
    }

    /**
     * Clears all cooldowns for an entity.
     */
    public void clearCooldown(@Nonnull Entity entity) {
        cooldownManager.clearCooldowns(Objects.requireNonNull(entity.getUUID()));
    }

    // ============================================================================
    // TICK
    // ============================================================================

    /**
     * Ticks the transport executor. Called once per server tick.
     */
    public void tick(@Nonnull MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        long currentTick = overworld.getGameTime();

        // Tick cooldown cleanup
        cooldownManager.tick(currentTick);

        // Tick charging for all levels
        for (ServerLevel level : server.getAllLevels()) {
            tickChargingForLevel(level);
        }

        // Process temporal node expirations
        TransportRegistry registry = TransportRegistry.get(overworld);
        registry.processExpirations(overworld, currentTick);

        // Tick transport managers
        CountdownManager.INSTANCE.tick(server);
        ArrivalManager.INSTANCE.tick(server);
        RecoveryManager.INSTANCE.tick(server);
        RiftGateManager.INSTANCE.tick(server);

        syncChargingOverlay(server);
    }

    private void tickChargingForLevel(@Nonnull ServerLevel level) {
        TransportRegistry registry = TransportRegistry.get(level);
        long currentTick = level.getGameTime();

        // Process charging completions
        List<ChargeCompleteEvent> completions = chargeManager.tick(level, ChargeManager.DEFAULT_POSITION_CHECKER);

        for (ChargeCompleteEvent event : completions) {
            Player player = level.getPlayerByUUID(event.playerId());
            if (player instanceof ServerPlayer serverPlayer) {
                Optional<TransportData> nodeOpt = registry.get(event.nodeId());

                if (nodeOpt.isPresent()) {
                    sendChargeCompleteUpdate(serverPlayer, nodeOpt.get());

                    // Play completion sound
                    effectManager.playPhaseSound(level, Objects.requireNonNull(event.nodePos()), TransportPhase.CHARGE_COMPLETE);

                    // Execute teleport
                    executeTeleport(serverPlayer, nodeOpt.get());
                }
            }
        }

        // Tick charging effects for all charging players
        for (ServerPlayer player : level.players()) {
            chargeManager.getState(player.getUUID()).ifPresent(state -> {
                sendChargeUpdate(player, state, currentTick);
                registry.get(state.nodeId()).ifPresent(node -> {
                    Vec3 pos = Vec3.atCenterOf(state.nodePos());
                    effectManager.tickChargingEffects(level, pos, state.color(), state.currentTicks(), state.requiredTicks());
                });
            });
        }
    }

    private void syncChargingOverlay(@Nonnull MinecraftServer server) {
        Set<UUID> currentCharging = new HashSet<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (chargeManager.isCharging(player.getUUID())) {
                currentCharging.add(player.getUUID());
            }
        }

        for (UUID previous : new HashSet<>(chargingPlayersLastTick)) {
            if (!currentCharging.contains(previous)) {
                ServerPlayer player = server.getPlayerList().getPlayer(previous);
                if (player != null) {
                    sendChargeExit(player);
                } else {
                    lastChargeSync.remove(previous);
                }
            }
        }

        chargingPlayersLastTick.clear();
        chargingPlayersLastTick.addAll(currentCharging);
    }

    // ============================================================================
    // SHUTDOWN
    // ============================================================================

    /**
     * Cleans up player-specific data (called on player disconnect).
     * Prevents memory leaks by removing player entries from all tracking maps.
     *
     * @param playerId the player's UUID
     */
    public void cleanupPlayer(@Nonnull UUID playerId) {
        chargeManager.stopCharging(playerId);
        cooldownManager.clearCooldowns(playerId);
        lastChargeSync.remove(playerId);
        chargingPlayersLastTick.remove(playerId);
    }

    /**
     * Cleans up all state (called on server shutdown).
     */
    public void shutdown() {
        chargeManager.clear();
        cooldownManager.clear();
        effectManager.clearAllBossBars();
        lastChargeSync.clear();
        chargingPlayersLastTick.clear();
    }
}
