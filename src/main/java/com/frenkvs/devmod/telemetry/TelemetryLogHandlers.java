package com.frenkvs.devmod.telemetry;

import com.devmod.arena.api.ArenaHandle;
import com.frenkvs.devmod.endurance.EnduranceQuestManager;
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
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

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
        FightSessionService.CombatContext context = resolveCombatContext(attacker, target);
        String contextJson = formatContextFields(context);

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
                + contextJson
                + "}";

        // DuckDB PRIMARY, NDJSON fallback
        if (DuckDBTelemetryService.INSTANCE.isEnabled()) {
            DuckDBTelemetryService.INSTANCE.logHit(room, level.dimension().location().toString(),
                context != null ? context.templateId() : null,
                context != null ? context.templateVersion() : null,
                context != null ? context.policyId() : null,
                context != null ? context.policyVersion() : null,
                context != null ? context.arenaId() : null,
                context != null ? context.sessionId() : null,
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
                attacker, target, hpAfter <= 0, context);
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
        @Nonnull Vec3 safeImpactPos = Objects.requireNonNull(impactPos, "impactPos");
        BlockPos impactBlock = BlockPos.containing(safeImpactPos.x, safeImpactPos.y, safeImpactPos.z);
        String room = service.resolveRoom((ServerLevel) level, impactBlock);
        String attackerName = attacker != null ? attacker.getName().getString() : "unknown";
        String attackerType = attacker != null ? EntityTypeName.of(attacker) : "unknown";
        double distance = attacker != null ? attacker.position().distanceTo(safeImpactPos) : -1;

        String line = "{\"ts\":\"" + Instant.now() + "\","
                + "\"room\":\"" + TelemetryJson.escape(room) + "\","
                + "\"world\":\"" + level.dimension().location() + "\","
                + "\"attacker\":\"" + TelemetryJson.escape(attackerName) + "\","
                + "\"attackerType\":\"" + TelemetryJson.escape(attackerType) + "\","
                + "\"dmg\":0,"
                + "\"miss\":true,"
                + "\"impact\":\"" + TelemetryJson.escape(impactType) + "\","
                + "\"pos\":[" + safeImpactPos.x + "," + safeImpactPos.y + "," + safeImpactPos.z + "],"
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
        FightSessionService.CombatContext context = resolveCombatContext(source.getEntity(), entity);
        String contextJson = formatContextFields(context);
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
                + contextJson
                + "}";

        // DuckDB PRIMARY, NDJSON fallback
        if (DuckDBTelemetryService.INSTANCE.isEnabled()) {
            DuckDBTelemetryService.INSTANCE.logDeath(room, level.dimension().location().toString(),
                context != null ? context.templateId() : null,
                context != null ? context.templateVersion() : null,
                context != null ? context.policyId() : null,
                context != null ? context.policyVersion() : null,
                context != null ? context.arenaId() : null,
                context != null ? context.sessionId() : null,
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
        FightSessionService.CombatContext context = resolveCombatContext(entity, null);
        String contextJson = formatContextFields(context);
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
                + contextJson
                + "}";

        // DuckDB PRIMARY, NDJSON fallback
        if (DuckDBTelemetryService.INSTANCE.isEnabled()) {
            DuckDBTelemetryService.INSTANCE.logHeal(room, level.dimension().location().toString(),
                context != null ? context.templateId() : null,
                context != null ? context.templateVersion() : null,
                context != null ? context.policyId() : null,
                context != null ? context.policyVersion() : null,
                context != null ? context.arenaId() : null,
                context != null ? context.sessionId() : null,
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
        FightSessionService.CombatContext context = resolveCombatContext(entity, null);
        String contextJson = formatContextFields(context);
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
                + contextJson
                + "}";

        // DuckDB PRIMARY, NDJSON fallback
        if (DuckDBTelemetryService.INSTANCE.isEnabled()) {
            DuckDBTelemetryService.INSTANCE.logSpawn(room, level.dimension().location().toString(),
                context != null ? context.templateId() : null,
                context != null ? context.templateVersion() : null,
                context != null ? context.policyId() : null,
                context != null ? context.policyVersion() : null,
                context != null ? context.arenaId() : null,
                context != null ? context.sessionId() : null,
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

    private static @Nullable FightSessionService.CombatContext resolveCombatContext(@Nullable Entity primary,
                                                                                   @Nullable Entity secondary) {
        FightSessionService.CombatContext context = resolveContextFromPlayer(primary);
        if (context != null) {
            return context;
        }
        context = resolveContextFromPlayer(secondary);
        if (context != null) {
            return context;
        }
        context = resolveContextFromEntity(primary);
        if (context != null) {
            return context;
        }
        return resolveContextFromEntity(secondary);
    }

    private static @Nullable FightSessionService.CombatContext resolveContextFromPlayer(@Nullable Entity entity) {
        if (!(entity instanceof ServerPlayer player)) {
            return null;
        }
        return EnduranceQuestManager.INSTANCE.getActiveSession(player)
            .map(session -> {
                UUID sessionId = session.getQuest() != null ? session.getQuest().getQuestId() : null;
                UUID arenaId = null;
                ArenaHandle handle = session.getArenaHandle();
                if (handle != null) {
                    arenaId = handle.arenaId();
                } else if (session.getArena() != null) {
                    arenaId = session.getArena().getId();
                }
                return new FightSessionService.CombatContext(
                    session.getTemplateId(),
                    session.getTemplateVersion(),
                    session.getPolicyId(),
                    session.getPolicyVersion(),
                    arenaId,
                    sessionId
                );
            })
            .orElse(null);
    }

    private static @Nullable FightSessionService.CombatContext resolveContextFromEntity(@Nullable Entity entity) {
        if (!(entity instanceof LivingEntity living)) {
            return null;
        }
        CompoundTag data = living.getPersistentData();
        boolean hasContext = data.hasUUID("endurance_quest_id")
            || data.hasUUID("endurance_arena_id")
            || data.contains("endurance_template_id")
            || data.contains("endurance_policy_id");
        if (!hasContext) {
            return null;
        }
        String templateId = data.contains("endurance_template_id") ? data.getString("endurance_template_id") : null;
        Integer templateVersion = data.contains("endurance_template_version")
            ? data.getInt("endurance_template_version")
            : null;
        String policyId = data.contains("endurance_policy_id") ? data.getString("endurance_policy_id") : null;
        Integer policyVersion = data.contains("endurance_policy_version")
            ? data.getInt("endurance_policy_version")
            : null;
        UUID arenaId = data.hasUUID("endurance_arena_id") ? data.getUUID("endurance_arena_id") : null;
        UUID sessionId = data.hasUUID("endurance_quest_id") ? data.getUUID("endurance_quest_id") : null;

        return new FightSessionService.CombatContext(
            templateId,
            templateVersion,
            policyId,
            policyVersion,
            arenaId,
            sessionId
        );
    }

    private static String formatContextFields(@Nullable FightSessionService.CombatContext context) {
        if (context == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        if (context.templateId() != null && !context.templateId().isBlank()) {
            sb.append(",\"templateId\":\"").append(TelemetryJson.escape(context.templateId())).append("\"");
        }
        if (context.templateVersion() != null) {
            sb.append(",\"templateVersion\":").append(context.templateVersion());
        }
        if (context.policyId() != null && !context.policyId().isBlank()) {
            sb.append(",\"policyId\":\"").append(TelemetryJson.escape(context.policyId())).append("\"");
        }
        if (context.policyVersion() != null) {
            sb.append(",\"policyVersion\":").append(context.policyVersion());
        }
        if (context.arenaId() != null) {
            sb.append(",\"arenaId\":\"").append(context.arenaId()).append("\"");
        }
        if (context.sessionId() != null) {
            sb.append(",\"sessionId\":\"").append(context.sessionId()).append("\"");
        }
        return sb.toString();
    }

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
        @Nonnull TagKey<DamageType> fireTag = Objects.requireNonNull(DamageTypeTags.IS_FIRE, "IS_FIRE");
        @Nonnull TagKey<DamageType> fallTag = Objects.requireNonNull(DamageTypeTags.IS_FALL, "IS_FALL");
        @Nonnull TagKey<DamageType> drownTag = Objects.requireNonNull(DamageTypeTags.IS_DROWNING, "IS_DROWNING");
        @Nonnull TagKey<DamageType> freezeTag = Objects.requireNonNull(DamageTypeTags.IS_FREEZING, "IS_FREEZING");
        @Nonnull TagKey<DamageType> lightningTag = Objects.requireNonNull(DamageTypeTags.IS_LIGHTNING, "IS_LIGHTNING");
        @Nonnull TagKey<DamageType> explosionTag = Objects.requireNonNull(DamageTypeTags.IS_EXPLOSION, "IS_EXPLOSION");
        @Nonnull TagKey<DamageType> projectileTag = Objects.requireNonNull(DamageTypeTags.IS_PROJECTILE, "IS_PROJECTILE");
        if (source.is(fireTag)) return "fire";
        if (source.is(fallTag)) return "fall";
        if (source.is(drownTag)) return "drown";
        if (source.is(freezeTag)) return "freeze";
        if (source.is(lightningTag)) return "lightning";
        if (source.is(explosionTag)) return "explosion";
        if (source.is(projectileTag)) return "projectile";
        return source.type().msgId();
    }

    private static boolean spawnInSolid(ServerLevel level, @Nonnull LivingEntity entity) {
        return !level.noCollision(Objects.requireNonNull(entity, "entity"));
    }

    private static final class EntityTypeName {
        static String of(Entity entity) {
            return entity.getType().toShortString();
        }
    }
}
