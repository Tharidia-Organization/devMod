package com.devmod.area.network;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import net.neoforged.neoforge.network.handling.IPayloadContext;

import com.devmod.DevMod;
import com.devmod.area.aesthetic.AreaBuilderMessages;
import com.devmod.area.data.AreaAuditLog;
import com.devmod.area.data.AreaDefinition;
import com.devmod.area.data.AreaRegistry;
import com.devmod.area.template.AreaTemplate;
import com.devmod.area.template.AreaTemplateRegistry;

/**
 * Handles template-related network operations.
 * Extracted from AreaNetworkHandler for better modularity.
 */
public final class TemplateManagementHandler {

    private static final String CLIENT_HOOKS_CLASS = "com.devmod.client.area.AreaClientHooks";

    @Nullable
    private static volatile MethodHandle handleTemplateListMethod;
    @Nullable
    private static volatile MethodHandle handleTemplateDataMethod;

    private TemplateManagementHandler() {}

    // ========================================================================
    // Server Handlers
    // ========================================================================

    public static void handleRequestTemplateListServer(RequestTemplateListPayload payload, IPayloadContext ctx) {
        AreaNetworkHandler.enqueueWork(ctx, () -> {
            ServerPlayer player = (ServerPlayer) ctx.player();
            if (player == null) return;

            AreaTemplateRegistry registry = AreaTemplateRegistry.get(Objects.requireNonNull(player.getServer()));
            List<TemplateListPayload.TemplateSummary> summaries = new ArrayList<>();

            for (AreaTemplate template : registry.getAllTemplates()) {
                summaries.add(new TemplateListPayload.TemplateSummary(
                    template.id(),
                    template.name(),
                    template.description(),
                    template.author(),
                    template.shape(),
                    template.createdAt()
                ));
            }

            AreaNetworkHandler.sendPacket(player, new TemplateListPayload(summaries));
            DevMod.LOGGER.debug("[Template] Sent template list to {} ({} templates)",
                player.getName().getString(), summaries.size());
        });
    }

    public static void handleLoadTemplateServer(LoadTemplatePayload payload, IPayloadContext ctx) {
        AreaNetworkHandler.enqueueWork(ctx, () -> {
            ServerPlayer player = (ServerPlayer) ctx.player();
            if (player == null) return;

            AreaTemplateRegistry registry = AreaTemplateRegistry.get(Objects.requireNonNull(player.getServer()));

            registry.getTemplate(Objects.requireNonNull(payload.templateId())).ifPresentOrElse(
                template -> {
                    String presetId = template.palette().presetId() != null
                        ? template.palette().presetId() : "default";

                    TemplateDataPayload response = new TemplateDataPayload(
                        template.id(),
                        template.generationType(),
                        template.shape(),
                        template.dimensions(),
                        template.palette(),
                        template.biomeConfig(),
                        template.options(),
                        presetId
                    );
                    AreaNetworkHandler.sendPacket(player, response);
                    DevMod.LOGGER.debug("[Template] Sent template data for {} to {}",
                        template.name(), player.getName().getString());
                },
                () -> player.displayClientMessage(
                    Objects.requireNonNull(AreaBuilderMessages.errorTranslatable("area.message.error_template_not_found")), true)
            );
        });
    }

