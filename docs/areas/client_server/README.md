# Client/Server Boundary

> Last updated: 2025-12-26
> Status: NEEDS_VERIFICATION
> Risk Level: MEDIUM (client-only misuse can crash dedicated servers)

---

## 1. Purpose

This area documents how DevMod keeps client-only code isolated from server logic:

- **Client-only packages** under `com.devmod.client`
- **Dist checks** via `FMLEnvironment.dist` and `@OnlyIn(Dist.CLIENT)`
- **Network safety** via `NetworkHandler.withClientHooks` and `ChannelId` validation
- **Input validation** via `PacketValidator`

---

## 2. Key Patterns (Implemented)

### Client Hooks for Payloads
Client-side handlers are accessed only through `NetworkHandler.withClientHooks`:
- Server classes avoid direct `Minecraft.getInstance()` calls.
- Client hooks are registered in client init and invoked only when present.

### Channel ID Validation
`ChannelId.validateNoCollisions()` runs at registration to prevent duplicate IDs within a direction.

### Packet Validation
`PacketValidator` clamps numeric values and rate-limits sensitive packets.

---

## 3. Structure

- Client-only UI, input, and rendering live in `com.devmod.client.*`.
- Network handlers are in `com.devmod.network.handlers` and split by domain.
- Telemetry batch payloads are handled server-side via `TelemetryPacketHandler`.

---

## 4. Automated Validation

| Behavior | Test |
|----------|------|
| Channel ID collision guard | `ChannelIdDirectTest` |
| Packet value clamping | `PacketValidatorDirectTest` |
| Client hooks dispatch safety | `NetworkHandlerDirectTest` |

---

## Cross-References

- `docs/areas/client_server/CLIENT_BOUNDARY_AUDIT.md`
- `docs/cross_cutting/CLIENT_SERVER.md`
