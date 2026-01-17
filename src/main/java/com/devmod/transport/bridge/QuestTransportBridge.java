package com.devmod.transport.bridge;

import java.util.List;
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

import com.devmod.endurance.EnduranceQuestManager;
import com.devmod.party.PartyData;
import com.devmod.party.PartyManager;
import com.devmod.transport.TransportColor;
import com.devmod.transport.TransportData;
import com.devmod.transport.TransportRegistry;
import com.devmod.transport.executor.ArrivalManager;
import com.devmod.transport.executor.CountdownManager;
import com.devmod.transport.executor.RecoveryManager;

/**
 * Bridge between the Transport system and the Quest/Party system.
 *
 * <p>Provides:
 * <ul>
 *   <li>Party group teleportation with countdown</li>
 *   <li>Quest arena entry/exit</li>
 *   <li>Arrival tracking for coordinated starts</li>
 * </ul>
 *
 * <p><b>NOTE:</b> Party and EnduranceQuest integration is stubbed until
 * PartyManager and EnduranceQuestManager are fully implemented.
 */
public final class QuestTransportBridge {
    private static final Logger LOGGER = LoggerFactory.getLogger(QuestTransportBridge.class);

    public static final QuestTransportBridge INSTANCE = new QuestTransportBridge();

    /** Default party teleport countdown (5 seconds). */
    private static final int PARTY_COUNTDOWN_TICKS = 100;

    private QuestTransportBridge() {}

    /**
     * Checks if a player is in an active quest session.
     */
    public boolean isPlayerInQuest(ServerPlayer player) {
        return EnduranceQuestManager.INSTANCE.isPlayerInQuest(player.getUUID());
    }

    /**
     * Gets the party ID for a player.
     */
    public Optional<UUID> getPlayerPartyId(UUID playerId) {
        return PartyManager.INSTANCE.getPartyByPlayer(playerId)
            .map(PartyData::getPartyId);
    }

    /**
     * Teleports an entire party to a destination with countdown and arrival tracking.
     *
     * @param partyId The party UUID
     * @param destination The destination transport node
     * @param server The Minecraft server
     * @param onAllArrived Callback when all party members arrive (or timeout)
     * @return true if teleport sequence started
     */
    public boolean teleportParty(
            UUID partyId,
            TransportData destination,
            MinecraftServer server,
            @Nullable Runnable onAllArrived) {

        PartyData party = PartyManager.INSTANCE.getParty(partyId);
        if (party == null) {
            LOGGER.warn("[QuestBridge] Party {} not found", partyId);
            return false;
        }

        List<ServerPlayer> members = Objects.requireNonNull(party.getMembers().stream()
            .map(uuid -> server.getPlayerList().getPlayer(Objects.requireNonNull(uuid)))
            .filter(Objects::nonNull)
            .toList());

        if (members.isEmpty()) {
            LOGGER.warn("[QuestBridge] No online members in party {}", partyId);
            return false;
        }

        return teleportGroup(members, destination, server, onAllArrived);
    }

    /**
     * Teleports a list of players to a destination with countdown.
     *
     * @param players The players to teleport
     * @param destination The destination transport node
     * @param server The Minecraft server
     * @param onAllArrived Callback when all players arrive (or timeout)
     * @return true if teleport sequence started
     */
    public boolean teleportGroup(
            List<ServerPlayer> players,
            TransportData destination,
            MinecraftServer server,
            @Nullable Runnable onAllArrived) {

        if (players.isEmpty()) {
            LOGGER.warn("[QuestBridge] No players to teleport");
            return false;
        }

        // Get destination position
        Optional<BlockPos> destPosOpt = destination.getPosition();
        if (destPosOpt.isEmpty()) {
            LOGGER.warn("[QuestBridge] Destination has no position");
            return false;
        }

        BlockPos destPos = destPosOpt.get();
        ResourceLocation destDim = destination.dimension();
        if (destDim == null) {
            LOGGER.warn("[QuestBridge] Destination has no dimension");
            return false;
        }

        ServerLevel destLevel = server.getLevel(
            Objects.requireNonNull(ResourceKey.create(
                Objects.requireNonNull(net.minecraft.core.registries.Registries.DIMENSION), destDim))
        );

        if (destLevel == null) {
            LOGGER.error("[QuestBridge] Destination level not found: {}", destDim);
            return false;
        }

        // Save recovery points for all members
        for (ServerPlayer member : Objects.requireNonNull(players)) {
            RecoveryManager.INSTANCE.saveRecoveryPoint(Objects.requireNonNull(member));
        }

        // Start countdown for all members
        UUID countdownId = UUID.randomUUID();
        ServerLevel finalDestLevel = destLevel;

        // Start countdown with BossBar
        CountdownManager.INSTANCE.startCountdown(
            Objects.requireNonNull(countdownId),
            Objects.requireNonNull(server.overworld()),
            Objects.requireNonNull(players.get(0).blockPosition()),
            PARTY_COUNTDOWN_TICKS,
            "devmod.transport.party_countdown",
            destination.color(),
            () -> {
                // Countdown complete - teleport all members
                executeGroupTeleport(players, finalDestLevel, Objects.requireNonNull(destPos));

                // Start arrival tracking
                List<UUID> memberIds = Objects.requireNonNull(players.stream()
                    .map(ServerPlayer::getUUID)
                    .toList());

                ArrivalManager.INSTANCE.startArrivalTracking(
                    Objects.requireNonNull(countdownId),
                    Objects.requireNonNull(memberIds),
                    finalDestLevel,
                    Objects.requireNonNull(destPos),
                    5.0,
                    result -> {
                        if (result.success() && onAllArrived != null) {
                            onAllArrived.run();
                        }
                        LOGGER.info("[QuestBridge] Group arrival complete: {}/{}",
                            result.arrivedCount(), result.expectedCount());
                    }
                );
            }
        );

        LOGGER.info("[QuestBridge] Started group teleport for {} players", players.size());
        return true;
    }

