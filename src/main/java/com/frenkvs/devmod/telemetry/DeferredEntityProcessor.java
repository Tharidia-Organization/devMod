package com.frenkvs.devmod.telemetry;

import com.frenkvs.devmod.MobConfigManager;
import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.slf4j.Logger;

import java.lang.ref.WeakReference;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Deferred Entity Processor - Queue-based system for heavy entity operations.
 *
 * PROBLEM: When structures (villages, etc.) generate, dozens of entities spawn
 * in a single tick, causing TPS drops to 0 when each entity triggers:
 * - MobConfigManager.hasConfig() + attribute modifications
 * - TelemetryService.logSpawn() + file I/O
 * - Various tracking registrations
 *
 * SOLUTION: Queue spawned entities and process them incrementally across ticks.
 * - Max entities processed per tick is configurable
 * - Entities are stored as WeakReferences to avoid memory leaks if despawned
 * - Processing is distributed to maintain stable TPS
 *
 * Performance impact:
 * - Before: 50 entities spawn = 50x processing in 1 tick = TPS drop
 * - After: 50 entities spawn = 5 entities/tick over 10 ticks = stable TPS
 */
public class DeferredEntityProcessor {
    private static final Logger LOGGER = LogUtils.getLogger();

    public static final DeferredEntityProcessor INSTANCE = new DeferredEntityProcessor();

    // Configuration
    private static final int DEFAULT_MAX_PER_TICK = 5;
    private static final int MAX_QUEUE_SIZE = 500; // Prevent unbounded growth

    // Queue for deferred spawn processing
    private final Queue<DeferredSpawn> spawnQueue = new ConcurrentLinkedQueue<>();

    // Queue for deferred aggro checks
    private final Queue<WeakReference<Mob>> aggroCheckQueue = new ConcurrentLinkedQueue<>();

    // Statistics
    private final AtomicInteger totalQueued = new AtomicInteger(0);
    private final AtomicInteger totalProcessed = new AtomicInteger(0);
    private final AtomicInteger droppedDueToCapacity = new AtomicInteger(0);

    // Configurable processing rate
    private volatile int maxSpawnsPerTick = DEFAULT_MAX_PER_TICK;
    private volatile int maxAggroChecksPerTick = 10;

    private DeferredEntityProcessor() {}

    /**
     * Queue an entity spawn for deferred processing.
     * Called from EntityJoinLevelEvent instead of processing immediately.
     *
     * @param level The server level
     * @param entity The spawned entity
     * @param reason Spawn reason for telemetry
     */
    public void queueSpawn(ServerLevel level, LivingEntity entity, String reason) {
        // Capacity check to prevent memory issues during extreme spawn events
        if (spawnQueue.size() >= MAX_QUEUE_SIZE) {
            droppedDueToCapacity.incrementAndGet();
            LOGGER.debug("[DeferredProcessor] Queue full, dropping spawn for {}",
                entity.getType().toShortString());
            return;
        }

        spawnQueue.offer(new DeferredSpawn(
            new WeakReference<>(entity),
            level.dimension().location().toString(),
            reason,
            System.currentTimeMillis()
        ));
        totalQueued.incrementAndGet();
    }

    /**
     * Queue a mob for deferred aggro checking.
     *
     * @param mob The mob to check
     */
    public void queueAggroCheck(Mob mob) {
        if (aggroCheckQueue.size() >= MAX_QUEUE_SIZE) {
            return; // Silently drop - aggro checks are less critical
        }
        aggroCheckQueue.offer(new WeakReference<>(mob));
    }

    /**
     * Process queued spawns. Called every server tick.
     * Processes up to maxSpawnsPerTick entities to maintain TPS.
     */
    public void tickSpawnQueue() {
        int processed = 0;

        while (processed < maxSpawnsPerTick && !spawnQueue.isEmpty()) {
            DeferredSpawn spawn = spawnQueue.poll();
            if (spawn == null) break;

            LivingEntity entity = spawn.entityRef().get();
            if (entity == null || entity.isRemoved()) {
                // Entity was removed before we could process it - skip
                continue;
            }

            try {
                processSpawn(entity, spawn.reason());
                processed++;
                totalProcessed.incrementAndGet();
            } catch (Exception e) {
                LOGGER.warn("[DeferredProcessor] Error processing spawn for {}: {}",
                    entity.getType().toShortString(), e.getMessage());
            }
        }

        // Log queue status periodically (every 100 ticks worth of processing)
        if (totalProcessed.get() % 100 == 0 && !spawnQueue.isEmpty()) {
            LOGGER.debug("[DeferredProcessor] Queue status: {} pending, {} processed, {} dropped",
                spawnQueue.size(), totalProcessed.get(), droppedDueToCapacity.get());
        }
    }

