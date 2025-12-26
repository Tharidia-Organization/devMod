package com.devmod.telemetry;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import com.devmod.telemetry.boss.BossPhaseService;
import com.devmod.telemetry.combat.FightSessionService;
import com.devmod.telemetry.damage.DamageTrackingService;
import com.devmod.telemetry.duckdb.DuckDBConfig;
import com.devmod.telemetry.duckdb.DuckDBTelemetryService;
import com.devmod.telemetry.dungeon.DungeonSessionService;
import com.devmod.telemetry.entity.EntityTrackingService;
import com.devmod.telemetry.entity.MinionService;
import com.devmod.telemetry.player.PlayerTrackingService;
import com.devmod.telemetry.room.RoomService;
import com.devmod.telemetry.skills.SkillTrackingService;
import com.devmod.telemetry.spatial.HeatmapService;
import com.devmod.telemetry.spatial.LightAnalysisService;
import com.devmod.telemetry.spatial.SpatialMetricsService;

import com.mojang.logging.LogUtils;

/**
 * Central telemetry logger. Keeps light in-memory aggregates and appends NDJSON lines to disk.
 */
// Minecraft API (getGameProfile) guaranteed non-null for ServerPlayer

public class TelemetryService {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final TelemetryService INSTANCE = new TelemetryService();

    // Room definitions delegated to RoomService
    private Path telemetryDir;
    private AsyncTelemetryWriter asyncWriter;

    // weaponAggregates, roomAggregates, activeFights -> delegated to services
    // playerRooms, lastMovementSample, oobTrackers, backtrackTrackers -> delegated to PlayerTrackingService
    private final AtomicInteger tickCounter = new AtomicInteger(0);
    private TelemetrySettings settings = TelemetrySettings.defaults();

    /**
     * Flag to track if DuckDB was requested but failed to initialize.
     * Used for circuit breaker logic when FALLBACK_ON_ERROR=false.
     */
    private volatile boolean duckDbInitFailed = false;

    // Delegate for log handlers (logHit, logMiss, logDeath, etc.)
    private TelemetryLogHandlers logHandlers;

    // Recording state for UI
    private volatile boolean recording = true;
    private volatile String currentRoom = "";
    // Delegated to SkillTrackingService
    // Delegated to BossPhaseService

    // Minion wave tracking delegated to MinionService
    // FASE 5: Advanced Metrics delegated to DungeonSessionService and SpatialMetricsService

    private TelemetryService() {}

    public TelemetrySettings getSettings() {
        return settings;
    }

    /**
     * Check if telemetry is currently recording.
     */
    public boolean isRecording() {
        return recording;
    }

    /**
     * Pause telemetry recording.
     */
    public void pauseRecording() {
        recording = false;
        LOGGER.debug("Telemetry recording paused");
    }

    /**
     * Resume telemetry recording.
     */
    public void resumeRecording() {
        recording = true;
        LOGGER.debug("Telemetry recording resumed");
    }

    /**
     * Get the current room the first tracked player is in.
     */
    public String getCurrentRoom() {
        return currentRoom;
    }

    /**
     * Update current room (called from trackPlayerRoom).
     */
    private void updateCurrentRoom(String roomId) {
        this.currentRoom = roomId;
    }

    /**
     * FASE 4 REQ-A3: Returns the list of configured room definitions for visualization.
     * Delegates to RoomService.
     */
    public List<RoomDefinition> getRoomDefinitions() {
        return RoomService.INSTANCE.getRoomDefinitions();
    }

    public void reload(MinecraftServer server) {
        // Shutdown old writer if exists
        if (asyncWriter != null) {
            asyncWriter.shutdown();
        }

        this.telemetryDir = server.getServerDirectory().resolve("telemetry");
        try {
            Files.createDirectories(telemetryDir);
        } catch (IOException e) {
            LOGGER.error("Unable to create telemetry directory", e);
        }

        // PERFORMANCE FIX: Initialize async writer for non-blocking telemetry I/O
        this.asyncWriter = new AsyncTelemetryWriter();

        // Initialize log handlers delegate
        this.logHandlers = new TelemetryLogHandlers(this);

        // Delegate room loading to RoomService
        RoomService.INSTANCE.loadRooms(new TelemetryConfig());
        this.settings = TelemetrySettings.load();

        // Configure sub-services with settings
        EntityTrackingService.INSTANCE.setStuckThresholdMs(settings.stuckMs());
        EntityTrackingService.INSTANCE.setAggroDropThresholdMs(settings.aggroDropMs());
        EntityTrackingService.INSTANCE.setCampingHitsThreshold(settings.campingHits());
        EntityTrackingService.INSTANCE.setCampingTimeThresholdMs(settings.campingMs());
        EntityTrackingService.INSTANCE.setBossHpThreshold(settings.bossHpThreshold());

        // Initialize DamageTrackingService with telemetry directory for persistence
        DamageTrackingService.INSTANCE.initialize(telemetryDir);

        // Initialize DuckDB telemetry storage (PRIMARY)
        if (DuckDBConfig.ENABLED) {
            boolean duckDbInitialized = DuckDBTelemetryService.INSTANCE.initialize(server);
            if (duckDbInitialized) {
                LOGGER.info("DuckDB telemetry initialized (NDJSON fallback: {})", DuckDBConfig.NDJSON_FALLBACK);
                duckDbInitFailed = false;
            } else {
                duckDbInitFailed = true;
                if (DuckDBConfig.FALLBACK_ON_ERROR) {
                    LOGGER.warn("DuckDB initialization failed, falling back to NDJSON only");
                } else {
                    LOGGER.error("DuckDB initialization failed and FALLBACK_ON_ERROR=false - TELEMETRY DISABLED");
                }
            }
        } else {
            LOGGER.info("DuckDB disabled, using NDJSON only (legacy mode)");
        }

        LOGGER.info("Telemetry rooms loaded: {}, Boss HP threshold: {}, Phase detection: {}, Skill tracking: {}",
                RoomService.INSTANCE.getRoomCount(), settings.bossHpThreshold(), settings.bossPhaseDetectionEnabled(), settings.skillTrackingEnabled());
    }

