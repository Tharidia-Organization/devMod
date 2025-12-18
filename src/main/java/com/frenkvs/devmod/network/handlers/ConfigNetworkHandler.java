package com.frenkvs.devmod.network.handlers;

import com.frenkvs.devmod.network.ClientConfigFeedback;
import com.frenkvs.devmod.network.EditorApplyConfirmPayload;
import com.frenkvs.devmod.network.MobConfigConfirmPayload;
import com.frenkvs.devmod.network.PacketSecurityService;
import com.frenkvs.devmod.network.PacketSecurityService.ValidationResult;
import com.frenkvs.devmod.telemetry.duckdb.packets.TelemetryBatchPayload;
import com.frenkvs.devmod.telemetry.duckdb.packets.TelemetryPacketHandler;
import com.frenkvs.devmod.util.I18n;
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
        context.enqueueWork(() -> ClientConfigFeedback.handleMobConfigConfirm(payload));
    }

    // =================================================================================
    // EDITOR APPLY CONFIRMATION (client-side)
    // =================================================================================
    public static void handleEditorApplyConfirm(EditorApplyConfirmPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            if (mc != null && mc.screen instanceof com.frenkvs.devmod.ui.editor.ItemEditorScreen screen) {
                screen.onServerConfirm(payload);
            }
        });
    }

    // =================================================================================
    // GLOBAL CONFIG SYNC (client-side)
    // =================================================================================
    public static void handleGlobalConfigSync(com.frenkvs.devmod.network.GlobalConfigSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            payload.applyToClientConfigs();
            LOGGER.debug("[NetworkHandler] Received global config sync");
        });
    }

    // =================================================================================
    // RECIPE SYNC (server-side)
    // =================================================================================
    public static void handleRecipeSync(com.frenkvs.devmod.network.RecipeSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                PacketSecurityService security = security();
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
                            var validationResult = com.frenkvs.devmod.recipe.RecipeValidator.validate(recipe);
                            if (!validationResult.valid()) {
                                player.sendSystemMessage(I18n.errorWithDetails("devmod.recipe.invalid", validationResult.getFirstError()));
                                continue;
                            }
                            com.frenkvs.devmod.recipe.RecipeConfigManager.addRecipe(recipe);
                            com.frenkvs.devmod.recipe.RecipeReloadListener.onRecipeModified(recipe);
                            LOGGER.info("[NetworkHandler] Added/updated recipe: {}", recipe.id());
                        }
                        case DELETE -> {
                            com.frenkvs.devmod.recipe.RecipeConfigManager.removeRecipe(recipe.id());
                            com.frenkvs.devmod.recipe.RecipeReloadListener.onRecipeDeleted(recipe);
                            LOGGER.info("[NetworkHandler] Deleted recipe: {}", recipe.id());
                        }
                        case SYNC_ALL -> {
                            if (firstSyncAll) {
                                com.frenkvs.devmod.recipe.RecipeConfigManager.clearAllRecipes();
                                firstSyncAll = false;
                            }
                            com.frenkvs.devmod.recipe.RecipeConfigManager.addRecipe(recipe);
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
    public static void handleRecipeClientSync(com.frenkvs.devmod.network.RecipeClientSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            var operation = payload.operation();
            var recipes = payload.recipes();

            boolean firstSyncAll = true;
            for (var recipe : recipes) {
                switch (operation) {
                    case ADD -> {
                        com.frenkvs.devmod.recipe.RecipeConfigManager.addRecipeClientOnly(recipe);
                        LOGGER.debug("[NetworkHandler] Client received recipe: {}", recipe.id());
                    }
                    case DELETE -> {
                        com.frenkvs.devmod.recipe.RecipeConfigManager.removeRecipeClientOnly(recipe.id());
                        LOGGER.debug("[NetworkHandler] Client removed recipe: {}", recipe.id());
                    }
                    case SYNC_ALL -> {
                        if (firstSyncAll) {
                            com.frenkvs.devmod.recipe.RecipeConfigManager.clearClientRecipes();
                            firstSyncAll = false;
                        }
                        com.frenkvs.devmod.recipe.RecipeConfigManager.addRecipeClientOnly(recipe);
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
