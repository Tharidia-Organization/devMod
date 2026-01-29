package com.devmod.transport.executor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nonnull;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import com.devmod.transport.TransportColor;
import com.devmod.transport.TransportData;

/**
 * Manages charging state for transport nodes.
 * Follows the pattern established in TelepadBlockEntity.
 *
 * <p>Default timing (Bibbia Estetica Rule 7):
 * <ul>
 *   <li>CHARGE_BASE: 40 ticks (2s)</li>
 *   <li>CHARGE_MIN: 0 (with HASTE/FLUX_CAPACITOR)</li>
 *   <li>CHARGE_MAX: 200 ticks (10s)</li>
 * </ul>
 */
public class ChargeManager {

    /**
     * Record representing current charging state for a player.
     */
    public record ChargingState(
        @Nonnull UUID nodeId,
        @Nonnull BlockPos nodePos,
        int currentTicks,
        int requiredTicks,
        @Nonnull TransportColor color
    ) {
        /**
         * Returns charge progress as percentage (0-100).
         */
        public int getProgressPercent() {
            if (requiredTicks <= 0) {
                return 100;
            }
            return Math.min(100, (currentTicks * 100) / requiredTicks);
        }

        /**
         * Returns whether charging is complete.
         */
        public boolean isComplete() {
            return currentTicks >= requiredTicks;
        }

        /**
         * Creates a new state with incremented tick.
         */
        public ChargingState tick() {
            return new ChargingState(nodeId, nodePos, currentTicks + 1, requiredTicks, color);
        }
    }

    private final Map<UUID, ChargingState> chargingPlayers = new ConcurrentHashMap<>();

    /**
     * Starts charging for a player at the given node.
     * Thread-safe: Uses compute() to atomically check and update charging state,
     * preventing TOCTOU race conditions.
     *
     * @param player the player
     * @param node the transport node
     */
    public void startCharging(@Nonnull Player player, @Nonnull TransportData node) {
        if (!(player instanceof ServerPlayer)) {
            return;
        }

        UUID playerId = player.getUUID();
        UUID nodeId = node.id();

        BlockPos nodePos = node.getPosition().orElse(null);
        if (nodePos == null) {
            return;
        }

        int chargeTime = node.getEffectiveChargeTime();
        TransportColor nodeColor = node.color();

        // Atomic check-and-update to prevent TOCTOU race condition
        chargingPlayers.compute(playerId, (id, existing) -> {
            // Don't restart charging if already charging at same node
            if (existing != null && existing.nodeId().equals(nodeId)) {
                return existing;
            }
            // Create new charging state
            return new ChargingState(nodeId, nodePos, 0, chargeTime, nodeColor);
        });
    }

    /**
     * Stops charging for a player.
     */
    public void stopCharging(@Nonnull UUID playerId) {
        chargingPlayers.remove(playerId);
    }

    /**
     * Returns whether a player is currently charging.
     */
    public boolean isCharging(@Nonnull UUID playerId) {
        return chargingPlayers.containsKey(playerId);
    }

    /**
     * Gets the current charging state for a player.
     */
    @Nonnull
    public Optional<ChargingState> getState(@Nonnull UUID playerId) {
        return Objects.requireNonNull(Optional.ofNullable(chargingPlayers.get(playerId)));
    }

    /**
     * Gets all players currently charging at a specific node.
     */
    @Nonnull
    public List<UUID> getPlayersChargingAt(@Nonnull UUID nodeId) {
        List<UUID> result = new ArrayList<>();
        for (Map.Entry<UUID, ChargingState> entry : chargingPlayers.entrySet()) {
            if (entry.getValue().nodeId().equals(nodeId)) {
                result.add(entry.getKey());
            }
        }
        return result;
    }

    /**
     * Ticks the charge manager, updating all charging states.
     *
     * @param level the server level
     * @param positionChecker function to check if player is still on the node
     * @return list of players whose charging completed this tick
     */
    @Nonnull
    public List<ChargeCompleteEvent> tick(@Nonnull ServerLevel level, @Nonnull PlayerPositionChecker positionChecker) {
        List<ChargeCompleteEvent> completed = new ArrayList<>();
        List<UUID> toRemove = new ArrayList<>();
        Map<UUID, ChargingState> updates = new HashMap<>();

        for (Map.Entry<UUID, ChargingState> entry : chargingPlayers.entrySet()) {
            UUID playerId = entry.getKey();
            ChargingState state = entry.getValue();

            Player player = level.getPlayerByUUID(Objects.requireNonNull(playerId));
            if (player == null || !positionChecker.isOnNode(player, state.nodePos())) {
                toRemove.add(playerId);
                continue;
            }

            // Increment tick
            ChargingState newState = state.tick();

            // Check completion
            if (newState.isComplete()) {
                completed.add(new ChargeCompleteEvent(Objects.requireNonNull(playerId), newState.nodeId(), newState.nodePos()));
                toRemove.add(playerId);
            } else {
                updates.put(playerId, newState);
            }
        }

        // Apply state updates after iteration
        updates.forEach(chargingPlayers::put);

        // Remove completed/invalid states
        toRemove.forEach(chargingPlayers::remove);

        return completed;
    }

    /**
     * Clears all charging states (used on server shutdown).
     */
    public void clear() {
        chargingPlayers.clear();
    }

    /**
     * Event fired when charging completes.
     */
    public record ChargeCompleteEvent(
        @Nonnull UUID playerId,
        @Nonnull UUID nodeId,
        @Nonnull BlockPos nodePos
    ) {}

    /**
     * Functional interface to check if player is on a node.
     */
    @FunctionalInterface
    public interface PlayerPositionChecker {
        boolean isOnNode(@Nonnull Player player, @Nonnull BlockPos nodePos);
    }

    /**
     * Default position checker: player is on node if within 1 block horizontally and vertically.
     */
    public static final PlayerPositionChecker DEFAULT_POSITION_CHECKER = (player, nodePos) -> {
        BlockPos playerPos = player.blockPosition();
        return playerPos.getX() == nodePos.getX()
            && playerPos.getZ() == nodePos.getZ()
            && Math.abs(playerPos.getY() - nodePos.getY()) <= 1;
    };
}
