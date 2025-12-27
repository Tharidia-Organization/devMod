# Client/Server Safety

> Last updated: 2025-12-26
> Status: CURRENT (verified against code)

This document captures the guardrails used to keep client-only code from loading on dedicated servers.

## Principles

- Client-only classes live under `com.devmod.client.*` (UI, overlays, input, render helpers).
- Client-only mixins are under `com.devmod.mixin.client.*`.
- Client-only initialization happens in `DevModClient` (keybinds, overlays, UI hooks).
- Common code never calls `Minecraft.getInstance()` directly; it goes through client hooks.

## Core Patterns

### Dist Gating

- Use `@OnlyIn(Dist.CLIENT)` on client-only classes.
- Use `FMLEnvironment.dist` checks in shared entrypoints when needed.

### Client Hooks for Payloads

- `NetworkHandler.withClientHooks(...)` dispatches to `ClientNetworkPayloadHooks` when available.
- `DevModClient` sets the client hooks via `NetworkHandler.setClientPayloadHooks(...)`.
- Payload handlers always use `context.enqueueWork(...)` to run on the correct thread.

## Related Tests

- `ClientServerSeparationTest`
- `ClientScreenAnnotationTest`
- `NetworkHandlerDirectTest`
