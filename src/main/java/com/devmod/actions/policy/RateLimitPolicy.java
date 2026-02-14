package com.devmod.actions.policy;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.devmod.actions.ActionContext;
import com.devmod.actions.catalog.ActionSpec;

/**
 * Per-player, per-action rate limiting using a simple token bucket approach.
 * Prevents abuse by limiting how frequently an action can be invoked.
 *
 * <p>Only applies when {@code ActionSpec.policyMeta().rateLimitPerSec() > 0}.
 * Actions with no rate limit are allowed unconditionally.
 *
 * <p>Token buckets are keyed by (playerUUID, actionId) and cleaned up lazily.
 */
public final class RateLimitPolicy implements ActionPolicy {

    /**
     * Key for rate limit buckets: combines player UUID and action ID.
     */
    private record BucketKey(UUID playerId, String actionId) {}

    /**
     * Simple token bucket: tracks last invocation time and available tokens.
     */
    private static final class TokenBucket {
        private long lastRefillNanos;
        private double tokens;
        private final int maxTokens;
        private final double refillRatePerNano;

        TokenBucket(int maxPerSecond) {
            this.maxTokens = maxPerSecond;
            this.tokens = maxPerSecond;
            this.lastRefillNanos = System.nanoTime();
            this.refillRatePerNano = maxPerSecond / 1_000_000_000.0;
        }

        synchronized boolean tryConsume() {
            refill();
            if (tokens >= 1.0) {
                tokens -= 1.0;
                return true;
            }
            return false;
        }

        private void refill() {
            long now = System.nanoTime();
            long elapsed = now - lastRefillNanos;
            if (elapsed > 0) {
                tokens = Math.min(maxTokens, tokens + elapsed * refillRatePerNano);
                lastRefillNanos = now;
            }
        }
    }

    private final Map<BucketKey, TokenBucket> buckets = new ConcurrentHashMap<>();

    @Override
    public PolicyResult evaluate(ActionSpec spec, ActionContext context) {
        int rateLimit = spec.policyMeta().rateLimitPerSec();
        if (rateLimit <= 0) {
            return PolicyResult.allow();
        }

        var player = context.getPlayer();
        if (player == null) {
            // No player = server invocation, no rate limit
            return PolicyResult.allow();
        }

        BucketKey key = new BucketKey(player.getUUID(), spec.id());
        TokenBucket bucket = buckets.computeIfAbsent(key, k -> new TokenBucket(rateLimit));

        if (bucket.tryConsume()) {
            return PolicyResult.allow();
        }

        return PolicyResult.deny(
            "Action '" + spec.id() + "' rate limited (max " + rateLimit + "/sec)",
            policyName()
        );
    }

    @Override
    public String policyName() {
        return "RateLimitPolicy";
    }

    @Override
    public int priority() {
        return 300;
    }

    /**
     * Clears all rate limit buckets. Useful for testing.
     */
    public void clearBuckets() {
        buckets.clear();
    }
}
