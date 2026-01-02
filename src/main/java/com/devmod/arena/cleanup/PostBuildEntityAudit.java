package com.devmod.arena.cleanup;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

/**
 * P1: Post-build residual entity audit for arena construction.
 *
 * After an arena is built, this audits the region for entities that shouldn't be there:
 * - Item drops from block placement
 * - Residual mobs from previous builds
 * - Any other non-player entities
 *
 * Provides options to:
 * - Report violations only (default)
 * - Clean up residual entities (rollback option)
 */
public class PostBuildEntityAudit {

    private static final Logger LOGGER = LoggerFactory.getLogger(PostBuildEntityAudit.class);

    /**
     * Expected entity types that are allowed during/after build.
     * Typically just players.
     */
    private static final Set<EntityType<?>> ALLOWED_ENTITY_TYPES = Set.of(
        EntityType.PLAYER
    );

    /**
     * Audit result record.
     */
    public record AuditResult(
        boolean passed,
        int totalEntitiesFound,
        int residualEntities,
        int itemsFound,
        int mobsFound,
        List<EntityViolation> violations,
        long auditDurationMs
    ) {
        public static AuditResult success(long durationMs) {
            return new AuditResult(true, 0, 0, 0, 0, List.of(), durationMs);
        }

        public static AuditResult empty() {
            return success(0);
        }

        public boolean hasResiduals() {
            return residualEntities > 0;
        }
    }

    /**
     * Individual entity violation.
     */
    public record EntityViolation(
        UUID entityId,
        String entityType,
        BlockPos position,
        String reason
    ) {}

    /**
     * Audit configuration.
     */
    public record AuditConfig(
        boolean removeItems,
        boolean removeMobs,
        boolean logViolations,
        int maxViolationsToLog
    ) {
        public static AuditConfig defaults() {
            return new AuditConfig(false, false, true, 10);
        }

        public static AuditConfig withCleanup() {
            return new AuditConfig(true, true, true, 10);
        }

        public static AuditConfig silent() {
            return new AuditConfig(false, false, false, 0);
        }
    }

    private final AuditConfig config;

    public PostBuildEntityAudit() {
        this(AuditConfig.defaults());
    }

    public PostBuildEntityAudit(AuditConfig config) {
        this.config = config;
    }

    /**
     * Audits the arena region for residual entities after build.
     *
     * @param level The server level
     * @param minX Arena min X coordinate
     * @param minY Arena min Y coordinate
     * @param minZ Arena min Z coordinate
     * @param maxX Arena max X coordinate
     * @param maxY Arena max Y coordinate
     * @param maxZ Arena max Z coordinate
     * @return Audit result with violations
     */
    public AuditResult audit(ServerLevel level, int minX, int minY, int minZ,
                              int maxX, int maxY, int maxZ) {
        long startNs = System.nanoTime();

        AABB bounds = new AABB(minX, minY, minZ, maxX + 1, maxY + 1, maxZ + 1);
        List<Entity> entities = level.getEntities((Entity) null, bounds, e -> true);

        if (entities.isEmpty()) {
            long durationMs = (System.nanoTime() - startNs) / 1_000_000;
            return AuditResult.success(durationMs);
        }

        List<EntityViolation> violations = new ArrayList<>();
        int itemsFound = 0;
        int mobsFound = 0;
        int residualCount = 0;
        List<Entity> toRemove = new ArrayList<>();

        for (Entity entity : entities) {
            // Skip allowed entity types (players)
            if (ALLOWED_ENTITY_TYPES.contains(entity.getType()) || entity instanceof Player) {
                continue;
            }

            residualCount++;
            BlockPos pos = entity.blockPosition();

            if (entity instanceof ItemEntity) {
                itemsFound++;
                violations.add(new EntityViolation(
                    entity.getUUID(),
                    EntityType.getKey(entity.getType()).toString(),
                    pos,
                    "Item drop found after build"
                ));
                if (config.removeItems()) {
                    toRemove.add(entity);
                }
            } else {
                mobsFound++;
                violations.add(new EntityViolation(
                    entity.getUUID(),
                    EntityType.getKey(entity.getType()).toString(),
                    pos,
                    "Mob/entity found after build"
                ));
                if (config.removeMobs()) {
                    toRemove.add(entity);
                }
            }
        }

        // Remove entities if configured
        int removed = 0;
        for (Entity entity : toRemove) {
            entity.discard();
            removed++;
        }

        // Log violations if configured
        if (config.logViolations() && !violations.isEmpty()) {
            int toLog = Math.min(violations.size(), config.maxViolationsToLog());
            LOGGER.warn("[PostBuildAudit] Found {} residual entities ({} items, {} mobs) in build region",
                residualCount, itemsFound, mobsFound);
            for (int i = 0; i < toLog; i++) {
                EntityViolation v = violations.get(i);
                LOGGER.warn("[PostBuildAudit]   - {} at {} ({})",
                    v.entityType(), v.position(), v.reason());
            }
            if (violations.size() > toLog) {
                LOGGER.warn("[PostBuildAudit]   ... and {} more violations",
                    violations.size() - toLog);
            }
            if (removed > 0) {
                LOGGER.info("[PostBuildAudit] Cleaned up {} residual entities", removed);
            }
        }

        long durationMs = (System.nanoTime() - startNs) / 1_000_000;
        return new AuditResult(
            residualCount == 0,
            entities.size(),
            residualCount,
            itemsFound,
            mobsFound,
            List.copyOf(violations),
            durationMs
        );
    }

    /**
     * Audits and optionally cleans up residual entities.
     * Convenience method that enables cleanup mode.
     */
    public AuditResult auditAndCleanup(ServerLevel level, int minX, int minY, int minZ,
                                        int maxX, int maxY, int maxZ) {
        PostBuildEntityAudit cleanupAudit = new PostBuildEntityAudit(AuditConfig.withCleanup());
        return cleanupAudit.audit(level, minX, minY, minZ, maxX, maxY, maxZ);
    }

    /**
     * Quick check if any residual entities exist (no detailed violations).
     */
    public static boolean hasResidualEntities(ServerLevel level, int minX, int minY, int minZ,
                                               int maxX, int maxY, int maxZ) {
        AABB bounds = new AABB(minX, minY, minZ, maxX + 1, maxY + 1, maxZ + 1);
        List<Entity> entities = level.getEntities((Entity) null, bounds,
            e -> !(e instanceof Player) && !ALLOWED_ENTITY_TYPES.contains(e.getType()));
        return !entities.isEmpty();
    }
}
