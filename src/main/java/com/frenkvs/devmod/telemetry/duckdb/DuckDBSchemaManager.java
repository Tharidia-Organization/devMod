package com.frenkvs.devmod.telemetry.duckdb;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Manages DuckDB schema creation and migrations.
 *
 * Creates all telemetry tables on first run and handles schema versioning
 * for future migrations. Tables are organized by category:
 *
 * - Combat (5): hits, deaths, heals, spawns, fights
 * - Endurance (9): sessions, waves, wave_kills, combos, perks, mutators, rewards, parties, bosses
 * - Player (3): snapshots, attribute_changes, abilities
 * - Progression (6): blocks, xp, advancements, dimensions, trades, fishing
 * - Economy (4): mob_kills, mob_drops, item_pickups, item_usage
 * - Spatial (3): heatmaps, alerts, room_transitions
 * - Dungeon (1): dungeon_runs (P2-B)
 * - Arena (2): arena_template_builds, arena_template_usage (Fase 1)
 * - System (2): performance, migrations
 *
 * Total: 35 tables
 */
public final class DuckDBSchemaManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(DuckDBSchemaManager.class);

    private DuckDBSchemaManager() {} // Utility class

    /**
     * Ensure all tables exist, creating them if necessary.
     * Called during DuckDB initialization.
     */
    public static void ensureSchema(Connection conn) throws SQLException {
        LOGGER.info("[DuckDB] Checking schema...");

        int currentVersion = getCurrentSchemaVersion(conn);

        if (currentVersion == 0) {
            // Fresh install - create all tables
            LOGGER.info("[DuckDB] Creating initial schema (version {})...", DuckDBConfig.SCHEMA_VERSION);
            createAllTables(conn);
            setSchemaVersion(conn, DuckDBConfig.SCHEMA_VERSION);
            LOGGER.info("[DuckDB] Schema created successfully");
        } else if (currentVersion < DuckDBConfig.SCHEMA_VERSION) {
            // Migration needed
            LOGGER.info("[DuckDB] Migrating schema from version {} to {}...", currentVersion, DuckDBConfig.SCHEMA_VERSION);
            migrateSchema(conn, currentVersion, DuckDBConfig.SCHEMA_VERSION);
            LOGGER.info("[DuckDB] Schema migration completed");
        } else {
            LOGGER.info("[DuckDB] Schema is up to date (version {})", currentVersion);
        }
    }

    /**
     * Get current schema version from migrations table.
     */
    private static int getCurrentSchemaVersion(Connection conn) {
        try (Statement stmt = conn.createStatement()) {
            // Check if migrations table exists
            ResultSet rs = stmt.executeQuery(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'migrations'"
            );
            if (rs.next() && rs.getInt(1) == 0) {
                return 0; // No migrations table = fresh install
            }

            // Get latest version
            rs = stmt.executeQuery("SELECT MAX(version) FROM migrations");
            if (rs.next()) {
                return rs.getInt(1);
            }
            return 0;
        } catch (SQLException e) {
            LOGGER.debug("[DuckDB] Could not get schema version: {}", e.getMessage());
            return 0;
        }
    }

    /**
     * Set schema version in migrations table.
     */
    private static void setSchemaVersion(Connection conn, int version) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(String.format(
                "INSERT INTO migrations (version, migrated_at) VALUES (%d, NOW())", version
            ));
        }
    }

    /**
     * Create all tables for initial schema.
     */
    private static void createAllTables(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            // System tables first
            createMigrationsTable(stmt);
            createPerformanceTable(stmt);

            // Combat tables
            createCombatHitsTable(stmt);
            createCombatDeathsTable(stmt);
            createCombatHealsTable(stmt);
            createCombatSpawnsTable(stmt);
            createCombatFightsTable(stmt);

            // Endurance tables
            createEnduranceSessionsTable(stmt);
            createEnduranceWavesTable(stmt);
            createEnduranceWaveKillsTable(stmt);
            createEnduranceCombosTable(stmt);
            createEndurancePerksTable(stmt);
            createEnduranceMutatorsTable(stmt);
            createEnduranceRewardsTable(stmt);
            createEndurancePartiesTable(stmt);
            createEnduranceBossesTable(stmt);

            // Player tables
            createPlayerSnapshotsTable(stmt);
            createPlayerAttributeChangesTable(stmt);
            createPlayerAbilitiesTable(stmt);

            // Progression tables
            createProgressionBlocksTable(stmt);
            createProgressionXpTable(stmt);
            createProgressionAdvancementsTable(stmt);
            createProgressionDimensionsTable(stmt);
            createProgressionTradesTable(stmt);
            createProgressionFishingTable(stmt);

            // Economy tables
            createEconomyMobKillsTable(stmt);
            createEconomyMobDropsTable(stmt);
            createEconomyItemPickupsTable(stmt);
            createEconomyItemUsageTable(stmt);

            // Spatial tables
            createSpatialHeatmapsTable(stmt);
            createSpatialAlertsTable(stmt);
            createSpatialRoomTransitionsTable(stmt);

            // Dungeon tables (P2-B)
            createDungeonRunsTable(stmt);

            // Arena tables (Fase 1)
            createArenaTemplateBuildsTable(stmt);
            createArenaTemplateUsageTable(stmt);

            LOGGER.info("[DuckDB] Created 35 tables");
        }
    }

    // ============================================
    // SYSTEM TABLES
    // ============================================

    private static void createMigrationsTable(Statement stmt) throws SQLException {
        stmt.execute("""
            CREATE TABLE IF NOT EXISTS migrations (
                version         INTEGER PRIMARY KEY,
                migrated_at     TIMESTAMP NOT NULL DEFAULT NOW()
            )
            """);
    }

    private static void createPerformanceTable(Statement stmt) throws SQLException {
        stmt.execute("""
            CREATE TABLE IF NOT EXISTS performance_samples (
                id              BIGINT PRIMARY KEY,
                ts              TIMESTAMP NOT NULL,
                mspt            DOUBLE,
                tps             DOUBLE
            )
            """);
        stmt.execute("CREATE SEQUENCE IF NOT EXISTS seq_performance_samples START 1");
    }

    // ============================================
    // COMBAT TABLES
    // ============================================

    private static void createCombatHitsTable(Statement stmt) throws SQLException {
        stmt.execute("""
            CREATE TABLE IF NOT EXISTS combat_hits (
                id              BIGINT PRIMARY KEY,
                ts              TIMESTAMP NOT NULL,
                room            VARCHAR(128),
                world           VARCHAR(128),
                attacker_name   VARCHAR(64),
                attacker_type   VARCHAR(128),
                target_name     VARCHAR(64),
                target_type     VARCHAR(128),
                damage          DOUBLE NOT NULL,
                damage_type     VARCHAR(64),
                hp_before       DOUBLE,
                hp_after        DOUBLE,
                body_part       VARCHAR(32),
                distance        DOUBLE,
                armor_pen_bonus DOUBLE,
                is_miss         BOOLEAN DEFAULT FALSE,
                is_hazard       BOOLEAN DEFAULT FALSE,
                hazard_type     VARCHAR(64),
                attacker_state  JSON,
                target_state    JSON
            )
            """);
        stmt.execute("CREATE SEQUENCE IF NOT EXISTS seq_combat_hits START 1");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_combat_hits_ts ON combat_hits(ts)");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_combat_hits_room ON combat_hits(room)");
    }

    private static void createCombatDeathsTable(Statement stmt) throws SQLException {
        stmt.execute("""
            CREATE TABLE IF NOT EXISTS combat_deaths (
                id              BIGINT PRIMARY KEY,
                ts              TIMESTAMP NOT NULL,
                room            VARCHAR(128),
                world           VARCHAR(128),
                target_name     VARCHAR(64),
                target_type     VARCHAR(128),
                cause           VARCHAR(128),
                ttk_first_hit_ms BIGINT,
                ttk_spawn_ms    BIGINT
            )
            """);
        stmt.execute("CREATE SEQUENCE IF NOT EXISTS seq_combat_deaths START 1");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_combat_deaths_ts ON combat_deaths(ts)");
    }

    private static void createCombatHealsTable(Statement stmt) throws SQLException {
        stmt.execute("""
            CREATE TABLE IF NOT EXISTS combat_heals (
                id              BIGINT PRIMARY KEY,
                ts              TIMESTAMP NOT NULL,
                room            VARCHAR(128),
                world           VARCHAR(128),
                target_name     VARCHAR(64),
                target_type     VARCHAR(128),
                heal_amount     DOUBLE,
                hp_before       DOUBLE,
                hp_after        DOUBLE,
                source          VARCHAR(64)
            )
            """);
        stmt.execute("CREATE SEQUENCE IF NOT EXISTS seq_combat_heals START 1");
    }

    private static void createCombatSpawnsTable(Statement stmt) throws SQLException {
        stmt.execute("""
            CREATE TABLE IF NOT EXISTS combat_spawns (
                id              BIGINT PRIMARY KEY,
                ts              TIMESTAMP NOT NULL,
                room            VARCHAR(128),
                world           VARCHAR(128),
                entity_name     VARCHAR(64),
                entity_type     VARCHAR(128),
                reason          VARCHAR(64),
                spawn_fail      BOOLEAN DEFAULT FALSE,
                x               DOUBLE,
                y               DOUBLE,
                z               DOUBLE
            )
            """);
        stmt.execute("CREATE SEQUENCE IF NOT EXISTS seq_combat_spawns START 1");
    }

    private static void createCombatFightsTable(Statement stmt) throws SQLException {
        stmt.execute("""
            CREATE TABLE IF NOT EXISTS combat_fights (
                id              BIGINT PRIMARY KEY,
                room            VARCHAR(128),
                world           VARCHAR(128),
                start_ts        TIMESTAMP NOT NULL,
                end_ts          TIMESTAMP NOT NULL,
                duration_ms     BIGINT,
                hits            INTEGER,
                mob_kills       INTEGER,
                player_deaths   INTEGER,
                players         VARCHAR[],
                mob_kills_by_type   JSON,
                player_deaths_by_name JSON,
                ttk_by_type     JSON,
                burst_max       DOUBLE,
                hp_after_players_avg DOUBLE,
                hp_after_mobs_avg DOUBLE
            )
            """);
        stmt.execute("CREATE SEQUENCE IF NOT EXISTS seq_combat_fights START 1");
    }

    // ============================================
    // ENDURANCE TABLES
    // ============================================

    private static void createEnduranceSessionsTable(Statement stmt) throws SQLException {
        stmt.execute("""
            CREATE TABLE IF NOT EXISTS endurance_sessions (
                id              UUID PRIMARY KEY,
                player_id       UUID NOT NULL,
                player_name     VARCHAR(64),
                quest_name      VARCHAR(128),
                quest_type      VARCHAR(32),
                total_waves     INTEGER,
                is_endless      BOOLEAN DEFAULT FALSE,
                player_count    INTEGER DEFAULT 1,
                start_ts        TIMESTAMP NOT NULL,
                end_ts          TIMESTAMP,
                outcome         VARCHAR(32),
                waves_completed INTEGER DEFAULT 0,
                total_kills     INTEGER DEFAULT 0,
                damage_dealt    DOUBLE DEFAULT 0,
                damage_taken    DOUBLE DEFAULT 0,
                tokens_earned   INTEGER DEFAULT 0,
                prestige_earned INTEGER DEFAULT 0,
                blood_gems_earned INTEGER DEFAULT 0,
                no_damage_waves INTEGER DEFAULT 0
            )
            """);
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_endurance_sessions_player ON endurance_sessions(player_id)");
    }

    private static void createEnduranceWavesTable(Statement stmt) throws SQLException {
        stmt.execute("""
            CREATE TABLE IF NOT EXISTS endurance_waves (
                id              BIGINT PRIMARY KEY,
                ts              TIMESTAMP NOT NULL,
                session_id      UUID,
                wave_number     INTEGER NOT NULL,
                event_type      VARCHAR(32),
                mob_count       INTEGER,
                player_count    INTEGER,
                quest_type      VARCHAR(32),
                modifiers       VARCHAR,
                mobs_killed     INTEGER,
                duration_ms     BIGINT,
                no_damage       BOOLEAN,
                kills_per_second DOUBLE
            )
            """);
        stmt.execute("CREATE SEQUENCE IF NOT EXISTS seq_endurance_waves START 1");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_endurance_waves_session ON endurance_waves(session_id)");
    }

    private static void createEnduranceWaveKillsTable(Statement stmt) throws SQLException {
        stmt.execute("""
            CREATE TABLE IF NOT EXISTS endurance_wave_kills (
                id              BIGINT PRIMARY KEY,
                ts              TIMESTAMP NOT NULL,
                session_id      UUID,
                wave_number     INTEGER,
                mob_type        VARCHAR(128),
                is_elite        BOOLEAN DEFAULT FALSE,
                killer_weapon   VARCHAR(128),
                damage_dealt    DOUBLE
            )
            """);
        stmt.execute("CREATE SEQUENCE IF NOT EXISTS seq_endurance_wave_kills START 1");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_endurance_wave_kills_session ON endurance_wave_kills(session_id)");
    }

    private static void createEnduranceCombosTable(Statement stmt) throws SQLException {
        stmt.execute("""
            CREATE TABLE IF NOT EXISTS endurance_combos (
                id              BIGINT PRIMARY KEY,
                ts              TIMESTAMP NOT NULL,
                player_id       UUID,
                session_id      UUID,
                event_type      VARCHAR(32),
                old_rank        VARCHAR(16),
                new_rank        VARCHAR(16),
                style_score     INTEGER,
                current_combo   INTEGER,
                milestone       INTEGER,
                combo_lost      INTEGER,
                damage_taken    DOUBLE,
                action_type     VARCHAR(32),
                points_earned   INTEGER,
                style_earned    INTEGER
            )
            """);
        stmt.execute("CREATE SEQUENCE IF NOT EXISTS seq_endurance_combos START 1");
    }

    private static void createEndurancePerksTable(Statement stmt) throws SQLException {
        stmt.execute("""
            CREATE TABLE IF NOT EXISTS endurance_perks (
                id              BIGINT PRIMARY KEY,
                ts              TIMESTAMP NOT NULL,
                player_id       UUID,
                session_id      UUID,
                event_type      VARCHAR(32),
                perk_id         VARCHAR(64),
                perk_name       VARCHAR(128),
                tier            VARCHAR(16),
                category        VARCHAR(32),
                stack_count     INTEGER,
                total_perks     INTEGER,
                wave_number     INTEGER,
                choices         JSON
            )
            """);
        stmt.execute("CREATE SEQUENCE IF NOT EXISTS seq_endurance_perks START 1");
    }

    private static void createEnduranceMutatorsTable(Statement stmt) throws SQLException {
        stmt.execute("""
            CREATE TABLE IF NOT EXISTS endurance_mutators (
                id              BIGINT PRIMARY KEY,
                ts              TIMESTAMP NOT NULL,
                session_id      UUID,
                event_type      VARCHAR(32),
                mutator_id      VARCHAR(64),
                mutator_category VARCHAR(32),
                wave_number     INTEGER,
                reward_multiplier DOUBLE,
                mutator_count   INTEGER,
                mutators        JSON
            )
            """);
        stmt.execute("CREATE SEQUENCE IF NOT EXISTS seq_endurance_mutators START 1");
    }

    private static void createEnduranceRewardsTable(Statement stmt) throws SQLException {
        stmt.execute("""
            CREATE TABLE IF NOT EXISTS endurance_rewards (
                id              BIGINT PRIMARY KEY,
                ts              TIMESTAMP NOT NULL,
                player_id       UUID,
                session_id      UUID,
                event_type      VARCHAR(32),
                currency        VARCHAR(32),
                amount          INTEGER,
                source          VARCHAR(64),
                item_id         VARCHAR(128),
                item_count      INTEGER,
                loot_tier       VARCHAR(16),
                achievement_id  VARCHAR(64),
                achievement_name VARCHAR(128),
                price           INTEGER,
                purchase_count  INTEGER
            )
            """);
        stmt.execute("CREATE SEQUENCE IF NOT EXISTS seq_endurance_rewards START 1");
    }

    private static void createEndurancePartiesTable(Statement stmt) throws SQLException {
        stmt.execute("""
            CREATE TABLE IF NOT EXISTS endurance_parties (
                id              BIGINT PRIMARY KEY,
                ts              TIMESTAMP NOT NULL,
                party_id        UUID,
                event_type      VARCHAR(32),
                leader_id       UUID,
                leader_name     VARCHAR(64),
                member_id       UUID,
                member_name     VARCHAR(64),
                quest_type      VARCHAR(32),
                party_size      INTEGER,
                reason          VARCHAR(64),
                accepted        BOOLEAN
            )
            """);
        stmt.execute("CREATE SEQUENCE IF NOT EXISTS seq_endurance_parties START 1");
    }

    private static void createEnduranceBossesTable(Statement stmt) throws SQLException {
        stmt.execute("""
            CREATE TABLE IF NOT EXISTS endurance_bosses (
                id              BIGINT PRIMARY KEY,
                ts              TIMESTAMP NOT NULL,
                session_id      UUID,
                event_type      VARCHAR(32),
                wave_number     INTEGER,
                archetype       VARCHAR(64),
                boss_max_health DOUBLE,
                player_count    INTEGER,
                ability_name    VARCHAR(64),
                players_hit     INTEGER,
                ability_damage  DOUBLE,
                fight_duration_ms BIGINT,
                bonus_points    INTEGER,
                damage_dealt_to_boss DOUBLE
            )
            """);
        stmt.execute("CREATE SEQUENCE IF NOT EXISTS seq_endurance_bosses START 1");
    }

    // ============================================
    // PLAYER TABLES
    // ============================================

    private static void createPlayerSnapshotsTable(Statement stmt) throws SQLException {
        stmt.execute("""
            CREATE TABLE IF NOT EXISTS player_snapshots (
                id              BIGINT PRIMARY KEY,
                ts              TIMESTAMP NOT NULL,
                player_id       UUID NOT NULL,
                player_name     VARCHAR(64),
                trigger_type    VARCHAR(32),
                health_hp       DOUBLE,
                max_health_hp   DOUBLE,
                health_hearts   INTEGER,
                absorption_hp   DOUBLE,
                hunger_level    INTEGER,
                saturation      DOUBLE,
                exhaustion      DOUBLE,
                movement_speed  DOUBLE,
                velocity_x      DOUBLE,
                velocity_y      DOUBLE,
                velocity_z      DOUBLE,
                movement_flags  INTEGER,
                melee_damage_mult DOUBLE,
                melee_reduction DOUBLE,
                magic_damage_mult DOUBLE,
                magic_reduction DOUBLE,
                ranged_damage_mult DOUBLE,
                ranged_reduction DOUBLE,
                armor_value     DOUBLE,
                armor_toughness DOUBLE,
                knockback_resistance DOUBLE,
                total_damage_reduction DOUBLE,
                reach           DOUBLE,
                hitbox_width    DOUBLE,
                hitbox_height   DOUBLE,
                pehkui_scale    DOUBLE,
                pehkui_hitbox_scale DOUBLE,
                stamina         DOUBLE,
                max_stamina     DOUBLE,
                dash_cooldown   DOUBLE,
                dodge_cooldown  DOUBLE,
                ability_flags   INTEGER,
                current_combo   INTEGER,
                style_rank      VARCHAR(16),
                style_score     INTEGER,
                x               DOUBLE,
                y               DOUBLE,
                z               DOUBLE,
                dimension       VARCHAR(128)
            )
            """);
        stmt.execute("CREATE SEQUENCE IF NOT EXISTS seq_player_snapshots START 1");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_player_snapshots_player ON player_snapshots(player_id)");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_player_snapshots_ts ON player_snapshots(ts)");
    }

    private static void createPlayerAttributeChangesTable(Statement stmt) throws SQLException {
        stmt.execute("""
            CREATE TABLE IF NOT EXISTS player_attribute_changes (
                id              BIGINT PRIMARY KEY,
                ts              TIMESTAMP NOT NULL,
                player_id       UUID NOT NULL,
                attribute_name  VARCHAR(64),
                old_value       DOUBLE,
                new_value       DOUBLE,
                delta           DOUBLE
            )
            """);
        stmt.execute("CREATE SEQUENCE IF NOT EXISTS seq_player_attribute_changes START 1");
    }

    private static void createPlayerAbilitiesTable(Statement stmt) throws SQLException {
        stmt.execute("""
            CREATE TABLE IF NOT EXISTS player_abilities (
                id              BIGINT PRIMARY KEY,
                ts              TIMESTAMP NOT NULL,
                player_id       UUID NOT NULL,
                ability_type    VARCHAR(32),
                success         BOOLEAN,
                result          INTEGER,
                stamina_before  DOUBLE,
                stamina_after   DOUBLE,
                stamina_cost    DOUBLE,
                damage_negated  DOUBLE,
                damage_source   VARCHAR(64),
                context         VARCHAR(64),
                regen_time_ms   BIGINT
            )
            """);
        stmt.execute("CREATE SEQUENCE IF NOT EXISTS seq_player_abilities START 1");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_player_abilities_player ON player_abilities(player_id)");
    }

    // ============================================
    // PROGRESSION TABLES
    // ============================================

    private static void createProgressionBlocksTable(Statement stmt) throws SQLException {
        stmt.execute("""
            CREATE TABLE IF NOT EXISTS progression_blocks (
                id              BIGINT PRIMARY KEY,
                ts              TIMESTAMP NOT NULL,
                player_id       UUID NOT NULL,
                player_name     VARCHAR(64),
                world_id        VARCHAR(128),
                room            VARCHAR(128),
                event_type      VARCHAR(16),
                block_id        VARCHAR(128),
                x               INTEGER,
                y               INTEGER,
                z               INTEGER
            )
            """);
        stmt.execute("CREATE SEQUENCE IF NOT EXISTS seq_progression_blocks START 1");
    }

    private static void createProgressionXpTable(Statement stmt) throws SQLException {
        stmt.execute("""
            CREATE TABLE IF NOT EXISTS progression_xp (
                id              BIGINT PRIMARY KEY,
                ts              TIMESTAMP NOT NULL,
                player_id       UUID NOT NULL,
                player_name     VARCHAR(64),
                world_id        VARCHAR(128),
                room            VARCHAR(128),
                event_type      VARCHAR(16),
                xp_amount       INTEGER,
                old_level       INTEGER,
                new_level       INTEGER,
                x               INTEGER,
                y               INTEGER,
                z               INTEGER
            )
            """);
        stmt.execute("CREATE SEQUENCE IF NOT EXISTS seq_progression_xp START 1");
    }

    private static void createProgressionAdvancementsTable(Statement stmt) throws SQLException {
        stmt.execute("""
            CREATE TABLE IF NOT EXISTS progression_advancements (
                id              BIGINT PRIMARY KEY,
                ts              TIMESTAMP NOT NULL,
                player_id       UUID NOT NULL,
                player_name     VARCHAR(64),
                world_id        VARCHAR(128),
                room            VARCHAR(128),
                advancement_id  VARCHAR(256),
                title           VARCHAR(128),
                x               INTEGER,
                y               INTEGER,
                z               INTEGER
            )
            """);
        stmt.execute("CREATE SEQUENCE IF NOT EXISTS seq_progression_advancements START 1");
    }

    private static void createProgressionDimensionsTable(Statement stmt) throws SQLException {
        stmt.execute("""
            CREATE TABLE IF NOT EXISTS progression_dimensions (
                id              BIGINT PRIMARY KEY,
                ts              TIMESTAMP NOT NULL,
                player_id       UUID NOT NULL,
                player_name     VARCHAR(64),
                world_id        VARCHAR(128),
                from_dimension  VARCHAR(128),
                to_dimension    VARCHAR(128),
                x               INTEGER,
                y               INTEGER,
                z               INTEGER
            )
            """);
        stmt.execute("CREATE SEQUENCE IF NOT EXISTS seq_progression_dimensions START 1");
    }

    private static void createProgressionTradesTable(Statement stmt) throws SQLException {
        stmt.execute("""
            CREATE TABLE IF NOT EXISTS progression_trades (
                id              BIGINT PRIMARY KEY,
                ts              TIMESTAMP NOT NULL,
                player_id       UUID NOT NULL,
                player_name     VARCHAR(64),
                world_id        VARCHAR(128),
                room            VARCHAR(128),
                villager_type   VARCHAR(128),
                profession      VARCHAR(64),
                item_bought     VARCHAR(128),
                item_bought_count INTEGER,
                item_sold       VARCHAR(128),
                item_sold_count INTEGER,
                x               INTEGER,
                y               INTEGER,
                z               INTEGER
            )
            """);
        stmt.execute("CREATE SEQUENCE IF NOT EXISTS seq_progression_trades START 1");
    }

    private static void createProgressionFishingTable(Statement stmt) throws SQLException {
        stmt.execute("""
            CREATE TABLE IF NOT EXISTS progression_fishing (
                id              BIGINT PRIMARY KEY,
                ts              TIMESTAMP NOT NULL,
                player_id       UUID NOT NULL,
                player_name     VARCHAR(64),
                world_id        VARCHAR(128),
                room            VARCHAR(128),
                item_id         VARCHAR(128),
                item_count      INTEGER,
                x               INTEGER,
                y               INTEGER,
                z               INTEGER
            )
            """);
        stmt.execute("CREATE SEQUENCE IF NOT EXISTS seq_progression_fishing START 1");
    }

    // ============================================
    // ECONOMY TABLES
    // ============================================

    private static void createEconomyMobKillsTable(Statement stmt) throws SQLException {
        stmt.execute("""
            CREATE TABLE IF NOT EXISTS economy_mob_kills (
                id              BIGINT PRIMARY KEY,
                ts              TIMESTAMP NOT NULL,
                mob_type        VARCHAR(128),
                total_kills     INTEGER,
                had_loot        BOOLEAN
            )
            """);
        stmt.execute("CREATE SEQUENCE IF NOT EXISTS seq_economy_mob_kills START 1");
    }

    private static void createEconomyMobDropsTable(Statement stmt) throws SQLException {
        stmt.execute("""
            CREATE TABLE IF NOT EXISTS economy_mob_drops (
                id              BIGINT PRIMARY KEY,
                ts              TIMESTAMP NOT NULL,
                mob_type        VARCHAR(128),
                room            VARCHAR(128),
                item_id         VARCHAR(128),
                item_count      INTEGER,
                x               INTEGER,
                y               INTEGER,
                z               INTEGER
            )
            """);
        stmt.execute("CREATE SEQUENCE IF NOT EXISTS seq_economy_mob_drops START 1");
    }

    private static void createEconomyItemPickupsTable(Statement stmt) throws SQLException {
        stmt.execute("""
            CREATE TABLE IF NOT EXISTS economy_item_pickups (
                id              BIGINT PRIMARY KEY,
                ts              TIMESTAMP NOT NULL,
                player_id       UUID NOT NULL,
                player_name     VARCHAR(64),
                room            VARCHAR(128),
                item_id         VARCHAR(128),
                item_count      INTEGER,
                x               INTEGER,
                y               INTEGER,
                z               INTEGER
            )
            """);
        stmt.execute("CREATE SEQUENCE IF NOT EXISTS seq_economy_item_pickups START 1");
    }

    private static void createEconomyItemUsageTable(Statement stmt) throws SQLException {
        stmt.execute("""
            CREATE TABLE IF NOT EXISTS economy_item_usage (
                id              BIGINT PRIMARY KEY,
                ts              TIMESTAMP NOT NULL,
                player_id       UUID NOT NULL,
                player_name     VARCHAR(64),
                event_type      VARCHAR(16),
                item_id         VARCHAR(128),
                item_count      INTEGER,
                use_type        VARCHAR(32)
            )
            """);
        stmt.execute("CREATE SEQUENCE IF NOT EXISTS seq_economy_item_usage START 1");
    }

    // ============================================
    // SPATIAL TABLES
    // ============================================

    private static void createSpatialHeatmapsTable(Statement stmt) throws SQLException {
        stmt.execute("""
            CREATE TABLE IF NOT EXISTS spatial_heatmaps (
                id              BIGINT PRIMARY KEY,
                ts              TIMESTAMP NOT NULL,
                heatmap_type    VARCHAR(32),
                room            VARCHAR(128),
                x               INTEGER,
                y               INTEGER,
                z               INTEGER,
                count           INTEGER DEFAULT 1
            )
            """);
        stmt.execute("CREATE SEQUENCE IF NOT EXISTS seq_spatial_heatmaps START 1");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_spatial_heatmaps_type ON spatial_heatmaps(heatmap_type)");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_spatial_heatmaps_room ON spatial_heatmaps(room)");
    }

    private static void createSpatialAlertsTable(Statement stmt) throws SQLException {
        stmt.execute("""
            CREATE TABLE IF NOT EXISTS spatial_alerts (
                id              BIGINT PRIMARY KEY,
                ts              TIMESTAMP NOT NULL,
                alert_type      VARCHAR(32),
                player_name     VARCHAR(64),
                entity_name     VARCHAR(64),
                entity_type     VARCHAR(128),
                room            VARCHAR(128),
                x               DOUBLE,
                y               DOUBLE,
                z               DOUBLE,
                extra_data      JSON
            )
            """);
        stmt.execute("CREATE SEQUENCE IF NOT EXISTS seq_spatial_alerts START 1");
    }

    private static void createSpatialRoomTransitionsTable(Statement stmt) throws SQLException {
        stmt.execute("""
            CREATE TABLE IF NOT EXISTS spatial_room_transitions (
                id              BIGINT PRIMARY KEY,
                ts              TIMESTAMP NOT NULL,
                player_id       UUID NOT NULL,
                player_name     VARCHAR(64),
                room            VARCHAR(128)
            )
            """);
        stmt.execute("CREATE SEQUENCE IF NOT EXISTS seq_spatial_room_transitions START 1");
    }

    // ============================================
    // DUNGEON TABLES (P2-B)
    // ============================================

    private static void createDungeonRunsTable(Statement stmt) throws SQLException {
        stmt.execute("""
            CREATE TABLE IF NOT EXISTS dungeon_runs (
                id              BIGINT PRIMARY KEY,
                start_ts        TIMESTAMP NOT NULL,
                end_ts          TIMESTAMP NOT NULL,
                duration_ms     BIGINT NOT NULL,
                player_id       VARCHAR(64) NOT NULL,
                player_name     VARCHAR(64),
                dungeon_id      VARCHAR(128) NOT NULL,
                outcome         VARCHAR(32) NOT NULL,
                rooms_visited   INTEGER NOT NULL DEFAULT 1,
                rooms_list      VARCHAR,
                deaths          INTEGER NOT NULL DEFAULT 0,
                kills           INTEGER NOT NULL DEFAULT 0,
                enemies_killed  VARCHAR,
                damage_dealt    REAL NOT NULL DEFAULT 0,
                damage_taken    REAL NOT NULL DEFAULT 0,
                reward_count    INTEGER NOT NULL DEFAULT 0,
                loot_collected  VARCHAR,
                last_death_room VARCHAR(128)
            )
            """);
        stmt.execute("CREATE SEQUENCE IF NOT EXISTS seq_dungeon_runs START 1");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_dungeon_runs_start_ts ON dungeon_runs(start_ts)");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_dungeon_runs_dungeon ON dungeon_runs(dungeon_id)");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_dungeon_runs_player ON dungeon_runs(player_id)");
    }

    // ============================================
    // ARENA TABLES (Fase 1)
    // ============================================

    private static void createArenaTemplateBuildsTable(Statement stmt) throws SQLException {
        stmt.execute("""
            CREATE TABLE IF NOT EXISTS arena_template_builds (
                id              BIGINT PRIMARY KEY,
                ts              TIMESTAMP NOT NULL,
                arena_id        UUID NOT NULL,
                template_id     VARCHAR(128) NOT NULL,
                template_version INTEGER NOT NULL,
                policy_id       VARCHAR(128),
                policy_version  INTEGER,
                origin_x        INTEGER,
                origin_y        INTEGER,
                origin_z        INTEGER,
                dimension       VARCHAR(128),
                estimated_blocks INTEGER,
                actual_blocks   INTEGER,
                estimated_ms    BIGINT,
                actual_ms       BIGINT,
                success         BOOLEAN NOT NULL,
                error_message   VARCHAR(512),
                rollback_ms     BIGINT,
                blocks_reverted INTEGER
            )
            """);
        stmt.execute("CREATE SEQUENCE IF NOT EXISTS seq_arena_template_builds START 1");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_arena_template_builds_ts ON arena_template_builds(ts)");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_arena_template_builds_template ON arena_template_builds(template_id)");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_arena_template_builds_arena ON arena_template_builds(arena_id)");
    }

    private static void createArenaTemplateUsageTable(Statement stmt) throws SQLException {
        stmt.execute("""
            CREATE TABLE IF NOT EXISTS arena_template_usage (
                id              BIGINT PRIMARY KEY,
                ts              TIMESTAMP NOT NULL,
                template_id     VARCHAR(128) NOT NULL,
                template_version INTEGER NOT NULL,
                policy_id       VARCHAR(128),
                policy_version  INTEGER,
                player_id       UUID,
                player_name     VARCHAR(64),
                quest_type      VARCHAR(64),
                mob_id          VARCHAR(128),
                difficulty      VARCHAR(32),
                player_count    INTEGER,
                session_id      UUID,
                event_type      VARCHAR(32) NOT NULL,
                duration_ms     BIGINT,
                waves_completed INTEGER,
                outcome         VARCHAR(32)
            )
            """);
        stmt.execute("CREATE SEQUENCE IF NOT EXISTS seq_arena_template_usage START 1");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_arena_template_usage_ts ON arena_template_usage(ts)");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_arena_template_usage_template ON arena_template_usage(template_id)");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_arena_template_usage_player ON arena_template_usage(player_id)");
    }

    // ============================================
    // MIGRATIONS
    // ============================================

    /**
     * Migrate schema from oldVersion to newVersion.
     * Add migration steps here for future schema changes.
     */
    private static void migrateSchema(Connection conn, int oldVersion, int newVersion) throws SQLException {
        // Migration V1 -> V2: Add dungeon_runs table (P2-B)
        if (oldVersion < 2) {
            LOGGER.info("[DuckDB] Running migration V1 -> V2: Adding dungeon_runs table");
            try (Statement stmt = conn.createStatement()) {
                createDungeonRunsTable(stmt);
            }
        }

        // Migration V2 -> V3: Add arena tables (Fase 1)
        if (oldVersion < 3) {
            LOGGER.info("[DuckDB] Running migration V2 -> V3: Adding arena_template_builds and arena_template_usage tables");
            try (Statement stmt = conn.createStatement()) {
                createArenaTemplateBuildsTable(stmt);
                createArenaTemplateUsageTable(stmt);
            }
        }

        setSchemaVersion(conn, newVersion);
    }
}
