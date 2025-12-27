# DevMod Network Packet Registry

> Last updated: 2025-12-26
> Status: CURRENT (verified against code)

**Source of truth:** `src/main/java/com/devmod/network/ChannelId.java` (IDs, direction, payload names).

Payloads are registered via `RegisterPayloadHandlersEvent` in `NetworkHandler`, with debug payloads in `DebugNetworkHandler`.

## Channel Ranges (from ChannelId)

- 1-4: Mob/Item
- 5-25: Endurance
- 26-35: Party
- 36-45: Config/Telemetry
- 46-55: Item stats
- 56-65: Shield
- 66-75: Ability + LVC
- 76-85: Arena
- 86-89: Challenges
- 90-91: Debug
- 92-99: Season pass
- 100-110: Mailbox

## Registration Points

- `NetworkHandler.registerPayloads(...)` registers the main payloads.
- `DebugNetworkHandler.registerPayloads(...)` registers debug payloads.
- Mailbox handlers live in `com.devmod.mailbox.network.MailboxNetworkHandler`.
- Telemetry batches route through `ConfigNetworkHandler` and `TelemetryPacketHandler`.

## Validation and Safety

- `PacketValidator` enforces inbound clamps and rate limits.
- `ChannelId.validateNoCollisions()` runs during network registration.
- Client-only payload handling is gated via `NetworkHandler.withClientHooks(...)`.

## How to Audit

- Review `ChannelId` for IDs and directions.
- Search for `event.registrar` in `NetworkHandler` and `DebugNetworkHandler` to see payload mappings.
