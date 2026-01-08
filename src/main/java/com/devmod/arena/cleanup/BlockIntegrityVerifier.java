package com.devmod.arena.cleanup;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

import com.devmod.arena.registry.ArenaTemplate;

/**
 * P1: Block integrity verification after arena build.
 *
 * Verifies that blocks were placed correctly by:
 * - Checking expected block count vs actual
 * - Sampling random positions for expected block types
 * - Detecting air blocks where solid blocks were expected
 */
public class BlockIntegrityVerifier {

    private static final Logger LOGGER = LoggerFactory.getLogger(BlockIntegrityVerifier.class);

    /**
     * Verification result.
     */
    public record VerificationResult(
        boolean passed,
        int expectedBlocks,
        int actualNonAirBlocks,
        int airWhereExpectedSolid,
        int sampleChecks,
        int sampleFailures,
        List<BlockViolation> violations,
        long verificationDurationMs
    ) {
        public static VerificationResult success(int expectedBlocks, int actualBlocks, long durationMs) {
            return new VerificationResult(true, expectedBlocks, actualBlocks, 0, 0, 0, List.of(), durationMs);
        }

        public boolean hasIntegrityIssues() {
            return !passed || airWhereExpectedSolid > 0 || sampleFailures > 0;
        }

        public double integrityPercent() {
            if (expectedBlocks <= 0) return 100.0;
            return Math.max(0, Math.min(100, (double) actualNonAirBlocks / expectedBlocks * 100));
        }
    }

    /**
     * Individual block violation.
     */
    public record BlockViolation(
        BlockPos position,
        String expected,
        String actual,
        String reason
    ) {}

    /**
     * Verification configuration.
     */
    public record VerificationConfig(
        boolean enabled,
        int sampleSize,
        double minIntegrityPercent,
        boolean logViolations,
        int maxViolationsToLog
    ) {
        public static VerificationConfig defaults() {
            return new VerificationConfig(true, 100, 95.0, true, 10);
        }

        public static VerificationConfig strict() {
            return new VerificationConfig(true, 200, 99.0, true, 20);
        }

        public static VerificationConfig disabled() {
            return new VerificationConfig(false, 0, 0, false, 0);
        }
    }

    private final VerificationConfig config;

    public BlockIntegrityVerifier() {
        this(VerificationConfig.defaults());
    }

    public BlockIntegrityVerifier(VerificationConfig config) {
        this.config = config;
    }

    /**
     * Verifies block integrity in the arena region.
     *
     * @param level The server level
     * @param minX Arena min X coordinate
     * @param minY Arena min Y coordinate
     * @param minZ Arena min Z coordinate
     * @param maxX Arena max X coordinate
     * @param maxY Arena max Y coordinate
     * @param maxZ Arena max Z coordinate
     * @param expectedBlockCount Expected number of non-air blocks
     * @return Verification result
     */
    public VerificationResult verify(ServerLevel level, int minX, int minY, int minZ,
                                      int maxX, int maxY, int maxZ, int expectedBlockCount) {
        return verify(level, minX, minY, minZ, maxX, maxY, maxZ, expectedBlockCount, null, 0, 0);
    }

