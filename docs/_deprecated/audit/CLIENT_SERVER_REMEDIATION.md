# Client/Server Separation Remediation

**Last Updated:** 2025-12-24  
**Status:** In Progress (P0 crash fixes applied)

## Scope

Eliminate dedicated server crashes caused by client-only classes loading on the server.  
Primary rule: any class importing `net.minecraft.client.*` must live under a `/client/` package.

## Moves Applied (This Remediation)

| File | Old Package/Path | New Package/Path | Reason |
|------|------------------|------------------|--------|
| `DevModClient.java` | `com.devmod` | `com.devmod.client` | Client entrypoint must live under client package (uses `Minecraft.getInstance`) |
| `CameraShakeMixin.java` | `com.devmod.mixin` | `com.devmod.mixin.client` | Client-only mixin (imports `net.minecraft.client.*`) |
| `GameRendererMixin.java` | `com.devmod.mixin` | `com.devmod.mixin.client` | Client-only mixin (imports `net.minecraft.client.*`) |
| `ModelPartTransformMixin.java` | `com.devmod.mixin` | `com.devmod.mixin.client` | Client-only mixin (imports `net.minecraft.client.*`) |
| `LivingEntityRendererMixin.java` | `com.devmod.mixin` | `com.devmod.mixin.client` | Client-only mixin (imports `net.minecraft.client.*`) |
| `DebugRendererMixin.java` | `com.devmod.mixin` | `com.devmod.mixin.client` | Client-only mixin (imports `net.minecraft.client.*`) |
| `InteractionEvents.java` | `com.devmod.events` | `com.devmod.client.events` | Client-only event subscriber (Dist.CLIENT) |
| `ClientTelemetryBuffer.java` | `com.devmod.telemetry.duckdb.packets` | `com.devmod.client.telemetry` | Client-only singleton buffer |

## Residuals Found and Resolved

- `Minecraft.getInstance()`  
  - **Found in:** `DevModClient.java`  
  - **Fix:** Moved entrypoint to `com.devmod.client`.

- `Screen` / `GuiGraphics` / other `net.minecraft.client.*` imports  
  - **Found in:** client-only mixins under `com.devmod.mixin`  
  - **Fix:** moved to `com.devmod.mixin.client` and updated `devmod.mixins.json`.

- `KeyMapping` imports  
  - **Found in:** client-only compat/keybind classes (`com.devmod.client.compat.*`, `com.devmod.client.input.*`)  
  - **Fix:** registration gated behind `DistExecutor.safeRunWhenOn(Dist.CLIENT, ...)` to avoid server classloading.

## Call-Site Hardening

- Client-only compat modules are now registered via:
  - `com.devmod.client.compat.ClientCompatRegistrar`
  - `DistExecutor.safeRunWhenOn(Dist.CLIENT, ...)` in `ModIntegrationManager`

This prevents dedicated servers from loading client-only classes at init.

## Guardrails

### Script: Client Imports

`tools/check-client-imports.sh`

- Fails if any `import net.minecraft.client.*` appears outside a `/client/` package.
- Also checks for `Minecraft.getInstance()` outside `/client/`.

### Tests

`src/test/java/com/devmod/analysis/ClientServerSeparationTest.java`

- Flags client-only singleton usage in common packages.
- Rejects fully-qualified `com.devmod.client.*.INSTANCE` references in server/common code.

## Current Status

- **Client imports outside `/client/`:** 0  
- **Client mixins in common package:** 0  
- **Dedicated server startup:** expected crash-free once builds/tests complete
