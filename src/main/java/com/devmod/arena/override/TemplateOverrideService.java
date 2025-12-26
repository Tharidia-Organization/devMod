package com.devmod.arena.override;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.server.level.ServerPlayer;

import com.devmod.arena.telemetry.ArenaTelemetry;

public class TemplateOverrideService {

    private static final Logger LOGGER = LoggerFactory.getLogger(TemplateOverrideService.class);

    private static final TemplateOverrideService INSTANCE = new TemplateOverrideService();

    /** Default TTL for overrides: 1 hour */
    public static final Duration DEFAULT_TTL = Duration.ofHours(1);

    /** Maximum session duration: 4 hours */
    public static final Duration MAX_TTL = Duration.ofHours(4);

    /** Permission required to force templates */
    public static final String PERMISSION_FORCE_TEMPLATE = "devmod.arena.force_template";

    // Player UUID -> Override
    private final ConcurrentHashMap<UUID, TemplateOverride> playerOverrides = new ConcurrentHashMap<>();

    // Party UUID -> Override (for PARTY scope)
    private final ConcurrentHashMap<UUID, TemplateOverride> partyOverrides = new ConcurrentHashMap<>();

    // Quest ID -> Override (for QUEST scope)
    private final ConcurrentHashMap<String, TemplateOverride> questOverrides = new ConcurrentHashMap<>();

    // Party membership tracking (player -> party)
    private final ConcurrentHashMap<UUID, UUID> playerToParty = new ConcurrentHashMap<>();

    @Nullable
    private ArenaTelemetry telemetry;

    @Nullable
    private PermissionChecker permissionChecker;

    private TemplateOverrideService() {}

    public static TemplateOverrideService getInstance() {
        return INSTANCE;
    }

    /**
     * Initialize with telemetry.
     */
    public TemplateOverrideService withTelemetry(ArenaTelemetry telemetry) {
        this.telemetry = telemetry;
        return this;
    }

    /**
     * Initialize with permission checker.
     */
    public TemplateOverrideService withPermissionChecker(PermissionChecker checker) {
        this.permissionChecker = checker;
        return this;
    }

