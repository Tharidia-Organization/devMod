package com.devmod.network;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.server.level.ServerPlayer;

public class PacketValidator {
    private static final Logger LOGGER = LoggerFactory.getLogger(PacketValidator.class);
    public static final PacketValidator INSTANCE = new PacketValidator();

    // Rate limiting configuration
    private static final int DEFAULT_RATE_LIMIT = 10; // packets per window
    private static final long RATE_WINDOW_MS = 1000; // 1 second window
    private static final long CLEANUP_INTERVAL_MS = 60_000; // cleanup every minute
    private static final Map<String, Integer> RATE_LIMIT_OVERRIDES = Map.of(
        "ability_action", 5,
        "shop_purchase", 5,
        "recipe_sync", 3,
        "telemetry_batch", 20
    );

    // Validation bounds
    public static final double MIN_ATTRIBUTE_VALUE = 0.0;
    public static final double MAX_HEALTH = 100_000.0;
    public static final double MAX_DAMAGE = 10_000.0;
    public static final double MAX_FOLLOW_RANGE = 1024.0;
    public static final double MAX_ARMOR = 1000.0;
    public static final double MIN_ATTACK_SPEED = -10.0;
    public static final double MAX_MULTIPLIER = 100.0;
    public static final double MIN_MULTIPLIER = 0.0;
    public static final double MAX_PENETRATION = 1.0;
    public static final double MIN_PENETRATION = -1.0;
    public static final double MAX_ARMOR_SHRED = 66.0;
    public static final double MAX_DAMAGE_VS = 2.0;
    public static final double MAX_TRUE_DAMAGE = 1.0;
    public static final double MAX_SWEEPING = 1.0;
    public static final int MAX_DURABILITY = 4096;
    public static final int MAX_REPAIR_COST = 1000;
    public static final double MAX_TOOL_SPEED = 128.0;
    public static final int MAX_TOOL_DAMAGE_PER_BLOCK = 128;
    public static final int MAX_TOOL_RULES = 16;
    public static final double MAX_RANGED_SPEED = 5.0;
    public static final double MAX_RANGED_GRAVITY = 0.2;
    public static final double MAX_RANGED_SPREAD = 3.0;
    public static final double MAX_RANGED_BASE_DAMAGE = 50.0;
    public static final double MIN_RANGED_MULT = 0.2;
    public static final double MAX_RANGED_MULT = 3.0;
    public static final double MIN_ARMOR_REDUCTION = 0.0;
    public static final double MAX_ARMOR_REDUCTION = 1.0;
    public static final double MIN_ARMOR_BONUS = -20.0;
    public static final double MAX_ARMOR_BONUS = 30.0;
    public static final double MIN_TOUGHNESS_BONUS = -10.0;
    public static final double MAX_TOUGHNESS_BONUS = 20.0;
    public static final double MIN_KNOCKBACK_RESIST = 0.0;
    public static final double MAX_KNOCKBACK_RESIST = 1.0;
    public static final double MIN_THORNS_PERCENT = 0.0;
    public static final double MAX_THORNS_PERCENT = 0.5;
    public static final double MIN_SHIELD_BLOCK = 0.0;
    public static final double MAX_SHIELD_BLOCK = 1.0;
    public static final double MIN_SHIELD_RECOVERY = 0.0;
    public static final double MAX_SHIELD_RECOVERY = 2.0;

    // Rate limiting state
    private final Map<String, RateLimitEntry> rateLimits = new ConcurrentHashMap<>();
    private volatile long lastCleanup = System.currentTimeMillis();

    // Telemetry counters for security monitoring
    private final Map<String, AtomicLong> rejectionCounters = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> rateLimitCounters = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> ipRateLimitCounters = new ConcurrentHashMap<>();

    // Rate limiting for failed operator checks (to prevent log spam)
    private static final long OP_FAIL_LOG_INTERVAL_MS = 60_000; // Log at most once per minute per player
    private final Map<String, Long> lastOpFailLogTime = new ConcurrentHashMap<>();

    private PacketValidator() {}

    // ===== Permission Checks =====

    /**
     * Check if a player has operator permissions.
     *
     * @param player The player to check
     * @return true if player is an operator
     */
    public boolean isOperator(ServerPlayer player) {
        if (player == null) return false;
        return player.hasPermissions(2); // OP level 2+
    }

