package com.frenkvs.devmod.telemetry;

import com.frenkvs.devmod.telemetry.boss.BossPhaseService;
import com.frenkvs.devmod.telemetry.combat.FightSessionService;
import com.frenkvs.devmod.telemetry.damage.DamageTrackingService;
import com.frenkvs.devmod.telemetry.duckdb.DuckDBConfig;
import com.frenkvs.devmod.telemetry.duckdb.DuckDBTelemetryService;
import com.frenkvs.devmod.telemetry.entity.EntityTrackingService;
import com.frenkvs.devmod.telemetry.entity.MinionService;
import com.frenkvs.devmod.telemetry.skills.SkillTrackingService;
import com.frenkvs.devmod.telemetry.spatial.HeatmapService;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles all log* methods for TelemetryService.
 * Extracted for single responsibility - all event logging operations.
 */
public class TelemetryLogHandlers {

    private final TelemetryService service;
    private final Map<UUID, Long> mobSpawnTime = new ConcurrentHashMap<>();
    private final Map<UUID, Long> mobFirstHit = new ConcurrentHashMap<>();

    public TelemetryLogHandlers(TelemetryService service) {
        this.service = service;
    }

    public void logHit(Level level, Entity attacker, LivingEntity target, DamageSource source,
                       double amount, double hpBefore, double hpAfter, String bodyPart,
                       double distance, double armorPenBonus) {
        if (level.isClientSide()) return;
        String room = service.resolveRoom((ServerLevel) level, target.blockPosition());
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

        // DuckDB PRIMARY, NDJSON fallback
        if (DuckDBTelemetryService.INSTANCE.isEnabled()) {
            DuckDBTelemetryService.INSTANCE.logHit(room, level.dimension().location().toString(),
                attackerName, attackerType, target.getName().getString(), targetType,
                amount, damageType, hpBefore, hpAfter, bodyPart, distance,
                armorPenBonus, false, hazard, hazardType, attackerState, targetState);
        }
        if (DuckDBConfig.NDJSON_FALLBACK || !DuckDBTelemetryService.INSTANCE.isEnabled()) {
            service.appendLine("hits.ndjson", line);
        }

        // Aggregates - delegate to DamageTrackingService
        if (attacker instanceof ServerPlayer player) {
            DamageTrackingService.INSTANCE.registerWeaponHit(player, amount, hpAfter <= 0);
        }
        if (attacker instanceof Mob mobAttacker && !(attacker instanceof ServerPlayer)) {
            DamageTrackingService.INSTANCE.registerMinionDamage(mobAttacker, amount);
        }
        DamageTrackingService.INSTANCE.registerRoomDamage(room, target, amount, hpAfter <= 0);

        if (level instanceof ServerLevel serverLevel) {
            FightSessionService.INSTANCE.registerHit(room, serverLevel.dimension().location().toString(),
                attacker, target, hpAfter <= 0);
        }

        // First-hit timestamp for TTK
        mobFirstHit.putIfAbsent(target.getUUID(), System.currentTimeMillis());

        // Track spawn time if not present
        mobSpawnTime.putIfAbsent(target.getUUID(), System.currentTimeMillis());

        // Stuck detection
        if (attacker instanceof LivingEntity livingAttacker) {
            service.checkStuck(livingAttacker);
        }
        service.checkStuck(target);

        // Camping detection
        if (attacker instanceof ServerPlayer player) {
            service.checkCamping(player, target);
        }

        // Burst tracking and HP stats
        if (level instanceof ServerLevel) {
            FightSessionService.INSTANCE.registerBurstDamage(room, amount);
            FightSessionService.INSTANCE.registerHpAfterHit(room, target, hpAfter);
        }
    }

    public void logMiss(Level level, Entity attacker, Vec3 impactPos, String impactType) {
        if (level.isClientSide()) return;
        String room = service.resolveRoom((ServerLevel) level, BlockPos.containing(impactPos));
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
        service.appendLine("hits.ndjson", line);

        if (attacker instanceof ServerPlayer player) {
            DamageTrackingService.INSTANCE.registerWeaponMiss(player);
        }
    }

