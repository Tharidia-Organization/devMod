package com.devmod.network;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.server.level.ServerPlayer;

/**
 * P0: Per-IP rate limiting for network and mailbox entry points.
 *
 * Provides IP-based rate limiting that is complementary to per-player UUID limiting.
 * IP limiting catches:
 * - Multi-account abuse (single IP, multiple accounts)
 * - Pre-authentication attacks
 * - Bots and automated attacks
 *
 * Thread-safe implementation using ConcurrentHashMap and atomic operations.
 */
public final class IpRateLimiter {
    private static final Logger LOGGER = LoggerFactory.getLogger(IpRateLimiter.class);

    /** Singleton instance */
    public static final IpRateLimiter INSTANCE = new IpRateLimiter();

    // Default configuration
    private static final int DEFAULT_REQUESTS_PER_WINDOW = 100;
    private static final long DEFAULT_WINDOW_MS = 60_000; // 1 minute
    private static final long BLOCK_DURATION_MS = 5 * 60_000; // 5 minute block after limit exceeded
    private static final long CLEANUP_INTERVAL_MS = 60_000; // cleanup every minute
    private static final long ENTRY_TTL_MS = 10 * 60_000; // 10 minute TTL for entries

    // Category-specific limits (more restrictive than default)
    private static final Map<String, CategoryLimit> CATEGORY_LIMITS = Map.of(
        "login", new CategoryLimit(10, 60_000, 10 * 60_000),      // 10/min, 10min block
        "mailbox_send", new CategoryLimit(30, 60_000, 5 * 60_000), // 30/min
        "ticket_create", new CategoryLimit(5, 60_000, 15 * 60_000), // 5/min, 15min block
        "telemetry_batch", new CategoryLimit(20, 60_000, 5 * 60_000), // 20/min
        "ability_action", new CategoryLimit(60, 60_000, 2 * 60_000), // 60/min (1/sec)
        "quest_action", new CategoryLimit(120, 60_000, 2 * 60_000)  // 120/min (2/sec)
    );

    // Rate limiting state: IP -> category -> entry
    private final Map<String, Map<String, RateLimitEntry>> ipLimits = new ConcurrentHashMap<>();

    // Block list: IP -> unblock time
    private final Map<String, Long> blockedIps = new ConcurrentHashMap<>();

    // Telemetry counters
    private final AtomicLong totalRequests = new AtomicLong(0);
    private final AtomicLong rateLimitedRequests = new AtomicLong(0);
    private final AtomicLong blockedRequests = new AtomicLong(0);
    private final Map<String, AtomicLong> categoryRateLimitCounts = new ConcurrentHashMap<>();

    private volatile long lastCleanup = System.currentTimeMillis();

    private IpRateLimiter() {}

    /**
     * Check if a request from this IP is allowed for the given category.
     *
     * @param player The server player (IP extracted from connection)
     * @param category The rate limit category (e.g., "mailbox_send", "login")
     * @return true if allowed, false if rate limited or blocked
     */
    public boolean checkRateLimit(ServerPlayer player, String category) {
        String ip = extractIp(player);
        return checkRateLimit(ip, category);
    }

    /**
     * Check if a request from this IP is allowed for the given category.
     *
     * @param ip The IP address string
     * @param category The rate limit category
     * @return true if allowed, false if rate limited or blocked
     */
    public boolean checkRateLimit(@Nullable String ip, String category) {
        if (ip == null || ip.isEmpty()) {
            // Can't identify IP, allow but log
            LOGGER.debug("[IpRateLimiter] Unknown IP for category {}, allowing", category);
            return true;
        }

        totalRequests.incrementAndGet();
        maybeCleanup();
        long now = System.currentTimeMillis();

        // Check block list first
        if (isBlocked(ip)) {
            blockedRequests.incrementAndGet();
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("[IpRateLimiter] Blocked IP {} attempted category {}", maskIp(ip), category);
            }
            return false;
        }

        // Get category limits
        CategoryLimit limit = CATEGORY_LIMITS.getOrDefault(category,
            new CategoryLimit(DEFAULT_REQUESTS_PER_WINDOW, DEFAULT_WINDOW_MS, BLOCK_DURATION_MS));

        // Get or create IP entry
        Map<String, RateLimitEntry> categoryMap = ipLimits.computeIfAbsent(ip, k -> new ConcurrentHashMap<>());
        RateLimitEntry entry = categoryMap.compute(category, (k, existing) -> {
            if (existing == null) {
                return new RateLimitEntry(now);
            }
            // Reset window if expired
            if (now - existing.windowStart > limit.windowMs) {
                existing.windowStart = now;
                existing.count.set(0);
            }
            return existing;
        });

        entry.lastAccess = now;
        int count = entry.count.incrementAndGet();