    // ===================
    // Override Setting (DD5)
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
                    playerOverrides.put(playerId, override);
                    LOGGER.debug("No party found, override set for player {}: template={}", playerId, override.templateId());
                }
            }
            case QUEST -> {
                LOGGER.warn("QUEST scope override should use setQuestOverride()");
                playerOverrides.put(playerId, override);
            }
        }

        if (telemetry != null) {
            telemetry.emitOverrideSet(
                playerId,
                override.templateId(),
                override.policyId(),
                override.scope().name(),
                override.source()
            );
        }
    }

    /**
     * Sets an override for a player with TTL (DD29).
     *
     * @param playerId   The player UUID
     * @param templateId The template to force
     * @param ttl        Time-to-live duration
     * @param source     Source of override (e.g., "command", "wizard")
     * @return The created override
     */
    public TemplateOverride setOverride(UUID playerId, String templateId, Duration ttl, String source) {
        Duration effectiveTtl = ttl != null && !ttl.isNegative() ? ttl : DEFAULT_TTL;
        if (effectiveTtl.compareTo(MAX_TTL) > 0) {
            effectiveTtl = MAX_TTL;
        }

        TemplateOverride override = TemplateOverride.builder(templateId)
            .scope(OverrideScope.PLAYER)
            .expiresAt(Instant.now().plus(effectiveTtl))
            .source(source)
            .build();

        setOverride(playerId, override);
        return override;
    }

    /**
     * Sets a persistent override that survives player relog (DD29).
     *
     * @param player     The player
     * @param templateId The template to force
     * @param ttl        Time-to-live duration
     * @param source     Source of override
     * @return The created override
     */
    public TemplateOverride setPersistentOverride(ServerPlayer player, String templateId, Duration ttl, String source) {
        TemplateOverride override = setOverride(player.getUUID(), templateId, ttl, source);

        // Store in data attachment for relog persistence
        TemplateOverrideAttachment.TemplateOverrideData data =
            player.getData(Objects.requireNonNull(TemplateOverrideAttachment.TEMPLATE_OVERRIDE.get()));
        data.setOverride(override);

        LOGGER.debug("Persistent override set for player {}: template={}", player.getUUID(), templateId);
        return override;
    }

    /**
     * Sets an override for a specific quest (DD5).
     *
     * @param questId  The quest identifier
     * @param override The override to set
     */
    public void setQuestOverride(String questId, TemplateOverride override) {
        Objects.requireNonNull(questId, "questId cannot be null");
        Objects.requireNonNull(override, "override cannot be null");

        questOverrides.put(questId, override);
        LOGGER.debug("Override set for quest {}: template={}", questId, override.templateId());

        if (telemetry != null) {
            telemetry.emit("arena.override.quest_set", Map.of(
                "questId", questId,
                "templateId", override.templateId(),
                "policyId", override.policyId() != null ? override.policyId() : "",
                "source", override.source()
            ));
        }
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
     * Gets the forced template ID for a player.
     *
     * @param playerId The player UUID
     * @return The forced template ID, or empty
     */
    public Optional<String> getForcedTemplateId(UUID playerId) {
        return getOverride(playerId).map(TemplateOverride::templateId);
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
    // Force Template Capability (DD29)
    // ===================

    /**
     * Creates a force template session with permission check.
     *
     * @param playerId   The player ID
     * @param templateId The template to force
     * @param ttl        Session duration
     * @param reason     Reason for forcing
     * @return The created override, or empty if not permitted
     */
    public Optional<TemplateOverride> createForceSession(
            UUID playerId,
            String templateId,
            Duration ttl,
            String reason) {

        // Check permission if checker is configured
        if (permissionChecker != null && !permissionChecker.hasPermission(playerId, PERMISSION_FORCE_TEMPLATE)) {
            LOGGER.warn("Player {} denied force template capability - no permission", playerId);
            if (telemetry != null) {
                telemetry.emit("arena.override.force_denied", Map.of(
                    "playerId", playerId.toString(),
                    "templateId", templateId,
                    "reason", "Missing permission"
                ));
            }
            return Optional.empty();
        }

        TemplateOverride override = setOverride(playerId, templateId, ttl, "force:" + reason);
        return Optional.of(override);
    }

    // ===================
    // Session Cleanup Hooks (DD5)
    // ===================

    /**
     * Clears override on quest end.
     *
     * @param playerId The player UUID
     * @param outcome  The quest outcome (e.g., "completed", "failed", "abandoned")
     */
    public void onQuestEnd(UUID playerId, String outcome) {
        clearOverride(playerId, "quest_end");
        if (telemetry != null) {
            telemetry.emitOverrideCleared(playerId, "quest_end", outcome);
        }
    }

    /**
     * Clears override on quest end with quest ID.
     *
     * @param playerId The player UUID
     * @param questId  The quest identifier
     * @param outcome  The quest outcome
     */
    public void onQuestEnd(UUID playerId, String questId, String outcome) {
        clearOverride(playerId, "quest_end");
        clearQuestOverride(questId, "quest_end");
        if (telemetry != null) {
            telemetry.emitOverrideCleared(playerId, "quest_end", outcome);
        }
    }

    /**
     * Clears override on player logout.
     *
     * @param playerId The player UUID
     */
    public void onPlayerLogout(UUID playerId) {
        clearOverride(playerId, "logout");

        UUID partyId = playerToParty.get(playerId);
        if (partyId != null) {
            LOGGER.debug("Player {} logged out, was in party {}", playerId, partyId);
        }

        playerToParty.remove(playerId);
        if (telemetry != null) {
            telemetry.emitOverrideCleared(playerId, "logout", null);
        }
    }

    /**
     * Clears override on party disband.
     *
     * @param partyId The party UUID
     */
    public void onPartyDisband(UUID partyId) {
        clearPartyOverride(partyId, "party_disband");

        playerToParty.entrySet().removeIf(e -> e.getValue().equals(partyId));

        if (telemetry != null) {
            telemetry.emit("arena.override.party_cleared", Map.of(
                "partyId", partyId.toString(),
                "reason", "party_disband"
            ));
        }
    }

    /**
     * Restores override from data attachment on player login (DD29).
     *
     * @param player The player logging in
     */
    public void restoreFromAttachment(ServerPlayer player) {
        TemplateOverrideAttachment.TemplateOverrideData data =
            player.getData(Objects.requireNonNull(TemplateOverrideAttachment.TEMPLATE_OVERRIDE.get()));

        data.getOverride().ifPresent(override -> {
            if (!override.isExpired()) {
                playerOverrides.put(player.getUUID(), override);
                LOGGER.debug("Restored override from attachment for player {}", player.getUUID());
            } else {
                data.clearOverride();
            }
        });
    }

    /**
     * Clears a persistent override (both session and data attachment).
     *
     * @param player The player
     */
    public void clearPersistentOverride(ServerPlayer player) {
        clearOverride(player.getUUID());

        TemplateOverrideAttachment.TemplateOverrideData data =
            player.getData(Objects.requireNonNull(TemplateOverrideAttachment.TEMPLATE_OVERRIDE.get()));
        data.clearOverride();
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

        if (telemetry != null) {
            telemetry.emit("arena.override.all_cleared", Map.of(
                "playerCount", playerCount,
                "partyCount", partyCount,
                "questCount", questCount
            ));
        }
    }

    /**
     * Cleans up expired overrides (call periodically).
     *
     * @return Number of expired overrides removed
     */
    public int cleanupExpired() {
        int removed = 0;

        for (UUID playerId : new ArrayList<>(playerOverrides.keySet())) {
            TemplateOverride override = playerOverrides.get(playerId);
            if (override != null && override.isExpired()) {
                playerOverrides.remove(playerId);
                removed++;
            }
        }

        for (UUID partyId : new ArrayList<>(partyOverrides.keySet())) {
            TemplateOverride override = partyOverrides.get(partyId);
            if (override != null && override.isExpired()) {
                partyOverrides.remove(partyId);
                removed++;
            }
        }

        for (String questId : new ArrayList<>(questOverrides.keySet())) {
            TemplateOverride override = questOverrides.get(questId);
            if (override != null && override.isExpired()) {
                questOverrides.remove(questId);
                removed++;
            }
        }

        if (removed > 0) {
            LOGGER.debug("Cleaned up {} expired overrides", removed);
        }

        return removed;
    }

    // ===================
    // Clear Methods
    // ===================

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

    public void setPlayerParty(UUID playerId, UUID partyId) {
        playerToParty.put(playerId, partyId);
    }

    public Optional<UUID> getPlayerParty(UUID playerId) {
        return Optional.ofNullable(playerToParty.get(playerId));
    }

    public void removePlayerFromParty(UUID playerId) {
        playerToParty.remove(playerId);
    }

    // ===================
    // Stats
    // ===================

    public OverrideStats getStats() {
        return new OverrideStats(
            playerOverrides.size(),
            partyOverrides.size(),
            questOverrides.size(),
            playerToParty.size()
        );
    }

    public Map<UUID, TemplateOverride> getAllPlayerOverrides() {
        return Collections.unmodifiableMap(new HashMap<>(playerOverrides));
    }

    // ===================
    // Supporting Types
    // ===================

    public record OverrideStats(
        int playerOverrides,
        int partyOverrides,
        int questOverrides,
        int partyMemberships
    ) {
        public Map<String, Integer> toMap() {
            return Map.of(
                "playerOverrides", playerOverrides,
                "partyOverrides", partyOverrides,
                "questOverrides", questOverrides,
                "partyMemberships", partyMemberships
            );
        }
    }

    /**
     * Interface for permission checking.
     */
    public interface PermissionChecker {
        boolean hasPermission(UUID playerId, String permission);
        default Set<String> getPlayerRoles(UUID playerId) {
            return Set.of();
        }
    }
}
