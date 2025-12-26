# Dedicated Server Readiness

**Last Updated:** 2025-12-25  
**Status:** IN PROGRESS (P0 fixes applied)

## Run Commands

```bash
./gradlew build
./gradlew runServer
./gradlew runClient
```

## Crash-Free Checklist

- [x] No `net.minecraft.client.*` imports outside `/client/`
- [x] Client-only mixins live under `com.devmod.mixin.client`
- [x] `devmod.mixins.json` uses `client.*` entries
- [x] Client-only compat modules registered via `DistExecutor.safeRunWhenOn`
- [x] No `com.devmod.client.*.INSTANCE` references from common code
- [x] Ability packets validated and rate limited

## Verified Mixin Config (Excerpt)

```json
{
  "package": "com.devmod.mixin",
  "mixins": [
    "MinecraftServerAccessor",
    "RecipeManagerMixin",
    "DebugPacketsMixin"
  ],
  "client": [
    "client.GameRendererMixin",
    "client.CameraShakeMixin",
    "client.ModelPartTransformMixin",
    "client.LivingEntityRendererMixin",
    "client.DebugRendererMixin"
  ]
}
```

## Known Issues (Non-Blocking)

- RuntimeDistCleaner errors (e.g. `LayeredDraw$Layer`, `TextureSheetParticle`) can still appear if third-party mods in a given pack load client classes on the server.
- DevMod itself is side-safe; validate per-pack logs when integrating.

## Related Docs

- `docs/_deprecated/audit/CLIENT_SERVER_REMEDIATION.md`
- `docs/_deprecated/audit/MIXIN_SIDE_SAFETY.md`
- `docs/_deprecated/audit/STATIC_STATE_AND_LEAKS.md`
- `docs/_deprecated/remediation/VERIFY.md`
