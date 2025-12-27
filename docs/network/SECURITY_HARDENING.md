# DevMod Network Security Hardening

> Last updated: 2025-12-24
> Status: CURRENT (verified against code)

## Policy

1. **Fail closed** on invalid context or validation failure.  
2. **Rate limit** all C->S packets.  
3. **Validate payload bounds** before processing.  
4. **Record telemetry** for drops/rejections.

## Rate Limiting

Implemented in `PacketValidator`:

```java
private static final int DEFAULT_RATE_LIMIT = 10; // per 1s
private static final Map<String, Integer> RATE_LIMIT_OVERRIDES = Map.of(
    "ability_action", 5,
    "shop_purchase", 5,
    "recipe_sync", 3,
    "telemetry_batch", 20
);
```

All C->S handlers call `validatePacket(...)` and fail closed on error.

## Payload Validation

### AbilityActionPayload (C->S)

- Decode now tolerates invalid ordinals.
- `AbilityNetworkHandler` rejects:
  - null/invalid ability
  - out-of-range dodge direction

### BuildProgressPayload (S->C)

- Constructor clamps all fields to safe ranges.
- Decoder logs out-of-range data.

## Telemetry Hooks

`PacketValidator` provides:

- `recordRateLimitHit(packetType, playerName)`
- `recordRejection(packetType, reason)`

`AbilityNetworkHandler` uses both for ability spam / invalid payloads.

## Fail-Closed Example

```java
if (!(context.player() instanceof ServerPlayer player)) {
    return;
}
var validation = security().validatePacket(player, "ability_action", false);
if (!validation.isSuccess()) {
    security().recordRateLimitHit("ability_action", player.getName().getString());
    return;
}
```

## Validation Bounds (Core)

Bounds are centralized in `PacketValidator` (health, damage, armor, ranged stats, etc.).  
Use those clamps for C->S payloads that carry numeric values.