    /**
     * Validate and check rate limit for a packet.
     * Returns a result with success status and optional error message.
     *
     * @param player The player sending the packet
     * @param packetType Type identifier for rate limiting
     * @param requiresOp Whether this packet requires operator permissions
     * @return ValidationResult with success status
     */
    public ValidationResult validatePacket(ServerPlayer player, String packetType, boolean requiresOp) {
        if (player == null) {
            return ValidationResult.fail("Invalid player context");
        }

        // P0: Per-IP rate limit check (catches multi-account abuse)
        if (!IpRateLimiter.INSTANCE.checkRateLimit(player, packetType)) {
            ipRateLimitCounters.computeIfAbsent(packetType, k -> new AtomicLong(0)).incrementAndGet();
            LOGGER.warn("IP rate limit exceeded for {} on packet type: {}",
                player.getName().getString(), packetType);
            return ValidationResult.fail("Rate limit exceeded. Please wait before retrying.");
        }

        // Permission check
        if (requiresOp && !isOperator(player)) {
            // Rate-limit logging to prevent log spam from malicious clients
            String playerKey = player.getUUID().toString();
            long now = System.currentTimeMillis();
            Long lastLog = lastOpFailLogTime.get(playerKey);
            if (lastLog == null || now - lastLog >= OP_FAIL_LOG_INTERVAL_MS) {
                lastOpFailLogTime.put(playerKey, now);
                LOGGER.warn("Non-operator {} attempted to send privileged packet: {}",
                    player.getName().getString(), packetType);
            }
            return ValidationResult.fail("Operation requires operator permissions");
        }

        // Per-player UUID rate limit check
        if (!checkRateLimit(player.getUUID(), packetType)) {
            LOGGER.warn("Player rate limit exceeded for {} on packet type: {}",
                player.getName().getString(), packetType);
            return ValidationResult.fail("Rate limit exceeded. Please wait before retrying.");
        }

        return ValidationResult.success();
    }

    // ===== Value Validation =====

    /**
     * Validate a health value.
     *
     * @param health The health value to validate
     * @return Clamped health value
     */
    public double validateHealth(double health) {
        return clamp(health, MIN_ATTRIBUTE_VALUE, MAX_HEALTH);
    }

    /**
     * Validate a damage value.
     *
     * @param damage The damage value to validate
     * @return Clamped damage value
     */
    public double validateDamage(double damage) {
        return clamp(damage, MIN_ATTRIBUTE_VALUE, MAX_DAMAGE);
    }

    /**
     * Validate a follow range value.
     *
     * @param followRange The follow range to validate
     * @return Clamped follow range value
     */
    public double validateFollowRange(double followRange) {
        return clamp(followRange, MIN_ATTRIBUTE_VALUE, MAX_FOLLOW_RANGE);
    }

    /**
     * Validate an armor value.
     *
     * @param armor The armor value to validate
     * @return Clamped armor value
     */
    public double validateArmor(double armor) {
        return clamp(armor, MIN_ATTRIBUTE_VALUE, MAX_ARMOR);
    }

    public double validateArmorShred(double shredPercent) {
        return clamp(shredPercent, MIN_ATTRIBUTE_VALUE, MAX_ARMOR_SHRED);
    }

    public double validateDamageVs(double percent) {
        return clamp(percent, MIN_ATTRIBUTE_VALUE, MAX_DAMAGE_VS);
    }

    public double validateTrueDamage(double percent) {
        return clamp(percent, MIN_ATTRIBUTE_VALUE, MAX_TRUE_DAMAGE);
    }

    public double validateSweeping(double percent) {
        return clamp(percent, MIN_ATTRIBUTE_VALUE, MAX_SWEEPING);
    }

    public int validateDurability(int value) {
        return (int) clamp(value, 0, MAX_DURABILITY);
    }

    public int validateRepairCost(int value) {
        return (int) clamp(value, 0, MAX_REPAIR_COST);
    }

    public double validateToolSpeed(double speed) {
        return clamp(speed, 0.0, MAX_TOOL_SPEED);
    }

    public int validateToolDamagePerBlock(int value) {
        return (int) clamp(value, 0, MAX_TOOL_DAMAGE_PER_BLOCK);
    }

    public double validateRangedMultiplier(double value) {
        return clamp(value, MIN_RANGED_MULT, MAX_RANGED_MULT);
    }

    public double validateRangedSpeed(double value) {
        return clamp(value, 0.0, MAX_RANGED_SPEED);
    }

    public double validateRangedGravity(double value) {
        return clamp(value, 0.0, MAX_RANGED_GRAVITY);
    }

    public double validateRangedSpread(double value) {
        return clamp(value, 0.0, MAX_RANGED_SPREAD);
    }

    public double validateRangedBaseDamage(double value) {
        return clamp(value, 0.0, MAX_RANGED_BASE_DAMAGE);
    }

    public double validateArmorReduction(double value) {
        return clamp(value, MIN_ARMOR_REDUCTION, MAX_ARMOR_REDUCTION);
    }

    public double validateArmorBonus(double value) {
        return clamp(value, MIN_ARMOR_BONUS, MAX_ARMOR_BONUS);
    }