    public static void handleSaveTemplateServer(SaveAreaTemplatePayload payload, IPayloadContext ctx) {
        AreaNetworkHandler.enqueueWork(ctx, () -> {
            ServerPlayer player = (ServerPlayer) ctx.player();
            if (player == null) return;

            if (!player.hasPermissions(2)) {
                player.displayClientMessage(
                    Objects.requireNonNull(AreaBuilderMessages.errorTranslatable(AreaBuilderMessages.MSG_ERROR_PERMISSION)), true);
                return;
            }

            // SEC-03 fix: Rate limiting for template saves
            UUID playerId = player.getUUID();
            long now = System.currentTimeMillis();
            long remainingSeconds = CooldownManager.getTemplateSaveCooldownRemaining(playerId, now);
            if (remainingSeconds > 0) {
                player.displayClientMessage(
                    Objects.requireNonNull(Component.translatable("area.message.error_template_cooldown", remainingSeconds)), true);
                return;
            }
            CooldownManager.updateTemplateSaveCooldown(playerId, now);

            if (!payload.isValid()) {
                player.displayClientMessage(
                    Objects.requireNonNull(AreaBuilderMessages.errorTranslatable("area.message.error_invalid_template")), true);
                return;
            }

            AreaRegistry areaRegistry = AreaRegistry.get(Objects.requireNonNull(player.getServer()));
            AreaTemplateRegistry templateRegistry = AreaTemplateRegistry.get(Objects.requireNonNull(player.getServer()));

            UUID sourceAreaId = Objects.requireNonNull(payload.sourceAreaId());
            String templateName = Objects.requireNonNull(payload.templateName());
            String description = Objects.requireNonNull(payload.description());
            String authorName = Objects.requireNonNull(player.getName().getString());

            Optional<AreaDefinition> sourceOpt = areaRegistry.getArea(sourceAreaId);
            if (sourceOpt.isEmpty()) {
                player.displayClientMessage(
                    Objects.requireNonNull(AreaBuilderMessages.errorTranslatable("area.message.error_area_not_found")), true);
                return;
            }

            if (!templateRegistry.hasRoom()) {
                player.displayClientMessage(
                    Objects.requireNonNull(AreaBuilderMessages.errorTranslatable("area.message.error_template_registry_full")), true);
                return;
            }

            AreaDefinition sourceArea = sourceOpt.get();

            AreaTemplate template = AreaTemplate.fromDefinition(
                Objects.requireNonNull(sourceArea),
                templateName,
                description,
                authorName
            );

            Optional<UUID> savedId = templateRegistry.saveTemplate(template);
            if (savedId.isPresent()) {
                UUID templateId = savedId.get();
                player.displayClientMessage(
                    Objects.requireNonNull(AreaBuilderMessages.successTranslatable("area.message.template_saved", templateName)), true);
                DevMod.LOGGER.info("Player {} saved template: {} ({})",
                    authorName, templateName, templateId);

                AreaAuditLog.get(Objects.requireNonNull(player.getServer())).log(
                    AreaAuditLog.ActionType.TEMPLATE_SAVE,
                    Objects.requireNonNull(templateId),
                    templateName,
                    player,
                    "sourceArea=" + sourceAreaId
                );
            } else {
                player.displayClientMessage(
                    Objects.requireNonNull(AreaBuilderMessages.errorTranslatable("area.message.error_template_save_failed")), true);
            }
        });
    }

