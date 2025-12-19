package com.frenkvs.devmod.recipe;

import com.frenkvs.devmod.DevMod;
import com.frenkvs.devmod.network.RecipeClientSyncPayload;
import com.frenkvs.devmod.util.ConfigPaths;
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

        // Use ConfigPaths for cross-platform compatibility (Linux/Windows/Mac)
        // This ensures recipes are stored in config/devmod/recipes/ instead of world-specific paths
        RecipeConfigManager.initializeServer(ConfigPaths.getRecipesDir());

        // Inject custom recipes into the game
        RecipeInjector.injectAll(event.getServer().getRecipeManager());

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
     * Syncs custom recipes to client and re-injects into RecipeManager on reload.
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
            // Single player joining - sync all recipes to them
            syncRecipesToPlayer(player);
        } else {
            // Server reload (/reload command)
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

            // Re-inject recipes after reload
            RecipeInjector.injectAll(server.getRecipeManager());
            DevMod.LOGGER.info("[RecipeReloadListener] Reloaded {} custom recipes",
                RecipeConfigManager.getRecipeCount());

            // Sync to all connected players
            for (ServerPlayer p : playerList.getPlayers()) {
                syncRecipesToPlayer(p);
            }
        }
    }

    /**
     * Sync all custom recipes to a specific player.
     */
    private static void syncRecipesToPlayer(ServerPlayer player) {
        ServerPlayer safePlayer = Objects.requireNonNull(player, "player");
        List<RecipeData> recipes = RecipeConfigManager.getAllCustomRecipes();
        if (recipes.isEmpty()) {
            DevMod.LOGGER.debug("[RecipeReloadListener] No recipes to sync to player {}",
                safePlayer.getName().getString());
            return;
        }

        RecipeClientSyncPayload payload = RecipeClientSyncPayload.syncAll(recipes);
        PacketDistributor.sendToPlayer(safePlayer, Objects.requireNonNull(payload, "recipe sync payload"));

        DevMod.LOGGER.debug("[RecipeReloadListener] Synced {} recipes to player {}",
            recipes.size(), safePlayer.getName().getString());
    }

    // ═══════════════════════════════════════════════════════════════
    // RECIPE MODIFICATION HANDLERS
    // ═══════════════════════════════════════════════════════════════

    /**
     * Called after a recipe is added/updated on the server.
     * Re-injects recipe into RecipeManager and broadcasts to all clients.
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
        DevMod.LOGGER.debug("[RecipeReloadListener] Recipe {} injected into RecipeManager", recipe.id());

        // Broadcast to all connected clients
        RecipeClientSyncPayload payload = RecipeClientSyncPayload.add(recipe);
        PacketDistributor.sendToAllPlayers(Objects.requireNonNull(payload, "recipe add payload"));
        DevMod.LOGGER.debug("[RecipeReloadListener] Broadcasted recipe {} to all clients", recipe.id());
    }

    /**
     * Called after a recipe is deleted on the server.
     * Removes from RecipeManager and broadcasts deletion to all clients.
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
        DevMod.LOGGER.debug("[RecipeReloadListener] Recipe {} removed from RecipeManager", recipe.id());

        // Broadcast deletion to all connected clients
        RecipeClientSyncPayload payload = RecipeClientSyncPayload.delete(recipe);
        PacketDistributor.sendToAllPlayers(Objects.requireNonNull(payload, "recipe delete payload"));
        DevMod.LOGGER.debug("[RecipeReloadListener] Broadcasted recipe deletion {} to all clients", recipe.id());
    }
}
