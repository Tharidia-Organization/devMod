package com.devmod.arena.builder;

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.devmod.compat.mods.c2me.C2MECompat;

public class ChunkLoadingManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(ChunkLoadingManager.class);

    // DD9: Ticket type for arena builds - used for chunk force-loading
    public static final String TICKET_TYPE_ARENA_BUILD = "arena_build";

    // Retry configuration
    private static final int DEFAULT_MAX_RETRIES = 3;

    /**
     * Owned by the single thread that drives a build: every call site builds its own manager
     * for one builder (ArenaCommandEvents.createBuilder, ArenaSetupManager.createTemplateBuilder),
     * so this needs no synchronization. Sharing a manager across threads would.
     */
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
                // Record before requesting the load: releaseAllTickets() only sees
                // chunks in this set, so a failure after addTicket must still be released.
                loadedChunks.add(packedPos);
                chunkLoader.requestLoad(chunkX, chunkZ);
            } catch (Exception e) {
                LOGGER.error("Failed to request chunk ({}, {}): {}", chunkX, chunkZ, e.getMessage());
                releaseAllTickets();
                return ChunkLoadResult.error("Failed to request chunk: " + e.getMessage());
            }
        }

        // 2. Verify every chunk reached FULL. requestLoad is synchronous (see ChunkLoader),
        // so waiting here cannot help: the caller is usually the server thread, and parking
        // it would block the very thread that advances chunk loading. Re-request instead.
        for (long packedPos : required) {
            int chunkX = unpackX(packedPos);
            int chunkZ = unpackZ(packedPos);

            if (!statusChecker.isFull(chunkX, chunkZ) && System.currentTimeMillis() < deadline) {
                try {
                    chunkLoader.requestLoad(chunkX, chunkZ);
                } catch (Exception e) {
                    LOGGER.error("Failed to request chunk ({}, {}): {}", chunkX, chunkZ, e.getMessage());
                    releaseAllTickets();
                    return ChunkLoadResult.error("Failed to request chunk: " + e.getMessage());
                }
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
     * Timeout is optimized when C2ME is available.
     */
    public ChunkLoadResult ensureChunksLoaded(int minX, int minZ, int maxX, int maxZ) {
        return ensureChunksLoaded(minX, minZ, maxX, maxZ, getDefaultTimeoutMs());
    }

    /**
     * Get the default timeout, optimized for C2ME if available.
     * C2ME parallelizes chunk loading, so timeouts can be shorter.
     */
    private static int getDefaultTimeoutMs() {
        return C2MECompat.getRecommendedChunkTimeoutMs();
    }

    /**
     * Ensures chunks are loaded, retrying failed attempts.
     *
     * <p>Retries run back to back: the loader is synchronous, so a delay between attempts
     * cannot make loading progress, and on the server thread it would prevent it.
     *
     * @param minX Minimum chunk X
     * @param minZ Minimum chunk Z
     * @param maxX Maximum chunk X
     * @param maxZ Maximum chunk Z
     * @param timeoutMs Timeout per attempt in milliseconds
     * @param maxRetries Maximum number of retry attempts
     * @return Result with loaded chunks or error after all retries exhausted
     */
    public ChunkLoadResult ensureChunksLoadedWithRetry(int minX, int minZ, int maxX, int maxZ,
                                                       int timeoutMs, int maxRetries) {
        ChunkLoadResult lastResult = null;

        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            if (attempt > 0) {
                LOGGER.info("Retrying chunk load (attempt {}/{})...", attempt + 1, maxRetries + 1);
            }

            lastResult = ensureChunksLoaded(minX, minZ, maxX, maxZ, timeoutMs);

            if (lastResult.success()) {
                if (attempt > 0) {
                    LOGGER.info("Chunk load succeeded on retry attempt {}", attempt + 1);
                }
                return lastResult;
            }

            LOGGER.warn("Chunk load attempt {} failed: {}", attempt + 1, lastResult.errorMessage());
        }

        LOGGER.error("All {} chunk load attempts failed", maxRetries + 1);
        return lastResult != null ? lastResult : ChunkLoadResult.error("No attempts made");
    }

    /**
     * Ensures chunks are loaded with default retry configuration.
     * Timeout is optimized when C2ME is available.
     */
    public ChunkLoadResult ensureChunksLoadedWithRetry(int minX, int minZ, int maxX, int maxZ) {
        return ensureChunksLoadedWithRetry(minX, minZ, maxX, maxZ, getDefaultTimeoutMs(), DEFAULT_MAX_RETRIES);
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

    /**
     * Loads a chunk. Implementations must load synchronously - the manager checks status
     * immediately after and never waits, since the caller is typically the server thread
     * and blocking it would stop chunk loading from progressing at all.
     */
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
        @Nullable String errorMessage,
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
