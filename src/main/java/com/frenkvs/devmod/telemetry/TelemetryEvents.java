package com.frenkvs.devmod.telemetry;

import com.frenkvs.devmod.Config;
import com.frenkvs.devmod.DevMod;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;

import java.util.UUID;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
// EntityJoinLevelEvent handling moved to DeferredEntityProcessor for performance
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@SuppressWarnings("null") // Minecraft/NeoForge API null-safety (Direction, BlockPos, Blocks)
@EventBusSubscriber(modid = DevMod.MODID)
public class TelemetryEvents {
    private static final Logger LOGGER = LogUtils.getLogger();
    // PERFORMANCE FIX: Run telemetry every 20 ticks (1 second) instead of every tick
    private static int telemetryTickCounter = 0;

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        TelemetryService.INSTANCE.reload(event.getServer());
        // Load saved mob configurations
        com.frenkvs.devmod.MobConfigManager.load();
        // Load user-defined presets (client-side but load early for integrated server)
        com.frenkvs.devmod.MobPresetManager.load();
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        // Clear deferred processor queue
        DeferredEntityProcessor.INSTANCE.clear();

        // PERFORMANCE FIX: Gracefully shutdown async telemetry writer
        // Ensures all pending writes are flushed before server shutdown
        TelemetryService.INSTANCE.shutdown();
    }

    @SubscribeEvent
    // ServerTickEvent is abstract; use concrete Pre variant.
    public static void onServerTick(ServerTickEvent.Pre event) {
        // Cleanup expired hit context entries EVERY tick (critical for hit consistency)
        com.frenkvs.devmod.HitContext.cleanup();

        // PERFORMANCE FIX: Process deferred entity spawns EVERY tick
        // This distributes spawn processing load across ticks to prevent TPS drops
        DeferredEntityProcessor.INSTANCE.tickSpawnQueue();

        // PERFORMANCE FIX: Run telemetry operations based on config interval (default 20 ticks = 1 second)
        // Reduces CPU usage by 95% for telemetry background tasks
        telemetryTickCounter++;
        int tickInterval = Config.TELEMETRY_TICK_INTERVAL.get();
        if (telemetryTickCounter >= tickInterval) {
            telemetryTickCounter = 0;

            TelemetryService.INSTANCE.tickFights();
            TelemetryService.INSTANCE.tickPerformance(event.getServer());
            if (event.getServer().overworld() != null) {
                TelemetryService.INSTANCE.tickAggro(event.getServer().overworld());
                // VOXEL-LAB M52: Entity Count per Room
                com.frenkvs.devmod.telemetry.room.RoomEntityCounter.INSTANCE.tick(event.getServer().overworld());
            }
            TelemetryService.INSTANCE.tickSkills(System.currentTimeMillis());

            // WORKAROUND: Track players via ServerTick instead of PlayerTick
            // This avoids the PlayerTickEvent registration issue
            for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
                if (!player.level().isClientSide()) {
                    TelemetryService.INSTANCE.trackPlayerRoom(player);
                    TelemetryService.INSTANCE.checkOutOfBounds(player);
                    // M13: Check for invisible collision (barrier blocks, etc.)
                    checkInvisibleCollisions(player);
                    // M10: Track movement for desire lines analysis
                    String roomId = com.frenkvs.devmod.telemetry.room.RoomService.INSTANCE
                        .resolveRoom((net.minecraft.server.level.ServerLevel) player.level(), player.blockPosition());
                    com.frenkvs.devmod.telemetry.spatial.DesireLinesService.INSTANCE
                        .recordMovement(player.getUUID(), roomId, player.position());
                    // M41: Track dungeon runs
                    com.frenkvs.devmod.telemetry.dungeon.DungeonRunService.INSTANCE
                        .trackPlayer(player, (net.minecraft.server.level.ServerLevel) player.level());
                    // M47: Track backtracking
                    com.frenkvs.devmod.telemetry.spatial.BacktrackingService.INSTANCE
                        .recordPosition(player.getUUID(), roomId, player.position());
                }
            }
        }
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        TelemetryReloadCommand.register(event.getDispatcher());
    }

    // NOTE: Server-side PlayerTickEvent is not available in NeoForge.
    // Player tracking is handled in onServerTick() by iterating over all players.
    // This is actually more efficient as it runs at configurable intervals.

    @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.LOWEST, receiveCanceled = true)
    public static void onDamage(LivingIncomingDamageEvent event) {
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide()) return;

        DamageSource source = event.getSource();
        Entity attacker = source.getEntity();
        double hpBefore = target.getHealth();
        double hpAfter = Math.max(0, hpBefore - event.getAmount());
        double distance = attacker != null ? attacker.distanceTo(target) : -1;

        // Retrieve body part AND armor pen bonus from context (already calculated by DamageHandler)
        // This ensures 100% consistency between damage calculation and telemetry logging
        String part = "UNKNOWN";
        float armorPenBonus = 0f;
        var hitInfo = com.frenkvs.devmod.HitContext.retrieve(target);
        if (hitInfo != null) {
            part = hitInfo.bodyPart().name();
            armorPenBonus = hitInfo.armorPenBonus();
        } else if (attacker instanceof LivingEntity livingAttacker) {
            // Fallback: calculate body part if context not available (non-combat damage)
            LOGGER.debug("[Telemetry] HitContext miss for {}, using fallback raycast", target.getUUID());
            part = com.frenkvs.devmod.HitHelper.rayTraceBodyPartAABB(livingAttacker, target).name();
            // No armor pen data available in fallback
        }

        // MODDED COMPATIBILITY: Log even if event is canceled (Better Combat, other combat mods)
        // This ensures we don't lose telemetry data when mods cancel events
        TelemetryService.INSTANCE.logHit(target.level(), attacker, target, source, event.getAmount(), hpBefore, hpAfter, part, distance, armorPenBonus);

        // M41: Track damage for dungeon runs
        if (target instanceof ServerPlayer player) {
            com.frenkvs.devmod.telemetry.dungeon.DungeonRunService.INSTANCE.onDamageTaken(player, event.getAmount());
        }
        if (attacker instanceof ServerPlayer player) {
            com.frenkvs.devmod.telemetry.dungeon.DungeonRunService.INSTANCE.onDamageDealt(player, event.getAmount());
        }

        // Mark damage-over-time effects as "hit" (poison, wither, etc.)
        if (attacker == null && source.type().msgId().contains("magic")) {
            for (var effect : target.getActiveEffects()) {
                String effectId = effect.getEffect().value().getDescriptionId();
                if (effectId.contains("poison") || effectId.contains("wither") || effectId.contains("harm")) {
                    String skillId = "effect_" + effectId;
                    TelemetryService.INSTANCE.logSkillHit(target, skillId);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide()) return;
        TelemetryService.INSTANCE.logDeath(entity.level(), entity, event.getSource());

        // M41: Track player death in dungeon
        if (entity instanceof ServerPlayer player) {
            com.frenkvs.devmod.telemetry.dungeon.DungeonRunService.INSTANCE.onPlayerDeath(player);
        }

        // M41: Track enemy kills for dungeon runs
        Entity killer = event.getSource().getEntity();
        if (killer instanceof ServerPlayer player && !(entity instanceof ServerPlayer)) {
            String enemyType = entity.getType().getDescriptionId();
            com.frenkvs.devmod.telemetry.dungeon.DungeonRunService.INSTANCE.onEnemyKill(player, enemyType);
        }
    }

    @SubscribeEvent
    public static void onHeal(LivingHealEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide()) return;
        String source = "unknown";
        if (entity instanceof ServerPlayer player) {
            if (!player.getMainHandItem().isEmpty()) {
                source = player.getMainHandItem().getItem().toString();
            } else {
                source = "player";
            }
        }
        TelemetryService.INSTANCE.logHeal(entity.level(), entity, event.getAmount(), source);

        // Mark regeneration/healing effects as "hit" (they successfully healed)
        for (var effect : entity.getActiveEffects()) {
            String effectId = effect.getEffect().value().getDescriptionId();
            if (effectId.contains("regeneration") || effectId.contains("instant_health")) {
                String skillId = "effect_" + effectId;
                TelemetryService.INSTANCE.logSkillHit(entity, skillId);
            }
        }
    }

    // NOTE: EntityJoinLevelEvent spawn logging moved to DeferredEntityProcessor
    // This prevents TPS drops when structures generate many entities at once.
    // See GlobalMobEvents.onEntityJoin() -> DeferredEntityProcessor.queueSpawn()

    @SubscribeEvent
    public static void onEntityLeave(EntityLeaveLevelEvent event) {
        if (event.getLevel().isClientSide()) return;

        // MEMORY LEAK FIX: Cleanup all 12 tracker maps when entity is removed
        // Without this, trackers accumulate indefinitely for despawned/killed entities
        UUID entityId = event.getEntity().getUUID();
        TelemetryService.INSTANCE.cleanupEntity(entityId);
    }

    @SubscribeEvent
    public static void onProjectileImpact(ProjectileImpactEvent event) {
        if (!(event.getEntity() instanceof Projectile projectile)) return;
        if (projectile.level().isClientSide()) return;

        Entity owner = projectile.getOwner();
        var result = event.getRayTraceResult();
        String impactType = result.getType().name();
        var hitPos = result.getLocation();

        // Miss/block impacts: count as miss for aggregates, plus log for projectiles.
        if (result.getType() != net.minecraft.world.phys.HitResult.Type.ENTITY) {
            TelemetryService.INSTANCE.logMiss(projectile.level(), owner, hitPos, impactType);
        }

        String line = "{\"ts\":\"" + java.time.Instant.now() + "\","
                + "\"world\":\"" + projectile.level().dimension().location() + "\","
                + "\"impact\":\"" + impactType + "\","
                + "\"pos\":[" + hitPos.x + "," + hitPos.y + "," + hitPos.z + "],"
                + "\"owner\":\"" + (owner != null ? TelemetryJson.escape(owner.getName().getString()) : "unknown") + "\"}";
        TelemetryService.INSTANCE.appendProjectile(line);
    }

    // ===== SPRINT 1: Spatial Metrics Triggers =====

    /**
     * M14: Parkour Fall Detection
     * Triggers when a player falls more than 3 blocks.
     * Records fall position and distance for level design analysis.
     */
    @SubscribeEvent
    public static void onPlayerFall(LivingFallEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.level().isClientSide()) return;

        float fallDistance = event.getDistance();
        // Only track significant falls (3+ blocks)
        if (fallDistance < 3.0f) return;

        BlockPos pos = player.blockPosition();

        // Use TelemetryService public API (handles room resolution and logging)
        TelemetryService.INSTANCE.logParkourFall(player, fallDistance, pos);

        LOGGER.debug("[Telemetry] Parkour fall detected: {} fell {} blocks at {}",
                player.getGameProfile().getName(), fallDistance, pos);
    }

    /**
     * M13: Invisible Collision Detection
     * Detects when a player is pushing against an invisible barrier or collision.
     * Called from ServerTick when player has horizontal collision but isn't moving.
     */
    public static void checkInvisibleCollisions(ServerPlayer player) {
        // Player must be pushing against something (horizontal collision) but not jumping/falling
        if (!player.horizontalCollision || player.verticalCollision) return;

        // Get the direction player is facing
        BlockPos playerPos = player.blockPosition();
        BlockPos checkPos = playerPos.relative(player.getDirection());

        BlockState state = player.level().getBlockState(checkPos);

        // Check for barrier blocks or blocks with invisible collision
        boolean isInvisibleCollision = state.is(Blocks.BARRIER) ||
                state.is(Blocks.STRUCTURE_VOID) ||
                state.is(Blocks.LIGHT); // Light blocks have collision but are invisible

        if (isInvisibleCollision) {
            // Use TelemetryService public API (handles room resolution and logging)
            TelemetryService.INSTANCE.logInvisibleCollision(player, checkPos);

            LOGGER.debug("[Telemetry] Invisible collision: {} hit {} at {}",
                    player.getGameProfile().getName(), state.getBlock().getName().getString(), checkPos);
        }
    }
}
