# Remediation Changelog

**Last Updated:** 2025-12-25  
**Scope:** Dedicated Server Side-Safety

## Client/Server Separation

- Moved `DevModClient` to `com.devmod.client`.
- Moved client mixins to `com.devmod.mixin.client` and updated mixin config.
- Moved `InteractionEvents` to `com.devmod.client.events`.
- Moved `ClientTelemetryBuffer` to `com.devmod.client.telemetry`.
- Added `ClientCompatRegistrar` and gated client-only compat registration via `DistExecutor.safeRunWhenOn`.
- Added `DebugNetworkClientHandler` and routed debug sync via client handler.
- Gated `ClothConfigCompat` init to client-only to prevent server classloading.
- Hardened TerraBlender compat to avoid early static init crashes on server.

## Static State / Leaks

- `ModelPartTransformCapture` now has:
  - `clientTick()` cleanup hook
  - per-entity removal
- Client-side entity unload now clears:
  - `ModelPartTransformCapture`
  - `ModelPartTransformExtractor`
  - `TransformProviderRegistry`

## Network Hardening

- `AbilityActionPayload` decode now tolerates invalid ordinals.
- `AbilityNetworkHandler` validates ability + dodge direction and records rejections.
- `PacketValidator` now supports per-packet rate-limit overrides.
- `BuildProgressPayload` logs out-of-range values on decode.
- `NetworkHandler` no longer references client `.INSTANCE` fields.
- `DebugNetworkHandler` guards debug payload registration to avoid classloading crashes.
- `RadialMenuScreen` uses a named item class to avoid anonymous inner class load errors.

## Docs Updated

- `docs/audit/CLIENT_SERVER_REMEDIATION.md`
- `docs/audit/MIXIN_SIDE_SAFETY.md`
- `docs/audit/STATIC_STATE_AND_LEAKS.md`
- `docs/network/PACKET_REGISTRY.md`
- `docs/network/SECURITY_HARDENING.md`
- `docs/remediation/DEDICATED_SERVER_READINESS.md`
- `docs/remediation/VERIFY.md`
