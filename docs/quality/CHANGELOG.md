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

## Batch 7 - Client/Server Boundary (Handlers)
- Routed client-side handling in Config/Endurance/Party/Shield network handlers through client payload hooks.
- Removed direct client references while keeping dist guards intact.
- Routed GameMechanicsSyncPayload client application through client payload hooks to avoid client singleton references.
- Added @OnlyIn(Dist.CLIENT) to QuestEditorScreen for client screen annotation enforcement.
- Touched files: src/main/java/com/devmod/network/NetworkHandler.java, src/main/java/com/devmod/client/network/ClientNetworkPayloadHooks.java, src/main/java/com/devmod/network/handlers/ConfigNetworkHandler.java, src/main/java/com/devmod/network/handlers/EnduranceNetworkHandler.java, src/main/java/com/devmod/network/handlers/PartyNetworkHandler.java, src/main/java/com/devmod/network/handlers/ShieldNetworkHandler.java, src/main/java/com/devmod/network/GameMechanicsSyncPayload.java, src/main/java/com/devmod/client/quest/QuestEditorScreen.java.

## Batch 8 - Imports / Order / Unused (Round 2)
- Reordered imports to match project grouping rules and replaced wildcard imports with explicit lists.
- Touched files: src/main/java/com/devmod/party/PartyManager.java, src/main/java/com/devmod/party/PartyData.java, src/main/java/com/devmod/runtime/InstanceRegistry.java, src/main/java/com/devmod/config/GameplayOverridesManager.java, src/main/java/com/devmod/network/RecipeSyncPayload.java, src/main/java/com/devmod/network/RecipeClientSyncPayload.java, src/main/java/com/devmod/recipe/RecipeConfigManager.java, src/main/java/com/devmod/collision/bodypart/BodyPartHierarchy.java.

## Batch 9 - Null-safety (Round 2)
- Cached nullable reads before checks to avoid inconsistent access.
- Touched files: src/main/java/com/devmod/party/PartyData.java, src/main/java/com/devmod/runtime/InstanceRegistry.java.
