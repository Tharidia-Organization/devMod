package com.frenkvs.devmod.recipe;

import com.frenkvs.devmod.DevMod;
import com.frenkvs.devmod.network.RecipeSyncPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;
import java.util.Objects;

import static com.frenkvs.devmod.DevMod.MODID;

/**
 * Handles recipe synchronization during datapack reload and server lifecycle events.
 * Ensures custom recipes are properly synced to clients and injected into the RecipeManager.
 */
@EventBusSubscriber(modid = MODID)
public final class RecipeReloadListener {

    private RecipeReloadListener() {}

    // ═══════════════════════════════════════════════════════════════
    // SERVER LIFECYCLE
    // ═══════════════════════════════════════════════════════════════

    /**
     * Called when server starts. Initialize recipe manager and inject custom recipes.
     */
    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        DevMod.LOGGER.info("[RecipeReloadListener] Server started, initializing recipe system");

        // Initialize the config manager with server config directory
        var server = event.getServer();
        var configDir = server.getWorldPath(Objects.requireNonNull(
            net.minecraft.world.level.storage.LevelResource.ROOT, "root"
        ))
            .resolve("serverconfig").resolve("devmod");

        RecipeConfigManager.initializeServer(configDir);

        // Inject custom recipes into the game
        RecipeInjector.injectAll(server.getRecipeManager());

        DevMod.LOGGER.info("[RecipeReloadListener] Loaded {} custom recipes",
            RecipeConfigManager.getRecipeCount());
    }

    /**
     * Called when server is stopping. Save any pending recipes and reset state.
     */
    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        DevMod.LOGGER.info("[RecipeReloadListener] Server stopping, cleaning up recipe system");

        // Save any pending changes
        RecipeConfigManager.saveServerRecipes();

        // Clear injected recipes
        RecipeInjector.clear();

        // Reset manager state to allow re-initialization on next server start
        RecipeConfigManager.reset();

        DevMod.LOGGER.info("[RecipeReloadListener] Recipe system cleanup complete");
    }

    // ═══════════════════════════════════════════════════════════════
    // DATAPACK SYNC
    // ═══════════════════════════════════════════════════════════════

    /**
     * Called when datapacks are synced to a player (on join or /reload).
     * Sends all custom recipes to the joining player.
     */
    @SubscribeEvent
    public static void onDatapackSync(OnDatapackSyncEvent event) {
        // Defensive null checks
        if (event == null) {
            DevMod.LOGGER.warn("[RecipeReloadListener] Received null OnDatapackSyncEvent");
            return;
        }

        ServerPlayer player = event.getPlayer();

        if (player != null) {
            // Single player joining - send recipes to them
            syncRecipesToPlayer(player);
        } else {
            // Server reload (/reload command) - sync to all players
            var playerList = event.getPlayerList();
            if (playerList == null) {
                DevMod.LOGGER.warn("[RecipeReloadListener] PlayerList is null in OnDatapackSyncEvent");
                return;
            }

            var server = playerList.getServer();
            if (server == null) {
                DevMod.LOGGER.warn("[RecipeReloadListener] Server is null in OnDatapackSyncEvent");
                return;
            }

            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                if (p != null) {
                    syncRecipesToPlayer(p);
                }
            }

            // Re-inject recipes after reload
            RecipeInjector.injectAll(server.getRecipeManager());
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // SYNC HELPERS
    // ═══════════════════════════════════════════════════════════════

    /**
     * Send all custom recipes to a specific player.
     */
    public static void syncRecipesToPlayer(ServerPlayer player) {
        if (player == null) {
            DevMod.LOGGER.warn("[RecipeReloadListener] Cannot sync recipes to null player");
            return;
        }

        try {
            List<RecipeData> recipes = RecipeConfigManager.getAllCustomRecipes();

            if (recipes.isEmpty()) {
                DevMod.LOGGER.debug("[RecipeReloadListener] No custom recipes to sync to {}",
                    player.getName().getString());
                return;
            }

            RecipeSyncPayload payload = RecipeSyncPayload.syncAll(Objects.requireNonNull(recipes, "recipes"));
            PacketDistributor.sendToPlayer(
                Objects.requireNonNull(player, "player"),
                Objects.requireNonNull(payload, "payload")
            );

            DevMod.LOGGER.debug("[RecipeReloadListener] Synced {} recipes to {}",
                recipes.size(), player.getName().getString());
        } catch (Exception e) {
            DevMod.LOGGER.error("[RecipeReloadListener] Failed to sync recipes to player: {}",
                e.getMessage());
        }
    }

    /**
     * Broadcast a recipe change to all connected players.
     */
    public static void broadcastRecipeChange(RecipeData recipe, RecipeSyncPayload.SyncOperation operation) {
        if (recipe == null || recipe.id() == null) {
            DevMod.LOGGER.warn("[RecipeReloadListener] Cannot broadcast null recipe");
            return;
        }

        if (operation == null) {
            DevMod.LOGGER.warn("[RecipeReloadListener] Cannot broadcast with null operation");
            return;
        }

        var server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            DevMod.LOGGER.debug("[RecipeReloadListener] Server not available for broadcast");
            return;
        }

        try {
            RecipeSyncPayload payload = new RecipeSyncPayload(
                Objects.requireNonNull(List.of(recipe), "recipes"),
                true,
                Objects.requireNonNull(operation, "operation")
            );

            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (player != null) {
                    PacketDistributor.sendToPlayer(
                        Objects.requireNonNull(player, "player"),
                        Objects.requireNonNull(payload, "payload")
                    );
                }
            }

            DevMod.LOGGER.debug("[RecipeReloadListener] Broadcast {} for recipe {}",
                operation, recipe.id());
        } catch (Exception e) {
            DevMod.LOGGER.error("[RecipeReloadListener] Failed to broadcast recipe change: {}",
                e.getMessage());
        }
    }

    /**
     * Called after a recipe is added/updated on the server.
     * Re-injects recipes and notifies clients.
     */
    public static void onRecipeModified(RecipeData recipe) {
        if (recipe == null || recipe.id() == null) {
            DevMod.LOGGER.warn("[RecipeReloadListener] Cannot process null recipe modification");
            return;
        }

        var server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            DevMod.LOGGER.debug("[RecipeReloadListener] Server not available for recipe modification");
            return;
        }

        // Re-inject into RecipeManager
        RecipeInjector.injectSingle(server.getRecipeManager(), recipe);

        // Broadcast to clients
        broadcastRecipeChange(recipe, RecipeSyncPayload.SyncOperation.UPDATE);
    }

    /**
     * Called after a recipe is deleted on the server.
     */
    public static void onRecipeDeleted(RecipeData recipe) {
        if (recipe == null || recipe.id() == null) {
            DevMod.LOGGER.warn("[RecipeReloadListener] Cannot process null recipe deletion");
            return;
        }

        var server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            DevMod.LOGGER.debug("[RecipeReloadListener] Server not available for recipe deletion");
            return;
        }

        // Remove from RecipeManager
        RecipeInjector.removeSingle(server.getRecipeManager(), recipe.id());

        // Broadcast to clients
        broadcastRecipeChange(recipe, RecipeSyncPayload.SyncOperation.DELETE);
    }
}
