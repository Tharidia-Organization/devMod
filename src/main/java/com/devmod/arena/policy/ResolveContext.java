package com.devmod.arena.policy;

import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.server.MinecraftServer;

import com.devmod.mob.MobRequirements;

public record ResolveContext(
    /** Player ID making the request */
    UUID playerId,

    /** Optional party ID */
    @Nullable UUID partyId,

    /** Mob type being fought */
    @Nullable String mobType,

    /** Mob requirements for arena selection (space, biome, light, etc.) */
    @Nullable MobRequirements mobRequirements,

    /** Quest type (e.g., "boss", "normal", "event") */
    @Nullable String questType,

    /** Difficulty level */
    @Nullable String difficulty,

    /** Number of players */
    int playerCount,

    /** Request tags for matching */
    Set<String> tags,

    /** Force specific template ID (override) */
    @Nullable String forceTemplateId,

    /** Force specific policy ID (override) */
    @Nullable String forcePolicyId,

    /** Server for registry access (biome lookup, etc.) */
    @Nullable MinecraftServer server
) {
    /**
     * Creates a minimal context with just player ID.
     */
    public static ResolveContext forPlayer(UUID playerId) {
        return new ResolveContext(
            playerId,
            null,
            null,
            null,
            null,
            null,
            1,
            Set.of(),
            null,
            null,
            null
        );
    }

    /**
     * Creates a builder for context.
     */
    public static Builder builder(UUID playerId) {
        return new Builder(playerId);
    }

    /**
     * Builder for ResolveContext.
     */
    public static class Builder {
        private final UUID playerId;
        private UUID partyId;
        private String mobType;
        private MobRequirements mobRequirements;
        private String questType;
        private String difficulty;
        private int playerCount = 1;
        private Set<String> tags = Set.of();
        private String forceTemplateId;
        private String forcePolicyId;
        private MinecraftServer server;

        public Builder(UUID playerId) {
            this.playerId = playerId;
        }

        public Builder partyId(UUID partyId) { this.partyId = partyId; return this; }
        public Builder mobType(String mobType) { this.mobType = mobType; return this; }
        public Builder mobRequirements(MobRequirements mobRequirements) { this.mobRequirements = mobRequirements; return this; }
        public Builder questType(String questType) { this.questType = questType; return this; }
        public Builder difficulty(String difficulty) { this.difficulty = difficulty; return this; }
        public Builder playerCount(int playerCount) { this.playerCount = playerCount; return this; }
        public Builder tags(Set<String> tags) { this.tags = tags; return this; }
        public Builder forceTemplateId(String forceTemplateId) { this.forceTemplateId = forceTemplateId; return this; }
        public Builder forcePolicyId(String forcePolicyId) { this.forcePolicyId = forcePolicyId; return this; }
        public Builder server(MinecraftServer server) { this.server = server; return this; }

        public ResolveContext build() {
            return new ResolveContext(
                playerId, partyId, mobType, mobRequirements, questType, difficulty,
                playerCount, tags, forceTemplateId, forcePolicyId, server
            );
        }
    }
}