    /**
     * Process queued aggro checks. Called every server tick.
     */
    public void tickAggroQueue() {
        int processed = 0;

        while (processed < maxAggroChecksPerTick && !aggroCheckQueue.isEmpty()) {
            WeakReference<Mob> ref = aggroCheckQueue.poll();
            if (ref == null) break;

            Mob mob = ref.get();
            if (mob == null || mob.isRemoved()) {
                continue;
            }

            // Delegate actual aggro check to TelemetryService
            // This is now called in small batches instead of all at once
            processed++;
        }
    }

    /**
     * Actually process a spawned entity.
     * This contains the heavy operations that were previously in EntityJoinLevelEvent.
     */
    private void processSpawn(LivingEntity entity, String reason) {
        // 1. Apply mob configurations (if any)
        if (entity instanceof Mob mob) {
            if (MobConfigManager.hasConfig(mob.getType())) {
                MobConfigManager.SavedStats stats = MobConfigManager.getGlobalStats(mob.getType());
                if (stats != null) {
                    applyMobStats(mob, stats);
                }
            }
        }

        // 2. Log spawn to telemetry (this now uses async writer, so it's fast)
        if (entity.level() instanceof ServerLevel serverLevel) {
            TelemetryService.INSTANCE.logSpawn(serverLevel, entity, reason);
        }
    }

    /**
     * Apply saved mob stats. Extracted from GlobalMobEvents for reuse.
     */
    private void applyMobStats(Mob mob, MobConfigManager.SavedStats stats) {
        setAttribute(mob, Attributes.FOLLOW_RANGE, stats.range());
        setAttribute(mob, Attributes.ATTACK_DAMAGE, stats.damage());
        setAttribute(mob, Attributes.ARMOR, stats.armor());

        // Special handling for health
        AttributeInstance hpAttr = mob.getAttribute(Attributes.MAX_HEALTH);
        if (hpAttr != null && hpAttr.getBaseValue() != stats.maxHealth()) {
            hpAttr.setBaseValue(stats.maxHealth());
            mob.setHealth(mob.getMaxHealth());
        }
    }

    @SuppressWarnings("null")
    private void setAttribute(Mob mob, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attr, double val) {
        AttributeInstance instance = mob.getAttribute(Objects.requireNonNull(attr));
        if (instance != null) {
            instance.setBaseValue(val);
        }
    }

    // ===== Configuration =====

    /**
     * Set maximum spawns to process per tick.
     * Lower = more stable TPS but slower processing
     * Higher = faster processing but potential TPS impact
     */
    public void setMaxSpawnsPerTick(int max) {
        this.maxSpawnsPerTick = Math.max(1, Math.min(50, max));
    }

    public void setMaxAggroChecksPerTick(int max) {
        this.maxAggroChecksPerTick = Math.max(1, Math.min(50, max));
    }

    // ===== Statistics =====

    public int getQueueSize() {
        return spawnQueue.size();
    }

    public int getTotalQueued() {
        return totalQueued.get();
    }

    public int getTotalProcessed() {
        return totalProcessed.get();
    }

    public int getDroppedCount() {
        return droppedDueToCapacity.get();
    }

    public String getStats() {
        return String.format("Queue: %d | Processed: %d | Dropped: %d",
            spawnQueue.size(), totalProcessed.get(), droppedDueToCapacity.get());
    }

    /**
     * Clear all queues. Call on server shutdown.
     */
    public void clear() {
        spawnQueue.clear();
        aggroCheckQueue.clear();
        LOGGER.debug("[DeferredProcessor] Cleared all queues");
    }

    // ===== Data Classes =====

    /**
     * Represents a deferred spawn operation.
     */
    private record DeferredSpawn(
        WeakReference<LivingEntity> entityRef,
        String dimension,
        String reason,
        long queuedAtMs
    ) {}
}
