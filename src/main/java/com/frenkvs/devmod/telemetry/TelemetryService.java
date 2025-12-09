package com.frenkvs.devmod.telemetry;

import com.frenkvs.devmod.telemetry.boss.BossPhaseService;
import com.frenkvs.devmod.telemetry.combat.FightSessionService;
import com.frenkvs.devmod.telemetry.damage.DamageTrackingService;
import com.frenkvs.devmod.telemetry.dungeon.DungeonSessionService;
import com.frenkvs.devmod.telemetry.entity.EntityTrackingService;
import com.frenkvs.devmod.telemetry.entity.MinionService;
import com.frenkvs.devmod.telemetry.player.PlayerTrackingService;
import com.frenkvs.devmod.telemetry.room.RoomService;
import com.frenkvs.devmod.telemetry.skills.SkillTrackingService;
import com.frenkvs.devmod.telemetry.spatial.HeatmapService;
import com.frenkvs.devmod.telemetry.spatial.LightAnalysisService;
import com.frenkvs.devmod.telemetry.spatial.SpatialMetricsService;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Central telemetry logger. Keeps light in-memory aggregates and appends NDJSON lines to disk.
 */
// Minecraft API (getGameProfile) guaranteed non-null for ServerPlayer
@SuppressWarnings("null")
public class TelemetryService {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final TelemetryService INSTANCE = new TelemetryService();

    // Room definitions delegated to RoomService
    private Path telemetryDir;
    private AsyncTelemetryWriter asyncWriter;

