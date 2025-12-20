package com.devmod.arena.gamification;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Badge Usage tracking implementing DD52: Badge Template Tracking.
 *
 * <p>Key features:
 * <ul>
 *   <li>Usage table as source of truth</li>
 *   <li>Version-agnostic tracking</li>
 *   <li>Migration support from badge_awards to badge_usage</li>
 * </ul>
 */
public final class BadgeUsage {

    /**
     * Represents a badge usage entry in the tracking system.
     * Version-agnostic as per DD52.
     */
    public record BadgeUsageEntry(
        UUID id,
        UUID playerId,
        String badgeTemplateId,
        Instant awardedAt,
        String awardReason,
        String metadata
    ) {}

    /**
     * Aggregated count result for badge usage queries.
     * Version-agnostic counting as per DD52.
     */
    public record BadgeUsageCount(
        String badgeTemplateId,
        long totalCount,
        long uniquePlayers
    ) {}

    /**
     * Migration result from badge_awards to badge_usage.
     */
    public record MigrationResult(
        int recordsMigrated,
        int recordsSkipped,
        int recordsFailed,
        List<String> errors
    ) {}

    private BadgeUsage() {
        // Utility class
    }

    /**
     * Creates a new badge usage entry.
     *
     * @param playerId the player who earned the badge
     * @param badgeTemplateId the template ID (version-agnostic)
     * @param awardReason why the badge was awarded
     * @param metadata additional JSON metadata
     * @return new BadgeUsageEntry
     */
    public static BadgeUsageEntry create(
            UUID playerId,
            String badgeTemplateId,
            String awardReason,
            String metadata) {
        return new BadgeUsageEntry(
            UUID.randomUUID(),
            playerId,
            badgeTemplateId,
            Instant.now(),
            awardReason,
            metadata
        );
    }

    /**
     * Generates SQL for creating the badge_usage table.
     * Implements DD52: usage table source of truth.
     *
     * @return DDL SQL for badge_usage table
     */
    public static String getCreateTableSql() {
        return """
            CREATE TABLE IF NOT EXISTS badge_usage (
                id UUID PRIMARY KEY,
                player_id UUID NOT NULL,
                badge_template_id VARCHAR(255) NOT NULL,
                awarded_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
                award_reason VARCHAR(500),
                metadata JSONB,

                -- Indexes for efficient queries
                CONSTRAINT fk_badge_usage_player FOREIGN KEY (player_id)
                    REFERENCES players(id) ON DELETE CASCADE
            );

            -- Index for player badge lookups
            CREATE INDEX IF NOT EXISTS idx_badge_usage_player_id
                ON badge_usage(player_id);

            -- Index for template-based aggregations (version-agnostic)
            CREATE INDEX IF NOT EXISTS idx_badge_usage_template_id
                ON badge_usage(badge_template_id);

            -- Composite index for player+template queries
            CREATE INDEX IF NOT EXISTS idx_badge_usage_player_template
                ON badge_usage(player_id, badge_template_id);

            -- Index for time-based queries
            CREATE INDEX IF NOT EXISTS idx_badge_usage_awarded_at
                ON badge_usage(awarded_at);
            """;
    }

    /**
     * Generates SQL for counting badge usage by template (version-agnostic).
     * Implements DD52: version-agnostic counting.
     *
     * @return SQL query for badge usage counts
     */
    public static String getCountByTemplateQuery() {
        return """
            SELECT
                badge_template_id,
                COUNT(*) as total_count,
                COUNT(DISTINCT player_id) as unique_players
            FROM badge_usage
            GROUP BY badge_template_id
            ORDER BY total_count DESC
            """;
    }

    /**
     * Generates SQL for counting specific badge template usage.
     *
     * @param badgeTemplateId the template ID to count
     * @return SQL query for specific badge count
     */
    public static String getCountForTemplateQuery(String badgeTemplateId) {
        return """
            SELECT
                badge_template_id,
                COUNT(*) as total_count,
                COUNT(DISTINCT player_id) as unique_players
            FROM badge_usage
            WHERE badge_template_id = '%s'
            GROUP BY badge_template_id
            """.formatted(badgeTemplateId);
    }