    /**
     * Shutdown async writer gracefully (flushes pending writes)
     * Should be called on server shutdown
     */
    public void shutdown() {
        // Save damage aggregates before shutdown
        DamageTrackingService.INSTANCE.saveAggregates();

        // Shutdown DuckDB (flushes pending batch writes)
        if (DuckDBTelemetryService.INSTANCE.isInitialized()) {
            DuckDBTelemetryService.INSTANCE.shutdown();
        }

        if (asyncWriter != null) {
            asyncWriter.shutdown();
            asyncWriter = null;
        }
    }

    public void trackPlayerRoom(ServerPlayer player) {
        String roomId = resolveRoom(player.serverLevel(), player.blockPosition());

        // Update current room for UI display
        updateCurrentRoom(roomId);

        // Delegate to PlayerTrackingService
        var result = PlayerTrackingService.INSTANCE.trackPlayerRoom(player, roomId);

        if (result.roomChanged()) {
            appendLine("room_time.ndjson", "{\"ts\":\"" + Instant.now() + "\",\"player\":\"" + TelemetryJson.escape(player.getGameProfile().getName()) + "\",\"room\":\"" + TelemetryJson.escape(roomId) + "\"}");

            // DuckDB: log room transition
            DuckDBTelemetryService.INSTANCE.logRoomTransition(
                player.getUUID(),
                player.getGameProfile().getName(),
                roomId);
        }

        // FASE 3 M10: Sample player movement for heatmap (with throttle)
        if (result.shouldSampleMovement()) {
            HeatmapService.INSTANCE.recordMovement(roomId, player.blockPosition(), player.getStringUUID());
        }
    }

    public void checkOutOfBounds(ServerPlayer player) {
        // Delegate to PlayerTrackingService
        var result = PlayerTrackingService.INSTANCE.checkOutOfBounds(player);

        if (result.shouldAlert()) {
            String playerName = player.getGameProfile().getName();
            Vec3 pos = result.position();

            String line = "{\"ts\":\"" + Instant.now() + "\","
                    + "\"type\":\"out_of_bounds_vertical\","
                    + "\"player\":\"" + TelemetryJson.escape(playerName) + "\","
                    + "\"room\":\"" + TelemetryJson.escape(result.roomId()) + "\","
                    + "\"pos\":[" + pos.x + "," + pos.y + "," + pos.z + "],"
                    + "\"deltaY\":" + result.deltaY() + "}";
            appendLine("alerts.ndjson", line);

            // DuckDB: log out_of_bounds alert
            DuckDBTelemetryService.INSTANCE.logAlert(
                "out_of_bounds", playerName, null, null, result.roomId(),
                pos.x, pos.y, pos.z,
                "{\"deltaY\":" + result.deltaY() + "}");
        }
    }

    /**
     * Resolve a position to a room ID. Delegates to RoomService.
     */
    public String resolveRoom(ServerLevel level, BlockPos pos) {
        return RoomService.INSTANCE.resolveRoom(level, pos);
    }

    public void logHit(Level level, Entity attacker, LivingEntity target, DamageSource source,
                       double amount, double hpBefore, double hpAfter, String bodyPart,
                       double distance, double armorPenBonus) {
        if (logHandlers != null) {
            logHandlers.logHit(level, attacker, target, source, amount, hpBefore, hpAfter,
                              bodyPart, distance, armorPenBonus);
        }
    }

    public void logMiss(Level level, Entity attacker, Vec3 impactPos, String impactType) {
        if (logHandlers != null) {
            logHandlers.logMiss(level, attacker, impactPos, impactType);
        }
    }