    public double validateToughnessBonus(double value) {
        return clamp(value, MIN_TOUGHNESS_BONUS, MAX_TOUGHNESS_BONUS);
    }

    public double validateKnockbackResistance(double value) {
        return clamp(value, MIN_KNOCKBACK_RESIST, MAX_KNOCKBACK_RESIST);
    }

    public double validateThornsPercent(double value) {
        return clamp(value, MIN_THORNS_PERCENT, MAX_THORNS_PERCENT);
    }

    public double validateShieldBlock(double value) {
        return clamp(value, MIN_SHIELD_BLOCK, MAX_SHIELD_BLOCK);
    }

    public double validateShieldRecovery(double value) {
        return clamp(value, MIN_SHIELD_RECOVERY, MAX_SHIELD_RECOVERY);
    }

    /**
     * Validate a damage multiplier value.
     *
     * @param multiplier The multiplier to validate
     * @return Clamped multiplier value
     */
    public double validateMultiplier(double multiplier) {
        return clamp(multiplier, MIN_MULTIPLIER, MAX_MULTIPLIER);
    }

    /**
     * Validate an attack speed value (allows negative speeds for vanilla weapons).
     */
    public double validateAttackSpeed(double speed) {
        return clamp(speed, MIN_ATTACK_SPEED, MAX_MULTIPLIER);
    }

    /**
     * Validate an armor penetration value.
     *
     * @param penetration The penetration to validate
     * @return Clamped penetration value
     */
    public double validatePenetration(double penetration) {
        return clamp(penetration, MIN_PENETRATION, MAX_PENETRATION);
    }

    /**
     * Validate an entity ID (must be positive).
     *
     * @param entityId The entity ID to validate
     * @return true if valid
     */
    public boolean validateEntityId(int entityId) {
        return entityId >= 0;
    }

    /**
     * Validate a string value (non-null, reasonable length, no control characters).
     *
     * @param value The string to validate
     * @param maxLength Maximum allowed length
     * @return Sanitized string or null if invalid
     */
    @Nullable
    public String validateString(String value, int maxLength) {
        if (value == null) return null;
        if (value.length() > maxLength) {
            value = value.substring(0, maxLength);
        }
        // Remove control characters except common whitespace
        return value.replaceAll("[\\p{Cntrl}&&[^\t\n\r]]", "");
    }

    /**
     * Validate an item ID string.
     *
     * @param itemId The item ID to validate
     * @return Sanitized item ID or null if invalid
     */
    @Nullable
    public String validateItemId(String itemId) {
        if (itemId == null || itemId.isEmpty()) return null;

        // Limit length
        if (itemId.length() > 256) return null;

        // Must match resource location pattern: namespace:path or just path
        if (!itemId.matches("^[a-z0-9_.-]+(:?[a-z0-9_/.-]*)?$")) {
            return null;
        }

        return itemId;
    }

    // ===== Rate Limiting =====

    /**
     * Check and update rate limit for a player/packet combination.
     *
     * @param playerId Player UUID
     * @param packetType Packet type identifier
     * @return true if within rate limit, false if exceeded
     */
    private boolean checkRateLimit(UUID playerId, String packetType) {
        maybeCleanup();

        int limit = RATE_LIMIT_OVERRIDES.getOrDefault(packetType, DEFAULT_RATE_LIMIT);
        String key = playerId.toString() + ":" + packetType;
        long now = System.currentTimeMillis();

        RateLimitEntry entry = rateLimits.compute(key, (k, existing) -> {
            if (existing == null) {
                return new RateLimitEntry(now);
            }

            // Reset window if expired
            if (now - existing.windowStart > RATE_WINDOW_MS) {
                existing.windowStart = now;
                existing.count.set(0);
            }

            return existing;
        });

        int count = entry.count.incrementAndGet();
        return count <= limit;
    }

    /**
     * Cleanup stale rate limit entries periodically.
     */
    private void maybeCleanup() {
        long now = System.currentTimeMillis();
        if (now - lastCleanup < CLEANUP_INTERVAL_MS) {
            return;
        }

        lastCleanup = now;
        long threshold = now - RATE_WINDOW_MS * 10; // Keep 10 windows of history

        rateLimits.entrySet().removeIf(entry ->
            entry.getValue().windowStart < threshold);

        // Also cleanup stale op fail log entries
        long opFailThreshold = now - OP_FAIL_LOG_INTERVAL_MS * 2;
        lastOpFailLogTime.entrySet().removeIf(entry ->
            entry.getValue() < opFailThreshold);
    }

    /**
     * Clear all rate limit data (for testing).
     */
    public void clearRateLimits() {
        rateLimits.clear();
    }

    // ===== Telemetry =====

