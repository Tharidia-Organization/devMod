# Quality Changelog

## Batch 1 - Imports / Order / Unused
- Reordered imports to match project grouping rules and removed unused imports.
- Replaced wildcard imports in core files and narrowed static imports.
- Touched files: src/main/java/com/devmod/DevMod.java, src/main/java/com/devmod/client/DevModClient.java, src/main/java/com/devmod/arena/alert/DiscordAlertChannel.java, src/main/java/com/devmod/debug/DebugManager.java, src/main/java/com/devmod/debug/DebugNetworkHandler.java, src/main/java/com/devmod/endurance/LeaderboardSystem.java, src/main/java/com/devmod/network/NetworkHandler.java.

## Batch 2 - Null-safety
- Annotated nullable fields and tightened nullable contracts where getters already return @Nullable.
- Copied @Nullable fields into locals before checks during serialization to avoid inconsistent reads.
- Touched files: src/main/java/com/devmod/runtime/InstanceData.java, src/main/java/com/devmod/runtime/PlayerInstanceSnapshot.java.

## Batch 3 - Logging Standardization
- Added errorId and common context IDs to console alert payloads for traceable logs.
- Touched files: src/main/java/com/devmod/arena/alert/ConsoleAlertChannel.java.

## Batch 4 - Comment Cleanup + Invariants
- Removed a redundant comment and replaced it with a rationale about client-thread execution.
- Touched files: src/main/java/com/devmod/client/DevModClient.java.

## Batch 5 - Micro-refactor
- Extracted small helpers for optional NBT fields to reduce duplication without behavior change.
- Touched files: src/main/java/com/devmod/runtime/PlayerInstanceSnapshot.java.

## Batch 6 - Client/Server Boundary
- Routed client-only NetworkHandler payloads through client-installed hooks to avoid common/client coupling.
- Guarded ClothConfigCompat parent screen reflection with a Dist.CLIENT check.
- Replaced GameDesignConfigManager's client-only path lookup with ConfigPaths for server safety.
- Touched files: src/main/java/com/devmod/network/NetworkHandler.java, src/main/java/com/devmod/client/DevModClient.java, src/main/java/com/devmod/client/network/ClientNetworkPayloadHooks.java, src/main/java/com/devmod/compat/mods/clothconfig/ClothConfigCompat.java, src/main/java/com/devmod/config/gamedesign/GameDesignConfigManager.java.
