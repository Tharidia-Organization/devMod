# Client/Server Separation Remediation

> Last updated: 2025-12-26
> Status: CURRENT (verified against code)

## Scope

Eliminate dedicated server crashes caused by client-only classes loading on the server.
Primary rule: any class importing `net.minecraft.client.*` must live under a `/client/` package
or be loaded behind a client-only gate.

## Verified Guardrails

- **Client entrypoint**: `com.devmod.client.DevModClient` is annotated with `@Mod(dist = Dist.CLIENT)`
  and `@EventBusSubscriber(..., value = Dist.CLIENT)`.
- **Client mixins**: listed under `client` in `src/main/resources/devmod.mixins.json` and live in
  `com.devmod.mixin.client`.
- **Client-only compat modules**: registered via `ClientCompatRegistrar` only when
  `FMLEnvironment.dist == Dist.CLIENT` inside `ModIntegrationManager`.
- **Client providers**: `TransformProviderRegistry` uses `FMLEnvironment.dist.isClient()` plus
  reflection to load `ClientTransformProvider` without server classloading.

## Enforcement Tools

### Script: Client Imports

`tools/check-client-imports.sh` rejects:
- `import net.minecraft.client.*` outside `/client/` packages
- `Minecraft.getInstance()` outside `/client/` packages

### Tests

`src/test/java/com/devmod/analysis/ClientServerSeparationTest.java` enforces:
- No client-only imports in common packages without a proper guard
- No direct references to client singletons from common packages
- Accepted guards include `FMLEnvironment.dist.isClient()`, `@OnlyIn(Dist.CLIENT)`, and delegate patterns

## Current Status

- Client-only code resides under `com.devmod.client` or `com.devmod.mixin.client`.
- `devmod.mixins.json` separates common and client mixins.
- Client-only registrations are gated behind `FMLEnvironment.dist` checks or client delegates.
