# Network Payload Limits and Policies

This document describes the network payload validation system used in DevMod to protect against abuse, DoS attacks, and ensure fair resource usage.

## Overview

DevMod implements a multi-layer validation system for all network payloads:

1. **Size Validation** - Prevents oversized packets from being processed
2. **Per-Player Rate Limiting** - Prevents packet flooding from individual accounts
3. **Per-IP Rate Limiting** - Catches multi-account abuse and pre-auth attacks
4. **Disconnect Policy** - Kicks players after repeated violations

## Payload Size Limits

Each payload type has a maximum allowed size:

| Category | Max Size | Rate Limit | Use Case |
|----------|----------|------------|----------|
| `SMALL` | 1 KB | 100/min | Actions, toggles, simple requests |
| `MEDIUM` | 8 KB | 60/min | Config updates, single entity data |
| `LARGE` | 32 KB | 30/min | Batch updates, telemetry |
| `XLARGE` | 64 KB | 15/min | Bulk sync, complex data |
| `SYNC_MEDIUM` | 512 KB | 30/min | Bulk lists, client state |
| `SYNC_LARGE` | 2 MB | 10/min | Full mailbox/news sync |
| `MAILBOX` | 16 KB | 30/min | Mailbox messages |
| `TELEMETRY` | 64 KB | 10/min | Telemetry batches |
| `TICKET` | 32 KB | 20/min | Ticket system |
| `QUEST_ACTION` | 2 KB | 600/min | Quest/party actions (10/sec) |
| `EDITOR` | 16 KB | 60/min | Item editor updates |
| `NONE` | Unlimited | Unlimited | Trusted internal payloads |

## Per-IP Rate Limits

Additional per-IP limits catch multi-account abuse:

| Category | Limit | Block Duration | Purpose |
|----------|-------|----------------|---------|
| `login` | 10/min | 10 min | Brute force protection |
| `mailbox_send` | 30/min | 5 min | Spam prevention |
| `ticket_create` | 5/min | 15 min | Abuse prevention |
| `telemetry_batch` | 20/min | 5 min | Server load protection |
| `ability_action` | 60/min | 2 min | Combat fairness |
| `quest_action` | 120/min | 2 min | Game loop protection |
| Default | 100/min | 5 min | Catch-all |

## Disconnect Policy

Players are disconnected after **10 consecutive violations** within a 60-second window.

The violation count resets after:
- 60 seconds of no violations
- Successful reconnection

## Usage

### Wrapping a Handler

```java
// Instead of:
event.registrar(...).playToServer(TYPE, CODEC, MyHandler::handle);

// Use:
event.registrar(...).playToServer(TYPE, CODEC,
    PayloadValidation.validated(MyHandler::handle, PayloadLimits.MAILBOX));
```

### Implementing SizedPayload

For accurate size validation, implement the `SizedPayload` interface:

```java
public record MyPayload(...) implements CustomPacketPayload, SizedPayload {
    @Override
    public int estimatePayloadSize() {
        int size = 4; // Base overhead
        size += subject.getBytes(StandardCharsets.UTF_8).length;
        size += body.getBytes(StandardCharsets.UTF_8).length;
        return size;
    }
}
```

### Per-IP Rate Limiting

```java
// Check IP rate limit for a specific category
if (!IpRateLimiter.INSTANCE.checkRateLimit(player, "mailbox_send")) {
    // Rate limited - reject the request
    return;
}
```

## Metrics and Monitoring

The following metrics are available for monitoring:

### Size Rejection Metrics
- `totalSizeRejections` - Total payloads rejected for size
- Per-type size rejection counts
- Maximum over-limit size per type

### Rate Limit Metrics
- `totalRateLimitRejections` - Total player rate limit hits
- `totalIpRateLimitRejections` - Total IP rate limit hits
- Per-category rate limit counts

### Access via Admin API

```json
GET /api/telemetry/payload-metrics

{
  "totalPayloadsProcessed": 15234,
  "totalSizeRejections": 12,
  "totalRateLimitRejections": 45,
  "totalIpRateLimitRejections": 8,
  "totalDisconnects": 2,
  "perTypeMetrics": { ... }
}
```

## Security Considerations

1. **Pre-authentication attacks**: IP rate limiting protects against attacks before player authentication

2. **Multi-account abuse**: IP limits are shared across all accounts from the same IP

3. **Amplification attacks**: Size limits prevent memory exhaustion from oversized packets

4. **Timing attacks**: Rate limits use sliding windows to prevent burst attacks

5. **Evasion**: Both UUID and IP limits must pass; attackers cannot bypass by switching accounts

## Cleanup and Memory Management

Rate limit state is automatically cleaned up:
- Stale entries removed every 60 seconds
- Entry TTL: 10 minutes
- Blocked IPs cleared after block duration expires

Call `PayloadValidation.cleanupStaleEntries()` periodically (e.g., on server tick) for additional cleanup.

## Configuration

Currently, limits are hardcoded for security. Future versions may support:
- Config-based limits with safe defaults
- Per-server adjustments
- Dynamic throttling based on server load

## Troubleshooting

### "Payload too large" rejections
- Check if payload size estimate is accurate
- Consider using a larger limit category if justified
- Review if payload data can be split or compressed

### High rate limit rejections
- Check for client bugs causing packet spam
- Review action frequency limits for gameplay balance
- Monitor for abuse patterns

### Unexpected disconnects
- Review violation logs for the player
- Check if client is sending malformed packets
- Verify packet timing is within limits