    /**
     * Executes teleportation for all players in a group.
     */
    private void executeGroupTeleport(
            List<ServerPlayer> members,
            ServerLevel destLevel,
            BlockPos destPos) {

        destLevel.getChunk(destPos);

        int index = 0;
        for (ServerPlayer member : members) {
            double angle = (2 * Math.PI * index) / members.size();
            double offsetX = members.size() > 1 ? Math.cos(angle) * 1.5 : 0;
            double offsetZ = members.size() > 1 ? Math.sin(angle) * 1.5 : 0;

            member.teleportTo(
                destLevel,
                destPos.getX() + 0.5 + offsetX,
                destPos.getY() + 0.5,
                destPos.getZ() + 0.5 + offsetZ,
                member.getYRot(),
                member.getXRot()
            );

            index++;
        }

        LOGGER.debug("[QuestBridge] Teleported {} group members to {}", members.size(), destPos);
    }

    /**
     * Confirms a player's arrival at the quest destination.
     */
    public boolean confirmArrival(ServerPlayer player, MinecraftServer server) {
        Optional<UUID> partyOpt = getPlayerPartyId(Objects.requireNonNull(player.getUUID()));
        if (partyOpt.isEmpty()) {
            return false;
        }

        return ArrivalManager.INSTANCE.confirmArrival(
            Objects.requireNonNull(partyOpt.get()), Objects.requireNonNull(player.getUUID()), server);
    }

    /**
     * Cancels a group teleport sequence.
     */
    public boolean cancelGroupTeleport(UUID groupId) {
        CountdownManager.INSTANCE.cancelCountdown(groupId);
        ArrivalManager.INSTANCE.cancelSession(groupId, "Teleport cancelled");
        LOGGER.info("[QuestBridge] Cancelled group teleport for {}", groupId);
        return true;
    }

    /**
     * Teleports a player out of a quest back to their recovery point.
     */
    public boolean exitQuest(ServerPlayer player) {
        if (RecoveryManager.INSTANCE.hasRecoveryPoint(Objects.requireNonNull(player.getUUID()))) {
            return RecoveryManager.INSTANCE.restorePlayer(player);
        }

        if (NexusTransportBridge.INSTANCE.isAvailable()) {
            return NexusTransportBridge.INSTANCE.teleportToHub(player);
        }

        MinecraftServer server = player.getServer();
        if (server != null) {
            ServerLevel overworld = server.overworld();
            BlockPos spawn = overworld.getSharedSpawnPos();
            player.teleportTo(overworld, spawn.getX() + 0.5, spawn.getY() + 0.5, spawn.getZ() + 0.5,
                player.getYRot(), player.getXRot());
            return true;
        }

        return false;
    }

    /**
     * Creates a transport node for quest entry.
     */
    public Optional<TransportData> createQuestEntryNode(
            TransportRegistry registry,
            String questName,
            ServerLevel destLevel,
            BlockPos destPos) {

        ResourceLocation destDim = destLevel.dimension().location();

        TransportData nodeData = TransportData.createZone(
            TransportColor.RED,
            Objects.requireNonNull(destDim), destPos,
            Objects.requireNonNull(destDim), destPos,
            questName
        );

        registry.register(Objects.requireNonNull(nodeData));
        LOGGER.info("[QuestBridge] Created quest entry node: {}", questName);

        return Objects.requireNonNull(Optional.of(nodeData));
    }

    /**
     * Handles player disconnect during quest.
     */
    public void onPlayerDisconnect(UUID playerId) {
        ArrivalManager.INSTANCE.onPlayerDisconnect(playerId);
        RecoveryManager.INSTANCE.clearRecoveryPoint(playerId);
        LOGGER.debug("[QuestBridge] Cleaned up quest state for disconnected player {}", playerId);
    }
}