    /**
     * Record a packet rejection for telemetry.
     *
     * @param packetType The type of packet that was rejected
     * @param reason The rejection reason (for logging)
     */
    public void recordRejection(String packetType, String reason) {
        rejectionCounters.computeIfAbsent(packetType, k -> new AtomicLong(0)).incrementAndGet();
        LOGGER.warn("[Security] Packet rejected - type: {}, reason: {}", packetType, reason);
    }

    /**
     * Record a rate limit hit for telemetry.
     *
     * @param packetType The type of packet that hit the limit
     * @param playerName The player who hit the limit
     */
    public void recordRateLimitHit(String packetType, String playerName) {
        rateLimitCounters.computeIfAbsent(packetType, k -> new AtomicLong(0)).incrementAndGet();
        LOGGER.warn("[Security] Rate limit hit - type: {}, player: {}", packetType, playerName);
    }

    /**
     * Get rejection count for a packet type (for monitoring).
     */
    public long getRejectionCount(String packetType) {
        AtomicLong counter = rejectionCounters.get(packetType);
        return counter != null ? counter.get() : 0;
    }

    /**
     * Get rate limit hit count for a packet type (for monitoring).
     */
    public long getRateLimitCount(String packetType) {
        AtomicLong counter = rateLimitCounters.get(packetType);
        return counter != null ? counter.get() : 0;
    }

    /**
     * Get total rejections across all packet types.
     */
    public long getTotalRejections() {
        return rejectionCounters.values().stream().mapToLong(AtomicLong::get).sum();
    }

    /**
     * Get total rate limit hits across all packet types.
     */
    public long getTotalRateLimitHits() {
        return rateLimitCounters.values().stream().mapToLong(AtomicLong::get).sum();
    }

    /**
     * Get IP rate limit hit count for a packet type (for monitoring).
     */
    public long getIpRateLimitCount(String packetType) {
        AtomicLong counter = ipRateLimitCounters.get(packetType);
        return counter != null ? counter.get() : 0;
    }

    /**
     * Get total IP rate limit hits across all packet types.
     */
    public long getTotalIpRateLimitHits() {
        return ipRateLimitCounters.values().stream().mapToLong(AtomicLong::get).sum();
    }

    /**
     * Reset all telemetry counters (for testing or periodic reset).
     */
    public void resetTelemetry() {
        rejectionCounters.clear();
        rateLimitCounters.clear();
        ipRateLimitCounters.clear();
    }

    /**
     * Get a summary of security telemetry for logging/monitoring.
     */
    public String getTelemetrySummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Security Telemetry ===\n");
        sb.append(String.format("Total rejections: %d%n", getTotalRejections()));
        sb.append(String.format("Total player rate limit hits: %d%n", getTotalRateLimitHits()));
        sb.append(String.format("Total IP rate limit hits: %d%n", getTotalIpRateLimitHits()));

        // IP rate limiter stats
        IpRateLimiter.Stats ipStats = IpRateLimiter.INSTANCE.getStats();
        sb.append(String.format("IP limiter - tracked: %d, blocked: %d%n",
            ipStats.trackedIps(), ipStats.blockedIps()));

        if (!rejectionCounters.isEmpty()) {
            sb.append("Rejections by type:\n");
            rejectionCounters.forEach((type, count) ->
                sb.append(String.format("  %s: %d%n", type, count.get())));
        }

        if (!rateLimitCounters.isEmpty()) {
            sb.append("Player rate limits by type:\n");
            rateLimitCounters.forEach((type, count) ->
                sb.append(String.format("  %s: %d%n", type, count.get())));
        }

        if (!ipRateLimitCounters.isEmpty()) {
            sb.append("IP rate limits by type:\n");
            ipRateLimitCounters.forEach((type, count) ->
                sb.append(String.format("  %s: %d%n", type, count.get())));
        }

        return sb.toString();
    }

    // ===== Utility =====

    private static double clamp(double value, double min, double max) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return min;
        }
        return Math.max(min, Math.min(max, value));
    }

    // ===== Internal Classes =====

    private static class RateLimitEntry {
        volatile long windowStart;
        final AtomicInteger count = new AtomicInteger(1);

        RateLimitEntry(long windowStart) {
            this.windowStart = windowStart;
        }
    }

    /**
     * Result of packet validation.
     */
    public static class ValidationResult {
        private final boolean success;
        @Nullable
        private final String errorMessage;

        private ValidationResult(boolean success, @Nullable String errorMessage) {
            this.success = success;
            this.errorMessage = errorMessage;
        }

        public static ValidationResult success() {
            return new ValidationResult(true, null);
        }

        public static ValidationResult fail(String message) {
            return new ValidationResult(false, message);
        }

        public boolean isSuccess() {
            return success;
        }

        @Nullable
        public String getErrorMessage() {
            return errorMessage;
        }
    }
}
