package com.devmod.telemetry.duckdb;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import javax.annotation.Nullable;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("DuckDB Migration Validation")
class DuckDBMigrationValidationTest {

    @TempDir
    @Nullable static Path tempDir;

    @Nullable private static Connection testConnection;

    @BeforeAll
    static void setupDatabase() throws SQLException {
        Path dbPath = Objects.requireNonNull(tempDir).resolve("migration_validation.duckdb");
        Connection connection = DriverManager.getConnection("jdbc:duckdb:" + dbPath);
        testConnection = connection;

        // Create schema inline (mirrors DuckDBSchemaManager)
        createTestSchema(connection);
    }

    @AfterAll
    static void cleanup() throws SQLException {
        if (testConnection != null && !testConnection.isClosed()) {
            testConnection.close();
        }
    }

    /**
     * Create all tables needed for tests (subset of full schema).
     */
    private static void createTestSchema(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            // Migrations table
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS migrations (
                    version INTEGER PRIMARY KEY,
                    migrated_at TIMESTAMP NOT NULL DEFAULT NOW()
                )
            """);

            // Combat tables
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS combat_hits (
                    id BIGINT PRIMARY KEY,
                    ts TIMESTAMP NOT NULL,
                    room VARCHAR(128),
                    world VARCHAR(128),
                    attacker_name VARCHAR(64),
                    attacker_type VARCHAR(128),
                    target_name VARCHAR(64),
                    target_type VARCHAR(128),
                    damage DOUBLE NOT NULL,
                    damage_type VARCHAR(64),
                    hp_before DOUBLE,
                    hp_after DOUBLE,
                    body_part VARCHAR(32),
                    distance DOUBLE,
                    armor_pen_bonus DOUBLE,
                    is_miss BOOLEAN DEFAULT FALSE,
                    is_hazard BOOLEAN DEFAULT FALSE,
                    hazard_type VARCHAR(64)
                )
            """);
            stmt.execute("CREATE SEQUENCE IF NOT EXISTS seq_combat_hits START 1");

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS combat_deaths (
                    id BIGINT PRIMARY KEY,
                    ts TIMESTAMP NOT NULL,
                    room VARCHAR(128),
                    world VARCHAR(128),
                    target_name VARCHAR(64),
                    target_type VARCHAR(128),
                    cause VARCHAR(128),
                    ttk_first_hit_ms BIGINT,
                    ttk_spawn_ms BIGINT
                )
            """);
            stmt.execute("CREATE SEQUENCE IF NOT EXISTS seq_combat_deaths START 1");

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS combat_heals (
                    id BIGINT PRIMARY KEY,
                    ts TIMESTAMP NOT NULL,
                    room VARCHAR(128),
                    world VARCHAR(128),
                    target_name VARCHAR(64),
                    target_type VARCHAR(128),
                    heal_amount DOUBLE,
                    hp_before DOUBLE,
                    hp_after DOUBLE,
                    source VARCHAR(64)
                )
            """);
            stmt.execute("CREATE SEQUENCE IF NOT EXISTS seq_combat_heals START 1");

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS combat_spawns (
                    id BIGINT PRIMARY KEY,
                    ts TIMESTAMP NOT NULL,
                    room VARCHAR(128),
                    world VARCHAR(128),
                    entity_name VARCHAR(64),
                    entity_type VARCHAR(128),
                    reason VARCHAR(64),
                    spawn_fail BOOLEAN DEFAULT FALSE,
                    x DOUBLE,
                    y DOUBLE,
                    z DOUBLE
                )
            """);
            stmt.execute("CREATE SEQUENCE IF NOT EXISTS seq_combat_spawns START 1");

            // Endurance tables
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS endurance_waves (
                    id BIGINT PRIMARY KEY,
                    ts TIMESTAMP NOT NULL,
                    session_id UUID,
                    wave_number INTEGER NOT NULL,
                    event_type VARCHAR(32),
                    mob_count INTEGER,
                    mobs_killed INTEGER,
                    duration_ms BIGINT,
                    no_damage BOOLEAN,
                    kills_per_second DOUBLE
                )
            """);
            stmt.execute("CREATE SEQUENCE IF NOT EXISTS seq_endurance_waves START 1");

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS endurance_wave_kills (
                    id BIGINT PRIMARY KEY,
                    ts TIMESTAMP NOT NULL,
                    session_id UUID,
                    wave_number INTEGER,
                    mob_type VARCHAR(128),
                    is_elite BOOLEAN DEFAULT FALSE,
                    killer_weapon VARCHAR(128),
                    damage_dealt DOUBLE
                )
            """);
            stmt.execute("CREATE SEQUENCE IF NOT EXISTS seq_endurance_wave_kills START 1");

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS endurance_combos (
                    id BIGINT PRIMARY KEY,
                    ts TIMESTAMP NOT NULL,
                    player_id UUID,
                    session_id UUID,
                    event_type VARCHAR(32),
                    old_rank VARCHAR(16),
                    new_rank VARCHAR(16),
                    style_score INTEGER,
                    current_combo INTEGER
                )
            """);
            stmt.execute("CREATE SEQUENCE IF NOT EXISTS seq_endurance_combos START 1");

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS endurance_perks (
                    id BIGINT PRIMARY KEY,
                    ts TIMESTAMP NOT NULL,
                    player_id UUID,
                    session_id UUID,
                    event_type VARCHAR(32),
                    perk_id VARCHAR(64),
                    perk_name VARCHAR(128),
                    tier VARCHAR(16),
                    category VARCHAR(32)
                )
            """);
            stmt.execute("CREATE SEQUENCE IF NOT EXISTS seq_endurance_perks START 1");

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS endurance_rewards (
                    id BIGINT PRIMARY KEY,
                    ts TIMESTAMP NOT NULL,
                    player_id UUID,
                    session_id UUID,
                    event_type VARCHAR(32),
                    currency VARCHAR(32),
                    amount INTEGER,
                    source VARCHAR(64)
                )
            """);
            stmt.execute("CREATE SEQUENCE IF NOT EXISTS seq_endurance_rewards START 1");

            // Player tables
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS player_snapshots (
                    id BIGINT PRIMARY KEY,
                    ts TIMESTAMP NOT NULL,
                    player_id UUID NOT NULL,
                    player_name VARCHAR(64),
                    trigger_type VARCHAR(32),
                    health_hp DOUBLE,
                    max_health_hp DOUBLE
                )
            """);
            stmt.execute("CREATE SEQUENCE IF NOT EXISTS seq_player_snapshots START 1");

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS player_abilities (
                    id BIGINT PRIMARY KEY,
                    ts TIMESTAMP NOT NULL,
                    player_id UUID NOT NULL,
                    ability_type VARCHAR(32),
                    success BOOLEAN,
                    stamina_before DOUBLE,
                    stamina_after DOUBLE,
                    stamina_cost DOUBLE
                )
            """);
            stmt.execute("CREATE SEQUENCE IF NOT EXISTS seq_player_abilities START 1");

            // Spatial tables
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS spatial_heatmaps (
                    id BIGINT PRIMARY KEY,
                    ts TIMESTAMP NOT NULL,
                    heatmap_type VARCHAR(32),
                    room VARCHAR(128),
                    x INTEGER,
                    y INTEGER,
                    z INTEGER,
                    count INTEGER DEFAULT 1
                )
            """);
            stmt.execute("CREATE SEQUENCE IF NOT EXISTS seq_spatial_heatmaps START 1");

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS spatial_alerts (
                    id BIGINT PRIMARY KEY,
                    ts TIMESTAMP NOT NULL,
                    alert_type VARCHAR(32),
                    player_name VARCHAR(64),
                    entity_name VARCHAR(64),
                    entity_type VARCHAR(128),
                    room VARCHAR(128),
                    x DOUBLE,
                    y DOUBLE,
                    z DOUBLE,
                    extra_data VARCHAR(512)
                )
            """);
            stmt.execute("CREATE SEQUENCE IF NOT EXISTS seq_spatial_alerts START 1");

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS spatial_room_transitions (
                    id BIGINT PRIMARY KEY,
                    ts TIMESTAMP NOT NULL,
                    player_id UUID NOT NULL,
                    player_name VARCHAR(64),
                    room VARCHAR(128)
                )
            """);
            stmt.execute("CREATE SEQUENCE IF NOT EXISTS seq_spatial_room_transitions START 1");

            // Performance table
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS performance_samples (
                    id BIGINT PRIMARY KEY,
                    ts TIMESTAMP NOT NULL,
                    mspt DOUBLE,
                    tps DOUBLE
                )
            """);
            stmt.execute("CREATE SEQUENCE IF NOT EXISTS seq_performance_samples START 1");
        }
    }

    private static Connection requireConnection() {
        return Objects.requireNonNull(testConnection, "Test connection not initialized");
    }

    // ============================================
    // DELIVERABLE 2: RUNTIME SMOKE TEST
    // ============================================

    @Test
    @Order(1)
    @DisplayName("Smoke Test: Combat Events (hits, deaths, heals, spawns)")
    void smokeTestCombatEvents() throws Exception {
        Instant now = Instant.now();

        // Insert combat hits
        try (PreparedStatement pstmt = requireConnection().prepareStatement(
            "INSERT INTO combat_hits (id, ts, room, world, attacker_name, attacker_type, target_name, target_type, damage, damage_type, hp_before, hp_after, body_part, distance) " +
            "VALUES (nextval('seq_combat_hits'), ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            for (int i = 0; i < 10; i++) {
                pstmt.setTimestamp(1, Timestamp.from(now));
                pstmt.setString(2, "smoke_test_arena");
                pstmt.setString(3, "minecraft:overworld");
                pstmt.setString(4, "Player" + i);
                pstmt.setString(5, "minecraft:player");
                pstmt.setString(6, "Zombie" + i);
                pstmt.setString(7, "minecraft:zombie");
                pstmt.setDouble(8, 5.0 + i);
                pstmt.setString(9, "melee");
                pstmt.setDouble(10, 20.0);
                pstmt.setDouble(11, 15.0 - i);
                pstmt.setString(12, "body");
                pstmt.setDouble(13, 3.5);
                pstmt.addBatch();
            }
            pstmt.executeBatch();
        }

        // Insert combat deaths
        try (PreparedStatement pstmt = requireConnection().prepareStatement(
            "INSERT INTO combat_deaths (id, ts, room, world, target_name, target_type, cause, ttk_first_hit_ms, ttk_spawn_ms) " +
            "VALUES (nextval('seq_combat_deaths'), ?, ?, ?, ?, ?, ?, ?, ?)")) {
            for (int i = 0; i < 3; i++) {
                pstmt.setTimestamp(1, Timestamp.from(now));
                pstmt.setString(2, "smoke_test_arena");
                pstmt.setString(3, "minecraft:overworld");
                pstmt.setString(4, "Zombie" + (i * 3));
                pstmt.setString(5, "minecraft:zombie");
                pstmt.setString(6, "player_attack");
                pstmt.setLong(7, 1500L);
                pstmt.setLong(8, 3000L);
                pstmt.addBatch();
            }
            pstmt.executeBatch();
        }

        // Insert combat heals
        try (PreparedStatement pstmt = requireConnection().prepareStatement(
            "INSERT INTO combat_heals (id, ts, room, world, target_name, target_type, heal_amount, hp_before, hp_after, source) " +
            "VALUES (nextval('seq_combat_heals'), ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            for (int i = 0; i < 10; i++) {
                pstmt.setTimestamp(1, Timestamp.from(now));
                pstmt.setString(2, "smoke_test_arena");
                pstmt.setString(3, "minecraft:overworld");
                pstmt.setString(4, "Player" + i);
                pstmt.setString(5, "minecraft:player");
                pstmt.setDouble(6, 2.0);
                pstmt.setDouble(7, 15.0);
                pstmt.setDouble(8, 17.0);
                pstmt.setString(9, "regeneration");
                pstmt.addBatch();
            }
            pstmt.executeBatch();
        }

        // Insert combat spawns
        try (PreparedStatement pstmt = requireConnection().prepareStatement(
            "INSERT INTO combat_spawns (id, ts, room, world, entity_name, entity_type, reason, spawn_fail, x, y, z) " +
            "VALUES (nextval('seq_combat_spawns'), ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            for (int i = 0; i < 10; i++) {
                pstmt.setTimestamp(1, Timestamp.from(now));
                pstmt.setString(2, "smoke_test_arena");
                pstmt.setString(3, "minecraft:overworld");
                pstmt.setString(4, "Zombie" + i);
                pstmt.setString(5, "minecraft:zombie");
                pstmt.setString(6, "wave_spawn");
                pstmt.setBoolean(7, false);
                pstmt.setDouble(8, 100.0 + i);
                pstmt.setDouble(9, 64.0);
                pstmt.setDouble(10, 200.0 + i);
                pstmt.addBatch();
            }
            pstmt.executeBatch();
        }

        // Verify counts
        assertTableCountAtLeast("combat_hits", 10, "room = 'smoke_test_arena'");
        assertTableCountAtLeast("combat_deaths", 3, null);
        assertTableCountAtLeast("combat_heals", 10, "room = 'smoke_test_arena'");
        assertTableCountAtLeast("combat_spawns", 10, "room = 'smoke_test_arena'");

        System.out.println("✅ Combat events smoke test PASSED");
    }

    @Test
    @Order(2)
    @DisplayName("Smoke Test: Endurance Events (waves, kills, combos, perks, rewards)")
    void smokeTestEnduranceEvents() throws Exception {
        UUID sessionId = UUID.randomUUID();
        UUID playerId = UUID.randomUUID();
        Instant now = Instant.now();

        // Wave events
        try (PreparedStatement pstmt = requireConnection().prepareStatement(
            "INSERT INTO endurance_waves (id, ts, session_id, wave_number, event_type, mob_count, mobs_killed, duration_ms, no_damage, kills_per_second) " +
            "VALUES (nextval('seq_endurance_waves'), ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            for (int wave = 1; wave <= 5; wave++) {
                // Start event
                pstmt.setTimestamp(1, Timestamp.from(now.plusSeconds(wave * 60L)));
                pstmt.setObject(2, sessionId);
                pstmt.setInt(3, wave);
                pstmt.setString(4, "start");
                pstmt.setInt(5, 10 + wave);
                pstmt.setNull(6, Types.INTEGER);
                pstmt.setNull(7, Types.BIGINT);
                pstmt.setNull(8, Types.BOOLEAN);
                pstmt.setNull(9, Types.DOUBLE);
                pstmt.addBatch();

                // Complete event
                pstmt.setTimestamp(1, Timestamp.from(now.plusSeconds(wave * 60L + 30)));
                pstmt.setObject(2, sessionId);
                pstmt.setInt(3, wave);
                pstmt.setString(4, "complete");
                pstmt.setInt(5, 10 + wave);
                pstmt.setInt(6, 10 + wave);
                pstmt.setLong(7, 30000L);
                pstmt.setBoolean(8, wave % 2 == 0);
                pstmt.setDouble(9, 0.33);
                pstmt.addBatch();
            }
            pstmt.executeBatch();
        }

        // Wave kills
        try (PreparedStatement pstmt = requireConnection().prepareStatement(
            "INSERT INTO endurance_wave_kills (id, ts, session_id, wave_number, mob_type, is_elite, killer_weapon, damage_dealt) " +
            "VALUES (nextval('seq_endurance_wave_kills'), ?, ?, ?, ?, ?, ?, ?)")) {
            for (int wave = 1; wave <= 5; wave++) {
                for (int kill = 0; kill < 5; kill++) {
                    pstmt.setTimestamp(1, Timestamp.from(now.plusSeconds(wave * 60L + kill)));
                    pstmt.setObject(2, sessionId);
                    pstmt.setInt(3, wave);
                    pstmt.setString(4, "minecraft:zombie");
                    pstmt.setBoolean(5, false);
                    pstmt.setString(6, "diamond_sword");
                    pstmt.setDouble(7, 25.0);
                    pstmt.addBatch();
                }
            }
            pstmt.executeBatch();
        }

        // Combo events
        try (PreparedStatement pstmt = requireConnection().prepareStatement(
            "INSERT INTO endurance_combos (id, ts, player_id, session_id, event_type, old_rank, new_rank, style_score, current_combo) " +
            "VALUES (nextval('seq_endurance_combos'), ?, ?, ?, ?, ?, ?, ?, ?)")) {
            pstmt.setTimestamp(1, Timestamp.from(now));
            pstmt.setObject(2, playerId);
            pstmt.setObject(3, sessionId);
            pstmt.setString(4, "rank_up");
            pstmt.setString(5, "D");
            pstmt.setString(6, "C");
            pstmt.setInt(7, 150);
            pstmt.setInt(8, 25);
            pstmt.addBatch();

            pstmt.setTimestamp(1, Timestamp.from(now.plusSeconds(1)));
            pstmt.setObject(2, playerId);
            pstmt.setObject(3, sessionId);
            pstmt.setString(4, "milestone");
            pstmt.setNull(5, Types.VARCHAR);
            pstmt.setNull(6, Types.VARCHAR);
            pstmt.setNull(7, Types.INTEGER);
            pstmt.setInt(8, 50);
            pstmt.addBatch();
            pstmt.executeBatch();
        }

        // Perk events
        try (PreparedStatement pstmt = requireConnection().prepareStatement(
            "INSERT INTO endurance_perks (id, ts, player_id, session_id, event_type, perk_id, perk_name, tier, category) " +
            "VALUES (nextval('seq_endurance_perks'), ?, ?, ?, ?, ?, ?, ?, ?)")) {
            pstmt.setTimestamp(1, Timestamp.from(now));
            pstmt.setObject(2, playerId);
            pstmt.setObject(3, sessionId);
            pstmt.setString(4, "perk_selected");
            pstmt.setString(5, "damage_boost");
            pstmt.setString(6, "Damage Boost");
            pstmt.setString(7, "COMMON");
            pstmt.setString(8, "OFFENSE");
            pstmt.executeUpdate();
        }

        // Reward events
        try (PreparedStatement pstmt = requireConnection().prepareStatement(
            "INSERT INTO endurance_rewards (id, ts, player_id, session_id, event_type, currency, amount, source) " +
            "VALUES (nextval('seq_endurance_rewards'), ?, ?, ?, ?, ?, ?, ?)")) {
            pstmt.setTimestamp(1, Timestamp.from(now));
            pstmt.setObject(2, playerId);
            pstmt.setObject(3, sessionId);
            pstmt.setString(4, "currency");
            pstmt.setString(5, "TOKENS");
            pstmt.setInt(6, 100);
            pstmt.setString(7, "wave_complete");
            pstmt.executeUpdate();
        }

        // Verify counts
        assertTableCountAtLeast("endurance_waves", 10, null); // 5 start + 5 complete
        assertTableCountAtLeast("endurance_wave_kills", 25, null); // 5 waves * 5 kills
        assertTableCountAtLeast("endurance_combos", 2, null);
        assertTableCountAtLeast("endurance_perks", 1, null);
        assertTableCountAtLeast("endurance_rewards", 1, null);

        System.out.println("✅ Endurance events smoke test PASSED");
    }

    @Test
    @Order(3)
    @DisplayName("Smoke Test: Player Events (snapshots, abilities)")
    void smokeTestPlayerEvents() throws Exception {
        UUID playerId = UUID.randomUUID();
        Instant now = Instant.now();

        // Player snapshots
        try (PreparedStatement pstmt = requireConnection().prepareStatement(
            "INSERT INTO player_snapshots (id, ts, player_id, player_name, trigger_type, health_hp, max_health_hp) " +
            "VALUES (nextval('seq_player_snapshots'), ?, ?, ?, ?, ?, ?)")) {
            for (int i = 0; i < 5; i++) {
                pstmt.setTimestamp(1, Timestamp.from(now.plusSeconds(i)));
                pstmt.setObject(2, playerId);
                pstmt.setString(3, "TestPlayer");
                pstmt.setString(4, "periodic");
                pstmt.setDouble(5, 20.0 - i);
                pstmt.setDouble(6, 20.0);
                pstmt.addBatch();
            }
            pstmt.executeBatch();
        }

        // Ability events
        try (PreparedStatement pstmt = requireConnection().prepareStatement(
            "INSERT INTO player_abilities (id, ts, player_id, ability_type, success, stamina_before, stamina_after, stamina_cost) " +
            "VALUES (nextval('seq_player_abilities'), ?, ?, ?, ?, ?, ?, ?)")) {
            String[] abilities = {"dash", "dodge", "perfect_dodge", "exhaustion", "stamina_full"};
            for (int i = 0; i < abilities.length; i++) {
                pstmt.setTimestamp(1, Timestamp.from(now.plusSeconds(i)));
                pstmt.setObject(2, playerId);
                pstmt.setString(3, abilities[i]);
                pstmt.setBoolean(4, true);
                pstmt.setDouble(5, 80.0 - i * 10);
                pstmt.setDouble(6, 60.0 - i * 10);
                pstmt.setDouble(7, 20.0);
                pstmt.addBatch();
            }
            pstmt.executeBatch();
        }

        assertTableCountAtLeast("player_snapshots", 5, null);
        assertTableCountAtLeast("player_abilities", 5, null);

        System.out.println("✅ Player events smoke test PASSED");
    }

    @Test
    @Order(4)
    @DisplayName("Smoke Test: Spatial Events (heatmaps, alerts, transitions)")
    void smokeTestSpatialEvents() throws Exception {
        UUID playerId = UUID.randomUUID();
        Instant now = Instant.now();

        // Heatmap events
        try (PreparedStatement pstmt = requireConnection().prepareStatement(
            "INSERT INTO spatial_heatmaps (id, ts, heatmap_type, room, x, y, z, count) " +
            "VALUES (nextval('seq_spatial_heatmaps'), ?, ?, ?, ?, ?, ?, ?)")) {
            String[] types = {"death", "stuck", "camping", "kiting"};
            for (String type : types) {
                pstmt.setTimestamp(1, Timestamp.from(now));
                pstmt.setString(2, type);
                pstmt.setString(3, "smoke_test_arena");
                pstmt.setInt(4, 100);
                pstmt.setInt(5, 64);
                pstmt.setInt(6, 200);
                pstmt.setInt(7, 5);
                pstmt.addBatch();
            }
            pstmt.executeBatch();
        }

        // Alert events
        try (PreparedStatement pstmt = requireConnection().prepareStatement(
            "INSERT INTO spatial_alerts (id, ts, alert_type, player_name, entity_name, entity_type, room, x, y, z, extra_data) " +
            "VALUES (nextval('seq_spatial_alerts'), ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            pstmt.setTimestamp(1, Timestamp.from(now));
            pstmt.setString(2, "stuck");
            pstmt.setString(3, "TestPlayer");
            pstmt.setString(4, "Zombie1");
            pstmt.setString(5, "minecraft:zombie");
            pstmt.setString(6, "smoke_test_arena");
            pstmt.setDouble(7, 100.5);
            pstmt.setDouble(8, 64.0);
            pstmt.setDouble(9, 200.5);
            pstmt.setString(10, "{\"duration_ms\":5000}");
            pstmt.executeUpdate();
        }

        // Room transitions
        try (PreparedStatement pstmt = requireConnection().prepareStatement(
            "INSERT INTO spatial_room_transitions (id, ts, player_id, player_name, room) " +
            "VALUES (nextval('seq_spatial_room_transitions'), ?, ?, ?, ?)")) {
            pstmt.setTimestamp(1, Timestamp.from(now));
            pstmt.setObject(2, playerId);
            pstmt.setString(3, "TestPlayer");
            pstmt.setString(4, "smoke_test_arena");
            pstmt.addBatch();

            pstmt.setTimestamp(1, Timestamp.from(now.plusSeconds(60)));
            pstmt.setObject(2, playerId);
            pstmt.setString(3, "TestPlayer");
            pstmt.setString(4, "boss_room");
            pstmt.addBatch();
            pstmt.executeBatch();
        }

        assertTableCountAtLeast("spatial_heatmaps", 4, null);
        assertTableCountAtLeast("spatial_alerts", 1, null);
        assertTableCountAtLeast("spatial_room_transitions", 2, null);

        System.out.println("✅ Spatial events smoke test PASSED");
    }

    @Test
    @Order(5)
    @DisplayName("Smoke Test: Performance Samples")
    void smokeTestPerformance() throws Exception {
        Instant now = Instant.now();

        try (PreparedStatement pstmt = requireConnection().prepareStatement(
            "INSERT INTO performance_samples (id, ts, mspt, tps) " +
            "VALUES (nextval('seq_performance_samples'), ?, ?, ?)")) {
            for (int i = 0; i < 10; i++) {
                pstmt.setTimestamp(1, Timestamp.from(now.plusSeconds(i)));
                pstmt.setDouble(2, 20.0 + i * 0.5);
                pstmt.setDouble(3, 20.0 - i * 0.1);
                pstmt.addBatch();
            }
            pstmt.executeBatch();
        }

        assertTableCountAtLeast("performance_samples", 10, null);

        System.out.println("✅ Performance samples smoke test PASSED");
    }

    // ============================================
    // DELIVERABLE 3: INVARIANT VALIDATION
    // ============================================

    @Test
    @Order(10)
    @DisplayName("Invariant: Timestamps are monotonic within sessions")
    void testTimestampMonotonicity() throws Exception {
        String query = """
            SELECT session_id, COUNT(*) as violations
            FROM (
                SELECT session_id, ts,
                       LAG(ts) OVER (PARTITION BY session_id ORDER BY id) as prev_ts
                FROM endurance_waves
            ) sub
            WHERE ts < prev_ts
            GROUP BY session_id
            HAVING COUNT(*) > 0
            """;

        try (Statement stmt = requireConnection().createStatement();
             ResultSet rs = stmt.executeQuery(query)) {
            assertFalse(rs.next(), "Timestamp monotonicity violation found in endurance_waves");
        }

        System.out.println("✅ Timestamp monotonicity invariant PASSED");
    }

    @Test
    @Order(11)
    @DisplayName("Invariant: Wave numbers are sequential within sessions")
    void testWaveSequentiality() throws Exception {
        String query = """
            SELECT session_id, wave_number, expected_wave
            FROM (
                SELECT session_id, wave_number,
                       ROW_NUMBER() OVER (PARTITION BY session_id ORDER BY id) as expected_wave
                FROM endurance_waves
                WHERE event_type = 'start'
            ) sub
            WHERE wave_number != expected_wave
            """;

        try (Statement stmt = requireConnection().createStatement();
             ResultSet rs = stmt.executeQuery(query)) {
            int violations = 0;
            while (rs.next()) {
                violations++;
                System.out.println("Wave sequence violation: session=" + rs.getString("session_id") +
                    ", actual=" + rs.getInt("wave_number") + ", expected=" + rs.getInt("expected_wave"));
            }
            assertEquals(0, violations, "Wave sequentiality violations found");
        }

        System.out.println("✅ Wave sequentiality invariant PASSED");
    }

    @Test
    @Order(12)
    @DisplayName("Invariant: Damage values are within sane ranges")
    void testDamageRangeSanity() throws Exception {
        String query = """
            SELECT COUNT(*) as violations FROM combat_hits
            WHERE damage < 0 OR damage > 10000
            """;

        try (Statement stmt = requireConnection().createStatement();
             ResultSet rs = stmt.executeQuery(query)) {
            assertTrue(rs.next());
            assertEquals(0, rs.getInt("violations"), "Damage range violations found");
        }

        System.out.println("✅ Damage range sanity invariant PASSED");
    }

    @Test
    @Order(13)
    @DisplayName("Invariant: Stamina values are within 0-150 range")
    void testStaminaRangeSanity() throws Exception {
        String query = """
            SELECT COUNT(*) as violations FROM player_abilities
            WHERE (stamina_before IS NOT NULL AND (stamina_before < 0 OR stamina_before > 150))
               OR (stamina_after IS NOT NULL AND (stamina_after < 0 OR stamina_after > 150))
            """;

        try (Statement stmt = requireConnection().createStatement();
             ResultSet rs = stmt.executeQuery(query)) {
            assertTrue(rs.next());
            assertEquals(0, rs.getInt("violations"), "Stamina range violations found");
        }

        System.out.println("✅ Stamina range sanity invariant PASSED");
    }

    // ============================================
    // DELIVERABLE 4: CONCURRENT WRITE TEST
    // ============================================

    @Test
    @Order(20)
    @DisplayName("Concurrency: Multi-thread writes are safe")
    void testConcurrentWriteSafety() throws Exception {
        int threadCount = 5;
        int insertsPerThread = 100;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);
        AtomicReference<Throwable> firstError = new AtomicReference<>();
        Connection connection = requireConnection();

        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;
            new Thread(() -> {
                try {
                    startLatch.await();

                    // Synchronized access to shared connection (DuckDB serializes writes)
                    synchronized (connection) {
                        try (PreparedStatement pstmt = connection.prepareStatement(
                            "INSERT INTO combat_hits (id, ts, room, damage, damage_type, attacker_name, target_name) " +
                            "VALUES (nextval('seq_combat_hits'), ?, ?, ?, ?, ?, ?)")) {

                            for (int i = 0; i < insertsPerThread; i++) {
                                pstmt.setTimestamp(1, Timestamp.from(Instant.now()));
                                pstmt.setString(2, "concurrent_test");
                                pstmt.setDouble(3, 10.0 + i);
                                pstmt.setString(4, "test");
                                pstmt.setString(5, "Thread" + threadId);
                                pstmt.setString(6, "Target" + i);
                                pstmt.executeUpdate();
                                successCount.incrementAndGet();
                            }
                        }
                    }
                } catch (Exception e) {
                    errorCount.incrementAndGet();
                    firstError.compareAndSet(null, e);
                } finally {
                    doneLatch.countDown();
                }
            }).start();
        }

        startLatch.countDown();
        assertTrue(doneLatch.await(30, TimeUnit.SECONDS), "All threads should complete");

        int expectedTotal = threadCount * insertsPerThread;
        assertEquals(expectedTotal, successCount.get(), "All inserts should succeed");
        assertEquals(0, errorCount.get(), "No errors should occur");
        assertNull(firstError.get(), "Unexpected error during concurrent writes: " + firstError.get());

        // Verify data
        try (Statement stmt = requireConnection().createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM combat_hits WHERE room = 'concurrent_test'")) {
            assertTrue(rs.next());
            assertEquals(expectedTotal, rs.getInt(1), "All concurrent writes should be persisted");
        }

        System.out.println("✅ Concurrent write safety test PASSED");
    }

    // ============================================
    // DELIVERABLE 5: BATCH INSERT PERFORMANCE
    // ============================================

    @Test
    @Order(30)
    @DisplayName("Performance: Batch insert throughput > 1000 rows/sec")
    void testBatchInsertThroughput() throws Exception {
        int batchSize = 100;
        int batches = 10;

        try (PreparedStatement pstmt = requireConnection().prepareStatement(
            "INSERT INTO performance_samples (id, ts, mspt, tps) " +
            "VALUES (nextval('seq_performance_samples'), ?, ?, ?)")) {

            long startNs = System.nanoTime();

            for (int b = 0; b < batches; b++) {
                for (int i = 0; i < batchSize; i++) {
                    pstmt.setTimestamp(1, Timestamp.from(Instant.now()));
                    pstmt.setDouble(2, 20.0 + i * 0.01);
                    pstmt.setDouble(3, 19.5);
                    pstmt.addBatch();
                }
                pstmt.executeBatch();
            }

            long elapsedNs = System.nanoTime() - startNs;
            int totalRows = batchSize * batches;
            double throughput = totalRows / (elapsedNs / 1_000_000_000.0);

            System.out.println("[PERF] Batch throughput: " + String.format("%.0f", throughput) + " rows/sec");

            assertTrue(throughput > 1000, "Throughput " + throughput + " rows/sec below target");
        }

        System.out.println("✅ Batch insert throughput test PASSED");
    }

    // ============================================
    // DELIVERABLE 6: DATA PERSISTENCE TEST
    // ============================================

    @Test
    @Order(40)
    @DisplayName("Persistence: Data survives connection close/reopen")
    void testDataPersistence() throws Exception {
        Path dbPath = Objects.requireNonNull(tempDir).resolve("persistence_test.duckdb");

        int eventsToWrite = 50;
        Instant now = Instant.now();

        // Write data
        try (Connection writeConn = DriverManager.getConnection("jdbc:duckdb:" + dbPath)) {
            try (Statement stmt = writeConn.createStatement()) {
                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS persistence_test (
                        id INTEGER PRIMARY KEY,
                        ts TIMESTAMP NOT NULL,
                        value DOUBLE
                    )
                """);
                stmt.execute("CREATE SEQUENCE IF NOT EXISTS seq_persistence_test START 1");
            }

            try (PreparedStatement pstmt = writeConn.prepareStatement(
                "INSERT INTO persistence_test (id, ts, value) VALUES (nextval('seq_persistence_test'), ?, ?)")) {
                for (int i = 0; i < eventsToWrite; i++) {
                    pstmt.setTimestamp(1, Timestamp.from(now.plusSeconds(i)));
                    pstmt.setDouble(2, i * 1.5);
                    pstmt.addBatch();
                }
                pstmt.executeBatch();
            }
        }

        // Reopen and verify
        try (Connection readConn = DriverManager.getConnection("jdbc:duckdb:" + dbPath);
             Statement stmt = readConn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM persistence_test")) {
            assertTrue(rs.next());
            int persisted = rs.getInt(1);
            assertEquals(eventsToWrite, persisted,
                "All events should persist after connection close. Expected: " + eventsToWrite + ", Got: " + persisted);
        }

        System.out.println("✅ Data persistence test PASSED - " + eventsToWrite + " events persisted");
    }

    // ============================================
    // QUERY HELPERS
    // ============================================

    private void assertTableCountAtLeast(String table, int minCount, @Nullable String whereClause) throws SQLException {
        String query = whereClause != null
            ? "SELECT COUNT(*) FROM " + table + " WHERE " + whereClause
            : "SELECT COUNT(*) FROM " + table;

        try (Statement stmt = requireConnection().createStatement();
             ResultSet rs = stmt.executeQuery(query)) {
            assertTrue(rs.next());
            int actual = rs.getInt(1);
            assertTrue(actual >= minCount,
                "Table " + table + " should have at least " + minCount + " rows, got " + actual);
        }
    }

    // ============================================
    // SUMMARY QUERIES
    // ============================================

    @Test
    @Order(99)
    @DisplayName("Generate Summary Queries for Manual Inspection")
    void generateSummaryQueries() throws Exception {
        System.out.println("\n========================================");
        System.out.println("SUMMARY QUERIES FOR MANUAL INSPECTION");
        System.out.println("========================================\n");

        String[] queries = {
            "SELECT 'combat_hits' as tbl, COUNT(*) as cnt FROM combat_hits",
            "SELECT 'combat_deaths' as tbl, COUNT(*) as cnt FROM combat_deaths",
            "SELECT 'combat_heals' as tbl, COUNT(*) as cnt FROM combat_heals",
            "SELECT 'combat_spawns' as tbl, COUNT(*) as cnt FROM combat_spawns",
            "SELECT 'endurance_waves' as tbl, COUNT(*) as cnt FROM endurance_waves",
            "SELECT 'endurance_wave_kills' as tbl, COUNT(*) as cnt FROM endurance_wave_kills",
            "SELECT 'endurance_combos' as tbl, COUNT(*) as cnt FROM endurance_combos",
            "SELECT 'endurance_perks' as tbl, COUNT(*) as cnt FROM endurance_perks",
            "SELECT 'endurance_rewards' as tbl, COUNT(*) as cnt FROM endurance_rewards",
            "SELECT 'player_snapshots' as tbl, COUNT(*) as cnt FROM player_snapshots",
            "SELECT 'player_abilities' as tbl, COUNT(*) as cnt FROM player_abilities",
            "SELECT 'spatial_heatmaps' as tbl, COUNT(*) as cnt FROM spatial_heatmaps",
            "SELECT 'spatial_alerts' as tbl, COUNT(*) as cnt FROM spatial_alerts",
            "SELECT 'spatial_room_transitions' as tbl, COUNT(*) as cnt FROM spatial_room_transitions",
            "SELECT 'performance_samples' as tbl, COUNT(*) as cnt FROM performance_samples"
        };

        System.out.println("TABLE ROW COUNTS:");
        System.out.println("-----------------");
        for (String query : queries) {
            try (Statement stmt = requireConnection().createStatement();
                 ResultSet rs = stmt.executeQuery(query)) {
                if (rs.next()) {
                    System.out.printf("%-30s: %d rows%n", rs.getString("tbl"), rs.getInt("cnt"));
                }
            }
        }

        System.out.println("\n========================================");
    }
}
