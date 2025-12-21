package com.frenkvs.devmod.actions;

import net.minecraft.network.chat.Component;

import java.util.Objects;
import java.util.function.Predicate;

public final class ActionPreconditions {
    private ActionPreconditions() {}

    public static ActionPrecondition always() {
        return context -> true;
    }

    public static ActionPrecondition clientOnly() {
        return withMessage(ActionContext::isClientSide, "devmod.action.client_only");
    }

    public static ActionPrecondition serverOnly() {
        return withMessage(context -> !context.isClientSide(), "devmod.action.server_only");
    }

    public static ActionPrecondition requiresPlayer() {
        return withMessage(context -> context.getPlayer() != null, "devmod.action.requires_player");
    }

    public static ActionPrecondition requiresServerPlayer() {
        return withMessage(context -> context.getServerPlayer() != null, "devmod.action.requires_server_player");
    }

    public static ActionPrecondition requiresPayload(Class<?> type, String messageKey) {
        Objects.requireNonNull(type, "type");
        return withMessage(context -> context.getPayload(type) != null, messageKey);
    }

    public static ActionPrecondition screenClosed() {
        return withMessage(context -> !context.isScreenOpen()
            || context.getOrigin() == ActionOrigin.RADIAL
            || context.getOrigin() == ActionOrigin.UI,
            "devmod.action.screen_open");
    }

    public static ActionPrecondition requiresPermission(int level) {
        return new ActionPrecondition() {
            @Override
            public boolean test(ActionContext context) {
                var source = context.getSource();
                if (source != null) {
                    return source.hasPermission(level);
                }
                var serverPlayer = context.getServerPlayer();
                if (serverPlayer != null) {
                    return serverPlayer.hasPermissions(level);
                }
                var player = context.getPlayer();
                if (player != null) {
                    return player.hasPermissions(level);
                }
                return false;
            }

            @Override
            public Component failureMessage(ActionContext context) {
                return Component.translatable("devmod.action.requires_permission", level);
            }
        };
    }

    public static ActionPrecondition requiresPermissionOrClient(int level) {
        return new ActionPrecondition() {
            @Override
            public boolean test(ActionContext context) {
                if (context.isClientSide()) {
                    var player = context.getPlayer();
                    return player != null && player.hasPermissions(level);
                }
                var source = context.getSource();
                if (source != null) {
                    return source.hasPermission(level);
                }
                var serverPlayer = context.getServerPlayer();
                if (serverPlayer != null) {
                    return serverPlayer.hasPermissions(level);
                }
                return false;
            }

            @Override
            public Component failureMessage(ActionContext context) {
                return Component.translatable("devmod.action.requires_permission", level);
            }
        };
    }

    public static ActionPrecondition withMessage(Predicate<ActionContext> predicate, String messageKey) {
        Objects.requireNonNull(predicate, "predicate");
        Objects.requireNonNull(messageKey, "messageKey");
        return new ActionPrecondition() {
            @Override
            public boolean test(ActionContext context) {
                return predicate.test(context);
            }

            @Override
            public Component failureMessage(ActionContext context) {
                return Component.translatable(messageKey);
            }
        };
    }
}