    public VerificationResult verify(ServerLevel level, int minX, int minY, int minZ,
                                      int maxX, int maxY, int maxZ, int expectedBlockCount,
                                      @Nullable ArenaTemplate template, int originX, int originZ) {
        if (!config.enabled()) {
            return VerificationResult.success(expectedBlockCount, expectedBlockCount, 0);
        }

        long startNs = System.nanoTime();
        List<BlockViolation> violations = new ArrayList<>();

        // Count actual non-air blocks in the region
        int actualNonAirBlocks = 0;
        int airWhereExpectedSolid = 0;

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    if (!state.isAir()) {
                        actualNonAirBlocks++;
                    }
                }
            }
        }

        // Sample random positions for deeper verification
        int sampleChecks = 0;
        int sampleFailures = 0;

        if (config.sampleSize() > 0 && expectedBlockCount > 0) {
            int volumeX = maxX - minX + 1;
            int volumeY = maxY - minY + 1;
            int volumeZ = maxZ - minZ + 1;

            ThreadLocalRandom random = ThreadLocalRandom.current();
            ArenaTemplate.Floor floor = template != null ? template.floor() : null;
            int floorY = floor != null ? floor.y() : minY;
            int floorMaxY = floor != null ? floor.y() + Math.max(1, floor.thickness()) - 1 : minY;
            ArenaTemplate.Walls walls = template != null ? template.walls() : null;
            boolean wallsEnabled = walls != null && walls.enabled();
            int wallThickness = wallsEnabled ? Math.max(1, walls.thickness()) : 0;
            int wallStartY = wallsEnabled ? walls.startY() : minY;
            int wallEndY = wallsEnabled ? walls.startY() + Math.max(1, walls.height()) - 1 : minY;

            for (int i = 0; i < config.sampleSize(); i++) {
                int x = minX + random.nextInt(volumeX);
                int y = minY + random.nextInt(volumeY);
                int z = minZ + random.nextInt(volumeZ);

                BlockPos pos = new BlockPos(x, y, z);
                BlockState state = level.getBlockState(pos);
                sampleChecks++;

                // Check for unexpected air in floor/wall regions (template-aware when available)
                boolean isFloorLevel = false;
                boolean isWallEdge = false;
                if (template != null && floor != null) {
                    int dx = x - originX;
                    int dz = z - originZ;
                    boolean inFloorBounds = isWithinFloorBounds(dx, dz, template);
                    boolean inShape = isInArenaShape(dx, dz, template);
                    isFloorLevel = inFloorBounds && inShape && y >= floorY && y <= floorMaxY;
                    if (wallsEnabled && y >= wallStartY && y <= wallEndY) {
                        ArenaTemplate.ArenaShape shape = template.arenaShape();
                        if (shape == null) {
                            shape = ArenaTemplate.ArenaShape.RECTANGULAR;
                        }
                        isWallEdge = switch (shape) {
                            case RECTANGULAR -> isOnRectWallEdge(dx, dz, template, wallThickness);
                            case CIRCULAR, RING -> isOnCircularBorder(dx, dz, template, wallThickness);
                        };
                    }
                } else {
                    isFloorLevel = (y == minY);
                    isWallEdge = (x == minX || x == maxX || z == minZ || z == maxZ);
                }

                if ((isFloorLevel || isWallEdge) && state.isAir()) {
                    sampleFailures++;
                    airWhereExpectedSolid++;
                    if (violations.size() < config.maxViolationsToLog()) {
                        violations.add(new BlockViolation(
                            pos,
                            "solid",
                            "air",
                            isFloorLevel ? "Floor position is air" : "Wall position is air"
                        ));
                    }
                }
            }
        }

        // Determine if verification passed
        double integrityPercent = expectedBlockCount > 0
            ? (double) actualNonAirBlocks / expectedBlockCount * 100
            : 100.0;
        double roundedPercent = Math.round(integrityPercent * 10.0) / 10.0;
        boolean passed = integrityPercent >= config.minIntegrityPercent()
            && sampleFailures == 0;

        // Log violations if configured
        if (config.logViolations() && !violations.isEmpty()) {
            LOGGER.warn("[BlockIntegrity] Verification found {} issues:", violations.size());
            for (int i = 0; i < Math.min(violations.size(), config.maxViolationsToLog()); i++) {
                BlockViolation v = violations.get(i);
                LOGGER.warn("[BlockIntegrity]   - {} at {}: expected {}, got {}",
                    v.reason(), v.position(), v.expected(), v.actual());
            }
            if (violations.size() > config.maxViolationsToLog()) {
                LOGGER.warn("[BlockIntegrity]   ... and {} more violations",
                    violations.size() - config.maxViolationsToLog());
            }
        }

        if (!passed) {
            LOGGER.warn("[BlockIntegrity] Verification FAILED: {}% integrity ({}/{} blocks), {} sample failures",
                roundedPercent, actualNonAirBlocks, expectedBlockCount, sampleFailures);
        } else if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("[BlockIntegrity] Verification PASSED: {}% integrity ({}/{} blocks)",
                roundedPercent, actualNonAirBlocks, expectedBlockCount);
        }

        long durationMs = (System.nanoTime() - startNs) / 1_000_000;
        return new VerificationResult(
            passed,
            expectedBlockCount,
            actualNonAirBlocks,
            airWhereExpectedSolid,
            sampleChecks,
            sampleFailures,
            List.copyOf(violations),
            durationMs
        );
    }

    /**
     * Quick integrity check without detailed violations.
     */
    public static boolean quickCheck(ServerLevel level, int minX, int minY, int minZ,
                                      int maxX, int maxY, int maxZ, int expectedBlockCount) {
        // Count non-air blocks
        int actualNonAirBlocks = 0;
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (!level.getBlockState(pos).isAir()) {
                        actualNonAirBlocks++;
                    }
                }
            }
        }

        // Allow 5% variance
        double integrityPercent = expectedBlockCount > 0
            ? (double) actualNonAirBlocks / expectedBlockCount * 100
            : 100.0;
        return integrityPercent >= 95.0;
    }

    private boolean isWithinFloorBounds(int dx, int dz, ArenaTemplate template) {
        ArenaTemplate.ArenaShape shape = template.arenaShape();
        if (shape == null) {
            shape = ArenaTemplate.ArenaShape.RECTANGULAR;
        }

        int halfX = getSizeX(template) / 2;
        int halfZ = getSizeZ(template) / 2;
        int radius = Math.max(halfX, halfZ);

        int iterHalfX = (shape == ArenaTemplate.ArenaShape.RECTANGULAR) ? halfX : radius;
        int iterHalfZ = (shape == ArenaTemplate.ArenaShape.RECTANGULAR) ? halfZ : radius;

        return dx >= -iterHalfX && dx < iterHalfX && dz >= -iterHalfZ && dz < iterHalfZ;
    }

    private boolean isInArenaShape(int dx, int dz, ArenaTemplate template) {
        ArenaTemplate.ArenaShape shape = template.arenaShape();
        if (shape == null) {
            shape = ArenaTemplate.ArenaShape.RECTANGULAR;
        }

        int halfX = getSizeX(template) / 2;
        int halfZ = getSizeZ(template) / 2;

        return switch (shape) {
            case RECTANGULAR -> dx >= -halfX && dx < halfX && dz >= -halfZ && dz < halfZ;
            case CIRCULAR -> {
                int radius = Math.max(halfX, halfZ);
                yield (dx * dx + dz * dz) <= (radius * radius);
            }
            case RING -> {
                int outerRadius = Math.max(halfX, halfZ);
                Integer innerRadiusVal = template.ringInnerRadius();
                int innerRadius = innerRadiusVal != null ? innerRadiusVal : outerRadius / 2;
                int distSq = dx * dx + dz * dz;
                yield distSq <= (outerRadius * outerRadius) && distSq >= (innerRadius * innerRadius);
            }
        };
    }

    private boolean isOnRectWallEdge(int dx, int dz, ArenaTemplate template, int thickness) {
        if (thickness <= 0) {
            return false;
        }

        int sizeX = getSizeX(template);
        int sizeZ = getSizeZ(template);
        int halfX = sizeX / 2;
        int halfZ = sizeZ / 2;

        int startX = -halfX;
        int startZ = -halfZ;
        int endX = startX + sizeX - 1;
        int endZ = startZ + sizeZ - 1;

        int pad = thickness - 1;

        boolean onNorthSouth = (dz >= startZ - pad && dz <= startZ)
            || (dz >= endZ && dz <= endZ + pad);
        boolean onEastWest = (dx >= startX - pad && dx <= startX)
            || (dx >= endX && dx <= endX + pad);

        boolean inNorthSouthSpan = dx >= startX && dx <= endX;
        boolean inEastWestSpan = dz >= startZ && dz <= endZ;

        return (onNorthSouth && inNorthSouthSpan) || (onEastWest && inEastWestSpan);
    }

    private boolean isOnCircularBorder(int dx, int dz, ArenaTemplate template, int thickness) {
        if (thickness <= 0) {
            return false;
        }

        ArenaTemplate.ArenaShape shape = template.arenaShape();
        if (shape == null || shape == ArenaTemplate.ArenaShape.RECTANGULAR) {
            return false;
        }

        int halfX = getSizeX(template) / 2;
        int halfZ = getSizeZ(template) / 2;
        int outerRadius = Math.max(halfX, halfZ);
        int distSq = dx * dx + dz * dz;

        int outerWallInnerSq = outerRadius * outerRadius;
        int outerWallOuterSq = (outerRadius + thickness) * (outerRadius + thickness);
        boolean onOuterWall = distSq >= outerWallInnerSq && distSq < outerWallOuterSq;

        if (shape == ArenaTemplate.ArenaShape.CIRCULAR) {
            return onOuterWall;
        }

        Integer innerRadiusVal = template.ringInnerRadius();
        int innerRadius = innerRadiusVal != null ? innerRadiusVal : outerRadius / 2;
        int innerWallOuterSq = innerRadius * innerRadius;
        int innerWallInnerSq = Math.max(0, (innerRadius - thickness) * (innerRadius - thickness));
        boolean onInnerWall = distSq <= innerWallOuterSq && distSq >= innerWallInnerSq;

        return onOuterWall || onInnerWall;
    }

    private int getSizeX(ArenaTemplate template) {
        Integer sx = template.sizeX();
        return sx != null ? sx.intValue() : template.size();
    }

    private int getSizeZ(ArenaTemplate template) {
        Integer sz = template.sizeZ();
        return sz != null ? sz.intValue() : template.size();
    }
}
