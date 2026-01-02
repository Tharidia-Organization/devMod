package com.devmod.mailbox.api.controllers;

/**
 * DTO for security and rate limiter metrics.
 */
public record SecurityMetricsDto(
    // Payload validation metrics
    long payloadsProcessed,
    long sizeRejections,
    long playerRateLimitRejections,
    long ipRateLimitRejections,
    long totalPayloadRejections,
    double rejectionRate,
    // IP rate limiter metrics
    long ipLimiterTotalRequests,
    long ipLimiterRateLimited,
    long ipLimiterBlocked,
    int ipLimiterTrackedIps,
    int ipLimiterBlockedIps,
    // Packet validator metrics
    long packetValidatorRejections,
    long packetValidatorPlayerRateLimits,
    long packetValidatorIpRateLimits,
    long timestampMillis
) {}
