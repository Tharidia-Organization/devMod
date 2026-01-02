package com.devmod.network.handlers;

import net.minecraft.server.level.ServerPlayer;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import com.devmod.network.ChannelId;
import com.devmod.network.EditorApplyConfirmPayload;
import com.devmod.network.GlobalConfigSyncPayload;
import com.devmod.network.MobConfigConfirmPayload;
import com.devmod.network.NetworkHandler;
import com.devmod.network.PacketValidator;
import com.devmod.network.PacketValidator.ValidationResult;
import com.devmod.network.PayloadValidation.PayloadLimits;
import com.devmod.network.RecipeClientSyncPayload;
import com.devmod.network.RecipeSyncPayload;
import com.devmod.telemetry.duckdb.packets.TelemetryBatchPayload;
import com.devmod.telemetry.duckdb.packets.TelemetryPacketHandler;
import com.devmod.util.I18n;

import static com.devmod.network.PayloadValidation.validated;

/**
 * P2: Domain-specific network handler for config/recipe/telemetry payloads.
 */
public final class ConfigNetworkHandler extends NetworkHandlerBase implements PayloadRegistrar {

    public static final ConfigNetworkHandler INSTANCE = new ConfigNetworkHandler();

    private ConfigNetworkHandler() {}

    // =================================================================================
    // PAYLOAD REGISTRATION (P2: Domain-specific registration)
    // =================================================================================

    @Override
    public String getDomainName() {
        return "config";
    }

    @Override
    public void registerPayloads(RegisterPayloadHandlersEvent event) {
        // RECIPE_SYNC: Client -> Server recipe modifications
        event.registrar(ChannelId.RECIPE_SYNC.asString()).playToServer(
            nn(RecipeSyncPayload.TYPE),
            nn(RecipeSyncPayload.STREAM_CODEC),
            validated(ConfigNetworkHandler::handleRecipeSync, PayloadLimits.LARGE)
        );

        // TELEMETRY_BATCH: Client -> Server telemetry events
        event.registrar(ChannelId.TELEMETRY_BATCH.asString()).playToServer(
            nn(TelemetryBatchPayload.TYPE),
            nn(TelemetryBatchPayload.STREAM_CODEC),
            validated(ConfigNetworkHandler::handleTelemetryBatch, PayloadLimits.TELEMETRY)
        );

        // GLOBAL_CONFIG_SYNC: Server -> Client config sync
        event.registrar(ChannelId.GLOBAL_CONFIG_SYNC.asString()).playToClient(
            nn(GlobalConfigSyncPayload.TYPE),
            nn(GlobalConfigSyncPayload.STREAM_CODEC),
            ConfigNetworkHandler::handleGlobalConfigSync
        );

        // RECIPE_CLIENT_SYNC: Server -> Client recipe sync
        event.registrar(ChannelId.RECIPE_CLIENT_SYNC.asString()).playToClient(
            nn(RecipeClientSyncPayload.TYPE),
            nn(RecipeClientSyncPayload.STREAM_CODEC),
            ConfigNetworkHandler::handleRecipeClientSync
        );

        // EDITOR_APPLY_CONFIRM: Server -> Client editor confirmation
        event.registrar(ChannelId.EDITOR_APPLY_CONFIRM.asString()).playToClient(
            nn(EditorApplyConfirmPayload.TYPE),
            nn(EditorApplyConfirmPayload.STREAM_CODEC),
            ConfigNetworkHandler::handleEditorApplyConfirm
        );

        // MOB_CONFIG_CONFIRM: Server -> Client mob config confirmation
        event.registrar(ChannelId.MOB_CONFIG_CONFIRM.asString()).playToClient(
            nn(MobConfigConfirmPayload.TYPE),
            nn(MobConfigConfirmPayload.STREAM_CODEC),
            ConfigNetworkHandler::handleMobConfigConfirm
        );
    }

    // =================================================================================
    // RECIPE SYNC (server-side)
    // =================================================================================
    public static void handleRecipeSync(com.devmod.network.RecipeSyncPayload payload, IPayloadContext context) {
        observeFuture(context.enqueueWork(() -> {
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
        }), "recipe sync");
    }

    // =================================================================================
    // TELEMETRY BATCH (server-side)
    // =================================================================================
    public static void handleTelemetryBatch(TelemetryBatchPayload payload, IPayloadContext context) {
        observeFuture(context.enqueueWork(() -> {
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
        }), "telemetry batch");
    }

    // =================================================================================
    // GLOBAL CONFIG SYNC (client-side)
    // =================================================================================
    public static void handleGlobalConfigSync(GlobalConfigSyncPayload payload, IPayloadContext context) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            observeFuture(context.enqueueWork(() ->
                NetworkHandler.withClientHooks(hooks -> hooks.handleGlobalConfigSync(payload))), "global config sync");
        }
    }

    // =================================================================================
    // RECIPE CLIENT SYNC (client-side)
    // =================================================================================
    public static void handleRecipeClientSync(RecipeClientSyncPayload payload, IPayloadContext context) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            observeFuture(context.enqueueWork(() ->
                NetworkHandler.withClientHooks(hooks -> hooks.handleRecipeClientSync(payload))), "recipe client sync");
        }
    }

    // =================================================================================
    // EDITOR APPLY CONFIRM (client-side)
    // =================================================================================
    public static void handleEditorApplyConfirm(EditorApplyConfirmPayload payload, IPayloadContext context) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            observeFuture(context.enqueueWork(() ->
                NetworkHandler.withClientHooks(hooks -> hooks.handleConfigEditorApplyConfirm(payload))), "editor apply confirm");
        }
    }

    // =================================================================================
    // MOB CONFIG CONFIRM (client-side)
    // =================================================================================
    public static void handleMobConfigConfirm(MobConfigConfirmPayload payload, IPayloadContext context) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            observeFuture(context.enqueueWork(() ->
                NetworkHandler.withClientHooks(hooks -> hooks.handleConfigMobConfigConfirm(payload))), "mob config confirm");
        }
    }
}