    // weaponAggregates, roomAggregates, activeFights -> delegated to services
    // playerRooms, lastMovementSample, oobTrackers, backtrackTrackers -> delegated to PlayerTrackingService
    private final AtomicInteger tickCounter = new AtomicInteger(0);
    private final Map<UUID, Long> mobSpawnTime = new ConcurrentHashMap<>();
    private TelemetrySettings settings = TelemetrySettings.defaults();
    private final Map<UUID, Long> mobFirstHit = new ConcurrentHashMap<>();

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
        }

        // FASE 3 M10: Sample player movement for heatmap (every 2 seconds)
        if (result.shouldSampleMovement()) {
            HeatmapService.INSTANCE.recordMovement(roomId, player.blockPosition());
        }
    }

    public void checkOutOfBounds(ServerPlayer player) {
        // Delegate to PlayerTrackingService
        var result = PlayerTrackingService.INSTANCE.checkOutOfBounds(player);

        if (result.shouldAlert()) {
            String line = "{\"ts\":\"" + Instant.now() + "\","
                    + "\"type\":\"out_of_bounds_vertical\","
                    + "\"player\":\"" + TelemetryJson.escape(player.getGameProfile().getName()) + "\","
                    + "\"room\":\"" + TelemetryJson.escape(result.roomId()) + "\","
                    + "\"pos\":[" + result.position().x + "," + result.position().y + "," + result.position().z + "],"
                    + "\"deltaY\":" + result.deltaY() + "}";
            appendLine("alerts.ndjson", line);
        }
    }

    /**
     * Resolve a position to a room ID. Delegates to RoomService.
     */
    public String resolveRoom(ServerLevel level, BlockPos pos) {
        return RoomService.INSTANCE.resolveRoom(level, pos);
    }

    /**
     * Find room definition for a position. Delegates to RoomService.
     */
    private RoomDefinition findRoom(ServerLevel level, BlockPos pos) {
        return RoomService.INSTANCE.findRoom(level, pos);
    }

    public void logHit(Level level, Entity attacker, LivingEntity target, DamageSource source, double amount, double hpBefore, double hpAfter, String bodyPart, double distance, double armorPenBonus) {
        if (level.isClientSide()) return;
        String room = resolveRoom((ServerLevel) level, target.blockPosition());
        String attackerName = attacker != null ? attacker.getName().getString() : source.getMsgId();
        String attackerType = attacker != null ? EntityTypeName.of(attacker) : "unknown";
        String targetType = EntityTypeName.of(target);
        String damageType = source.type().msgId();
        boolean hazard = attacker == null;
        String hazardType = hazard ? classifyHazard(source) : "";

        String attackerState = attacker instanceof LivingEntity livingAttacker
                ? stateJson(livingAttacker)
                : "{}";
        String targetState = stateJson(target);

        String line = "{\"ts\":\"" + Instant.now() + "\","
                + "\"room\":\"" + TelemetryJson.escape(room) + "\","
                + "\"world\":\"" + level.dimension().location() + "\","
                + "\"attacker\":\"" + TelemetryJson.escape(attackerName) + "\","
                + "\"attackerType\":\"" + TelemetryJson.escape(attackerType) + "\","
                + "\"target\":\"" + TelemetryJson.escape(target.getName().getString()) + "\","
                + "\"targetType\":\"" + TelemetryJson.escape(targetType) + "\","
                + "\"dmg\":" + amount + ","
                + "\"dmgType\":\"" + TelemetryJson.escape(damageType) + "\","
                + "\"hpBefore\":" + hpBefore + ","
                + "\"hpAfter\":" + hpAfter + ","
                + "\"bodyPart\":\"" + TelemetryJson.escape(bodyPart) + "\","
                + "\"distance\":" + distance + ","
                + "\"armorPenBonus\":" + armorPenBonus + ","
                + "\"miss\":false,"
                + "\"hazard\":" + hazard + ","
                + "\"hazardType\":\"" + TelemetryJson.escape(hazardType) + "\","
                + "\"attackerState\":" + attackerState + ","
                + "\"targetState\":" + targetState
                + "}";
        appendLine("hits.ndjson", line);

        // Aggregates - delegate to DamageTrackingService
        if (attacker instanceof ServerPlayer player) {
            DamageTrackingService.INSTANCE.registerWeaponHit(player, amount, hpAfter <= 0);
        }
        if (attacker instanceof Mob mobAttacker && !(attacker instanceof ServerPlayer)) {
            DamageTrackingService.INSTANCE.registerMinionDamage(mobAttacker, amount);
        }
        DamageTrackingService.INSTANCE.registerRoomDamage(room, target, amount, hpAfter <= 0);

        if (level instanceof ServerLevel serverLevel) {
            registerFightHit(serverLevel, room, attacker, target, hpAfter <= 0);
        }

        // First-hit timestamp for TTK
        mobFirstHit.putIfAbsent(target.getUUID(), System.currentTimeMillis());

        // Track spawn time if not present
        mobSpawnTime.putIfAbsent(target.getUUID(), System.currentTimeMillis());

        // Stuck detection (mob not moving for >3s while fighting)
        if (attacker instanceof LivingEntity livingAttacker) {
            checkStuck(livingAttacker);
        }
        checkStuck(target);

        // Camping detection: repeated hits from same block
        if (attacker instanceof ServerPlayer player) {
            checkCamping(player, target);
        }

        // Burst tracking and HP stats - delegate to FightSessionService
        if (level instanceof ServerLevel) {
            FightSessionService.INSTANCE.registerBurstDamage(room, amount);
            FightSessionService.INSTANCE.registerHpAfterHit(room, target, hpAfter);
        }
    }

    public void logMiss(Level level, Entity attacker, Vec3 impactPos, String impactType) {
        if (level.isClientSide()) return;
        String room = resolveRoom((ServerLevel) level, BlockPos.containing(impactPos));
        String attackerName = attacker != null ? attacker.getName().getString() : "unknown";
        String attackerType = attacker != null ? EntityTypeName.of(attacker) : "unknown";
        double distance = attacker != null ? attacker.position().distanceTo(impactPos) : -1;

        String line = "{\"ts\":\"" + Instant.now() + "\","
                + "\"room\":\"" + TelemetryJson.escape(room) + "\","
                + "\"world\":\"" + level.dimension().location() + "\","
                + "\"attacker\":\"" + TelemetryJson.escape(attackerName) + "\","
                + "\"attackerType\":\"" + TelemetryJson.escape(attackerType) + "\","
                + "\"dmg\":0,"
                + "\"miss\":true,"
                + "\"impact\":\"" + TelemetryJson.escape(impactType) + "\","
                + "\"pos\":[" + impactPos.x + "," + impactPos.y + "," + impactPos.z + "],"
                + "\"distance\":" + distance
                + "}";
        appendLine("hits.ndjson", line);

        // Delegate miss tracking to DamageTrackingService
        if (attacker instanceof ServerPlayer player) {
            DamageTrackingService.INSTANCE.registerWeaponMiss(player);
        }
    }

    public void logSkillCast(LivingEntity caster, String skillId) {
        if (caster.level().isClientSide()) return;
        String room = resolveRoom((ServerLevel) caster.level(), caster.blockPosition());
        SkillTrackingService.INSTANCE.recordCast(
            caster.getUUID(), skillId, room,
            caster.level().dimension().location().toString(),
            caster.getName().getString(), EntityTypeName.of(caster)
        );
    }

    public void logSkillHit(LivingEntity caster, String skillId) {
        if (caster.level().isClientSide()) return;
        SkillTrackingService.INSTANCE.recordHit(caster.getUUID(), skillId);
    }

    public void logBossPhaseStart(ServerLevel level, LivingEntity boss, String phase) {
        String room = resolveRoom(level, boss.blockPosition());
        BossPhaseService.INSTANCE.startPhase(
            boss.getUUID(), phase, room,
            level.dimension().location().toString()
        );
    }

    public void logBossPhaseEnd(LivingEntity boss) {
        BossPhaseService.INSTANCE.endPhase(
            boss.getUUID(),
            boss.getName().getString(),
            EntityTypeName.of(boss)
        ).ifPresent(result -> appendLine("phases.ndjson", result.toJson()));
    }

    public void logDeath(Level level, LivingEntity entity, DamageSource source) {
        if (level.isClientSide()) return;
        String room = resolveRoom((ServerLevel) level, entity.blockPosition());
        long deathTime = System.currentTimeMillis();
        Long firstHit = mobFirstHit.remove(entity.getUUID());
        Long firstSeen = mobSpawnTime.remove(entity.getUUID());
        EntityTrackingService.INSTANCE.cleanupEntity(entity.getUUID());
        Long ttkFirstHit = (firstHit != null) ? (deathTime - firstHit) : null;
        Long ttkSpawn = (firstSeen != null) ? (deathTime - firstSeen) : null;
        String line = "{\"ts\":\"" + Instant.now() + "\","
                + "\"room\":\"" + TelemetryJson.escape(room) + "\","
                + "\"world\":\"" + level.dimension().location() + "\","
                + "\"target\":\"" + TelemetryJson.escape(entity.getName().getString()) + "\","
                + "\"targetType\":\"" + TelemetryJson.escape(EntityTypeName.of(entity)) + "\","
                + "\"cause\":\"" + TelemetryJson.escape(source.getMsgId()) + "\","
                + "\"ttkFirstHitMs\":" + (ttkFirstHit != null ? ttkFirstHit : -1) + ","
                + "\"ttkSpawnMs\":" + (ttkSpawn != null ? ttkSpawn : -1)
                + "}";
        appendLine("deaths.ndjson", line);

        // FASE 3 M9: Aggregate death position for heatmap
        HeatmapService.INSTANCE.recordDeath(room, entity.blockPosition());

        // Register TTK in FightSessionService
        if (level instanceof ServerLevel && ttkFirstHit != null) {
            FightSessionService.INSTANCE.registerTTK(room, entity, ttkFirstHit);
        }

        // Get minion damage stats from DamageTrackingService
        var minionStats = DamageTrackingService.INSTANCE.removeMinionStats(entity.getUUID());
        if (minionStats.isPresent() && !(entity instanceof ServerPlayer)) {
            var stats = minionStats.get();
            // FASE 2 M6: Track minion death for concurrent count - delegated to MinionService
            MinionService.INSTANCE.recordDeath(room, entity.getUUID(), stats.getTotalDamage());
            int peakConcurrent = MinionService.INSTANCE.getPeakConcurrent(room);
            int currentConcurrent = MinionService.INSTANCE.getCurrentCount(room);

            String minionLine = "{\"ts\":\"" + Instant.now() + "\","
                    + "\"room\":\"" + TelemetryJson.escape(room) + "\","
                    + "\"world\":\"" + level.dimension().location() + "\","
                    + "\"mob\":\"" + TelemetryJson.escape(entity.getName().getString()) + "\","
                    + "\"mobType\":\"" + TelemetryJson.escape(EntityTypeName.of(entity)) + "\","
                    + "\"lifetimeMs\":" + (ttkSpawn != null ? ttkSpawn : -1) + ","
                    + "\"damageDone\":" + stats.getTotalDamage() + ","
                    + "\"hits\":" + stats.getHits() + ","
                    + "\"peakConcurrent\":" + peakConcurrent + ","
                    + "\"currentConcurrent\":" + currentConcurrent
                    + "}";
            appendLine("minions.ndjson", minionLine);
        }

        BossPhaseService.INSTANCE.cleanupEntity(entity.getUUID());
    }

    public void logHeal(Level level, LivingEntity entity, double amount, String source) {
        if (level.isClientSide()) return;
        String room = resolveRoom((ServerLevel) level, entity.blockPosition());
        double hpBefore = entity.getHealth();
        double hpAfter = Math.min(entity.getMaxHealth(), hpBefore + amount);
        String line = "{\"ts\":\"" + Instant.now() + "\","
                + "\"room\":\"" + TelemetryJson.escape(room) + "\","
                + "\"world\":\"" + level.dimension().location() + "\","
                + "\"target\":\"" + TelemetryJson.escape(entity.getName().getString()) + "\","
                + "\"targetType\":\"" + TelemetryJson.escape(EntityTypeName.of(entity)) + "\","
                + "\"heal\":" + amount + ","
                + "\"hpBefore\":" + hpBefore + ","
                + "\"hpAfter\":" + hpAfter + ","
                + "\"source\":\"" + TelemetryJson.escape(source) + "\""
                + "}";
        appendLine("heals.ndjson", line);

        // Aggregates per stanza - delegate to DamageTrackingService
        DamageTrackingService.INSTANCE.registerRoomHeal(room, entity, amount);
    }

    public void logSpawn(ServerLevel level, LivingEntity entity, String reason) {
        mobSpawnTime.put(entity.getUUID(), System.currentTimeMillis());
        // Register spawn info in EntityTrackingService
        EntityTrackingService.INSTANCE.registerSpawn(entity.getUUID(),
                level.dimension().location().toString(), entity.position());
        String room = resolveRoom(level, entity.blockPosition());
        boolean failed = spawnInSolid(level, entity) || entity.getY() < level.getMinBuildHeight() || entity.getY() > level.getMaxBuildHeight() + 4;
        String line = "{\"ts\":\"" + Instant.now() + "\","
                + "\"room\":\"" + TelemetryJson.escape(room) + "\","
                + "\"world\":\"" + level.dimension().location() + "\","
                + "\"entity\":\"" + TelemetryJson.escape(entity.getName().getString()) + "\","
                + "\"entityType\":\"" + TelemetryJson.escape(EntityTypeName.of(entity)) + "\","
                + "\"reason\":\"" + TelemetryJson.escape(reason) + "\","
                + "\"spawnFail\":" + failed
                + "}";
        appendLine("spawns.ndjson", line);

        // FASE 2 M6: Track minion spawn for concurrent count - delegated to MinionService
        if (entity instanceof Mob && !(entity instanceof ServerPlayer) && entity.getMaxHealth() < settings.bossHpThreshold()) {
            MinionService.INSTANCE.recordSpawn(room, entity.getUUID());
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
     * Register a hit into the fight tracker. Delegates to FightSessionService.
     */
    private void registerFightHit(ServerLevel level, String room, Entity attacker, LivingEntity target, boolean kill) {
        FightSessionService.INSTANCE.registerHit(room, level.dimension().location().toString(), attacker, target, kill);
    }

    /**
     * Called every server tick to close inactive fights. Delegates to FightSessionService.
     */
    public void tickFights() {
        FightSessionService.INSTANCE.tick(result -> {
            // Write fight result to NDJSON using the service's built-in JSON serialization
            appendLine("fights.ndjson", result.toJson());
        });
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
        appendLine("performance.ndjson", line);
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

    private void checkStuck(LivingEntity entity) {
        if (entity.level().isClientSide()) return;

        // Delegate to EntityTrackingService
        boolean isStuck = EntityTrackingService.INSTANCE.checkStuck(entity);

        if (isStuck) {
            ResourceKey<Level> dim = entity.level().dimension();
            String line = "{\"ts\":\"" + Instant.now() + "\","
                    + "\"type\":\"stuck\","
                    + "\"entity\":\"" + TelemetryJson.escape(entity.getName().getString()) + "\","
                    + "\"entityType\":\"" + TelemetryJson.escape(EntityTypeName.of(entity)) + "\","
                    + "\"pos\":[" + entity.getX() + "," + entity.getY() + "," + entity.getZ() + "],"
                    + "\"world\":\"" + dim.location() + "\"}";
            appendLine("alerts.ndjson", line);

            // Delegate heatmap to HeatmapService
            if (entity.level() instanceof ServerLevel serverLevel) {
                String room = resolveRoom(serverLevel, entity.blockPosition());
                HeatmapService.INSTANCE.recordStuck(room, entity.blockPosition());
            }
        }
    }

    private void checkCamping(ServerPlayer player, LivingEntity target) {
        // Delegate to EntityTrackingService
        boolean isCamping = EntityTrackingService.INSTANCE.checkCamping(player, target);

        if (isCamping) {
            BlockPos pos = player.blockPosition();
            String line = "{\"ts\":\"" + Instant.now() + "\","
                    + "\"type\":\"camping\","
                    + "\"player\":\"" + TelemetryJson.escape(player.getGameProfile().getName()) + "\","
                    + "\"pos\":[" + pos.getX() + "," + pos.getY() + "," + pos.getZ() + "],"
                    + "\"target\":\"" + TelemetryJson.escape(target.getName().getString()) + "\"}";
            appendLine("alerts.ndjson", line);

            // Delegate heatmap to HeatmapService
            String room = resolveRoom(player.serverLevel(), pos);
            HeatmapService.INSTANCE.recordCamping(room, pos);
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
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, area)) {
            if (!(entity instanceof net.minecraft.world.entity.Mob mob)) continue;
            if (entity.isRemoved()) continue;

            // Update path tracker and check for kiting issues
            String pathIssue = EntityTrackingService.INSTANCE.updatePathAndDetectIssue(mob);
            if (pathIssue != null) {
                String room = resolveRoom(level, mob.blockPosition());
                String line = "{\"ts\":\"" + Instant.now() + "\","
                        + "\"type\":\"" + pathIssue + "\","
                        + "\"mob\":\"" + TelemetryJson.escape(mob.getName().getString()) + "\","
                        + "\"mobType\":\"" + TelemetryJson.escape(EntityTypeName.of(mob)) + "\","
                        + "\"pos\":[" + mob.getX() + "," + mob.getY() + "," + mob.getZ() + "],"
                        + "\"room\":\"" + TelemetryJson.escape(room) + "\"}";
                appendLine("alerts.ndjson", line);

                // Record kiting heatmap
                if (pathIssue.equals("kiting_path") || pathIssue.equals("spin")) {
                    HeatmapService.INSTANCE.recordKiting(room, mob.blockPosition());
                }
            }

            // Check aggro drop using EntityTrackingService
            boolean aggroDropped = EntityTrackingService.INSTANCE.checkAggroDrop(mob);
            if (aggroDropped) {
                String room = resolveRoom(level, mob.blockPosition());
                String line = "{\"ts\":\"" + Instant.now() + "\","
                        + "\"type\":\"aggro_drop\","
                        + "\"mob\":\"" + TelemetryJson.escape(mob.getName().getString()) + "\","
                        + "\"mobType\":\"" + TelemetryJson.escape(EntityTypeName.of(mob)) + "\","
                        + "\"pos\":[" + mob.getX() + "," + mob.getY() + "," + mob.getZ() + "],"
                        + "\"room\":\"" + TelemetryJson.escape(room) + "\"}";
                appendLine("alerts.ndjson", line);

                // Delegate heatmap to HeatmapService
                HeatmapService.INSTANCE.recordAggroDrop(room, mob.blockPosition());

                // Boss reset / leash fail heuristic: far from spawn
                EntityTrackingService.INSTANCE.getSpawnInfo(mob.getUUID()).ifPresent(info -> {
                    double dist = info.distanceFrom(mob.position());
                    if (dist > 20) {
                        String resetLine = "{\"ts\":\"" + Instant.now() + "\","
                                + "\"type\":\"reset\","
                                + "\"mob\":\"" + TelemetryJson.escape(mob.getName().getString()) + "\","
                                + "\"mobType\":\"" + TelemetryJson.escape(EntityTypeName.of(mob)) + "\","
                                + "\"pos\":[" + mob.getX() + "," + mob.getY() + "," + mob.getZ() + "],"
                                + "\"spawnPos\":[" + info.position().x + "," + info.position().y + "," + info.position().z + "],"
                                + "\"room\":\"" + TelemetryJson.escape(room) + "\"}";
                        appendLine("alerts.ndjson", resetLine);
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
     * PERFORMANCE FIX: Queue telemetry write to background thread (~0.1ms overhead)
     *
     * Before: Blocking I/O write in server tick (5-20ms lag spike)
     * After: Non-blocking queue operation (<0.1ms)
     *
     * Impact: ~95% reduction in telemetry I/O lag
     */
    void appendLine(String fileName, String line) {
        if (telemetryDir == null || asyncWriter == null) return;
        Path file = telemetryDir.resolve(fileName);
        asyncWriter.queueWrite(file, line);
    }

    /**
     * Append economy telemetry line (public API for LootTrackingEvents).
     */
    public void appendEconomyLine(String line) {
        appendLine("economy.ndjson", line);
    }

    // PlayerRoomTracker -> delegated to PlayerTrackingService

    private static String stateJson(LivingEntity entity) {
        StringBuilder effects = new StringBuilder();
        effects.append('[');
        boolean first = true;
        for (MobEffectInstance eff : entity.getActiveEffects()) {
            if (!first) effects.append(',');
            first = false;
            effects.append("{\"id\":\"")
                    .append(TelemetryJson.escape(eff.getEffect().value().getDescriptionId()))
                    .append("\",\"dur\":")
                    .append(eff.getDuration())
                    .append(",\"amp\":")
                    .append(eff.getAmplifier())
                    .append('}');
        }
        effects.append(']');

        String mainHand = entity.getMainHandItem().isEmpty()
                ? ""
                : entity.getMainHandItem().getItem().toString();

        return "{" +
                "\"hp\":" + entity.getHealth() + "," +
                "\"maxHp\":" + entity.getMaxHealth() + "," +
                "\"armor\":" + entity.getArmorValue() + "," +
                "\"mainHand\":\"" + TelemetryJson.escape(mainHand) + "\"," +
                "\"effects\":" + effects +
                "}";
    }

    private static String classifyHazard(DamageSource source) {
        if (source.is(net.minecraft.tags.DamageTypeTags.IS_FIRE)) return "fire";
        if (source.is(net.minecraft.tags.DamageTypeTags.IS_FALL)) return "fall";
        if (source.is(net.minecraft.tags.DamageTypeTags.IS_DROWNING)) return "drown";
        if (source.is(net.minecraft.tags.DamageTypeTags.IS_FREEZING)) return "freeze";
        if (source.is(net.minecraft.tags.DamageTypeTags.IS_LIGHTNING)) return "lightning";
        if (source.is(net.minecraft.tags.DamageTypeTags.IS_EXPLOSION)) return "explosion";
        if (source.is(net.minecraft.tags.DamageTypeTags.IS_PROJECTILE)) return "projectile";
        return source.type().msgId();
    }

    // serializeTTK, avg -> no longer needed (FightSessionService.FightSessionResult.toJson() handles this)

    private static boolean spawnInSolid(ServerLevel level, LivingEntity entity) {
        return !level.noCollision(entity);
    }

    // DamageStats, SkillTracker -> delegated to DamageTrackingService, SkillTrackingService
    // WeaponAggregate, RoomAggregate -> delegated to DamageTrackingService

    private static final class EntityTypeName {
        static String of(Entity entity) {
            return entity.getType().toShortString();
        }
    }

    // FightSession, TTKAggregate, FightBurst -> delegated to FightSessionService
    // PosTracker, CampingTracker, AggroTracker, SpawnInfo -> delegated to EntityTrackingService
    // OutOfBoundsTracker -> delegated to PlayerTrackingService

    /**
     * MEMORY LEAK FIX: Cleanup all tracker maps when entity is removed.
     * Removes entity from all tracker maps to prevent memory leaks.
     * Delegates to sub-services for their respective trackers.
     */
    public void cleanupEntity(UUID entityId) {
        // Local trackers
        mobSpawnTime.remove(entityId);
        mobFirstHit.remove(entityId);

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
        var record = SpatialMetricsService.INSTANCE.recordQuit(room, player.blockPosition());
        appendLine("player_quits.ndjson", record.toJson(player.getName().getString()));
    }

    /**
     * M47: Track player room visit for backtracking detection.
     * Delegates backtrack tracking to PlayerTrackingService and DungeonSessionService.
     */
    public void trackRoomVisit(ServerPlayer player, String roomId) {
        // Delegate backtrack tracking to PlayerTrackingService
        var result = PlayerTrackingService.INSTANCE.trackRoomVisit(player.getUUID(), roomId);

        if (result.isBacktrack()) {
            String line = "{\"ts\":\"" + Instant.now() + "\","
                    + "\"type\":\"backtrack\","
                    + "\"player\":\"" + TelemetryJson.escape(player.getName().getString()) + "\","
                    + "\"room\":\"" + TelemetryJson.escape(roomId) + "\","
                    + "\"backtrackCount\":" + result.backtrackCount() + "}";
            appendLine("alerts.ndjson", line);
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
        appendLine("alerts.ndjson", record.toJson(player.getName().getString()));
    }

    /**
     * M14: Log parkour fall for difficulty analysis. Delegates to SpatialMetricsService.
     */
    public void logParkourFall(ServerPlayer player, float fallDistance, BlockPos fallPos) {
        String room = resolveRoom((ServerLevel) player.level(), fallPos);
        var record = SpatialMetricsService.INSTANCE.recordParkourFall(room, fallPos, fallDistance);
        appendLine("parkour_falls.ndjson", record.toJson(player.getName().getString()));
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
        var envStats = com.frenkvs.devmod.testing.stats.EnvironmentalDamageStats.INSTANCE;
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
