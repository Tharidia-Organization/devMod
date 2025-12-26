package com.devmod.arena.currency;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
public record CurrencyGrant(
    UUID id,
    UUID playerId,
    CurrencySource source,
    String sourceId,
    long amount,
    Instant grantedAt,
    String description,
    UUID grantedBy
) {

    /**
     * Maximum length for sourceId (DD54).
     */
    public static final int MAX_SOURCE_ID_LENGTH = 64;

    /**
     * Compact constructor with validation.
     */
    public CurrencyGrant {
        Objects.requireNonNull(id, "id cannot be null");
        Objects.requireNonNull(playerId, "playerId cannot be null");
        Objects.requireNonNull(source, "source cannot be null");
        Objects.requireNonNull(grantedAt, "grantedAt cannot be null");

        if (amount <= 0) {
            throw new IllegalArgumentException("amount must be positive: " + amount);
        }

        if (sourceId != null && sourceId.length() > MAX_SOURCE_ID_LENGTH) {
            throw new IllegalArgumentException(
                "sourceId exceeds maximum length of " + MAX_SOURCE_ID_LENGTH +
                " characters: " + sourceId.length()
            );
        }

        // Validate sourceId format (alphanumeric, underscore, hyphen, colon)
        if (sourceId != null && !sourceId.matches("^[a-zA-Z0-9_:\\-]*$")) {
            throw new IllegalArgumentException(
                "sourceId contains invalid characters: " + sourceId
            );
        }
    }

    /**
     * Builder for creating CurrencyGrant instances.
     */
    public static final class Builder {
        private UUID id;
        private UUID playerId;
        private CurrencySource source;
        private String sourceId;
        private long amount;
        private Instant grantedAt;
        private String description;
        private UUID grantedBy;

        public Builder() {
            this.id = UUID.randomUUID();
            this.grantedAt = Instant.now();
        }

        public Builder id(UUID id) {
            this.id = id;
            return this;
        }

        public Builder playerId(UUID playerId) {
            this.playerId = playerId;
            return this;
        }

        public Builder source(CurrencySource source) {
            this.source = source;
            return this;
        }

        public Builder sourceId(String sourceId) {
            this.sourceId = sourceId;
            return this;
        }

        public Builder amount(long amount) {
            this.amount = amount;
            return this;
        }

        public Builder grantedAt(Instant grantedAt) {
            this.grantedAt = grantedAt;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder grantedBy(UUID grantedBy) {
            this.grantedBy = grantedBy;
            return this;
        }

        public CurrencyGrant build() {
            return new CurrencyGrant(
                id, playerId, source, sourceId, amount, grantedAt, description, grantedBy
            );
        }
    }

    /**
     * Creates a new builder.
     *
     * @return new Builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Creates a grant for a match win.
     *
     * @param playerId the player receiving currency
     * @param matchId the match ID as sourceId
     * @param amount the amount to grant
     * @return new CurrencyGrant
     */
    public static CurrencyGrant forMatchWin(UUID playerId, String matchId, long amount) {
        return builder()
            .playerId(playerId)
            .source(CurrencySource.MATCH_WIN)
            .sourceId(matchId)
            .amount(amount)
            .description("Match win reward")
            .build();
    }

    /**
     * Creates a grant for completing a challenge.
     *
     * @param playerId the player receiving currency
     * @param challengeId the challenge ID as sourceId
     * @param amount the amount to grant
     * @return new CurrencyGrant
     */
    public static CurrencyGrant forChallenge(UUID playerId, String challengeId, long amount) {
        return builder()
            .playerId(playerId)
            .source(CurrencySource.CHALLENGE_COMPLETE)
            .sourceId(challengeId)
            .amount(amount)
            .description("Challenge completion reward")
            .build();
    }

    /**
     * Creates an admin grant.
     *
     * @param playerId the player receiving currency
     * @param amount the amount to grant
     * @param reason reason for the grant
     * @param adminId the admin granting currency
     * @return new CurrencyGrant
     */
    public static CurrencyGrant adminGrant(UUID playerId, long amount, String reason, UUID adminId) {
        return builder()
            .playerId(playerId)
            .source(CurrencySource.ADMIN_GRANT)
            .sourceId("admin:" + adminId.toString().substring(0, 8))
            .amount(amount)
            .description(reason)
            .grantedBy(adminId)
            .build();
    }

    /**
     * Validates the sourceId meets requirements.
     *
     * @param sourceId the sourceId to validate
     * @return true if valid
     */
    public static boolean isValidSourceId(String sourceId) {
        if (sourceId == null) {
            return true; // null is allowed
        }
        if (sourceId.length() > MAX_SOURCE_ID_LENGTH) {
            return false;
        }
        return sourceId.matches("^[a-zA-Z0-9_:\\-]*$");
    }

    /**
     * Gets the full source identifier combining source and sourceId.
     *
     * @return combined source identifier
     */
    public String getFullSourceIdentifier() {
        if (sourceId == null || sourceId.isEmpty()) {
            return source.name();
        }
        return source.name() + ":" + sourceId;
    }

    /**
     * Checks if this grant requires authorization.
     *
     * @return true if authorization required
     */
    public boolean requiresAuthorization() {
        return source.requiresAuthorization();
    }

    /**
     * Checks if this grant was earned in-game.
     *
     * @return true if earned through gameplay
     */
    public boolean isEarned() {
        return source.isEarnedInGame();
    }

    /**
     * Generates SQL for inserting this grant.
     *
     * @return SQL insert statement
     */
    public String toInsertSql() {
        return """
            INSERT INTO currency_grants (id, player_id, source, source_id, amount, granted_at, description, granted_by)
            VALUES ('%s', '%s', '%s', %s, %d, '%s', %s, %s)
            """.formatted(
                id,
                playerId,
                source.name(),
                sourceId != null ? "'" + sourceId + "'" : "NULL",
                amount,
                grantedAt,
                description != null ? "'" + description.replace("'", "''") + "'" : "NULL",
                grantedBy != null ? "'" + grantedBy + "'" : "NULL"
            );
    }
}