    public void logSkillCast(LivingEntity caster, String skillId) {
        if (caster.level().isClientSide()) return;
        String room = service.resolveRoom((ServerLevel) caster.level(), caster.blockPosition());
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
        String room = service.resolveRoom(level, boss.blockPosition());
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
        ).ifPresent(result -> service.appendLine("phases.ndjson", result.toJson()));
    }

    public void logDeath(Level level, LivingEntity entity, DamageSource source) {
        if (level.isClientSide()) return;
        String room = service.resolveRoom((ServerLevel) level, entity.blockPosition());
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

        // DuckDB PRIMARY, NDJSON fallback
        if (DuckDBTelemetryService.INSTANCE.isEnabled()) {
            DuckDBTelemetryService.INSTANCE.logDeath(room, level.dimension().location().toString(),
                entity.getName().getString(), EntityTypeName.of(entity),
                source.getMsgId(), ttkFirstHit, ttkSpawn);
        }
        if (DuckDBConfig.NDJSON_FALLBACK || !DuckDBTelemetryService.INSTANCE.isEnabled()) {
            service.appendLine("deaths.ndjson", line);
        }

        // Aggregate death position for heatmap
        HeatmapService.INSTANCE.recordDeath(room, entity.blockPosition());

        // Register TTK in FightSessionService
        if (level instanceof ServerLevel && ttkFirstHit != null) {
            FightSessionService.INSTANCE.registerTTK(room, entity, ttkFirstHit);
        }

        // Get minion damage stats
        var minionStats = DamageTrackingService.INSTANCE.removeMinionStats(entity.getUUID());
        if (minionStats.isPresent() && !(entity instanceof ServerPlayer)) {
            var stats = minionStats.get();
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
            service.appendLine("minions.ndjson", minionLine);
        }

        BossPhaseService.INSTANCE.cleanupEntity(entity.getUUID());
    }

    public void logHeal(Level level, LivingEntity entity, double amount, String source) {
        if (level.isClientSide()) return;
        String room = service.resolveRoom((ServerLevel) level, entity.blockPosition());
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

        // DuckDB PRIMARY, NDJSON fallback
        if (DuckDBTelemetryService.INSTANCE.isEnabled()) {
            DuckDBTelemetryService.INSTANCE.logHeal(room, level.dimension().location().toString(),
                entity.getName().getString(), EntityTypeName.of(entity),
                amount, hpBefore, hpAfter, source);
        }
        if (DuckDBConfig.NDJSON_FALLBACK || !DuckDBTelemetryService.INSTANCE.isEnabled()) {
            service.appendLine("heals.ndjson", line);
        }

        DamageTrackingService.INSTANCE.registerRoomHeal(room, entity, amount);
    }

    public void logSpawn(ServerLevel level, LivingEntity entity, String reason) {
        mobSpawnTime.put(entity.getUUID(), System.currentTimeMillis());
        EntityTrackingService.INSTANCE.registerSpawn(entity.getUUID(),
                level.dimension().location().toString(), entity.position());
        String room = service.resolveRoom(level, entity.blockPosition());
        boolean failed = spawnInSolid(level, entity) ||
                entity.getY() < level.getMinBuildHeight() ||
                entity.getY() > level.getMaxBuildHeight() + 4;

        String line = "{\"ts\":\"" + Instant.now() + "\","
                + "\"room\":\"" + TelemetryJson.escape(room) + "\","
                + "\"world\":\"" + level.dimension().location() + "\","
                + "\"entity\":\"" + TelemetryJson.escape(entity.getName().getString()) + "\","
                + "\"entityType\":\"" + TelemetryJson.escape(EntityTypeName.of(entity)) + "\","
                + "\"reason\":\"" + TelemetryJson.escape(reason) + "\","
                + "\"spawnFail\":" + failed
                + "}";

        // DuckDB PRIMARY, NDJSON fallback
        if (DuckDBTelemetryService.INSTANCE.isEnabled()) {
            DuckDBTelemetryService.INSTANCE.logSpawn(room, level.dimension().location().toString(),
                entity.getName().getString(), EntityTypeName.of(entity),
                reason, failed, entity.getX(), entity.getY(), entity.getZ());
        }
        if (DuckDBConfig.NDJSON_FALLBACK || !DuckDBTelemetryService.INSTANCE.isEnabled()) {
            service.appendLine("spawns.ndjson", line);
        }

        // Track minion spawn for concurrent count
        if (entity instanceof Mob && !(entity instanceof ServerPlayer) &&
                entity.getMaxHealth() < service.getSettings().bossHpThreshold()) {
            MinionService.INSTANCE.recordSpawn(room, entity.getUUID());
        }
    }

    public void cleanupEntity(UUID entityId) {
        mobSpawnTime.remove(entityId);
        mobFirstHit.remove(entityId);
    }

    // ===== Utility Methods =====

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

    private static boolean spawnInSolid(ServerLevel level, LivingEntity entity) {
        return !level.noCollision(entity);
    }

    private static final class EntityTypeName {
        static String of(Entity entity) {
            return entity.getType().toShortString();
        }
    }
}
