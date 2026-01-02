# Mixin Side Safety Audit

**Last Updated:** 2025-12-24  
**Config:** `src/main/resources/devmod.mixins.json`

## Summary

All client-only mixins live under `com.devmod.mixin.client` and are listed in the `client` section of the mixin config.  
Common/server mixins remain under `com.devmod.mixin` and are listed in `mixins`.

## Audit Table

| Mixin Class | Target | Side | Config Section | Risk | Fix |
|-------------|--------|------|----------------|------|-----|
| `com.devmod.mixin.client.GameRendererMixin` | `net.minecraft.client.renderer.GameRenderer` | CLIENT | `client` | NONE | Moved under `mixin.client` |
| `com.devmod.mixin.client.CameraShakeMixin` | `net.minecraft.client.Camera` | CLIENT | `client` | NONE | Moved under `mixin.client` |
| `com.devmod.mixin.client.ModelPartTransformMixin` | `net.minecraft.client.model.geom.ModelPart` | CLIENT | `client` | NONE | Moved under `mixin.client` |
| `com.devmod.mixin.client.LivingEntityRendererMixin` | `net.minecraft.client.renderer.entity.LivingEntityRenderer` | CLIENT | `client` | NONE | Moved under `mixin.client` |
| `com.devmod.mixin.client.DebugRendererMixin` | `net.minecraft.client.renderer.debug.DebugRenderer` | CLIENT | `client` | NONE | Moved under `mixin.client` |
| `com.devmod.mixin.DebugPacketsMixin` | `net.minecraft.network.protocol.game.DebugPackets` | COMMON | `mixins` | NONE | Server-safe |
| `com.devmod.mixin.MinecraftServerAccessor` | `net.minecraft.server.MinecraftServer` | COMMON | `mixins` | NONE | Server-safe accessor |
| `com.devmod.mixin.RecipeManagerMixin` | `net.minecraft.world.item.crafting.RecipeManager` | COMMON | `mixins` | NONE | Common-safe |

## Config Snapshot

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

## Checks

- Client mixins only target client classes and never load on dedicated server.
- Common mixins target server/common classes only.
- `package`, `refmap`, `compatibilityLevel`, and `required` are consistent.