    public void logSkillCast(LivingEntity caster, String skillId) {
        if (logHandlers != null) {
            logHandlers.logSkillCast(caster, skillId);
        }
    }

    public void logSkillHit(LivingEntity caster, String skillId) {
        if (logHandlers != null) {
            logHandlers.logSkillHit(caster, skillId);
        }
    }

    public void logBossPhaseStart(ServerLevel level, LivingEntity boss, String phase) {
        if (logHandlers != null) {
            logHandlers.logBossPhaseStart(level, boss, phase);
        }
    }

    public void logBossPhaseEnd(LivingEntity boss) {
        if (logHandlers != null) {
            logHandlers.logBossPhaseEnd(boss);
        }
    }

    public void logDeath(Level level, LivingEntity entity, DamageSource source) {
        if (logHandlers != null) {
            logHandlers.logDeath(level, entity, source);
        }
    }

    public void logHeal(Level level, LivingEntity entity, double amount, String source) {
        if (logHandlers != null) {
            logHandlers.logHeal(level, entity, amount, source);
        }
    }

    public void logSpawn(ServerLevel level, LivingEntity entity, String reason) {
        if (logHandlers != null) {
            logHandlers.logSpawn(level, entity, reason);
        }
    }

    public void appendProjectile(String line) {
        appendLine("projectiles.ndjson", line);
    }

    public void appendDungeonRun(String line) {
        appendLine("dungeon_runs.ndjson", line);
    }

    public void appendBacktrack(String line) {
        appendLine("backtracks.ndjson", line);
    }

    /**
     * Called every server tick to close inactive fights. Delegates to FightSessionService.
     */
    public void tickFights() {
        FightSessionService.INSTANCE.tick(result -> {
            // DuckDB: PRIMARY storage
            if (DuckDBTelemetryService.INSTANCE.isEnabled()) {
                DuckDBTelemetryService.INSTANCE.logFight(
                    result.room(),
                    result.worldId(),
                    result.templateId(),
                    result.templateVersion(),
                    result.policyId(),
                    result.policyVersion(),
                    result.arenaId(),
                    result.sessionId(),
                    result.startInstant(),
                    result.endInstant(),
                    result.durationMs(),
                    result.hits(),
                    result.mobKills(),
                    result.playerDeaths(),
                    result.players().toArray(String[]::new),
                    mapToJson(result.mobKillsByType()),
                    mapToJson(result.playerDeathsByName()),
                    ttkMapToJson(result.ttkByType()),
                    result.maxBurst(),
                    result.avgHpAfterPlayers(),
                    result.avgHpAfterMobs()
                );
            }

            // NDJSON: Fallback only
            if (DuckDBConfig.NDJSON_FALLBACK || !DuckDBTelemetryService.INSTANCE.isEnabled()) {
                appendLine("fights.ndjson", result.toJson());
            }
        });
    }

