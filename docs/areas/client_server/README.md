# Client/Server Boundary

> Last updated: 2025-12-26
> Status: CURRENT (verified against code)

This area documents how DevMod keeps client-only code isolated from server logic.

## Scope

- Client-only packages under `com.devmod.client.*`
- Dist checks via `FMLEnvironment.dist` and `@OnlyIn(Dist.CLIENT)`
- Network safety via `NetworkHandler.withClientHooks` and `ChannelId` validation
- Packet validation via `PacketValidator`

## Key Patterns

- Client-side payload handlers are accessed through `NetworkHandler.withClientHooks` and `ClientNetworkPayloadHooks`.
- Channel IDs are validated for collisions (`ChannelId.validateNoCollisions`).
- Packet payloads are validated and clamped by `PacketValidator` before handling.

## Structure

- Client UI, input, and rendering are under `com.devmod.client.*`.
- Network handlers are in `com.devmod.network.handlers` with client hooks in `com.devmod.client.network`.
- Server-side telemetry packet handling is in `com.devmod.telemetry.duckdb.packets`.

## Automated Validation

- `ClientServerSeparationTest`
- `ClientScreenAnnotationTest`
- `ChannelIdDirectTest`
- `PacketValidatorDirectTest`
- `NetworkHandlerDirectTest`

## Cross-References

- `docs/areas/client_server/CLIENT_BOUNDARY_AUDIT.md`
- `docs/cross_cutting/CLIENT_SERVER.md`
