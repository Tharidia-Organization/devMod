package com.devmod.arena.cleanup;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

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

            java.util.Random random = new java.util.Random();

            for (int i = 0; i < config.sampleSize(); i++) {
                int x = minX + random.nextInt(volumeX);
                int y = minY + random.nextInt(volumeY);
                int z = minZ + random.nextInt(volumeZ);

                BlockPos pos = new BlockPos(x, y, z);
                BlockState state = level.getBlockState(pos);
                sampleChecks++;

                // Check for unexpected air in floor/wall regions (heuristic)
                // Floor is typically at minY, walls at edges
                boolean isFloorLevel = (y == minY);
                boolean isWallEdge = (x == minX || x == maxX || z == minZ || z == maxZ);

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
            LOGGER.warn("[BlockIntegrity] Verification FAILED: {:.1f}% integrity ({}/{} blocks), {} sample failures",
                integrityPercent, actualNonAirBlocks, expectedBlockCount, sampleFailures);
        } else if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("[BlockIntegrity] Verification PASSED: {:.1f}% integrity ({}/{} blocks)",
                integrityPercent, actualNonAirBlocks, expectedBlockCount);
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
}