    public static void handleDeleteTemplateServer(DeleteTemplatePayload payload, IPayloadContext ctx) {
        AreaNetworkHandler.enqueueWork(ctx, () -> {
            ServerPlayer player = (ServerPlayer) ctx.player();
            if (player == null) return;

            if (!player.hasPermissions(2)) {
                player.displayClientMessage(
                    Objects.requireNonNull(AreaBuilderMessages.errorTranslatable(AreaBuilderMessages.MSG_ERROR_PERMISSION)), true);
                return;
            }

            UUID templateId = payload.templateId();
            if (templateId == null) {
                player.displayClientMessage(
                    Objects.requireNonNull(AreaBuilderMessages.errorTranslatable("area.message.error_template_not_found")), true);
                return;
            }

            AreaTemplateRegistry registry = AreaTemplateRegistry.get(Objects.requireNonNull(player.getServer()));

            Optional<AreaTemplate> templateOpt = registry.getTemplate(templateId);
            if (templateOpt.isEmpty()) {
                player.displayClientMessage(
                    Objects.requireNonNull(AreaBuilderMessages.errorTranslatable("area.message.error_template_not_found")), true);
                return;
            }

            AreaTemplate template = templateOpt.get();

            // SEC-02 fix: Check ownership - only template author or OP4+ can delete
            String templateAuthor = template.author();
            String playerName = player.getName().getString();
            if (templateAuthor != null && !templateAuthor.equalsIgnoreCase(playerName) && !player.hasPermissions(4)) {
                player.displayClientMessage(
                    Objects.requireNonNull(AreaBuilderMessages.errorTranslatable("area.message.error_not_author")), true);
                DevMod.LOGGER.debug("[Template] Delete denied for {} - not author of template {} (author: {})",
                    playerName, template.name(), templateAuthor);
                return;
            }

            if (registry.deleteTemplate(templateId)) {
                player.displayClientMessage(
                    Objects.requireNonNull(AreaBuilderMessages.successTranslatable("area.message.template_deleted", template.name())), true);
                DevMod.LOGGER.info("Player {} deleted template: {} ({})",
                    player.getName().getString(), template.name(), templateId);

                AreaAuditLog.get(Objects.requireNonNull(player.getServer())).log(
                    AreaAuditLog.ActionType.TEMPLATE_DELETE,
                    templateId,
                    Objects.requireNonNull(template.name()),
                    player,
                    null
                );
            } else {
                player.displayClientMessage(
                    Objects.requireNonNull(AreaBuilderMessages.errorTranslatable("area.message.error_template_delete_failed")), true);
            }
        });
    }

    // ========================================================================
    // Client Handlers
    // ========================================================================

    public static void handleTemplateListClient(TemplateListPayload payload, IPayloadContext ctx) {
        AreaNetworkHandler.enqueueWork(ctx, () -> {
            MethodHandle method = getHandleTemplateListMethod();
            if (method == null) return;
            try {
                method.invokeExact(payload);
            } catch (Throwable e) {
                DevMod.LOGGER.error("Failed to handle template list", e);
            }
        });
    }

    public static void handleTemplateDataClient(TemplateDataPayload payload, IPayloadContext ctx) {
        AreaNetworkHandler.enqueueWork(ctx, () -> {
            MethodHandle method = getHandleTemplateDataMethod();
            if (method == null) return;
            try {
                method.invokeExact(payload);
            } catch (Throwable e) {
                DevMod.LOGGER.error("Failed to handle template data", e);
            }
        });
    }

    // ========================================================================
    // MethodHandle Utilities
    // ========================================================================

    @Nullable
    private static MethodHandle getHandleTemplateListMethod() {
        if (handleTemplateListMethod == null) {
            synchronized (TemplateManagementHandler.class) {
                if (handleTemplateListMethod == null) {
                    handleTemplateListMethod = lookupMethod("handleTemplateList", TemplateListPayload.class);
                }
            }
        }
        return handleTemplateListMethod;
    }

    @Nullable
    private static MethodHandle getHandleTemplateDataMethod() {
        if (handleTemplateDataMethod == null) {
            synchronized (TemplateManagementHandler.class) {
                if (handleTemplateDataMethod == null) {
                    handleTemplateDataMethod = lookupMethod("handleTemplateData", TemplateDataPayload.class);
                }
            }
        }
        return handleTemplateDataMethod;
    }

    @Nullable
    private static MethodHandle lookupMethod(String methodName, Class<?> paramType) {
        try {
            Class<?> hooksClass = Class.forName(CLIENT_HOOKS_CLASS);
            MethodHandles.Lookup lookup = MethodHandles.lookup();
            return lookup.findStatic(hooksClass, methodName, MethodType.methodType(void.class, paramType));
        } catch (ClassNotFoundException e) {
            DevMod.LOGGER.debug("[Template] Client hooks class not available (dedicated server)");
            return null;
        } catch (NoSuchMethodException | IllegalAccessException e) {
            DevMod.LOGGER.error("[Template] Failed to lookup client method: {}", methodName, e);
            return null;
        }
    }
}