        if (count > limit.maxRequests) {
            rateLimitedRequests.incrementAndGet();
            categoryRateLimitCounts.computeIfAbsent(category, k -> new AtomicLong(0)).incrementAndGet();

            // Block IP if significantly over limit
            if (count > limit.maxRequests * 2) {
                blockIp(ip, limit.blockDurationMs);
                LOGGER.warn("[IpRateLimiter] Blocking IP {} for {}ms due to excessive {} requests ({} in window)",
                    maskIp(ip), limit.blockDurationMs, category, count);
            } else {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("[IpRateLimiter] Rate limited IP {} for category {} ({}/{})",
                        maskIp(ip), category, count, limit.maxRequests);
                }
            }
            return false;
        }

        return true;
    }

    /**
     * Extract IP address from a ServerPlayer's connection.
     *
     * @param player The server player
     * @return IP address string, or null if unavailable
     */
    @Nullable
    public String extractIp(ServerPlayer player) {
        if (player == null || player.connection == null) {
            return null;
        }

        try {
            SocketAddress address = player.connection.getRemoteAddress();
            if (address instanceof InetSocketAddress inetAddress) {
                InetAddress inetAddr = inetAddress.getAddress();
                if (inetAddr != null) {
                    return inetAddr.getHostAddress();
                }
            }
        } catch (Exception e) {
            LOGGER.debug("[IpRateLimiter] Failed to extract IP from player {}: {}",
                player.getName().getString(), e.getMessage());
        }
        return null;
    }

    /**
     * Manually block an IP address.
     *
     * @param ip The IP to block
     * @param durationMs Block duration in milliseconds
     */
    public void blockIp(String ip, long durationMs) {
        if (ip == null || ip.isEmpty()) return;
        long unblockTime = System.currentTimeMillis() + durationMs;
        blockedIps.put(ip, unblockTime);
    }

    /**
     * Check if an IP is currently blocked.
     *
     * @param ip The IP to check
     * @return true if blocked
     */
    public boolean isBlocked(String ip) {
        if (ip == null) return false;
        Long unblockTime = blockedIps.get(ip);
        if (unblockTime == null) return false;
        if (System.currentTimeMillis() >= unblockTime) {
            blockedIps.remove(ip);
            return false;
        }
        return true;
    }

    /**
     * Manually unblock an IP address.
     *
     * @param ip The IP to unblock
     */
    public void unblockIp(String ip) {
        if (ip != null) {
            blockedIps.remove(ip);
        }
    }

    /**
     * Get current statistics.
     */
    public Stats getStats() {
        return new Stats(
            totalRequests.get(),
            rateLimitedRequests.get(),
            blockedRequests.get(),
            ipLimits.size(),
            blockedIps.size()
        );
    }

    /**
     * Get rate limit count for a specific category.
     */
    public long getCategoryRateLimitCount(String category) {
        AtomicLong counter = categoryRateLimitCounts.get(category);
        return counter != null ? counter.get() : 0;
    }

    /**
     * Reset all counters and state (for testing).
     */
    public void reset() {
        ipLimits.clear();
        blockedIps.clear();
        totalRequests.set(0);
        rateLimitedRequests.set(0);
        blockedRequests.set(0);
        categoryRateLimitCounts.clear();
    }

    /**
     * Clean up stale entries periodically.
     * Called from server tick event.
     */
    public void cleanup() {
        long now = System.currentTimeMillis();
        long threshold = now - ENTRY_TTL_MS;

        // Cleanup rate limit entries
        ipLimits.entrySet().removeIf(ipEntry -> {
            ipEntry.getValue().entrySet().removeIf(catEntry ->
                catEntry.getValue().lastAccess < threshold);
            return ipEntry.getValue().isEmpty();
        });

        // Cleanup expired blocks
        blockedIps.entrySet().removeIf(entry -> now >= entry.getValue());
    }

    private void maybeCleanup() {
        long now = System.currentTimeMillis();
        if (now - lastCleanup >= CLEANUP_INTERVAL_MS) {
            lastCleanup = now;
            cleanup();
        }
    }

    /**
     * Mask IP for logging (privacy protection).
     * Shows first two octets for IPv4, first two groups for IPv6.
     */
    private String maskIp(String ip) {
        if (ip == null) return "<unknown>";
        if (ip.contains(":")) {
            // IPv6: show first two groups
            int first = ip.indexOf(':');
            int second = first >= 0 ? ip.indexOf(':', first + 1) : -1;
            if (first >= 0 && second > first) {
                return ip.substring(0, first) + ":" + ip.substring(first + 1, second) + ":****";
            }
        } else if (ip.contains(".")) {
            // IPv4: show first two octets
            int first = ip.indexOf('.');
            int second = first >= 0 ? ip.indexOf('.', first + 1) : -1;
            if (first >= 0 && second > first) {
                return ip.substring(0, first) + "." + ip.substring(first + 1, second) + ".*.* ";
            }
        }
        return ip.substring(0, Math.min(ip.length(), 4)) + "****";
    }

    // ===== Internal Types =====

    private record CategoryLimit(int maxRequests, long windowMs, long blockDurationMs) {}

    private static class RateLimitEntry {
        volatile long windowStart;
        volatile long lastAccess;
        final AtomicInteger count = new AtomicInteger(0);

        RateLimitEntry(long windowStart) {
            this.windowStart = windowStart;
            this.lastAccess = windowStart;
        }
    }

    /**
     * Statistics record for monitoring.
     */
    public record Stats(
        long totalRequests,
        long rateLimitedRequests,
        long blockedRequests,
        int trackedIps,
        int blockedIps
    ) {
        public double rateLimitRate() {
            return totalRequests > 0 ? (double) rateLimitedRequests / totalRequests : 0;
        }
    }
}
