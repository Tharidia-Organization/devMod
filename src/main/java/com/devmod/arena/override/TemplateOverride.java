package com.devmod.arena.override;

import java.time.Instant;

import javax.annotation.Nullable;
public record TemplateOverride(
    /** Template ID to force */
    String templateId,

    /** Optional policy ID to force */
    @Nullable String policyId,

    /** Scope of the override */
    OverrideScope scope,

    /** When the override was created */
    Instant createdAt,

    /** Optional expiration time (TTL) */
    @Nullable Instant expiresAt,

    /** Source of the override (e.g., "command", "wizard", "api") */
    String source
) {
    /**
     * Creates an override with no expiration.
     */
    public static TemplateOverride create(String templateId, OverrideScope scope, String source) {
        return new TemplateOverride(
            templateId,
            null,
            scope,
            Instant.now(),
            null,
            source
        );
    }

    /**
     * Creates an override with policy and no expiration.
     */
    public static TemplateOverride create(String templateId, String policyId, OverrideScope scope, String source) {
        return new TemplateOverride(
            templateId,
            policyId,
            scope,
            Instant.now(),
            null,
            source
        );
    }

    /**
     * Creates an override with expiration.
     */
    public static TemplateOverride createWithTTL(String templateId, OverrideScope scope, String source, Instant expiresAt) {
        return new TemplateOverride(
            templateId,
            null,
            scope,
            Instant.now(),
            expiresAt,
            source
        );
    }

    /**
     * Checks if this override has expired.
     */
    public boolean isExpired() {
        return expiresAt != null && Instant.now().isAfter(expiresAt);
    }

    /**
     * Creates a builder for this override.
     */
    public static Builder builder(String templateId) {
        return new Builder(templateId);
    }

    /**
     * Builder for TemplateOverride.
     */
    public static class Builder {
        private final String templateId;
        private String policyId;
        private OverrideScope scope = OverrideScope.PLAYER;
        private Instant createdAt = Instant.now();
        private Instant expiresAt;
        private String source = "api";

        public Builder(String templateId) {
            this.templateId = templateId;
        }

        public Builder policyId(String policyId) { this.policyId = policyId; return this; }
        public Builder scope(OverrideScope scope) { this.scope = scope; return this; }
        public Builder createdAt(Instant createdAt) { this.createdAt = createdAt; return this; }
        public Builder expiresAt(Instant expiresAt) { this.expiresAt = expiresAt; return this; }
        public Builder source(String source) { this.source = source; return this; }

        public TemplateOverride build() {
            return new TemplateOverride(templateId, policyId, scope, createdAt, expiresAt, source);
        }
    }
}