    /**
     * Generates SQL for player badge history.
     *
     * @param playerId the player to query
     * @return SQL query for player's badge history
     */
    public static String getPlayerBadgesQuery(UUID playerId) {
        return """
            SELECT
                id,
                player_id,
                badge_template_id,
                awarded_at,
                award_reason,
                metadata
            FROM badge_usage
            WHERE player_id = '%s'
            ORDER BY awarded_at DESC
            """.formatted(playerId);
    }

    /**
     * Generates migration SQL script from badge_awards to badge_usage.
     * Implements DD52: migration script.
     *
     * @return SQL migration script
     */
    public static String getMigrationScript() {
        return """
            -- Migration from badge_awards to badge_usage (DD52)
            -- This migration extracts template IDs from versioned badge IDs

            -- Step 1: Create temporary function to extract template ID
            CREATE OR REPLACE FUNCTION extract_badge_template_id(badge_id VARCHAR)
            RETURNS VARCHAR AS $$
            BEGIN
                -- Remove version suffix (e.g., 'badge_v1' -> 'badge', 'badge_v2.1' -> 'badge')
                RETURN regexp_replace(badge_id, '_v[0-9]+(\\.\\d+)?$', '');
            END;
            $$ LANGUAGE plpgsql IMMUTABLE;

            -- Step 2: Insert into badge_usage with transformed template IDs
            INSERT INTO badge_usage (id, player_id, badge_template_id, awarded_at, award_reason, metadata)
            SELECT
                gen_random_uuid(),
                player_id,
                extract_badge_template_id(badge_id) as badge_template_id,
                awarded_at,
                COALESCE(reason, 'migrated_from_badge_awards') as award_reason,
                jsonb_build_object(
                    'migrated', true,
                    'original_badge_id', badge_id,
                    'migration_timestamp', CURRENT_TIMESTAMP
                ) as metadata
            FROM badge_awards
            WHERE NOT EXISTS (
                SELECT 1 FROM badge_usage bu
                WHERE bu.player_id = badge_awards.player_id
                AND bu.badge_template_id = extract_badge_template_id(badge_awards.badge_id)
                AND bu.awarded_at = badge_awards.awarded_at
            );

            -- Step 3: Log migration results
            DO $$
            DECLARE
                migrated_count INTEGER;
            BEGIN
                GET DIAGNOSTICS migrated_count = ROW_COUNT;
                RAISE NOTICE 'Migrated % records from badge_awards to badge_usage', migrated_count;
            END $$;

            -- Step 4: Cleanup (optional - uncomment to drop old table)
            -- DROP TABLE IF EXISTS badge_awards;

            -- Step 5: Drop temporary function
            DROP FUNCTION IF EXISTS extract_badge_template_id(VARCHAR);
            """;
    }

    /**
     * Generates SQL for inserting a badge usage entry.
     *
     * @param entry the badge usage entry to insert
     * @return SQL insert statement
     */
    public static String getInsertSql(BadgeUsageEntry entry) {
        return """
            INSERT INTO badge_usage (id, player_id, badge_template_id, awarded_at, award_reason, metadata)
            VALUES ('%s', '%s', '%s', '%s', '%s', '%s'::jsonb)
            """.formatted(
                entry.id(),
                entry.playerId(),
                entry.badgeTemplateId(),
                entry.awardedAt(),
                entry.awardReason() != null ? entry.awardReason().replace("'", "''") : "",
                entry.metadata() != null ? entry.metadata().replace("'", "''") : "{}"
            );
    }

    /**
     * Parse a BadgeUsageCount from query results.
     *
     * @param badgeTemplateId the template ID
     * @param totalCount total usage count
     * @param uniquePlayers unique player count
     * @return BadgeUsageCount record
     */
    public static BadgeUsageCount parseCount(String badgeTemplateId, long totalCount, long uniquePlayers) {
        return new BadgeUsageCount(badgeTemplateId, totalCount, uniquePlayers);
    }
}
