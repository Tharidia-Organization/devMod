package com.devmod.network.handlers;

import com.devmod.network.ClientConfigFeedbackPayload;
import com.devmod.network.EditorApplyConfirmPayload;
import com.devmod.network.MobConfigConfirmPayload;
import com.devmod.network.PacketValidator;
import com.devmod.network.PacketValidator.ValidationResult;
import com.devmod.telemetry.duckdb.packets.TelemetryBatchPayload;
import com.devmod.telemetry.duckdb.packets.TelemetryPacketHandler;
import com.devmod.util.I18n;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Network handler for config sync, recipe sync, and telemetry packets.
 * Extracted from NetworkHandler for single responsibility.
 */
public final class ConfigNetworkHandler extends NetworkHandlerBase {

    private ConfigNetworkHandler() {}

    // =================================================================================
    // MOB CONFIG CONFIRMATION (client-side)
    // =================================================================================
    public static void handleMobConfigConfirm(MobConfigConfirmPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientConfigFeedbackPayload.handleMobConfigConfirm(payload));
    }

    // =================================================================================
    // EDITOR APPLY CONFIRMATION (client-side)
    // =================================================================================
    public static void handleEditorApplyConfirm(EditorApplyConfirmPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            if (mc != null && mc.screen instanceof com.devmod.ui.editor.ItemEditorScreen screen) {
                screen.onServerConfirm(payload);
            }
        });
    }

    // =================================================================================
    // GLOBAL CONFIG SYNC (client-side)
    // =================================================================================
    public static void handleGlobalConfigSync(com.devmod.network.GlobalConfigSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            payload.applyToClientConfigs();
            LOGGER.debug("[NetworkHandler] Received global config sync");
        });
    }

    // =================================================================================
    // RECIPE SYNC (server-side)
    // =================================================================================
    public static void handleRecipeSync(com.devmod.network.RecipeSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                PacketValidator security = security();
                ValidationResult validation = security.validatePacket(player, "recipe_sync", true);
                if (!validation.isSuccess()) {
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
            }
        });
    }

    // =================================================================================
    // RECIPE CLIENT SYNC (client-side)
    // =================================================================================
    public static void handleRecipeClientSync(com.devmod.network.RecipeClientSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            var operation = payload.operation();
            var recipes = payload.recipes();

            boolean firstSyncAll = true;
            for (var recipe : recipes) {
                switch (operation) {
                    case ADD -> {
                        com.devmod.recipe.RecipeConfigManager.addRecipeClientOnly(recipe);
                        LOGGER.debug("[NetworkHandler] Client received recipe: {}", recipe.id());
                    }
                    case DELETE -> {
                        com.devmod.recipe.RecipeConfigManager.removeRecipeClientOnly(recipe.id());
                        LOGGER.debug("[NetworkHandler] Client removed recipe: {}", recipe.id());
                    }
                    case SYNC_ALL -> {
                        if (firstSyncAll) {
                            com.devmod.recipe.RecipeConfigManager.clearClientRecipes();
                            firstSyncAll = false;
                        }
                        com.devmod.recipe.RecipeConfigManager.addRecipeClientOnly(recipe);
                    }
                }
            }

            LOGGER.info("[NetworkHandler] Client synced {} recipes (operation: {})", recipes.size(), operation);
        });
    }

    // =================================================================================
    // TELEMETRY BATCH (server-side)
    // =================================================================================
    public static void handleTelemetryBatch(TelemetryBatchPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                TelemetryPacketHandler.INSTANCE.handleBatch(player, payload);
            }
        });
    }
}
