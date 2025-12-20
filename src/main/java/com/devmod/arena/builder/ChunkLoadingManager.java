package com.devmod.arena.builder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.locks.LockSupport;

/**
 * Manages chunk loading for arena builds (DD9).
 *
 * <p>Ensures all required chunks are FULL status before building.
 * Implements polling with timeout and proper cleanup on failure.
 */
public class ChunkLoadingManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(ChunkLoadingManager.class);

    // DD9: Polling interval and default timeout
    private static final long POLL_INTERVAL_NS = 10_000_000; // 10ms
    private static final int DEFAULT_TIMEOUT_MS = 30_000; // 30 seconds

    private final Set<Long> loadedChunks = new HashSet<>();
    private final ChunkLoader chunkLoader;
    private final ChunkStatusChecker statusChecker;
    private final TicketManager ticketManager;

    public ChunkLoadingManager(
            ChunkLoader chunkLoader,
            ChunkStatusChecker statusChecker,
            TicketManager ticketManager) {
        this.chunkLoader = chunkLoader;
        this.statusChecker = statusChecker;
        this.ticketManager = ticketManager;
    }

    /**
     * Ensures all chunks in the given bounds are loaded to FULL status.
     *
     * @param minX Minimum chunk X
     * @param minZ Minimum chunk Z
     * @param maxX Maximum chunk X
     * @param maxZ Maximum chunk Z
     * @param timeoutMs Timeout in milliseconds
     * @return Result with loaded chunks or error
     */
    public ChunkLoadResult ensureChunksLoaded(int minX, int minZ, int maxX, int maxZ, int timeoutMs) {
        Set<Long> required = computeRequiredChunks(minX, minZ, maxX, maxZ);
        long deadline = System.currentTimeMillis() + timeoutMs;

        LOGGER.debug("Loading {} chunks for arena: ({},{}) to ({},{})",
            required.size(), minX, minZ, maxX, maxZ);

        // 1. Request loading for all chunks and add tickets
        for (long packedPos : required) {
            int chunkX = unpackX(packedPos);
            int chunkZ = unpackZ(packedPos);

            try {
                ticketManager.addTicket(chunkX, chunkZ);
                chunkLoader.requestLoad(chunkX, chunkZ);
                loadedChunks.add(packedPos);
            } catch (Exception e) {
                LOGGER.error("Failed to request chunk ({}, {}): {}", chunkX, chunkZ, e.getMessage());
                releaseAllTickets();
                return ChunkLoadResult.error("Failed to request chunk: " + e.getMessage());
            }
        }

        // 2. Poll until all chunks are FULL or timeout
        for (long packedPos : required) {
            int chunkX = unpackX(packedPos);
            int chunkZ = unpackZ(packedPos);

            while (System.currentTimeMillis() < deadline) {
                if (statusChecker.isFull(chunkX, chunkZ)) {
                    break;
                }
                LockSupport.parkNanos(POLL_INTERVAL_NS);
            }

            // Final check
            if (!statusChecker.isFull(chunkX, chunkZ)) {
                LOGGER.error("Chunk ({}, {}) failed to reach FULL status within {}ms",
                    chunkX, chunkZ, timeoutMs);
                releaseAllTickets();
                return ChunkLoadResult.timeout(chunkX, chunkZ, timeoutMs);
            }
        }

        LOGGER.debug("All {} chunks loaded successfully", required.size());
        return ChunkLoadResult.success(required);
    }

    /**
     * Ensures chunks are loaded using default timeout.
     */
    public ChunkLoadResult ensureChunksLoaded(int minX, int minZ, int maxX, int maxZ) {
        return ensureChunksLoaded(minX, minZ, maxX, maxZ, DEFAULT_TIMEOUT_MS);
    }

    /**
     * Releases all tickets for loaded chunks.
     */
    public void releaseAllTickets() {
        for (long packedPos : loadedChunks) {
            try {
                ticketManager.removeTicket(unpackX(packedPos), unpackZ(packedPos));
            } catch (Exception e) {
                LOGGER.warn("Failed to release ticket for chunk {}: {}", packedPos, e.getMessage());
            }
        }
        loadedChunks.clear();
    }

    /**
     * Returns the set of loaded chunks.
     */
    public Set<Long> getLoadedChunks() {
        return Set.copyOf(loadedChunks);
    }

    // === Helpers ===

    private Set<Long> computeRequiredChunks(int minX, int minZ, int maxX, int maxZ) {
        Set<Long> chunks = new HashSet<>();
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                chunks.add(packChunkPos(x, z));
            }
        }
        return chunks;
    }

    private static long packChunkPos(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    private static int unpackX(long packed) {
        return (int) (packed >> 32);
    }

    private static int unpackZ(long packed) {
        return (int) packed;
    }

    // === Functional Interfaces ===

    @FunctionalInterface
    public interface ChunkLoader {
        void requestLoad(int chunkX, int chunkZ);
    }

    @FunctionalInterface
    public interface ChunkStatusChecker {
        boolean isFull(int chunkX, int chunkZ);
    }

    public interface TicketManager {
        void addTicket(int chunkX, int chunkZ);
        void removeTicket(int chunkX, int chunkZ);
    }

    // === Result Record ===

    public record ChunkLoadResult(
        boolean success,
        Set<Long> loadedChunks,
        String errorMessage,
        int failedChunkX,
        int failedChunkZ
    ) {
        public static ChunkLoadResult success(Set<Long> chunks) {
            return new ChunkLoadResult(true, chunks, null, 0, 0);
        }

        public static ChunkLoadResult timeout(int chunkX, int chunkZ, int timeoutMs) {
            return new ChunkLoadResult(
                false,
                Set.of(),
                "Chunk (%d, %d) timeout after %dms".formatted(chunkX, chunkZ, timeoutMs),
                chunkX,
                chunkZ
            );
        }

        public static ChunkLoadResult error(String message) {
            return new ChunkLoadResult(false, Set.of(), message, 0, 0);
        }
    }
}
