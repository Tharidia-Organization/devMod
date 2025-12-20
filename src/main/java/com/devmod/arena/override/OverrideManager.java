package com.devmod.arena.override;

import com.devmod.arena.telemetry.ArenaTelemetry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manager for template overrides with session cleanup hooks.
 *
 * <p>Implements DD5: Override Scope - Session-based with Cleanup.
 *
 * <p>Storage: Map<UUID, TemplateOverride> in memory, NOT persisted.
 *
 * @see <a href="TODO_ARENA_TEMPLATE.md">Arena Template Design Document</a>
 */
public class OverrideManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(OverrideManager.class);

    // Player UUID -> Override
    private final ConcurrentHashMap<UUID, TemplateOverride> playerOverrides = new ConcurrentHashMap<>();

    // Party UUID -> Override (for PARTY scope)
    private final ConcurrentHashMap<UUID, TemplateOverride> partyOverrides = new ConcurrentHashMap<>();

    // Quest ID -> Override (for QUEST scope)
    private final ConcurrentHashMap<String, TemplateOverride> questOverrides = new ConcurrentHashMap<>();

    // Party membership tracking (player -> party)
    private final ConcurrentHashMap<UUID, UUID> playerToParty = new ConcurrentHashMap<>();

    private final ArenaTelemetry telemetry;

    public OverrideManager(ArenaTelemetry telemetry) {
        this.telemetry = telemetry;
    }

    // ===================
    // Override Setting
    // ===================

    /**
     * Sets an override for a player.
     *
     * @param playerId The player UUID
     * @param override The override to set
     */
    public void setOverride(UUID playerId, TemplateOverride override) {
        Objects.requireNonNull(playerId, "playerId cannot be null");
        Objects.requireNonNull(override, "override cannot be null");

        switch (override.scope()) {
            case PLAYER -> {
                playerOverrides.put(playerId, override);
                LOGGER.debug("Override set for player {}: template={}", playerId, override.templateId());
            }
            case PARTY -> {
                UUID partyId = playerToParty.get(playerId);
                if (partyId != null) {
                    partyOverrides.put(partyId, override);
                    LOGGER.debug("Override set for party {}: template={}", partyId, override.templateId());
                } else {
                    // Fall back to player scope if not in party
                    playerOverrides.put(playerId, override);
                    LOGGER.debug("No party found, override set for player {}: template={}", playerId, override.templateId());
                }
            }
            case QUEST -> {
                // Quest overrides need a quest ID, stored separately
                LOGGER.warn("QUEST scope override should use setQuestOverride()");
                playerOverrides.put(playerId, override);
            }
        }

        telemetry.emitOverrideSet(
            playerId,
            override.templateId(),
            override.policyId(),
            override.scope().name(),
            override.source()
        );
    }

    /**
     * Sets an override for a specific quest.
     *
     * @param questId The quest identifier
     * @param override The override to set
     */
    public void setQuestOverride(String questId, TemplateOverride override) {
        Objects.requireNonNull(questId, "questId cannot be null");
        Objects.requireNonNull(override, "override cannot be null");

        questOverrides.put(questId, override);
        LOGGER.debug("Override set for quest {}: template={}", questId, override.templateId());

        telemetry.emit("arena.override.quest_set", Map.of(
            "questId", questId,
            "templateId", override.templateId(),
            "policyId", override.policyId() != null ? override.policyId() : "",
            "source", override.source()
        ));
    }

    // ===================
    // Override Retrieval
    // ===================

    /**
     * Gets the effective override for a player.
     * Checks player override first, then party override.
     *
     * @param playerId The player UUID
     * @return The override if set and not expired, empty otherwise
     */
    public Optional<TemplateOverride> getOverride(UUID playerId) {
        // Check player override first
        TemplateOverride playerOverride = playerOverrides.get(playerId);
        if (playerOverride != null) {
            if (playerOverride.isExpired()) {
                clearOverride(playerId, "expired");
                return Optional.empty();
            }
            return Optional.of(playerOverride);
        }

        // Check party override
        UUID partyId = playerToParty.get(playerId);
        if (partyId != null) {
            TemplateOverride partyOverride = partyOverrides.get(partyId);
            if (partyOverride != null) {
                if (partyOverride.isExpired()) {
                    clearPartyOverride(partyId, "expired");
                    return Optional.empty();
                }
                return Optional.of(partyOverride);
            }
        }

        return Optional.empty();
    }

    /**
     * Gets the override for a specific quest.
     *
     * @param questId The quest identifier
     * @return The override if set and not expired, empty otherwise
     */
    public Optional<TemplateOverride> getQuestOverride(String questId) {
        TemplateOverride override = questOverrides.get(questId);
        if (override != null) {
            if (override.isExpired()) {
                clearQuestOverride(questId, "expired");
                return Optional.empty();
            }
            return Optional.of(override);
        }
        return Optional.empty();
    }

    /**
     * Checks if a player has an active override.
     */
    public boolean hasOverride(UUID playerId) {
        return getOverride(playerId).isPresent();
    }

    // ===================
    // Session Cleanup Hooks (DD5)
    // ===================

    /**
     * Clears override on quest end.
     * Called by EnduranceQuestManager or equivalent.
     *
     * @param playerId The player UUID
     * @param outcome The quest outcome (e.g., "completed", "failed", "abandoned")
     */
    public void onQuestEnd(UUID playerId, String outcome) {
        clearOverride(playerId, "quest_end");
        telemetry.emitOverrideCleared(playerId, "quest_end", outcome);
    }

    /**
     * Clears override on quest end with quest ID.
     *
     * @param playerId The player UUID
     * @param questId The quest identifier
     * @param outcome The quest outcome
     */
    public void onQuestEnd(UUID playerId, String questId, String outcome) {
        clearOverride(playerId, "quest_end");
        clearQuestOverride(questId, "quest_end");
        telemetry.emitOverrideCleared(playerId, "quest_end", outcome);
    }

    /**
     * Clears override on player logout.
     *
     * @param playerId The player UUID
     */
    public void onPlayerLogout(UUID playerId) {
        // Clear player override
        clearOverride(playerId, "logout");

        // If was party leader, clear party override
        UUID partyId = playerToParty.get(playerId);
        if (partyId != null) {
            // Check if this player was party leader (simplified - real impl needs party system integration)
            // For now, we just note the logout
            LOGGER.debug("Player {} logged out, was in party {}", playerId, partyId);
        }

        playerToParty.remove(playerId);
        telemetry.emitOverrideCleared(playerId, "logout", null);
    }

    /**
     * Clears override on party disband.
     *
     * @param partyId The party UUID
     */
    public void onPartyDisband(UUID partyId) {
        clearPartyOverride(partyId, "party_disband");

        // Clear player-to-party mappings
        playerToParty.entrySet().removeIf(e -> e.getValue().equals(partyId));

        telemetry.emit("arena.override.party_cleared", Map.of(
            "partyId", partyId.toString(),
            "reason", "party_disband"
        ));
    }

    /**
     * Clears all overrides (e.g., on server shutdown).
     */
    public void clearAll() {
        int playerCount = playerOverrides.size();
        int partyCount = partyOverrides.size();
        int questCount = questOverrides.size();

        playerOverrides.clear();
        partyOverrides.clear();
        questOverrides.clear();
        playerToParty.clear();

        LOGGER.info("Cleared all overrides: {} player, {} party, {} quest", playerCount, partyCount, questCount);

        telemetry.emit("arena.override.all_cleared", Map.of(
            "playerCount", playerCount,
            "partyCount", partyCount,
            "questCount", questCount
        ));
    }

    // ===================
    // Internal Clear Methods
    // ===================

    /**
     * Clears a player's override.
     */
    public void clearOverride(UUID playerId) {
        clearOverride(playerId, "manual");
    }

    private void clearOverride(UUID playerId, String reason) {
        TemplateOverride removed = playerOverrides.remove(playerId);
        if (removed != null) {
            LOGGER.debug("Override cleared for player {}: reason={}", playerId, reason);
        }
    }

    private void clearPartyOverride(UUID partyId, String reason) {
        TemplateOverride removed = partyOverrides.remove(partyId);
        if (removed != null) {
            LOGGER.debug("Override cleared for party {}: reason={}", partyId, reason);
        }
    }

    private void clearQuestOverride(String questId, String reason) {
        TemplateOverride removed = questOverrides.remove(questId);
        if (removed != null) {
            LOGGER.debug("Override cleared for quest {}: reason={}", questId, reason);
        }
    }

    // ===================
    // Party Management
    // ===================

    /**
     * Registers a player's party membership.
     */
    public void setPlayerParty(UUID playerId, UUID partyId) {
        playerToParty.put(playerId, partyId);
    }

    /**
     * Gets a player's party.
     */
    public Optional<UUID> getPlayerParty(UUID playerId) {
        return Optional.ofNullable(playerToParty.get(playerId));
    }

    /**
     * Removes a player from their party.
     */
    public void removePlayerFromParty(UUID playerId) {
        playerToParty.remove(playerId);
    }

    // ===================
    // Stats
    // ===================

    /**
     * Gets override statistics.
     */
    public Map<String, Integer> getStats() {
        return Map.of(
            "playerOverrides", playerOverrides.size(),
            "partyOverrides", partyOverrides.size(),
            "questOverrides", questOverrides.size(),
            "partyMemberships", playerToParty.size()
        );
    }

    /**
     * Gets all active player overrides (for debugging/admin).
     */
    public Map<UUID, TemplateOverride> getAllPlayerOverrides() {
        return Collections.unmodifiableMap(new HashMap<>(playerOverrides));
    }
}
