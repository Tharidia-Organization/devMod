package com.devmod.arena.api;

import javax.annotation.Nullable;
import java.util.Set;
import java.util.UUID;

/**
 * Options for arena resolution (DD14).
 *
 * <p>Used by prepareArenaForPartyV2() to configure arena selection and build.
 */
public record ResolveOptions(
    // Party context
    UUID partyId,
    UUID primaryPlayerId,
    int playerCount,

    // Quest context
    @Nullable String questType,
    @Nullable String questId,
    @Nullable String difficulty,

    // Mob context
    @Nullable String mobType,
    @Nullable Set<String> requiredTags,
    @Nullable Set<String> excludedTags,

    // Override options
    @Nullable String forcePolicyId,
    @Nullable String forceTemplateId,

    // Build options
    boolean async,
    int timeoutMs,

    // Idempotency
    @Nullable String requestId
) {
    /**
     * Default timeout in milliseconds.
     */
    public static final int DEFAULT_TIMEOUT_MS = 30_000;

    /**
     * Creates minimal options for a party.
     */
    public static ResolveOptions forParty(UUID partyId, UUID primaryPlayerId, int playerCount) {
        return new ResolveOptions(
            partyId,
            primaryPlayerId,
            playerCount,
            null, null, null,
            null, null, null,
            null, null,
            false,
            DEFAULT_TIMEOUT_MS,
            null
        );
    }

    /**
     * Creates options with quest context.
     */
    public ResolveOptions withQuest(String questType, String questId, String difficulty) {
        return new ResolveOptions(
            partyId, primaryPlayerId, playerCount,
            questType, questId, difficulty,
            mobType, requiredTags, excludedTags,
            forcePolicyId, forceTemplateId,
            async, timeoutMs,
            requestId
        );
    }

    /**
     * Creates options with mob type.
     */
    public ResolveOptions withMobType(String mobType) {
        return new ResolveOptions(
            partyId, primaryPlayerId, playerCount,
            questType, questId, difficulty,
            mobType, requiredTags, excludedTags,
            forcePolicyId, forceTemplateId,
            async, timeoutMs,
            requestId
        );
    }

    /**
     * Creates options with required tags.
     */
    public ResolveOptions withRequiredTags(Set<String> tags) {
        return new ResolveOptions(
            partyId, primaryPlayerId, playerCount,
            questType, questId, difficulty,
            mobType, tags, excludedTags,
            forcePolicyId, forceTemplateId,
            async, timeoutMs,
            requestId
        );
    }

    /**
     * Creates options with excluded tags.
     */
    public ResolveOptions withExcludedTags(Set<String> tags) {
        return new ResolveOptions(
            partyId, primaryPlayerId, playerCount,
            questType, questId, difficulty,
            mobType, requiredTags, tags,
            forcePolicyId, forceTemplateId,
            async, timeoutMs,
            requestId
        );
    }

    /**
     * Creates options forcing a specific template.
     */
    public ResolveOptions withForceTemplate(String templateId) {
        return new ResolveOptions(
            partyId, primaryPlayerId, playerCount,
            questType, questId, difficulty,
            mobType, requiredTags, excludedTags,
            null, templateId,
            async, timeoutMs,
            requestId
        );
    }

    /**
     * Creates options forcing a specific policy.
     */
    public ResolveOptions withForcePolicy(String policyId) {
        return new ResolveOptions(
            partyId, primaryPlayerId, playerCount,
            questType, questId, difficulty,
            mobType, requiredTags, excludedTags,
            policyId, null,
            async, timeoutMs,
            requestId
        );
    }

    /**
     * Creates options for async build.
     */
    public ResolveOptions withAsync(boolean async) {
        return new ResolveOptions(
            partyId, primaryPlayerId, playerCount,
            questType, questId, difficulty,
            mobType, requiredTags, excludedTags,
            forcePolicyId, forceTemplateId,
            async, timeoutMs,
            requestId
        );
    }

    /**
     * Creates options with custom timeout.
     */
    public ResolveOptions withTimeout(int timeoutMs) {
        return new ResolveOptions(
            partyId, primaryPlayerId, playerCount,
            questType, questId, difficulty,
            mobType, requiredTags, excludedTags,
            forcePolicyId, forceTemplateId,
            async, timeoutMs,
            requestId
        );
    }

    /**
     * Creates options with request ID for idempotency.
     */
    public ResolveOptions withRequestId(String requestId) {
        return new ResolveOptions(
            partyId, primaryPlayerId, playerCount,
            questType, questId, difficulty,
            mobType, requiredTags, excludedTags,
            forcePolicyId, forceTemplateId,
            async, timeoutMs,
            requestId
        );
    }

    /**
     * Builder for ResolveOptions.
     */
    public static class Builder {
        private UUID partyId;
        private UUID primaryPlayerId;
        private int playerCount;
        private String questType;
        private String questId;
        private String difficulty;
        private String mobType;
        private Set<String> requiredTags;
        private Set<String> excludedTags;
        private String forcePolicyId;
        private String forceTemplateId;
        private boolean async = false;
        private int timeoutMs = DEFAULT_TIMEOUT_MS;
        private String requestId;

        public Builder partyId(UUID partyId) {
            this.partyId = partyId;
            return this;
        }

        public Builder primaryPlayerId(UUID primaryPlayerId) {
            this.primaryPlayerId = primaryPlayerId;
            return this;
        }

        public Builder playerCount(int playerCount) {
            this.playerCount = playerCount;
            return this;
        }

        public Builder questType(String questType) {
            this.questType = questType;
            return this;
        }

        public Builder questId(String questId) {
            this.questId = questId;
            return this;
        }

        public Builder difficulty(String difficulty) {
            this.difficulty = difficulty;
            return this;
        }

        public Builder mobType(String mobType) {
            this.mobType = mobType;
            return this;
        }

        public Builder requiredTags(Set<String> requiredTags) {
            this.requiredTags = requiredTags;
            return this;
        }

        public Builder excludedTags(Set<String> excludedTags) {
            this.excludedTags = excludedTags;
            return this;
        }

        public Builder forcePolicyId(String forcePolicyId) {
            this.forcePolicyId = forcePolicyId;
            return this;
        }

        public Builder forceTemplateId(String forceTemplateId) {
            this.forceTemplateId = forceTemplateId;
            return this;
        }

        public Builder async(boolean async) {
            this.async = async;
            return this;
        }

        public Builder timeoutMs(int timeoutMs) {
            this.timeoutMs = timeoutMs;
            return this;
        }

        public Builder requestId(String requestId) {
            this.requestId = requestId;
            return this;
        }

        public ResolveOptions build() {
            return new ResolveOptions(
                partyId,
                primaryPlayerId,
                playerCount,
                questType,
                questId,
                difficulty,
                mobType,
                requiredTags,
                excludedTags,
                forcePolicyId,
                forceTemplateId,
                async,
                timeoutMs,
                requestId
            );
        }
    }

    public static Builder builder() {
        return new Builder();
    }
}
