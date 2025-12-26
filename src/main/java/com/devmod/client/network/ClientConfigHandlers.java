package com.devmod.client.network;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import com.devmod.network.EditorApplyConfirmPayload;
import com.devmod.network.GlobalConfigSyncPayload;
import com.devmod.network.MobConfigConfirmPayload;
import com.devmod.network.RecipeClientSyncPayload;
import com.devmod.recipe.RecipeConfigManager;
import com.devmod.util.I18n;

@OnlyIn(Dist.CLIENT)
public final class ClientConfigHandlers {

    private static final Logger LOGGER = LoggerFactory.getLogger(ClientConfigHandlers.class);

    private ClientConfigHandlers() {}

    /**
     * Handles global config sync from server.
     * Applies armor and weapon configs to client-side managers.
     */
    public static void handleGlobalConfigSync(GlobalConfigSyncPayload payload) {
        payload.applyToClientConfigs();
        LOGGER.debug("[DevMod] Applied global config sync from server");
    }

    /**
     * Handles recipe sync from server.
     * Applies recipe changes to client-side cache.
     */
    public static void handleRecipeClientSync(RecipeClientSyncPayload payload) {
        switch (payload.operation()) {
            case ADD -> {
                for (var recipe : payload.recipes()) {
                    RecipeConfigManager.addRecipeClientOnly(recipe);
                }
                LOGGER.debug("[DevMod] Added {} recipe(s) from server", payload.recipes().size());
            }
            case DELETE -> {
                for (var recipe : payload.recipes()) {
                    RecipeConfigManager.removeRecipeClientOnly(recipe.id());
                }
                LOGGER.debug("[DevMod] Deleted {} recipe(s) from server", payload.recipes().size());
            }
            case SYNC_ALL -> {
                RecipeConfigManager.clearClientRecipes();
                for (var recipe : payload.recipes()) {
                    RecipeConfigManager.addRecipeClientOnly(recipe);
                }
                LOGGER.debug("[DevMod] Full recipe sync: {} recipes from server", payload.recipes().size());
            }
        }
    }

    /**
     * Handles editor apply confirmation from server.
     * Shows feedback to user in chat.
     */
    public static void handleEditorApplyConfirm(EditorApplyConfirmPayload payload) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        Component message;
        if (payload.success()) {
            String key = payload.global()
                ? "devmod.editor.apply.global.success"
                : "devmod.editor.apply.success";
            message = I18n.translate(key, payload.scope(), payload.itemId());
        } else {
            message = I18n.errorWithDetails("devmod.editor.apply.failed", payload.message());
        }
        mc.player.displayClientMessage(message, false);

        LOGGER.debug("[DevMod] Editor apply confirm: success={}, global={}, scope={}, item={}",
            payload.success(), payload.global(), payload.scope(), payload.itemId());
    }

    /**
     * Handles mob config confirmation from server.
     * Shows feedback to user in chat.
     */
    public static void handleMobConfigConfirm(MobConfigConfirmPayload payload) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        Component message;
        if (payload.success()) {
            if (payload.isGlobal()) {
                message = I18n.translate("devmod.mob.config.global.success",
                    payload.mobTypeName(), payload.affectedCount());
            } else {
                message = I18n.translate("devmod.mob.config.success", payload.mobTypeName());
            }
        } else {
            message = I18n.errorWithDetails("devmod.mob.config.failed", payload.message());
        }
        mc.player.displayClientMessage(message, false);

        LOGGER.debug("[DevMod] Mob config confirm: success={}, global={}, mob={}, affected={}",
            payload.success(), payload.isGlobal(), payload.mobTypeName(), payload.affectedCount());
    }
}
