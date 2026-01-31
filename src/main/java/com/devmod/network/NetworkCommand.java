package com.devmod.network;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Splitter;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/**
 * Network diagnostics command providing visibility into PayloadValidation metrics.
 *
 * Commands:
 *   /devmod network stats        - Show aggregate metrics summary
 *   /devmod network detailed     - Show detailed per-type breakdown
 *   /devmod network reset        - Reset all metrics (admin only)
 *   /devmod network health       - Quick health check
 */
public final class NetworkCommand {
    private static final Logger LOGGER = LoggerFactory.getLogger(NetworkCommand.class);

    private NetworkCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("devmod")
                .then(Commands.literal("network")
                    .requires(source -> source.hasPermission(2))
                    .then(Commands.literal("stats")
                        .executes(NetworkCommand::showStats))
                    .then(Commands.literal("detailed")
                        .executes(NetworkCommand::showDetailed))
                    .then(Commands.literal("reset")
                        .requires(source -> source.hasPermission(4)) // OP level 4 only
                        .executes(NetworkCommand::resetMetrics))
                    .then(Commands.literal("health")
                        .executes(NetworkCommand::showHealth))
                    .executes(NetworkCommand::showStats))
        );
    }

    /**
     * Show aggregate stats using PayloadValidation getters.
     */
    private static int showStats(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();

        // Use individual getters for metrics
        long processed = PayloadValidation.getTotalPayloadsProcessed();
        long sizeRejections = PayloadValidation.getTotalSizeRejections();
        long rateLimitRejections = PayloadValidation.getTotalRateLimitRejections();
        long ipRateLimitRejections = PayloadValidation.getTotalIpRateLimitRejections();
        long disconnects = PayloadValidation.getTotalDisconnects();

        long totalRejections = sizeRejections + rateLimitRejections + ipRateLimitRejections;
        double rejectionRate = (processed + totalRejections) > 0
            ? (double) totalRejections / (processed + totalRejections) * 100
            : 0.0;

        source.sendSuccess(() -> Objects.requireNonNull(
            Component.literal("\u00A7e=== Network Payload Metrics ===")), false);
        source.sendSuccess(() -> Objects.requireNonNull(
            Component.literal(String.format("\u00A77Processed: \u00A7f%,d", processed))), false);
        source.sendSuccess(() -> Objects.requireNonNull(
            Component.literal(String.format("\u00A77Size Rejections: \u00A7c%,d", sizeRejections))), false);
        source.sendSuccess(() -> Objects.requireNonNull(
            Component.literal(String.format("\u00A77Rate Limit Rejections: \u00A7c%,d", rateLimitRejections))), false);
        source.sendSuccess(() -> Objects.requireNonNull(
            Component.literal(String.format("\u00A77IP Rate Limit Rejections: \u00A7c%,d", ipRateLimitRejections))), false);
        source.sendSuccess(() -> Objects.requireNonNull(
            Component.literal(String.format("\u00A77Forced Disconnects: \u00A7c%,d", disconnects))), false);
        source.sendSuccess(() -> Objects.requireNonNull(
            Component.literal(String.format("\u00A77Rejection Rate: \u00A7f%.2f%%", rejectionRate))), false);

        // IP rate limiter stats
        IpRateLimiter.Stats ipStats = IpRateLimiter.INSTANCE.getStats();
        source.sendSuccess(() -> Objects.requireNonNull(
            Component.literal("\u00A7e--- IP Rate Limiter ---")), false);
        source.sendSuccess(() -> Objects.requireNonNull(
            Component.literal(String.format("\u00A77Tracked IPs: \u00A7f%d \u00A77| Blocked: \u00A7c%d",
                ipStats.trackedIps(), ipStats.blockedIps()))), false);

        LOGGER.debug("Network stats command executed: {} processed, {} rejections", processed, totalRejections);
        return 1;
    }

    /**
     * Show detailed per-type breakdown using getMetricsSummary().
     */
    private static int showDetailed(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();

        // Use getMetricsSummary() for detailed breakdown
        String summary = PayloadValidation.getMetricsSummary();

        source.sendSuccess(() -> Objects.requireNonNull(
            Component.literal("\u00A7e=== Detailed Network Metrics ===")), false);

        // Split and send each line
        for (String line : Splitter.on('\n').split(summary)) {
            if (!line.isBlank()) {
                String formattedLine = line
                    .replace("===", "\u00A7e===")
                    .replace("Processed:", "\u00A77Processed: \u00A7f")
                    .replace("rejections:", "\u00A77rejections: \u00A7c")
                    .replace("by type:", "\u00A77by type:");
                source.sendSuccess(() -> Objects.requireNonNull(
                    Component.literal(formattedLine)), false);
            }
        }

        // Also show per-type counts using per-type getters
        PayloadValidation.PayloadMetrics metrics = PayloadValidation.getMetrics();

        if (!metrics.sizeRejectionsByType().isEmpty()) {
            source.sendSuccess(() -> Objects.requireNonNull(
                Component.literal("\u00A7e--- Size Rejections by Type ---")), false);
            for (Map.Entry<String, AtomicLong> entry : metrics.sizeRejectionsByType().entrySet()) {
                String type = entry.getKey();
                long count = PayloadValidation.getSizeRejectionCount(type);
                source.sendSuccess(() -> Objects.requireNonNull(
                    Component.literal(String.format("\u00A77  %s: \u00A7c%d", type, count))), false);
            }
        }

        if (!metrics.rateLimitRejectionsByType().isEmpty()) {
            source.sendSuccess(() -> Objects.requireNonNull(
                Component.literal("\u00A7e--- Rate Limit Rejections by Type ---")), false);
            for (Map.Entry<String, AtomicLong> entry : metrics.rateLimitRejectionsByType().entrySet()) {
                String type = entry.getKey();
                long count = PayloadValidation.getRateLimitRejectionCount(type);
                source.sendSuccess(() -> Objects.requireNonNull(
                    Component.literal(String.format("\u00A77  %s: \u00A7c%d", type, count))), false);
            }
        }

        if (!metrics.ipRateLimitRejectionsByType().isEmpty()) {
            source.sendSuccess(() -> Objects.requireNonNull(
                Component.literal("\u00A7e--- IP Rate Limit Rejections by Type ---")), false);
            for (Map.Entry<String, AtomicLong> entry : metrics.ipRateLimitRejectionsByType().entrySet()) {
                String type = entry.getKey();
                long count = PayloadValidation.getIpRateLimitRejectionCount(type);
                source.sendSuccess(() -> Objects.requireNonNull(
                    Component.literal(String.format("\u00A77  %s: \u00A7c%d", type, count))), false);
            }
        }

        LOGGER.info("Detailed network metrics requested via command");
        return 1;
    }

    /**
     * Reset all metrics (admin only).
     */
    private static int resetMetrics(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();

        // Capture pre-reset values for logging
        long preProcessed = PayloadValidation.getTotalPayloadsProcessed();
        long preRejections = PayloadValidation.getTotalSizeRejections()
            + PayloadValidation.getTotalRateLimitRejections()
            + PayloadValidation.getTotalIpRateLimitRejections();

        // Reset metrics
        PayloadValidation.resetMetrics();
        IpRateLimiter.INSTANCE.reset();

        source.sendSuccess(() -> Objects.requireNonNull(
            Component.literal("\u00A7a[Network] All payload metrics reset.")), true);
        source.sendSuccess(() -> Objects.requireNonNull(
            Component.literal(String.format("\u00A77Cleared: %,d processed, %,d rejections",
                preProcessed, preRejections))), false);

        LOGGER.info("Network metrics reset via command by {}: {} processed, {} rejections cleared",
            source.getTextName(), preProcessed, preRejections);
        return 1;
    }

    /**
     * Quick health check showing critical indicators.
     */
    private static int showHealth(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();

        long processed = PayloadValidation.getTotalPayloadsProcessed();
        long totalRejections = PayloadValidation.getTotalSizeRejections()
            + PayloadValidation.getTotalRateLimitRejections()
            + PayloadValidation.getTotalIpRateLimitRejections();
        long disconnects = PayloadValidation.getTotalDisconnects();

        double rejectionRate = (processed + totalRejections) > 0
            ? (double) totalRejections / (processed + totalRejections) * 100
            : 0.0;

        IpRateLimiter.Stats ipStats = IpRateLimiter.INSTANCE.getStats();

        // Determine health status
        String healthStatus;
        String healthColor;
        if (rejectionRate > 10.0 || disconnects > 5 || ipStats.blockedIps() > 10) {
            healthStatus = "DEGRADED";
            healthColor = "\u00A7c";
        } else if (rejectionRate > 2.0 || disconnects > 0 || ipStats.blockedIps() > 0) {
            healthStatus = "CAUTION";
            healthColor = "\u00A7e";
        } else {
            healthStatus = "HEALTHY";
            healthColor = "\u00A7a";
        }

        source.sendSuccess(() -> Objects.requireNonNull(
            Component.literal(String.format("\u00A77[Network] Status: %s%s", healthColor, healthStatus))), false);
        source.sendSuccess(() -> Objects.requireNonNull(
            Component.literal(String.format("\u00A77  Rejection Rate: \u00A7f%.2f%% \u00A77| Disconnects: \u00A7f%d \u00A77| Blocked IPs: \u00A7f%d",
                rejectionRate, disconnects, ipStats.blockedIps()))), false);

        return 1;
    }
}
