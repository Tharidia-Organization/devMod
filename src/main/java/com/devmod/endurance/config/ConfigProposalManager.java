package com.devmod.endurance.config;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.server.level.ServerPlayer;

import com.devmod.endurance.EnduranceConfigSyncPayload;
import com.devmod.endurance.EnduranceMobConfigSyncPayload;

/**
 * Manages config change proposals from testers.
 * Proposals are queued for admin approval before being applied globally.
 */
public final class ConfigProposalManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(ConfigProposalManager.class);

    public static final ConfigProposalManager INSTANCE = new ConfigProposalManager();

    // Pending proposals keyed by proposal ID
    private final ConcurrentMap<UUID, ConfigProposal> pendingProposals = new ConcurrentHashMap<>();

    // Pending mob config proposals keyed by proposal ID
    private final ConcurrentMap<UUID, MobConfigProposal> pendingMobProposals = new ConcurrentHashMap<>();

    // Max pending proposals per player
    private static final int MAX_PROPOSALS_PER_PLAYER = 5;

    private ConfigProposalManager() {}

    /**
     * A config change proposal from a tester.
     */
    public record ConfigProposal(
        UUID proposalId,
        UUID playerId,
        String playerName,
        List<EnduranceConfigSyncPayload.ConfigEntry> entries,
        long timestamp,
        String reason
    ) {
        public ConfigProposal {
            entries = List.copyOf(entries);
        }
    }

    /**
     * A mob config change proposal from a tester.
     */
    public record MobConfigProposal(
        UUID proposalId,
        UUID playerId,
        String playerName,
        EnduranceMobConfigSyncPayload payload,
        long timestamp,
        String reason
    ) {}

    /**
     * Submit a new config proposal.
     * @param player The player submitting the proposal
     * @param entries The config entries to change
     * @param reason Optional reason for the change
     * @return The proposal ID, or null if rejected
     */
    public UUID submitProposal(ServerPlayer player, List<EnduranceConfigSyncPayload.ConfigEntry> entries, @Nullable String reason) {
        if (entries == null || entries.isEmpty()) {
            return null;
        }

        // Check proposal limit per player
        long playerProposalCount = pendingProposals.values().stream()
            .filter(p -> p.playerId().equals(player.getUUID()))
            .count();

        if (playerProposalCount >= MAX_PROPOSALS_PER_PLAYER) {
            LOGGER.warn("[ConfigProposal] Player {} has reached max pending proposals ({})",
                player.getName().getString(), MAX_PROPOSALS_PER_PLAYER);
            return null;
        }

        UUID proposalId = UUID.randomUUID();
        ConfigProposal proposal = new ConfigProposal(
            proposalId,
            player.getUUID(),
            player.getName().getString(),
            entries,
            System.currentTimeMillis(),
            reason != null ? reason : ""
        );

        pendingProposals.put(proposalId, proposal);

        LOGGER.info("[ConfigProposal] New proposal {} from {} with {} entries",
            proposalId, player.getName().getString(), entries.size());

        return proposalId;
    }

    /**
     * Get all pending proposals.
     */
    public List<ConfigProposal> getPendingProposals() {
        return new ArrayList<>(pendingProposals.values());
    }

    /**
     * Get a specific proposal by ID.
     */
    public ConfigProposal getProposal(UUID proposalId) {
        return pendingProposals.get(proposalId);
    }

    /**
     * Approve a proposal (admin only).
     * @param proposalId The proposal to approve
     * @return The proposal entries to apply, or empty list if not found
     */
    public List<EnduranceConfigSyncPayload.ConfigEntry> approveProposal(UUID proposalId) {
        ConfigProposal proposal = pendingProposals.remove(proposalId);
        if (proposal == null) {
            LOGGER.warn("[ConfigProposal] Proposal {} not found for approval", proposalId);
            return Collections.emptyList();
        }

        LOGGER.info("[ConfigProposal] Approved proposal {} from {} with {} entries",
            proposalId, proposal.playerName(), proposal.entries().size());

        return proposal.entries();
    }

    /**
     * Reject a proposal (admin only).
     * @param proposalId The proposal to reject
     * @param reason Reason for rejection
     * @return true if proposal was found and rejected
     */
    public boolean rejectProposal(UUID proposalId, String reason) {
        ConfigProposal proposal = pendingProposals.remove(proposalId);
        if (proposal == null) {
            LOGGER.warn("[ConfigProposal] Proposal {} not found for rejection", proposalId);
            return false;
        }

        LOGGER.info("[ConfigProposal] Rejected proposal {} from {}: {}",
            proposalId, proposal.playerName(), reason);

        return true;
    }

    /**
     * Get count of pending proposals.
     */
    public int getPendingCount() {
        return pendingProposals.size();
    }

    /**
     * Clear all pending proposals (server shutdown).
     */
    public void clearAll() {
        int count = pendingProposals.size();
        int mobCount = pendingMobProposals.size();
        pendingProposals.clear();
        pendingMobProposals.clear();
        if (count > 0 || mobCount > 0) {
            LOGGER.info("[ConfigProposal] Cleared {} pending proposals and {} mob proposals", count, mobCount);
        }
    }

    // ========== Mob Config Proposals ==========

    /**
     * Submit a new mob config proposal.
     * @param player The player submitting the proposal
     * @param payload The mob config payload
     * @param reason Optional reason for the change
     * @return The proposal ID, or null if rejected
     */
    public UUID submitMobConfigProposal(ServerPlayer player, EnduranceMobConfigSyncPayload payload, @Nullable String reason) {
        if (payload == null || payload.mobEntries().isEmpty()) {
            return null;
        }

        // Check proposal limit per player (combined with regular proposals)
        long playerProposalCount = pendingProposals.values().stream()
            .filter(p -> p.playerId().equals(player.getUUID()))
            .count();
        long playerMobProposalCount = pendingMobProposals.values().stream()
            .filter(p -> p.playerId().equals(player.getUUID()))
            .count();

        if (playerProposalCount + playerMobProposalCount >= MAX_PROPOSALS_PER_PLAYER) {
            LOGGER.warn("[ConfigProposal] Player {} has reached max pending proposals ({})",
                player.getName().getString(), MAX_PROPOSALS_PER_PLAYER);
            return null;
        }

        UUID proposalId = UUID.randomUUID();
        MobConfigProposal proposal = new MobConfigProposal(
            proposalId,
            player.getUUID(),
            player.getName().getString(),
            payload,
            System.currentTimeMillis(),
            reason != null ? reason : ""
        );

        pendingMobProposals.put(proposalId, proposal);

        LOGGER.info("[ConfigProposal] New mob config proposal {} from {} with {} mob entries",
            proposalId, player.getName().getString(), payload.mobEntries().size());

        return proposalId;
    }

    /**
     * Get all pending mob config proposals.
     */
    public List<MobConfigProposal> getPendingMobProposals() {
        return new ArrayList<>(pendingMobProposals.values());
    }

    /**
     * Get a specific mob config proposal by ID.
     */
    public MobConfigProposal getMobProposal(UUID proposalId) {
        return pendingMobProposals.get(proposalId);
    }

    /**
     * Approve a mob config proposal (admin only).
     * @param proposalId The proposal to approve
     * @return The payload to apply, or null if not found
     */
    public EnduranceMobConfigSyncPayload approveMobProposal(UUID proposalId) {
        MobConfigProposal proposal = pendingMobProposals.remove(proposalId);
        if (proposal == null) {
            LOGGER.warn("[ConfigProposal] Mob proposal {} not found for approval", proposalId);
            return null;
        }

        LOGGER.info("[ConfigProposal] Approved mob proposal {} from {} with {} mob entries",
            proposalId, proposal.playerName(), proposal.payload().mobEntries().size());

        return proposal.payload();
    }

    /**
     * Reject a mob config proposal (admin only).
     * @param proposalId The proposal to reject
     * @param reason Reason for rejection
     * @return true if proposal was found and rejected
     */
    public boolean rejectMobProposal(UUID proposalId, String reason) {
        MobConfigProposal proposal = pendingMobProposals.remove(proposalId);
        if (proposal == null) {
            LOGGER.warn("[ConfigProposal] Mob proposal {} not found for rejection", proposalId);
            return false;
        }

        LOGGER.info("[ConfigProposal] Rejected mob proposal {} from {}: {}",
            proposalId, proposal.playerName(), reason);

        return true;
    }

    /**
     * Get count of pending mob config proposals.
     */
    public int getPendingMobCount() {
        return pendingMobProposals.size();
    }
}
