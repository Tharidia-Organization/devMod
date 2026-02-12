package com.devmod.template.network;


import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import com.devmod.DevMod;
import com.devmod.network.PayloadValidation;
import com.devmod.template.data.RoomTemplate;
import com.devmod.template.data.TemplateRegistry;
import com.devmod.template.runtime.TemplateManager;
/**
 * Network handler for Room Templates system.
 * Registers payloads and handles server-side processing.
 */
public enum TemplateNetworkHandler {
    INSTANCE;

    private static final String PROTOCOL_VERSION = "1";

    /** Default height for clearing area before template application */
    private static final int DEFAULT_CLEAR_HEIGHT = 8;

    // MethodHandle for client-side reflection (consistent with other network handlers)
    private static final String CLIENT_HANDLER_CLASS = "com.devmod.client.template.TemplateClientHandler";

    @Nullable
    private static volatile MethodHandle openEditorMethod;

    @Nullable
    private static MethodHandle getOpenEditorMethod() {
        if (openEditorMethod == null) {
            synchronized (TemplateNetworkHandler.class) {
                if (openEditorMethod == null) {
                    try {
                        Class<?> hooksClass = Class.forName(CLIENT_HANDLER_CLASS);
                        MethodHandles.Lookup lookup = MethodHandles.lookup();
                        openEditorMethod = lookup.findStatic(hooksClass, "handleOpenEditor",
                            MethodType.methodType(void.class, OpenTemplateEditorPayload.class));
                    } catch (ClassNotFoundException e) {
                        DevMod.LOGGER.debug("[Template] Client handler class not available (dedicated server)");
                    } catch (NoSuchMethodException | IllegalAccessException e) {
                        DevMod.LOGGER.error("[Template] Failed to lookup client method: handleOpenEditor", e);
                    }
                }
            }
        }
        return openEditorMethod;
    }

    /**
     * Register all template payloads.
     */
    public void registerPayloads(@Nonnull RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(DevMod.MODID)
            .versioned(PROTOCOL_VERSION);

        // Open template editor (S->C)
        registrar.playToClient(
            OpenTemplateEditorPayload.TYPE,
            OpenTemplateEditorPayload.STREAM_CODEC,
            PayloadValidation.validated(TemplateNetworkHandler::handleOpenTemplateEditor,
                PayloadValidation.PayloadLimits.SYNC_MEDIUM)
        );

        // Apply template (C->S)
        registrar.playToServer(
            ApplyTemplatePayload.TYPE,
            ApplyTemplatePayload.STREAM_CODEC,
            PayloadValidation.validated(this::handleApplyTemplate,
                PayloadValidation.PayloadLimits.SMALL)
        );

        // Save template (C->S)
        registrar.playToServer(
            SaveTemplatePayload.TYPE,
            SaveTemplatePayload.STREAM_CODEC,
            PayloadValidation.validated(this::handleSaveTemplate,
                PayloadValidation.PayloadLimits.XLARGE)
        );

        DevMod.LOGGER.debug("[Template] Registered network payloads");
    }

    // =========================================================================
    // HANDLERS
    // =========================================================================

    private void handleApplyTemplate(ApplyTemplatePayload payload, IPayloadContext context) {
        enqueueWork(context, () -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }

            // Permission check
            if (!player.hasPermissions(2)) {
                player.sendSystemMessage(Component.translatable("message.devmod.template.error.no_permission"));
                return;
            }

            ServerLevel level = player.serverLevel();

            // Clear area if requested
            if (payload.clearFirst()) {
                TemplateManager.INSTANCE.clearArea(level,
                    payload.minX(), payload.minZ(), payload.maxX(), payload.maxZ(),
                    payload.floorY(), DEFAULT_CLEAR_HEIGHT);
            }

            // Apply template
            TemplateManager.INSTANCE.applyTemplate(level, payload.templateId(),
                payload.minX(), payload.minZ(), payload.maxX(), payload.maxZ(),
                payload.floorY(), player);
        });
    }

    private void handleSaveTemplate(SaveTemplatePayload payload, IPayloadContext context) {
        enqueueWork(context, () -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }

            // Permission check
            if (!player.hasPermissions(2)) {
                player.sendSystemMessage(Component.translatable("message.devmod.template.error.no_permission"));
                return;
            }

            ServerLevel level = player.serverLevel();
            TemplateRegistry registry = TemplateRegistry.get(level);

            RoomTemplate template = payload.template();

            // Check if updating or creating
            if (registry.getTemplate(template.id()).isPresent()) {
                // Update existing
                if (registry.updateTemplate(template.id(), template, payload.expectedRevision())) {
                    player.sendSystemMessage(Component.translatable(
                        "message.devmod.template.updated", template.displayName()));
                } else {
                    player.sendSystemMessage(Component.translatable(
                        "message.devmod.template.error.revision_conflict"));
                }
            } else {
                // Create new
                if (!template.isCustom()) {
                    player.sendSystemMessage(Component.translatable(
                        "message.devmod.template.error.cannot_modify_preset"));
                    return;
                }
                registry.registerTemplate(template);
                player.sendSystemMessage(Component.translatable(
                    "message.devmod.template.saved", template.displayName()));
            }
        });
    }

    private static void handleOpenTemplateEditor(OpenTemplateEditorPayload payload, IPayloadContext context) {
        if (FMLEnvironment.dist != Dist.CLIENT) {
            return;
        }

        enqueueWork(context, () -> {
            MethodHandle method = getOpenEditorMethod();
            if (method == null) return;
            try {
                method.invokeExact(payload);
            } catch (Throwable e) {
                DevMod.LOGGER.warn("[Template] Failed to open template editor on client", e);
            }
        });
    }

    @SuppressWarnings("FutureReturnValueIgnored")
    private static void enqueueWork(IPayloadContext context, Runnable work) {
        context.enqueueWork(work);
    }
}
