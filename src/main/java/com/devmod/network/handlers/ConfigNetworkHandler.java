package com.devmod.network.handlers;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import com.devmod.network.EditorApplyConfirmPayload;
import com.devmod.network.GlobalConfigSyncPayload;
import com.devmod.network.MobConfigConfirmPayload;
import com.devmod.network.NetworkHandler;
import com.devmod.network.PacketValidator;
import com.devmod.network.PacketValidator.ValidationResult;
import com.devmod.network.RecipeClientSyncPayload;
import com.devmod.telemetry.duckdb.packets.TelemetryBatchPayload;
import com.devmod.telemetry.duckdb.packets.TelemetryPacketHandler;
import com.devmod.util.I18n;

/**
 * Network handler for config sync, recipe sync, and telemetry packets.
 * Extracted from NetworkHandler for single responsibility.
 *
 * <p>Client-side handlers delegate to ClientConfigHandlers with proper dist checks
 * to ensure dedicated server compatibility.</p>
 */
public final class ConfigNetworkHandler extends NetworkHandlerBase {

    private ConfigNetworkHandler() {}

    // =================================================================================
    // RECIPE SYNC (server-side)
    // =================================================================================
    public static void handleRecipeSync(com.devmod.network.RecipeSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return; // Fail closed: invalid context
            }

            PacketValidator security = security();
            ValidationResult validation = security.validatePacket(player, "recipe_sync", true);
            if (!validation.isSuccess()) {
                security.recordRateLimitHit("recipe_sync", player.getName().getString());
                player.sendSystemMessage(I18n.errorWithDetails("devmod.ui.error", validation.getErrorMessage()));
                return;
            }

            var operation = payload.operation();
            var recipes = payload.recipes();

            boolean firstSyncAll = true;
            for (var recipe : recipes) {
                switch (operation) {
                    case ADD, UPDATE -> {
                        var validationResult = com.devmod.recipe.RecipeValidator.validate(recipe);
                        if (!validationResult.valid()) {
                            player.sendSystemMessage(I18n.errorWithDetails("devmod.recipe.invalid", validationResult.getFirstError()));
                            continue;
                        }
                        com.devmod.recipe.RecipeConfigManager.addRecipe(recipe);
                        com.devmod.recipe.RecipeReloadListener.onRecipeModified(recipe);
                        LOGGER.info("[NetworkHandler] Added/updated recipe: {}", recipe.id());
                    }
                    case DELETE -> {
                        com.devmod.recipe.RecipeConfigManager.removeRecipe(recipe.id());
                        com.devmod.recipe.RecipeReloadListener.onRecipeDeleted(recipe);
                        LOGGER.info("[NetworkHandler] Deleted recipe: {}", recipe.id());
                    }
                    case SYNC_ALL -> {
                        if (firstSyncAll) {
                            com.devmod.recipe.RecipeConfigManager.clearAllRecipes();
                            firstSyncAll = false;
                        }
                        com.devmod.recipe.RecipeConfigManager.addRecipe(recipe);
                    }
                }
            }

            player.sendSystemMessage(I18n.translate("devmod.recipe.saved"));
        });
    }

    // =================================================================================
    // TELEMETRY BATCH (server-side)
    // =================================================================================
    public static void handleTelemetryBatch(TelemetryBatchPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return; // Fail closed: invalid context
            }

            // Rate limit check - telemetry can be batched so we allow higher rate
            var validation = security().validatePacket(player, "telemetry_batch", false);
            if (!validation.isSuccess()) {
                security().recordRateLimitHit("telemetry_batch", player.getName().getString());
                return; // Fail closed: rate limited
            }

            TelemetryPacketHandler.INSTANCE.handleBatch(player, payload);
        });
    }

    // =================================================================================
    // GLOBAL CONFIG SYNC (client-side)
    // =================================================================================
    public static void handleGlobalConfigSync(GlobalConfigSyncPayload payload, IPayloadContext context) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            context.enqueueWork(() ->
                NetworkHandler.withClientHooks(hooks -> hooks.handleGlobalConfigSync(payload)));
        }
    }

    // =================================================================================
    // RECIPE CLIENT SYNC (client-side)
    // =================================================================================
    public static void handleRecipeClientSync(RecipeClientSyncPayload payload, IPayloadContext context) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            context.enqueueWork(() ->
                NetworkHandler.withClientHooks(hooks -> hooks.handleRecipeClientSync(payload)));
        }
    }

    // =================================================================================
    // EDITOR APPLY CONFIRM (client-side)
    // =================================================================================
    public static void handleEditorApplyConfirm(EditorApplyConfirmPayload payload, IPayloadContext context) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            context.enqueueWork(() ->
                NetworkHandler.withClientHooks(hooks -> hooks.handleConfigEditorApplyConfirm(payload)));
        }
    }

    // =================================================================================
    // MOB CONFIG CONFIRM (client-side)
    // =================================================================================
    public static void handleMobConfigConfirm(MobConfigConfirmPayload payload, IPayloadContext context) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            context.enqueueWork(() ->
                NetworkHandler.withClientHooks(hooks -> hooks.handleConfigMobConfigConfirm(payload)));
        }
    }
}