    /** Helper: Convert Map<String, Integer> to JSON string */
    private String mapToJson(Map<String, Integer> map) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Integer> e : map.entrySet()) {
            if (!first) sb.append(",");
            first = false;
            sb.append("\"").append(TelemetryJson.escape(e.getKey())).append("\":").append(e.getValue());
        }
        sb.append("}");
        return sb.toString();
    }

    /** Helper: Convert TTK map to JSON string */
    private String ttkMapToJson(Map<String, FightSessionService.TTKAggregate> map) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, FightSessionService.TTKAggregate> e : map.entrySet()) {
            if (!first) sb.append(",");
            first = false;
            FightSessionService.TTKAggregate ttk = e.getValue();
            sb.append("\"").append(TelemetryJson.escape(e.getKey())).append("\":{");
            sb.append("\"avg\":").append(ttk.avgMs()).append(",");
            sb.append("\"max\":").append(ttk.maxMs()).append(",");
            sb.append("\"count\":").append(ttk.count()).append("}");
        }
        sb.append("}");
        return sb.toString();
    }

    /**
     * Sample performance (tick time) every 20 ticks (~1s) and write to performance.ndjson.
     */
    public void tickPerformance(MinecraftServer server) {
        int tick = tickCounter.incrementAndGet();
        if (tick % 20 != 0) return;
        double averageMspt = server.getAverageTickTimeNanos() / 1_000_000.0;
        double tps = averageMspt > 0 ? Math.min(1000.0 / averageMspt, 20.0) : 20.0;
        String line = "{\"ts\":\"" + Instant.now() + "\","
                + "\"mspt\":" + averageMspt + ","
                + "\"tps\":" + tps + "}";

        // DuckDB PRIMARY, NDJSON fallback
        if (DuckDBTelemetryService.INSTANCE.isEnabled()) {
            DuckDBTelemetryService.INSTANCE.logPerformance(averageMspt, tps);
        }
        if (DuckDBConfig.NDJSON_FALLBACK || !DuckDBTelemetryService.INSTANCE.isEnabled()) {
            appendLine("performance.ndjson", line);
        }
    }

    public List<String> getWeaponSummaries() {
        // Delegate to DamageTrackingService
        return DamageTrackingService.INSTANCE.getWeaponSummaries();
    }

    public List<String> getRoomSummaries() {
        // Delegate to DamageTrackingService
        return DamageTrackingService.INSTANCE.getRoomSummaries();
    }

    public List<String> getFightSummaries() {
        // Delegate to FightSessionService
        List<String> lines = new java.util.ArrayList<>();
        for (FightSessionService.FightSessionSummary summary : FightSessionService.INSTANCE.getActiveFightSummaries()) {
            lines.add(summary.toString());
        }
        return lines;
    }

    // ===== FASE 2: Heatmap Export Methods =====

    /**
     * Export stuck heatmap to NDJSON file for visualization.
     * Format: {"room": "...", "x": N, "y": N, "z": N, "count": N}
     */
    public void exportStuckHeatmap() {
        HeatmapService.INSTANCE.exportStuckHeatmap((room, line) -> appendLine("stuck_heatmap.ndjson", line));
    }

    /**
     * Export aggro drop heatmap to NDJSON file for visualization.
     */
    public void exportAggroDropHeatmap() {
        HeatmapService.INSTANCE.exportAggroDropHeatmap((room, line) -> appendLine("aggro_drop_heatmap.ndjson", line));
    }

    /**
     * Export kiting/spin heatmap to NDJSON file for visualization.
     */
    public void exportKitingHeatmap() {
        HeatmapService.INSTANCE.exportKitingHeatmap((room, line) -> appendLine("kiting_heatmap.ndjson", line));
    }

    /**
     * Get minion wave statistics for a room. Delegated to MinionService.
     */
    public String getMinionWaveStats(String room) {
        return MinionService.INSTANCE.getStats(room);
    }

    /**
     * Get all rooms with minion wave data. Delegated to MinionService.
     */
    public List<String> getAllMinionWaveStats() {
        return MinionService.INSTANCE.getAllStats();
    }

    // ===== FASE 3: Geometry and Navigation Heatmap Export Methods =====

    /**
     * Export death heatmap to NDJSON file for visualization.
     * Format: {"room": "...", "x": N, "y": N, "z": N, "count": N}
     */
    public void exportDeathHeatmap() {
        HeatmapService.INSTANCE.exportDeathHeatmap((room, line) -> appendLine("death_heatmap.ndjson", line));
    }

    /**
     * Export movement heatmap to NDJSON file for visualization.
     * Shows where players spend most time (sampled every 2 seconds).
     */
    public void exportMovementHeatmap() {
        HeatmapService.INSTANCE.exportMovementHeatmap((room, line) -> appendLine("movement_heatmap.ndjson", line));
    }

    /**
     * Export camping/safe spot heatmap to NDJSON file for visualization.
     * Shows positions where players stay and attack from (safe spots).
     */
    public void exportCampingHeatmap() {
        HeatmapService.INSTANCE.exportCampingHeatmap((room, line) -> appendLine("camping_heatmap.ndjson", line));
    }

    // ===== FASE 3 M27: Light Level & Spawnability Analysis =====
    // Delegated to LightAnalysisService

    /**
     * Scan a room and export light level + mob spawnability data.
     * Delegates to LightAnalysisService.
     */
    public void scanRoomLightLevels(ServerLevel level, String roomId) {
        LightAnalysisService.INSTANCE.scanRoomLightLevels(level, roomId,
            (fileName, line) -> appendLine(fileName, line));
    }

    /**
     * Scan all configured rooms for light levels.
     * Delegates to LightAnalysisService.
     */
    public void scanAllRoomsLightLevels(ServerLevel level) {
        LightAnalysisService.INSTANCE.scanAllRoomsLightLevels(level,
            (fileName, line) -> appendLine(fileName, line));
    }

    /**
     * Get spawnable spots count for a specific room (quick check without full export).
     * Delegates to LightAnalysisService.
     */
    public LightAnalysisService.SpawnabilityReport getSpawnabilityReport(ServerLevel level, String roomId) {
        return LightAnalysisService.INSTANCE.getSpawnabilityReport(level, roomId);
    }

    void checkStuck(LivingEntity entity) {
        if (entity.level().isClientSide()) return;

        // Delegate to EntityTrackingService
        boolean isStuck = EntityTrackingService.INSTANCE.checkStuck(entity);

        if (isStuck) {
            ResourceKey<Level> dim = entity.level().dimension();
            String entityName = entity.getName().getString();
            String entityType = entity.getType().toShortString();
            double x = entity.getX();
            double y = entity.getY();
            double z = entity.getZ();

            String line = "{\"ts\":\"" + Instant.now() + "\","
                    + "\"type\":\"stuck\","
                    + "\"entity\":\"" + TelemetryJson.escape(entityName) + "\","
                    + "\"entityType\":\"" + TelemetryJson.escape(entityType) + "\","
                    + "\"pos\":[" + x + "," + y + "," + z + "],"
                    + "\"world\":\"" + dim.location() + "\"}";
            appendLine("alerts.ndjson", line);

            // Delegate heatmap to HeatmapService
            if (entity.level() instanceof ServerLevel serverLevel) {
                String room = resolveRoom(serverLevel, entity.blockPosition());
                HeatmapService.INSTANCE.recordStuck(room, entity.blockPosition());

                // DuckDB: log stuck alert
                DuckDBTelemetryService.INSTANCE.logAlert(
                    "stuck", null, entityName, entityType, room, x, y, z,
                    "{\"world\":\"" + dim.location() + "\"}");
            }
        }
    }

    void checkCamping(ServerPlayer player, LivingEntity target) {
        // Delegate to EntityTrackingService
        boolean isCamping = EntityTrackingService.INSTANCE.checkCamping(player, target);

        if (isCamping) {
            BlockPos pos = player.blockPosition();
            String playerName = player.getGameProfile().getName();
            String targetName = target.getName().getString();
            String targetType = target.getType().toShortString();

            String line = "{\"ts\":\"" + Instant.now() + "\","
                    + "\"type\":\"camping\","
                    + "\"player\":\"" + TelemetryJson.escape(playerName) + "\","
                    + "\"pos\":[" + pos.getX() + "," + pos.getY() + "," + pos.getZ() + "],"
                    + "\"target\":\"" + TelemetryJson.escape(targetName) + "\"}";
            appendLine("alerts.ndjson", line);

            // Delegate heatmap to HeatmapService
            String room = resolveRoom(player.serverLevel(), pos);
            HeatmapService.INSTANCE.recordCamping(room, pos);

            // DuckDB: log camping alert
            DuckDBTelemetryService.INSTANCE.logAlert(
                "camping", playerName, targetName, targetType, room,
                pos.getX(), pos.getY(), pos.getZ(), null);
        }
    }

    /**
     * Track aggro drop: if a mob loses target while players are in the same room, log it.
     * PERFORMANCE FIX: Only check mobs within 64 blocks of players, not the entire world.
     */
    public void tickAggro(ServerLevel level) {
        // PERFORMANCE FIX: Check mobs around each player instead of entire world
        double trackingRange = 64.0;
        for (ServerPlayer player : level.players()) {
            var playerPos = player.position();
            var searchBox = new net.minecraft.world.phys.AABB(
                playerPos.x - trackingRange, playerPos.y - trackingRange, playerPos.z - trackingRange,
                playerPos.x + trackingRange, playerPos.y + trackingRange, playerPos.z + trackingRange
            );
            tickAggroForArea(level, searchBox);
        }
    }

    /**
     * Process aggro tracking for a specific area.
     */
    private void tickAggroForArea(ServerLevel level, net.minecraft.world.phys.AABB area) {
        net.minecraft.world.phys.AABB safeArea = Objects.requireNonNull(area, "area");
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, safeArea)) {
            if (!(entity instanceof net.minecraft.world.entity.Mob mob)) continue;
            if (entity.isRemoved()) continue;

            String mobName = mob.getName().getString();
            String mobType = mob.getType().toShortString();
            double mobX = mob.getX();
            double mobY = mob.getY();
            double mobZ = mob.getZ();

            // Update path tracker and check for kiting issues
            String pathIssue = EntityTrackingService.INSTANCE.updatePathAndDetectIssue(mob);
            if (pathIssue != null) {
                String room = resolveRoom(level, mob.blockPosition());
                String line = "{\"ts\":\"" + Instant.now() + "\","
                        + "\"type\":\"" + pathIssue + "\","
                        + "\"mob\":\"" + TelemetryJson.escape(mobName) + "\","
                        + "\"mobType\":\"" + TelemetryJson.escape(mobType) + "\","
                        + "\"pos\":[" + mobX + "," + mobY + "," + mobZ + "],"
                        + "\"room\":\"" + TelemetryJson.escape(room) + "\"}";
                appendLine("alerts.ndjson", line);

                // Record kiting heatmap
                if (pathIssue.equals("kiting_path") || pathIssue.equals("spin")) {
                    HeatmapService.INSTANCE.recordKiting(room, mob.blockPosition());
                }

                // DuckDB: log kiting/spin alert
                DuckDBTelemetryService.INSTANCE.logAlert(
                    pathIssue, null, mobName, mobType, room, mobX, mobY, mobZ, null);
            }

            // Check aggro drop using EntityTrackingService
            boolean aggroDropped = EntityTrackingService.INSTANCE.checkAggroDrop(mob);
            if (aggroDropped) {
                String room = resolveRoom(level, mob.blockPosition());
                String line = "{\"ts\":\"" + Instant.now() + "\","
                        + "\"type\":\"aggro_drop\","
                        + "\"mob\":\"" + TelemetryJson.escape(mobName) + "\","
                        + "\"mobType\":\"" + TelemetryJson.escape(mobType) + "\","
                        + "\"pos\":[" + mobX + "," + mobY + "," + mobZ + "],"
                        + "\"room\":\"" + TelemetryJson.escape(room) + "\"}";
                appendLine("alerts.ndjson", line);

                // Delegate heatmap to HeatmapService
                HeatmapService.INSTANCE.recordAggroDrop(room, mob.blockPosition());

                // DuckDB: log aggro_drop alert
                DuckDBTelemetryService.INSTANCE.logAlert(
                    "aggro_drop", null, mobName, mobType, room, mobX, mobY, mobZ, null);

                // Boss reset / leash fail heuristic: far from spawn
                EntityTrackingService.INSTANCE.getSpawnInfo(mob.getUUID()).ifPresent(info -> {
                    double dist = info.distanceFrom(mob.position());
                    if (dist > 20) {
                        String resetLine = "{\"ts\":\"" + Instant.now() + "\","
                                + "\"type\":\"reset\","
                                + "\"mob\":\"" + TelemetryJson.escape(mobName) + "\","
                                + "\"mobType\":\"" + TelemetryJson.escape(mobType) + "\","
                                + "\"pos\":[" + mobX + "," + mobY + "," + mobZ + "],"
                                + "\"spawnPos\":[" + info.position().x + "," + info.position().y + "," + info.position().z + "],"
                                + "\"room\":\"" + TelemetryJson.escape(room) + "\"}";
                        appendLine("alerts.ndjson", resetLine);

                        // DuckDB: log reset alert with spawn position in extra_data
                        String spawnPosJson = "{\"spawnPos\":[" + info.position().x + "," + info.position().y + "," + info.position().z + "]}";
                        DuckDBTelemetryService.INSTANCE.logAlert(
                            "reset", null, mobName, mobType, room, mobX, mobY, mobZ, spawnPosJson);
                    }
                });
            }
        }
    }

    public void tickSkills(long now) {
        SkillTrackingService.INSTANCE.tick(now, whiff ->
            appendLine("alerts.ndjson", whiff.toJson())
        );
    }

    // logFightEnd -> delegated to FightSessionService.FightSessionResult.toJson()

    /**
     * Counter for rate-limited NDJSON skip logging.
     */
    private final AtomicInteger ndjsonSkipCount = new AtomicInteger(0);

    /**
     * PERFORMANCE FIX: Queue telemetry write to background thread (~0.1ms overhead)
     *
     * Before: Blocking I/O write in server tick (5-20ms lag spike)
     * After: Non-blocking queue operation (<0.1ms)
     *
     * Impact: ~95% reduction in telemetry I/O lag
     *
     * DuckDB Migration: When DuckDB is PRIMARY (NDJSON_FALLBACK=false),
     * NDJSON writes are SKIPPED entirely. This is the single guard point
     * for ALL NDJSON writes in the system.
     */
    void appendLine(String fileName, String line) {
        if (telemetryDir == null || asyncWriter == null) return;

        // Circuit breaker: If DuckDB failed and FALLBACK_ON_ERROR=false, disable ALL telemetry
        if (duckDbInitFailed && !DuckDBConfig.FALLBACK_ON_ERROR) {
            // Rate-limited logging (every 1000 skips)
            int count = ndjsonSkipCount.incrementAndGet();
            if (count == 1 || count % 1000 == 0) {
                LOGGER.debug("[NDJSON] Skipped {} writes (STRICT MODE - DuckDB failed, no fallback) - latest: {}", count, fileName);
            }
            return;
        }

        // DuckDB PRIMARY mode: Skip NDJSON writes when DuckDB is enabled and fallback is disabled
        if (!DuckDBConfig.NDJSON_FALLBACK && DuckDBTelemetryService.INSTANCE.isEnabled()) {
            // Rate-limited logging (every 1000 skips)
            int count = ndjsonSkipCount.incrementAndGet();
            if (count == 1 || count % 1000 == 0) {
                LOGGER.debug("[NDJSON] Skipped {} writes (DuckDB PRIMARY mode) - latest: {}", count, fileName);
            }
            return;
        }

        Path file = telemetryDir.resolve(fileName);
        asyncWriter.queueWrite(file, line);
    }

    /**
     * Append action invocation telemetry line.
     */
    public void appendActionLine(String line) {
        appendLine("actions.ndjson", line);
    }

    /**
     * Append economy telemetry line (public API for LootTrackingEvents).
     */
    public void appendEconomyLine(String line) {
        appendLine("economy.ndjson", line);
    }

    /**
     * Append progression telemetry line (public API for ProgressionTrackingEvents).
     * Tracks: blocks, xp, advancements, dimensions, combat, trades, fishing.
     */
    public void appendProgressionLine(String line) {
        appendLine("progression.ndjson", line);
    }

    /**
     * Append endurance telemetry line (public API for EnduranceTelemetryService).
     * Tracks: waves, combos, perks, mutators, rewards, party, bosses.
     */
    public void appendEnduranceLine(String line) {
        appendLine("endurance.ndjson", line);
    }

    /**
     * Append player attributes telemetry line (public API for PlayerAttributeTelemetryService).
     * Tracks: player attribute changes, status effects, modifiers.
     */
    public void appendPlayerAttributesLine(String line) {
        appendLine("player_attributes.ndjson", line);
    }

    /**
     * Append ability usage telemetry line (public API for AbilityTelemetryService).
     * Tracks: dash, dodge, stamina usage, perfect dodges, exhaustion events.
     */
    public void appendAbilityUsageLine(String line) {
        appendLine("ability_usage.ndjson", line);
    }

    // Utility methods (stateJson, classifyHazard, spawnInSolid) moved to TelemetryLogHandlers

    // FightSession, TTKAggregate, FightBurst -> delegated to FightSessionService
    // PosTracker, CampingTracker, AggroTracker, SpawnInfo -> delegated to EntityTrackingService
    // OutOfBoundsTracker -> delegated to PlayerTrackingService

    /**
     * MEMORY LEAK FIX: Cleanup all tracker maps when entity is removed.
     * Removes entity from all tracker maps to prevent memory leaks.
     * Delegates to sub-services for their respective trackers.
     */
    public void cleanupEntity(UUID entityId) {
        // Delegate to log handlers for their trackers
        if (logHandlers != null) {
            logHandlers.cleanupEntity(entityId);
        }

        // Delegate to sub-services
        PlayerTrackingService.INSTANCE.cleanupPlayer(entityId);
        SkillTrackingService.INSTANCE.cleanupEntity(entityId);
        BossPhaseService.INSTANCE.cleanupEntity(entityId);
        DungeonSessionService.INSTANCE.cleanupEntity(entityId);
        DamageTrackingService.INSTANCE.cleanupEntity(entityId);
        EntityTrackingService.INSTANCE.cleanupEntity(entityId);
    }

    // BossPhase -> delegated to BossPhaseService
    // MinionWaveTracker -> delegated to MinionService
    // DungeonSession, BacktrackTracker -> delegated to DungeonSessionService
    // EntityDensityTracker, ChokePoint, InvisibleCollision, ParkourFall -> delegated to SpatialMetricsService

    // ===== FASE 5: Public Methods =====

    /**
     * M41: Start a dungeon session for a player. Delegates to DungeonSessionService.
     */
    public void startDungeonSession(ServerPlayer player, String dungeonId) {
        DungeonSessionService.INSTANCE.startSession(player.getUUID(), dungeonId);
        LOGGER.debug("Started dungeon session {} for {}", dungeonId, player.getName().getString());
    }

    /**
     * M41: End a dungeon session with outcome. Delegates to DungeonSessionService.
     */
    public void endDungeonSession(ServerPlayer player, String outcome) {
        DungeonSessionService.INSTANCE.endSession(player.getUUID(), player.getName().getString(), outcome)
            .ifPresent(result -> appendLine("dungeon_sessions.ndjson", result.toJson()));
    }

    /**
     * M42: Log player quit position for choke point analysis. Delegates to SpatialMetricsService.
     */
    public void logPlayerQuit(ServerPlayer player) {
        String room = resolveRoom((ServerLevel) player.level(), player.blockPosition());
        BlockPos pos = player.blockPosition();
        String playerName = player.getName().getString();
        var record = SpatialMetricsService.INSTANCE.recordQuit(room, pos);
        appendLine("player_quits.ndjson", record.toJson(playerName));

        // DuckDB: log choke_point alert (player quit position)
        DuckDBTelemetryService.INSTANCE.logAlert(
            "choke_point", playerName, null, null, room,
            pos.getX(), pos.getY(), pos.getZ(), null);
    }

    /**
     * M47: Track player room visit for backtracking detection.
     * Delegates backtrack tracking to PlayerTrackingService and DungeonSessionService.
     */
    public void trackRoomVisit(ServerPlayer player, String roomId) {
        // Delegate backtrack tracking to PlayerTrackingService
        var result = PlayerTrackingService.INSTANCE.trackRoomVisit(player.getUUID(), roomId);

        if (result.isBacktrack()) {
            String playerName = player.getName().getString();
            BlockPos pos = player.blockPosition();

            String line = "{\"ts\":\"" + Instant.now() + "\","
                    + "\"type\":\"backtrack\","
                    + "\"player\":\"" + TelemetryJson.escape(playerName) + "\","
                    + "\"room\":\"" + TelemetryJson.escape(roomId) + "\","
                    + "\"backtrackCount\":" + result.backtrackCount() + "}";
            appendLine("alerts.ndjson", line);

            // DuckDB: log backtrack alert
            DuckDBTelemetryService.INSTANCE.logAlert(
                "backtrack", playerName, null, null, roomId,
                pos.getX(), pos.getY(), pos.getZ(),
                "{\"backtrackCount\":" + result.backtrackCount() + "}");
        }

        // Update dungeon session room sequence
        DungeonSessionService.INSTANCE.enterRoom(player.getUUID(), roomId);
    }

    /**
     * M52: Update entity density for a room. Delegates to SpatialMetricsService.
     */
    public void updateEntityDensity(ServerLevel level, String roomId) {
        SpatialMetricsService.INSTANCE.updateEntityDensity(level, roomId);
    }

    /**
     * M13: Log invisible collision detection. Delegates to SpatialMetricsService.
     */
    public void logInvisibleCollision(ServerPlayer player, BlockPos pos) {
        String room = resolveRoom((ServerLevel) player.level(), pos);
        var record = SpatialMetricsService.INSTANCE.recordInvisibleCollision(room, pos);
        String playerName = player.getName().getString();
        appendLine("alerts.ndjson", record.toJson(playerName));

        // DuckDB: log invisible_collision alert
        DuckDBTelemetryService.INSTANCE.logAlert(
            "invisible_collision", playerName, null, null, room,
            pos.getX(), pos.getY(), pos.getZ(),
            "{\"totalCount\":" + record.totalCount() + "}");
    }

    /**
     * M14: Log parkour fall for difficulty analysis. Delegates to SpatialMetricsService.
     */
    public void logParkourFall(ServerPlayer player, float fallDistance, BlockPos fallPos) {
        String room = resolveRoom((ServerLevel) player.level(), fallPos);
        var record = SpatialMetricsService.INSTANCE.recordParkourFall(room, fallPos, fallDistance);
        String playerName = player.getName().getString();
        appendLine("parkour_falls.ndjson", record.toJson(playerName));

        // DuckDB: log parkour_fall alert
        DuckDBTelemetryService.INSTANCE.logAlert(
            "parkour_fall", playerName, null, null, room,
            fallPos.getX(), fallPos.getY(), fallPos.getZ(),
            "{\"fallDistance\":" + fallDistance + ",\"totalCount\":" + record.totalCount() + "}");
    }

    // ===== FASE 5: Export Methods =====

    /**
     * Export choke point quit heatmap. Delegates to SpatialMetricsService.
     */
    public void exportChokePointHeatmap() {
        SpatialMetricsService.INSTANCE.exportChokePointHeatmap((room, line) ->
            appendLine("choke_point_heatmap.ndjson", line));
    }

    /**
     * Export invisible collision heatmap. Delegates to SpatialMetricsService.
     */
    public void exportInvisibleCollisionHeatmap() {
        SpatialMetricsService.INSTANCE.exportInvisibleCollisionHeatmap((room, line) ->
            appendLine("invisible_collision_heatmap.ndjson", line));
    }

    /**
     * Export parkour fall heatmap. Delegates to SpatialMetricsService.
     */
    public void exportParkourFallHeatmap() {
        SpatialMetricsService.INSTANCE.exportParkourFallHeatmap((room, line) ->
            appendLine("parkour_fall_heatmap.ndjson", line));
    }

    /**
     * Get entity density report for all rooms. Delegates to SpatialMetricsService.
     */
    public List<String> getEntityDensityReport() {
        return SpatialMetricsService.INSTANCE.getEntityDensityReport();
    }

    /**
     * Export damage statistics to NDJSON file.
     * Includes environmental damage, combat damage, and room stats.
     */
    public void exportDamageStats() {
        String timestamp = Instant.now().toString();

        // Environmental damage
        var envStats = com.devmod.testing.stats.EnvironmentalDamageStats.INSTANCE;
        com.google.gson.JsonObject envJson = envStats.toJson();
        envJson.addProperty("type", "environmental_damage");
        envJson.addProperty("timestamp", timestamp);
        appendLine("damage_stats.ndjson", envJson.toString());

        // Weapon summaries from DamageTrackingService
        var damageService = DamageTrackingService.INSTANCE;
        for (String summary : damageService.getWeaponSummaries()) {
            com.google.gson.JsonObject weaponJson = new com.google.gson.JsonObject();
            weaponJson.addProperty("type", "weapon_summary");
            weaponJson.addProperty("data", summary);
            weaponJson.addProperty("timestamp", timestamp);
            appendLine("damage_stats.ndjson", weaponJson.toString());
        }

        // Room summaries
        for (String summary : damageService.getRoomSummaries()) {
            com.google.gson.JsonObject roomJson = new com.google.gson.JsonObject();
            roomJson.addProperty("type", "room_damage_summary");
            roomJson.addProperty("data", summary);
            roomJson.addProperty("timestamp", timestamp);
            appendLine("damage_stats.ndjson", roomJson.toString());
        }

        LOGGER.info("Exported damage statistics to damage_stats.ndjson");
    }
}
